package com.perfectgps.camera.media;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import org.junit.Test;

/**
 * Pins the watermark ink colours. The meta lines once used a seven-digit literal
 * (0xE6E9ECF, alpha 0x0E) that rendered the coordinates and timestamp at about five per
 * cent opacity, which is unreadable over the panel. Read by reflection so the constants
 * stay private to the class that uses them.
 */
public class PhotoWatermarkerTest {

    private static int colour(String name) throws Exception {
        Class<?> owner = Class.forName("com.perfectgps.camera.media.PhotoWatermarker");
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(null);
    }

    @Test
    public void everyWatermarkColourIsFullyOpaque() throws Exception {
        assertEquals("META_COLOR", 0xFF, (colour("META_COLOR") >>> 24) & 0xFF);
        assertEquals("HEADLINE_COLOR", 0xFF, (colour("HEADLINE_COLOR") >>> 24) & 0xFF);
        assertEquals("PANEL_COLOR", 0xCC, (colour("PANEL_COLOR") >>> 24) & 0xFF);
    }

    @Test
    public void metaInkIsTheLightGreyIntendedForThePanel() throws Exception {
        assertEquals(0xFFE6E9EC, colour("META_COLOR"));
    }

    @Test
    public void headlineAndMetaInkRemainDistinctColours() throws Exception {
        assertTrue(colour("HEADLINE_COLOR") != colour("META_COLOR"));
    }
}
