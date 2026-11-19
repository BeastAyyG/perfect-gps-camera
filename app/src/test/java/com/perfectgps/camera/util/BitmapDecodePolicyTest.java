package com.perfectgps.camera.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class BitmapDecodePolicyTest {
    @Test
    public void keepsFullSizeWhenSourceAlreadyFits() {
        assertEquals(1, BitmapDecodePolicy.sampleSize(1856, 1080));
    }

    @Test
    public void keepsFullSizeExactlyAtTwiceTheTarget() {
        assertEquals(1, BitmapDecodePolicy.sampleSize(2880, 1440));
    }

    @Test
    public void halvesJustAboveTwiceTheTarget() {
        assertEquals(2, BitmapDecodePolicy.sampleSize(4080, 1440));
    }

    @Test
    public void halvesTheSamsungStillForTheReviewScreen() {
        assertEquals(2, BitmapDecodePolicy.sampleSize(4080, 1440));
    }

    @Test
    public void reducesLargeStillsToAThumbnail() {
        assertEquals(8, BitmapDecodePolicy.sampleSize(4080, 360));
    }

    @Test
    public void survivesDegenerateInput() {
        assertEquals(1, BitmapDecodePolicy.sampleSize(0, 1080));
        assertEquals(1, BitmapDecodePolicy.sampleSize(4080, 0));
        assertEquals(1, BitmapDecodePolicy.sampleSize(-5, -5));
    }

    @Test
    public void boundsTheSampleSizeForCorruptDimensions() {
        assertEquals(1024, BitmapDecodePolicy.sampleSize(Integer.MAX_VALUE, 1));
    }

    @Test
    public void previewTargetNeverDropsBelowOne() {
        assertEquals(1, BitmapDecodePolicy.previewTarget(0));
        assertEquals(1, BitmapDecodePolicy.previewTarget(-320));
        assertEquals(1080, BitmapDecodePolicy.previewTarget(1080));
    }
}
