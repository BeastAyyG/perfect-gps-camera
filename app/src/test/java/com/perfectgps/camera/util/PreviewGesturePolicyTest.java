package com.perfectgps.camera.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PreviewGesturePolicyTest {

    private static final float SLOP = 40.0f;

    @Test
    public void classifiesDownwardSwipe() {
        assertEquals(PreviewGesturePolicy.DIRECTION_DOWN,
                PreviewGesturePolicy.verticalSwipeDirection(4.0f, 180.0f, SLOP));
    }

    @Test
    public void classifiesUpwardSwipe() {
        assertEquals(PreviewGesturePolicy.DIRECTION_UP,
                PreviewGesturePolicy.verticalSwipeDirection(-6.0f, -200.0f, SLOP));
    }

    @Test
    public void ignoresMovementBelowSlop() {
        assertEquals(PreviewGesturePolicy.DIRECTION_NONE,
                PreviewGesturePolicy.verticalSwipeDirection(0.0f, SLOP - 0.5f, SLOP));
    }

    @Test
    public void ignoresHorizontalDominantMovement() {
        assertEquals(PreviewGesturePolicy.DIRECTION_NONE,
                PreviewGesturePolicy.verticalSwipeDirection(300.0f, 120.0f, SLOP));
    }

    @Test
    public void ignoresExactlyDiagonalMovement() {
        assertEquals(PreviewGesturePolicy.DIRECTION_NONE,
                PreviewGesturePolicy.verticalSwipeDirection(150.0f, 150.0f, SLOP));
    }

    @Test
    public void ignoresZeroSlop() {
        assertEquals(PreviewGesturePolicy.DIRECTION_NONE,
                PreviewGesturePolicy.verticalSwipeDirection(0.0f, 400.0f, 0.0f));
    }

    @Test
    public void acceptsDoubleTapWithinTimeoutAndSlop() {
        assertTrue(PreviewGesturePolicy.isDoubleTap(120L, 280, 8.0f, -6.0f, SLOP));
    }

    @Test
    public void rejectsDoubleTapAfterTimeout() {
        assertFalse(PreviewGesturePolicy.isDoubleTap(400L, 280, 8.0f, -6.0f, SLOP));
    }

    @Test
    public void rejectsDoubleTapTooFarApart() {
        assertFalse(PreviewGesturePolicy.isDoubleTap(120L, 280, 260.0f, 0.0f, SLOP));
    }

    @Test
    public void rejectsDoubleTapWithoutPreviousTap() {
        assertFalse(PreviewGesturePolicy.isDoubleTap(-1L, 280, 0.0f, 0.0f, SLOP));
    }

    @Test
    public void rejectsDoubleTapWithNonPositiveTimeout() {
        assertFalse(PreviewGesturePolicy.isDoubleTap(10L, 0, 0.0f, 0.0f, SLOP));
    }
}
