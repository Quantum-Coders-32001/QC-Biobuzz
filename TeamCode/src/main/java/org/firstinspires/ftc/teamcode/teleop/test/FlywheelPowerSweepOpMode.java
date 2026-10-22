package org.firstinspires.ftc.teamcode.teleop.test;

import static org.firstinspires.ftc.teamcode.config.RobotConstants.*;

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorEx;
import com.qualcomm.robotcore.hardware.DcMotorSimple;
import com.qualcomm.robotcore.hardware.VoltageSensor;
import com.qualcomm.robotcore.util.ElapsedTime;

import org.firstinspires.ftc.teamcode.math.LinearFit;

import java.util.ArrayList;
import java.util.List;

/**
 * PHASE 1 of shooter tuning (AGENTS.md L3): open-loop power sweep -> kV fit.
 *
 * Holds each power in SWEEP_POWERS until velocity settles (or TIMES_OUT), rests, and records
 * (P, V_settled). At the end it least-squares fits P = kV * V + kS over the fastest half of the
 * settled points (the operating band, where the P(V) relation is linear; below that, stiction
 * bends the curve). LAUNCH_KF gets the slope kV — SolversLib's kF multiplies the SETPOINT
 * (verified), so kF has the same units as this kV: power per ticks/sec.
 *
 * OUTPUT: a CSV you paste into the session log, then LAUNCH_KF / (optionally kS) into
 * RobotConstants. PASS prints only if MY_SETTELED_DATA was tight enough.
 *
 * BENCH SAFETY: strap the flywheels. Outputs only start after play; end-of-sweep zeroing runs
 * in-code after the loop (LinearOpMode.stop() is final, so there is no end() hook here — see
 * StaticShooterTeleOp.end() for the match-side safe stop).
 */
@TeleOp(name = "Flywheel Power Sweep", group = "test")
public class FlywheelPowerSweepOpMode extends LinearOpMode {
    // ---- procedure knobs (bench constants, not match constants) ----
    private static final double[] SWEEP_POWERS = {0.20, 0.30, 0.40, 0.50, 0.60, 0.70, 0.80, 0.90, 1.00};
    private static final double SETTLE_TIMEOUT_S = 4.0;  // per step
    private static final double SETTLE_SPREAD_TPS = 25.0; // window max-min below this = settled
    private static final int SETTLE_WINDOW = 12;         // samples (~0.25 s at 20 ms)
    private static final double MIN_CLEAN_S = 0.35;      // burn-in before settle checks
    private static final double REST_S = 0.75;           // cooldown at 0 power between steps
    private static final long SLEEP_MS_STEP = 20;        // control/log cadence

    // fit + pass criteria (PROPOSED; confirm with the user per AGENTS.md §6)
    private static final double FIT_TOP_FRACTION = 0.5;  // fit the fastest half of points
    private static final double PASS_R_SQUARED = 0.995;
    private static final int MIN_FIT_POINTS = 4;

    @Override
    public void runOpMode() {
        DcMotorEx r = hardwareMap.get(DcMotorEx.class, LAUNCH_R_NAME);
        DcMotorEx l = hardwareMap.get(DcMotorEx.class, LAUNCH_L_NAME);
        r.setDirection(DcMotorSimple.Direction.REVERSE);

        double batteryV = Double.NaN;
        for (VoltageSensor vs : hardwareMap.getAll(VoltageSensor.class)) {
            if (Double.isNaN(batteryV)) batteryV = vs.getVoltage(); // first hub's battery
        }

        for (DcMotorEx m : new DcMotorEx[]{r, l}) {
            m.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
            m.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
            m.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.FLOAT);
        }

        telemetry.addLine("Strap flywheels. Press INIT, then PLAY on the bench.");
        telemetry.update();

        List<double[]> data = new ArrayList<>(); // {P, V} in sweep order
        sweep(r, l, data);

        // ---- analysis (pure math, mirrors any later spreadsheet check) ----
        double vmax = 0.0;
        for (double[] d : data) vmax = Math.max(vmax, d[1]);
        List<double[]> band = new ArrayList<>();
        for (double[] d : data) if (d[1] >= FIT_TOP_FRACTION * vmax) band.add(d);

        LinearFit.Result fit = null;
        if (band.size() >= MIN_FIT_POINTS) {
            double[] xs = new double[band.size()], ys = new double[band.size()];
            for (int i = 0; i < band.size(); i++) { xs[i] = band.get(i)[1]; ys[i] = band.get(i)[0]; }
            fit = LinearFit.fit(xs, ys); // x = settled velocity, y = power
        }

        telemetry.clear();
        telemetry.addLine("CSV (power, settledVelTPS):");
        for (double[] d : data) telemetry.addData("d", "%.3f, %.0f", d[0], d[1]);

        boolean pass;
        if (fit == null) {
            pass = false;
            telemetry.addLine("FAIL: not enough settled points for the fit band (need "
                    + MIN_FIT_POINTS + "). Raise powers or lengthen timeouts.");
        } else {
            telemetry.addData("LAUNCH_KF (kV) =", "%.6f power/tps", fit.slope);
            telemetry.addData("kS (info only) =", "%.4f power", fit.intercept);
            telemetry.addData("R^2 =", "%.5f", fit.rSquared);
            telemetry.addData("fit points =", "%d", fit.n);
            pass = fit.rSquared >= PASS_R_SQUARED && fit.slope > 0;
            telemetry.addLine(pass
                    ? "PASS band — copy LAUNCH_KF above into RobotConstants."
                    : "FAIL band — R^2 low: pick a higher FIT_TOP_FRACTION or fix mechanical issue.");
        }
        telemetry.addData("battery", "%.2f V", batteryV);
        telemetry.update();

        zeroed(r, l); // safe-stop: sweep output done
    }

    private void sweep(DcMotorEx r, DcMotorEx l, List<double[]> data) {
        ElapsedTime stepTimer = new ElapsedTime();
        for (double p : SWEEP_POWERS) {
            double[] velR = new double[SETTLE_WINDOW], velL = new double[SETTLE_WINDOW];
            int head = 0, count = 0;
            boolean settled = false;
            stepTimer.reset();
            set(r, l, p);

            while (!isStopRequested() && !settled) {
                if (stepTimer.seconds() > SETTLE_TIMEOUT_S) break;
                double now = stepTimer.seconds();
                if (now > MIN_CLEAN_S) {
                    velR[head] = r.getVelocity();
                    velL[head] = l.getVelocity();
                    head = (head + 1) % SETTLE_WINDOW;
                    count = Math.min(count + 1, SETTLE_WINDOW);
                    if (count == SETTLE_WINDOW) {
                        double max = 0, min = Double.MAX_VALUE;
                        for (int i = 0; i < SETTLE_WINDOW; i++) {
                            double v = 0.5 * (velR[i] + velL[i]);
                            max = Math.max(max, v); min = Math.min(min, v);
                        }
                        if (max - min <= SETTLE_SPREAD_TPS) settled = true;
                    }
                }
                sleep(SLEEP_MS_STEP);
                pushDriveTelemetry(p, now, settled);
            }

            // record the step's settled velocity = mean of the last few samples
            double vAvg = meanTail(velR, velL, head, count);
            data.add(new double[]{p, vAvg});
            telemetry.addData("step", "P=%.2f V=%.0f%s", p, vAvg, settled ? "" : " (TIMED OUT)");
            telemetry.update();

            set(r, l, 0.0);
            rest(r, l, REST_S);
        }
        set(r, l, 0.0); // also safe on an early stop
    }

    private static double meanTail(double[] a, double[] b, int head, int count) {
        double sum = 0; int n = 0;
        for (int k = 1; k <= count && k <= 5; k++) {
            int idx = ((head - k) % a.length + a.length) % a.length;
            sum += 0.5 * (a[idx] + b[idx]);
            n++;
        }
        return n == 0 ? 0 : sum / n;
    }

    private void rest(DcMotorEx r, DcMotorEx l, double seconds) {
        ElapsedTime t = new ElapsedTime();
        while (!isStopRequested() && t.seconds() < seconds) {
            sleep(SLEEP_MS_STEP);
            pushDriveTelemetry(0, t.seconds(), false);
        }
    }

    private void pushDriveTelemetry(double p, double t, boolean lastStatus) {
        telemetry.addData("P", "%.2f  t=%.2fs  %s", p, t, lastStatus ? "settled" : "settling");
        telemetry.update();
    }

    private static void set(DcMotorEx r, DcMotorEx l, double p) {
        r.setPower(p);
        l.setPower(p);
    }

    private void zeroed(DcMotorEx r, DcMotorEx l) {
        set(r, l, 0.0);
    }
}
