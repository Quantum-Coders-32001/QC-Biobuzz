package org.firstinspires.ftc.teamcode.math;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Synthetic-data tests for the least-squares fitter used by the flywheel characterizer. */
public class LinearFitTest {
    private static final double EPS = 1e-9;

    @Test public void perfectLinearDataRecoversExactSlopeAndIntercept() {
        // y = 0.0003*x + 0.07
        double[] x = {1000, 1200, 1400, 1600, 1800};
        double[] y = new double[5];
        for (int i = 0; i < x.length; i++) y[i] = 0.0003 * x[i] + 0.07;

        LinearFit.Result r = LinearFit.fit(x, y);

        assertNotNull(r);
        assertEquals(0.0003, r.slope, 1e-12);
        assertEquals(0.07, r.intercept, 1e-12);
        assertEquals(1.0, r.rSquared, 1e-9);
    }

    @Test public void noisyDataIsApproximatedAndClampedToPhysicalRange() {
        double[] x = {1000, 1200, 1400, 1600, 1800};
        double[] y = {0.37, 0.44, 0.49, 0.565, 0.61}; // slope ~0.00012ish

        LinearFit.Result r = LinearFit.fit(x, y);

        assertNotNull(r);
        assertTrue(r.slope > 0 && r.slope < 0.001);
        assertTrue(r.rSquared > 0.95);
    }

    @Test public void predictMatchesFitEquation() {
        double[] x = {0, 10, 20};
        double[] y = {1, 3, 5}; // m = 0.2, b = 1
        LinearFit.Result r = LinearFit.fit(x, y);

        assertNotNull(r);
        assertEquals(0.2, r.slope, 1e-9);
        assertEquals(1.0, r.intercept, 1e-9);
        assertEquals(5.0, r.predict(20), 1e-9);
        assertEquals(1.0, r.predict(0), 1e-9);
    }

    @Test public void rejectsBadInputs() {
        assertNull(LinearFit.fit(null, null));
        assertNull(LinearFit.fit(new double[]{1}, new double[]{1}));
        assertNull(LinearFit.fit(new double[]{1, 2}, new double[]{1}));
        assertNull(LinearFit.fit(new double[]{1, Double.NaN, 3}, new double[]{1, 2, 3}));
        assertNull(LinearFit.fit(new double[]{0, 0, 0}, new double[]{1, 2, 3})); // collinear x
    }

    @Test public void identicalYNearPerfectFlatFitStillReturnsResult() {
        // All y equal: sst ~ 0; fit is flat, r2 reported as 1. Must not blow up.
        LinearFit.Result r = LinearFit.fit(new double[]{1, 2, 3}, new double[]{2, 2, 2});

        assertNotNull(r);
        assertFalse(r.rSquared > 1.0);
        assertEquals(0.0, r.slope, 1e-9);
    }
}
