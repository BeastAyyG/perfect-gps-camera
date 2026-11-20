package com.perfectgps.camera.media

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.media.ExifInterface
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import com.perfectgps.camera.model.GeoLocationSnapshot
import com.perfectgps.camera.util.PhotoNameFormatter
import com.perfectgps.camera.util.WatermarkPolicy
import java.io.File
import java.io.FileOutputStream
import java.util.Date
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class PhotoRepository(context: Context) {
    interface Callback {
        fun onSaved(photo: SavedPhoto?)

        fun onSaveFailed(exception: Exception)
    }

    class SavedPhoto(
            private val uri: Uri,
            private val displayName: String,
            private val hasLocation: Boolean
    ) {
        fun uri(): Uri = uri

        fun displayName(): String = displayName

        fun hasLocation(): Boolean = hasLocation
    }

    private val context: Context = context.applicationContext
    private val resolver: ContentResolver = this.context.contentResolver
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    fun save(
            jpeg: ByteArray?,
            location: GeoLocationSnapshot?,
            captureTimeMillis: Long,
            frontFacing: Boolean,
            rotationDegrees: Int,
            placeName: String?,
            coordinates: String?,
            noFixLabel: String?,
            callback: Callback
    ) {
        val lines = WatermarkPolicy.buildLines(placeName, coordinates, noFixLabel)
        executor.execute {
            try {
                val dotCode = if (location != null && location.isValid()) {
                    PhotoFingerprint.dotCodeHex(location, captureTimeMillis)
                } else {
                    null
                }
                val stamped = PhotoWatermarker.apply(
                        jpeg, lines, rotationDegrees, frontFacing, dotCode
                )
                // Tamper-proof fingerprint: the exact location in full detail
                // plus a SHA-256 of the image, embedded as a JPEG COM segment.
                // It touches no pixel, so quality is unchanged. A matching
                // printer-dot grid (the dot code above) is already in the
                // photo, and the two marks cross-check on verify.
                val fingerprinted = PhotoFingerprint.embed(
                        stamped, location, captureTimeMillis
                )
                val photo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    saveModern(fingerprinted, location, captureTimeMillis, frontFacing)
                } else {
                    saveLegacy(fingerprinted, location, captureTimeMillis, frontFacing)
                }
                callback.onSaved(photo)
            } catch (exception: Exception) {
                callback.onSaveFailed(exception)
            }
        }
    }

    fun close() {
        executor.shutdownNow()
    }

    private fun saveModern(
            jpeg: ByteArray?,
            location: GeoLocationSnapshot?,
            captureTimeMillis: Long,
            frontFacing: Boolean
    ): SavedPhoto {
        val displayName = PhotoNameFormatter.format(
                Date(captureTimeMillis),
                if (frontFacing) "front" else "back",
                location
        )
        val values = ContentValues()
        values.put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        values.put(
                MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/" + ALBUM
        )
        values.put(MediaStore.Images.Media.DATE_TAKEN, captureTimeMillis)
        values.put(MediaStore.Images.Media.IS_PENDING, 1)
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("MediaStore did not create a photo entry")
        try {
            val output = resolver.openOutputStream(uri, "w")
                    ?: throw IllegalStateException("Photo output stream is unavailable")
            output.use { it.write(jpeg) }
            writeExif(uri, location, captureTimeMillis)
            val completed = ContentValues()
            completed.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, completed, null, null)
            return SavedPhoto(uri, displayName, location != null && location.isValid())
        } catch (exception: Exception) {
            resolver.delete(uri, null, null)
            throw exception
        }
    }

    @Suppress("DEPRECATION")
    private fun saveLegacy(
            jpeg: ByteArray?,
            location: GeoLocationSnapshot?,
            captureTimeMillis: Long,
            frontFacing: Boolean
    ): SavedPhoto {
        val albumDirectory = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                ALBUM
        )
        if (!albumDirectory.exists() && !albumDirectory.mkdirs()) {
            throw IllegalStateException("Photo album directory could not be created")
        }
        val displayName = PhotoNameFormatter.format(
                Date(captureTimeMillis),
                if (frontFacing) "front" else "back",
                location
        )
        val file = File(albumDirectory, displayName)
        try {
            val output = FileOutputStream(file)
            output.use { stream ->
                stream.write(jpeg)
                stream.flush()
                val exif = ExifInterface(stream.fd)
                ExifMetadataWriter.write(exif, location, captureTimeMillis)
                exif.saveAttributes()
            }
        } catch (exception: Exception) {
            if (file.exists() && !file.delete()) {
                file.deleteOnExit()
            }
            throw exception
        }
        MediaScannerConnection.scanFile(
                context,
                arrayOf(file.absolutePath),
                arrayOf("image/jpeg"),
                null
        )
        return SavedPhoto(Uri.fromFile(file), displayName, location != null && location.isValid())
    }

    private fun writeExif(
            uri: Uri,
            location: GeoLocationSnapshot?,
            captureTimeMillis: Long
    ) {
        val descriptor = resolver.openFileDescriptor(uri, "rw")
                ?: throw IllegalStateException("Photo file descriptor is unavailable")
        descriptor.use {
            val exif = ExifInterface(it.fileDescriptor)
            ExifMetadataWriter.write(exif, location, captureTimeMillis)
            exif.saveAttributes()
        }
    }

    companion object {
        private const val ALBUM = "Perfect GPS Camera"
    }
}
