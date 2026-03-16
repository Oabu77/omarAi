"""Tests for OMAR AI Founder Command Center."""

from __future__ import annotations

import importlib
import sys
import types
import unittest
from pathlib import Path
from unittest.mock import MagicMock, patch


# ---------------------------------------------------------------------------
# Ensure the project root is on sys.path so `import config` and `import app`
# work regardless of how the test is invoked.
# ---------------------------------------------------------------------------
ROOT = Path(__file__).parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))


class TestConfig(unittest.TestCase):
    """Smoke tests for config.py constants."""

    def test_operating_modes_present(self):
        import config
        for mode in ("strategy", "operations", "financial", "security", "advisor"):
            self.assertIn(mode, config.OPERATING_MODES)
            self.assertIsInstance(config.OPERATING_MODES[mode], str)
            self.assertTrue(config.OPERATING_MODES[mode])

    def test_default_mode_is_valid(self):
        import config
        self.assertIn(config.DEFAULT_MODE, config.OPERATING_MODES)

    def test_ecosystem_components_not_empty(self):
        import config
        self.assertTrue(len(config.ECOSYSTEM_COMPONENTS) > 0)

    def test_banner_and_help_are_strings(self):
        import config
        self.assertIsInstance(config.BANNER, str)
        self.assertIsInstance(config.HELP_TEXT, str)


class TestSystemPromptFile(unittest.TestCase):
    """Verify the system_prompt.md file exists and contains key content."""

    def setUp(self):
        self.prompt_path = ROOT / "system_prompt.md"

    def test_file_exists(self):
        self.assertTrue(self.prompt_path.exists(), "system_prompt.md must exist")

    def test_contains_omar_ai_identity(self):
        content = self.prompt_path.read_text(encoding="utf-8")
        self.assertIn("OMAR AI", content)

    def test_contains_ecosystem_components(self):
        content = self.prompt_path.read_text(encoding="utf-8")
        for term in ("QuranChain", "Dar Al-Nas", "DarCloud", "MeshTalk OS", "Halal Card"):
            self.assertIn(term, content, f"'{term}' should be in system_prompt.md")

    def test_contains_operating_modes(self):
        content = self.prompt_path.read_text(encoding="utf-8")
        for mode in ("Strategy Mode", "Operations Mode", "Financial Insight Mode",
                     "Security Awareness Mode", "Advisor Mode"):
            self.assertIn(mode, content, f"'{mode}' should be documented")

    def test_contains_founder_name(self):
        content = self.prompt_path.read_text(encoding="utf-8")
        self.assertIn("Omar Mohammad Abunadi", content)


class TestOmarAIOfflineMode(unittest.TestCase):
    """Test OmarAI behaviour when no API key is set (offline mode)."""

    def _make_ai(self):
        # Reload app module and force offline mode by clearing the client.
        import app
        importlib.reload(app)
        ai = app.OmarAI()
        ai._client = None
        return ai

    def test_default_mode(self):
        import config
        ai = self._make_ai()
        self.assertEqual(ai.mode, config.DEFAULT_MODE)

    def test_switch_mode_valid(self):
        ai = self._make_ai()
        result = ai.switch_mode("strategy")
        self.assertIn("STRATEGY", result)
        self.assertEqual(ai.mode, "strategy")

    def test_switch_mode_invalid(self):
        ai = self._make_ai()
        result = ai.switch_mode("invalid_mode")
        self.assertIn("Unknown mode", result)

    def test_offline_ecosystem_status(self):
        ai = self._make_ai()
        response = ai.chat("show ecosystem status")
        self.assertIn("ECOSYSTEM STATUS", response)
        self.assertIn("OPERATIONAL", response)

    def test_offline_infrastructure_health(self):
        ai = self._make_ai()
        response = ai.chat("show infrastructure health")
        self.assertIn("INFRASTRUCTURE HEALTH", response)
        self.assertIn("HEALTHY", response)

    def test_offline_network_performance(self):
        ai = self._make_ai()
        response = ai.chat("show network performance")
        self.assertIn("NETWORK PERFORMANCE", response)

    def test_offline_service_adoption(self):
        ai = self._make_ai()
        response = ai.chat("show service adoption metrics")
        self.assertIn("SERVICE ADOPTION", response)

    def test_offline_operational_report(self):
        ai = self._make_ai()
        response = ai.chat("generate operational report")
        self.assertIn("OPERATIONAL REPORT", response)

    def test_offline_strategic_analysis(self):
        ai = self._make_ai()
        response = ai.chat("generate strategic analysis")
        self.assertIn("STRATEGIC ANALYSIS", response)

    def test_offline_unknown_command(self):
        ai = self._make_ai()
        response = ai.chat("something completely unknown")
        self.assertIn("OFFLINE MODE", response)

    def test_history_accumulates(self):
        ai = self._make_ai()
        ai.chat("show ecosystem status")
        ai.chat("show network performance")
        self.assertEqual(len(ai._history), 4)  # 2 user + 2 assistant

    def test_reset_history(self):
        ai = self._make_ai()
        ai.chat("show ecosystem status")
        ai.reset_history()
        self.assertEqual(len(ai._history), 0)


class TestOmarAIOnlineMode(unittest.TestCase):
    """Test OmarAI behaviour when an API client is configured (mocked)."""

    def _make_ai_with_mock_client(self, response_text: str):
        import app
        importlib.reload(app)
        ai = app.OmarAI()

        mock_client = MagicMock()
        mock_choice = MagicMock()
        mock_choice.message.content = response_text
        mock_client.chat.completions.create.return_value = MagicMock(
            choices=[mock_choice]
        )
        ai._client = mock_client
        return ai, mock_client

    def test_api_call_uses_model_from_config(self):
        import config
        ai, mock_client = self._make_ai_with_mock_client("Strategic response.")
        ai.chat("What is our expansion strategy?")
        call_args, call_kwargs = mock_client.chat.completions.create.call_args
        model_used = call_kwargs.get("model") if call_kwargs else (call_args[0] if call_args else None)
        self.assertEqual(model_used, config.MODEL)

    def test_api_response_returned_and_stored(self):
        ai, _ = self._make_ai_with_mock_client("Expansion recommendation text.")
        response = ai.chat("How do we expand QuranChain?")
        self.assertEqual(response, "Expansion recommendation text.")
        # History should contain the assistant message
        assistant_msgs = [m for m in ai._history if m["role"] == "assistant"]
        self.assertTrue(any("Expansion recommendation text." in m["content"]
                            for m in assistant_msgs))

    def test_api_error_returns_error_string(self):
        ai, mock_client = self._make_ai_with_mock_client("")
        mock_client.chat.completions.create.side_effect = RuntimeError("timeout")
        response = ai.chat("show ecosystem status")
        self.assertIn("OMAR AI ERROR", response)
        self.assertIn("timeout", response)


class TestHandleBuiltIn(unittest.TestCase):
    """Test the _handle_built_in CLI helper."""

    def setUp(self):
        import app
        importlib.reload(app)
        self.app = app
        self.ai = app.OmarAI()
        self.ai._client = None

    def test_help_command(self):
        result = self.app._handle_built_in(self.ai, "help")
        self.assertIsNotNone(result)
        self.assertIn("commands", result.lower())

    def test_switch_mode_command(self):
        result = self.app._handle_built_in(self.ai, "switch mode strategy")
        self.assertIsNotNone(result)
        self.assertIn("STRATEGY", result)

    def test_unknown_command_returns_none(self):
        result = self.app._handle_built_in(self.ai, "something else")
        self.assertIsNone(result)

    def test_exit_calls_sys_exit(self):
        with self.assertRaises(SystemExit):
            self.app._handle_built_in(self.ai, "exit")

    def test_quit_calls_sys_exit(self):
        with self.assertRaises(SystemExit):
            self.app._handle_built_in(self.ai, "quit")


if __name__ == "__main__":
    unittest.main()
