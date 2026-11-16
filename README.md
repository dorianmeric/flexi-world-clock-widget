# Flexi World-Clock Widget

Two flexible Android home-screen widgets in one APK, built from scratch with framework views only
(no Kotlin, no Compose, no AndroidX UI) so that `RemoteViews` inflates cleanly inside the launcher
process.

## Requirements

| Tool | Version |
| --- | --- |
| JDK | 21 (Android Studio's bundled JBR works) |
| Android SDK | build-tools 35.0.0, platform 35 |
| Gradle | 8.7 via the wrapper |
| min SDK | 31 |
| target SDK | 35 |

`local.properties` points at the SDK; `JAVA_HOME` must point at JDK 21, because the Gradle wrapper
and AGP 8.5.2 do not support JDK 23.

## The widgets

### Flexi Clock (2×1)

```
17:23-05            <- clock, includes seconds, format HH:mm'-'ss
Monday 16-Nov       <- date, format EEEE d'-'MMM
```

* Left-aligned, both lines vertically centred in the widget.
* Font family and font size are configurable **separately** for the time and the date.
* Left padding is configurable (0–64 dp).
* Tapping the widget opens its configuration screen.

### Flexi World Clock (flexible width)

```
   London      New York       Tokyo      Sydney
   17:26        12:26         02:26       04:26
   UTC+1        UTC-5         UTC+9       UTC+11    <- optional
```

* Up to 10 timezones side by side, chosen from a searchable list of ~76 cities.
* City name above, `HH:mm` below, optional `UTC+8`-style label underneath.
* Font family and size configurable for the city, the time and the UTC label.
* Configurable gap between clocks; the columns share the remaining space so the row always spans
  the full widget width.
* Configurable width (2–6 launcher cells) plus free horizontal/vertical resizing.

## Build

```powershell
.\scripts\build.ps1            # assemble + zipalign + sign + verify
.\scripts\build.ps1 -Clean     # wipe intermediates first
.\scripts\build.ps1 -Test      # also run the JVM unit tests
.\scripts\build.ps1 -NoSign    # unsigned APK only
```

Outputs:

* `dist/flexi-world-clock-widget.apk` — signed, ready to install
* `dist/flexi-world-clock-widget.apk.idsig` — the APK Signature Scheme v4 sidecar

A bash equivalent is available as `scripts/build.sh` (`--clean`, `--test`).

### Signing: v2, v3 and v4

Gradle produces an **unsigned** release APK on purpose. `scripts/build.ps1` then:

1. runs `zipalign -p -f 4`,
2. runs `apksigner sign` with `--v2-signing-enabled true --v3-signing-enabled true
   --v4-signing-enabled true`, which also writes the `.idsig` file,
3. verifies with `apksigner verify --min-sdk-version 24`,
4. verifies v4 with `scripts/V4Verifier.java`, which uses the `apksigner` library API.

Two details are easy to get wrong:

* `apksigner verify` reports `v2: false` when checked against the manifest's `minSdk` of 31, because
  from API 31 onward only the v3 signature is *used*. Verifying with `--min-sdk-version 24` proves
  that v2 is present and valid.
* `apksigner verify` has **no v4 flag**. v4 can only be checked through the library
  (`ApkVerifier.Builder(...).setV4SignatureFile(idsig)`), which is what `V4Verifier` does. It also
  calls `setMinCheckedPlatformVersion(24)` so the v2 result is meaningful.

> [!NOTE]
> `apksigner verify` reports `Verified using v4 scheme: false` even for a correctly v4-signed APK,
> because the CLI cannot check v4 at all. The `v4: true` line printed by `V4Verifier` directly below
> is the authoritative result.

### Keystore

The release keystore is not committed. Recreate it once — `signing.ps1` documents the exact command:

```powershell
keytool -genkeypair -v -keystore keystore/flexi-release.jks -alias flexi `
        -keyalg RSA -keysize 4096 -validity 10950 -storetype PKCS12 `
        -storepass flexiwidget -keypass flexiwidget `
        -dname "CN=Flexi World-Clock Widget, OU=Widgets, O=Flexi, L=Berlin, C=DE"
```

Override the defaults with `FLEXI_STORE_FILE`, `FLEXI_STORE_PASSWORD`, `FLEXI_KEY_ALIAS` and
`FLEXI_KEY_PASSWORD`.

## Install on a connected device

```powershell
.\scripts\install.ps1              # adb install -r, then list the registered providers
.\scripts\install.ps1 -Launch      # install and open the app
.\scripts\install.ps1 -Uninstall   # remove the previous build first
.\scripts\install.ps1 -Emulator    # boot the bundled AVD headless when no device is attached
.\scripts\install.ps1 -Apk x.apk   # install a specific APK
```

`install.ps1` tries `adb install --v4` first so the device can stream the install from the v4
descriptor. Some `adb`/`pm` builds reject that flag outright; the script detects this, falls back to a
plain `adb install -r`, and the v2/v3 signatures in the APK still apply. A bash equivalent is
available as `scripts/install.sh`.

Debug and release builds use different signing keys, so uninstall the release APK before running
`.\gradlew.bat :app:connectedDebugAndroidTest` against a device that already has it installed.

## Tests

```powershell
.\gradlew.bat :app:testReleaseUnitTest         # 2 test classes, 15 cases
.\gradlew.bat :app:connectedDebugAndroidTest   # 9 on-device cases
```

The JVM tests cover the date/time formatting (`HH:mm'-'ss`, `EEEE d'-'MMM`), the `UTC+8` /
`UTC+5:30` label maths including daylight saving, settings JSON round-trips, clamping of invalid
stored values, and the width arithmetic. They need `org.json:json` on the test classpath because the
`org.json` shipped in `android.jar` is stubbed.

The on-device tests cover the parts that need a real Android runtime:

* the `TypefaceSpan` in the clock format survives a full `RemoteViews` **Parcel round-trip**, which is
  what actually reaches the launcher;
* a custom font measurably changes the laid-out text width, proving the span reaches rendering and is
  not just decoration on the format string;
* columns plus gaps plus padding exactly fill the widget width;
* left padding, font sizes, the UTC label toggle and the per-zone column count all take effect.

## How custom fonts work across processes

`setTypeface` is not a `@RemotableViewMethod`, and a `TypefaceSpan(Typeface)` relies on
process-local storage, so neither survives the trip to the launcher. The one mechanism that does is
`TypefaceSpan(String family)`, which parcels only the family name.

`Fonts.clockFormat()` therefore builds the `TextClock` format string as a `SpannableStringBuilder`
carrying a whole-string `TypefaceSpan`, and assigns it to **both** `format24Hour` and `format12Hour`
so the user's 24-hour setting cannot change the layout. A quoted zero-width space (`'\u200b'`) is
appended to the pattern so the span never ends exactly on the token the framework re-formats on each
tick.

For plain `TextView`s (`zone_city`, `zone_offset`) the same `TypefaceSpan` is applied to the text
itself with `Fonts.spanned()`. Text clocks have to go through the format because they re-render their
content every second.

## Layout constraints that shaped the code

`RemoteViews` can only inflate a fixed set of view classes and their direct descendants, so the
widget layouts use only `FrameLayout`, `LinearLayout`, `TextView` and `TextClock`. There is no
`ConstraintLayout` and no custom view. Columns of the world clock are weighted children of a
horizontal `LinearLayout`, which is what makes them distribute across the widget width without
depending on the width the launcher reports.

## Project layout

```
app/src/main/java/com/flexi/worldclock/
  ClockFormats.java          time/date patterns and UTC offset labels
  Fonts.java                 system font catalogue, spanned text, clock format builder
  ZoneCatalog.java           ~76 city -> timezone entries with search
  WidgetPrefs.java           per-widget settings, persisted as JSON
  ClockWidgetViews.java      RemoteViews builder for the 2x1 clock
  WorldClockWidgetViews.java RemoteViews builder for the world clock
  ClockWidgetProvider.java   app widget provider (clock)
  WorldClockWidgetProvider.java
  PinWidgets.java            "add to home screen" request
  PreviewRenderer.java       applies RemoteViews in-app for the live preview
  Ui.java                    slider/spinner plumbing
  ClockConfigActivity.java   clock configuration screen
  WorldClockConfigActivity.java
  MainActivity.java          landing screen
scripts/
  build.ps1, build.sh        build + zipalign + sign (v2/v3/v4) + verify
  install.ps1, install.sh    adb install (+ --v4 with fallback)
  V4Verifier.java            proves the v4 signature via the apksigner library
```

## Licence

MIT — see [LICENSE](./LICENSE).