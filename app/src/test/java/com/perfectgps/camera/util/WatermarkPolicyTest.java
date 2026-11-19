package com.perfectgps.camera.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WatermarkPolicyTest {
    private static final float DELTA = 0.01f;

    @Test
    public void placeNameBecomesTheHeadline() {
        String[] lines = WatermarkPolicy.buildLines(
                "Treetop Hideaways", "16.463401, 80.505807", "NO GPS");
        assertArrayEquals(new String[]{
                "Treetop Hideaways",
                "16.463401, 80.505807"
        }, lines);
    }

    @Test
    public void headlineFallsBackToNoFixLabelWhenPlaceIsUnknown() {
        String[] lines = WatermarkPolicy.buildLines("", "16.463401, 80.505807", "NO GPS");
        assertArrayEquals(new String[]{"NO GPS", "16.463401, 80.505807"}, lines);
    }

    @Test
    public void missingCoordinatesFallBackToTheNoFixLabel() {
        String[] lines = WatermarkPolicy.buildLines("Treetop Hideaways", "", "NO GPS");
        assertArrayEquals(new String[]{"Treetop Hideaways", "NO GPS"}, lines);
    }

    @Test
    public void bothMissingShowsNoFixLabelTwice() {
        String[] lines = WatermarkPolicy.buildLines(null, null, "NO GPS");
        assertArrayEquals(new String[]{"NO GPS", "NO GPS"}, lines);
    }

    @Test
    public void everyRenderedLineIsNeverBlank() {
        String[] lines = WatermarkPolicy.buildLines("  ", "", "NO GPS");
        assertEquals(2, lines.length);
        for (String line : lines) {
            assertTrue("blank line: [" + line + "]", !line.trim().isEmpty());
        }
    }

    @Test
    public void surroundingWhitespaceIsTrimmed() {
        String[] lines = WatermarkPolicy.buildLines("  Treetop  ", "  1, 2  ", "  NONE  ");
        assertEquals("Treetop", lines[0]);
        assertEquals("1, 2", lines[1]);
    }

    @Test
    public void alwaysProducesExactlyTwoLines() {
        assertEquals(2, WatermarkPolicy.buildLines("P", "C", "N").length);
        assertEquals(2, WatermarkPolicy.buildLines("", "", "N").length);
        assertEquals(2, WatermarkPolicy.buildLines(null, null, "N").length);
    }

    @Test
    public void textScalesWithTheImageLongEdge() {
        int large = WatermarkPolicy.headlineSize(4080);
        int small = WatermarkPolicy.headlineSize(1856);
        assertTrue("large=" + large, large > small);
        assertEquals(106, large);
        assertEquals(48, small);
    }

    @Test
    public void sizesAreClampedToASaneRange() {
        assertEquals(WatermarkPolicy.headlineSize(WatermarkPolicy.MIN_LONG_EDGE),
                WatermarkPolicy.headlineSize(0));
        assertEquals(WatermarkPolicy.headlineSize(WatermarkPolicy.MIN_LONG_EDGE),
                WatermarkPolicy.headlineSize(-500));
        assertEquals(WatermarkPolicy.headlineSize(WatermarkPolicy.MAX_LONG_EDGE),
                WatermarkPolicy.headlineSize(99999));
    }

    @Test
    public void sizesAreAlwaysAtLeastOnePixel() {
        assertTrue(WatermarkPolicy.metaSize(0) >= 1);
        assertTrue(WatermarkPolicy.margin(0) >= 1);
        assertTrue(WatermarkPolicy.panelHeight(0) >= 1);
    }

    @Test
    public void theMetaAndHeadlineSizesNeverInvertAtAnyScale() {
        for (int edge = 0; edge <= 12000; edge += 137) {
            assertTrue("edge=" + edge,
                    WatermarkPolicy.metaSize(edge) <= WatermarkPolicy.headlineSize(edge));
        }
    }

    @Test
    public void boundedIsMonotonic() {
        assertEquals(WatermarkPolicy.MIN_LONG_EDGE, WatermarkPolicy.bounded(0), DELTA);
        assertEquals(4320f, WatermarkPolicy.bounded(4320), DELTA);
        assertEquals(WatermarkPolicy.MAX_LONG_EDGE, WatermarkPolicy.bounded(100000), DELTA);
    }

    @Test
    public void cameraRotationDegreesPassThroughUnchanged() {
        assertEquals(0, WatermarkPolicy.uprightRotation(0));
        assertEquals(90, WatermarkPolicy.uprightRotation(90));
        assertEquals(180, WatermarkPolicy.uprightRotation(180));
        assertEquals(270, WatermarkPolicy.uprightRotation(270));
    }

    @Test
    public void rotationAlwaysSnapsToAQuarterTurn() {
        for (int degrees = -1080; degrees <= 1080; degrees++) {
            int rotation = WatermarkPolicy.uprightRotation(degrees);
            assertTrue("not a quarter turn: " + rotation, rotation % 90 == 0);
            assertTrue("out of range: " + rotation, rotation >= 0 && rotation < 360);
        }
    }

    @Test
    public void exifOrientationCodesAreNotMistakenForDegrees() {
        assertEquals(0, WatermarkPolicy.uprightRotation(6));
        assertEquals(0, WatermarkPolicy.uprightRotation(8));
        assertEquals(0, WatermarkPolicy.uprightRotation(3));
    }

    @Test
    public void rotationSnapsToTheNearestQuarterTurn() {
        assertEquals(0, WatermarkPolicy.uprightRotation(44));
        assertEquals(90, WatermarkPolicy.uprightRotation(45));
        assertEquals(90, WatermarkPolicy.uprightRotation(46));
        assertEquals(180, WatermarkPolicy.uprightRotation(135));
        assertEquals(0, WatermarkPolicy.uprightRotation(315));
        assertEquals(90, WatermarkPolicy.uprightRotation(405));
        assertEquals(0, WatermarkPolicy.uprightRotation(404));
    }
}
