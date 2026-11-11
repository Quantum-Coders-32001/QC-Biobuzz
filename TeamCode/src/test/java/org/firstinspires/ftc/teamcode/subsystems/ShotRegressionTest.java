package org.firstinspires.ftc.teamcode.subsystems;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Off-robot proof for the shot regression (AGENTS.md §6 L1). No FTC imports are involved, which is
 * the whole reason {@link ShotRegression} and {@link ShotMap} are plain Java.
 */
public class ShotRegressionTest {

    private static double[] line(double[] xs, double slope, double intercept) {
        double[] ys = new double[xs.length];
        for (int i = 0; i < xs.length; i++) ys[i] = slope * xs[i] + intercept;
        return ys;
    }

    // -------------------------------------------------------------- exact fits
    @Test
    public void exactLineIsRecovered() {
        double[] xs = {50, 60, 70, 80};
        ShotRegression fit = ShotRegression.fit(xs, line(xs, 2.5, 7.0), 2);
        assertNotNull(fit);
        // Data is linear, so a quadratic is allowed but the prediction must still be the line.
        assertEquals(2.5 * 55 + 7.0, fit.predictRaw(55), 1e-6);
        assertEquals(2.5 * 95 + 7.0, fit.predictRaw(95), 1e-6);
        assertEquals(1.0, fit.rSquared(), 1e-9);
        assertEquals(0.0, fit.maxAbsResidual(), 1e-6);
        assertEquals(4, fit.pointCount());
    }

    @Test
    public void exactQuadraticIsRecovered() {
        double[] xs = {40, 55, 70, 85, 100};
        double[] ys = new double[xs.length];
        for (int i = 0; i < xs.length; i++) ys[i] = 0.5 * xs[i] * xs[i] - 3.0 * xs[i] + 10.0;

        ShotRegression fit = ShotRegression.fit(xs, ys, 2);
        assertNotNull(fit);
        assertEquals(2, fit.degree());
        // Centring + re-expansion must not lose the curve: check between the samples too.
        for (double x = 40; x <= 100; x += 5) {
            assertEquals(0.5 * x * x - 3.0 * x + 10.0, fit.predictRaw(x), 1e-6);
        }
        assertEquals(1.0, fit.rSquared(), 1e-9);
    }

    @Test
    public void quadraticNeedsFourPoints() {
        double[] xs = {50, 60, 70};
        ShotRegression fit = ShotRegression.fit(xs, new double[]{1000, 1200, 1450}, 2);
        assertNotNull(fit);
        assertEquals("a 3-point parabola fits exactly and says nothing", 1, fit.degree());
    }

    @Test
    public void twoPointsGiveALine() {
        double[] xs = {50, 70};
        ShotRegression fit = ShotRegression.fit(xs, new double[]{1000, 1400}, 2);
        assertNotNull(fit);
        assertEquals(1, fit.degree());
        assertEquals(1200.0, fit.predictRaw(60), 1e-9);
    }

    @Test
    public void onePointGivesAConstant() {
        ShotRegression fit = ShotRegression.fit(new double[]{62}, new double[]{1250}, 2);
        assertNotNull(fit);
        assertEquals(0, fit.degree());
        assertEquals(1250.0, fit.predictRaw(10), 1e-9);
        assertEquals(1250.0, fit.predictRaw(200), 1e-9);
    }

    @Test
    public void degreeZeroIsHonoured() {
        double[] xs = {50, 60, 70, 80};
        ShotRegression fit = ShotRegression.fit(xs, new double[]{1000, 1200, 1400, 1600}, 0);
        assertNotNull(fit);
        assertEquals(0, fit.degree());
        assertEquals(1300.0, fit.predictRaw(1), 1e-9);
    }

    // ------------------------------------------------------------- clamp rules
    @Test
    public void predictionClampsToTheMeasuredBand() {
        double[] xs = {50, 60, 70, 80};
        ShotRegression fit = ShotRegression.fit(xs, line(xs, 20.0, 0.0), 2);
        assertNotNull(fit);

        assertEquals(fit.predictRaw(50), fit.predict(10), 1e-9);
        assertEquals(fit.predictRaw(80), fit.predict(500), 1e-9);
        assertTrue(fit.isClamped(10));
        assertTrue(fit.isClamped(500));
        assertFalse(fit.isClamped(65));
        assertEquals(fit.predictRaw(65), fit.predict(65), 1e-9);
    }

    @Test
    public void nonFiniteQueryReturnsNaNNotAClamp() {
        double[] xs = {50, 60, 70, 80};
        ShotRegression fit = ShotRegression.fit(xs, line(xs, 20.0, 0.0), 2);
        assertNotNull(fit);
        assertTrue(Double.isNaN(fit.predict(Double.NaN)));
        assertTrue(Double.isNaN(fit.predictRaw(Double.POSITIVE_INFINITY)));
    }

    // ------------------------------------------------------------ reject input
    @Test
    public void unusableInputReturnsNull() {
        double[] xs = {50, 60};
        double[] ys = {1, 2};
        assertNull(ShotRegression.fit(null, ys, 2));
        assertNull(ShotRegression.fit(xs, null, 2));
        assertNull(ShotRegression.fit(new double[]{1, 2}, new double[]{1}, 2));
        assertNull(ShotRegression.fit(new double[]{}, new double[]{}, 2));
        assertNull(ShotRegression.fit(new double[]{1, 2}, new double[]{1, 2}, -1));
    }

    @Test
    public void nonFiniteSampleRejects() {
        assertNull(ShotRegression.fit(new double[]{50, Double.NaN}, new double[]{1, 2}, 2));
        assertNull(ShotRegression.fit(new double[]{50, 60}, new double[]{1, Double.NaN}, 2));
    }

    @Test
    public void allIdenticalXRejects() {
        assertNull(ShotRegression.fit(new double[]{60, 60, 60, 60}, new double[]{1, 2, 3, 4}, 2));
    }

    // ----------------------------------------------------------------- residuals
    @Test
    public void residualsAreMeasuredMinusPredicted() {
        double[] xs = {50, 60, 70, 80};
        double[] ys = {1000, 1250, 1300, 1600}; // deliberately noisy
        ShotRegression fit = ShotRegression.fit(xs, ys, 1);
        assertNotNull(fit);
        double[] r = fit.residuals();
        assertEquals(xs.length, r.length);
        for (int i = 0; i < xs.length; i++) {
            assertEquals(ys[i] - fit.predictRaw(xs[i]), r[i], 1e-9);
        }
        assertTrue(fit.rSquared() < 0.999); // a line is a poor description of this data
    }

    // ------------------------------------------------------------------ ShotMap
    @Test
    public void emptyArraysProduceAnInertMap() {
        ShotMap map = ShotMap.fit(new double[]{}, new double[]{}, new double[]{}, 2);
        assertFalse(map.isUsable());
        assertFalse(map.hasHood());
        assertEquals(0, map.pointCount());
        assertTrue(Double.isNaN(map.velocityTps(70)));
        assertTrue(Double.isNaN(map.hoodPos(70)));
    }

    @Test
    public void mismatchedArraysDegradeInsteadOfThrowing() {
        // Half-filled constants must not kill a match OpMode at INIT.
        ShotMap map = ShotMap.fit(new double[]{50, 60, 70}, new double[]{1000}, new double[]{}, 2);
        assertFalse(map.isUsable());
        assertTrue(Double.isNaN(map.velocityTps(60)));
    }

    @Test
    public void shotMapFitsVelocityAndHoodIndependently() {
        double[] d = {50, 60, 70, 80};
        double[] v = new double[d.length];
        double[] h = new double[d.length];
        for (int i = 0; i < d.length; i++) {
            v[i] = 8.0 * d[i] + 400.0;
            h[i] = 0.002 * d[i] + 0.35;
        }
        ShotMap map = ShotMap.fit(d, v, h, 2);
        assertTrue(map.isUsable());
        assertTrue(map.hasHood());
        assertEquals(4, map.pointCount());
        assertEquals(8.0 * 65 + 400.0, map.velocityTps(65), 1e-6);
        assertEquals(0.002 * 65 + 0.35, map.hoodPos(65), 1e-9);
    }

    @Test
    public void aMissingHoodFitLeavesVelocityUsable() {
        double[] d = {50, 60, 70, 80};
        ShotMap map = ShotMap.fit(d, line(d, 8.0, 400.0), new double[]{Double.NaN, Double.NaN,
                Double.NaN, Double.NaN}, 2);
        assertTrue(map.isUsable());
        assertFalse(map.hasHood());
        assertTrue(Double.isNaN(map.hoodPos(65)));
        assertTrue(Double.isFinite(map.velocityTps(65)));
    }

    @Test
    public void extrapolationIsReportedNotSilent() {
        double[] d = {55, 65, 75, 85};
        ShotMap map = ShotMap.fit(d, line(d, 8.0, 400.0), line(d, 0.002, 0.35), 2);
        assertTrue(map.isUsable());
        assertTrue(map.isExtrapolating(40));
        assertTrue(map.isExtrapolating(120));
        assertFalse(map.isExtrapolating(70));
    }

    // --------------------------------------------------------------- realistic
    @Test
    public void sqrtShapedVelocityIsFittedWellByAQuadratic() {
        // Required launch speed for a fixed target grows roughly with sqrt(distance). Confirm a
        // quadratic tracks that shape across the shooting band better than a straight line would.
        double[] d = {40, 50, 60, 70, 80, 90};
        double[] v = new double[d.length];
        for (int i = 0; i < d.length; i++) v[i] = 200.0 * Math.sqrt(d[i]);

        ShotRegression quad = ShotRegression.fit(d, v, 2);
        ShotRegression line = ShotRegression.fit(d, v, 1);
        assertNotNull(quad);
        assertNotNull(line);
        assertEquals(2, quad.degree());
        assertTrue("quadratic should beat a line on a sqrt curve",
                quad.maxAbsResidual() < line.maxAbsResidual());
        assertTrue(quad.rSquared() > 0.999);
        // Worst case inside the band stays within a few percent of the truth.
        for (double x = 40; x <= 90; x += 2.5) {
            assertEquals(200.0 * Math.sqrt(x), quad.predict(x), 0.03 * 200.0 * Math.sqrt(x));
        }
    }
}
