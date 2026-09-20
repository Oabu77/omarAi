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


class SecurityWorkflowCoverageTests(unittest.TestCase):
    def test_security_relevant_paths_trigger_push_and_pull_request(self) -> None:
        text = WORKFLOW.read_text(encoding="utf-8")
        for path in CRITICAL_RUNTIME_PATHS:
            self.assertGreaterEqual(
                text.count(f"- {path}"),
                2,
                f"{path} must trigger both push and pull_request security runs",
            )

    def test_workflow_stays_read_only_and_checkout_is_immutable(self) -> None:
        text = WORKFLOW.read_text(encoding="utf-8")
        self.assertIn("permissions:\n  contents: read", text)
        checkout = re.search(r"uses:\s*actions/checkout@([0-9a-f]{40})", text)
        self.assertIsNotNone(checkout, "actions/checkout must be pinned to a full commit SHA")


if __name__ == "__main__":
    unittest.main()
