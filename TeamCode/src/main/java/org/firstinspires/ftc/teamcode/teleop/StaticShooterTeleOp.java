package org.firstinspires.ftc.teamcode.teleop;

import static org.firstinspires.ftc.teamcode.config.RobotConstants.*;

import com.qualcomm.hardware.lynx.LynxModule;
import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.DcMotorSimple;
import com.qualcomm.robotcore.hardware.Servo;
import com.seattlesolvers.solverslib.controller.PIDFController;

import org.firstinspires.ftc.teamcode.math.ShotTable;

import java.util.List;

/**
 * Static-shooter teleop. ONE FILE on purpose: no subsystem classes, no commands, no GamepadEx.
 * Everything the robot does in a match is here, in the order it happens each loop.
 *
 * Controls (all on gamepad1):
 *   sticks        mecanum, robot-centric (left = move/strafe, right x = turn)
 *   B             toggle flower intake
 *   A (hold)      intake + transfer forward
 *   X (hold)      intake + transfer reverse
 *   Y             toggle launchers (ON at start)
 *   Right bumper  open the blocker for BLOCKER_OPEN_MS then close (presses during that window are ignored)
 *
 * WHY plain OpMode, not LinearOpMode: {@link OpMode#stop()} is overridable (verified in RobotCore
 * 12.0.0 bytecode, and internalRunOpMode() calls it after the last loop), while LinearOpMode.stop()
 * is final. stop() is where the G403/G404 safe-stop lives. Also verified in the same bytecode:
 * the SDK calls telemetry.update() after every user callback, so loop() does not call it.
 *
 * Bench/tuning opmodes (teleop/test) still exist and still use the subsystem classes; this file
 * does not. If you change the flywheel control loop here, change it there too — they are separate
 * copies of the same math.
 *
 * UNVERIFIED: not run on a robot.
 */
@TeleOp(name = "Static Shooter TeleOp", group = "TeleOp")
public class StaticShooterTeleOp extends OpMode {
    // ---- hardware ----
    private List<LynxModule> hubs;
    private DcMotorEx lf, lr, rf, rr;      // drive
    private DcMotorEx intakeR, intakeL;    // intake + transfer
    private DcMotorEx launchR, launchL;    // flywheels
    private Servo hood, blocker, flowerL, flowerR;

    // ---- shooter ----
    private PIDFController pidR, pidL;
    private ShotTable velocityTable, hoodTable; // null when the shot table is not filled in
    private boolean hoodConfigured;
    /** No distance source exists yet, so this stays NaN until one is wired (auto-aim off). */
    private double distanceToGoalIn = Double.NaN;
    private boolean shooterOn = true; // ON at the start of teleop
    private boolean autoAiming;
    private double targetVelTps;
    private double hoodPos = Double.NaN;

    // ---- other mechanism state ----
    private boolean flowerDeployed;
    private boolean blockerOpen;
    private boolean blockerHomed; // the blocker is first driven on the first loop, not at INIT
    private long blockerCloseAtNs;

    // ---- edge detection (a held button must act once) ----
    private boolean lastA, lastX, lastY, lastB, lastRb;

    @Override
    public void init() {
        // Manual bulk caching: cleared once per loop in loop(), so every read in a loop sees one
        // consistent snapshot. Without this, the PIDF would read stale velocities.
        hubs = hardwareMap.getAll(LynxModule.class);
        for (LynxModule hub : hubs) hub.setBulkCachingMode(LynxModule.BulkCachingMode.MANUAL);

        // ---- drive ----
        lf = hardwareMap.get(DcMotorEx.class, LF_NAME);
        lr = hardwareMap.get(DcMotorEx.class, LR_NAME);
        rf = hardwareMap.get(DcMotorEx.class, RF_NAME);
        rr = hardwareMap.get(DcMotorEx.class, RR_NAME);
        DcMotorSimple.Direction left = LEFT_SIDE_REVERSED
                ? DcMotorSimple.Direction.REVERSE : DcMotorSimple.Direction.FORWARD;
        DcMotorSimple.Direction right = LEFT_SIDE_REVERSED
                ? DcMotorSimple.Direction.FORWARD : DcMotorSimple.Direction.REVERSE;
        lf.setDirection(left);
        lr.setDirection(left);
        rf.setDirection(right);
        rr.setDirection(right);
        for (DcMotorEx m : new DcMotorEx[]{lf, lr, rf, rr}) {
            m.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
            m.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
        }

        // ---- intake + transfer ----
        intakeR = hardwareMap.get(DcMotorEx.class, INTAKE_R_NAME);
        intakeL = hardwareMap.get(DcMotorEx.class, INTAKE_L_NAME);
        intakeR.setDirection(DcMotorSimple.Direction.REVERSE);
        intakeR.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
        intakeL.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);

        // ---- flywheels ----
        launchR = hardwareMap.get(DcMotorEx.class, LAUNCH_R_NAME);
        launchL = hardwareMap.get(DcMotorEx.class, LAUNCH_L_NAME);
        launchR.setDirection(DcMotorSimple.Direction.REVERSE);
        for (DcMotorEx m : new DcMotorEx[]{launchR, launchL}) {
            m.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
            m.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER); // the PIDF is done in software
            m.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.FLOAT);
        }

        // ---- servos ----
        hood = hardwareMap.get(Servo.class, HOOD_NAME);
        blocker = hardwareMap.get(Servo.class, BLOCKER_NAME);
        flowerL = hardwareMap.get(Servo.class, FLOWER_L_NAME);
        flowerR = hardwareMap.get(Servo.class, FLOWER_R_NAME);
        flowerR.setDirection(Servo.Direction.REVERSE);
        // Nothing is commanded here: the robot must not move after INIT (G304/G403). The blocker
        // gets its closed position on the first loop after Play, the flowers on the first B press.

        hoodConfigured = !Double.isNaN(HOOD_MIN_POS) && !Double.isNaN(HOOD_MAX_POS)
                && !Double.isNaN(HOOD_DEFAULT_POS) && HOOD_MIN_POS <= HOOD_MAX_POS;

        boolean tableReady = SHOT_DISTANCES_IN.length >= 2
                && SHOT_VELOCITIES_TPS.length == SHOT_DISTANCES_IN.length
                && SHOT_HOOD_POS.length == SHOT_DISTANCES_IN.length;
        velocityTable = tableReady ? new ShotTable(SHOT_DISTANCES_IN, SHOT_VELOCITIES_TPS) : null;
        hoodTable = tableReady ? new ShotTable(SHOT_DISTANCES_IN, SHOT_HOOD_POS) : null;

        resetPids();
    }

    @Override
    public void loop() {
        for (LynxModule hub : hubs) hub.clearBulkCache();

        updateDrive();
        updateIntake();
        updateFlower();
        updateShooterToggle();
        updateBlocker();
        updateShooter();
        pushTelemetry();
    }

    // ------------------------------------------------------------------ drive
    private void updateDrive() {
        double forward = -gamepad1.left_stick_y; // raw sticks: no ambiguity about GamepadEx's Y sign
        double strafe = gamepad1.left_stick_x;
        double turn = gamepad1.right_stick_x;

        double lfP = forward + strafe + turn;
        double lrP = forward - strafe + turn;
        double rfP = forward - strafe - turn;
        double rrP = forward + strafe - turn;

        // Desaturate instead of clipping, so the turn bias is preserved at full stick.
        double max = Math.max(1.0, Math.max(Math.max(Math.abs(lfP), Math.abs(lrP)),
                Math.max(Math.abs(rfP), Math.abs(rrP))));

        lf.setPower(lfP / max);
        lr.setPower(lrP / max);
        rf.setPower(rfP / max);
        rr.setPower(rrP / max);
    }

    // ----------------------------------------------------------------- intake
    private void updateIntake() {
        boolean a = gamepad1.a, x = gamepad1.x;
        if (a) setIntake(INTAKE_POWER);          // A wins if both are held
        else if (x) setIntake(-INTAKE_POWER);
        else setIntake(0);
    }

    private void setIntake(double power) {
        intakeR.setPower(power);
        intakeL.setPower(power);
    }

    // ----------------------------------------------------------------- flower
    private void updateFlower() {
        boolean b = gamepad1.b;
        if (b && !lastB) {
            if (flowerDeployed) {
                if (!Double.isNaN(FLOWER_STOW_POS)) { // stow position not measured yet -> ignore
                    flowerDeployed = false;
                    setFlower(FLOWER_STOW_POS);
                }
            } else {
                flowerDeployed = true;
                setFlower(FLOWER_DEPLOY_POS);
            }
        }
        lastB = b;
    }

    private void setFlower(double pos) {
        flowerL.setPosition(pos);
        flowerR.setPosition(pos);
    }

    // ---------------------------------------------------------------- shooter
    private void updateShooterToggle() {
        boolean y = gamepad1.y;
        if (y && !lastY) {
            shooterOn = !shooterOn;
            if (shooterOn) resetPids(); // fresh integrator on every spin-up
        }
        lastY = y;
    }

    private void updateShooter() {
        autoAiming = velocityTable != null && Double.isFinite(distanceToGoalIn);
        targetVelTps = autoAiming ? velocityTable.get(distanceToGoalIn) : DEFAULT_LAUNCH_VEL_TPS;

        if (hoodConfigured) {
            double pos = autoAiming ? hoodTable.get(distanceToGoalIn) : HOOD_DEFAULT_POS;
            hoodPos = Math.max(HOOD_MIN_POS, Math.min(HOOD_MAX_POS, pos));
            hood.setPosition(hoodPos);
        }

        if (shooterOn && targetVelTps > 0) {
            // Output clamped to [0,1]: a flywheel is never driven backwards to brake.
            launchR.setPower(clamp01(pidR.calculate(launchR.getVelocity(), targetVelTps)));
            launchL.setPower(clamp01(pidL.calculate(launchL.getVelocity(), targetVelTps)));
        } else {
            launchR.setPower(0);
            launchL.setPower(0);
        }
    }

    private void resetPids() {
        pidR = new PIDFController(LAUNCH_KP, LAUNCH_KI, LAUNCH_KD, LAUNCH_KF);
        pidL = new PIDFController(LAUNCH_KP, LAUNCH_KI, LAUNCH_KD, LAUNCH_KF);
    }

    private static double clamp01(double v) {
        return Double.isNaN(v) ? 0.0 : Math.max(0.0, Math.min(1.0, v));
    }

    // ---------------------------------------------------------------- blocker
    private void updateBlocker() {
        if (!blockerHomed) { // first loop after Play: start from a known closed position
            blocker.setPosition(BLOCKER_CLOSED_POS);
            blockerOpen = false;
            blockerHomed = true;
        }

        // Close first, so a press on the exact loop the window expires is still accepted.
        if (blockerOpen && System.nanoTime() >= blockerCloseAtNs) {
            blocker.setPosition(BLOCKER_CLOSED_POS);
            blockerOpen = false;
        }

        boolean rb = gamepad1.right_bumper;
        if (rb && !lastRb && !blockerOpen) {
            blocker.setPosition(BLOCKER_OPEN_POS);
            blockerOpen = true;
            blockerCloseAtNs = System.nanoTime() + BLOCKER_OPEN_MS * 1_000_000L;
        }
        lastRb = rb;
    }

    // -------------------------------------------------------------- telemetry
    private void pushTelemetry() {
        telemetry.addData("Launchers", shooterOn ? "ON" : "OFF");
        telemetry.addData("Aim mode", autoAiming ? "AUTO (distance)" : "DEFAULT (no distance/table)");
        telemetry.addData("Target vel (tps)", "%.0f", targetVelTps);
        telemetry.addData("Vel R / L (tps)", "%.0f / %.0f", launchR.getVelocity(), launchL.getVelocity());
        telemetry.addData("At speed", atSpeed());
        telemetry.addData("Hood", hoodConfigured ? String.format("%.3f", hoodPos) : "NOT CONFIGURED");
        telemetry.addData("Flower", flowerDeployed ? "DEPLOYED" : "STOWED/UNKNOWN");
        telemetry.addData("Blocker", blockerOpen ? "OPEN" : "CLOSED");
        if (LAUNCH_KP == 0 && LAUNCH_KI == 0 && LAUNCH_KD == 0 && LAUNCH_KF == 0) {
            telemetry.addLine("WARN: launcher PIDF gains are all 0 (untuned)");
        }
        if (DEFAULT_LAUNCH_VEL_TPS <= 0) {
            telemetry.addLine("WARN: DEFAULT_LAUNCH_VEL_TPS is 0, launchers will not spin");
        }
    }

    private boolean atSpeed() {
        return shooterOn && targetVelTps > 0
                && Math.abs(launchR.getVelocity() - targetVelTps) <= LAUNCH_VEL_TOLERANCE_TPS
                && Math.abs(launchL.getVelocity() - targetVelTps) <= LAUNCH_VEL_TOLERANCE_TPS;
    }

    // -------------------------------------------------------------- safe stop
    /**
     * Safe-stop before the Driver Station regains control (G403/G404). Verified: plain
     * OpMode.stop() is the SDK's end-of-run hook and is overridable. Zero the motors directly —
     * no loop runs after this, so nothing else will. Servos are deliberately NOT re-commanded:
     * no new output keeps the robot motionless, which is what G404 requires after TELEOP.
     * Null-guarded because stop() also runs if init() threw part-way through.
     */
    @Override
    public void stop() {
        if (lf != null) { lf.setPower(0); lr.setPower(0); rf.setPower(0); rr.setPower(0); }
        if (intakeR != null) { intakeR.setPower(0); intakeL.setPower(0); }
        if (launchR != null) { launchR.setPower(0); launchL.setPower(0); }
    }
}
