from pathlib import Path
import unittest


WORKFLOW = Path(".github/workflows/runner-smoke.yml")
CRITICAL_PATHS = (
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


def event_paths(text, event_name):
    lines = text.splitlines()
    event_header = f"  {event_name}:"

    try:
        event_start = lines.index(event_header)
    except ValueError as exc:
        raise AssertionError(f"missing {event_name} event") from exc

    event_end = len(lines)
    for index in range(event_start + 1, len(lines)):
        line = lines[index]
        if line and not line.startswith(" "):
            event_end = index
            break
        if line.startswith("  ") and not line.startswith("    ") and line.strip().endswith(":"):
            event_end = index
            break

    event_lines = lines[event_start + 1:event_end]
    try:
        paths_start = event_lines.index("    paths:")
    except ValueError as exc:
        raise AssertionError(f"missing {event_name}.paths") from exc

    paths = set()
    for line in event_lines[paths_start + 1:]:
        if not line.strip():
            continue
        if not line.startswith("      "):
            break
        stripped = line.strip()
        if stripped.startswith("- "):
            paths.add(stripped[2:].strip("'\""))
    return paths


class SecuritySmokePathCoverageTests(unittest.TestCase):
    def test_security_relevant_paths_are_covered_for_push_and_pr(self):
        text = WORKFLOW.read_text(encoding="utf-8")
        for event_name in ("push", "pull_request"):
            paths = event_paths(text, event_name)
            for path in CRITICAL_PATHS:
                with self.subTest(event=event_name, path=path):
                    self.assertIn(
                        path,
                        paths,
                        f"{path} must trigger the {event_name} security smoke workflow",
                    )

    def test_coverage_regression_runs_in_smoke_job(self):
        text = WORKFLOW.read_text(encoding="utf-8")
        self.assertIn(
            "python -m unittest -v test_security_ci_path_coverage.py",
            text,
        )


if __name__ == "__main__":
    unittest.main()
