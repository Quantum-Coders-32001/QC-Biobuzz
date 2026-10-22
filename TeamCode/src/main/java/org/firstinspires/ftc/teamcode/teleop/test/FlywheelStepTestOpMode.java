package org.firstinspires.ftc.teamcode.teleop.test;

import static org.firstinspires.ftc.teamcode.config.RobotConstants.*;

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.util.ElapsedTime;

import org.firstinspires.ftc.teamcode.subsystems.ShooterSubsystem;

/**
 * PHASE 2 of shooter tuning (AGENTS.md L3): step response with the REAL control path.
 *
 * This drives {@link ShooterSubsystem} itself (its PIDF object, RUN_WITHOUT_ENCODER, clamp01),
 * with a test override for the exact setpoint. Motor modes/gains come from RobotConstants, so
 * what you tune here is exactly what flies in a match. Iterate on LAUNCH_KP manually:
 * raise it until overshoot ≈ 4-5% of target, then back off ~35%. kI/kD stay 0 (both inspo
 * teams run feedforward + P only on flywheels; an "I" turns shot-load dips into windup).
 *
 * CONTROLS (bench only):
 *   dpad up/down : target velocity +/- 50 tps (floor 0)
 *   A            : run ONE step to the current target (2 s), print metrics + PASS/FAIL
 *   Y            : toggle the launchers ON/OFF (same as match toggle)
 *   B            : clear the override and exit override mode (defaults return)
 *
 * PASS (per step, PROPOSED for the user to confirm):
 *   overshoot <= 5% of target, steady error <= STEADY_ERR_PASS_TPS, settle <= SETTLE_PASS_S.
 */
@TeleOp(name = "Flywheel Step Test", group = "test")
public class FlywheelStepTestOpMode extends LinearOpMode {
    private static final double STEP_TPS = 50.0;
    private static final double STEP_HOLD_S = 2.0;      // each step measured for this long
    private static final double MIN_CLEAN_S = 0.15;     // ignore kick-off transient samples

    // proposed pass criteria (LAUNCH_VEL_TOLERANCE_TPS is the "at speed" telemetry band)
    private static final double OVERSHOOT_PASS_FRAC = 0.05;
    private static final double STEADY_ERR_PASS_TPS = 25.0;
    private static final double SETTLE_PASS_S = 0.4;

    @Override
    public void runOpMode() {
        ShooterSubsystem shooter = new ShooterSubsystem(hardwareMap);
        telemetry.addLine("Set a target with dpad, then A to run one step. Strap the flywheels.");
        telemetry.update();

        double target = 0.0;
        int stepCount = 0;

        waitForStart();

        boolean lastA = false, lastY = false, lastUp = false, lastDown = false, lastB = false;
        Step currentStep = null;

        while (!isStopRequested()) {
            // ---- controls (edge-detected so a held button triggers once) ----
            boolean up = gamepad1.dpad_up, down = gamepad1.dpad_down;
            boolean a = gamepad1.a, y = gamepad1.y, b = gamepad1.b;

            if (up && !lastUp) target += STEP_TPS;
            if (down && !lastDown) target = Math.max(0, target - STEP_TPS);
            if (y && !lastY) shooter.toggle();
            if (b && !lastB) {
                currentStep = null;
                shooter.setTestTargetOverrideTps(null); // back to normal sourcing
            } else if (a && !lastA && target > 0) {
                shooter.setTestTargetOverrideTps(target);
                currentStep = new Step();
            }
            lastUp = up; lastDown = down; lastA = a; lastY = y; lastB = b;

            // ---- same periodic path as match code ----
            shooter.periodic();

            // ---- step measurement ----
            double velNow = shooter.getVelRTps();
            if (currentStep != null) {
                double t = currentStep.timer.seconds();
                if (t < STEP_HOLD_S) {
                    currentStep.sample(t, target, velNow);
                } else {
                    report(currentStep, target, ++stepCount);
                    currentStep = null;
                    shooter.setTestTargetOverrideTps(null); // rest until the next A-step
                }
            }

            // ---- telemetry ----
            telemetry.addData("target", "%.0f tps (dpad +/-%.0f)", target, STEP_TPS);
            telemetry.addData("vel R/L", "%.0f / %.0f", shooter.getVelRTps(), shooter.getVelLTps());
            telemetry.addData("armed", shooter.isEnabled());
            telemetry.addData("override", shooter.isTargetOverridden() ? "on" : "off");
            telemetry.addData("atSpeed", shooter.atSpeed());
            telemetry.update();
        }

        shooter.setTestTargetOverrideTps(null);
        shooter.zeroOutputs(); // bench safe-stop
    }

    private static final double STEADY_WINDOW_S = 0.5; // steady-error averages the last 0.5 s

    /** Accumulates one step's metrics from sequential velocity readings. */
    private static final class Step {
        final ElapsedTime timer = new ElapsedTime();
        double maxVel = 0, errSumSteady = 0, samplesSteady = 0;
        Double settleAt = null;

        void sample(double t, double target, double vel) {
            if (t < MIN_CLEAN_S) return;
            maxVel = Math.max(maxVel, vel);
            if (t >= STEP_HOLD_S - STEADY_WINDOW_S) {
                errSumSteady += Math.abs(vel - target);
                samplesSteady++;
            }
            if (settleAt == null && Math.abs(vel - target) <= LAUNCH_VEL_TOLERANCE_TPS) {
                settleAt = t;
            }
        }
    }

    private void report(Step s, double target, int stepNumber) {
        double overshootFrac = target > 0 ? Math.max(0, s.maxVel - target) / target : 0;
        double steadyErr = s.samplesSteady > 0 ? s.errSumSteady / s.samplesSteady : Double.NaN;
        boolean pass = overshootFrac <= OVERSHOOT_PASS_FRAC
                && Double.isFinite(steadyErr) && steadyErr <= STEADY_ERR_PASS_TPS
                && s.settleAt != null && s.settleAt <= SETTLE_PASS_S;

        telemetry.addLine("--- STEP " + stepNumber + " (target " + (int) target + " tps) ---");
        telemetry.addData("overshoot", "%.1f%% (max %.0f)", overshootFrac * 100, s.maxVel);
        telemetry.addData("steadyErr", "%.0f tps", steadyErr);
        telemetry.addData("settleAt", s.settleAt == null ? "NEVER (band "
                + (int) LAUNCH_VEL_TOLERANCE_TPS + " tps)" : "%.2f s", s.settleAt);
        telemetry.addLine((pass ? "PASS" : "FAIL") + " — criteria: "
                + (int) (OVERSHOOT_PASS_FRAC * 100) + "% overshoot, "
                + (int) STEADY_ERR_PASS_TPS + " tps steady err, "
                + SETTLE_PASS_S + " s settle");
        telemetry.update();
    }
}
