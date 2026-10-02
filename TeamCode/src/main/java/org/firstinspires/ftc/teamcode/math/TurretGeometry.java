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
            if (Double.isNaN(pivotOffsetXIn)) missing.append("pivotOffsetXIn ");
            if (Double.isNaN(pivotOffsetYIn)) missing.append("pivotOffsetYIn ");
            if (Double.isNaN(zeroOffsetRad)) missing.append("zeroOffsetRad ");
            if (Double.isNaN(minTurretAngleRad)) missing.append("minTurretAngleRad ");
            if (Double.isNaN(maxTurretAngleRad)) missing.append("maxTurretAngleRad ");
            if (missing.length() > 0) {
                throw new IllegalStateException("TurretGeometry.Config missing required values: " + missing.toString().trim());
            }
            if (minTurretAngleRad > maxTurretAngleRad) {
                throw new IllegalStateException("minTurretAngleRad must be <= maxTurretAngleRad");
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
        double flightTimeSeconds(double distanceIn);
    }

    public static final class SolveOutput {
        public final double targetAngleRad;
        public final double targetRateRadPerSec;
        public final double distIn;
        public final boolean feasible;
        public final boolean clamped;

        public SolveOutput(double targetAngleRad, double targetRateRadPerSec, double distIn, boolean feasible, boolean clamped) {
            this.targetAngleRad = targetAngleRad;
            this.targetRateRadPerSec = targetRateRadPerSec;
            this.distIn = distIn;
            this.feasible = feasible;
            this.clamped = clamped;
        }
    }

    private static final int MAX_ITERATIONS = 10;
    private static final double CONVERGENCE_TOLERANCE_IN = 1e-6;
    private static final double MIN_DIST_FOR_LEAD_IN = 1e-3;

    public static SolveOutput solve(Config config, SolveInput input) {
        config.validate();

        double cosH = Math.cos(input.robotHeadingRad);
        double sinH = Math.sin(input.robotHeadingRad);

        double pivotXIn = input.robotXIn + cosH * config.pivotOffsetXIn - sinH * config.pivotOffsetYIn;
        double pivotYIn = input.robotYIn + sinH * config.pivotOffsetXIn + cosH * config.pivotOffsetYIn;

        double pivotVxInPerSec = input.robotVxInPerSec - input.robotOmegaRadPerSec * (sinH * config.pivotOffsetXIn + cosH * config.pivotOffsetYIn);
        double pivotVyInPerSec = input.robotVyInPerSec + input.robotOmegaRadPerSec * (cosH * config.pivotOffsetXIn - sinH * config.pivotOffsetYIn);

        double virtualTargetXIn = input.targetXIn;
        double virtualTargetYIn = input.targetYIn;

        if (input.flightTimeFunction != null) {
            double dx = virtualTargetXIn - pivotXIn;
            double dy = virtualTargetYIn - pivotYIn;
            double distIn = Math.hypot(dx, dy);

            if (distIn > MIN_DIST_FOR_LEAD_IN) {
                for (int iter = 0; iter < MAX_ITERATIONS; iter++) {
                    double t = input.flightTimeFunction.flightTimeSeconds(distIn);
                    double prevVirtualTargetXIn = virtualTargetXIn;
                    double prevVirtualTargetYIn = virtualTargetYIn;

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

        double rangeSpan = config.maxTurretAngleRad - config.minTurretAngleRad;
        boolean fullCircle = rangeSpan >= AngleUtil.TWO_PI - 1e-12;

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

        return new SolveOutput(finalAngleRad, targetRateRadPerSec, distIn, feasible, clamped);
    }
}