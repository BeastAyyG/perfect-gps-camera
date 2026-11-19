package com.perfectgps.camera.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ZoomPolicyTest {
    private static final float EPSILON = 0.0001f;

    @Test
    public void clampsRequestedRatioToCameraMaximum() {
        assertEquals(4.0f, ZoomPolicy.clamp(4.0f, 10.0f), EPSILON);
        assertEquals(10.0f, ZoomPolicy.clamp(25.0f, 10.0f), EPSILON);
    }

    @Test
    public void neverReturnsBelowOneToOne() {
        assertEquals(1.0f, ZoomPolicy.clamp(0.25f, 10.0f), EPSILON);
        assertEquals(1.0f, ZoomPolicy.clamp(0.0f, 10.0f), EPSILON);
        assertEquals(1.0f, ZoomPolicy.clamp(-3.0f, 10.0f), EPSILON);
    }

    @Test
    public void treatsMissingOrDegenerateMaximumAsOneToOne() {
        assertEquals(1.0f, ZoomPolicy.clamp(3.0f, 0.0f), EPSILON);
        assertEquals(1.0f, ZoomPolicy.clamp(3.0f, 1.0f), EPSILON);
    }

    @Test
    public void switchingLensStartsFromOneToOne() {
        assertEquals(1.0f, ZoomPolicy.afterLensSwitch(), EPSILON);
    }
}
