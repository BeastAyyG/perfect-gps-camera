package com.perfectgps.camera.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.perfectgps.camera.model.GeoLocationSnapshot;
import org.junit.Test;

public class LocationFreshnessPolicyTest {
    private static final long NOW = 1_700_000_000_000L;

    @Test
    public void acceptsFreshFixAndRejectsStaleOrFutureFix() {
        assertTrue(LocationFreshnessPolicy.isFresh(fixAt(NOW - 5_000L), NOW, 30_000L));
        assertFalse(LocationFreshnessPolicy.isFresh(fixAt(NOW - 31_000L), NOW, 30_000L));
        assertFalse(LocationFreshnessPolicy.isFresh(fixAt(NOW + 6_000L), NOW, 30_000L));
        assertTrue(LocationFreshnessPolicy.isFresh(fixAt(NOW + 2_000L), NOW, 30_000L));
    }

    @Test
    public void rejectsMissingInvalidAndNonPositiveAgeBudgets() {
        assertFalse(LocationFreshnessPolicy.isFresh(null, NOW, 30_000L));
        assertFalse(LocationFreshnessPolicy.isFresh(fixAt(NOW), NOW, 0L));
        assertFalse(LocationFreshnessPolicy.isFresh(fixAt(NOW), NOW, -1L));
    }

    private static GeoLocationSnapshot fixAt(long timestamp) {
        return new GeoLocationSnapshot(
                40.0,
                -74.0,
                4.0,
                Double.NaN,
                0.0,
                Double.NaN,
                timestamp,
                "gps",
                10
        );
    }
}
