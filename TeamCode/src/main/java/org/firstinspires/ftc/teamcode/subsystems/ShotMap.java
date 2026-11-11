package org.firstinspires.ftc.teamcode.subsystems;

/**
 * The distance -> (flywheel velocity, hood position) model the match teleop actually shoots with.
 *
 * <p>Holds two independent regressions over the same distance axis, because the two mechanisms are
 * mechanically independent and each is measured separately:
 * <ul>
 *   <li>{@link #velocityTps} - fitted from (distance, achieved ticks/sec) shot samples</li>
 *   <li>{@link #hoodPos} - fitted from (distance, hood position) shot samples</li>
 * </ul>
 *
 * <p>Why independent fits instead of one ballistic model: a ballistic solve needs the CELL mouth
 * height, the launcher exit height and the slip in the launcher, and none of those are measured
 * (AGENTS.md §5). A regression only needs shots that actually scored. It is a curve through
 * observed reality rather than a prediction from unmeasured geometry.
 *
 * <p>The honest weakness, stated rather than hidden: velocity and hood are <b>coupled</b>. For any
 * one distance there is a band of (velocity, hood) pairs that score - flat and fast, or steep and
 * slow. If those two are picked by hand shot to shot, the scatter in the dataset is a choice, not
 * measurement noise, and the fits will disagree with each other in ways that look like bad data.
 * The fix is procedural, not mathematical: set the hood schedule by hand and only let the
 * regression learn velocity, or shoot every sample at the same hood. See {@code ShotMapTuner}.
 *
 * <p>Never throws. This is constructed at INIT of a match OpMode, where an exception means a dead
 * robot on the field. An empty or malformed constant array degrades to "no auto aim", which is the
 * existing behaviour.
 */
public final class ShotMap {

    private final ShotRegression velocity;
    private final ShotRegression hood;

    private ShotMap(ShotRegression velocity, ShotRegression hood) {
        this.velocity = velocity;
        this.hood = hood;
    }

    /**
     * Fit both laws from the constant arrays. Order does not matter (least squares is
     * order-independent), duplicate distances are allowed, and a NaN hood sample just drops the
     * hood fit to null.
     *
     * @param distIn     measured distances in inches
     * @param velTps     achieved flywheel speed at each distance, ticks/sec
     * @param hoodPos    hood servo position at each distance
     * @param maxDegree  2 = quadratic once there are 4+ points (see {@link ShotRegression})
     */
    public static ShotMap fit(double[] distIn, double[] velTps, double[] hoodPos, int maxDegree) {
        return new ShotMap(
                ShotRegression.fit(distIn, velTps, maxDegree),
                ShotRegression.fit(distIn, hoodPos, maxDegree));
    }

    /** An inert map: everything falls back to the defaults in {@code RobotConstants}. */
    public static ShotMap empty() {
        return new ShotMap(null, null);
    }

    /** True when a velocity law exists, i.e. auto velocity can be used. */
    public boolean isUsable() {
        return velocity != null;
    }

    /** True when a hood law exists. The hood may legitimately be unfitted while velocity is fine. */
    public boolean hasHood() {
        return hood != null;
    }

    /** Fitted target flywheel speed at {@code distIn}, or NaN when no fit exists. */
    public double velocityTps(double distIn) {
        return velocity == null ? Double.NaN : velocity.predict(distIn);
    }

    /** Fitted hood position at {@code distIn}, or NaN when no fit exists. */
    public double hoodPos(double distIn) {
        return hood == null ? Double.NaN : hood.predict(distIn);
    }

    /** True when the requested distance is outside the measured band, so the fit is clamping. */
    public boolean isExtrapolating(double distIn) {
        return (velocity != null && velocity.isClamped(distIn))
                || (hood != null && hood.isClamped(distIn));
    }

    public int pointCount() {
        return velocity == null ? (hood == null ? 0 : hood.pointCount()) : velocity.pointCount();
    }

    /** The velocity fit, or null. Exposed for telemetry only. */
    public ShotRegression velocityFit() {
        return velocity;
    }

    /** The hood fit, or null. Exposed for telemetry only. */
    public ShotRegression hoodFit() {
        return hood;
    }
}
