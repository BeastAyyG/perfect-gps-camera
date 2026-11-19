package com.perfectgps.camera;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.ExifInterface;
import android.test.AndroidTestCase;

import com.perfectgps.camera.media.ExifMetadataWriter;

import java.io.File;
import java.io.FileOutputStream;

public class ExifMetadataWriterTest extends AndroidTestCase {

    private static final long CAPTURE_TIME_MILLIS = 1700000000000L;

    private File jpeg;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        Context context = getContext();
        assertNotNull("AndroidTestCase was not given a context", context);
        jpeg = new File(context.getCacheDir(), "exif_orientation_test.jpg");
        writeFreshJpeg();
    }

    @Override
    protected void tearDown() throws Exception {
        if (jpeg != null && jpeg.exists() && !jpeg.delete()) {
            fail("could not delete " + jpeg.getAbsolutePath());
        }
        super.tearDown();
    }

    private void writeFreshJpeg() throws Exception {
        Bitmap bitmap = Bitmap.createBitmap(32, 24, Bitmap.Config.ARGB_8888);
        try {
            bitmap.eraseColor(0xFF336699);
            FileOutputStream stream = new FileOutputStream(jpeg);
            try {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream));
                stream.flush();
            } finally {
                stream.close();
            }
        } finally {
            bitmap.recycle();
        }
    }

    private int persistedOrientation() throws Exception {
        return new ExifInterface(jpeg.getAbsolutePath())
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, -1);
    }

    private int writeAndReRead() throws Exception {
        ExifInterface exif = new ExifInterface(jpeg.getAbsolutePath());
        ExifMetadataWriter.write(exif, null, CAPTURE_TIME_MILLIS);
        exif.saveAttributes();
        return persistedOrientation();
    }

    public void testSavedFileCarriesAValidUprightOrientation() throws Exception {
        assertEquals(ExifInterface.ORIENTATION_NORMAL, writeAndReRead());
    }

    public void testAnInheritedRotationFlagIsNormalisedBecausePixelsAreAlreadyUpright()
            throws Exception {
        ExifInterface seeded = new ExifInterface(jpeg.getAbsolutePath());
        seeded.setAttribute(
                ExifInterface.TAG_ORIENTATION,
                String.valueOf(ExifInterface.ORIENTATION_ROTATE_90));
        seeded.saveAttributes();
        assertEquals(ExifInterface.ORIENTATION_ROTATE_90, persistedOrientation());

        assertEquals(ExifInterface.ORIENTATION_NORMAL, writeAndReRead());
    }

    public void testTheOtherSavedFieldsSurviveAlongsideTheOrientation() throws Exception {
        writeAndReRead();

        ExifInterface saved = new ExifInterface(jpeg.getAbsolutePath());
        assertEquals("Perfect GPS Camera", saved.getAttribute(ExifInterface.TAG_SOFTWARE));
        assertNotNull(saved.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL));
        assertNotNull(saved.getAttribute(ExifInterface.TAG_MAKE));
        assertEquals(ExifInterface.ORIENTATION_NORMAL,
                saved.getAttributeInt(ExifInterface.TAG_ORIENTATION, -1));
    }
}
