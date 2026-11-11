package org.firstinspires.ftc.teamcode.subsystems;

/**
 * Ordinary least-squares polynomial fit y(x) = c0 + c1*x + c2*x^2, used to turn a handful of
 * measured shots into a distance -> value model.
 *
 * <p>Pure Java, no FTC imports, so the maths can be proven with unit tests instead of on a robot
 * (AGENTS.md §6 L1).
 *
 * <p>Why a polynomial and not the lookup table it replaces:
 * <ul>
 *   <li>A table returns the measured value at each measured point and a straight line between
 *       them. Two adjacent shots at 58 in and 74 in therefore produce a piecewise-linear velocity
 *       schedule with a kink, and every distance in between is a guess. A fit smooths the
 *       measurement noise and gives one continuous law.</li>
 *   <li>It also gives you an <b>R-squared and a worst-case residual</b>. A table gives you nothing:
 *       you cannot tell a clean dataset from four inconsistent shots. That number is the whole
 *       reason to fit rather than interpolate.</li>
 * </ul>
 *
 * <p>Design rules, all deliberate:
 * <ul>
 *   <li><b>Never extrapolate.</b> {@link #predict} clamps to the measured distance band. Feeding a
 *       30 in target to a model fitted from 55-85 in would silently command nonsense.
 *       {@link #isClamped} reports when that happened.</li>
 *   <li><b>No quadratic on 3 points.</b> Three points fit a parabola exactly, so R-squared is 1.0
 *       and the fit is meaningless. A quadratic needs 4 or more.</li>
 *   <li><b>Centring before the solve.</b> Raw distances are ~30-90 in, so the normal equations
 *       contain terms up to x^4 ~ 6.5e7 and lose precision. Fitting in u = x - mean(x) keeps the
 *       matrix well conditioned; the result is converted back to x afterwards.</li>
 *   <li><b>Returns null instead of throwing.</b> This runs at INIT of a match OpMode. A malformed
 *       constant array must degrade to "no auto aim", not to a dead robot.</li>
 * </ul>
 */
public final class ShotRegression {

    /** Highest supported degree. Two is enough: v(d) is close to sqrt(d), hood(d) close to linear. */
    public static final int MAX_SUPPORTED_DEGREE = 2;

    /** Coefficients in powers of x (the ORIGINAL x, not the centred one). */
    private final double[] coef;
    private final double[] xs;
    private final double[] ys;
    private final double rSquared;
    private final double maxAbsResidual;
    private final double xMin;
    private final double xMax;

    private ShotRegression(double[] coef, double[] xs, double[] ys) {
        this.coef = coef;
        this.xs = xs.clone();
        this.ys = ys.clone();

        double xMin = Double.POSITIVE_INFINITY, xMax = Double.NEGATIVE_INFINITY;
        double sum = 0, worst = 0;
        for (double y : this.ys) sum += y;
        double meanY = sum / this.ys.length;
        double sst = 0, sse = 0;
        for (int i = 0; i < this.xs.length; i++) {
            double p = evaluate(this.xs[i]);
            double err = this.ys[i] - p;
            sse += err * err;
            worst = Math.max(worst, Math.abs(err));
            double dy = this.ys[i] - meanY;
            sst += dy * dy;
            xMin = Math.min(xMin, this.xs[i]);
            xMax = Math.max(xMax, this.xs[i]);
        }
        this.xMin = xMin;
        this.xMax = xMax;
        this.maxAbsResidual = worst;
        // A flat dataset (SST = 0) is fitted perfectly by a constant; call that 1.0, not 0/0.
        this.rSquared = (sst <= 1e-12) ? 1.0 : 1.0 - sse / sst;
    }

    /**
     * @param xs        measured distances in inches
     * @param ys        the value measured at each distance (velocity in ticks/sec, or hood position)
     * @param maxDegree 2 for a quadratic, 1 to force a line, 0 for a constant
     * @return the fit, or null when nothing trustworthy can be produced (null/mismatched arrays,
     *         non-finite values, fewer than 1 sample, or every x identical so no slope exists)
     */
    public static ShotRegression fit(double[] xs, double[] ys, int maxDegree) {
        if (xs == null || ys == null) return null;
        if (xs.length != ys.length || xs.length == 0) return null;

        int n = xs.length;
        for (int i = 0; i < n; i++) {
            if (!Double.isFinite(xs[i]) || !Double.isFinite(ys[i])) return null;
        }

        int degree = chooseDegree(n, maxDegree);
        if (degree < 0) return null;

        if (degree == 0) {
            double sum = 0;
            for (double y : ys) sum += y;
            double mean = sum / n;
            return Double.isFinite(mean) ? new ShotRegression(new double[]{mean}, xs, ys) : null;
        }

        double meanX = 0;
        for (double x : xs) meanX += x;
        meanX /= n;

        double[] centred = solveCentred(xs, ys, meanX, degree);
        if (centred == null && degree == 2) {
            // Near-collinear x can make the quadratic singular; a line is still a usable answer.
            centred = solveCentred(xs, ys, meanX, 1);
        }
        if (centred == null) return null;

        double[] expanded = toOriginalX(centred, meanX);
        if (expanded == null) return null;
        return new ShotRegression(expanded, xs, ys);
    }

    /**
     * Polynomial degree that the sample count can actually support.
     * n = 1 -> constant, n = 2 or 3 -> line, n >= 4 -> the caller's choice.
     */
    private static int chooseDegree(int n, int maxDegree) {
        if (maxDegree < 0) return -1;
        int want = Math.min(maxDegree, MAX_SUPPORTED_DEGREE);
        if (n == 1) return 0;
        if (n <= 3) return Math.min(want, 1); // a quadratic through 3 points is an interpolation, not a fit
        return want;
    }

    /**
     * Solve the normal equations in the centred variable u = x - meanX.
     * @return coefficients in u (index = power), or null if the system is singular
     */
    private static double[] solveCentred(double[] xs, double[] ys, double meanX, int degree) {
        int k = degree + 1;
        int n = xs.length;

        // s[p] = sum(u^p) for p up to 2*degree; b[e] = sum(y * u^e) for e up to degree.
        double[] s = new double[2 * degree + 1];
        double[] b = new double[k];
        for (int i = 0; i < n; i++) {
            double u = xs[i] - meanX;
            double p = 1.0;
            for (int e = 0; e <= 2 * degree; e++) {
                s[e] += p;
                p *= u;
            }
            double q = 1.0;
            for (int e = 0; e <= degree; e++) {
                b[e] += ys[i] * q;
                q *= u;
            }
        }

        double[][] a = new double[k][k];
        for (int r = 0; r < k; r++) {
            for (int c = 0; c < k; c++) a[r][c] = s[r + c];
        }
        return gauss(a, b);
    }

    /** Gaussian elimination with partial pivoting. Returns null when the matrix is singular. */
    private static double[] gauss(double[][] matrix, double[] rhs) {
        int k = rhs.length;
        double[][] m = new double[k][];
        for (int i = 0; i < k; i++) m[i] = matrix[i].clone();
        double[] v = rhs.clone();

        for (int col = 0; col < k; col++) {
            int pivot = col;
            for (int row = col + 1; row < k; row++) {
                if (Math.abs(m[row][col]) > Math.abs(m[pivot][col])) pivot = row;
            }
            if (Math.abs(m[pivot][col]) < 1e-12) return null;

            if (pivot != col) {
                double[] tmpRow = m[pivot];
                m[pivot] = m[col];
                m[col] = tmpRow;
                double tmpV = v[pivot];
                v[pivot] = v[col];
                v[col] = tmpV;
            }

            for (int row = col + 1; row < k; row++) {
                double factor = m[row][col] / m[col][col];
                if (factor == 0.0) continue;
                for (int c = col; c < k; c++) m[row][c] -= factor * m[col][c];
                v[row] -= factor * v[col];
            }
        }

        double[] out = new double[k];
        for (int i = k - 1; i >= 0; i--) {
            double sum = v[i];
            for (int c = i + 1; c < k; c++) sum -= m[i][c] * out[c];
            out[i] = sum / m[i][i];
        }
        for (double value : out) {
            if (!Double.isFinite(value)) return null;
        }
        return out;
    }

    /**
     * Rewrite {@code c0 + c1*u + c2*u^2} with u = x - meanX as {@code a0 + a1*x + a2*x^2}.
     * Storing the fit in the original variable keeps {@link #equation()} readable.
     */
    private static double[] toOriginalX(double[] c, double meanX) {
        if (c.length == 1) return new double[]{c[0]};
        if (c.length == 2) return new double[]{c[0] - c[1] * meanX, c[1]};
        if (c.length == 3) {
            return new double[]{
                    c[0] - c[1] * meanX + c[2] * meanX * meanX,
                    c[1] - 2.0 * c[2] * meanX,
                    c[2]};
        }
        return null;
    }

    private double evaluate(double x) {
        double acc = 0;
        for (int i = coef.length - 1; i >= 0; i--) acc = acc * x + coef[i];
        return acc;
    }

    /**
     * Predicted value at {@code x}, clamped to the measured band {@code [xMin, xMax]}.
     * A non-finite {@code x} returns NaN rather than a silent clamp.
     */
    public double predict(double x) {
        if (!Double.isFinite(x)) return Double.NaN;
        return evaluate(Math.max(xMin, Math.min(xMax, x)));
    }

    /** Prediction with no clamping. Only for residual reporting; never for control. */
    public double predictRaw(double x) {
        if (!Double.isFinite(x)) return Double.NaN;
        return evaluate(x);
    }

    /** True when {@code x} lies outside the measured band, i.e. {@link #predict} is clamping. */
    public boolean isClamped(double x) {
        return Double.isFinite(x) && (x < xMin || x > xMax);
    }

    /** Residual (measured - predicted) per input sample, in input order. */
    public double[] residuals() {
        double[] out = new double[xs.length];
        for (int i = 0; i < xs.length; i++) out[i] = ys[i] - predictRaw(xs[i]);
        return out;
    }

    public int pointCount() {
        return xs.length;
    }

    public int degree() {
        return coef.length - 1;
    }

    /** 1.0 = the points lie exactly on the curve. Low values mean the data is not a curve at all. */
    public double rSquared() {
        return rSquared;
    }

    public double maxAbsResidual() {
        return maxAbsResidual;
    }

    public double xMin() {
        return xMin;
    }

    public double xMax() {
        return xMax;
    }

    /** Human-readable law, e.g. {@code 0.0123*d^2 + 4.5*d + 120}. */
    public String equation(String variable) {
        String v = (variable == null || variable.isEmpty()) ? "d" : variable;
        StringBuilder sb = new StringBuilder();
        for (int i = coef.length - 1; i >= 0; i--) {
            double c = coef[i];
            if (c == 0.0 && coef.length > 1) continue;
            if (sb.length() > 0) sb.append(c < 0 ? " - " : " + ");
            else if (c < 0) sb.append('-');
            sb.append(String.format("%.4g", Math.abs(c)));
            if (i >= 2) sb.append('*').append(v).append('^').append(i);
            else if (i == 1) sb.append('*').append(v);
        }
        return sb.length() == 0 ? "0" : sb.toString();
    }
}
