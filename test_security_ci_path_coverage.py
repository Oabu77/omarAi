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


class SecuritySmokePathCoverageTests(unittest.TestCase):
    def test_security_relevant_paths_are_covered_for_push_and_pr(self):
        text = WORKFLOW.read_text(encoding="utf-8")
        for path in CRITICAL_PATHS:
            with self.subTest(path=path):
                self.assertGreaterEqual(
                    text.count(f"- {path}"),
                    2,
                    f"{path} must trigger both push and pull_request security smoke runs",
                )

    def test_coverage_regression_runs_in_smoke_job(self):
        text = WORKFLOW.read_text(encoding="utf-8")
        self.assertIn(
            "python -m unittest -v test_security_ci_path_coverage.py",
            text,
        )


if __name__ == "__main__":
    unittest.main()
