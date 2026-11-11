package org.firstinspires.ftc.teamcode.subsystems;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Pure geometry + selection maths for aiming the chassis at a HIVE CELL using its AprilTags.
 *
 * <p>No FTC imports on purpose (AGENTS.md: logic lives where it can be unit-tested off-robot).
 * {@link org.firstinspires.ftc.teamcode.teleop.AutoAimTeleOpBase} is the only caller; it converts
 * Limelight results into {@link Sample}s and applies {@link #aimTurnPower} as a chassis turn.
 *
 * <p>Why this shape:
 * <ul>
 *   <li>Each CELL carries a cluster of 4 tags (manual 9.9). The centre of the cluster is the aim
 *       point, so the median of the visible tags' tx is the bearing to the CELL, not to one tag.</li>
 *   <li>The pipeline sees the centre pair of BOTH cells (4 IDs), so there can be two vertical
 *       clusters in one frame. The raised CELL's tags sit higher in the image, so the upper
 *       cluster is the one being shot at. See {@link #selectRaisedCell}.</li>
 *   <li>Nothing here knows which CELL is up from odometry: it reads that off the image, which is
 *       what makes it survive a HIVE tip caused by the partner robot (AGENTS.md 4.4).</li>
 * </ul>
 *
 * <p>Sign conventions (Limelight, verified against docs): tx is positive when the target is to the
 * RIGHT of the crosshair. This chassis turns clockwise (to the right) for positive turn input, so
 * a positive tx error maps directly to a positive turn command.
 */
public final class HiveAim {
    private HiveAim() {}

    /** One detected AprilTag, already converted to the units this class uses. */
    public static final class Sample {
        public final int id;
        /** Degrees right of the camera crosshair. */
        public final double txDeg;
        /** Degrees above the camera crosshair. */
        public final double tyDeg;
        /** Horizontal camera-to-tag distance in inches, or NaN if the 3D solve was unusable. */
        public final double distIn;

        public Sample(int id, double txDeg, double tyDeg, double distIn) {
            this.id = id;
            this.txDeg = txDeg;
            this.tyDeg = tyDeg;
            this.distIn = distIn;
        }

        public boolean isUsable() {
            return Double.isFinite(txDeg) && Double.isFinite(tyDeg);
        }
    }

    /** The tags chosen for one aim, plus the aggregate numbers derived from them. */
    public static final class Selection {
        public final double txDeg;
        public final double tyDeg;
        /** Median horizontal distance in inches, or NaN if no tag had a usable 3D solve. */
        public final double distIn;
        public final int count;
        public final int[] ids;
        /** True when two separate vertical clusters were seen (i.e. both CELLs are in frame). */
        public final boolean twoClusters;

        Selection(double txDeg, double tyDeg, double distIn, int count, int[] ids, boolean twoClusters) {
            this.txDeg = txDeg;
            this.tyDeg = tyDeg;
            this.distIn = distIn;
            this.count = count;
            this.ids = ids;
            this.twoClusters = twoClusters;
        }
    }

    /**
     * Pick the raised CELL's tag cluster and reduce it to one bearing, one elevation and one range.
     *
     * @param samples            usable detections (non-finite ones are ignored here too)
     * @param minClusterGapDeg   minimum ty gap that counts as "these are two different CELLs".
     *                           Tags in one cluster are nearly co-planar, so a couple of degrees
     *                           separates the 18.8 in cell spacing from tag-to-tag noise.
     * @return null when nothing usable is in frame
     */
    public static Selection selectRaisedCell(List<Sample> samples, double minClusterGapDeg) {
        List<Sample> valid = new ArrayList<>();
        if (samples != null) {
            for (Sample s : samples) {
                if (s != null && s.isUsable()) valid.add(s);
            }
        }
        if (valid.isEmpty()) return null;

        valid.sort((a, b) -> Double.compare(a.tyDeg, b.tyDeg));

        int from = 0;
        boolean twoClusters = false;
        int n = valid.size();

        if (n >= 3) {
            int splitAfter = -1;
            double widest = Double.NEGATIVE_INFINITY;
            for (int i = 1; i < n; i++) {
                double gap = valid.get(i).tyDeg - valid.get(i - 1).tyDeg;
                if (gap > widest) {
                    widest = gap;
                    splitAfter = i;
                }
            }
            if (widest >= minClusterGapDeg) {
                from = splitAfter; // keep the upper half only
                twoClusters = true;
            }
        } else if (n == 2) {
            if (valid.get(1).tyDeg - valid.get(0).tyDeg >= minClusterGapDeg) {
                from = 1; // one tag from each CELL: trust the higher one
                twoClusters = true;
            }
        }

        List<Sample> chosen = valid.subList(from, n);

        double[] tx = new double[chosen.size()];
        double[] ty = new double[chosen.size()];
        double[] dist = new double[chosen.size()];
        int[] ids = new int[chosen.size()];
        for (int i = 0; i < chosen.size(); i++) {
            Sample s = chosen.get(i);
            tx[i] = s.txDeg;
            ty[i] = s.tyDeg;
            dist[i] = s.distIn;
            ids[i] = s.id;
        }

        return new Selection(median(tx), median(ty), median(dist), chosen.size(), ids, twoClusters);
    }

    /**
     * Median of the finite values. Returns NaN when nothing finite is present.
     * Even counts average the two middle values, so 2 tags give the midpoint between them.
     */
    public static double median(double[] values) {
        if (values == null || values.length == 0) return Double.NaN;
        double[] tmp = new double[values.length];
        int k = 0;
        for (double v : values) {
            if (Double.isFinite(v)) tmp[k++] = v;
        }
        if (k == 0) return Double.NaN;
        tmp = Arrays.copyOf(tmp, k);
        Arrays.sort(tmp);
        int mid = k / 2;
        return (k % 2 == 1) ? tmp[mid] : 0.5 * (tmp[mid - 1] + tmp[mid]);
    }

    /**
     * Horizontal camera-to-target distance, in the same unit as the inputs.
     *
     * <p>Limelight camera space is X right, Y DOWN, Z out of the lens (docs: "3D Coordinate Systems
     * In Detail"). The camera is tilted UP by {@code pitchUpDeg}, so the camera's forward axis is
     * {@code (cos p, 0, sin p)} and its down axis is {@code (sin p, 0, -cos p)} in a robot frame
     * that is {forward, right, up}. Rotating the target vector into that frame:
     * <pre>
     *   forward = y*sin(p) + z*cos(p)
     *   right   = x
     * </pre>
     * Using raw {@code z} instead would over-estimate range by 1/cos(p) - about 30% at 40 deg.
     */
    public static double horizontalDistanceIn(double camXIn, double camYIn, double camZIn, double pitchUpDeg) {
        if (!Double.isFinite(camXIn) || !Double.isFinite(camYIn) || !Double.isFinite(camZIn)) return Double.NaN;
        if (!Double.isFinite(pitchUpDeg)) return Double.NaN;
        double p = Math.toRadians(pitchUpDeg);
        double forward = camYIn * Math.sin(p) + camZIn * Math.cos(p);
        return Math.hypot(camXIn, forward);
    }

    /**
     * Proportional turn power for a bearing error. Zero inside the deadband (so the chassis does not
     * hunt while the image jitters), saturated at {@code maxAbs}.
     *
     * <p>No D term: tx is quantised by the tag solve and noisy frame to frame, and differentiating it
     * just injects that noise into the wheels. Add damping as a gain/config change only if the real
     * robot visibly overshoots.
     */
    public static double aimTurnPower(double errDeg, double kp, double deadbandDeg, double maxAbs) {
        if (!Double.isFinite(errDeg)) return 0.0;
        if (Math.abs(errDeg) <= deadbandDeg) return 0.0;
        double p = kp * errDeg;
        return Math.max(-maxAbs, Math.min(maxAbs, p));
    }

    /** Move {@code previous} toward {@code target} by at most {@code maxStep}. */
    public static double slewToward(double target, double previous, double maxStep) {
        if (!Double.isFinite(target)) return previous;
        if (!(maxStep > 0)) return target;
        double delta = target - previous;
        if (delta > maxStep) delta = maxStep;
        if (delta < -maxStep) delta = -maxStep;
        return previous + delta;
    }
}
