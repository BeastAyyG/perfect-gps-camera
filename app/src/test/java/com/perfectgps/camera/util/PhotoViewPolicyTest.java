package com.perfectgps.camera.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PhotoViewPolicyTest {

    private static final float DELTA = 0.001f;

    @Test
    public void fitsWithinTheTighterDimension() {
        assertEquals(1.0f, PhotoViewPolicy.fitScale(1080, 1440, 1080, 1080), DELTA);
        assertEquals(1080.0f / 4000.0f, PhotoViewPolicy.fitScale(1080, 1440, 4000, 3000), DELTA);
        assertEquals(1440.0f / 4000.0f, PhotoViewPolicy.fitScale(1080, 1440, 3000, 4000), DELTA);
    }

    @Test
    public void degenerateSizesFallBackToIdentity() {
        assertEquals(1.0f, PhotoViewPolicy.fitScale(0, 1440, 1080, 1080), DELTA);
        assertEquals(1.0f, PhotoViewPolicy.fitScale(1080, 1440, 0, 1080), DELTA);
        assertEquals(1.0f, PhotoViewPolicy.fitScale(1080, 1440, 1080, -5), DELTA);
    }

    @Test
    public void scaleIsBoundedToTheUsableRange() {
        assertEquals(1.0f, PhotoViewPolicy.clampScale(0.2f), DELTA);
        assertEquals(4.0f, PhotoViewPolicy.clampScale(12.0f), DELTA);
        assertEquals(2.0f, PhotoViewPolicy.clampScale(2.0f), DELTA);
    }

    @Test
    public void invalidScaleFallsBackToFit() {
        assertEquals(1.0f, PhotoViewPolicy.clampScale(Float.NaN), DELTA);
    }

    @Test
    public void zoomStateIsDetectedWithATolerance() {
        assertFalse(PhotoViewPolicy.isZoomed(1.0f));
        assertFalse(PhotoViewPolicy.isZoomed(1.0005f));
        assertTrue(PhotoViewPolicy.isZoomed(1.05f));
    }

    @Test
    public void shownSizeScalesWithFit() {
        assertEquals(0.0f, PhotoViewPolicy.shownSize(0, 2.0f), DELTA);
        assertEquals(2160.0f, PhotoViewPolicy.shownSize(1080, 2.0f), DELTA);
    }

    @Test
    public void offsetIsPinnedWhileThePhotoFits() {
        assertEquals(0.0f, PhotoViewPolicy.clampOffset(500.0f, 1080, 1080, 1.0f), DELTA);
    }

    @Test
    public void offsetMayTravelHalfTheOverflow() {
        float limit = (1080.0f * 2.0f - 1080) / 2.0f;
        assertEquals(limit, PhotoViewPolicy.clampOffset(9999.0f, 1080, 1080, 2.0f), DELTA);
        assertEquals(-limit, PhotoViewPolicy.clampOffset(-9999.0f, 1080, 1080, 2.0f), DELTA);
    }

    @Test
    public void offsetIsSafeWithDegenerateGeometry() {
        assertEquals(0.0f, PhotoViewPolicy.clampOffset(120.0f, 0, 1080, 2.0f), DELTA);
        assertEquals(0.0f, PhotoViewPolicy.clampOffset(120.0f, 1080, 0.0f, 2.0f), DELTA);
    }

    @Test
    public void focusStaysAnchoredWhileScaling() {
        float focus = 800.0f;
        float startScale = 1.0f;
        float startOffset = 40.0f;
        float anchored = focus / startScale - startOffset;
        float nextOffset = PhotoViewPolicy.focusOffset(focus, startOffset, startScale, 2.0f);
        assertEquals(anchored * 2.0f + nextOffset, focus, DELTA);
    }

    @Test
    public void focusOffsetIsSafeWithUnsetState() {
        assertEquals(0.0f, PhotoViewPolicy.focusOffset(500.0f, 0.0f, 0.0f, 2.0f), DELTA);
        assertEquals(0.0f, PhotoViewPolicy.focusOffset(500.0f, 0.0f, 1.0f, 0.0f), DELTA);
        assertEquals(0.0f, PhotoViewPolicy.focusOffset(500.0f, Float.NaN, 1.0f, 2.0f), DELTA);
    }
}
