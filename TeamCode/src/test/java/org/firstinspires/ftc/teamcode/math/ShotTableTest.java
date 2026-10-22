package org.firstinspires.ftc.teamcode.math;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ShotTableTest {
    private static ShotTable table() {
        return new ShotTable(new double[]{24, 48, 96}, new double[]{1000, 2000, 3000});
    }

    @Test public void exactPointsReturnTheirValues() {
        assertEquals(1000, table().get(24), 1e-9);
        assertEquals(2000, table().get(48), 1e-9);
        assertEquals(3000, table().get(96), 1e-9);
    }

    @Test public void interpolatesLinearlyWithinASegment() {
        assertEquals(1500, table().get(36), 1e-9);
        assertEquals(2500, table().get(72), 1e-9);
    }

    @Test public void clampsOutsideTheRangeInsteadOfExtrapolating() {
        assertEquals(1000, table().get(0), 1e-9);
        assertEquals(1000, table().get(-50), 1e-9);
        assertEquals(3000, table().get(500), 1e-9);
        assertEquals(3000, table().get(Double.POSITIVE_INFINITY), 1e-9);
        assertEquals(1000, table().get(Double.NEGATIVE_INFINITY), 1e-9);
    }

    @Test public void nanInputReturnsNan() {
        assertTrue(Double.isNaN(table().get(Double.NaN)));
    }

    @Test public void monotonicSweepNeverGoesBackwardsOrOutOfBounds() {
        ShotTable t = table();
        double prev = t.get(0);
        for (double d = 0; d <= 120; d += 0.25) {
            double v = t.get(d);
            assertTrue("went backwards at " + d, v >= prev - 1e-9);
            assertTrue(v >= 1000 && v <= 3000);
            prev = v;
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsFewerThanTwoPoints() {
        new ShotTable(new double[]{1}, new double[]{1});
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsLengthMismatch() {
        new ShotTable(new double[]{1, 2}, new double[]{1});
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonIncreasingDistances() {
        new ShotTable(new double[]{1, 1}, new double[]{1, 2});
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsDecreasingDistances() {
        new ShotTable(new double[]{2, 1}, new double[]{1, 2});
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNanValues() {
        new ShotTable(new double[]{1, 2}, new double[]{1, Double.NaN});
    }
}
