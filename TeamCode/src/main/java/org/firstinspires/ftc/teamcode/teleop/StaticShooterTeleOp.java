package org.firstinspires.ftc.teamcode.teleop;

import static org.firstinspires.ftc.teamcode.config.RobotConstants.*;

import com.qualcomm.hardware.lynx.LynxModule;
import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.DcMotorSimple;
import com.qualcomm.robotcore.hardware.Gamepad;
import com.qualcomm.robotcore.hardware.Servo;
import com.seattlesolvers.solverslib.controller.PIDFController;

import org.firstinspires.ftc.teamcode.subsystems.ShotMap;
import org.firstinspires.ftc.teamcode.subsystems.ShotSequence;

import java.util.List;

/**
 * Static-shooter teleop. ONE FILE on purpose: no subsystem classes, no commands, no GamepadEx.
 * Everything the robot does in a match is here, in the order it happens each loop.
 *
 * <p>Hardware names come from {@code ivyCommands.java}, which is the config the auto also uses:
 * {@code shooter} (one flywheel), {@code lt}/{@code rt} (the ball path), {@code gate} (the servo
 * the old teleop called "blocker"), {@code roll} (a constant-speed roller, see below).
 *
 * <p>THE BALL PATH IS {@code lt}/{@code rt} AND NOTHING ELSE. The motor the team calls
 * "notshooter" (hardware name {@code roll}) is not the intake and is not the transfer: it runs at a
 * constant {@link #ROLL_POWER} and this code never pulses it, reverses it, or stops it mid-match.
 * Do not add it to A/X again - that was a bug, corrected 2026-11-10.
 *
 * Controls (on {@link #mechanismPad()}; gamepad1 here, gamepad2 in the auto-aim teleops):
 *   A (hold)      lt + rt forward at TRANSFER_POWER (1.0)
 *   X (hold)      lt + rt reverse at TRANSFER_POWER (clear a jam)
 *   B             toggle flower intake
 *   Y             toggle shooter (ON at start)
 *   Right bumper  HOLD = shoot sequence. Gate to {@link #GATE_FIRE_POS} (0.55) and both transfers
 *                 at full power; releasing restores the gate to {@link #GATE_REST_POS} (0.75).
 *
 * Extension seams (used by the auto-aim teleops, no behaviour change here):
 *   {@link #beforeLoop()}   runs after the bulk cache is cleared, before anything reads a sensor
 *   {@link #driverTurn()}   the turn component of the drive mix
 *   {@link #mechanismPad()}  which gamepad owns the mechanism buttons
 *   {@link #extraTelemetry()}  add lines after the standard ones
 *   {@link #distanceToGoalIn}  write a finite number and the shot table starts being used
 *
 * WHY plain OpMode, not LinearOpMode: {@link OpMode#stop()} is overridable (verified in RobotCore
 * 12.0.0 bytecode, and internalRunOpMode() calls it after the last loop), while LinearOpMode.stop()
 * is final. stop() is where the G403/G404 safe-stop lives. Also verified in the same bytecode:
 * the SDK calls telemetry.update() after every user callback, so loop() does not call it.
 *
 * UNVERIFIED: not run on a robot.
 */
@TeleOp(name = "Static Shooter TeleOp", group = "TeleOp")
public class StaticShooterTeleOp extends OpMode {
    // ---- hardware ----
    private List<LynxModule> hubs;
    private DcMotorEx lf, lr, rf, rr;   // drive
    private DcMotor roll;              // "roll" (the team's "notshooter"): constant 0.4, not the ball path
    private DcMotor transferL, transferR; // "lt" / "rt" - the whole ball path
    private DcMotorEx shooter;         // one flywheel motor
    private ShotSequence shotSeq;      // gate + both transfers; the same class the tuner fires with
    private Servo hood, flowerL, flowerR; // optional: null when not in the DS config

    // ---- shooter ----
    private PIDFController pidf;
    private ShotMap shotMap;           // inert when the regression arrays are empty
    private boolean hoodConfigured;
    /**
     * Horizontal distance to the aim point in inches. NaN = unknown, and every consumer treats NaN
     * as "not configured": {@link #updateShooter()} falls back to DEFAULT_LAUNCH_VEL_TPS. The
     * auto-aim teleops write this from vision; nothing else does.
     */
    protected double distanceToGoalIn = Double.NaN;
    private boolean shooterOn = true; // ON at the start of teleop
    private boolean autoAiming;
    private double targetVelTps;
    private double hoodPos = Double.NaN;

    // ---- other mechanism state ----
    private boolean flowerDeployed;

    // ---- edge detection (a held button must act once) ----
    private boolean lastY, lastB;

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

        // ---- ball path (lt/rt) + the constant-speed roller ----
        transferL = hardwareMap.get(DcMotor.class, TRANSFER_L_NAME);
        transferR = hardwareMap.get(DcMotor.class, TRANSFER_R_NAME);
        roll = optionalMotor(ROLL_NAME);

        // ---- flywheel ----
        shooter = hardwareMap.get(DcMotorEx.class, SHOOTER_NAME);
        configureFlywheelMotor(shooter, false);

        // ---- servos ----
        Servo gate = hardwareMap.get(Servo.class, GATE_NAME);
        shotSeq = new ShotSequence(gate, transferL, transferR, GATE_REST_POS, GATE_FIRE_POS);
        hood = optionalServo(HOOD_NAME);
        flowerL = optionalServo(FLOWER_L_NAME);
        flowerR = optionalServo(FLOWER_R_NAME);
        if (flowerR != null) flowerR.setDirection(Servo.Direction.REVERSE);
        // Nothing is commanded here: the robot must not move after INIT (G304/G403). The gate gets
        // its rest position on the first loop after Play, the flowers on the first B press.

        hoodConfigured = hood != null
                && !Double.isNaN(HOOD_MIN_POS) && !Double.isNaN(HOOD_MAX_POS)
                && !Double.isNaN(HOOD_DEFAULT_POS) && HOOD_MIN_POS <= HOOD_MAX_POS;

        // Never throws: a half-filled constant array degrades to "no auto aim" rather than killing
        // the OpMode at INIT with the robot already on the field.
        shotMap = ShotMap.fit(SHOT_DISTANCES_IN, SHOT_VELOCITIES_TPS, SHOT_HOOD_POS,
                SHOT_FIT_MAX_DEGREE);

        resetPid();
    }

    /** A servo that may not be in the Driver Station config. Null means "not fitted", not "broken". */
    private Servo optionalServo(String name) {
        try {
            return hardwareMap.get(Servo.class, name);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * A motor that may not be in the Driver Station config. Used for the roller, so a wrong device
     * name cannot turn into a dead OpMode with the robot already on the field.
     */
    private DcMotor optionalMotor(String name) {
        try {
            return hardwareMap.get(DcMotor.class, name);
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Override
    public void loop() {
        for (LynxModule hub : hubs) hub.clearBulkCache();

        beforeLoop();
        updateDrive();
        updateTransfer();
        updateShoot();   // after updateTransfer: the shoot sequence overrides lt/rt
        updateRoll();
        updateFlower();
        updateShooterToggle();
        updateShooter();
        pushTelemetry();
    }

    // ------------------------------------------------------------------ hooks
    /** Runs once per loop after the bulk cache is cleared, before any subsystem reads. */
    protected void beforeLoop() {}

    /** Turn component of the drive mix. Subclasses override to steer the chassis themselves. */
    protected double driverTurn() {
        return gamepad1.right_stick_x;
    }

    /** The gamepad that owns intake/flower/launcher/gate. gamepad1 unless a subclass says otherwise. */
    protected Gamepad mechanismPad() {
        return gamepad1;
    }

    /** Extra telemetry lines; called last so subclasses cannot bury the safety warnings. */
    protected void extraTelemetry() {}

    // ------------------------------------------------------------------ drive
    private void updateDrive() {
        double forward = -gamepad1.left_stick_y; // raw sticks: no ambiguity about GamepadEx's Y sign
        double strafe = gamepad1.left_stick_x;
        double turn = driverTurn();

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

    // ------------------------------------------------------------- ball path
    /**
     * A / X drive {@code lt} and {@code rt} and nothing else. Both at {@link #TRANSFER_POWER}.
     * A wins if both are held, which is the safer of the two when a ball is jammed.
     */
    private void updateTransfer() {
        Gamepad pad = mechanismPad();
        double power = pad.a ? TRANSFER_POWER
                : pad.x ? -TRANSFER_POWER
                : 0.0;
        transferL.setPower(power);
        transferR.setPower(power);
    }

    /** The "notshooter" roller: constant power for the whole match, never driven by a button. */
    private void updateRoll() {
        if (roll != null) roll.setPower(ROLL_POWER);
    }

    // ------------------------------------------------------------------ shoot
    /**
     * The shoot sequence, held rather than timed. The actuator work lives in {@link ShotSequence}
     * because {@code ShotMapTuner} fires through the same class: a shot recorded with different
     * gate timing would not be the shot the match runs, and the fitted constants would describe a
     * mechanism that never exists on the field.
     */
    private void updateShoot() {
        shotSeq.update(mechanismPad().right_bumper);
    }

    // ----------------------------------------------------------------- flower
    private void updateFlower() {
        boolean b = mechanismPad().b;
        if (b && !lastB && flowerL != null && flowerR != null) {
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
        boolean y = mechanismPad().y;
        if (y && !lastY) {
            shooterOn = !shooterOn;
            if (shooterOn) resetPid(); // fresh integrator on every spin-up
        }
        lastY = y;
    }

    private void updateShooter() {
        // The fit is asked even when there is no distance: predict(NaN) is NaN, and everything below
        // treats NaN as "not configured". When the arrays are empty the fit is null and this is
        // exactly the old default-velocity behaviour.
        double fittedVel = shotMap.velocityTps(distanceToGoalIn);
        autoAiming = Double.isFinite(fittedVel) && fittedVel > 0;
        targetVelTps = autoAiming ? fittedVel : DEFAULT_LAUNCH_VEL_TPS;

        if (hoodConfigured) {
            double pos = autoAiming ? shotMap.hoodPos(distanceToGoalIn) : HOOD_DEFAULT_POS;
            if (!Double.isFinite(pos)) pos = HOOD_DEFAULT_POS; // hood law not fitted yet
            hoodPos = Math.max(HOOD_MIN_POS, Math.min(HOOD_MAX_POS, pos));
            hood.setPosition(hoodPos);
        }

        if (shooterOn && targetVelTps > 0) {
            shooter.setPower(flywheelPower(pidf, shooter.getVelocity(), targetVelTps));
        } else {
            shooter.setPower(0);
        }
    }

    private void resetPid() {
        pidf = new PIDFController(LAUNCH_KP, LAUNCH_KI, LAUNCH_KD, LAUNCH_KF);
    }

    // ------------------------------------------------- shared with the bench test
    /**
     * Flywheel motor setup for the match teleop AND the bench tuner. One place, because a
     * direction/mode mismatch between bench and match silently invalidates every tuning number.
     * (STOP_AND_RESET_ENCODER is only about a known zero; control is velocity-based.)
     */
    public static void configureFlywheelMotor(DcMotorEx motor, boolean reversed) {
        motor.setDirection(reversed ? DcMotorSimple.Direction.REVERSE : DcMotorSimple.Direction.FORWARD);
        motor.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
        motor.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER); // the PIDF is done in software
        motor.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.FLOAT);
    }

    /**
     * The one flywheel control law: PIDF on measured vs target velocity, clamped to [0,1]
     * because a flywheel is never driven backwards to brake. Called by the match loop and by
     * {@link org.firstinspires.ftc.teamcode.tuners.PidfTuner} — do not copy this expression
     * anywhere else.
     *
     * <p>SolversLib's PIDFController takes (measured, setpoint) in that order, and kF multiplies
     * the setpoint. Verified against the 0.3.6 docs; swapping the arguments silently ruins tuning.
     */
    public static double flywheelPower(PIDFController pid, double measuredTps, double targetTps) {
        double out = pid.calculate(measuredTps, targetTps);
        return Double.isNaN(out) ? 0.0 : Math.max(0.0, Math.min(1.0, out));
    }

    // -------------------------------------------------------------- telemetry
    private void pushTelemetry() {
        telemetry.addData("Shooter", shooterOn ? "ON" : "OFF");
        telemetry.addData("Aim mode", autoAiming ? "AUTO (distance)"
                : shotMap.isUsable() ? "DEFAULT (no distance)" : "DEFAULT (no shot data)");
        telemetry.addData("Target vel (tps)", "%.0f", targetVelTps);
        telemetry.addData("Vel (tps)", "%.0f", shooter.getVelocity());
        telemetry.addData("At speed", atSpeed());
        telemetry.addData("Hood", hoodConfigured ? String.format("%.3f", hoodPos)
                : (hood == null ? "NOT IN CONFIG" : "NOT CONFIGURED"));
        telemetry.addData("Gate", shotSeq.isFiring() ? String.format("FIRING %.2f", GATE_FIRE_POS)
                : String.format("REST %.2f", GATE_REST_POS));
        telemetry.addData("Transfer", shotSeq.isFiring() ? "FIRE 1.0"
                : (mechanismPad().a ? "IN 1.0" : mechanismPad().x ? "REV -1.0" : "0"));
        telemetry.addData("Roller", roll == null ? "NOT IN CONFIG (" + ROLL_NAME + ")"
                : String.format("%.2f", ROLL_POWER));
        telemetry.addData("Flower", (flowerL == null || flowerR == null) ? "NOT IN CONFIG"
                : (flowerDeployed ? "DEPLOYED" : "STOWED/UNKNOWN"));
        telemetry.addData("Shot fit", shotMap.isUsable()
                ? String.format("%d pts, v(d)=%s, hood(d)=%s", shotMap.pointCount(),
                shotMap.velocityFit().equation("d"),
                shotMap.hasHood() ? shotMap.hoodFit().equation("d") : "NONE")
                : "EMPTY (run ShotMapTuner)");
        if (LAUNCH_KP == 0 && LAUNCH_KI == 0 && LAUNCH_KD == 0 && LAUNCH_KF == 0) {
            telemetry.addLine("WARN: shooter PIDF gains are all 0 (untuned)");
        }
        if (DEFAULT_LAUNCH_VEL_TPS <= 0) {
            telemetry.addLine("WARN: DEFAULT_LAUNCH_VEL_TPS is 0, the shooter will not spin");
        }
        extraTelemetry();
    }

    private boolean atSpeed() {
        return shooterOn && targetVelTps > 0
                && Math.abs(shooter.getVelocity() - targetVelTps) <= LAUNCH_VEL_TOLERANCE_TPS;
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
        if (transferL != null) { transferL.setPower(0); transferR.setPower(0); }
        if (roll != null) roll.setPower(0); // G404: the roller must not keep spinning after TELEOP
        if (shooter != null) shooter.setPower(0);
    }
}
