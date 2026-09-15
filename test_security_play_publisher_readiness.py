"""Provider-free regression for the Google Play publisher readiness workflow."""

from pathlib import Path
import unittest


WORKFLOW = Path(__file__).parent / ".github" / "workflows" / "play-publisher-readiness.yml"


class PlayPublisherReadinessSecurityTest(unittest.TestCase):
    def setUp(self) -> None:
        self.text = WORKFLOW.read_text(encoding="utf-8")

    def test_workflow_does_not_reference_github_secrets(self) -> None:
        self.assertNotIn("${{ secrets.", self.text)

    def test_readiness_is_explicitly_provider_free(self) -> None:
        self.assertIn('"publisher_credentials_present": "not_checked"', self.text)
        self.assertIn("provider-free and secret-free", self.text)

    def test_workflow_has_read_only_repository_permission(self) -> None:
        self.assertIn("permissions:\n  contents: read", self.text)


if __name__ == "__main__":
    unittest.main()
