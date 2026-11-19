package com.perfectgps.camera.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.perfectgps.camera.model.GeoLocationSnapshot;
import org.junit.Test;

public class CapturePolicyTest {
    private static final GeoLocationSnapshot FIX = new GeoLocationSnapshot(
            40.712776,
            -74.005974,
            4.2,
            18.5,
            1.4,
            90.0,
            1_700_000_000_000L,
            "gps",
            12
    );

    @Test
    public void rejectsCaptureWhenCameraIsNotReady() {
        assertFalse(CapturePolicy.canCapture(false, false, null));
    }

    @Test
    public void allowsCaptureWithoutLocationWhenLockIsDisabled() {
        assertTrue(CapturePolicy.canCapture(true, false, null));
    }

    @Test
    public void rejectsCaptureWithoutFixWhenLocationLockIsEnabled() {
        assertFalse(CapturePolicy.canCapture(true, true, null));
        assertFalse(CapturePolicy.canCapture(true, true, new GeoLocationSnapshot(
                91.0,
                10.0,
                3.0,
                0.0,
                0.0,
                0.0,
                1_700_000_000_000L,
                "gps",
                8
        )));
    }

    @Test
    public void allowsCaptureWithValidFixWhenLocationLockIsEnabled() {
        assertTrue(CapturePolicy.canCapture(true, true, FIX));
    }
}
