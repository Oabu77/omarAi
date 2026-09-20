"""Provider-free regression for Omar AI security workflow coverage."""

from pathlib import Path
import re
import unittest


WORKFLOW = Path(".github/workflows/runner-smoke.yml")
CRITICAL_RUNTIME_PATHS = (
    "app.py",
    "config.py",
    "live_data.py",
    "requirements.txt",
    "system_prompt.md",
    "test_security_error_redaction.py",
    "test_security_play_publisher_readiness.py",
    "test_security_status_integrity.py",
    "test_security_ci_path_coverage.py",
    ".github/workflows/play-publisher-readiness.yml",
    ".github/workflows/runner-smoke.yml",
)


def _event_paths(text: str, event: str) -> set[str]:
    marker = f"  {event}:\n    paths:\n"
    if marker not in text:
        raise AssertionError(f"missing {event} paths block")

    paths: set[str] = set()
    remainder = text.split(marker, 1)[1]
    for line in remainder.splitlines():
        if line and not line.startswith(" "):
            break
        if re.match(r"^  [^\s].*:\s*$", line):
            break
        item = re.match(r"^\s{6}-\s+(.+?)\s*$", line)
        if item:
            paths.add(item.group(1))
    return paths


def _top_level_permissions_block(text: str) -> str:
    match = re.search(r"(?ms)^permissions:\n(?P<body>(?:  [^\n]+\n)+)\njobs:", text)
    if not match:
        raise AssertionError("missing top-level permissions block before jobs")
    return match.group("body")


class SecurityWorkflowCoverageTests(unittest.TestCase):
    def test_security_relevant_paths_trigger_each_event(self) -> None:
        text = WORKFLOW.read_text(encoding="utf-8")
        for event in ("push", "pull_request"):
            paths = _event_paths(text, event)
            for path in CRITICAL_RUNTIME_PATHS:
                self.assertIn(path, paths, f"{path} must trigger {event} security runs")

    def test_workflow_has_only_read_only_top_level_permissions(self) -> None:
        text = WORKFLOW.read_text(encoding="utf-8")
        self.assertEqual(_top_level_permissions_block(text), "  contents: read\n")
        permission_headers = re.findall(r"(?m)^([ \t]*)permissions:[ \t]*$", text)
        self.assertEqual(permission_headers, [""], "no job/step-level permissions blocks are allowed")

    def test_every_checkout_reference_is_immutable(self) -> None:
        text = WORKFLOW.read_text(encoding="utf-8")
        checkout_refs = re.findall(r"(?m)^[ \t]*uses:[ \t]*actions/checkout@([^\s#]+)", text)
        self.assertTrue(checkout_refs, "workflow must contain actions/checkout")
        for ref in checkout_refs:
            self.assertRegex(ref, r"^[0-9a-f]{40}$", f"checkout ref must be immutable: {ref}")

    def test_declared_dependencies_are_exercised_in_isolation(self) -> None:
        text = WORKFLOW.read_text(encoding="utf-8")
        self.assertIn("python -m venv .venv", text)
        self.assertIn("--only-binary=:all: -r requirements.txt", text)
        self.assertIn(".venv/bin/python -m pip check", text)
        self.assertNotIn("sudo pip", text)


if __name__ == "__main__":
    unittest.main()
