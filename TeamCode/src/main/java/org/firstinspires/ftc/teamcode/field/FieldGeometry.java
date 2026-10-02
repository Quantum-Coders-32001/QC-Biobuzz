package org.firstinspires.ftc.teamcode.field;

import org.firstinspires.ftc.teamcode.math.AngleUtil;

public final class FieldGeometry {
    private FieldGeometry() {}

    public static final double FIELD_WIDTH_IN = 144.0;
    public static final double FIELD_HEIGHT_IN = 144.0;
    public static final double FIELD_CENTER_X_IN = FIELD_WIDTH_IN / 2.0;
    public static final double FIELD_CENTER_Y_IN = FIELD_HEIGHT_IN / 2.0;

    public enum Alliance {
        RED, BLUE
    }

    public enum CellSlot {
        A, B
    }

    public static final class CellTarget {
        public final double mouthXIn;
        public final double mouthYIn;

        public CellTarget(double mouthXIn, double mouthYIn) {
            this.mouthXIn = mouthXIn;
            this.mouthYIn = mouthYIn;
        }

        public static CellTarget unknown() {
            return new CellTarget(Double.NaN, Double.NaN);
        }

        public boolean isKnown() {
            return !Double.isNaN(mouthXIn) && !Double.isNaN(mouthYIn);
        }
    }

    public static final class HiveCellPositions {
        public final CellTarget slotA;
        public final CellTarget slotB;

        public HiveCellPositions(CellTarget slotA, CellTarget slotB) {
            this.slotA = slotA;
            this.slotB = slotB;
        }

        public CellTarget getUpwardCell(CellSlot slot) {
            return (slot == CellSlot.A) ? slotA : slotB;
        }

        public HiveCellPositions afterTip() {
            return new HiveCellPositions(slotB, slotA);
        }

        public void validate() {
            if (!slotA.isKnown()) {
                throw new IllegalStateException("HiveCellPositions slotA is unknown (NaN)");
            }
            if (!slotB.isKnown()) {
                throw new IllegalStateException("HiveCellPositions slotB is unknown (NaN)");
            }
        }
    }

    public static HiveCellPositions createHiveCellPositions(
            double slotAXIn, double slotAYIn,
            double slotBXIn, double slotBYIn) {
        return new HiveCellPositions(
                new CellTarget(slotAXIn, slotAYIn),
                new CellTarget(slotBXIn, slotBYIn));
    }

    public static double mirrorX(double xIn) {
        return FIELD_WIDTH_IN - xIn;
    }

    public static double mirrorY(double yIn) {
        return FIELD_HEIGHT_IN - yIn;
    }

    public static double mirrorHeading(double headingRad) {
        return AngleUtil.wrap(Math.PI - headingRad);
    }

    public static double mirrorPivotOffsetY(double pivotOffsetYIn) {
        return -pivotOffsetYIn;
    }

    public static class MirroredPose {
        public final double xIn;
        public final double yIn;
        public final double headingRad;

        public MirroredPose(double xIn, double yIn, double headingRad) {
            this.xIn = xIn;
            this.yIn = yIn;
            this.headingRad = headingRad;
        }
    }

    public static MirroredPose mirrorPose(double xIn, double yIn, double headingRad) {
        return new MirroredPose(mirrorX(xIn), yIn, mirrorHeading(headingRad));
    }

    public static class MirroredVelocity {
        public final double vxInPerSec;
        public final double vyInPerSec;
        public final double omegaRadPerSec;

        public MirroredVelocity(double vxInPerSec, double vyInPerSec, double omegaRadPerSec) {
            this.vxInPerSec = vxInPerSec;
            this.vyInPerSec = vyInPerSec;
            this.omegaRadPerSec = omegaRadPerSec;
        }
    }

    public static MirroredVelocity mirrorVelocity(double vxInPerSec, double vyInPerSec, double omegaRadPerSec) {
        return new MirroredVelocity(-vxInPerSec, vyInPerSec, -omegaRadPerSec);
    }

    public static CellTarget mirrorTarget(CellTarget target) {
        return new CellTarget(mirrorX(target.mouthXIn), target.mouthYIn);
    }

    public static HiveCellPositions mirrorHivePositions(HiveCellPositions positions) {
        return new HiveCellPositions(
                mirrorTarget(positions.slotA),
                mirrorTarget(positions.slotB));
    }
}