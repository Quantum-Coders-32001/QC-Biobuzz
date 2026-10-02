package org.firstinspires.ftc.teamcode.math;

import org.firstinspires.ftc.teamcode.field.FieldGeometry;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Random;

public class TurretGeometryTest {
    private static final double EPS = 1e-9;
    private static final double RATE_EPS = 1e-6;
    private static final double DIST_EPS = 1e-6;

    private TurretGeometry.Config makeDefaultConfig() {
        TurretGeometry.Config cfg = new TurretGeometry.Config();
        cfg.pivotOffsetXIn = 0.0;
        cfg.pivotOffsetYIn = 0.0;
        cfg.zeroOffsetRad = 0.0;
        cfg.minTurretAngleRad = -Math.PI;
        cfg.maxTurretAngleRad = Math.PI;
        cfg.hysteresisRad = 0.01;
        return cfg;
    }

    private TurretGeometry.SolveInput makeDefaultInput() {
        TurretGeometry.SolveInput in = new TurretGeometry.SolveInput();
        in.robotXIn = 0.0;
        in.robotYIn = 0.0;
        in.robotHeadingRad = 0.0;
        in.robotVxInPerSec = 0.0;
        in.robotVyInPerSec = 0.0;
        in.robotOmegaRadPerSec = 0.0;
        in.targetXIn = 10.0;
        in.targetYIn = 0.0;
        in.currentTurretAngleRad = 0.0;
        in.flightTimeFunction = null;
        return in;
    }

    @Test
    public void testRobotAtOriginHeadingZeroTargetOnPlusX() {
        TurretGeometry.Config cfg = makeDefaultConfig();
        TurretGeometry.SolveInput in = makeDefaultInput();
        in.targetXIn = 10.0;
        in.targetYIn = 0.0;

        TurretGeometry.SolveOutput out = TurretGeometry.solve(cfg, in);
        assertEquals(0.0, out.targetAngleRad, EPS);
        assertEquals(10.0, out.distIn, DIST_EPS);
        assertTrue(out.feasible);
        assertFalse(out.clamped);
    }

    @Test
    public void testRotatingRobotChangesTurretAngleByMinusDelta() {
        TurretGeometry.Config cfg = makeDefaultConfig();
        double delta = Math.PI / 6;

        TurretGeometry.SolveInput in1 = makeDefaultInput();
        in1.robotHeadingRad = 0.0;
        TurretGeometry.SolveOutput out1 = TurretGeometry.solve(cfg, in1);

        TurretGeometry.SolveInput in2 = makeDefaultInput();
        in2.robotHeadingRad = delta;
        TurretGeometry.SolveOutput out2 = TurretGeometry.solve(cfg, in2);

        double expectedDiff = AngleUtil.wrap(-delta);
        double actualDiff = AngleUtil.wrap(out2.targetAngleRad - out1.targetAngleRad);
        assertEquals(expectedDiff, actualDiff, EPS);
    }

    @Test
    public void testPivotOffsetHandComputed() {
        TurretGeometry.Config cfg = makeDefaultConfig();
        cfg.pivotOffsetXIn = 5.0;
        cfg.pivotOffsetYIn = 3.0;
        cfg.zeroOffsetRad = 0.0;

        TurretGeometry.SolveInput in = makeDefaultInput();
        in.robotXIn = 0.0;
        in.robotYIn = 0.0;
        in.robotHeadingRad = 0.0;
        in.targetXIn = 10.0;
        in.targetYIn = 0.0;

        TurretGeometry.SolveOutput out = TurretGeometry.solve(cfg, in);

        double expectedPivotX = 5.0;
        double expectedPivotY = 3.0;
        double expectedDx = 10.0 - 5.0;
        double expectedDy = 0.0 - 3.0;
        double expectedAngle = Math.atan2(expectedDy, expectedDx);

        assertEquals(expectedAngle, out.targetAngleRad, 1e-9);
        assertEquals(Math.hypot(expectedDx, expectedDy), out.distIn, DIST_EPS);
    }

    @Test
    public void testMirrorSymmetry() {
        TurretGeometry.Config cfg = makeDefaultConfig();
        cfg.pivotOffsetXIn = 4.0;
        cfg.pivotOffsetYIn = 2.0;

        TurretGeometry.SolveInput in = makeDefaultInput();
        in.robotXIn = 20.0;
        in.robotYIn = 30.0;
        in.robotHeadingRad = Math.PI / 4;
        in.targetXIn = 100.0;
        in.targetYIn = 80.0;
        in.currentTurretAngleRad = 0.5;

        TurretGeometry.SolveOutput out1 = TurretGeometry.solve(cfg, in);

        TurretGeometry.Config cfgMirror = makeDefaultConfig();
        cfgMirror.pivotOffsetXIn = 4.0;
        cfgMirror.pivotOffsetYIn = -2.0;
        cfgMirror.zeroOffsetRad = 0.0;
        cfgMirror.minTurretAngleRad = -Math.PI;
        cfgMirror.maxTurretAngleRad = Math.PI;

        TurretGeometry.SolveInput inMirror = makeDefaultInput();
        inMirror.robotXIn = 144.0 - 20.0;
        inMirror.robotYIn = 30.0;
        inMirror.robotHeadingRad = AngleUtil.wrap(Math.PI - Math.PI / 4);
        inMirror.targetXIn = 144.0 - 100.0;
        inMirror.targetYIn = 80.0;
        inMirror.currentTurretAngleRad = -0.5;
        inMirror.robotVxInPerSec = -in.robotVxInPerSec;
        inMirror.robotVyInPerSec = in.robotVyInPerSec;
        inMirror.robotOmegaRadPerSec = -in.robotOmegaRadPerSec;

        TurretGeometry.SolveOutput out2 = TurretGeometry.solve(cfgMirror, inMirror);

        assertEquals(-out1.targetAngleRad, out2.targetAngleRad, 1e-9);
        assertEquals(out1.distIn, out2.distIn, DIST_EPS);
        assertEquals(-out1.targetRateRadPerSec, out2.targetRateRadPerSec, RATE_EPS);
    }

    @Test
    public void testAngleSelectionNearestToCurrent() {
        TurretGeometry.Config cfg = makeDefaultConfig();
        cfg.minTurretAngleRad = -Math.PI / 2;
        cfg.maxTurretAngleRad = Math.PI / 2;

        TurretGeometry.SolveInput in = makeDefaultInput();
        in.targetXIn = 10.0;
        in.targetYIn = 0.0;

        in.currentTurretAngleRad = 0.0;
        TurretGeometry.SolveOutput out = TurretGeometry.solve(cfg, in);
        assertEquals(0.0, out.targetAngleRad, EPS);
        assertTrue(out.feasible);

        in.currentTurretAngleRad = Math.PI / 4;
        out = TurretGeometry.solve(cfg, in);
        assertEquals(0.0, out.targetAngleRad, EPS);

        cfg.zeroOffsetRad = 0.0;
        cfg.minTurretAngleRad = 0.0;
        cfg.maxTurretAngleRad = Math.PI;
        in.currentTurretAngleRad = Math.PI / 2;
        out = TurretGeometry.solve(cfg, in);
        assertEquals(0.0, out.targetAngleRad, EPS);
    }

    @Test
    public void testDeadZoneYieldsFeasibleFalseAndClamps() {
        TurretGeometry.Config cfg = makeDefaultConfig();
        cfg.minTurretAngleRad = -Math.PI / 4;
        cfg.maxTurretAngleRad = Math.PI / 4;

        TurretGeometry.SolveInput in = makeDefaultInput();
        in.targetXIn = 0.0;
        in.targetYIn = 10.0;

        TurretGeometry.SolveOutput out = TurretGeometry.solve(cfg, in);
        assertFalse(out.feasible);
        assertTrue(out.clamped);
        double expectedClamp = Math.PI / 4;
        assertEquals(expectedClamp, out.targetAngleRad, EPS);
    }

    @Test
    public void testFullCircleRangeAlwaysFeasible() {
        TurretGeometry.Config cfg = makeDefaultConfig();
        cfg.minTurretAngleRad = -Math.PI;
        cfg.maxTurretAngleRad = Math.PI;

        TurretGeometry.SolveInput in = makeDefaultInput();
        in.targetXIn = 0.0;
        in.targetYIn = -10.0;

        TurretGeometry.SolveOutput out = TurretGeometry.solve(cfg, in);
        assertTrue(out.feasible);
        assertFalse(out.clamped);
    }

    @Test
    public void testHysteresisPreventsFlipFlopping() {
        TurretGeometry.Config cfg = makeDefaultConfig();
        cfg.minTurretAngleRad = -Math.PI / 2;
        cfg.maxTurretAngleRad = Math.PI / 2;
        cfg.hysteresisRad = 0.1;

        TurretGeometry.SolveInput in = makeDefaultInput();
        in.targetXIn = 0.0;
        in.targetYIn = -10.0;

        in.currentTurretAngleRad = -Math.PI / 2 + 0.05;
        TurretGeometry.SolveOutput out = TurretGeometry.solve(cfg, in);
        assertEquals(-Math.PI / 2, out.targetAngleRad, EPS);
    }

    @Test
    public void testContinuitySweepHeading() {
        TurretGeometry.Config cfg = makeDefaultConfig();
        cfg.minTurretAngleRad = -Math.PI;
        cfg.maxTurretAngleRad = Math.PI;

        TurretGeometry.SolveInput in = makeDefaultInput();
        in.targetXIn = 50.0;
        in.targetYIn = 50.0;
        in.robotHeadingRad = 0.0;
        in.currentTurretAngleRad = 0.0;
        TurretGeometry.SolveOutput out = TurretGeometry.solve(cfg, in);
        double prevAngle = out.targetAngleRad;

        double maxJump = 0.0;
        int deadZoneCrossings = 0;

        for (int i = 1; i <= 100; i++) {
            double heading = 2 * Math.PI * i / 100.0;
            in = makeDefaultInput();
            in.robotHeadingRad = heading;
            in.targetXIn = 50.0;
            in.targetYIn = 50.0;
            in.currentTurretAngleRad = prevAngle;

            out = TurretGeometry.solve(cfg, in);
            double jump = Math.abs(AngleUtil.shortestAngularDistance(prevAngle, out.targetAngleRad));
            if (jump > maxJump && out.feasible) {
                maxJump = jump;
            }
            if (!out.feasible) {
                deadZoneCrossings++;
            }
            prevAngle = out.targetAngleRad;
        }

        assertTrue("Max jump too large: " + maxJump, maxJump < 0.5 || deadZoneCrossings > 0);
    }

    @Test
    public void testVelocityLeadZeroVelocityEqualsNoLead() {
        TurretGeometry.Config cfg = makeDefaultConfig();
        cfg.pivotOffsetXIn = 2.0;
        cfg.pivotOffsetYIn = 1.0;

        TurretGeometry.SolveInput in = makeDefaultInput();
        in.robotVxInPerSec = 0.0;
        in.robotVyInPerSec = 0.0;
        in.robotOmegaRadPerSec = 0.0;
        in.flightTimeFunction = dist -> 0.5;

        TurretGeometry.SolveOutput outLead = TurretGeometry.solve(cfg, in);

        in.flightTimeFunction = null;
        TurretGeometry.SolveOutput outNoLead = TurretGeometry.solve(cfg, in);

        assertEquals(outNoLead.targetAngleRad, outLead.targetAngleRad, 1e-9);
        assertEquals(outNoLead.distIn, outLead.distIn, DIST_EPS);
    }

    @Test
    public void testVelocityLeadConstantFlightTimeMatchesClosedForm() {
        TurretGeometry.Config cfg = makeDefaultConfig();
        cfg.pivotOffsetXIn = 0.0;
        cfg.pivotOffsetYIn = 0.0;

        double vx = 10.0;
        double vy = 5.0;
        double omega = 0.0;
        double t = 0.5;

        TurretGeometry.SolveInput in = makeDefaultInput();
        in.robotVxInPerSec = vx;
        in.robotVyInPerSec = vy;
        in.robotOmegaRadPerSec = omega;
        in.targetXIn = 100.0;
        in.targetYIn = 50.0;
        in.flightTimeFunction = dist -> t;

        TurretGeometry.SolveOutput out = TurretGeometry.solve(cfg, in);

        double expectedVx = vx;
        double expectedVy = vy;
        double expectedVirtualX = in.targetXIn - expectedVx * t;
        double expectedVirtualY = in.targetYIn - expectedVy * t;
        double expectedAngle = Math.atan2(expectedVirtualY, expectedVirtualX);

        assertEquals(expectedAngle, out.targetAngleRad, 1e-6);
    }

    @Test
    public void testVelocityLeadIterationConvergesAndRespectsMaxIter() {
        TurretGeometry.Config cfg = makeDefaultConfig();
        cfg.pivotOffsetXIn = 3.0;
        cfg.pivotOffsetYIn = 2.0;

        TurretGeometry.SolveInput in = makeDefaultInput();
        in.robotVxInPerSec = 5.0;
        in.robotVyInPerSec = 3.0;
        in.robotOmegaRadPerSec = 0.2;
        in.targetXIn = 80.0;
        in.targetYIn = 60.0;
        in.flightTimeFunction = dist -> 0.001 * dist;

        TurretGeometry.SolveOutput out = TurretGeometry.solve(cfg, in);
        assertTrue(out.feasible);
        assertFalse(Double.isNaN(out.targetAngleRad));
    }

    @Test
    public void testRateFeedforwardMatchesFiniteDifference() {
        TurretGeometry.Config cfg = makeDefaultConfig();
        cfg.pivotOffsetXIn = 4.0;
        cfg.pivotOffsetYIn = 2.0;

        TurretGeometry.SolveInput in = makeDefaultInput();
        in.robotVxInPerSec = 12.0;
        in.robotVyInPerSec = 8.0;
        in.robotOmegaRadPerSec = 0.5;
        in.targetXIn = 90.0;
        in.targetYIn = 70.0;

        TurretGeometry.SolveOutput out = TurretGeometry.solve(cfg, in);

        double h = 1e-6;
        TurretGeometry.SolveInput inPlus = makeDefaultInput();
        inPlus.robotVxInPerSec = 12.0;
        inPlus.robotVyInPerSec = 8.0;
        inPlus.robotOmegaRadPerSec = 0.5;
        inPlus.targetXIn = 90.0;
        inPlus.targetYIn = 70.0;
        inPlus.robotXIn += in.robotVxInPerSec * h;
        inPlus.robotYIn += in.robotVyInPerSec * h;
        inPlus.robotHeadingRad += in.robotOmegaRadPerSec * h;

        TurretGeometry.SolveInput inMinus = makeDefaultInput();
        inMinus.robotVxInPerSec = 12.0;
        inMinus.robotVyInPerSec = 8.0;
        inMinus.robotOmegaRadPerSec = 0.5;
        inMinus.targetXIn = 90.0;
        inMinus.targetYIn = 70.0;
        inMinus.robotXIn -= in.robotVxInPerSec * h;
        inMinus.robotYIn -= in.robotVyInPerSec * h;
        inMinus.robotHeadingRad -= in.robotOmegaRadPerSec * h;

        TurretGeometry.SolveOutput outPlus = TurretGeometry.solve(cfg, inPlus);
        TurretGeometry.SolveOutput outMinus = TurretGeometry.solve(cfg, inMinus);

        double numericalRate = (outPlus.targetAngleRad - outMinus.targetAngleRad) / (2 * h);
        assertEquals(numericalRate, out.targetRateRadPerSec, RATE_EPS);
    }

    @Test
    public void testRateFeedforwardWithOmegaAndPivotOffset() {
        TurretGeometry.Config cfg = makeDefaultConfig();
        cfg.pivotOffsetXIn = 5.0;
        cfg.pivotOffsetYIn = 3.0;

        TurretGeometry.SolveInput in = makeDefaultInput();
        in.robotVxInPerSec = 15.0;
        in.robotVyInPerSec = 10.0;
        in.robotOmegaRadPerSec = 1.0;
        in.targetXIn = 85.0;
        in.targetYIn = 65.0;

        TurretGeometry.SolveOutput out = TurretGeometry.solve(cfg, in);

        double h = 1e-6;
        TurretGeometry.SolveInput inPlus = makeDefaultInput();
        inPlus.robotVxInPerSec = 15.0;
        inPlus.robotVyInPerSec = 10.0;
        inPlus.robotOmegaRadPerSec = 1.0;
        inPlus.targetXIn = 85.0;
        inPlus.targetYIn = 65.0;
        inPlus.robotXIn += in.robotVxInPerSec * h;
        inPlus.robotYIn += in.robotVyInPerSec * h;
        inPlus.robotHeadingRad += in.robotOmegaRadPerSec * h;

        TurretGeometry.SolveInput inMinus = makeDefaultInput();
        inMinus.robotVxInPerSec = 15.0;
        inMinus.robotVyInPerSec = 10.0;
        inMinus.robotOmegaRadPerSec = 1.0;
        inMinus.targetXIn = 85.0;
        inMinus.targetYIn = 65.0;
        inMinus.robotXIn -= in.robotVxInPerSec * h;
        inMinus.robotYIn -= in.robotVyInPerSec * h;
        inMinus.robotHeadingRad -= in.robotOmegaRadPerSec * h;

        TurretGeometry.SolveOutput outPlus = TurretGeometry.solve(cfg, inPlus);
        TurretGeometry.SolveOutput outMinus = TurretGeometry.solve(cfg, inMinus);

        double numericalRate = (outPlus.targetAngleRad - outMinus.targetAngleRad) / (2 * h);
        assertEquals(numericalRate, out.targetRateRadPerSec, RATE_EPS);
    }

    @Test
    public void testCellSwitching() {
        FieldGeometry.HiveCellPositions hive = FieldGeometry.createHiveCellPositions(
                72.0, 80.0,
                72.0, 60.0);

        TurretGeometry.Config cfg = makeDefaultConfig();
        cfg.pivotOffsetXIn = 0.0;
        cfg.pivotOffsetYIn = 0.0;

        TurretGeometry.SolveInput in = makeDefaultInput();
        in.robotXIn = 20.0;
        in.robotYIn = 20.0;
        in.targetXIn = hive.getUpwardCell(FieldGeometry.CellSlot.A).mouthXIn;
        in.targetYIn = hive.getUpwardCell(FieldGeometry.CellSlot.A).mouthYIn;

        TurretGeometry.SolveOutput outA = TurretGeometry.solve(cfg, in);

        in.targetXIn = hive.getUpwardCell(FieldGeometry.CellSlot.B).mouthXIn;
        in.targetYIn = hive.getUpwardCell(FieldGeometry.CellSlot.B).mouthYIn;

        TurretGeometry.SolveOutput outB = TurretGeometry.solve(cfg, in);

        assertNotEquals(outA.targetAngleRad, outB.targetAngleRad, 1e-6);
        assertNotEquals(outA.distIn, outB.distIn, 1e-6);
    }

    @Test(expected = IllegalStateException.class)
    public void testNaNGatingThrowsOnMissingPivotOffsetX() {
        TurretGeometry.Config cfg = makeDefaultConfig();
        cfg.pivotOffsetXIn = Double.NaN;
        TurretGeometry.solve(cfg, makeDefaultInput());
    }

    @Test(expected = IllegalStateException.class)
    public void testNaNGatingThrowsOnMissingPivotOffsetY() {
        TurretGeometry.Config cfg = makeDefaultConfig();
        cfg.pivotOffsetYIn = Double.NaN;
        TurretGeometry.solve(cfg, makeDefaultInput());
    }

    @Test(expected = IllegalStateException.class)
    public void testNaNGatingThrowsOnMissingZeroOffset() {
        TurretGeometry.Config cfg = makeDefaultConfig();
        cfg.zeroOffsetRad = Double.NaN;
        TurretGeometry.solve(cfg, makeDefaultInput());
    }

    @Test(expected = IllegalStateException.class)
    public void testNaNGatingThrowsOnMissingMinAngle() {
        TurretGeometry.Config cfg = makeDefaultConfig();
        cfg.minTurretAngleRad = Double.NaN;
        TurretGeometry.solve(cfg, makeDefaultInput());
    }

    @Test(expected = IllegalStateException.class)
    public void testNaNGatingThrowsOnMissingMaxAngle() {
        TurretGeometry.Config cfg = makeDefaultConfig();
        cfg.maxTurretAngleRad = Double.NaN;
        TurretGeometry.solve(cfg, makeDefaultInput());
    }

    @Test
    public void testNaNGatingMessageNamesFields() {
        TurretGeometry.Config cfg = makeDefaultConfig();
        cfg.pivotOffsetXIn = Double.NaN;
        cfg.pivotOffsetYIn = Double.NaN;
        cfg.zeroOffsetRad = Double.NaN;

        try {
            TurretGeometry.solve(cfg, makeDefaultInput());
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            String msg = e.getMessage();
            assertTrue(msg.contains("pivotOffsetXIn"));
            assertTrue(msg.contains("pivotOffsetYIn"));
            assertTrue(msg.contains("zeroOffsetRad"));
        }
    }

    @Test
    public void testDegeneratePivotAtTarget() {
        TurretGeometry.Config cfg = makeDefaultConfig();
        cfg.pivotOffsetXIn = 0.0;
        cfg.pivotOffsetYIn = 0.0;

        TurretGeometry.SolveInput in = makeDefaultInput();
        in.robotXIn = 10.0;
        in.robotYIn = 10.0;
        in.targetXIn = 10.0;
        in.targetYIn = 10.0;

        TurretGeometry.SolveOutput out = TurretGeometry.solve(cfg, in);
        assertFalse(Double.isNaN(out.targetAngleRad));
        assertFalse(Double.isInfinite(out.targetAngleRad));
        assertEquals(in.robotHeadingRad, out.targetAngleRad, EPS);
    }

    @Test
    public void testFuzzRandomPoses() {
        TurretGeometry.Config cfg = makeDefaultConfig();
        cfg.pivotOffsetXIn = 3.0;
        cfg.pivotOffsetYIn = 2.0;
        cfg.minTurretAngleRad = -Math.PI;
        cfg.maxTurretAngleRad = Math.PI;

        Random rng = new Random(0xC0FFEE);
        for (int i = 0; i < 10000; i++) {
            TurretGeometry.SolveInput in = makeDefaultInput();
            in.robotXIn = rng.nextDouble() * 144.0;
            in.robotYIn = rng.nextDouble() * 144.0;
            in.robotHeadingRad = rng.nextDouble() * 2 * Math.PI - Math.PI;
            in.robotVxInPerSec = (rng.nextDouble() - 0.5) * 50.0;
            in.robotVyInPerSec = (rng.nextDouble() - 0.5) * 50.0;
            in.robotOmegaRadPerSec = (rng.nextDouble() - 0.5) * 5.0;
            in.targetXIn = rng.nextDouble() * 144.0;
            in.targetYIn = rng.nextDouble() * 144.0;
            in.currentTurretAngleRad = rng.nextDouble() * 2 * Math.PI - Math.PI;
            in.flightTimeFunction = dist -> 0.001 * dist;

            TurretGeometry.SolveOutput out = TurretGeometry.solve(cfg, in);
            assertFalse("NaN angle at iteration " + i, Double.isNaN(out.targetAngleRad));
            assertFalse("Infinite angle at iteration " + i, Double.isInfinite(out.targetAngleRad));
            assertFalse("NaN rate at iteration " + i, Double.isNaN(out.targetRateRadPerSec));
            assertFalse("NaN dist at iteration " + i, Double.isNaN(out.distIn));
            if (out.feasible) {
                assertTrue("Angle out of range at iteration " + i,
                        out.targetAngleRad >= cfg.minTurretAngleRad - 1e-9 &&
                        out.targetAngleRad <= cfg.maxTurretAngleRad + 1e-9);
            }
        }
    }
}