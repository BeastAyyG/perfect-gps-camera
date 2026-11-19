package com.perfectgps.camera.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.perfectgps.camera.model.GeoLocationSnapshot;
import java.util.Date;
import org.junit.Test;

public class PhotoNameFormatterTest {
    @Test
    public void createsSortableUtcNameWithCameraAndCoordinates() {
        String name = PhotoNameFormatter.format(
                new Date(1_700_000_000_000L),
                "back",
                new GeoLocationSnapshot(
                        40.712776,
                        -74.005974,
                        4.2,
                        18.5,
                        1.4,
                        90.0,
                        1_700_000_000_000L,
                        "gps",
                        12
                )
        );
        assertEquals("PGSC_20231114_221320_000_40.7128N_074.0060W.jpg", name);
    }

    @Test
    public void usesSafeFilenameWhenLocationIsUnavailable() {
        assertEquals(
                "PGSC_20231114_221320_000_FRONT.jpg",
                PhotoNameFormatter.format(new Date(1_700_000_000_000L), "front", null)
        );
    }

    @Test
    public void rejectsUnsafeCameraFacingAndInvalidDates() {
        boolean threw = false;
        try {
            PhotoNameFormatter.format(new Date(1_700_000_000_000L), "../../bad", null);
        } catch (IllegalArgumentException expected) {
            threw = true;
        }
        assertTrue(threw);
        assertFalse(PhotoNameFormatter.format(new Date(Long.MIN_VALUE), "back", null).isEmpty());
    }
}
