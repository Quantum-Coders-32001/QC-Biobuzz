package org.firstinspires.ftc.teamcode.math;

/**
 * Linear-interpolation lookup table. Pure Java, no FTC imports, so it is unit-testable off-robot.
 * Clamps at both ends (never extrapolates). A NaN input returns NaN.
 */
public final class ShotTable {
    private final double[] xs;
    private final double[] ys;

    public ShotTable(double[] xs, double[] ys) {
        if (xs.length != ys.length || xs.length < 2) {
            throw new IllegalArgumentException("ShotTable needs equal-length arrays with at least 2 points");
        }
        for (int i = 0; i < xs.length; i++) {
            if (!Double.isFinite(xs[i]) || !Double.isFinite(ys[i])) {
                throw new IllegalArgumentException("ShotTable values must be finite (index " + i + ")");
            }
            if (i > 0 && xs[i] <= xs[i - 1]) {
                throw new IllegalArgumentException("ShotTable distances must be strictly increasing");
            }
        }
        this.xs = xs.clone();
        this.ys = ys.clone();
    }

    public double get(double x) {
        if (Double.isNaN(x)) return Double.NaN;
        int last = xs.length - 1;
        if (x <= xs[0]) return ys[0];
        if (x >= xs[last]) return ys[last];
        int i = 1;
        while (xs[i] < x) i++;
        double t = (x - xs[i - 1]) / (xs[i] - xs[i - 1]);
        return ys[i - 1] + t * (ys[i] - ys[i - 1]);
    }
}
