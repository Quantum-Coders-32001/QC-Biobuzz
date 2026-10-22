package org.firstinspires.ftc.teamcode.math;

/**
 * Ordinary least-squares fit of a line y = m*x + b. Pure Java, no FTC imports (unit-testable).
 *
 * Used by {@link org.firstinspires.ftc.teamcode.teleop.test.FlywheelPowerSweepOpMode} to fit
 * held power P against settled velocity V (y = P, x = V), yielding m = kV [power / (ticks/s)]
 * and b = kS (the constant-friction power offset).
 */
public final class LinearFit {
    private LinearFit() {}

    public static final class Result {
        public final double slope;      // m
        public final double intercept;  // b
        /** 1 - SSE/SST. 1.0 = perfect line; low values mean the fit band was not linear. */
        public final double rSquared;
        public final int n;

        Result(double m, double b, double r2, int n) {
            this.slope = m;
            this.intercept = b;
            this.rSquared = r2;
            this.n = n;
        }

        /** Prediction is only valid for x inside the fitted band; never extrapolate. */
        public double predict(double x) {
            return slope * x + intercept;
        }
    }

    /**
     * @param xs sample x values (e.g. settled velocities)
     * @param ys sample y values (e.g. held powers)
     * @return result, or null when the fit is impossible (null/length mismatch/NaN/n < 2/
     *         near-collinear inputs so the slope is not trustworthy).
     */
    public static Result fit(double[] xs, double[] ys) {
        if (xs == null || ys == null || xs.length != ys.length || xs.length < 2) return null;

        double sumX = 0, sumY = 0;
        int n = xs.length;
        for (int i = 0; i < n; i++) {
            if (!Double.isFinite(xs[i]) || !Double.isFinite(ys[i])) return null;
            sumX += xs[i];
            sumY += ys[i];
        }
        if (!Double.isFinite(sumX) || !Double.isFinite(sumY)) return null;
        double meanX = sumX / n, meanY = sumY / n;

        double sxx = 0, sxy = 0;
        for (int i = 0; i < n; i++) {
            double dx = xs[i] - meanX, dy = ys[i] - meanY;
            sxx += dx * dx;
            sxy += dx * dy;
        }
        if (sxx <= 1e-12) return null; // all x identical -> slope undefined, reject

        double m = sxy / sxx;
        double b = meanY - m * meanX;
        if (!Double.isFinite(m) || !Double.isFinite(b)) return null;

        // R^2 over the same points.
        double sst = 0, sse = 0;
        for (int i = 0; i < n; i++) {
            double dy = ys[i] - meanY;
            sst += dy * dy;
            sse += (ys[i] - (m * xs[i] + b)) * (ys[i] - (m * xs[i] + b));
        }
        double r2 = (sst <= 1e-12) ? 1.0 : 1.0 - sse / sst;
        return new Result(m, b, r2, n);
    }
}
