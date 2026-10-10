package org.firstinspires.ftc.teamcode.math;

public final class AngleUtil {
    private AngleUtil() {}

    public static final double TWO_PI = 2.0 * Math.PI;
    public static final double PI = Math.PI;

    public static double wrap(double angleRad) {
        double wrapped = angleRad % TWO_PI;
        if (wrapped <= -PI) {
            wrapped += TWO_PI;
        } else if (wrapped > PI) {
            wrapped -= TWO_PI;
        }
        return wrapped;
    }

    public static double wrapPositive(double angleRad) {
        double wrapped = angleRad % TWO_PI;
        if (wrapped < 0) {
            wrapped += TWO_PI;
        }
        return wrapped;
    }

    public static double shortestAngularDistance(double fromRad, double toRad) {
        return wrap(toRad - fromRad);
    }

    public static boolean isAngleInRange(double angleRad, double minRad, double maxRad) {
        if (maxRad - minRad >= TWO_PI) {
            return true;
        }
        double normalizedAngle = wrapPositive(angleRad);
        double normalizedMin = wrapPositive(minRad);
        double normalizedMax = wrapPositive(maxRad);
        if (normalizedMin <= normalizedMax) {
            return normalizedAngle >= normalizedMin && normalizedAngle <= normalizedMax;
        } else {
            return normalizedAngle >= normalizedMin || normalizedAngle <= normalizedMax;
        }
    }

    public static double clampToRange(double angleRad, double minRad, double maxRad) {
        if (maxRad - minRad >= TWO_PI) {
            return angleRad;
        }
        double wrapped = wrap(angleRad);
        if (wrapped < minRad) {
            return minRad;
        }
        if (wrapped > maxRad) {
            return maxRad;
        }
        return wrapped;
    }

    public static double findEquivalentAngleInRange(double angleRad, double minRad, double maxRad, double referenceRad) {
        if (maxRad - minRad >= TWO_PI) {
            return angleRad;
        }

        double bestAngle = Double.NaN;
        double bestDiff = Double.POSITIVE_INFINITY;

        for (int k = -2; k <= 2; k++) {
            double candidate = angleRad + k * TWO_PI;
            if (candidate >= minRad && candidate <= maxRad) {
                double diff = Math.abs(shortestAngularDistance(referenceRad, candidate));
                if (diff < bestDiff) {
                    bestDiff = diff;
                    bestAngle = candidate;
                }
            }
        }

        if (Double.isNaN(bestAngle)) {
            double distToMin = Math.abs(shortestAngularDistance(referenceRad, minRad));
            double distToMax = Math.abs(shortestAngularDistance(referenceRad, maxRad));
            bestAngle = (distToMin < distToMax) ? minRad : maxRad;
        }

        return bestAngle;
    }

    public static double findEquivalentAngleInRangeWithHysteresis(
            double angleRad, double minRad, double maxRad, double referenceRad, double hysteresisRad) {
        if (maxRad - minRad >= TWO_PI) {
            return angleRad;
        }

        double baseAngle = findEquivalentAngleInRange(angleRad, minRad, maxRad, referenceRad);

        double distanceToMin = Math.abs(baseAngle - minRad);
        double distanceToMax = Math.abs(maxRad - baseAngle);

        if (distanceToMin <= hysteresisRad && referenceRad <= minRad + hysteresisRad) {
            return minRad;
        }
        if (distanceToMax <= hysteresisRad && referenceRad >= maxRad - hysteresisRad) {
            return maxRad;
        }

        return baseAngle;
    }
}