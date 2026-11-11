package org.firstinspires.ftc.teamcode.tuners;

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
import com.qualcomm.robotcore.hardware.Servo;
import com.seattlesolvers.solverslib.controller.PIDFController;

import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit;
import org.firstinspires.ftc.robotcore.external.navigation.Position;
import org.firstinspires.ftc.robotcore.external.navigation.Pose3D;
import org.firstinspires.ftc.teamcode.subsystems.HiveAim;
import org.firstinspires.ftc.teamcode.subsystems.ShotMap;
import org.firstinspires.ftc.teamcode.subsystems.ShotRegression;
import org.firstinspires.ftc.teamcode.subsystems.ShotSequence;
import org.firstinspires.ftc.teamcode.teleop.StaticShooterTeleOp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Collects the shot samples the distance -> (velocity, hood) regression is fitted from, then prints
 * the constant arrays to paste into {@link org.firstinspires.ftc.teamcode.config.RobotConstants}.
 *
 * <p>Workflow, one distance at a time:
 * <ol>
 *   <li>Park the robot at a real shooting spot and point it at your HIVE.</li>
 *   <li>Aim the hood with dpad left/right, then drag the target speed with dpad up/down until a shot
 *       goes in.</li>
 *   <li>Hold RB to fire. This is the match's own {@link ShotSequence}, not a copy of it, so the gate
 *       timing and transfer power are identical to the shot you will actually take in a match.</li>
 *   <li>Press LB. It records (Limelight range, achieved velocity, hood position).</li>
 *   <li>Move to the next distance and repeat. Six to eight points across the range you will
 *       actually shoot from is plenty.</li>
 * </ol>
 *
 * <p>RECORDING IS REFUSED, with the reason on screen, when:
 * <ul>
 *   <li>the flywheel is not within {@code LAUNCH_VEL_TOLERANCE_TPS} of the target - a shot taken
 *       mid-spin-up left the barrel at a speed this tool cannot name;</li>
 *   <li>the target changed less than {@link #SETTLE_MS} ago - the wheel is still accelerating;</li>
 *   <li>no CELL tag has been decoded in the last {@link #RANGE_FRESH_MS}, or the range is outside
 *       {@link #MIN_DIST_IN}..{@link #MAX_DIST_IN}.</li>
 * </ul>
 * A stale range is the dangerous case: it is finite, plausible, and wrong, and it silently corrupts
 * the dataset. Refusing is cheaper than discovering it in a match.
 *
 * <p>Requires a tuned flywheel first: with {@code LAUNCH_K*} all 0 the wheel never reaches target and
 * nothing can be recorded. Run {@link PidfTuner} before this OpMode. That ordering is real, not a
 * formality - the regression is only as good as the speed loop underneath it.
 *
 * <p>UNVERIFIED: no robot and no field. The maths is unit-tested off-robot; the hardware paths are not.
 */
@TeleOp(name = "Shot Map Tuner", group = "tuners")
public class ShotMapTuner extends LinearOpMode {

    /** Live tuning values. Dashboard-editable; only this tuner reads them. */
    @Config
    public static class G {
        /** Flywheel setpoint in ticks/sec. 0 = stopped. */
        public static double targetTps = 0.0;
        /**
         * Hood servo position. 0.5 is NOT a measured value - it is an arbitrary mid-travel place to
         * start from on the first run of a new robot. Replace it with your measured stow position.
         */
        public static double hoodPos = 0.5;
        public static double velStepTps = 50.0;
        public static double hoodStep = 0.005;
    }

    /** A range older than this is treated as "no range" (Limelight polls at 100 Hz). */
    private static final long RANGE_FRESH_MS = 250;
    /** After the target changes, wait this long before the wheel speed means anything. */
    private static final long SETTLE_MS = 800;
    /** Sanity window for a recorded distance, in inches. A CELL is inside the field; nothing else is. */
    private static final double MIN_DIST_IN = 20.0;
    private static final double MAX_DIST_IN = 200.0;

    private static final class Point {
        final double distIn;
        final double velTps;
        final double hoodPos;

        Point(double distIn, double velTps, double hoodPos) {
            this.distIn = distIn;
            this.velTps = velTps;
            this.hoodPos = hoodPos;
        }
    }

    private DcMotorEx shooter;
    private PIDFController pidf;
    private Servo hoodServo;
    private DcMotor transferL, transferR; // lt/rt: the ball path
    private DcMotor roll;                 // constant 0.4, same as the match
    private ShotSequence shotSeq;
    private Limelight3A limelight;

    private final List<Point> points = new ArrayList<>();
    private ShotMap map = ShotMap.empty();

    private long lastTargetChangeNanos;
    private long lastRangeNanos;
    private double lastRangeIn = Double.NaN;
    private String status = "idle";

    // edge detection
    private boolean lastUp, lastDown, lastLeft, lastRight, lastRecord, lastUndo, lastClear;
    private double lastSeenTarget = Double.NaN;

    @Override
    public void runOpMode() {
        telemetry = new MultipleTelemetry(telemetry, FtcDashboard.getInstance().getTelemetry());

        shooter = hardwareMap.get(DcMotorEx.class, SHOOTER_NAME);
        StaticShooterTeleOp.configureFlywheelMotor(shooter, false); // identical to the match
        pidf = new PIDFController(LAUNCH_KP, LAUNCH_KI, LAUNCH_KD, LAUNCH_KF);

        DcMotor transferL = hardwareMap.get(DcMotor.class, TRANSFER_L_NAME);
        DcMotor transferR = hardwareMap.get(DcMotor.class, TRANSFER_R_NAME);
        this.transferL = transferL;
        this.transferR = transferR;
        roll = optionalMotor(ROLL_NAME);
        Servo gate = hardwareMap.get(Servo.class, GATE_NAME);
        shotSeq = new ShotSequence(gate, transferL, transferR, GATE_REST_POS, GATE_FIRE_POS);

        hoodServo = optionalServo(HOOD_NAME);

        limelight = optionalLimelight();
        if (limelight != null) {
            limelight.setPollRateHz(100);
            limelight.start();
        }

        lastTargetChangeNanos = System.nanoTime();

        telemetry.addLine("RB fire | A transfer | B reverse | dpad = vel/hood | LB record | X undo | Y clear");
        if (limelight == null) telemetry.addLine("WARN: no Limelight, nothing can be recorded");
        if (hoodServo == null) telemetry.addLine("WARN: no hood servo, hood samples will be NaN");
        if (LAUNCH_KP == 0 && LAUNCH_KI == 0 && LAUNCH_KD == 0 && LAUNCH_KF == 0) {
            telemetry.addLine("WARN: LAUNCH_K* are all 0 - run PidfTuner first, nothing will reach target");
        }
        telemetry.update();

        waitForStart();
        if (isStopRequested()) {
            stopEverything();
            return;
        }

        while (!isStopRequested()) {
            long now = System.nanoTime();

            handleButtons(now);
            updateTransfer();
            shotSeq.update(gamepad1.right_bumper);
            updateFlywheel(now);
            updateHood();
            updateRange();

            pushTelemetry(now);
            telemetry.update();
            sleep(20);
        }
        stopEverything();
    }

    // ------------------------------------------------------------------ inputs
    private void handleButtons(long now) {
        boolean up = gamepad1.dpad_up, down = gamepad1.dpad_down;
        boolean left = gamepad1.dpad_left, right = gamepad1.dpad_right;

        if (up && !lastUp) changeTarget(G.targetTps + G.velStepTps);
        if (down && !lastDown) changeTarget(Math.max(0.0, G.targetTps - G.velStepTps));
        if (left && !lastLeft) changeHood(G.hoodPos - G.hoodStep);
        if (right && !lastRight) changeHood(G.hoodPos + G.hoodStep);

        // LB records. RB is the fire trigger and is read by shotSeq, so it must stay untouched here.
        boolean record = gamepad1.left_bumper;
        if (record && !lastRecord) record(now);
        lastRecord = record;

        boolean undo = gamepad1.x;
        if (undo && !lastUndo) undo();
        lastUndo = undo;

        boolean clear = gamepad1.y;
        if (clear && !lastClear) clearPoints();
        lastClear = clear;

        lastUp = up;
        lastDown = down;
        lastLeft = left;
        lastRight = right;
    }

    private void changeTarget(double tps) {
        G.targetTps = Math.max(0.0, tps);
    }

    private void changeHood(double pos) {
        G.hoodPos = Math.max(0.0, Math.min(1.0, pos));
    }

    // ----------------------------------------------------------------- outputs
    /** A / B drive the ball path (lt + rt). The roller is not a button - it is always at 0.4. */
    private void updateTransfer() {
        double power = gamepad1.a ? TRANSFER_POWER : gamepad1.b ? -TRANSFER_POWER : 0.0;
        transferL.setPower(power);
        transferR.setPower(power);
        if (roll != null) roll.setPower(ROLL_POWER);
    }

    private void updateFlywheel(long now) {
        // Catches a dashboard edit as well as a dpad press, so the settle gate cannot be bypassed by
        // typing a new target into the Configure tab.
        if (G.targetTps != lastSeenTarget) {
            lastSeenTarget = G.targetTps;
            lastTargetChangeNanos = now;
        }
        if (G.targetTps > 0) {
            shooter.setPower(StaticShooterTeleOp.flywheelPower(
                    pidf, shooter.getVelocity(), G.targetTps));
        } else {
            shooter.setPower(0);
        }
    }

    private void updateHood() {
        if (hoodServo != null) hoodServo.setPosition(G.hoodPos);
    }

    private void updateRange() {
        double r = readRangeIn();
        if (Double.isFinite(r)) {
            lastRangeIn = r;
            lastRangeNanos = System.nanoTime();
        }
    }

    // --------------------------------------------------------------- recording
    private void record(long now) {
        if (!rangeFresh(now)) {
            status = "REFUSED: no fresh CELL tag (range " + fmt(lastRangeIn, "%.1f") + ")";
            return;
        }
        if (!atSpeed()) {
            status = String.format("REFUSED: not at speed (%.0f vs %.0f tps)",
                    shooter.getVelocity(), G.targetTps);
            return;
        }
        if (now - lastTargetChangeNanos < SETTLE_MS * 1_000_000L) {
            status = "REFUSED: target changed " + ((now - lastTargetChangeNanos) / 1_000_000L)
                    + " ms ago, wheel still settling";
            return;
        }
        if (lastRangeIn < MIN_DIST_IN || lastRangeIn > MAX_DIST_IN) {
            status = String.format("REFUSED: range %.1f in outside %.0f-%.0f",
                    lastRangeIn, MIN_DIST_IN, MAX_DIST_IN);
            return;
        }

        // Achieved speed, not the setpoint: this is the speed the ball actually left at.
        points.add(new Point(lastRangeIn, shooter.getVelocity(), currentHood()));
        rebuild();
        status = String.format("RECORDED %.1f in @ %.0f tps, hood %.3f",
                lastRangeIn, shooter.getVelocity(), currentHood());
    }

    private void undo() {
        if (points.isEmpty()) {
            status = "nothing to undo";
            return;
        }
        points.remove(points.size() - 1);
        rebuild();
        status = "undid last point (" + points.size() + " left)";
    }

    private void clearPoints() {
        points.clear();
        rebuild();
        status = "cleared";
    }

    private void rebuild() {
        map = ShotMap.fit(distances(), velocities(), hoods(), SHOT_FIT_MAX_DEGREE);
    }

    private double currentHood() {
        return hoodServo == null ? Double.NaN : G.hoodPos;
    }

    private boolean atSpeed() {
        return G.targetTps > 0
                && Math.abs(shooter.getVelocity() - G.targetTps) <= LAUNCH_VEL_TOLERANCE_TPS;
    }

    private boolean rangeFresh(long now) {
        return Double.isFinite(lastRangeIn)
                && (now - lastRangeNanos) <= RANGE_FRESH_MS * 1_000_000L;
    }

    // -------------------------------------------------------------- hardware io
    /** Median horizontal range to every CELL tag in view, or NaN. */
    private double readRangeIn() {
        if (limelight == null) return Double.NaN;
        LLResult result = limelight.getLatestResult();
        if (result == null || !result.isValid()) return Double.NaN;
        List<LLResultTypes.FiducialResult> fiducials = result.getFiducialResults();
        if (fiducials == null || fiducials.isEmpty()) return Double.NaN;

        double[] ranges = new double[fiducials.size()];
        int k = 0;
        for (LLResultTypes.FiducialResult fiducial : fiducials) {
            if (!isCellTag(fiducial.getFiducialId())) continue;
            double r = rangeOf(fiducial);
            if (Double.isFinite(r)) ranges[k++] = r;
        }
        return HiveAim.median(Arrays.copyOf(ranges, k));
    }

    private static double rangeOf(LLResultTypes.FiducialResult fiducial) {
        try {
            Pose3D pose = fiducial.getTargetPoseCameraSpace();
            if (pose == null) return Double.NaN;
            Position p = pose.getPosition().toUnit(DistanceUnit.INCH);
            return HiveAim.horizontalDistanceIn(p.x, p.y, p.z, CAMERA_PITCH_UP_DEG);
        } catch (RuntimeException e) {
            return Double.NaN;
        }
    }

    private static boolean isCellTag(int id) {
        for (int t : RED_TAG_IDS) if (t == id) return true;
        for (int t : BLUE_TAG_IDS) if (t == id) return true;
        return false;
    }

    private Servo optionalServo(String name) {
        try {
            return hardwareMap.get(Servo.class, name);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private DcMotor optionalMotor(String name) {
        try {
            return hardwareMap.get(DcMotor.class, name);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private Limelight3A optionalLimelight() {
        try {
            return hardwareMap.get(Limelight3A.class, LIMELIGHT_NAME);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void stopEverything() {
        if (shooter != null) shooter.setPower(0);
        if (transferL != null) { transferL.setPower(0); transferR.setPower(0); }
        if (roll != null) roll.setPower(0);
        if (limelight != null) limelight.stop();
    }

    // --------------------------------------------------------------- telemetry
    private void pushTelemetry(long now) {
        telemetry.addData("target / actual", "%.0f / %.0f tps", G.targetTps, shooter.getVelocity());
        telemetry.addData("at speed", atSpeed());
        telemetry.addData("hood", hoodServo == null ? "NO SERVO" : String.format("%.3f", G.hoodPos));
        telemetry.addData("range", rangeFresh(now)
                ? String.format("%.1f in", lastRangeIn)
                : "STALE " + fmt(lastRangeIn, "%.1f"));
        telemetry.addData("status", status);

        telemetry.addLine("---- points ----");
        if (points.isEmpty()) {
            telemetry.addLine("(none)");
        } else {
            for (Point p : points) {
                telemetry.addLine(String.format("%.1f in  %.0f tps  hood %.3f",
                        p.distIn, p.velTps, p.hoodPos));
            }
        }

        telemetry.addLine("---- fit ----");
        addFit("velocity", map.velocityFit(), "tps", "%.1f");
        addFit("hood", map.hoodFit(), "pos", "%.4f");

        if (points.size() > 0 && points.size() < 4 && SHOT_FIT_MAX_DEGREE >= 2) {
            telemetry.addLine("note: a line is used until you have 4 points (a 3-point parabola is meaningless)");
        }
        if (points.size() >= 2) {
            telemetry.addLine("---- paste into RobotConstants ----");
            telemetry.addLine(java("SHOT_DISTANCES_IN", distances(), "%.1f"));
            telemetry.addLine(java("SHOT_VELOCITIES_TPS", velocities(), "%.0f"));
            telemetry.addLine(java("SHOT_HOOD_POS", hoods(), "%.3f"));
        }
    }

    private void addFit(String label, ShotRegression fit, String unit, String fmt) {
        if (fit == null) {
            telemetry.addData(label, "not fitted");
            return;
        }
        telemetry.addData(label + "(d)", fit.equation("d"));
        telemetry.addData(label + " quality", "deg %d, R2 %.3f, worst %.2f %s, band %.0f-%.0f in",
                fit.degree(), fit.rSquared(), fit.maxAbsResidual(), unit, fit.xMin(), fit.xMax());
    }

    // ------------------------------------------------------------------ arrays
    private double[] distances() {
        double[] a = new double[points.size()];
        for (int i = 0; i < a.length; i++) a[i] = points.get(i).distIn;
        return a;
    }

    private double[] velocities() {
        double[] a = new double[points.size()];
        for (int i = 0; i < a.length; i++) a[i] = points.get(i).velTps;
        return a;
    }

    private double[] hoods() {
        double[] a = new double[points.size()];
        for (int i = 0; i < a.length; i++) a[i] = points.get(i).hoodPos;
        return a;
    }

    private static String java(String name, double[] values, String fmt) {
        StringBuilder sb = new StringBuilder("public static final double[] ")
                .append(name).append(" = {");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(String.format(fmt, values[i]));
        }
        return sb.append("};").toString();
    }

    private static String fmt(double v, String pattern) {
        return Double.isFinite(v) ? String.format(pattern, v) : "none";
    }
}
