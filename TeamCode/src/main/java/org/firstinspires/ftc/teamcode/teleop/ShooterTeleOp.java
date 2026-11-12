package org.firstinspires.ftc.teamcode.teleop;

import static org.firstinspires.ftc.teamcode.config.RobotConstants.*;

import com.acmerobotics.dashboard.FtcDashboard;
import com.acmerobotics.dashboard.config.Config;
import com.acmerobotics.dashboard.telemetry.MultipleTelemetry;
import com.qualcomm.hardware.limelightvision.LLResult;
import com.qualcomm.hardware.limelightvision.LLResultTypes;
import com.qualcomm.hardware.limelightvision.Limelight3A;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.DcMotorSimple;
import com.qualcomm.robotcore.hardware.Servo;
import com.qualcomm.robotcore.hardware.VoltageSensor;
import com.qualcomm.robotcore.util.ElapsedTime;
import com.qualcomm.robotcore.util.Range;

import java.util.List;

/**
 * Shooter teleop.
 *
 * <p>Gamepad 1 (driver): left stick = drive, right stick X = turn (robot-centric mecanum).
 * HOLD right bumper = auto-align: the whole robot turns until the midpoint of the two
 * AprilTags (Limelight pipeline 1) is centered. If both tags are not visible, the
 * right stick turns the robot as normal.
 *
 * <p>Gamepad 2 (operator):
 * <ul>
 *   <li>A or B (hold) = lt + rt transfer.</li>
 *   <li>Right bumper (press) = blocker opens (0.75) for 3 s while lt + rt run, then the
 *       blocker returns to 0.55.</li>
 * </ul>
 *
 * <p>The "blocker" servo rests at 0.55 the whole time except during the 3 s feed.
 * While feeding (A, B, or the timed sequence) the flywheel switches to bang-bang.
 */
@TeleOp(name = "Shooter TeleOp", group = "teleop")
public class ShooterTeleOp extends LinearOpMode {

    @Config("Shooter TeleOp")
    public static class G {
        // Setpoints.
        // The three bang-bang numbers are SEEDED from RobotConstants (static-imported at the top of
        // this file) so there is one canonical value instead of two copies that drift apart. They
        // stay non-final so the dashboard can still tune them live; the Ivy auto reads the
        // RobotConstants copy, so a value you settle on here has to be pasted back there.
        public static double targetTps = BANG_TARGET_TPS;
        public static double transferPower = 1.0;

        // Flywheel (tuned values from the PIDF Tuner)
        public static double kP = 0.03;
        public static double kI = 0;
        public static double kD = 0;
        public static double kF = 1;
        public static double maxTps = 2540;
        public static double deadbandTps = BANG_DEADBAND_TPS;
        public static double bangPower = BANG_POWER;

        // Blocker gate
        public static double blockerClosedPos = 0.55;
        public static double blockerOpenPos = 0.8;
        public static double blockerOpenSeconds = 2.0;

        // Limelight auto-align
        public static int alignPipeline = 1;
        public static double alignKp = 0.02;          // turn power per degree of error
        public static double alignKs = 0.05;          // minimum power to overcome friction
        public static double alignMaxPower = 0.2;
        public static double alignDeadbandDeg = 1.0;  // within this, stop turning
        public static double alignOffsetDeg = 0.0;    // trim if the camera is not centered on the robot
        public static double maxStalenessMs = 150;    // ignore results older than this
    }

    // Drivetrain hardware names. Change these to match your configuration.
    private static final String FL_NAME = "fl";
    private static final String FR_NAME = "fr";
    private static final String BL_NAME = "bl";
    private static final String BR_NAME = "br";

    private static final double STICK_DEADBAND = 0.05;

    public static final String SHOOTER_NAME = "fly";
    public static final String BLOCKER_NAME = "block";
    public static final String LIMELIGHT_NAME = "limelight";

    private DcMotor fl, fr, bl, br;
    private DcMotorEx shooter;
    private DcMotor roll;
    private DcMotor transferL, transferR;
    private Servo blocker;
    private Limelight3A limelight;
    private VoltageSensor voltageSensor;

    private double usedP, usedI, usedD, usedF;
    private double integral, lastError;
    private long lastTimeNs;

    // Blocker sequence state
    private final ElapsedTime blockerTimer = new ElapsedTime();
    private boolean blockerSequenceActive = false;
    private boolean prevGp2RightBumper = false;

    // Auto-align telemetry
    private boolean aligning = false;
    private boolean tagsSeen = false;
    private double alignErrorDeg = 0;

    @Override
    public void runOpMode() {
        telemetry = new MultipleTelemetry(telemetry, FtcDashboard.getInstance().getTelemetry());

        fl = hardwareMap.get(DcMotor.class, FL_NAME);
        fr = hardwareMap.get(DcMotor.class, FR_NAME);
        bl = hardwareMap.get(DcMotor.class, BL_NAME);
        br = hardwareMap.get(DcMotor.class, BR_NAME);
        // Same directions as the repo's DrivetrainHardware: left FORWARD, right REVERSE
        fl.setDirection(DcMotorSimple.Direction.FORWARD);
        bl.setDirection(DcMotorSimple.Direction.FORWARD);
        fr.setDirection(DcMotorSimple.Direction.REVERSE);
        br.setDirection(DcMotorSimple.Direction.REVERSE);
        fl.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
        fr.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
        bl.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
        br.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);

        shooter = hardwareMap.get(DcMotorEx.class, SHOOTER_NAME);
        StaticShooterTeleOp.configureFlywheelMotor(shooter, false);
        shooter.setDirection(DcMotorSimple.Direction.REVERSE); // same as the tuner

        roll = hardwareMap.get(DcMotor.class, ROLL_NAME);
        transferL = hardwareMap.get(DcMotor.class, TRANSFER_L_NAME);
        transferR = hardwareMap.get(DcMotor.class, TRANSFER_R_NAME);
        transferL.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
        transferR.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
        transferL.setDirection(DcMotorSimple.Direction.FORWARD);
        transferR.setDirection(DcMotorSimple.Direction.REVERSE); // rt is reversed
        transferL.setPower(0);
        transferR.setPower(0);

        blocker = hardwareMap.get(Servo.class, BLOCKER_NAME);
        blocker.setPosition(G.blockerClosedPos);

        limelight = hardwareMap.get(Limelight3A.class, LIMELIGHT_NAME);
        limelight.setPollRateHz(100);
        limelight.pipelineSwitch(G.alignPipeline);
        limelight.start();

        voltageSensor = hardwareMap.voltageSensor.iterator().next();

        resetController();
        roll.setPower(0.4);

        telemetry.addLine("GP1: sticks = drive, hold RB = auto-align to AprilTags. "
                + "GP2: A or B = transfer, RB = blocker open + transfer for 3 s.");
        telemetry.update();

        waitForStart();
        if (isStopRequested()) {
            stopAll();
            return;
        }

        while (!isStopRequested()) {
            if (gainsChanged()) resetController();

            updateBlocker();
            updateDrive();
            updateTransfer();
            updateFlywheel();

            pushTelemetry();
            sleep(20);
        }
        stopAll();
    }

    // ------------------------------------------------------------------ drive
    /** Robot-centric mecanum. Holding GP1 right bumper replaces the turn input with Limelight auto-align. */
    private void updateDrive() {
        double rawY = -gamepad1.left_stick_y;
        double rawX = gamepad1.left_stick_x;
        double y = Math.abs(rawY) > STICK_DEADBAND ? rawY : 0;
        double x = Math.abs(rawX) > STICK_DEADBAND ? rawX : 0;
        double rx = gamepad1.right_stick_x;

        aligning = gamepad1.right_bumper;
        if (aligning) {
            Double autoTurn = computeAlignTurn();
            if (autoTurn != null) {
                rx = autoTurn;
            }
            // if both tags are not visible, rx stays as the driver's stick input
        } else {
            tagsSeen = false;
        }

        double frontLeftPower = y + x + rx;
        double frontRightPower = y - x - rx;
        double backLeftPower = y - x + rx;
        double backRightPower = y + x - rx;

        double maxPower = Math.max(1.0, Math.max(
                Math.abs(frontLeftPower),
                Math.max(Math.abs(frontRightPower),
                        Math.max(Math.abs(backLeftPower), Math.abs(backRightPower)))));

        fl.setPower(frontLeftPower / maxPower);
        fr.setPower(frontRightPower / maxPower);
        bl.setPower(backLeftPower / maxPower);
        br.setPower(backRightPower / maxPower);
    }

    /**
     * Finds the midpoint between the two AprilTags and returns a turn power (rx) that
     * rotates the robot toward it. Returns null if two tags are not currently visible.
     *
     * <p>Limelight tx is positive when the target is to the RIGHT of the crosshair, and
     * positive rx turns the robot clockwise (right) in the drive math above, so
     * rx = +kP * error.
     */
    private Double computeAlignTurn() {
        LLResult result = limelight.getLatestResult();
        tagsSeen = false;
        if (result == null || !result.isValid() || result.getStaleness() > G.maxStalenessMs) {
            return null;
        }

        List<LLResultTypes.FiducialResult> tags = result.getFiducialResults();
        if (tags == null || tags.size() < 2) {
            return null;
        }

        // Center of the two tags = average of their horizontal angles.
        // (If more than two are visible, the first two reported are used.)
        double midTx = (tags.get(0).getTargetXDegrees() + tags.get(1).getTargetXDegrees()) / 2.0;
        tagsSeen = true;

        double error = midTx - G.alignOffsetDeg;
        alignErrorDeg = error;

        if (Math.abs(error) < G.alignDeadbandDeg) {
            return 0.0;
        }

        double turn = G.alignKp * error + G.alignKs * Math.signum(error);
        return Range.clip(turn, -G.alignMaxPower, G.alignMaxPower);
    }

    // ---------------------------------------------------------------- blocker
    /**
     * GP2 right bumper (rising edge) opens the blocker for blockerOpenSeconds, then closes it.
     * Pressing again while the sequence is running is ignored.
     */
    private void updateBlocker() {
        boolean rb = gamepad2.right_bumper;
        if (rb && !prevGp2RightBumper && !blockerSequenceActive) {
            blockerSequenceActive = true;
            blockerTimer.reset();
        }
        prevGp2RightBumper = rb;

        if (blockerSequenceActive && blockerTimer.seconds() >= G.blockerOpenSeconds) {
            blockerSequenceActive = false;
        }

        blocker.setPosition(blockerSequenceActive ? G.blockerOpenPos : G.blockerClosedPos);
    }

    // --------------------------------------------------------------- transfer
    /** True while the transfer should be feeding: GP2 A/B held, or the timed blocker sequence. */
    private boolean isFeeding() {
        return gamepad2.a || gamepad2.b || blockerSequenceActive;
    }

    /**
     * Runs lt/rt while feeding, otherwise 0.
     * Same direction as the tuner's A button: rt is reversed through its motor direction.
     */
    private void updateTransfer() {
        double power = isFeeding() ? -Range.clip(G.transferPower, 0.0, 1.0) : 0.0;
        transferL.setPower(power);
        transferR.setPower(power);
        roll.setPower(0.4);
    }

    // -------------------------------------------------------------- flywheel
    private void updateFlywheel() {
        double target = G.targetTps;
        double velocity = shooter.getVelocity();

        if (target <= 0) {
            shooter.setPower(0);
            resetController();
        } else if (isFeeding()) {
            shooter.setPower(velocity < target - G.deadbandTps ? G.bangPower : 0);
        } else {
            shooter.setPower(pidfPower(target, velocity));
        }
    }

    private double pidfPower(double target, double current) {
        long now = System.nanoTime();
        double dt = (now - lastTimeNs) / 1e9;
        if (dt <= 0) return 0;
        lastTimeNs = now;

        double error = target - current;

        if (usedI != 0) {
            integral += error * dt;
            integral = Range.clip(integral, -1, 1);
        } else {
            integral = 0;
        }

        double derivative = (error - lastError) / dt;
        lastError = error;

        double ff = usedF * (target / G.maxTps);
        double voltageComp = 12.0 / Math.max(voltageSensor.getVoltage(), 1.0);
        double output = (ff + usedP * error + usedI * integral + usedD * derivative) * voltageComp;

        return Range.clip(output, -1, 1);
    }

    private boolean gainsChanged() {
        return G.kP != usedP || G.kI != usedI || G.kD != usedD || G.kF != usedF;
    }

    private void resetController() {
        usedP = G.kP;
        usedI = G.kI;
        usedD = G.kD;
        usedF = G.kF;
        integral = 0;
        lastError = 0;
        lastTimeNs = System.nanoTime();
    }

    // ---------------------------------------------------------------- helpers
    private void pushTelemetry() {
        double v = shooter.getVelocity();
        telemetry.addData("target tps", "%.0f", G.targetTps);
        telemetry.addData("vel", v);
        telemetry.addData("err", G.targetTps - v);
        telemetry.addData("transfer power", "%.2f", G.transferPower);
        telemetry.addData("voltage", "%.2f", voltageSensor.getVoltage());
        telemetry.addData("aligning (GP1 RB)", aligning);
        telemetry.addData("both tags seen", tagsSeen);
        telemetry.addData("align error deg", "%.2f", alignErrorDeg);
        telemetry.addData("blocker active", blockerSequenceActive);
        telemetry.addData("blocker time left", "%.1f",
                blockerSequenceActive ? Math.max(0, G.blockerOpenSeconds - blockerTimer.seconds()) : 0.0);
        telemetry.update();
    }

    private void stopAll() {
        if (fl != null) { fl.setPower(0); fr.setPower(0); bl.setPower(0); br.setPower(0); }
        if (shooter != null) shooter.setPower(0);
        if (transferL != null) { transferL.setPower(0); transferR.setPower(0); }
        if (roll != null) roll.setPower(0);
        if (blocker != null) blocker.setPosition(G.blockerClosedPos);
        if (limelight != null) limelight.stop();
    }
}