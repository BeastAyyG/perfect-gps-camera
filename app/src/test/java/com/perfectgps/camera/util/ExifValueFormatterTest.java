package com.perfectgps.camera.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ExifValueFormatterTest {
    @Test
    public void formatsCoordinatesAsExifRationalTriples() {
        assertEquals("40/1,42/1,459936/10000", ExifValueFormatter.latitude(40.712776));
        assertEquals("74/1,0/1,215064/10000", ExifValueFormatter.longitude(-74.005974));
    }

    @Test
    public void formatsAltitudeSpeedAndDirectionAsReducedRationals() {
        assertEquals("37/2", ExifValueFormatter.altitude(18.5));
        assertEquals("7/5", ExifValueFormatter.speed(1.4));
        assertEquals("90/1", ExifValueFormatter.direction(90.0));
    }

    @Test
    public void normalizesHemisphereAndDateReferences() {
        assertEquals("N", ExifValueFormatter.latitudeReference(40.0));
        assertEquals("W", ExifValueFormatter.longitudeReference(-74.0));
        assertEquals("1", ExifValueFormatter.altitudeReference(-18.5));
        assertEquals("2026:09:25", ExifValueFormatter.gpsDate(1_790_346_920_000L));
        assertEquals("14:35:20", ExifValueFormatter.gpsTime(1_790_346_920_000L));
    }
}
