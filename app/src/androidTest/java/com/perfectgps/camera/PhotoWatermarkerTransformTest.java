package com.perfectgps.camera;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import com.perfectgps.camera.media.PhotoWatermarker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;

/**
 * Mirroring and rotation are separate contracts, and the order they compose in is the part
 * that goes subtly wrong: a left-right flip applied in the pre-rotation space becomes a
 * top-bottom flip once the image has been turned a quarter turn.
 *
 * The fixture is deliberately asymmetric — a dark block in exactly one quadrant — so a
 * transform cannot pass by accident the way it would with dimensions alone. JUnit3 style to
 * match the module's existing instrumented test, which has no JUnit4 dependency.
 */
public class PhotoWatermarkerTransformTest extends TestCase {

    private static final int WIDTH = 120;
    private static final int HEIGHT = 60;

    private static Bitmap asymmetricSource() {
        Bitmap source = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(source);
        canvas.drawColor(Color.WHITE);
        Paint dark = new Paint();
        dark.setColor(Color.BLACK);
        canvas.drawRect(0, 0, WIDTH / 2f, HEIGHT / 2f, dark);
        return source;
    }

    /** Names the predominantly dark quadrant: TL, TR, BL or BR. */
    private static String darkQuadrant(Bitmap bitmap) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int[][] centres = {
                {w / 4, h / 4}, {3 * w / 4, h / 4},
                {w / 4, 3 * h / 4}, {3 * w / 4, 3 * h / 4}
        };
        String[] names = {"TL", "TR", "BL", "BR"};
        String best = "";
        int bestDark = -1;
        for (int index = 0; index < 4; index++) {
            int dark = 0;
            for (int dx = -6; dx <= 6; dx += 2) {
                for (int dy = -6; dy <= 6; dy += 2) {
                    int x = Math.max(0, Math.min(w - 1, centres[index][0] + dx));
                    int y = Math.max(0, Math.min(h - 1, centres[index][1] + dy));
                    if (Color.red(bitmap.getPixel(x, y)) < 128) {
                        dark++;
                    }
                }
            }
            if (dark > bestDark) {
                bestDark = dark;
                best = names[index];
            }
        }
        return best;
    }

    private static String flipHorizontally(String quadrant) {
        switch (quadrant) {
            case "TL": return "TR";
            case "TR": return "TL";
            case "BL": return "BR";
            case "BR": return "BL";
            default: return quadrant;
        }
    }

    private static String flipVertically(String quadrant) {
        switch (quadrant) {
            case "TL": return "BL";
            case "BL": return "TL";
            case "TR": return "BR";
            case "BR": return "TR";
            default: return quadrant;
        }
    }

    public void testNoRotationNoMirrorLeavesTheFixtureAlone() {
        Bitmap result = PhotoWatermarker.rotateToUpright(asymmetricSource(), 0, false);
        assertEquals(Arrays.asList(WIDTH + "x" + HEIGHT, "TL"), describe(result));
    }

    public void testMirrorFlipsHorizontallyNotVertically() {
        Bitmap result = PhotoWatermarker.rotateToUpright(asymmetricSource(), 0, true);
        assertEquals(WIDTH, result.getWidth());
        assertEquals(HEIGHT, result.getHeight());
        assertEquals("a selfie must mirror left-right", "TR", darkQuadrant(result));
    }

    public void testQuarterTurnSwapsDimensions() {
        Bitmap result = PhotoWatermarker.rotateToUpright(asymmetricSource(), 90, false);
        assertEquals(HEIGHT, result.getWidth());
        assertEquals(WIDTH, result.getHeight());
    }

    /**
     * The contract under test: once the frame is upright, "mirror" must still be a left-right
     * flip of that final frame. If the flip is composed before the rotation it lands on the
     * wrong axis for every quarter turn.
     */
    public void testMirrorStaysHorizontalAfterAQuarterTurn() {
        Bitmap unmirrored = PhotoWatermarker.rotateToUpright(asymmetricSource(), 90, false);
        Bitmap mirrored = PhotoWatermarker.rotateToUpright(asymmetricSource(), 90, true);
        assertEquals("mirroring must flip the upright frame left-right, not top-bottom",
                flipHorizontally(darkQuadrant(unmirrored)), darkQuadrant(mirrored));
    }

    public void testMirrorStaysHorizontalAfterHalfAndThreeQuarterTurns() {
        int[] degrees = {180, 270};
        for (int degree : degrees) {
            Bitmap unmirrored = PhotoWatermarker.rotateToUpright(asymmetricSource(), degree, false);
            Bitmap mirrored = PhotoWatermarker.rotateToUpright(asymmetricSource(), degree, true);
            assertEquals("degrees " + degree,
                    flipHorizontally(darkQuadrant(unmirrored)), darkQuadrant(mirrored));
        }
    }

    public void testMirrorIsNotAccidentallyEquivalentToAVerticalFlip() {
        Bitmap unmirrored = PhotoWatermarker.rotateToUpright(asymmetricSource(), 90, false);
        Bitmap mirrored = PhotoWatermarker.rotateToUpright(asymmetricSource(), 90, true);
        String vertical = flipVertically(darkQuadrant(unmirrored));
        assertFalse("mirror came out as a top-bottom flip",
                vertical.equals(darkQuadrant(mirrored)) && !vertical.equals(
                        flipHorizontally(darkQuadrant(unmirrored))));
    }

    public void testDoubleMirrorIsIdentityAndFullTurnIsIdentity() {
        Bitmap once = PhotoWatermarker.rotateToUpright(asymmetricSource(), 0, true);
        Bitmap twice = PhotoWatermarker.rotateToUpright(once, 0, true);
        assertEquals("TL", darkQuadrant(twice));

        Bitmap turned = PhotoWatermarker.rotateToUpright(asymmetricSource(), 360, false);
        assertEquals(Arrays.asList(WIDTH + "x" + HEIGHT, "TL"), describe(turned));
    }

    public void testNonsenseDegreesSnapToAQuarterTurn() {
        Bitmap result = PhotoWatermarker.rotateToUpright(asymmetricSource(), 45, false);
        assertEquals(HEIGHT, result.getWidth());
        assertEquals(WIDTH, result.getHeight());
    }

    private static List<String> describe(Bitmap bitmap) {
        List<String> out = new ArrayList<>();
        out.add(bitmap.getWidth() + "x" + bitmap.getHeight());
        out.add(darkQuadrant(bitmap));
        return out;
    }
}
