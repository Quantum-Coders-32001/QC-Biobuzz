package org.firstinspires.ftc.teamcode.math;

public final class TurretGeometry {
    private TurretGeometry() {}

    public static final class Config {
        public double pivotOffsetXIn = Double.NaN;
        public double pivotOffsetYIn = Double.NaN;
        public double zeroOffsetRad = Double.NaN;
        public double minTurretAngleRad = Double.NaN;
        public double maxTurretAngleRad = Double.NaN;
        public double hysteresisRad = 0.01;

        public void validate() {
            StringBuilder missing = new StringBuilder();
            if (!Double.isFinite(pivotOffsetXIn)) missing.append("pivotOffsetXIn ");
            if (!Double.isFinite(pivotOffsetYIn)) missing.append("pivotOffsetYIn ");
            if (!Double.isFinite(zeroOffsetRad)) missing.append("zeroOffsetRad ");
            if (!Double.isFinite(minTurretAngleRad)) missing.append("minTurretAngleRad ");
            if (!Double.isFinite(maxTurretAngleRad)) missing.append("maxTurretAngleRad ");
            if (!Double.isFinite(hysteresisRad) || hysteresisRad < 0) missing.append("hysteresisRad ");
            if (missing.length() > 0) {
                throw new IllegalStateException("TurretGeometry.Config missing or invalid required values: " + missing.toString().trim());
            }
            if (minTurretAngleRad >= maxTurretAngleRad) {
                throw new IllegalStateException("minTurretAngleRad must be < maxTurretAngleRad");
            }
        }
    }

    public static final class SolveInput {
        public double robotXIn;
        public double robotYIn;
        public double robotHeadingRad;
        public double robotVxInPerSec;
        public double robotVyInPerSec;
        public double robotOmegaRadPerSec;
        public double targetXIn;
        public double targetYIn;
        public double currentTurretAngleRad;
        public FlightTimeFunction flightTimeFunction;

        public SolveInput() {}
    }

    public interface FlightTimeFunction {
        /**
         * Returns the flight time in seconds for a given distance in inches.
         * Must be a total function (defined for all non-negative distances).
         * Must NOT throw exceptions; return NaN for out-of-domain input (solve() then reports INVALID_INPUT).
         * Exceptions are not caught by solve() and will propagate.
         * @param distanceIn distance in inches
         * @return flight time in seconds, or NaN for invalid input
         */
        double flightTimeSeconds(double distanceIn);
    }

    public static final class SolveOutput {
        public enum Status {
            OK,
            DEAD_ZONE,
            /**
             * INVALID_INPUT: any non-finite SolveInput field (robot x/y/heading/vx/vy/omega,
             * target x/y, currentTurretAngleRad), or a flightTimeFunction result that is
             * non-finite or negative.
             * Output: targetAngleRad = currentTurretAngleRad if finite, else NaN;
             * targetRateRadPerSec = 0.0; distIn = NaN; leadConverged = false.
             * Consumers must hold position / output zero power.
             * NaN currentTurretAngleRad is the normal unhomed state and yields INVALID_INPUT.
             */
            INVALID_INPUT
        }

        public final double targetAngleRad;
        public final double targetRateRadPerSec;
        public final double distIn;
        public final Status status;
        /**
         * leadConverged: true unless the velocity-lead iteration exhausted MAX_ITERATIONS
         * without meeting tolerance; meaningful only when status != INVALID_INPUT;
         * consumers must check status first.
         */
        public final boolean leadConverged;

        public SolveOutput(double targetAngleRad, double targetRateRadPerSec, double distIn, Status status, boolean leadConverged) {
            this.targetAngleRad = targetAngleRad;
            this.targetRateRadPerSec = targetRateRadPerSec;
            this.distIn = distIn;
            this.status = status;
            this.leadConverged = leadConverged;
        }
    }

    private static final int MAX_ITERATIONS = 10;
    private static final double CONVERGENCE_TOLERANCE_IN = 1e-6;
    private static final double MIN_DIST_FOR_LEAD_IN = 1e-3;

    public static SolveOutput solve(Config config, SolveInput input) {
        config.validate();

        // Input validation: all SolveInput doubles must be finite
        if (!Double.isFinite(input.robotXIn) || !Double.isFinite(input.robotYIn) ||
                !Double.isFinite(input.robotHeadingRad) || !Double.isFinite(input.robotVxInPerSec) ||
                !Double.isFinite(input.robotVyInPerSec) || !Double.isFinite(input.robotOmegaRadPerSec) ||
                !Double.isFinite(input.targetXIn) || !Double.isFinite(input.targetYIn) ||
                !Double.isFinite(input.currentTurretAngleRad)) {
            return invalidInputOutput(input.currentTurretAngleRad);
        }

        double cosH = Math.cos(input.robotHeadingRad);
        double sinH = Math.sin(input.robotHeadingRad);

        double pivotXIn = input.robotXIn + cosH * config.pivotOffsetXIn - sinH * config.pivotOffsetYIn;
        double pivotYIn = input.robotYIn + sinH * config.pivotOffsetXIn + cosH * config.pivotOffsetYIn;

        double pivotVxInPerSec = input.robotVxInPerSec - input.robotOmegaRadPerSec * (sinH * config.pivotOffsetXIn + cosH * config.pivotOffsetYIn);
        double pivotVyInPerSec = input.robotVyInPerSec + input.robotOmegaRadPerSec * (cosH * config.pivotOffsetXIn - sinH * config.pivotOffsetYIn);

        double virtualTargetXIn = input.targetXIn;
        double virtualTargetYIn = input.targetYIn;
        boolean leadConverged = true;

        if (input.flightTimeFunction != null) {
            double dx = virtualTargetXIn - pivotXIn;
            double dy = virtualTargetYIn - pivotYIn;
            double distIn = Math.hypot(dx, dy);

            if (distIn > MIN_DIST_FOR_LEAD_IN) {
                for (int iter = 0; iter < MAX_ITERATIONS; iter++) {
                    double t = input.flightTimeFunction.flightTimeSeconds(distIn);

                    // flightTimeFunction output must be finite and non-negative
                    if (!Double.isFinite(t) || t < 0.0) {
                        return invalidInputOutput(input.currentTurretAngleRad);
                    }

                    virtualTargetXIn = input.targetXIn - pivotVxInPerSec * t;
                    virtualTargetYIn = input.targetYIn - pivotVyInPerSec * t;

                    dx = virtualTargetXIn - pivotXIn;
                    dy = virtualTargetYIn - pivotYIn;
                    double newDistIn = Math.hypot(dx, dy);

                    if (Math.abs(newDistIn - distIn) < CONVERGENCE_TOLERANCE_IN) {
                        distIn = newDistIn;
                        break;
                    }
                    distIn = newDistIn;

                    if (iter == MAX_ITERATIONS - 1) {
                        leadConverged = false;
                    }
                }
            }
        }

        double dx = virtualTargetXIn - pivotXIn;
        double dy = virtualTargetYIn - pivotYIn;
        double distIn = Math.hypot(dx, dy);

        double bearingFieldRad;
        if (distIn < 1e-9) {
            bearingFieldRad = input.robotHeadingRad;
        } else {
            bearingFieldRad = Math.atan2(dy, dx);
        }

        double turretAngleRad = AngleUtil.wrap(bearingFieldRad - input.robotHeadingRad - config.zeroOffsetRad);

        boolean feasible = true;
        boolean clamped = false;
        double finalAngleRad;

        boolean hasEquivalentInRange = false;
        for (int k = -2; k <= 2; k++) {
            double candidate = turretAngleRad + k * AngleUtil.TWO_PI;
            if (candidate >= config.minTurretAngleRad && candidate <= config.maxTurretAngleRad) {
                hasEquivalentInRange = true;
                break;
            }
        }

        if (hasEquivalentInRange) {
            double candidateAngle = AngleUtil.findEquivalentAngleInRange(
                    turretAngleRad, config.minTurretAngleRad, config.maxTurretAngleRad,
                    input.currentTurretAngleRad);
            finalAngleRad = AngleUtil.findEquivalentAngleInRangeWithHysteresis(
                    candidateAngle, config.minTurretAngleRad, config.maxTurretAngleRad,
                    input.currentTurretAngleRad, config.hysteresisRad);
        } else {
            feasible = false;
            clamped = true;
            double distToMin = Math.abs(AngleUtil.shortestAngularDistance(config.minTurretAngleRad, turretAngleRad));
            double distToMax = Math.abs(AngleUtil.shortestAngularDistance(config.maxTurretAngleRad, turretAngleRad));
            finalAngleRad = (distToMin < distToMax) ? config.minTurretAngleRad : config.maxTurretAngleRad;
        }

        double targetRateRadPerSec = 0.0;
        if (distIn > 1e-9) {
            double bearingRateRadPerSec = (dy * pivotVxInPerSec - dx * pivotVyInPerSec) / (distIn * distIn);
            targetRateRadPerSec = bearingRateRadPerSec - input.robotOmegaRadPerSec;
        }

        SolveOutput.Status status = feasible ? SolveOutput.Status.OK : SolveOutput.Status.DEAD_ZONE;
        return new SolveOutput(finalAngleRad, targetRateRadPerSec, distIn, status, leadConverged);
    }

    private static SolveOutput invalidInputOutput(double currentTurretAngleRad) {
        double angle = Double.isFinite(currentTurretAngleRad) ? currentTurretAngleRad : Double.NaN;
        return new SolveOutput(angle, 0.0, Double.NaN, SolveOutput.Status.INVALID_INPUT, false);
    }
}