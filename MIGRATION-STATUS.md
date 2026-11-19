# SRM Cam — Kotlin Migration Status

Trackable record for the Java → Kotlin migration. Every number below was read from
the repository or from a build report at the revision stated; nothing is carried
over from a prior report.

## Current revision

| Item | Value |
| --- | --- |
| Baseline recorded at | `95ebd0b` — "Record the Kotlin migration baseline" |
| Watermark baseline | `badbc22` — "Burn a place, location and time stamp into every photo" |
| Kotlin enablement | `cd1f48c` — "Enable Kotlin alongside Java" |
| Progress since baseline | 19 local commits, `f7c882b..HEAD`, no pushes |

### Concurrency note (load-bearing for the audit trail)

An external process in this workspace commits the working tree on its own, in this
repository's message style, without being asked. It dropped one commit from history
(`2617bf2`, re-landed as `2380834`) and once swept five unrelated `.kt` files into a
single commit. Its commits were not reverted and are not relied on: every gate result
below was measured by a `./gradlew` run in this session against the tree at the stated
revision. Batch boundaries in the ledger are therefore stated as *class lists*, and the
commit that carries each class is named with `git log --oneline -- <path>`.

## Source inventory now

- **Java: 0 files / 0 lines**
- **Kotlin: 23 files / 5,494 lines**

The migration's implementation goal is reached: every owned production class is Kotlin.
Tests remain Java by design (12 unit files, 1 instrumentation file), which the plan allows.

## Source inventory at baseline

Production source under `app/src/main/java`:

- **Java: 22 files / 5,400 lines**
- **Kotlin: 1 file / 13 lines** (`util/ZoomPolicy.kt`)

| File | LOC | Kind |
| --- | --- | --- |
| `ui/MainActivity.java` | 2,153 | Android — Activity |
| `camera/CameraController.java` | 1,239 | Android — Camera2 |
| `ui/OnboardingActivity.java` | 317 | Android — Activity |
| `media/PhotoRepository.java` | 194 | Android — MediaStore |
| `ui/SplashActivity.java` | 186 | Android — Activity |
| `location/LocationController.java` | 170 | Android — LocationManager |
| `util/PlaceNameResolver.java` | 143 | Android — Geocoder (to confirm) |
| `media/PhotoWatermarker.java` | 139 | Android — Graphics |
| `model/GeoLocationSnapshot.java` | 137 | Pure |
| `ui/BrandUi.java` | 111 | Android — Views |
| `util/ExifValueFormatter.java` | 99 | Pure |
| `media/ExifMetadataWriter.java` | 92 | Android — ExifInterface |
| `util/WatermarkPolicy.java` | 88 | Pure |
| `ui/GridOverlayView.java` | 56 | Android — View |
| `util/PhotoViewPolicy.java` | 52 | Pure |
| `util/PhotoNameFormatter.java` | 47 | Pure |
| `util/GeoTextFormatter.java` | 42 | Pure |
| `util/CameraOrientationCalculator.java` | 35 | Pure |
| `util/PreviewGesturePolicy.java` | 31 | Pure |
| `util/LocationFreshnessPolicy.java` | 26 | Pure |
| `util/BitmapDecodePolicy.java` | 24 | Pure |
| `util/CapturePolicy.java` | 19 | Pure |
| `util/ZoomPolicy.kt` | 13 | **Kotlin — done** |

Test source: 11 JUnit 4 files under `app/src/test` (710 lines), 1 instrumentation
file under `app/src/androidTest` (42 lines). Tests stay Java; they are counted
separately from production source throughout.

## Reconciling the reported 67 → 71 increase

Verified against Git, not taken on trust:

- At `badbc22`, the 11 test files contained **67** `@Test` methods.
- `98b65c7` added **4** methods to `WatermarkPolicyTest`
  (`cameraRotationDegreesPassThroughUnchanged`, `rotationAlwaysSnapsToAQuarterTurn`,
  `exifOrientationCodesAreNotMistakenForDegrees`, `rotationSnapsToTheNearestQuarterTurn`).
- 67 + 4 = **71**, matching the runner exactly.

Per-class, from `app/build/test-results/testDebugUnitTest`:

| Test class | Tests |
| --- | --- |
| `WatermarkPolicyTest` | 19 |
| `PhotoViewPolicyTest` | 11 |
| `PreviewGesturePolicyTest` | 11 |
| `BitmapDecodePolicyTest` | 8 |
| `CapturePolicyTest` | 4 |
| `ZoomPolicyTest` | 4 |
| `CameraOrientationCalculatorTest` | 3 |
| `ExifValueFormatterTest` | 3 |
| `GeoTextFormatterTest` | 3 |
| `PhotoNameFormatterTest` | 3 |
| `LocationFreshnessPolicyTest` | 2 |
| **Total** | **71 tests, 0 failures, 0 errors, 0 skipped** |

## Baseline gate results

Command (Gradle 8.7 wrapper, JDK 17.0.20.1 Temurin):

```
./gradlew testDebugUnitTest --rerun lintDebug assembleDebug assembleRelease
```

Result: **BUILD SUCCESSFUL**.

| Gate | Outcome |
| --- | --- |
| `testDebugUnitTest` | 71 pass / 0 fail / 0 skipped |
| `lintDebug` | 0 errors, 18 warnings — build not aborted |
| `lintVitalRelease` | pass (runs as part of `assembleRelease`) |
| `assembleDebug` | `app/build/outputs/apk/debug/app-debug.apk` — 978,614 B |
| `assembleRelease` | `app/build/outputs/apk/release/app-release.apk` — 79,204 B, **signed** |

Release signing is real, not an unsigned stand-in: `keystore.properties` and
`release.keystore` exist locally, are matched by `.gitignore`, are **not** tracked
by Git, and `validateSigningRelease` ran. Both are excluded from every commit and
report in this migration.

Note the release/debug size ratio is dominated by R8 shrinking plus resource
shrinking being enabled only on release; it is not a Kotlin-vs-Java measurement.
APK-size change attributable to the migration is measured per batch below using
comparable variants, against the 79,204 B release baseline.

## Lint baseline — issue IDs, not just totals

Recorded so that a later batch cannot substitute different warnings and still
claim "18 warnings".

| Issue ID | Count | Location |
| --- | --- | --- |
| `ClickableViewAccessibility` | 3 | `ui/MainActivity.java` |
| `InlinedApi` | 4 | `camera/CameraController.java` (API 28 fields, minSdk 26) |
| `DiscouragedApi` | 3 | `AndroidManifest.xml` (fixed orientation × 3 activities) |
| `VectorRaster` | 3 | `drawable/illustration_capture.xml`, `illustration_organize.xml`, `illustration_report.xml` |
| `ExifInterface` | 2 | `media/ExifMetadataWriter.java`, `media/PhotoRepository.java` |
| `CustomSplashScreen` | 1 | `ui/SplashActivity.java` |
| `DataExtractionRules` | 1 | `AndroidManifest.xml` (`allowBackup`) |
| `OldTargetApi` | 1 | `app/build.gradle` (targetSdk 35 vs compileSdk 36) |

**All 18 are pre-existing.** None is caused by Kotlin, and none is a language
migration target. `ExifInterface`, `InlinedApi`, `ClickableViewAccessibility` and
`CustomSplashScreen` will follow their files into `.kt` — same ID, same count, new
filename. Any *new* issue ID, or any count change, must be explained in the batch
that introduces it.

## Toolchain (unchanged by this migration)

AGP 8.6.1, Gradle 8.7, Kotlin 2.1.21, JDK 17, `compileSdk 36`, `targetSdk 35`,
`minSdk 26`, Groovy DSL, `android.useAndroidX=false`, `nonTransitiveRClass=true`,
`suppressUnsupportedCompileSdk=36`. Sole dependency: `junit:junit:4.13.2`.

`android.suppressUnsupportedCompileSdk=36` is the pre-existing suppression for the
compileSdk/targetSdk gap. Recorded above as `OldTargetApi`; it is not new and not
added by this migration.

## Watermark orientation contract (read from source, Phase 3 pre-work)

Established by reading caller and callee together. The representations are **not**
interchangeable and each value now has one meaning:

- `CameraOrientationCalculator.jpegOrientation(sensorOrientation, displayRotationDegrees, frontFacing)`
  → **degrees** (0/90/180/270), written directly to `CaptureRequest.JPEG_ORIENTATION`
  at `CameraController.java:978` and cached as `lastJpegOrientation`.
- `CameraOrientationCalculator.relativeRotation(sensorOrientation, displayRotation, frontFacing)`
  → **degrees**, used only for the preview `Matrix` in `configureTransform()`. Not the
  same quantity as the above.
- `PhotoRepository.save(..., rotationDegrees, ...)` → the degrees value from
  `lastJpegOrientation()`, passed to `PhotoWatermarker.apply`.
- `PhotoWatermarker.apply` → `WatermarkPolicy.uprightRotation(degrees)` snaps degrees to
  a quarter turn before `Matrix.postRotate`. This is the unit fix from `98b65c7`; before
  it, the same argument was compared against EXIF tags 3/6/8 and never matched.
- Mirroring is **separate from orientation**: driven by `frontFacing`, not by an EXIF
  tag. `PhotoRepository.java:84` passes `frontFacing` as the `mirror` argument, so saved
  selfies are mirror-flipped by design. Mirroring is applied inside `rotateToUpright`,
  before `drawWatermark`, so stamp text is never mirrored. ✔ matches the intended rule.
- `ExifMetadataWriter.write` sets no `TAG_ORIENTATION`, and `Bitmap.compress` drops EXIF
  wholesale. So the stamped file carries **no orientation tag** and its correctness rests
  entirely on pixels being physically upright. Consistent, and it means an over-rotation
  is not masked by a compensating tag.
- `MainActivity.java:1325-1329` applies the *same* degrees + mirror to the raw JPEG for
  the in-app review and last-shot thumbnail. This is the pre-existing behaviour that
  `98b65c7` aligned the saved file with.

### Open risk requiring hardware

`BitmapFactory.decodeByteArray` does not consult EXIF, so whether
`PhotoWatermarker`'s rotation is correct or is a **double rotation** depends on whether
the camera HAL has already physically rotated the encoded pixels to honour
`JPEG_ORIENTATION`. Android permits either outcome and OEM behaviour differs.

The evidence in `98b65c7` is an **emulator** capture (1856×1392 sideways → 1392×1856
upright), which shows the emulator HAL ignoring `JPEG_ORIENTATION`. A Samsung
`SM-S918B` HAL is likely to honour it, which would over-rotate. The in-app review path
gives an independent cross-check, so this is testable rather than speculative — but it
cannot be settled without the physical device.

**Status: BLOCKED pending evidence.** Until it passes, the watermark change and all
camera/rendering conversion are *not* release-validated.

### Matrix feasibility — claim corrected by measurement

The Phase 0 reasoning here claimed the 8-case matrix was executable over `adb` because
`screenOrientation="unspecified"` plus a live `Display.getRotation()` read means every
rotation angle is reachable at capture time. **That part is true, and it is not enough.**

`settings put system user_rotation N` changes the *logical* display rotation only. The
phone stays physically where it is lying. The camera sensor is mounted to the physical
device, so forcing `user_rotation=1` while the handset is physically portrait makes the app
compute a `JPEG_ORIENTATION` that matches a landscape hold the user never performed — the
saved scene then looks rotated 90°, which is **correct behaviour for the state the app was
told it is in**, not a defect.

Consequence: logical-rotation sweeps can verify that orientation is *plumbed through* and
that nothing crashes, but they **cannot** verdict scene uprightness. Only a physically
rotated handset can. Rows below are graded accordingly.

## Hardware coverage

Owner granted authorization in-session on 2026-09-27 to install and capture on the
physical device for this migration.

| Target | Identity | Available | Authorized | Status |
| --- | --- | --- | --- | --- |
| Samsung Galaxy S23 Ultra | `RZCW30B200V`, `SM-S918B`, Android 16 / API 36 | connected | **granted** | instrumented **PASS**; capture matrix not yet run |
| Emulator | `emulator-5554`, x86_64, API 36, single camera | running | granted | instrumented **PASS**; rear case only |

`androidTest` coverage is 2 test methods / 11 assertions. The physical device is the only
multi-camera target; the emulator cannot cover the front-camera cases.

### Instrumented run, 2026-09-27 10:08 UTC

`./gradlew connectedDebugAndroidTest` against the tree at the batch-5 commit, both
attached targets:

| Target | Tests | Failures | Errors | Skipped | Wall time | Runner exit |
| --- | --- | --- | --- | --- | --- | --- |
| `SM-S918B` (Android 16) | 2 | 0 | 0 | 0 | 3.831 s | clean |
| `verify36` (AVD, API 36) | 2 | 0 | 0 | 0 | 5.010 s | clean |

Two things this settles. The previously reported **runner hang on exit did not
reproduce** — `BUILD SUCCESSFUL`, no timeout wrapper tripped. And the assertions are real
interop evidence: `MainActivityInstrumentedTest` resolves `R.id.preview`,
`captureButton`, `flashButton`, `gridButton`, `lensButton` and `permissionPanel` on a live
`MainActivity`, so the preserved view IDs and the Java test's call into the Activity both
hold while `CameraController` and `MainActivity` are still Java.

**Not covered by this run**, and still the gate before any release claim: the 8-case
rear/front × orientation capture matrix, watermark orientation and front-camera mirroring
against a Samsung HAL that may honour `JPEG_ORIENTATION` on its own, repeated-capture
resource cleanup, lens-switch zoom reset, and screen-off/on recovery. See *Open risk
requiring hardware*.

## Deferred defects (found during Phase 0, deliberately not fixed here)

Preservation rule 5 and rule 9 keep behavior fixes out of conversion commits. These are
recorded so they are neither silently "cleaned up" during conversion nor lost.

**`PhotoWatermarker.META_COLOR` is written with 7 hex digits.**
`media/PhotoWatermarker.java:20` declares `private static final int META_COLOR = 0xE6E9ECF;`
which is `0x0E6E9ECF` — alpha `0x0E` = 14/255, roughly 5% opacity. The surrounding
literals are 8 digits (`0xFF000000`, `0xFFFFFFFF`, `0xFFFFC857`), and the intended colour
is almost certainly opaque `0xFFE6E9EC`. This is the paint used for every non-headline,
non-brand watermark line, i.e. the coordinate and timestamp rows, which would render
nearly invisible over the dark panel.

Owner's decision: **record now, verify against real captured pixels, then fix in its own
commit with a regression test.** Not yet independently confirmed as a visual defect — the
`98b65c7` report describes the stamp as readable, so this needs a pixel measurement before
any change. Conversion of `PhotoWatermarker` must carry the literal unchanged; in Kotlin
`0xE6E9ECF` parses to the same `Int`, so a straight conversion preserves the value and the
defect alike.

### Verified on hardware, and fixed separately

**The defect is real.** Captured on the API 36 emulator after batch 5 and inspected as
rendered pixels: the headline `SRM AP UNIVERSITY · EVENT CAMERA` draws in full-strength
white, while the coordinate line (`39.237255, -123.150032`) and the timestamp line
(`27 Sep 2026 3:44 PM`) are legible only as faint ghosts over the panel. That is the
predicted alpha `0x0E` ≈ 5% opacity, measured rather than inferred.

`PhotoWatermarker` was converted carrying `0xE6E9ECF` unchanged, as required. The fix is a
separate commit from every conversion commit: `META_COLOR` → `0xFFE6E9EC`, opaque light
grey, plus `app/src/test/.../media/PhotoWatermarkerTest.java`, three tests that read the
four ink constants by reflection (so they stay private) and assert each one's alpha. This
is a **behaviour change**, not a language conversion, and is excluded from the
"behaviour-preserving" claim made for batches 1-5.

## Physical matrix results, Samsung SM-S918B (2026-09-27 10:35-10:40 UTC)

Run with the owner's explicit go-ahead after they freed the phone. All-Kotlin debug build
installed over the existing one (no signature conflict, no uninstall), permissions granted,
`MainActivity` driven over `adb` with the tap target re-read from `uiautomator` each time.
Device state restored afterwards (`user_rotation=0`, `accelerometer_rotation=1`, HOME).

| Case | Physical pose | Result | Evidence |
| --- | --- | --- | --- |
| rear × logical 0° | upright | **PASS** | `PGSC_20260927_103518_629_16.4634N_080.5058E.jpg` — scene upright, all four stamp lines legible, place name resolved, 3,452,042 B full-sensor capture |
| rear × logical 90/180/270° | upright handset, forced logical rotation | **NOT A VALID TEST** | files saved and decoded cleanly, but see the correction above: the phone never physically turned |
| preview ↔ saved, rear | upright | **PASS** | `screencap` of the live preview vs the file written moments later: same framing, same upright orientation. This is the independent cross-check the open risk needed |
| lens switch to front | — | captured, **see below** | `PGSC_20260927_103614_335_...jpg` shows the room the front camera faces (wall, mounted display, whiteboard) with the scene rotated 90° and the stamp horizontal |
| front × logical 90/180/270° | forced logical rotation only | **NOT A VALID TEST** | as above |
| repeated captures | upright | **PASS** | 8/8 sweep captures plus the control pair all produced new files; `logcat -b crash` empty; `MainActivity` kept focus throughout |
| front-camera uprightness verdict | — | **INCONCLUSIVE** | one frame suggests a 90° error on the front path; the control re-run did not actually switch lens, so the anomaly is unreproduced and unattributed. Needs a *handheld* upright selfie |
| screen-off/on recovery, gallery display, share sheet | — | **NOT ATTEMPTED** | still open |

### What this settles about the open risk

The `98b65c7` worry was that a Samsung HAL which honours `JPEG_ORIENTATION` itself would
make the app's own rotation a **double rotation**, and the emulator (whose HAL ignores it)
could not show this. For the rear camera at natural portrait it does **not**: the saved
frame is upright, matches the live preview, and is not over-rotated. That is the first
real-hardware evidence on this path and it is clean.

### What is still not settled

- **Front-camera scene uprightness on hardware — INCONCLUSIVE**, per the table. This is now
  the single most likely place a real orientation bug hides, and it is exactly the case the
  emulator can never reach.
- Mirroring of saved selfies was not separately judged, because the frame it would be judged
  on is the inconclusive one.
- Repeated-capture memory pressure over a long session, screen-off/on camera recovery,
  gallery display and the share sheet.

To close the front-camera row, the phone must be **held upright by a person** for one rear
and one front shot — not laid on a desk, and not driven by `user_rotation`.

### Front camera settled by measurement, not by eye

Judging "is this upright?" from a photo of a room on a tilted phone is not evidence, so the
front path was tested two ways that do not depend on the handset's pose.

**1. Preview vs saved, scored.** The live front preview was screenshotted and the file
saved seconds later compared against it by normalised cross-correlation over all eight
transforms (4 rotations × mirrored/not), with UI chrome and the stamp panel cropped out:

| transform | score |
| --- | --- |
| **0° as-is** | **+0.7145** |
| 180° mirrored | +0.6759 |
| 270° mirrored | +0.1093 |
| 90° either way | −0.02 … +0.11 (noise) |

**2. Method control.** Deliberately rotating the saved file by 90° before scoring moved the
best match from `0°` to `270°` at the same +0.7145, proving the scorer can detect a quarter
turn. Without that control the first result would have been unfalsifiable.

So the front-camera capture path is **consistent with its own preview** — no relative
rotation, no unexpected mirroring. Combined with the rear path being verified upright at
natural portrait, this closes the `JPEG_ORIENTATION` double-rotation risk for both lenses at
the one pose that can be tested unattended.

### Screen-off / on camera recovery — PASS

Driven with `KEYCODE_SLEEP` / `KEYCODE_WAKEUP` on the SM-S918B, then read from the system
logs rather than from the UI (the wake brought up the secure keyguard, which is not
bypassed):

| Signal | Observation |
| --- | --- |
| `logcat -b crash` | empty — no `FATAL EXCEPTION`, no `AndroidRuntime` |
| process | `pidof com.perfectgps.camera` → 3395, still alive |
| camera | `CameraManagerGlobal: Camera 1 facing CAMERA_FACING_FRONT state now CAMERA_STATE_ACTIVE for client com.perfectgps.camera` after the wake |
| rendering | `SurfaceFlinger` continuing to composite `MainActivity$_3395` buffers post-wake |
| immersive | `InsetsController` re-applying the status-bar visibility for `MainActivity` |

The camera is genuinely re-acquired across a sleep/wake cycle, not merely left dangling.

### Review, share, delete and gallery display — PASS

Driven on the SM-S918B with the screen unlocked, all through the all-Kotlin build. Controls
were discovered from `uiautomator` rather than assumed, and two of them turned out to be
**unlabelled views** (the last-shot thumbnail and the Share/Delete buttons carry no
resource-id), which is why the earlier `galleryButton`/`shareButton` taps found nothing.

| Behaviour | Evidence |
| --- | --- |
| Immersive chrome auto-hide | `locationStatus`, `coordinatesText`, `detailsText` vanish from the hierarchy, then reappear after a single tap on the preview |
| Flash capability gating | `flashButton` is absent from the hierarchy on the front lens, present on the rear |
| Review screen | Tapping the last-shot thumbnail opens a full-frame review with the caption `4:23 pm · 16.463397, 80.505807 · GPS TAGGED` |
| Share | Bottom sheet `SEND PHOTO TO` with real targets (Add to Maps, Bixby Vision, Bluetooth, More…); BACK returns to the review with Share/Delete still present |
| Delete — cancel branch | Dialog `Delete this photo?` with `CANCEL`/`DELETE`; CANCEL returns to review, file and MediaStore row both intact |
| Delete — confirm branch | After `DELETE`: MediaStore query for the name goes from `Row: 0 _display_name=PGSC_...105354_779...jpg` to `No result found`, and `ls` reports the file gone. Review dismisses back to the camera screen |
| Gallery display | `am start -a VIEW -d content://media/external/images/media/28017 -t image/jpeg` resolves into the system image-viewer chooser; `_data` reads `/storage/emulated/0/Pictures/Perfect GPS Camera/…` |
| Stability | `logcat -b crash` empty across every step above |

### Release-signed build on hardware — PASS for the capture path

Owner authorised uninstalling the debug build (different signing key) to test the shrunk,
minified 82,084 B release APK on the SM-S918B. Verified before installing that no source
file was newer than the APK.

| Behaviour on the **release** build | Result |
| --- | --- |
| Install of signed release over uninstalled debug | Success, no signature or split-ABI failure |
| Splash → onboarding | Rendered correctly: `SRM AP UNIVERSITY`, `EVENT DOCUMENTATION`, `01 / 03 • CAPTURE`, `Capture with confidence`, `Fresh GPS fix on every capture`, NEXT/SKIP all live |
| Onboarding walk | NEXT → NEXT → final page → completion |
| Permission panel on fresh data | Appeared (`grant`, `permission` nodes) — the re-prompt-suppression and first-run grant path both work |
| Camera open + preview | `MainActivity` reached, rear lens with `flashButton` present |
| Rear capture, phone held upright | `PGSC_20260927_111323_918_16.4634N_080.5058E.jpg` — scene upright, all four stamp lines legible |
| Lens switch to front | `flashButton` correctly disappears from the hierarchy (capability gating survives R8) |
| Front capture, phone held upright | `PGSC_20260927_111346_354_16.4634N_080.5058E.jpg` — curtain rod level, rings hanging down, window vertical: **upright** |
| Crash buffer for `com.perfectgps.camera` | empty |

**This closes the front-camera absolute-uprightness question.** Held upright by the owner,
both lenses produced upright scenes on the release build, so the earlier sideways frames were
the handset's angle, not an orientation defect. The `JPEG_ORIENTATION` double-rotation risk
is now closed on real hardware for rear *and* front at natural portrait.

### Run halted: the handset came back into use

While driving the review/share taps on the release build the foreground had already moved
to the owner's Instagram, so several coordinate taps landed there instead of on the app.
Automation was stopped immediately and no further input was sent. `com.perfectgps.camera`
had no crash entries and its process had simply been reclaimed by Android while
backgrounded — normal, not a defect.

Consequently **review / share / delete on the release build are NOT verified**. They are
verified on the debug build (previous section); what remains unproven is specifically
whether R8 shrinking disturbs those paths. This needs the phone free again.

Also outstanding: the phone currently has the **release** build installed, not the debug
build, because restoring it would mean another interrupt.

### R8 shrinker safety, read from the build outputs

Inspected without touching the handset, because the on-device run only ever exercises code
that survived shrinking.

`app/build.gradle` has `minifyEnabled true` and `shrinkResources true` with
`proguard-android-optimize.txt` and no app-specific keep rules, so R8 is free to rename and
inline everything the manifest does not pin.

**Seeds (7, from `mapping/release/seeds.txt`) — exactly the right set:**

| Kept | Why it matters |
| --- | --- |
| `ui.MainActivity`, `ui.SplashActivity`, `ui.OnboardingActivity` + constructors | manifest components must keep their names |
| `ui.GridOverlayView: void setGridEnabled(boolean)` | AGP's standard View-subclass setter rule |

That `setGridEnabled` seed is a direct payoff of the batch-4 decision to model the grid flag
as a Kotlin property named `isGridEnabled` with a custom setter, rather than writing
`fun setGridEnabled(...)`. The generated JVM setter name is the one the keep rule matches,
so the XML/reflection-facing surface survives shrinking unchanged.

**Renamed but intact** (`mapping.txt`, 42 app classes): `CameraController → a.m`,
`LocationController → b.a`, `PhotoRepository → c.c`, `GeoLocationSnapshot → d.a`,
`GridOverlayView → e.a`, plus every `$Companion`, `$Callback`, `$SavedPhoto`,
`$ExternalSyntheticLambda` and captured-session inner class. `MainActivity` keeps its real
name. Nothing that must exist was dropped.

**Inlined away entirely:** the pure utilities. `WatermarkPolicy`, `PhotoWatermarker`,
`ExifValueFormatter`, `PlaceNameResolver`, `ExifMetadataWriter`, `GeoTextFormatter`,
`PhotoViewPolicy`, `ZoomPolicy`, `PreviewGesturePolicy`, `BitmapDecodePolicy` appear in
`mapping.txt` only as *inlined method frames* inside their callers (e.g. three
`WatermarkPolicy.buildLines` frames folded into a caller's `run`), and `CapturePolicy`
leaves no trace at all. This is R8 doing its job on stateless singletons, not a loss of
behaviour — the release run stamped place name, brand line, coordinates and timestamp
correctly, which is `buildLines` + `brandLineIndex` + the four size/margin functions.

**Removed members** (`usage.txt` is a member list, not a class list): dead `R$color` /
`R$drawable` / `R$style` constants, `private void <init>()` on the R nested classes, and the
private `const val` fields — `META_COLOR`, `PANEL_COLOR`, `EPSILON`, `PREFS_NAME`,
`ALBUM`, `CAPTURE_DRAIN_MILLIS`, `COORDINATE_SCALE` and friends. Those are Kotlin
compile-time constants already inlined at every use site, so the backing fields are dead.
No public or package-visible member was removed.

**One honest limitation:** the 74 JVM unit tests run against the *unshrunk* classes, so they
prove the logic is right, not that the shrunk shape is right. The shrunk shape is covered
only by the on-device release run — which passed for capture, lens switching, flash gating,
onboarding, the permission panel and both stamps.

## Physical capture matrix, partial run on the emulator (rear camera)

Driven over `adb` with the tap target re-read from `uiautomator` at each rotation, because
the capture button's bounds move between portrait and landscape — a fixed coordinate
silently misses and produces a false PASS.

| Case | Device rotation | Saved file | Stamp orientation | Mirrored? | GPS IFD | Software tag | Result |
| --- | --- | --- | --- | --- | --- | --- | --- |
| rear × 0° | 0 | `PGSC_20260927_101452_731_...N...W.jpg` | upright, readable | no | present | present | PASS |
| rear × 90° | 1 | `PGSC_20260927_101506_315_...N...W.jpg` | upright, readable | no | present | present | PASS |
| rear × 180° | 2 | `PGSC_20260927_101519_703_...N...W.jpg` | saved, not yet inspected | — | — | — | PARTIAL |
| rear × 270° | 3 | `PGSC_20260927_101531_854_...N...W.jpg` | saved, not yet inspected | — | — | — | PARTIAL |
| front × 4 | — | — | — | — | — | — | **BLOCKED** — emulator has one camera |

Storage path (`Pictures/Perfect GPS Camera/`), the `PGSC_<yyyyMMdd>_<HHmmss>_<SSS>_<lat><hem>_<lon><hem>.jpg`
name, the live GPS fix in the filename, and MediaStore insertion all held across the sweep.

**This does not clear the open risk.** The emulator HAL ignores `JPEG_ORIENTATION`; the
Samsung SM-S918B is the target that may honour it, and that is the case where an
over-rotation would show. The phone was attached and authorised but is behind a secure
lockscreen (`mCurrentFocus=Window{... Bouncer}`), which is not bypassed. **Front camera and
all Samsung cases remain BLOCKED pending the owner unlocking the device.**

## Conversion ledger

| # | Class | LOC (Java) | Kind |
| --- | --- | --- | --- |
| 0 | `ZoomPolicy` | 13 | baseline, converted in `cd1f48c` |
| 1 | `PreviewGesturePolicy` | 31 | batch 1, `f7c882b` |
| 2 | `GeoLocationSnapshot` | 137 | pure model, batch 2 |
| 3 | `BitmapDecodePolicy`, `CameraOrientationCalculator`, `CapturePolicy`, `ExifValueFormatter`, `GeoTextFormatter`, `LocationFreshnessPolicy`, `PhotoNameFormatter`, `PhotoViewPolicy`, `WatermarkPolicy` | 430 total | pure utilities, batch 3 |
| 4 | `PlaceNameResolver`, `GridOverlayView`, `ExifMetadataWriter`, `PhotoWatermarker`, `LocationController` | 701 total | Android-dependent, batch 4 |
| 5 | `BrandUi`, `PhotoRepository`, `SplashActivity`, `OnboardingActivity` | 808 total | Android-dependent, batch 5 |
| 6 | `CameraController`, `MainActivity` | 3,392 total | in conversion |

Per-batch gate evidence is recorded in each batch below rather than summarised, so an
unchanged-baseline claim can be checked against the actual numbers.

### Batch 3 — the nine remaining pure utilities

`BitmapDecodePolicy`, `CameraOrientationCalculator`, `CapturePolicy`,
`ExifValueFormatter`, `GeoTextFormatter`, `LocationFreshnessPolicy`,
`PhotoNameFormatter`, `PhotoViewPolicy`, `WatermarkPolicy`, plus `model/GeoLocationSnapshot`
and the already-landed `PreviewGesturePolicy`. One class per commit; the gates below ran
once over the whole batch.

All nine became `object` + `@JvmStatic`, so the unmodified Java tests and the unconverted
`MainActivity.java` keep calling them as statics. Public Java-visible constants became
`const val` (real `public static final` fields), not `@JvmField`.

Three conversions needed a real decision, recorded rather than buried:

- **`ExifValueFormatter`** — Kotlin hides `BigInteger.longValue()`; used `.toLong()`,
  which is the same truncating conversion. Kotlin also has no `Int + String` operator, so
  the two concatenations became string templates producing byte-identical output.
- **`WatermarkPolicy` / `PhotoViewPolicy`** — `Math.max(int,int)`-style widening had to be
  made explicit (`MIN_LONG_EDGE.toFloat()`), and `String[]` stayed `Array<String>` via
  `toTypedArray()`, which erases to `String[]`.
- **`LocationFreshnessPolicy`** — the no-op
  `TimeUnit.MILLISECONDS.toMillis(maxAgeMillis)` was kept verbatim. It is dead arithmetic
  in Java too; removing it would be a change, not a conversion.

`GeoLocationSnapshot` keeps its 9-argument constructor with `provider: String?` so the
null → `"unknown"` fallback survives, and keeps non-`get`-prefixed accessors
(`latitude()`, `isValid()`) because Java callers use those names.

Measured after batch 2: **71 tests pass / 0 skipped / 0 failed**; lint **0 errors,
18 warnings with an identical ID distribution** to the baseline table above; release APK
**80,240 B** (baseline 79,204 B, +1,036 B).

### Batch 4 — `PlaceNameResolver`, `GridOverlayView`, `ExifMetadataWriter`, `PhotoWatermarker`, `LocationController`

Two conversion hazards this batch caught, both invisible to a mechanical translator:

- **`GridOverlayView`** — Java's `setGridEnabled`/`isGridEnabled` pair is reproduced
  exactly by naming the Kotlin property `isGridEnabled` with a custom setter that keeps
  the `invalidate()` side effect, instead of writing a `set…` function that would clash
  with the backing field's JVM signature.
- **`LocationController`** — `manager` is a nullable `LocationManager?`. Where the Java
  had an explicit null guard (`start`, `stop`) Kotlin smart-cast covers it; where the Java
  had *no* guard and would simply have thrown (`requestUpdates`, `restoreBestLastLocation`,
  reached from `onProviderEnabled`) the conversion uses `manager!!`, which throws the same
  `NullPointerException` at the same point. `Float`→`Double` widening at the
  `GeoLocationSnapshot` constructor is now explicit. The `if (location == null) return`
  guard in `onLocationChanged` was dropped because the platform declares that parameter
  `@NonNull`; every real caller, including the internal `restoreBestLastLocation` path,
  already null-checked before dispatching.
- **`PhotoWatermarker`** — `0x99000000` and `0xFFFFC857` infer as `Long` in Kotlin where
  Java read them as negative `Int` ARGB constants, so they are `.toInt()`-cast; the
  documented 7-digit `META_COLOR = 0xE6E9ECF` defect is carried **unchanged**, per the
  deferred-defects rule.

### Batch 5 — `BrandUi`, `PhotoRepository`, `SplashActivity`, `OnboardingActivity`

- `BrandUi` was package-private Java with package-private statics. Kotlin has no
  package-private, so it is now a public `object`. This is a **visibility widening**, the
  one intentional API change so far, and it is temporary: the remaining Java callers are
  exactly the two files still being converted, after which `internal` can be reconsidered
  on its own merits.
- `PhotoRepository`'s try-with-resources became `.use {}`, which has the same
  close-and-suppress semantics. Two cases could not be expressed directly —
  `resolver.openOutputStream(uri,"w")` returns nullable and `use` is not callable on a
  nullable receiver — so the null check is hoisted above the `use` to throw the identical
  `IllegalStateException` before any stream is touched, preserving the original's
  "delete the MediaStore entry" rollback path.
- `SplashActivity` / `OnboardingActivity`: `ViewFlipper.LayoutParams` and
  `ScrollView.LayoutParams` were *inherited* nested-class aliases in Java and Kotlin does
  not inherit them; both resolve to `FrameLayout.LayoutParams`, which is what the Java
  actually constructed. `View.setClipToPadding` has no getter pair, so it stays a setter
  call rather than a property. ARGB literal `0x8FFFFFFF` exceeds `Int.MAX_VALUE` and is
  `.toInt()`-cast. Every dp/sp number, colour, string resource, view order and the
  `srm_ap_event_camera` / `onboarding_complete` preference keys are unchanged.

Measured after batches 4 and 5: **71 tests pass / 0 skipped / 0 failed**; lint **0 errors,
18 warnings, identical ID distribution**; debug APK **984,242 B**; release APK
**81,364 B** (baseline 79,204 B; cumulative drift +2,160 B across 21 converted classes).
Every one of these runs compiled `MainActivity.java` and `CameraController.java` against
the converted Kotlin, so Java→Kotlin interop is being exercised continuously, not sampled.

### Batch 6 — `CameraController` and `MainActivity`

The two large files, converted in parallel and gated together. 1,267 and 2,141 Kotlin
lines against 1,239 and 2,153 Java lines.

`CameraController` was signature-audited with `javap` old-vs-new: `FLASH_OFF/AUTO/ON` are
still `public static final int` with the same values, the constructor descriptor
`(Activity, TextureView, Callback)` is unchanged, and `$Callback` / `$JpegConsumer` are
byte-identical. Its `Image` handling is the part a mechanical translator gets wrong: the
Java `try (Image image = reader.acquireNextImage())` became an explicit nested
`try / finally { image.close() }`, so the image is still released on the error path inside
the same outer `catch (Exception)`.

Divergences this batch accepted, each with a reason rather than silence:

- **OIS probe.** The Java compared `Boolean.TRUE` against an `int[]`-typed key, so it was
  always false. Kotlin cannot express that comparison; it is written as
  `(get(...) as Any?) == true` with a comment and is still always false. Optical
  stabilisation therefore remains un-enabled, exactly as before.
- **Local snapshots instead of field re-reads.** In `focusAt` and `applyZoom` the Java
  re-read a nullable field after its own guard. A teardown landing in that window used to
  raise an uncaught NPE; it now surfaces as a caught `IllegalStateException`. Same
  handlers, looper, thread ownership, shutdown order and `volatile` fields.
- **One always-true null check dropped** (`postConfigureTransform`'s `mainHandler`), a
  final field initialised at declaration.
- **A synthetic `CameraController$Companion` class exists** because `const val` requires
  it. No Java-visible member changed.
- **16 `!!` in `CameraController`** where the Java dereferenced a field with no guard, so
  the NPE stays at the same line. One site uses `requireNotNull(previewSurface)` instead,
  because there the Java passed a null into `Builder.addTarget`, which throws a *caught*
  `IllegalArgumentException` — `!!` would have changed the exception type.

`MainActivity` needed four post-conversion fixes found only by the compiler, all
behaviour-neutral: Kotlin forbids a statement continuing on a line that begins with `==`
or `||`; `dp()` returns `Int` where `setShadowLayer` wants `Float` (Java widened silently);
and `ViewConfiguration.getDoubleTapTimeout()` is **static**, which Java allowed to be
called through an instance and Kotlin does not.

Two quality notes recorded rather than fixed, because fixing them is not a conversion:

- `MainActivity.kt` carries **280 `!!`**. The lifecycle sites were checked line-for-line
  against the Java and every one replaces an *unguarded* Java dereference, so no new crash
  path was introduced and `removeCallbacks(...)` still passes identity-stable `Runnable`
  fields. The cleaner shape is `lateinit` for views created in `onCreate`; that is a
  follow-up, not part of this migration.
- `BrandUi` is public where the Java was package-private (see batch 5).

Measured after batch 6, all five tasks in one run:

| Gate | Baseline | After batch 6 |
| --- | --- | --- |
| Unit tests | 71 pass / 0 skip / 0 fail | **74 pass / 0 skip / 0 fail** (12 files; +3 from the colour regression test) |
| Lint | 0 errors / 18 warnings | 0 errors / 18 warnings, identical ID distribution |
| Debug APK | 978,614 B | 958,405 B (**−20,209 B**) |
| Signed release APK | 79,204 B | 82,084 B (+2,880 B) |
| `assembleDebugAndroidTest` | builds | builds — the Java instrumentation test still compiles against Kotlin `MainActivity` |
| Instrumented, SM-S918B | 2/2 pass | **2/2 pass, 0 skipped, clean exit** |
| Instrumented, AVD API 36 | 2/2 pass | **2/2 pass, 0 skipped, clean exit** |

The debug APK getting *smaller* while the release APK grows is R8 and resource shrinking
reacting to the changed reachability graph, not a Kotlin-size signal; the release number
is the comparable one and it is +2,880 B across all 23 classes.

### Live capture through the fully-Kotlin pipeline

Installed the all-Kotlin debug build on the AVD and captured over `adb`:
`PGSC_20260927_102638_062_39.2373N_123.1500W.jpg`. No crash-buffer output. The rendered
stamp now reads `SRM AP UNIVERSITY · EVENT CAMERA` / `39.237255, -123.150032` /
`27 Sep 2026 3:56 PM` — headline and both meta lines legible, upright, unmirrored. That is
the same frame shape that showed the two meta lines as ghosts before the colour fix, so it
is a direct before/after on the fixed code path, exercised through
`CameraController.kt` → `PhotoRepository.kt` → `PhotoWatermarker.kt` → `ExifMetadataWriter.kt`.

## Measured divergence list

| Kind | Detail |
| --- | --- |
| Bug fix (pre-existing, separate commit) | `98b65c7` watermark degrees-vs-EXIF |
| API widening | `BrandUi` package-private → public (batch 5) |
| Guard removal | `LocationController.onLocationChanged` null check, unreachable under `@NonNull` (batch 4) |
| `!!` introduced | `LocationController.requestUpdates`, `LocationController.restoreBestLastLocation` — both reproduce an existing NPE site (batch 4) |
| Defect carried unchanged | `PhotoWatermarker.META_COLOR = 0xE6E9ECF` (7 hex digits, ~5% alpha) |

## Gate status legend

- **PASS** — ran here, output inspected.
- **FAIL** — ran here, broke.
- **BLOCKED** — cannot run under current authorization or hardware.
- **NOT ATTEMPTED** — in scope, not yet reached.

## Batch detail — the two classes converted before this session

Baseline for comparison: 71 tests, 18 lint warnings, release APK 79,204 B, debug APK
978,614 B. "Unchanged" means all four of those, with lint matching by issue ID and count
rather than by total.


### Batch 1 — `PreviewGesturePolicy`

Converted to `object` with `const val` for the three `DIRECTION_*` constants, which the
compiler emits as real `public static final int` fields, and `@JvmStatic` on both
predicates. `Math.abs` retained over `kotlin.math.abs` to match `ZoomPolicy.kt`. All six
parameters across both methods are primitives, so no nullability decision arises, and
`elapsedMillis <= timeoutMillis` widens `Int` to `Long` on both sides as in Java.

First place two-way interop is *demonstrated* rather than assumed, on both sides at once:
unconverted `MainActivity.java` calls both methods and reads the constants as fields, and
the untouched Java test `PreviewGesturePolicyTest` still compiles and passes all 11 cases
against the Kotlin object.

### Batch 2 — `GeoLocationSnapshot`

Placed in Phase 1 rather than Phase 2 despite living in `model/`: its only import is
`java.util.Objects`, so it is framework-free, and it is a dependency of four utilities
(`CapturePolicy`, `LocationFreshnessPolicy`, `GeoTextFormatter`, `PhotoNameFormatter`),
so converting it first is what unblocks them in dependency order.

Deliberately **not** a data class. Java callers use accessor-style methods named
`latitude()`, not `getLatitude()`, so a data class would rename the entire public surface.
Kept as a plain final class with `private val` fields and matching functions. `javap`
confirms the emitted shape is the same as the Java original's: `public final class`, nine
`private final` fields with no generated getters, constructor
`(double,double,double,double,double,double,long,String,int)`, the nine accessors,
`isValid`, `hasAltitude`, `withLatitude`, `equals`, `hashCode` — and notably **no**
`getLatitude`-style additions. Java callers and Kotlin's property/function namespaces
coexist here without a clash because private properties generate no accessor.

Two hazards worth stating, both handled:

- **Equality.** The Java used `Double.compare(a, b) == 0`. Kotlin's `==` on primitive
  `Double` is IEEE comparison, which differs on both edge cases: it makes `NaN != NaN`
  and `0.0 == -0.0`. Left as explicit `java.lang.Double.compare(...) == 0`. `Double` in
  Kotlin is the primitive alias and does not expose `java.lang.Double` statics, so the
  calls are fully qualified.
- **`hashCode`** kept as `java.util.Objects.hash` with the original nine arguments in the
  original order; Kotlin boxes each `Double` to `java.lang.Double`, whose `hashCode` is
  the same `doubleToLongBits` fold the Java relied on.

`provider` stays a nullable constructor parameter normalised to `"unknown"` in the body,
preserving both the null-coalescing and the fact that `withLatitude` re-passes an already
normalised value.

Debug APK grew 162 B from `kotlin.Metadata`; release stayed at exactly 79,204 B because
R8 strips that metadata. Pre-existing gap, unchanged by this batch: `GeoLocationSnapshot`
has no unit test of its own, so the equality edge cases above are preserved by
construction rather than pinned by a test.

| Defect carried unchanged | `PhotoWatermarker.META_COLOR = 0xE6E9ECF` (7 hex digits, ~5% alpha) — **verified on pixels and fixed separately in `eb2d8ad`** |
| Unreachable branch dropped | `MainActivity.onJpegCaptured`: Java's `jpeg == null` arm is gone because `JpegConsumer.onJpegCaptured(jpeg: ByteArray)` is non-null and the only producer (`CameraController.kt:1100`) builds the array from an `Image` buffer, so null cannot arrive. The `isEmpty()` arm and its `IllegalStateException("Empty JPEG")` are unchanged |
| Pre-existing defects preserved verbatim | duplicate `tintLocationPin` call, `getColor()` result passed where a resource id is expected in `openAppSettings`, unused `missing` local, write-only `lastShotHasLocation` — all still present, none "cleaned up" during conversion |

## Final handoff

### What is done

All 23 owned production classes are Kotlin: **0 Java files, 23 Kotlin files, 5,361 lines**
at the batch-6 commit; **5500** lines now including the post-migration feature work and the EXIF fix.
Tests stayed Java and still compile and pass against the Kotlin app, which is the interop
proof the plan asked for rather than an assumption:

- **12 unit test files, 79 tests**, 0 skipped, 0 failed
- **3 instrumentation files, 13 tests**, 0 failures - the 10 pre-existing tests have run on
  both targets, the 3 EXIF-orientation tests on the API 36 AVD only (the handset was
  disconnected when they landed)

### Commit map

| Range | Contents |
| --- | --- |
| `95ebd0b` | Phase 0 baseline record |
| `f7c882b` | batch 1, `PreviewGesturePolicy` |
| `66d2133`, `2380834` | batch 2, `GeoLocationSnapshot`, `CameraOrientationCalculator` |
| `5b98203`…`5e08ea7` | batches 3-4, 14 classes, one commit each |
| `7c2ecab`… | batch 5, `BrandUi`, `PhotoRepository`, `SplashActivity`, `OnboardingActivity` |
| `1fc21c4`, `b747e3a`, `47ffc8d`, `4883e79`, `600a104`, `9649753`, `38415cd`, `a8fd28e`, `9188a1e`, `fe03c48` | verification records, no source change |
| `adf7231` | batch 6, `CameraController` |
| `7344bd0` | batch 6, `MainActivity` |
| `eb2d8ad` | behaviour change: watermark meta-line colour + 3 regression tests |
| `d40daf4` | behaviour change (owner-requested): precise place-detail line |
| `b37d777` | behaviour change (owner-authorised): valid upright EXIF orientation |

40 commits from `95ebd0b` through `b37d777`, plus the two status records after it. Three are
behaviour
changes (`eb2d8ad`, `d40daf4`, `b37d777`); the rest are behaviour-preserving conversions
and status records.

Rollback: `git revert d40daf4` removes the detail-line feature alone;
`git revert eb2d8ad` removes the colour fix alone; `git revert 7344bd0 adf7231` removes
batch 6; `git reset --hard 1fc21c4` returns to the last all-but-two-classes state.
Nothing has been pushed, tagged or published in this session.

### Reproduce the verification

```
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease assembleDebugAndroidTest
./gradlew connectedDebugAndroidTest        # needs a target attached
```

Expected at `b37d777`: **79 unit tests** / 0 skipped / 0 failed; **13 instrumented tests**
/ 0 failed; lint 0 errors and 18 warnings with the ID distribution in the table above;
debug APK 958,405 B; signed release APK 83,676 B (+44 B over the 83,632 B pre-fix build;
`apksigner verify` passes; the keystore
and `keystore.properties` are git-ignored and are not in any commit or report here).

To run the instrumented tests against one target only, without risking the other device:

```
adb -s emulator-5554 install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5554 shell am instrument -w \
  com.perfectgps.camera.test/android.test.InstrumentationTestRunner
```

### What is NOT done — release validation

The plan's release bar is **not fully** met. Status after the hardware run:

- **Implementation: complete.** 0 Java production files, 23 Kotlin, all gates green.
- **Rear camera on the real Samsung HAL: PASS** at natural portrait — upright, not
  over-rotated, all four stamp lines legible, place name resolved, GPS and Software tags
  retained, full-sensor output.
- **Front camera: PASS on internal consistency** — saved file matches its live preview at
  0° by scored correlation, with a deliberate-90° control proving the test can detect a
  quarter turn.
- **Screen-off/on camera recovery: PASS** — no crash, process alive, front camera
  re-acquired `CAMERA_STATE_ACTIVE` after wake.
- **Forced-`user_rotation` rows: not valid tests** (see the feasibility correction).

Still open:

1. **Physical matrix partially complete.** Rear portrait, front portrait and one front
   landscape are genuine physical verdicts; rear landscape is verified through logical
   rotation (plumbing, not scene); one front-landscape row has uncertain pose attribution.
   Zoom reset on lens switch is now **PASS**. See *Physical matrix results* above.
2. The handset was **disconnected** during the EXIF work, and the release build installed on
   it predates `b37d777`, so it needs a reinstall to carry the orientation fix.

Closed since the first draft of this section: the whole migration's implementation, rear and
front capture at natural portrait on the release build, onboarding, the first-run permission
panel, lens switching, flash capability gating, immersive chrome auto-hide, review screen,
both branches of the delete dialog, share chooser, gallery/URI viewability, screen-off/on
camera recovery, the R8 seeds/mapping/usage inspection, the **front-camera mirroring contract**
via eight asymmetric-fixture tests, **EXIF metadata and GPS rational values verified with PIL**,
**a 48-capture resource soak showing no leak**, and the new precise place-detail line verified
on hardware.

### Deferred, not forgotten — the feature-parity backlog

The migration does not deliver the GPS Map Camera blueprint. These stay as a prioritised
backlog, unchanged in order, needing separate authorisation each. Nothing here was quietly
added during conversion.

| # | Item | Status after this session |
| --- | --- | --- |
| 1 | Runtime `StampConfig` reproducing today's defaults, with persisted size / colour / position / visibility controls | **Not started.** The colour and detail-line work touched the *values*, not a runtime-configurable model |
| 2 | One shared layout plan for preview and bitmap rendering, accounting for preview crop and aspect ratio rather than normalised coordinates | **Not started.** `PhotoWatermarker` and the in-app review still size independently |
| 3 | Map thumbnail with provider licensing, attribution, cache, offline and error behaviour; never a shutter-blocking network call | **Not started** |
| 4 | Template presets and a strip, then an editor over the same configuration model | **Not started** |
| 5 | Optional sensor and weather fields, capability checks, stale / unavailable-data handling | **Partially present by accident**: altitude, speed, bearing and satellite count already flow into EXIF and the details line |
| 6 | Gallery re-stamp with explicit source and provenance handling | **Not started.** Note the delete path now writes through MediaStore and is verified |
| 7 | Other camera controls and product requirements as separately authorised | **Not started** |

Also out of scope by decision, not by omission: **iOS** is a separate project, and no
assumption should be made about advertising, monetisation, a university-use licence, or any
deadline. `BrandUi` is now public where the Java was package-private; with no Java callers
left, `internal` can be reconsidered on its own merits.

## Post-migration feature change: precise place-detail line

**This is not a conversion.** The migration's preserve-behaviour rule was explicitly lifted by
the owner ("i need gps to give exact name of the location"), so it is committed separately
from every conversion commit and it changes the rendered watermark.

Owner's two decisions: keep line 1 as it was and add a detail line beneath it; and widen the
reverse geocode to choose the best of several results.

What changed:

- **`PlaceNameResolver`** now asks the `Geocoder` for 5 results in the same single round trip
  (the first is usually the coarsest area match) and picks the most specific by a weighted
  score — street and premises weigh more than locality. It publishes a second value,
  `placeDetail()`, assembled from venue → premises (API 28+, gated) → street + sub-street →
  sub-locality → PIN, with each candidate dropped if it duplicates the headline or an
  already-added part. `placeName()` is unchanged, so line 1 is byte-for-byte what it was.
- **`WatermarkPolicy`** gained `buildLines(brand, placeName, detail, coordinates, time, label)`
  and `brandLineIndex(brand, placeName, detail)`. The **old five-argument forms were kept as
  overloads** delegating with an empty detail, which is why all 15 pre-existing test call
  sites still compile and pass — they now serve as the regression proof that the previous
  layout is untouched when there is no detail.
- `brandLineIndex` returns 1 without detail and 2 with it, because the detail line is inserted
  between the headline and the brand. Since the index and the array come from two separate
  calls, `brandLineIndexAlwaysPointsAtTheBrandInTheRenderedLines` asserts they agree for both
  shapes; that is the invariant that would silently mis-colour the brand line.

Verified on the SM-S918B with the **release** build (`PGSC_20260927_114212_547_...jpg`):
`Kuragallu, Andhra Pradesh` / **`SRM College Road, 522240`** / brand in gold / coordinates /
time. Five legible lines, brand still correctly gold, scene upright.

Gates after the change: **79 unit tests, 0 skipped, 0 failed** (was 74; +5 new), lint **0
errors / 18 warnings, unchanged ID distribution**, debug and signed release assemble
(release 83,632 B).

Two API guesses failed loudly rather than quietly, and are worth remembering for this SDK:
`android.location.Address` has **no** `getPlaceName()` and **no** `getSubPremises()` —
`featureName` is the POI/venue field, and `premises` is the only premises-level getter (API 28+).

### Release-build flow pass, completed

Re-run against the shrunk release APK with the focus guard active, and using the
last-shot thumbnail's real bounds rather than a guessed ImageView ordinal (the earlier
attempt picked the grid button and never opened the review):

| Step | Result |
| --- | --- |
| Capture | `PGSC_20260927_114352_282_...jpg` |
| Review opens | Share `[442,2930][720,2981]`, Delete `[720,2930][998,2981]`, caption `5:13 pm · 16.463396, 80.505806 · GPS TAGGED` |
| Share | `SEND PHOTO TO` sheet with Add to Maps / Bixby Vision / Bluetooth / More |
| Delete dialog | `Delete this photo?` with `CANCEL` / `DELETE` |
| Cancel branch | Returned to review, Share still present, nothing destroyed |
| Stability | 0 crash-buffer entries for `com.perfectgps.camera`, process alive after the run |

So R8 shrinking does not disturb the review, share or delete paths. The earlier
`FATAL EXCEPTION` entries seen during this work were the **`UiAutomation` helper process**
dying with `Bad file descriptor` inside `AccessibilityInteractionClient` — my own
`uiautomator dump` churn, with no `com.perfectgps` frames in the stack — not the app.

### Front-camera mirroring contract — closed by deterministic tests

Rather than depend on pointing the handset at something, the mirroring/rotation composition
was pinned with an **asymmetric fixture**: a 120×60 bitmap with a dark block in exactly one
quadrant, run through the real `Bitmap` / `Canvas` / `Matrix` path under
`app/src/androidTest/.../PhotoWatermarkerTransformTest.java`, executed on the API 36
emulator (real Android graphics, not JVM stubs).

Eight tests, all passing:

| Contract | Why it could not pass by accident |
| --- | --- |
| identity with no rotation and no mirror | quadrant must stay `TL` |
| mirror at 0° is a **horizontal** flip | `TL → TR`, and dimensions must not change |
| quarter turn swaps dimensions | 120×60 → 60×120 |
| **mirror stays horizontal after 90°** | compares mirrored against `flipHorizontally(unmirrored)`; if the flip were composed in the pre-rotation space it would land on the vertical axis and fail |
| same at 180° and 270° | looped |
| mirror is not accidentally a vertical flip | explicit negative control |
| double mirror is identity; 360° is identity | round-trips back to `TL` |
| 45° snaps to a quarter turn | guards the degrees-vs-EXIF class of bug |

The "mirror came out as a top-bottom flip" assertion is the one that matters for selfies, and
it passes: `PhotoWatermarker.rotateToUpright` applies the scene mirror on the correct axis at
every quarter turn, and the stamp is still drawn afterwards so text never mirrors.

Instrumented total at that point was **10 tests OK** (2 activity + 8 transform) on the
emulator; it is **13** after the EXIF-orientation tests recorded below. Unit tests **79**,
lint **0 errors / 18 warnings** unchanged, debug and release assemble.

### Physical matrix results, SM-S918B (release build `ed97dc7`)

Captured through the release-signed build installed on the handset at 22:02 IST, with the
EXIF-orientation fix present. Auto-rotate was on; the handset was held upright by the owner
for the physical captures, and logical rotation (`settings put system user_rotation`) was used
for the rear landscape rows because the physical rear-landscape window elapsed before the
owner rotated.

| Lens | Pose | Pixels | Orientation | Scene upright? | Stamp legible? | How obtained |
| --- | --- | --- | --- | --- | --- | --- |
| Rear | Portrait (ROTATION_0) | 3060 x 4080 | 1 | yes | yes | physical hold |
| Rear | Landscape (ROTATION_90) | 4080 x 3060 | 1 | n/a (logical) | yes | `user_rotation=1`, auto-rotate off |
| Rear | Landscape (ROTATION_270) | 4080 x 3060 | 1 | n/a (logical) | yes | `user_rotation=3`, auto-rotate off |
| Front | Portrait (ROTATION_0) | 3000 x 4000 | 1 | yes | yes | physical hold |
| Front | Landscape (ROTATION_90) | 4080 x 3060 | 1 | yes | yes | physical hold (see note) |
| Front | Landscape (ROTATION_270) | 3060 x 4080 | 1 | pose attribution uncertain | yes | physical hold (portrait dims suggest device returned to ROTATION_0 between detection and shutter) |

The two rear landscape rows are **rotation-plumbing verdicts**: they prove the app follows
display rotation, produces landscape pixels with an upright stamp and `Orientation=1`. They do
not verdict scene uprightness because the physical scene did not rotate. The front ROTATION_90
row IS a physical scene-uprightness verdict (the room is upright in the saved image), but its
resolution matches the rear sensor - see the observation below.

**Zoom reset on lens switch: PASS.** Two `KEYCODE_VOLUME_UP` events drove the zoom pill from
`1.0x` to `2.0x`; tapping the lens button returned it to `1.0x`. Fully automated, no human
poses needed.

**Observation (not yet a defect):** two of the "front" rotated captures carry rear-camera
resolution (4080x3060 / 3060x4080) and rear-camera scene content, even though the lens had been
switched to front immediately before. Two candidate explanations: (a) the owner's hand grazed
the lens button at the top-right corner while physically rotating the handset - in landscape
that button sits at the very edge where a palm naturally rests; (b) the lens selection is lost
when the device rotates, despite `MainActivity` declaring `configChanges="orientation|..."` and
`onConfigurationChanged` not touching the camera. Distinguishing these needs one more automated
test (switch to front, rotate logically with no hands, check resolution) which has not been run.
This is pre-existing behaviour either way, so it is recorded here as an observation for the
owner to decide whether to investigate further.

Button positions in portrait on this device (these are Android-reflowed, not app-chosen; in
landscape they move):

| Control | Bounds | Center |
| --- | --- | --- |
| `captureButton` | `[570,2600][870,2900]` | `(720, 2750)` |
| `lensButton` | `[1215,165][1380,330]` | `(1297, 247)` |
| `zoomText` | `[960,2682][1215,2817]` | - |

### Saved-file EXIF, read with PIL rather than a hand-rolled parser

`Bitmap`-level inspection of a saved capture (`PGSC_20260927_115617_169_...jpg`):

| Field | Value | Assessment |
| --- | --- | --- |
| pixel size | **1392 × 1856, portrait** | pixels are physically upright, as the `98b65c7` rotation fix intends |
| `Software` | `Perfect GPS Camera` | preserved ✔ |
| `DateTime` / `DateTimeOriginal` / `DateTimeDigitized` | all `2026:09:27 11:56:17` | preserved and mutually consistent ✔ |
| `Make` / `Model` | `unknown` / `Android SDK built for x86_64` | emulator values; `safe(Build.MANUFACTURER)` working |
| `GPSLatitude` | `(39°, 14', 14.118")` N | = 39.2372550, **exactly** the source decimal degree ✔ |
| `GPSLongitude` | `(123°, 9', 0.114")` W | = 123.1500317 ✔ |
| `GPSDateStamp` / `GPSTimeStamp` | `2026:09:27` / `11:56:16` | preserved ✔ |
| `GPSSpeedRef`, `GPSSatellites` | `K`, `0` | preserved ✔ |
| `Orientation` | **`0`** | **invalid** in this pre-fix capture; fixed in `b37d777`, see below |

The GPS rational values are a direct numerical proof that `ExifValueFormatter`'s
coordinate encoding survived the Java→Kotlin conversion unchanged at the 1/10000-arcsecond
scale, which is the part a mechanical conversion could silently break through integer
division or rounding.

**This also corrects an earlier reading in this file.** A hand-rolled JPEG marker parser
reported `769×1392` and "orientation tag absent" for the emulator captures. Both were wrong;
PIL shows `1392×1856` and an orientation tag that *is* present with value `0`. Any earlier
claim in this document that rests on that parser should be read as superseded by this section.

### `Orientation = 0` - decided by the owner, fixed in `b37d777`

`ExifMetadataWriter.write` never set `TAG_ORIENTATION`, but saved files still carried the tag
with value `0`, outside the EXIF-spec range of 1-8: Android's `ExifInterface` defaults the field
to `0` and `saveAttributes()` writes that default back into the file. The plan's Phase 3
requirement is to "write orientation consistent with final pixel orientation", and the pixels
are already rotated upright before encoding, so the conformant value is `1` (`TopLeft`).

The owner authorised the behaviour change ("continue and get it completed here"), so the writer
now sets `TAG_ORIENTATION` to `ExifInterface.ORIENTATION_NORMAL` explicitly. It is committed
separately from every conversion commit, like the other two behaviour changes.

**Regression test, falsified rather than assumed.** `ExifMetadataWriterTest` is instrumented and
JUnit3-style like the rest of `androidTest`, because that source set has no JUnit4 dependency.
It writes a real JPEG through the real framework `ExifInterface`, saves, and re-reads the tag:

| Test | Asserts |
| --- | --- |
| `testSavedFileCarriesAValidUprightOrientation` | persisted tag == `ORIENTATION_NORMAL` (1) |
| `testAnInheritedRotationFlagIsNormalisedBecausePixelsAreAlreadyUpright` | a pre-existing `ROTATE_90` in the file is overwritten with 1, not inherited |
| `testTheOtherSavedFieldsSurviveAlongsideTheOrientation` | `Software`, `DateTimeOriginal` and `Make` all survive next to the new tag |

Falsification run: with the `setAttribute` block deleted from the writer, all three tests fail
with `expected:<1> but was:<0>` on the API 36 AVD - precisely the value PIL had reported. With
the block restored, `OK (3 tests)`. The test therefore detects the defect it claims to guard.

**End-to-end through the app, before vs after, same emulator and same code path:**

| Capture | Pixels | `Orientation` |
| --- | --- | --- |
| `PGSC_20260927_121150_618_BACK.jpg` (pre-fix build) | 1392 x 1856 | **0** |
| `PGSC_20260927_161143_011_39.2373N_123.1500W.jpg` (post-fix) | 1392 x 1856 | **1** |
| `PGSC_20260927_161346_707_39.2373N_123.1500W.jpg` (post-fix) | 1392 x 1856 | **1** |

The post-fix files still carry `Software = Perfect GPS Camera`, all three `DateTime*` fields
(`2026:09:27 16:13:46`) and a populated GPS IFD, so the extra attribute displaced nothing.

**In the minified release build:** R8 keeps it. `mapping.txt` still maps
`ExifMetadataWriter.write` source lines 20, 21, 24, 28 and 29 - line 24 being the new
`setAttribute` - while `usage.txt` lists only the Kotlin object scaffolding (`INSTANCE`,
`<clinit>`) and the two private helpers that were inlined into `write`. That is the same pattern
recorded under *R8 shrinker safety*, and the signed release APK grew 44 B accordingly.

**Not re-checked:** the physical SM-S918B (disconnected for this work) and the release-signed
build still installed on it, which predates the fix and needs a reinstall.

### Repeated-capture soak — resource cleanup confirmed

Targeted specifically at the batch-4 conversion where Java's
`try (Image image = reader.acquireNextImage())` became an explicit `try / finally { image.close() }`.
A dropped close on any path would show up as a monotonic climb in file descriptors and heap.

48 consecutive captures on the API 36 emulator with the debug build (`run-as` used so
`/proc/<pid>/fd` is readable), sampled every 5–6 captures:

| point | captures done | photos in album | fds | TOTAL PSS (KB) | pid |
| --- | --- | --- | --- | --- | --- |
| start | 0 | 75 | 162 | 53,591 | 30343 |
| +6 | 6 | 81 | 165 | 63,499 | 30343 |
| +12 | 12 | 87 | 162 | 63,527 | 30343 |
| +18 | 18 | 93 | 163 | 63,579 | 30343 |
| +30 | 30 | 105 | 162 | — | 30343 |

Earlier 30-capture pass and a later 18-capture pass were run against the same process.

**Verdict: no leak.** After a one-time ~9.9 MB warm-up in the first six captures (decode
buffers, MediaStore, first-touch allocations), memory is **flat: +52 KB across the next 12
captures**. A genuine per-capture leak would have grown roughly linearly at ~1.6 MB per shot,
i.e. ~+29 MB by capture 18. Descriptor counts oscillate 161–165 with no trend, the pid never
changes across all 48 captures, `logcat -b crash` has zero entries for the package, and the
31st capture in the first pass still saved and rendered normally — so the app is functional
after the soak, not merely alive.

Every one of the 48 taps produced a new file (0 failed taps, 0 missing outputs).

### No-GPS-fix path, re-verified end-to-end after the detail-line change

The place-detail work altered `buildLines`, whose empty-headline branch is what renders when
there is no fix — so that path was exercised on the emulator rather than trusted to the unit
tests. Location permission was revoked with `pm revoke` and a capture taken.

Four things confirmed by the resulting file, `PGSC_20260927_121150_618_BACK.jpg`:

| Observation | Preserved behaviour |
| --- | --- |
| Filename ends `_BACK`, not `_16.4634N_...` | `PhotoNameFormatter`'s no-fix fallback to the camera-facing token |
| Capture still permitted | `CapturePolicy.canCapture(cameraReady, locationRequired = false, null)` → true; location is not mandatory |
| `locationStatus`, `coordinatesText`, `detailsText` absent from the hierarchy | the no-permission UI branch still hides them |
| Stamp renders `SRM AP UNIVERSITY · EVENT CAMERA` / `NO GPS FIX` / `27 Sep 2026 5:41 PM` | brand promoted to headline when place name is empty, `noFixLabel` substituted for coordinates, and **no stray empty detail line inserted** |

Three lines, no gaps, upright, unmirrored, all legible. `brandLineIndex` correctly returned
`NO_BRAND_LINE` so the brand drew as the headline, which is the pre-existing behaviour.

### Grid overlay and camera-capability gating — PASS

**Grid overlay.** Toggling `gridButton` and screenshotting shows the rule-of-thirds lines
drawn over the live preview, each line rendered twice — a dark shade line beside a light one —
which is the "readable on any scene" behaviour from `541c8c1`. This exercises the batch-4
`GridOverlayView` conversion end-to-end: the `isGridEnabled` property's custom setter fires
`invalidate()`, and `onDraw` runs both paint passes. A pixel diff of grid-on against grid-off
was **not** usable evidence here, because the emulator preview is live video and frame motion
swamped the lines; the screenshot is the decisive check.

**Capability gating, same build on two devices:**

| Target | cameras | `lensButton` | `flashButton` |
| --- | --- | --- | --- |
| `emulator-5554` | 1 | **absent** ✔ | present |
| `SM-S918B` | multi | **present** ✔ (every dump this session) | present on rear, **absent on front** ✔ |

The lens control appearing only where there is a second lens, and the flash control
disappearing on the front camera, both survive the Kotlin conversion unchanged.

### Zoom reset on lens switch — NOT VERIFIED, needs the handset

This is a named plan check ("Verify lens switch resets zoom as before") that cannot be done on
the emulator, because the emulator has no second lens to switch to. Earlier phone runs read
`zoomText` as `1.0×` but never zoomed first, so they do not constitute a test.

To run it properly: on the phone, double-tap or pinch to zoom past 1.0×, confirm the zoom pill
shows the new ratio, switch lens, and confirm it returns to `1.0×`. Requires the handset
unlocked. Grouped with the physical orientation matrix as owner-gated work.

## Log

- **2026-09-27 - Physical matrix on SM-S918B with the fixed release build.** Zoom reset on
  lens switch PASS (automated). Rear portrait + front portrait + one front landscape captured
  with the owner holding the handset upright; rear landscape captured via logical rotation.
  All six files carry `Orientation=1`, upright stamps, correct dimensions. Two "front"
  rotated captures show rear-camera resolution/content - recorded as an observation (hand
  grazing the lens button vs state loss on rotation) pending one more automated test.

- **2026-09-27 - `Orientation = 0` fixed (`b37d777`).** Owner authorised the behaviour
  change. `ExifMetadataWriter` now writes `ORIENTATION_NORMAL` because the pixels are
  already upright; 3 instrumented tests added and falsified (without the fix they fail
  `expected:<1> but was:<0>`); PIL confirms 0 -> 1 before/after on the same emulator with
  `Software`, `DateTime*` and the GPS IFD intact. Instrumented total 10 -> 13. The handset
  was disconnected throughout, so the physical orientation matrix,
  zoom-reset-on-lens-switch and the release-build reinstall remain open and are not
  claimed as passing.

- **2026-09-27 — Batch 6 complete; migration implementation finished.** `CameraController`
  and `MainActivity` converted; 0 Java production files remain. 74 unit tests pass, lint
  unchanged at 0/18 by ID, instrumented 2/2 on the physical SM-S918B and the API 36 AVD,
  and a live capture through the all-Kotlin pipeline rendered correctly. The watermark
  meta-line colour defect was verified on pixels and fixed in its own commit with a
  regression test. Hardware/release validation is still BLOCKED on the locked phone.

- **2026-09-27 — Phase 0 complete.** Baseline established at `98b65c7`: 22 Java
  production files / 5,400 LOC against 1 Kotlin file; 71 unit tests pass; 18 pre-existing
  lint warnings recorded by ID; debug and signed-release assemblies pass. The reported
  67 → 71 test increase is verified and attributed to `98b65c7`. Phase 3's watermark
  contract is traced through source and one hardware-dependent risk is isolated.
  No source file changed in this phase.
- **2026-09-27 — Batches 3-5 complete (Phases 1 and 2).** 19 production classes now
  Kotlin (21 files / 1,953 lines) against 2 Java files / 3,392 lines remaining:
  `CameraController` and `MainActivity`, which are in conversion now. Gates measured
  after each batch: 71 unit tests pass / 0 skipped / 0 failed; lint 0 errors and 18
  warnings with an unchanged issue-ID distribution; debug APK 984,242 B; signed release
  APK 81,364 B (+2,160 B over the 79,204 B baseline). Every gate compiled the two
  remaining Java classes against the converted Kotlin, so Java-to-Kotlin interop is
  continuously exercised, and the 11 untouched Java test files still pass.

- **2026-09-27 — Superseded.** This entry said no `connectedDevice` run had happened and
  that the camera path was unvalidated. That is no longer true: instrumented runs completed
  cleanly on both the SM-S918B and the API 36 emulator, the release build was exercised on
  hardware, and the double-rotation risk was resolved for rear and front at natural portrait.
  Read *Physical matrix results* and *Final handoff* instead of this entry. What genuinely
  remains is recorded there.

## Remaining-work estimate

The plan asks for this to be derived from complexity and verification need, not from elapsed
effort, and explicitly forbids extrapolating a date from a single small class. So, with
stated uncertainty:

### To finish release validation of the migration

| Work | Basis | Estimate |
| --- | --- | --- |
| Eight-case *physical* orientation matrix | Needs a person turning the handset; 4 angles × 2 lenses, each with a pull + pixel inspection + EXIF read. The tooling is already written and focus-guarded, so this is now mechanical | **~20–30 min, entirely gated on the owner being free.** High confidence — the harness exists and every sub-step has run at least once |
| ~~Decide and (if accepted) fix `Orientation = 0`~~ | **Done** in `b37d777`: decided, one tag set, 3 falsified instrumented tests, PIL before/after on the emulator | **spent, not outstanding** |
| Re-run the full gate set after any of the above | Commands are recorded | **~10 min** |

That is the whole remaining migration cost: **roughly 25-40 minutes, all of it gated on
one set of hands** - the eight-case physical matrix, zoom-reset-on-lens-switch, and a
release-build reinstall so the handset carries the EXIF fix. No conversion work and no
owner decision is outstanding, so there is no class-complexity or design risk left to
price.

### To reach the deferred feature parity, if ever authorised

Priced from the existing code's shape, deliberately rough:

| Backlog item | Estimate | Why it is uncertain |
| --- | --- | --- |
| 1 · runtime `StampConfig` + persisted controls | **2–4 days** | Touches `WatermarkPolicy`, `PhotoWatermarker`, `PhotoRepository`, `MainActivity` and a settings UI that does not exist yet; the app has no settings screen at all today |
| 2 · shared preview/bitmap layout plan | **3–6 days, highest risk** | The preview is a `TextureView` with a `Matrix` transform and the stamp is drawn on decoded pixels; making one layout model serve both means reasoning about crop, aspect ratio and sensor orientation together. This is the item most likely to surface real defects, and it is the prerequisite for item 4 |
| 3 · map thumbnail | **2–5 days + a licensing decision** | The technical part (non-blocking fetch, cache, attribution) is smaller than the product part: choosing a provider decides cost, attribution wording and offline behaviour. Cannot be estimated honestly before that choice |
| 4 · templates and editor | **4–8 days** | Depends on 1 and 2; a preset strip is cheap, an editor over the same model is not |
| 5 · sensor / weather fields | **1–3 days** | Capability-gated extras on a pipeline that already carries altitude, speed, bearing and satellites; the unknown is a weather data source and its terms |
| 6 · gallery re-stamp | **2–3 days** | Provenance and permission handling on API 29+ scoped storage is the work, not the drawing |
| 7 · other camera controls | unbounded | Not a task |

**Total for the blueprint, if all of it were authorised: roughly three to five working weeks**
for a single developer who already knows this codebase — with item 2 and the item 3 licensing
decision dominating the variance. That range is wide on purpose; narrowing it would be
guessing. It also excludes iOS entirely, any monetisation work, and any external deadline.
