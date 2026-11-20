# Perfect GPS Camera

Android camera app that stamps photos with the exact GPS location, date and time.

## What it does

- Captures photos with the exact GPS coordinates (latitude, longitude, altitude, speed, bearing).
- Draws a visible watermark panel on the photo with the location and time.
- Writes full GPS EXIF tags (latitude, longitude, timestamp, satellites, altitude, speed, direction).

## Photo fingerprint (tamper-proof location)

Every photo taken with a location carries a fingerprint, in the style of the
invisible tracking dots colour laser printers put on every page. The photo
itself proves exactly where it was taken, and any change breaks the proof.

It has two linked marks:

1. **Hidden data block.** The exact location in full detail (latitude,
   longitude, altitude, speed, bearing, satellites, accuracy, provider, GPS
   and capture timestamps, device make and model) plus a SHA-256 fingerprint
   of the image itself, embedded as a JPEG COM segment. The segment is
   metadata, not pixels, so it does not touch the image and costs zero
   quality.
2. **Printer-dot grid.** Tiny dots drawn into the watermark panel that encode
   a short code derived from the same location and time. The code in the dots
   matches the code in the data block, so the two marks cross-check.

**Verification.** `PhotoFingerprint.verify` recomputes both hashes from the
file. It returns Authentic only when the stored image hash matches the image
bytes and the stored fingerprint matches the stored fields. Changing any
pixel, or any part of the location, returns Tampered. A photo with no
fingerprint returns NoFingerprint. The dot grid is read back by
`DotPattern.decode`.

## APK

- `app-release.apk` is the release build.
- `app-debug.apk` is the debug build.

## Build

Open the project in Android Studio and run, or build with `./gradlew assembleRelease`.
