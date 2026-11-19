package com.perfectgps.camera.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.perfectgps.camera.model.GeoLocationSnapshot;
import java.util.Locale;
import org.junit.Test;

public class GeoTextFormatterTest {
    @Test
    public void formatsSignedCoordinatesToSixDecimals() {
        assertEquals(
                "40.712776, -74.005974",
                GeoTextFormatter.formatCoordinates(validLocation(), Locale.US)
        );
    }

    @Test
    public void formatsDegreesMinutesAndHemisphere() {
        assertEquals(
                "40°42'46.0\"N 74°00'21.5\"W",
                GeoTextFormatter.formatDms(validLocation(), Locale.US)
        );
    }

    @Test
    public void formatsAccuracyAndRejectsInvalidLocation() {
        GeoLocationSnapshot location = validLocation();
        assertEquals("±4.2 m", GeoTextFormatter.formatAccuracy(location, Locale.US));
        assertFalse(location.withLatitude(95.0).isValid());
        assertTrue(GeoTextFormatter.formatCoordinates(null, Locale.US).isEmpty());
    }

    private static GeoLocationSnapshot validLocation() {
        return new GeoLocationSnapshot(
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
    }
}
