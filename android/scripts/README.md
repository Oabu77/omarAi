# Deterministic Android device smoke and screenshot evidence

`emulator_release_smoke.py` drives the real installed Omar AI UI through the
Android accessibility tree. It never starts/stops an emulator, writes directly
to Room, edits task state, adds an overlay, or claims a Play release is Active.

The workflow exercises:

1. all four onboarding pages from a clean install;
2. Home and its disconnected release disclosure;
3. one real local Planned task created through the visible request field;
4. Command Center state and disconnected error copy;
5. Integration status;
6. local export/delete controls; and
7. the informational Plans page, including the disabled restore state and
   absence of Buy, Subscribe, subscription-management, or purchase-success UI.

It captures four actual app screens: Home, Command Center, Integration status,
and Privacy & data. Each device PNG is preserved in `raw/`. A second PNG is
encoded as 24-bit RGB without resizing, cropping, overlays, retouching, or
compositing. `evidence.json`, the package dump, and every accessibility-tree XML
record provenance and assertions.

## Helper tests

```bash
cd android/scripts
python3 -m unittest -v test_emulator_release_smoke.py
```

## Debug QA (not a Play Store screenshot candidate)

Use a new evidence directory every time. The script refuses to overwrite one.

```bash
python3 android/scripts/emulator_release_smoke.py \
  --adb /absolute/path/to/adb \
  --serial emulator-5570 \
  --mode debug-smoke \
  --package com.darcloud.omarai.debug \
  --apk /absolute/path/to/app-debug.apk \
  --reset-data \
  --output /absolute/path/to/new-debug-evidence
```

Debug output proves only a sideloaded QA workflow. It must not be uploaded as a
signed Play-track screenshot set.

## Signed Play-track candidate

First install the exact candidate from its closed-test Play opt-in page. Do not
pass an APK. The workflow fails unless the installed package is the release ID,
the installer is `com.android.vending`, `run-as` does not identify the app as
debuggable, the version matches, and the display is exactly 1,080 × 1,920.

```bash
python3 android/scripts/emulator_release_smoke.py \
  --adb /absolute/path/to/adb \
  --serial DEVICE_SERIAL \
  --mode play-track \
  --package com.darcloud.omarai \
  --expected-version-code 1 \
  --expected-version-name 0.1.0 \
  --expected-aab-sha256 FINAL_64_CHARACTER_AAB_SHA256 \
  --git-commit FINAL_40_CHARACTER_GIT_COMMIT \
  --play-track closed \
  --reset-data \
  --output /absolute/path/to/new-play-evidence
```

The AAB hash and commit are provenance inputs, not facts inferred from the
installed APK. A Play installer check also does not prove the Console status;
the operator must separately record that the release is Active, the opt-in URL,
and a successful eligible-tester install.
