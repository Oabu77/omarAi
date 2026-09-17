"""Provider-free regression for security workflow change-detection coverage."""

from pathlib import Path
import unittest


WORKFLOW = Path(__file__).parent / ".github" / "workflows" / "runner-smoke.yml"


class SecurityCICoverageTest(unittest.TestCase):
    def setUp(self) -> None:
        self.text = WORKFLOW.read_text(encoding="utf-8")

    def test_security_relevant_runtime_files_trigger_smoke_workflow(self) -> None:
        critical_paths = (
            "app.py",
            "config.py",
            "live_data.py",
            "requirements.txt",
            "system_prompt.md",
        )
        for path in critical_paths:
            self.assertGreaterEqual(
                self.text.count(f"      - {path}\n"),
                2,
                msg=f"{path} must be covered by both push and pull_request filters",
            )

    def test_coverage_regression_triggers_itself(self) -> None:
        self.assertGreaterEqual(
            self.text.count("      - test_security_ci_coverage.py\n"),
            2,
        )
        self.assertIn(
            "python -m unittest -v test_security_ci_coverage.py",
            self.text,
        )

    def test_workflow_remains_read_only_and_immutably_pinned(self) -> None:
        self.assertIn("permissions:\n  contents: read", self.text)
        self.assertIn(
            "actions/checkout@11d5960a326750d5838078e36cf38b85af677262",
            self.text,
        )


if __name__ == "__main__":
    unittest.main()
