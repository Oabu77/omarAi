"""Security regression for the Google Play publisher-readiness trust boundary."""

from pathlib import Path
import unittest


WORKFLOW = Path(".github/workflows/play-publisher-readiness.yml")
FORBIDDEN_SECRET_ALIASES = (
    "GOOGLE_PLAY_SERVICE_ACCOUNT_JSON",
    "PLAY_SERVICE_ACCOUNT_JSON",
    "ANDROID_PUBLISHER_CREDENTIALS",
    "GOOGLE_SERVICE_ACCOUNT_JSON",
    "SERVICE_ACCOUNT_JSON",
    "GOOGLE_PLAY_SERVICE_ACCOUNT",
    "PLAY_STORE_SERVICE_ACCOUNT_JSON",
    "GCP_SERVICE_ACCOUNT_KEY",
    "SUPPLY_JSON_KEY_DATA",
)


class TestPlayWorkflowSecretBoundary(unittest.TestCase):
    def test_readiness_workflow_never_receives_publisher_secret_values(self):
        workflow = WORKFLOW.read_text(encoding="utf-8")
        self.assertNotIn("${{ secrets.", workflow)
        for alias in FORBIDDEN_SECRET_ALIASES:
            self.assertNotIn(alias, workflow)

    def test_readiness_is_explicitly_provider_free(self):
        workflow = WORKFLOW.read_text(encoding="utf-8")
        self.assertIn("credential_material_inspected", workflow)
        self.assertIn("publishing_requires_protected_identity_gate", workflow)
        self.assertIn("PLAY_PUBLISHER_IDENTITY_READY", workflow)


if __name__ == "__main__":
    unittest.main()
