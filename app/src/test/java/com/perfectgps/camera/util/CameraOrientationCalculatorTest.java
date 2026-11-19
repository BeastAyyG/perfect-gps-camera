package com.perfectgps.camera.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class CameraOrientationCalculatorTest {
    @Test
    public void calculatesBackCameraJpegOrientation() {
        assertEquals(90, CameraOrientationCalculator.jpegOrientation(90, 0, false));
        assertEquals(0, CameraOrientationCalculator.jpegOrientation(90, 90, false));
        assertEquals(180, CameraOrientationCalculator.jpegOrientation(270, 90, false));
    }

    @Test
    public void calculatesMirroredFrontCameraJpegOrientation() {
        assertEquals(270, CameraOrientationCalculator.jpegOrientation(90, 0, true));
        assertEquals(180, CameraOrientationCalculator.jpegOrientation(90, 90, true));
        assertEquals(0, CameraOrientationCalculator.jpegOrientation(90, 270, true));
    }

    @Test
    public void calculatesRelativePreviewRotation() {
        assertEquals(90, CameraOrientationCalculator.relativeRotation(90, 0, false));
        assertEquals(180, CameraOrientationCalculator.relativeRotation(90, 90, false));
        assertEquals(270, CameraOrientationCalculator.relativeRotation(270, 0, true));
        assertEquals(180, CameraOrientationCalculator.relativeRotation(270, 90, true));
    }
}
