#!/usr/bin/env python3
"""Deterministic Omar AI Android smoke test and screenshot capture.

The script drives the real installed UI through Android's accessibility tree. It
does not start or stop an emulator, seed a database directly, edit pixels, or
claim that a Play release is active. Raw device screenshots are retained beside
24-bit RGB copies so every store-candidate image remains auditable.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterable, Sequence

try:
    from PIL import Image
except ImportError as error:  # pragma: no cover - exercised only on an incomplete QA host
    raise SystemExit("Pillow is required to verify and normalize screenshot color mode.") from error


RELEASE_PACKAGE = "com.darcloud.omarai"
DEFAULT_ACTIVITY = "com.darcloud.omarai.MainActivity"
STORE_SIZE = (1080, 1920)
WINDOW_XML_PATH = "/sdcard/omar-ai-window.xml"


class SmokeFailure(RuntimeError):
    """A failed assertion that must stop capture rather than invent evidence."""


@dataclass(frozen=True)
class UiNode:
    text: str
    description: str
    class_name: str
    bounds: tuple[int, int, int, int]
    enabled: bool
    visible: bool

    @property
    def center(self) -> tuple[int, int]:
        left, top, right, bottom = self.bounds
        return ((left + right) // 2, (top + bottom) // 2)

    @property
    def area(self) -> int:
        left, top, right, bottom = self.bounds
        return max(0, right - left) * max(0, bottom - top)


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def parse_bounds(value: str) -> tuple[int, int, int, int]:
    match = re.fullmatch(r"\[(\d+),(\d+)]\[(\d+),(\d+)]", value)
    if not match:
        raise SmokeFailure(f"Invalid UI bounds: {value!r}")
    return tuple(int(part) for part in match.groups())  # type: ignore[return-value]


def parse_ui_nodes(xml_text: str) -> list[UiNode]:
    xml_start = xml_text.find("<?xml")
    if xml_start < 0:
        raise SmokeFailure("uiautomator did not return XML")
    root = ET.fromstring(xml_text[xml_start:])
    nodes: list[UiNode] = []
    for element in root.iter("node"):
        raw_bounds = element.attrib.get("bounds", "")
        if not raw_bounds:
            continue
        nodes.append(
            UiNode(
                text=element.attrib.get("text", "").strip(),
                description=element.attrib.get("content-desc", "").strip(),
                class_name=element.attrib.get("class", ""),
                bounds=parse_bounds(raw_bounds),
                enabled=element.attrib.get("enabled", "true") == "true",
                visible=element.attrib.get("visible-to-user", "true") == "true",
            ),
        )
    return nodes


def parse_effective_wm_size(output: str) -> tuple[int, int]:
    sizes = re.findall(r"(?:Physical|Override) size:\s*(\d+)x(\d+)", output)
    if not sizes:
        raise SmokeFailure(f"Could not parse wm size from: {output!r}")
    width, height = sizes[-1]
    return int(width), int(height)


def parse_version(package_dump: str) -> tuple[int | None, str | None]:
    code_match = re.search(r"\bversionCode=(\d+)", package_dump)
    name_match = re.search(r"\bversionName=([^\s]+)", package_dump)
    return (
        int(code_match.group(1)) if code_match else None,
        name_match.group(1) if name_match else None,
    )


class Adb:
    def __init__(self, executable: str, serial: str) -> None:
        self.prefix = [executable, "-s", serial]

    def run(
        self,
        *arguments: str,
        binary: bool = False,
        check: bool = True,
        timeout: int = 60,
    ) -> str | bytes:
        completed = subprocess.run(
            [*self.prefix, *arguments],
            check=False,
            capture_output=True,
            timeout=timeout,
        )
        if check and completed.returncode != 0:
            stderr = completed.stderr.decode("utf-8", "replace")
            stdout = completed.stdout.decode("utf-8", "replace")
            raise SmokeFailure(
                f"adb command failed ({completed.returncode}): {' '.join(arguments)}\n"
                f"stdout: {stdout}\nstderr: {stderr}",
            )
        if binary:
            return completed.stdout
        return completed.stdout.decode("utf-8", "replace").strip()

    def shell(self, *arguments: str, check: bool = True, timeout: int = 60) -> str:
        return str(self.run("shell", *arguments, check=check, timeout=timeout))


class UiDriver:
    def __init__(self, adb: Adb, xml_dir: Path) -> None:
        self.adb = adb
        self.xml_dir = xml_dir
        self.last_xml = ""
        self.dump_count = 0

    def dump(self, label: str = "window") -> list[UiNode]:
        # Software-only CI emulators can take well over 30 seconds to warm the
        # accessibility bridge on the first hierarchy dump, and may briefly
        # return no root node while the screen is waking.
        xml_text = ""
        last_dump_error = ""
        for attempt in range(1, 5):
            try:
                self.adb.shell("uiautomator", "dump", "--compressed", WINDOW_XML_PATH, timeout=120)
                xml_text = str(
                    self.adb.run("exec-out", "cat", WINDOW_XML_PATH, timeout=120),
                )
                if "<?xml" in xml_text:
                    break
                last_dump_error = f"attempt {attempt} returned no XML: {xml_text[:200]!r}"
            except (SmokeFailure, subprocess.TimeoutExpired) as error:
                last_dump_error = f"attempt {attempt} failed: {error}"
            self.adb.shell("input", "keyevent", "224", check=False)
            time.sleep(10)
        else:
            raise SmokeFailure(f"uiautomator did not return XML after retries: {last_dump_error}")
        nodes = parse_ui_nodes(xml_text)
        self.last_xml = xml_text[xml_text.find("<?xml") :]
        self.dump_count += 1
        safe_label = re.sub(r"[^a-z0-9-]+", "-", label.lower()).strip("-") or "window"
        (self.xml_dir / f"{self.dump_count:03d}-{safe_label}.xml").write_text(
            self.last_xml,
            encoding="utf-8",
        )
        return nodes

    @staticmethod
    def matching(
        nodes: Iterable[UiNode],
        value: str,
        *,
        exact: bool = True,
        fields: Sequence[str] = ("text", "description"),
    ) -> list[UiNode]:
        def field_matches(candidate: str) -> bool:
            return candidate == value if exact else value in candidate

        return sorted(
            (
                node
                for node in nodes
                if node.visible
                and any(field_matches(getattr(node, field)) for field in fields)
            ),
            key=lambda node: (node.enabled, node.area),
            reverse=True,
        )

    def wait_for(
        self,
        value: str,
        *,
        exact: bool = True,
        fields: Sequence[str] = ("text", "description"),
        timeout: float = 20.0,
    ) -> UiNode:
        deadline = time.monotonic() + timeout
        last_visible: list[str] = []
        while time.monotonic() < deadline:
            nodes = self.dump(f"wait-{value}")
            matches = self.matching(nodes, value, exact=exact, fields=fields)
            if matches:
                return matches[0]
            last_visible = sorted(
                {item for node in nodes for item in (node.text, node.description) if item},
            )
            time.sleep(0.4)
        raise SmokeFailure(
            f"Timed out waiting for {value!r}. Visible UI: {last_visible[:50]}",
        )

    def wait_for_any(self, values: Sequence[str], *, timeout: float = 20.0) -> tuple[str, UiNode]:
        deadline = time.monotonic() + timeout
        last_visible: list[str] = []
        while time.monotonic() < deadline:
            nodes = self.dump("wait-any")
            for value in values:
                matches = self.matching(nodes, value)
                if matches:
                    return value, matches[0]
            last_visible = sorted(
                {item for node in nodes for item in (node.text, node.description) if item},
            )
            time.sleep(0.4)
        raise SmokeFailure(
            f"Timed out waiting for one of {values!r}. Visible UI: {last_visible[:50]}",
        )

    def tap(self, value: str, *, exact: bool = True, fields: Sequence[str] = ("text", "description")) -> None:
        node = self.wait_for(value, exact=exact, fields=fields)
        if not node.enabled:
            raise SmokeFailure(f"Refusing to tap disabled UI node: {value!r}")
        x, y = node.center
        self.adb.shell("input", "tap", str(x), str(y))
        time.sleep(0.5)

    def tap_any(self, values: Sequence[str]) -> str:
        value, node = self.wait_for_any(values)
        if not node.enabled:
            raise SmokeFailure(f"Refusing to tap disabled UI node: {value!r}")
        x, y = node.center
        self.adb.shell("input", "tap", str(x), str(y))
        time.sleep(0.5)
        return value

    def tap_edit_text(self) -> None:
        nodes = self.dump("edit-text")
        candidates = sorted(
            (
                node
                for node in nodes
                if node.visible and node.enabled and node.class_name.endswith("EditText")
            ),
            key=lambda node: node.area,
            reverse=True,
        )
        if not candidates:
            raise SmokeFailure("No visible enabled request input was found")
        x, y = candidates[0].center
        self.adb.shell("input", "tap", str(x), str(y))
        time.sleep(0.4)

    def assert_absent(self, forbidden: Sequence[str]) -> None:
        nodes = self.dump("forbidden-claim-check")
        visible = {item for node in nodes for item in (node.text, node.description) if item}
        found = sorted(value for value in forbidden if value in visible)
        if found:
            raise SmokeFailure(f"Forbidden visible claim/control found: {found}")


def ensure_clean_directory(path: Path) -> None:
    path.mkdir(parents=True, exist_ok=True)
    existing = [entry for entry in path.iterdir() if entry.name not in {".gitkeep"}]
    if existing:
        raise SmokeFailure(
            f"Evidence directory is not empty: {path}. Use a new directory to preserve provenance.",
        )


def normalize_screenshot(raw: bytes, raw_path: Path, final_path: Path) -> dict[str, object]:
    raw_path.write_bytes(raw)
    with Image.open(raw_path) as image:
        image.load()
        if image.size != STORE_SIZE:
            raise SmokeFailure(f"Screenshot {raw_path.name} is {image.size}, expected {STORE_SIZE}")
        image.convert("RGB").save(final_path, format="PNG", optimize=False)
    with Image.open(final_path) as verified:
        if verified.size != STORE_SIZE or verified.mode != "RGB":
            raise SmokeFailure(
                f"Normalized screenshot {final_path.name} is {verified.size}/{verified.mode}, expected {STORE_SIZE}/RGB",
            )
    return {
        "rawFile": str(raw_path),
        "rawSha256": sha256_file(raw_path),
        "file": str(final_path),
        "sha256": sha256_file(final_path),
        "width": STORE_SIZE[0],
        "height": STORE_SIZE[1],
        "mode": "RGB",
        "pixelOperation": "RGBA/RGB to 24-bit RGB only; no crop, resize, overlay, retouch, or compositing",
    }


def capture_screen(
    adb: Adb,
    driver: UiDriver,
    raw_dir: Path,
    screenshot_dir: Path,
    sequence: int,
    slug: str,
    required_text: Sequence[str],
) -> dict[str, object]:
    for value in required_text:
        driver.wait_for(value)
    xml_hash = sha256_bytes(driver.last_xml.encode("utf-8"))
    raw = adb.run("exec-out", "screencap", "-p", binary=True, timeout=30)
    assert isinstance(raw, bytes)
    prefix = f"{sequence:02d}-{slug}"
    evidence = normalize_screenshot(
        raw,
        raw_dir / f"{prefix}-raw.png",
        screenshot_dir / f"{prefix}.png",
    )
    evidence.update({"screen": slug, "requiredVisibleText": list(required_text), "windowXmlSha256": xml_hash})
    return evidence


def capture_screen_without_accessibility(
    adb: Adb,
    raw_dir: Path,
    screenshot_dir: Path,
    sequence: int,
    slug: str,
) -> dict[str, object]:
    """Capture the installed UI when the emulator accessibility bridge is unavailable.

    This fallback never edits pixels beyond converting the device screencap to
    24-bit RGB. The resulting images require independent visual review because
    no accessibility-tree text assertion is available in this mode.
    """
    raw = adb.run("exec-out", "screencap", "-p", binary=True, timeout=120)
    assert isinstance(raw, bytes)
    prefix = f"{sequence:02d}-{slug}"
    evidence = normalize_screenshot(
        raw,
        raw_dir / f"{prefix}-raw.png",
        screenshot_dir / f"{prefix}.png",
    )
    evidence.update(
        {
            "screen": slug,
            "requiredVisibleText": [],
            "windowXmlSha256": None,
            "visualReviewRequired": True,
        },
    )
    return evidence


def capture_release_by_coordinates(
    args: argparse.Namespace,
    adb: Adb,
    raw_dir: Path,
    screenshot_dir: Path,
    package_dump: str,
    installer_output: str,
    wm_size_output: str,
    wm_density_output: str,
    effective_size: tuple[int, int],
) -> int:
    """Bounded fallback for the exact non-debuggable release on a 1080x1920 phone.

    Some software-only Android emulators render Compose correctly while their
    uiautomator accessibility bridge never returns a root node. Coordinates are
    tied to the exact STORE_SIZE already verified by the caller. Every image is
    retained raw and must pass a separate visual review before store upload.
    """

    def tap(x: int, y: int, settle_seconds: int = 12) -> None:
        adb.shell("input", "tap", str(x), str(y), timeout=30)
        time.sleep(settle_seconds)

    # A clean app-data reset always opens the four-page onboarding flow. The
    # first page has one full-width action; subsequent pages use the right-hand
    # Continue/Open action. Long settles account for unaccelerated TCG hosts.
    print("[coordinate] waiting for the first Compose frame", flush=True)
    time.sleep(20)
    tap(540, 1810)
    tap(805, 1810)
    tap(805, 1810)
    tap(805, 1810, settle_seconds=20)
    print("[coordinate] onboarding taps completed", flush=True)

    screenshots: list[dict[str, object]] = []
    screenshots.append(capture_screen_without_accessibility(adb, raw_dir, screenshot_dir, 1, "home"))
    print("[coordinate] captured 01-home", flush=True)

    # Bottom navigation centers for Home, Business, Tasks, and Settings.
    tap(405, 1810)
    screenshots.append(capture_screen_without_accessibility(adb, raw_dir, screenshot_dir, 5, "business"))
    print("[coordinate] captured 05-business", flush=True)
    tap(675, 1810)
    screenshots.append(capture_screen_without_accessibility(adb, raw_dir, screenshot_dir, 2, "command-center"))
    print("[coordinate] captured 02-command-center", flush=True)
    tap(945, 1810)
    screenshots.append(capture_screen_without_accessibility(adb, raw_dir, screenshot_dir, 6, "settings"))
    print("[coordinate] captured 06-settings", flush=True)

    # Open Privacy & data directly from Settings. Do not label a coordinate-
    # driven detail screen unless navigation can be verified independently.
    tap(540, 1350, settle_seconds=18)
    screenshots.append(capture_screen_without_accessibility(adb, raw_dir, screenshot_dir, 3, "privacy-data"))
    print("[coordinate] captured 03-privacy-data", flush=True)

    installed_path, installed_base_apk_sha256 = collect_installed_apk_hash(adb, args.package)
    version_code, version_name = parse_version(package_dump)
    metadata = {
        "schema": "omar-ai-emulator-coordinate-capture-v1",
        "capturedAtUtc": datetime.now(timezone.utc).isoformat(),
        "result": "CAPTURED_REQUIRES_VISUAL_REVIEW",
        "mode": args.mode,
        "playConsoleStateVerified": False,
        "storeCandidate": False,
        "serial": args.serial,
        "model": adb.shell("getprop", "ro.product.model"),
        "apiLevel": int(adb.shell("getprop", "ro.build.version.sdk")),
        "wmSize": wm_size_output,
        "wmDensity": wm_density_output,
        "effectiveSize": list(effective_size),
        "package": args.package,
        "versionCode": version_code,
        "versionName": version_name,
        "installerEvidence": installer_output,
        "installedBaseApkPath": installed_path,
        "installedBaseApkSha256": installed_base_apk_sha256,
        "inputApkSha256": sha256_file(args.apk) if args.apk else None,
        "appDataReset": args.reset_data,
        "accessibilityAvailable": False,
        "externalActionsClaimedOrSeeded": False,
        "screenshots": screenshots,
    }
    (args.output / "evidence.json").write_text(
        json.dumps(metadata, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    print(json.dumps({"result": metadata["result"], "evidence": str(args.output / "evidence.json")}, indent=2))
    return 0


def complete_onboarding_if_needed(driver: UiDriver) -> bool:
    first, _ = driver.wait_for_any(("Meet Omar AI", "What do you want Omar AI to do?"), timeout=30)
    if first != "Meet Omar AI":
        return False
    pages = (
        ("Continue", "What matters to you?"),
        ("Continue", "Connect only what you need"),
        ("Continue", "You stay in control"),
        ("Open Omar AI", "What do you want Omar AI to do?"),
    )
    for action, destination in pages:
        driver.tap(action)
        driver.wait_for(destination)
    return True


def seed_real_local_plan(adb: Adb, driver: UiDriver) -> None:
    driver.tap_edit_text()
    adb.shell(
        "input",
        "text",
        "Prepare%sa%slocal%splan%sfor%sDemo%sLandscaping",
    )
    adb.shell("input", "keyevent", "4")
    time.sleep(0.4)
    driver.tap_any(("Create local plan", "Route request"))
    time.sleep(1.2)


def collect_installed_apk_hash(adb: Adb, package_name: str) -> tuple[str | None, str | None]:
    paths = str(adb.shell("pm", "path", package_name)).splitlines()
    base_entry = next((line for line in paths if line.startswith("package:") and line.endswith("/base.apk")), None)
    if base_entry is None:
        base_entry = next((line for line in paths if line.startswith("package:")), None)
    if base_entry is None:
        return None, None
    remote_path = base_entry.removeprefix("package:")
    apk_bytes = adb.run("exec-out", "cat", remote_path, binary=True, timeout=120)
    assert isinstance(apk_bytes, bytes)
    if not apk_bytes:
        return remote_path, None
    return remote_path, sha256_bytes(apk_bytes)


def validate_play_mode(args: argparse.Namespace, adb: Adb, installer_output: str, package_dump: str) -> None:
    if args.apk:
        raise SmokeFailure("--mode play-track must use the Play-installed app; do not pass --apk")
    if args.package != RELEASE_PACKAGE:
        raise SmokeFailure(f"Play-track capture requires package {RELEASE_PACKAGE}")
    if "installer=com.android.vending" not in installer_output:
        raise SmokeFailure(f"Play installer evidence missing: {installer_output!r}")
    run_as = adb.shell("run-as", args.package, "id", check=False)
    if run_as:
        raise SmokeFailure("Play-track screenshot candidate is debuggable (run-as succeeded)")
    if not args.expected_version_code or not args.expected_version_name:
        raise SmokeFailure("Play-track capture requires --expected-version-code and --expected-version-name")
    if not re.fullmatch(r"[0-9a-fA-F]{64}", args.expected_aab_sha256 or ""):
        raise SmokeFailure("Play-track capture requires a 64-character --expected-aab-sha256")
    if not re.fullmatch(r"[0-9a-fA-F]{40}", args.git_commit or ""):
        raise SmokeFailure("Play-track capture requires the reviewed 40-character --git-commit")
    actual_code, actual_name = parse_version(package_dump)
    if actual_code != args.expected_version_code or actual_name != args.expected_version_name:
        raise SmokeFailure(
            f"Installed version {actual_code}/{actual_name} does not match expected "
            f"{args.expected_version_code}/{args.expected_version_name}",
        )


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", required=True, help="Absolute path to adb")
    parser.add_argument("--serial", required=True, help="Explicit emulator/device serial")
    parser.add_argument("--mode", required=True, choices=("debug-smoke", "play-track"))
    parser.add_argument("--package", default="com.darcloud.omarai.debug")
    parser.add_argument("--activity", default=DEFAULT_ACTIVITY)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--apk", type=Path, help="Optional debug APK to install; prohibited in play-track mode")
    parser.add_argument("--reset-data", action="store_true", help="Clear the selected app before the smoke flow")
    parser.add_argument("--expected-version-code", type=int)
    parser.add_argument("--expected-version-name")
    parser.add_argument("--expected-aab-sha256")
    parser.add_argument("--git-commit")
    parser.add_argument("--play-track", default="closed")
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    ensure_clean_directory(args.output)
    raw_dir = args.output / "raw"
    screenshot_dir = args.output / "screenshots"
    xml_dir = args.output / "window-xml"
    for directory in (raw_dir, screenshot_dir, xml_dir):
        directory.mkdir()

    adb = Adb(args.adb, args.serial)
    state = str(adb.run("get-state"))
    if state != "device":
        raise SmokeFailure(f"Device {args.serial} is not ready: {state!r}")
    if adb.shell("getprop", "sys.boot_completed") != "1":
        raise SmokeFailure(f"Device {args.serial} has not completed boot")

    wm_size_output = adb.shell("wm", "size")
    effective_size = parse_effective_wm_size(wm_size_output)
    if effective_size != STORE_SIZE:
        raise SmokeFailure(f"Device size is {effective_size}; expected exact store capture size {STORE_SIZE}")
    wm_density_output = adb.shell("wm", "density")

    if args.apk:
        if args.mode != "debug-smoke":
            raise SmokeFailure("Only debug-smoke mode may install an APK")
        if not args.apk.is_file():
            raise SmokeFailure(f"APK does not exist: {args.apk}")
        adb.run("install", "-r", "-t", str(args.apk), timeout=180)

    if args.reset_data:
        clear_result = adb.shell("pm", "clear", args.package)
        if clear_result != "Success":
            raise SmokeFailure(f"Could not clear {args.package}: {clear_result}")

    package_dump = adb.shell("dumpsys", "package", args.package, timeout=90)
    if f"Package [{args.package}]" not in package_dump:
        raise SmokeFailure(f"Package {args.package} is not installed")
    installer_output = adb.shell("cmd", "package", "list", "packages", "-i", args.package)
    if args.mode == "play-track":
        validate_play_mode(args, adb, installer_output, package_dump)

    (args.output / "package-dump.txt").write_text(package_dump, encoding="utf-8")
    adb.shell("am", "force-stop", args.package)
    start_output = adb.shell(
        "am",
        "start",
        "-W",
        "-n",
        f"{args.package}/{args.activity}",
        timeout=60,
    )
    if "Error:" in start_output:
        raise SmokeFailure(f"App launch failed: {start_output}")

    # Let the first Compose frame and accessibility bridge settle on
    # software-rendered emulators before requesting the initial hierarchy.
    time.sleep(10)
    if args.mode == "debug-smoke" and args.package == RELEASE_PACKAGE and args.apk is None:
        return capture_release_by_coordinates(
            args,
            adb,
            raw_dir,
            screenshot_dir,
            package_dump,
            installer_output,
            wm_size_output,
            wm_density_output,
            effective_size,
        )
    driver = UiDriver(adb, xml_dir)
    onboarding_completed = complete_onboarding_if_needed(driver)
    driver.wait_for("What do you want Omar AI to do?")
    driver.wait_for("Local preview · external services disconnected")
    screenshots: list[dict[str, object]] = []
    screenshots.append(
        capture_screen(
            adb,
            driver,
            raw_dir,
            screenshot_dir,
            1,
            "home",
            ("What do you want Omar AI to do?", "Local preview · external services disconnected"),
        ),
    )

    seed_real_local_plan(adb, driver)
    driver.tap("Tasks")
    driver.wait_for("Command Center")
    driver.wait_for("Status: PLANNED")
    driver.wait_for("Omar AI service is not connected; no analysis or external action occurred.")
    screenshots.append(
        capture_screen(
            adb,
            driver,
            raw_dir,
            screenshot_dir,
            2,
            "command-center",
            ("Command Center", "Status: PLANNED", "Omar AI service is not connected; no analysis or external action occurred."),
        ),
    )

    driver.tap("Settings")
    driver.wait_for("Settings")
    driver.tap("Integration status")
    driver.wait_for("Integrations")
    driver.wait_for("Omar AI service")
    driver.wait_for("DISCONNECTED")
    screenshots.append(
        capture_screen(
            adb,
            driver,
            raw_dir,
            screenshot_dir,
            3,
            "integration-status",
            ("Integrations", "Omar AI service", "DISCONNECTED"),
        ),
    )

    driver.tap("Back", fields=("description",))
    driver.wait_for("Settings")
    driver.tap("Privacy & data")
    driver.wait_for("Privacy & data")
    driver.wait_for("Export local data")
    driver.wait_for("Delete local data")
    screenshots.append(
        capture_screen(
            adb,
            driver,
            raw_dir,
            screenshot_dir,
            4,
            "privacy-data",
            ("Privacy & data", "Export local data", "Delete local data"),
        ),
    )

    driver.tap("Back", fields=("description",))
    driver.wait_for("Settings")
    driver.tap("Plans")
    driver.wait_for("Paid plans unavailable")
    driver.wait_for("Purchase restore unavailable")
    driver.assert_absent(("Buy", "Subscribe", "Manage subscriptions", "Purchase successful", "Purchase restored"))

    installed_path, installed_base_apk_sha256 = collect_installed_apk_hash(adb, args.package)
    version_code, version_name = parse_version(package_dump)
    metadata = {
        "schema": "omar-ai-emulator-smoke-v1",
        "capturedAtUtc": datetime.now(timezone.utc).isoformat(),
        "result": "PASS",
        "mode": args.mode,
        "playConsoleStateVerified": False,
        "playConsoleStateNote": "This device workflow cannot prove that a Play release is Active; verify the Console separately.",
        "storeCandidate": args.mode == "play-track",
        "serial": args.serial,
        "model": adb.shell("getprop", "ro.product.model"),
        "apiLevel": int(adb.shell("getprop", "ro.build.version.sdk")),
        "wmSize": wm_size_output,
        "wmDensity": wm_density_output,
        "effectiveSize": list(effective_size),
        "package": args.package,
        "versionCode": version_code,
        "versionName": version_name,
        "installerEvidence": installer_output,
        "installedBaseApkPath": installed_path,
        "installedBaseApkSha256": installed_base_apk_sha256,
        "inputApkSha256": sha256_file(args.apk) if args.apk else None,
        "expectedAabSha256": args.expected_aab_sha256,
        "gitCommit": args.git_commit,
        "declaredPlayTrack": args.play_track if args.mode == "play-track" else None,
        "appDataReset": args.reset_data,
        "onboardingCompletedByWorkflow": onboarding_completed,
        "fictionalLocalInput": "Prepare a local plan for Demo Landscaping",
        "externalActionsClaimedOrSeeded": False,
        "screenshots": screenshots,
    }
    (args.output / "evidence.json").write_text(
        json.dumps(metadata, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    print(json.dumps({"result": "PASS", "evidence": str(args.output / "evidence.json")}, indent=2))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except SmokeFailure as error:
        print(f"FAIL: {error}", file=sys.stderr)
        raise SystemExit(1)
