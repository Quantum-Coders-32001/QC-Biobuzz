package org.firstinspires.ftc.teamcode.teleop.test;

import static org.firstinspires.ftc.teamcode.config.RobotConstants.*;

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.util.ElapsedTime;
import com.seattlesolvers.solverslib.controller.PIDFController;

import org.firstinspires.ftc.teamcode.teleop.StaticShooterTeleOp;

/**
 * PHASE 2 of shooter tuning (AGENTS.md L3): step response, using the SAME flywheel setup and
 * control law as the match teleop — {@link StaticShooterTeleOp#configureFlywheelMotor} and
 * {@link StaticShooterTeleOp#flywheelPower}. There is exactly one implementation of both; this
 * opmode cannot drift away from what flies in a match.
 *
 * It owns its two motors directly (no subsystem, nothing shared with the teleop's OpMode
 * instance), which is what makes it safe to run standalone on the bench.
 *
 * CONTROLS
 *   dpad up/down : target velocity +/- 50 tps (floor 0)
 *   A            : run ONE recorded step to the target (2 s), then print metrics + PASS/FAIL
 *   Y            : hold the target continuously (listen to it, watch steady-state error)
 *   B            : stop everything and clear the hold
 *
 * PASS criteria are PROPOSED and must be approved by the user (AGENTS.md §6).
 * The hood is NOT exercised here; the teleop drives it from HOOD_* / the shot table.
 *
 * BENCH SAFETY: strap the flywheels. Outputs only start after Play and are zeroed when the loop
 * ends (LinearOpMode.stop() is final, so there is no end hook to use).
 */
@TeleOp(name = "Flywheel Step Test", group = "test")
public class FlywheelStepTestOpMode extends LinearOpMode {
    private static final double STEP_TPS = 50.0;        // dpad increment
    private static final double STEP_HOLD_S = 2.0;      // each step is measured for this long
    private static final double MIN_CLEAN_S = 0.15;     // ignore the kick-off transient
    private static final double STEADY_WINDOW_S = 0.5;  // steady error averages the last 0.5 s
    private static final double REPORT_HOLD_S = 5.0;    // keep the last result on screen

    // proposed pass criteria (LAUNCH_VEL_TOLERANCE_TPS is the "at speed" telemetry band)
    private static final double OVERSHOOT_PASS_FRAC = 0.05;
    private static final double STEADY_ERR_PASS_TPS = 25.0;
    private static final double SETTLE_PASS_S = 0.4;

    private DcMotorEx launchR, launchL;
    private PIDFController pidR, pidL;

    private String[] lastReport;
    private double reportUntilS;

    @Override
    public void runOpMode() {
        launchR = hardwareMap.get(DcMotorEx.class, LAUNCH_R_NAME);
        launchL = hardwareMap.get(DcMotorEx.class, LAUNCH_L_NAME);
        StaticShooterTeleOp.configureFlywheelMotor(launchR, true);  // launchr is reversed, as in the match
        StaticShooterTeleOp.configureFlywheelMotor(launchL, false);
        resetPids();

        telemetry.addLine("Strap the flywheels. dpad=target, A=step, Y=hold, B=stop.");
        telemetry.update();

        waitForStart();

        double target = 0.0;
        boolean hold = false;
        int stepCount = 0;
        Step step = null;

        boolean lastA = false, lastY = false, lastB = false, lastUp = false, lastDown = false;

        while (!isStopRequested()) {
            boolean up = gamepad1.dpad_up, down = gamepad1.dpad_down;
            boolean a = gamepad1.a, y = gamepad1.y, b = gamepad1.b;

            if (up && !lastUp) target += STEP_TPS;
            if (down && !lastDown) target = Math.max(0, target - STEP_TPS);

            if (y && !lastY) {
                hold = !hold;
                if (hold) resetPids(); // fresh integrator, same as the teleop's re-arm
            }
            if (b && !lastB) {
                hold = false;
                step = null;
                target = 0.0;
            }
            if (a && !lastA && target > 0 && step == null) {
                resetPids();          // every step starts from the same controller state
                step = new Step(target);
            }
            lastUp = up; lastDown = down; lastA = a; lastY = y; lastB = b;

            // ---- exactly the match control path ----
            double commanded = step != null ? step.targetTps : (hold ? target : 0.0);
            if (commanded > 0) {
                launchR.setPower(StaticShooterTeleOp.flywheelPower(pidR, launchR.getVelocity(), commanded));
                launchL.setPower(StaticShooterTeleOp.flywheelPower(pidL, launchL.getVelocity(), commanded));
            } else {
                launchR.setPower(0);
                launchL.setPower(0);
            }

            // ---- measurement ----
            if (step != null) {
                double t = step.timer.seconds();
                if (t < STEP_HOLD_S) {
                    step.sample(t, launchR.getVelocity());
                } else {
                    report(step, ++stepCount);
                    step = null;
                }
            }

            // ---- telemetry ----
            telemetry.addData("target", "%.0f tps (dpad +/-%.0f)", target, STEP_TPS);
            telemetry.addData("commanded", "%.0f tps", commanded);
            telemetry.addData("vel R/L", "%.0f / %.0f", launchR.getVelocity(), launchL.getVelocity());
            telemetry.addData("hold", hold ? "ON" : "OFF");
            if (commanded > 0) {
                telemetry.addData("live error", "%.0f tps", commanded - launchR.getVelocity());
            }
            if (step != null) {
                telemetry.addData("stepping", "%.2f / %.1f s", step.timer.seconds(), STEP_HOLD_S);
            }
            if (lastReport != null && getRuntime() < reportUntilS) {
                for (String line : lastReport) telemetry.addLine(line);
            }
            telemetry.update();
        }

        zero(); // bench safe-stop
    }

    private void resetPids() {
        pidR = new PIDFController(LAUNCH_KP, LAUNCH_KI, LAUNCH_KD, LAUNCH_KF);
        pidL = new PIDFController(LAUNCH_KP, LAUNCH_KI, LAUNCH_KD, LAUNCH_KF);
    }

    private void zero() {
        if (launchR != null) launchR.setPower(0);
        if (launchL != null) launchL.setPower(0);
    }

    private void report(Step s, int stepNumber) {
        double overshootFrac = Math.max(0.0, s.maxVel - s.targetTps) / s.targetTps;
        double steadyErr = s.samplesSteady > 0 ? s.errSumSteady / s.samplesSteady : Double.NaN;
        boolean pass = overshootFrac <= OVERSHOOT_PASS_FRAC
                && Double.isFinite(steadyErr) && steadyErr <= STEADY_ERR_PASS_TPS
                && s.settleAt != null && s.settleAt <= SETTLE_PASS_S;

        lastReport = new String[]{
                "--- STEP " + stepNumber + " (target " + (int) s.targetTps + " tps) ---",
                String.format("overshoot %.1f%% (max %.0f tps)", overshootFrac * 100, s.maxVel),
                String.format("steadyErr %.0f tps", steadyErr),
                s.settleAt == null
                        ? String.format("settle NEVER (band +/-%.0f tps)", LAUNCH_VEL_TOLERANCE_TPS)
                        : String.format("settle %.2f s", s.settleAt),
                (pass ? "PASS" : "FAIL") + String.format(" — criteria: %.0f%% overshoot, %.0f tps steady err, %.1f s settle",
                        OVERSHOOT_PASS_FRAC * 100, STEADY_ERR_PASS_TPS, SETTLE_PASS_S)
        };
        reportUntilS = getRuntime() + REPORT_HOLD_S;
    }

    /** One recorded step: peak velocity, steady error over the last window, first settle time. */
    private static final class Step {
        final double targetTps;
        final ElapsedTime timer = new ElapsedTime();
        double maxVel;
        double errSumSteady;
        int samplesSteady;
        Double settleAt;

        Step(double targetTps) {
            this.targetTps = targetTps;
        }

        void sample(double t, double vel) {
            if (t < MIN_CLEAN_S) return;
            maxVel = Math.max(maxVel, vel);
            if (t >= STEP_HOLD_S - STEADY_WINDOW_S) {
                errSumSteady += Math.abs(vel - targetTps);
                samplesSteady++;
            }
            if (settleAt == null && Math.abs(vel - targetTps) <= LAUNCH_VEL_TOLERANCE_TPS) {
                settleAt = t;
            }
        }
    }
}
