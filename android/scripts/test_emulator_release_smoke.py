import tempfile
import unittest
from pathlib import Path

from PIL import Image

from emulator_release_smoke import (
    STORE_SIZE,
    SmokeFailure,
    UiDriver,
    ensure_clean_directory,
    normalize_screenshot,
    parse_bounds,
    parse_effective_wm_size,
    parse_ui_nodes,
    parse_version,
)


SAMPLE_XML = """<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>
<hierarchy rotation="0">
  <node text="" content-desc="" class="android.view.View" enabled="true" visible-to-user="true" bounds="[0,0][1080,1920]">
    <node text="Settings" content-desc="" class="android.widget.TextView" enabled="true" visible-to-user="true" bounds="[810,1800][1080,1920]" />
    <node text="" content-desc="Back" class="android.view.View" enabled="true" visible-to-user="true" bounds="[0,0][100,100]" />
    <node text="Hidden" content-desc="" class="android.widget.TextView" enabled="true" visible-to-user="false" bounds="[0,0][20,20]" />
  </node>
</hierarchy>"""


class EmulatorReleaseSmokeHelpersTest(unittest.TestCase):
    def test_effective_wm_size_prefers_override(self) -> None:
        self.assertEqual(
            (1080, 1920),
            parse_effective_wm_size("Physical size: 1080x2400\nOverride size: 1080x1920"),
        )
        self.assertEqual((1080, 1920), parse_effective_wm_size("Physical size: 1080x1920"))

    def test_invalid_wm_size_fails_closed(self) -> None:
        with self.assertRaises(SmokeFailure):
            parse_effective_wm_size("size unavailable")

    def test_ui_parser_supports_visible_text_and_descriptions(self) -> None:
        nodes = parse_ui_nodes(SAMPLE_XML)
        self.assertEqual((0, 0, 1080, 1920), parse_bounds("[0,0][1080,1920]"))
        self.assertEqual("Settings", UiDriver.matching(nodes, "Settings")[0].text)
        self.assertEqual("Back", UiDriver.matching(nodes, "Back", fields=("description",))[0].description)
        self.assertEqual([], UiDriver.matching(nodes, "Hidden"))

    def test_ui_matching_is_exact_by_default(self) -> None:
        nodes = parse_ui_nodes(SAMPLE_XML)
        self.assertEqual([], UiDriver.matching(nodes, "Setting"))
        self.assertEqual(1, len(UiDriver.matching(nodes, "Setting", exact=False)))

    def test_version_parser_returns_declared_values(self) -> None:
        package_dump = "versionCode=7 minSdk=26 targetSdk=36\nversionName=0.7.0"
        self.assertEqual((7, "0.7.0"), parse_version(package_dump))
        self.assertEqual((None, None), parse_version("no version here"))

    def test_evidence_directory_must_be_new(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "evidence"
            ensure_clean_directory(path)
            (path / "existing.txt").write_text("preserve me", encoding="utf-8")
            with self.assertRaises(SmokeFailure):
                ensure_clean_directory(path)

    def test_normalization_preserves_dimensions_and_removes_alpha_only(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "source.png"
            raw = root / "raw.png"
            final = root / "final.png"
            Image.new("RGBA", STORE_SIZE, (12, 34, 56, 255)).save(source)
            result = normalize_screenshot(source.read_bytes(), raw, final)
            with Image.open(final) as image:
                self.assertEqual(STORE_SIZE, image.size)
                self.assertEqual("RGB", image.mode)
                self.assertEqual((12, 34, 56), image.getpixel((10, 10)))
            self.assertEqual("RGB", result["mode"])


if __name__ == "__main__":
    unittest.main()
