package org.firstinspires.ftc.teamcode.math;

import org.junit.Test;
import static org.junit.Assert.*;

public class AngleUtilTest {
    private static final double EPS = 1e-9;

    @Test
    public void testWrap() {
        assertEquals(0.0, AngleUtil.wrap(0.0), EPS);
        assertEquals(Math.PI, AngleUtil.wrap(Math.PI), EPS);
        assertEquals(Math.PI, AngleUtil.wrap(-Math.PI), EPS);
        assertEquals(Math.PI / 2, AngleUtil.wrap(5 * Math.PI / 2), EPS);
        assertEquals(-Math.PI / 2, AngleUtil.wrap(-5 * Math.PI / 2), EPS);
        assertEquals(0.1, AngleUtil.wrap(0.1 + 2 * Math.PI), EPS);
        assertEquals(-0.1, AngleUtil.wrap(-0.1 - 2 * Math.PI), EPS);
    }

    @Test
    public void testWrapPositive() {
        assertEquals(0.0, AngleUtil.wrapPositive(0.0), EPS);
        assertEquals(Math.PI, AngleUtil.wrapPositive(Math.PI), EPS);
        assertEquals(Math.PI, AngleUtil.wrapPositive(-Math.PI), EPS);
        assertEquals(Math.PI / 2, AngleUtil.wrapPositive(5 * Math.PI / 2), EPS);
        assertEquals(3 * Math.PI / 2, AngleUtil.wrapPositive(-Math.PI / 2), EPS);
    }

    @Test
    public void testShortestAngularDistance() {
        assertEquals(Math.PI / 2, AngleUtil.shortestAngularDistance(0, Math.PI / 2), EPS);
        assertEquals(-Math.PI / 2, AngleUtil.shortestAngularDistance(Math.PI / 2, 0), EPS);
        assertEquals(-0.2, AngleUtil.shortestAngularDistance(-Math.PI + 0.1, Math.PI - 0.1), EPS);
    }

    @Test
    public void testIsAngleInRange() {
        assertTrue(AngleUtil.isAngleInRange(0, -Math.PI / 2, Math.PI / 2));
        assertTrue(AngleUtil.isAngleInRange(Math.PI / 4, -Math.PI / 2, Math.PI / 2));
        assertFalse(AngleUtil.isAngleInRange(Math.PI, -Math.PI / 2, Math.PI / 2));
        assertTrue(AngleUtil.isAngleInRange(0, -Math.PI, Math.PI));
        assertTrue(AngleUtil.isAngleInRange(Math.PI - 0.1, Math.PI / 2, -Math.PI / 2));
        assertTrue(AngleUtil.isAngleInRange(-Math.PI + 0.1, Math.PI / 2, -Math.PI / 2));
    }

    @Test
    public void testClampToRange() {
        assertEquals(0.0, AngleUtil.clampToRange(0.0, -1.0, 1.0), EPS);
        assertEquals(1.0, AngleUtil.clampToRange(2.0, -1.0, 1.0), EPS);
        assertEquals(-1.0, AngleUtil.clampToRange(-2.0, -1.0, 1.0), EPS);
        assertEquals(-1.0, AngleUtil.clampToRange(10.0, -1.0, 1.0), EPS);
    }

    @Test
    public void testFindEquivalentAngleInRange() {
        double min = -Math.PI / 2;
        double max = Math.PI / 2;

        assertEquals(0.0, AngleUtil.findEquivalentAngleInRange(0.0, min, max, 0.0), EPS);
        assertEquals(Math.PI / 4, AngleUtil.findEquivalentAngleInRange(Math.PI / 4, min, max, 0.0), EPS);
        assertEquals(-Math.PI / 4, AngleUtil.findEquivalentAngleInRange(-Math.PI / 4, min, max, 0.0), EPS);

        double angle = 3 * Math.PI / 4;
        double result = AngleUtil.findEquivalentAngleInRange(angle, min, max, 0.0);
        assertEquals(Math.PI / 2, result, EPS);

        double fullMin = -Math.PI;
        double fullMax = Math.PI;
        assertEquals(Math.PI - 0.1, AngleUtil.findEquivalentAngleInRange(Math.PI - 0.1, fullMin, fullMax, 0.0), EPS);
    }

    @Test
    public void testFindEquivalentAngleInRangeWithHysteresis() {
        double min = -Math.PI / 2;
        double max = Math.PI / 2;
        double hysteresis = 0.1;

        double result = AngleUtil.findEquivalentAngleInRangeWithHysteresis(min + 0.05, min, max, min, hysteresis);
        assertEquals(min, result, EPS);

        result = AngleUtil.findEquivalentAngleInRangeWithHysteresis(max - 0.05, min, max, max, hysteresis);
        assertEquals(max, result, EPS);

        result = AngleUtil.findEquivalentAngleInRangeWithHysteresis(0.0, min, max, 0.0, hysteresis);
        assertEquals(0.0, result, EPS);
    }
}