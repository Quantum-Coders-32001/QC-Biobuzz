package org.firstinspires.ftc.teamcode.field;

import org.firstinspires.ftc.teamcode.math.AngleUtil;
import org.junit.Test;
import static org.junit.Assert.*;

public class FieldGeometryTest {
    private static final double EPS = 1e-9;

    @Test
    public void testMirrorX() {
        assertEquals(144.0, FieldGeometry.mirrorX(0.0), EPS);
        assertEquals(72.0, FieldGeometry.mirrorX(72.0), EPS);
        assertEquals(0.0, FieldGeometry.mirrorX(144.0), EPS);
        assertEquals(100.0, FieldGeometry.mirrorX(44.0), EPS);
    }

    @Test
    public void testMirrorY() {
        assertEquals(144.0, FieldGeometry.mirrorY(0.0), EPS);
        assertEquals(72.0, FieldGeometry.mirrorY(72.0), EPS);
        assertEquals(0.0, FieldGeometry.mirrorY(144.0), EPS);
    }

    @Test
    public void testMirrorHeading() {
        assertEquals(Math.PI, FieldGeometry.mirrorHeading(0.0), EPS);
        assertEquals(0.0, FieldGeometry.mirrorHeading(Math.PI), EPS);
        assertEquals(Math.PI / 2, FieldGeometry.mirrorHeading(Math.PI / 2), EPS);
        assertEquals(-Math.PI / 2, FieldGeometry.mirrorHeading(-Math.PI / 2), EPS);
    }

    @Test
    public void testMirrorPivotOffsetY() {
        assertEquals(-5.0, FieldGeometry.mirrorPivotOffsetY(5.0), EPS);
        assertEquals(3.0, FieldGeometry.mirrorPivotOffsetY(-3.0), EPS);
    }

    @Test
    public void testMirrorPose() {
        FieldGeometry.MirroredPose mirrored = FieldGeometry.mirrorPose(20.0, 30.0, Math.PI / 4);
        assertEquals(124.0, mirrored.xIn, EPS);
        assertEquals(30.0, mirrored.yIn, EPS);
        assertEquals(AngleUtil.wrap(Math.PI - Math.PI / 4), mirrored.headingRad, EPS);
    }

    @Test
    public void testMirrorVelocity() {
        FieldGeometry.MirroredVelocity mirrored = FieldGeometry.mirrorVelocity(10.0, 5.0, 0.5);
        assertEquals(-10.0, mirrored.vxInPerSec, EPS);
        assertEquals(5.0, mirrored.vyInPerSec, EPS);
        assertEquals(-0.5, mirrored.omegaRadPerSec, EPS);
    }

    @Test
    public void testMirrorTarget() {
        FieldGeometry.CellTarget target = new FieldGeometry.CellTarget(100.0, 80.0);
        FieldGeometry.CellTarget mirrored = FieldGeometry.mirrorTarget(target);
        assertEquals(44.0, mirrored.mouthXIn, EPS);
        assertEquals(80.0, mirrored.mouthYIn, EPS);
    }

    @Test
    public void testMirrorHivePositions() {
        FieldGeometry.HiveCellPositions original = FieldGeometry.createHiveCellPositions(
                70.0, 80.0,
                70.0, 60.0);

        FieldGeometry.HiveCellPositions mirrored = FieldGeometry.mirrorHivePositions(original);

        assertEquals(74.0, mirrored.slotA.mouthXIn, EPS);
        assertEquals(80.0, mirrored.slotA.mouthYIn, EPS);
        assertEquals(74.0, mirrored.slotB.mouthXIn, EPS);
        assertEquals(60.0, mirrored.slotB.mouthYIn, EPS);
    }

    @Test
    public void testCellTargetUnknown() {
        FieldGeometry.CellTarget unknown = FieldGeometry.CellTarget.unknown();
        assertFalse(unknown.isKnown());
        assertTrue(Double.isNaN(unknown.mouthXIn));
        assertTrue(Double.isNaN(unknown.mouthYIn));
    }

    @Test
    public void testCellTargetKnown() {
        FieldGeometry.CellTarget known = new FieldGeometry.CellTarget(10.0, 20.0);
        assertTrue(known.isKnown());
    }

    @Test
    public void testHiveCellPositionsGetUpwardCell() {
        FieldGeometry.CellTarget slotA = new FieldGeometry.CellTarget(10.0, 20.0);
        FieldGeometry.CellTarget slotB = new FieldGeometry.CellTarget(30.0, 40.0);
        FieldGeometry.HiveCellPositions hive = new FieldGeometry.HiveCellPositions(slotA, slotB);

        assertSame(slotA, hive.getUpwardCell(FieldGeometry.CellSlot.A));
        assertSame(slotB, hive.getUpwardCell(FieldGeometry.CellSlot.B));
    }

    @Test
    public void testHiveCellPositionsAfterTip() {
        FieldGeometry.CellTarget slotA = new FieldGeometry.CellTarget(10.0, 20.0);
        FieldGeometry.CellTarget slotB = new FieldGeometry.CellTarget(30.0, 40.0);
        FieldGeometry.HiveCellPositions hive = new FieldGeometry.HiveCellPositions(slotA, slotB);

        FieldGeometry.HiveCellPositions afterTip = hive.afterTip();

        assertSame(slotB, afterTip.slotA);
        assertSame(slotA, afterTip.slotB);
    }

    @Test(expected = IllegalStateException.class)
    public void testHiveCellPositionsValidateThrowsOnUnknownA() {
        FieldGeometry.HiveCellPositions hive = new FieldGeometry.HiveCellPositions(
                FieldGeometry.CellTarget.unknown(),
                new FieldGeometry.CellTarget(30.0, 40.0));
        hive.validate();
    }

    @Test(expected = IllegalStateException.class)
    public void testHiveCellPositionsValidateThrowsOnUnknownB() {
        FieldGeometry.HiveCellPositions hive = new FieldGeometry.HiveCellPositions(
                new FieldGeometry.CellTarget(10.0, 20.0),
                FieldGeometry.CellTarget.unknown());
        hive.validate();
    }

    @Test
    public void testCreateHiveCellPositions() {
        FieldGeometry.HiveCellPositions hive = FieldGeometry.createHiveCellPositions(
                72.0, 80.0,
                72.0, 60.0);

        assertEquals(72.0, hive.slotA.mouthXIn, EPS);
        assertEquals(80.0, hive.slotA.mouthYIn, EPS);
        assertEquals(72.0, hive.slotB.mouthXIn, EPS);
        assertEquals(60.0, hive.slotB.mouthYIn, EPS);
    }
}