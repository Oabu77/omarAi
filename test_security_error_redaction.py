"""Security regression tests for user-visible provider errors."""

from __future__ import annotations

import importlib
import unittest
from unittest.mock import MagicMock


class TestProviderErrorRedaction(unittest.TestCase):
    def test_provider_exception_details_are_not_reflected(self):
        import app

        importlib.reload(app)
        ai = app.OmarAI()
        mock_client = MagicMock()
        marker = "SYNTHETIC_PROVIDER_DETAIL_DO_NOT_REFLECT_92381"
        mock_client.chat.completions.create.side_effect = RuntimeError(marker)
        ai._client = mock_client

        response = ai.chat("synthetic security regression")

        self.assertIn("OMAR AI ERROR", response)
        self.assertNotIn(marker, response)


if __name__ == "__main__":
    unittest.main()
