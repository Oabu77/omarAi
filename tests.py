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

    def test_status_command(self):
        result = self.app._handle_built_in(self.ai, "status")
        self.assertIsNotNone(result)
        self.assertIn("SYSTEM STATUS", result)
        self.assertIn("OPERATIONAL", result)

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


class TestStatusCommand(unittest.TestCase):
    """Tests for the `status` built-in command."""

    def _make_ai(self, connected: bool = False):
        import app
        importlib.reload(app)
        ai = app.OmarAI()
        ai._client = MagicMock() if connected else None
        return ai

    def test_status_summary_contains_header(self):
        import app
        importlib.reload(app)
        ai = self._make_ai()
        result = ai.status_summary()
        self.assertIn("OMAR AI — SYSTEM STATUS", result)

    def test_status_summary_shows_mode(self):
        import app
        importlib.reload(app)
        ai = self._make_ai()
        result = ai.status_summary()
        self.assertIn("OPERATIONS MODE", result)

    def test_status_summary_lists_all_components(self):
        import config
        import app
        importlib.reload(app)
        ai = self._make_ai()
        result = ai.status_summary()
        for component in config.ECOSYSTEM_COMPONENTS:
            self.assertIn(component, result)

    def test_status_summary_shows_operational(self):
        import app
        importlib.reload(app)
        ai = self._make_ai()
        result = ai.status_summary()
        self.assertIn("ALL SYSTEMS OPERATIONAL", result)

    def test_status_summary_shows_offline_when_no_client(self):
        import app
        importlib.reload(app)
        ai = self._make_ai(connected=False)
        result = ai.status_summary()
        self.assertIn("OFFLINE", result)

    def test_status_summary_shows_connected_when_client_set(self):
        import app
        importlib.reload(app)
        ai = self._make_ai(connected=True)
        result = ai.status_summary()
        self.assertIn("CONNECTED", result)

    def test_status_reflects_switched_mode(self):
        import app
        importlib.reload(app)
        ai = self._make_ai()
        ai.switch_mode("strategy")
        result = ai.status_summary()
        self.assertIn("STRATEGY MODE", result)

    def test_status_built_in_handled(self):
        import app
        importlib.reload(app)
        ai = self._make_ai()
        result = app._handle_built_in(ai, "status")
        self.assertIsNotNone(result)
        self.assertIn("SYSTEM STATUS", result)

    def test_status_case_insensitive(self):
        import app
        importlib.reload(app)
        ai = self._make_ai()
        result = app._handle_built_in(ai, "STATUS")
        self.assertIsNotNone(result)
        self.assertIn("SYSTEM STATUS", result)

    def test_help_text_documents_status(self):
        import config
        self.assertIn("status", config.HELP_TEXT)

    def test_system_prompt_documents_status(self):
        content = (ROOT / "system_prompt.md").read_text(encoding="utf-8")
        self.assertIn("`status`", content)

    def test_status_shows_live_metrics_section(self):
        """When psutil is available the output must include the live metrics header."""
        import app
        importlib.reload(app)
        ai = self._make_ai()
        result = ai.status_summary()
        self.assertIn("INFRASTRUCTURE METRICS (Live)", result)

    def test_status_shows_snapshot_timestamp(self):
        """A UTC timestamp line must appear in the output."""
        import app
        importlib.reload(app)
        ai = self._make_ai()
        result = ai.status_summary()
        self.assertIn("UTC", result)


class TestLiveData(unittest.TestCase):
    """Unit tests for the live_data module."""

    def _fake_metrics(self) -> dict:
        """Build the psutil fake objects used across several tests."""
        return {
            "cpu_percent": 23.5,
            "cpu_count": 4,
            "virtual_memory": types.SimpleNamespace(
                percent=55.0, used=4 * 1024 ** 3, total=8 * 1024 ** 3
            ),
            "disk_usage": types.SimpleNamespace(
                percent=42.0, used=100 * 1024 ** 3, total=500 * 1024 ** 3
            ),
            "boot_time": 0.0,  # will be mocked
            "net_io_counters": types.SimpleNamespace(
                bytes_sent=1_000_000, bytes_recv=5_000_000
            ),
            "pids": [1, 2, 3, 4, 5],
        }

    def _patch_psutil(self, fake: dict, uptime_seconds: float = 90061.0):
        """Return a context manager that patches all psutil calls in live_data."""
        import live_data as ld
        # boot_time returns epoch seconds such that time.time()-boot_time = uptime_seconds
        fixed_now = 1_000_000.0
        boot_ts = fixed_now - uptime_seconds

        patches = [
            patch.object(ld, "_psutil", create=True),
            patch("time.time", return_value=fixed_now),
        ]
        return patches, boot_ts, fake

    @patch("time.time")
    def test_collect_keys_always_present(self, mock_time):
        import live_data as ld
        mock_time.return_value = 1_000_000.0
        with patch.object(ld, "_PSUTIL_AVAILABLE", False):
            result = ld.collect()
        expected_keys = {
            "timestamp", "psutil_available",
            "uptime_str", "cpu_percent", "cpu_count",
            "memory_percent", "memory_used_gb", "memory_total_gb",
            "disk_percent", "disk_used_gb", "disk_total_gb",
            "net_bytes_sent", "net_bytes_recv", "process_count",
        }
        self.assertEqual(expected_keys, set(result.keys()))

    @patch("time.time")
    def test_collect_without_psutil_returns_none_values(self, mock_time):
        import live_data as ld
        mock_time.return_value = 1_000_000.0
        with patch.object(ld, "_PSUTIL_AVAILABLE", False):
            result = ld.collect()
        self.assertFalse(result["psutil_available"])
        for key in ("cpu_percent", "memory_percent", "disk_percent",
                    "uptime_str", "net_bytes_sent", "net_bytes_recv",
                    "process_count"):
            self.assertIsNone(result[key], f"{key} should be None without psutil")

    @patch("time.time")
    def test_collect_with_psutil_populates_metrics(self, mock_time):
        import live_data as ld
        fixed_now = 1_000_000.0
        mock_time.return_value = fixed_now
        fake = self._fake_metrics()

        mock_ps = MagicMock()
        mock_ps.cpu_percent.return_value = fake["cpu_percent"]
        mock_ps.cpu_count.return_value = fake["cpu_count"]
        mock_ps.virtual_memory.return_value = fake["virtual_memory"]
        mock_ps.disk_usage.return_value = fake["disk_usage"]
        mock_ps.boot_time.return_value = fixed_now - 90061.0
        mock_ps.net_io_counters.return_value = fake["net_io_counters"]
        mock_ps.pids.return_value = fake["pids"]

        with patch.object(ld, "_psutil", mock_ps), \
             patch.object(ld, "_PSUTIL_AVAILABLE", True):
            result = ld.collect()

        self.assertTrue(result["psutil_available"])
        self.assertAlmostEqual(result["cpu_percent"], 23.5)
        self.assertEqual(result["cpu_count"], 4)
        self.assertAlmostEqual(result["memory_percent"], 55.0)
        self.assertAlmostEqual(result["memory_total_gb"], 8.0)
        self.assertAlmostEqual(result["disk_percent"], 42.0)
        self.assertAlmostEqual(result["disk_total_gb"], 500.0)
        self.assertEqual(result["net_bytes_sent"], 1_000_000)
        self.assertEqual(result["net_bytes_recv"], 5_000_000)
        self.assertEqual(result["process_count"], 5)

    @patch("time.time")
    def test_collect_uptime_format_days(self, mock_time):
        import live_data as ld
        fixed_now = 1_000_000.0
        mock_time.return_value = fixed_now

        mock_ps = MagicMock()
        mock_ps.cpu_percent.return_value = 0.0
        mock_ps.cpu_count.return_value = 1
        mock_ps.virtual_memory.return_value = types.SimpleNamespace(
            percent=0.0, used=0, total=1
        )
        mock_ps.disk_usage.return_value = types.SimpleNamespace(
            percent=0.0, used=0, total=1
        )
        mock_ps.boot_time.return_value = fixed_now - (2 * 86400 + 3 * 3600 + 15 * 60)
        mock_ps.net_io_counters.return_value = types.SimpleNamespace(
            bytes_sent=0, bytes_recv=0
        )
        mock_ps.pids.return_value = []

        with patch.object(ld, "_psutil", mock_ps), \
             patch.object(ld, "_PSUTIL_AVAILABLE", True):
            result = ld.collect()
        self.assertEqual(result["uptime_str"], "2d 3h 15m")

    def test_fmt_uptime_hours_only(self):
        import live_data as ld
        self.assertEqual(ld._fmt_uptime(3661), "1h 1m")

    def test_fmt_uptime_with_days(self):
        import live_data as ld
        self.assertEqual(ld._fmt_uptime(86400 + 3600 + 60), "1d 1h 1m")

    def test_fmt_bytes_kb(self):
        import live_data as ld
        self.assertEqual(ld.fmt_bytes(512), "512.0 B")
        self.assertEqual(ld.fmt_bytes(2048), "2.0 KB")

    def test_fmt_bytes_mb(self):
        import live_data as ld
        self.assertEqual(ld.fmt_bytes(5 * 1024 * 1024), "5.0 MB")

    def test_fmt_bytes_gb(self):
        import live_data as ld
        self.assertEqual(ld.fmt_bytes(3 * 1024 ** 3), "3.0 GB")

    @patch("time.time")
    def test_status_summary_includes_live_cpu(self, mock_time):
        """status_summary() must embed actual CPU% from live_data."""
        import app
        import live_data as ld
        importlib.reload(app)
        fixed_now = 1_000_000.0
        mock_time.return_value = fixed_now

        mock_ps = MagicMock()
        mock_ps.cpu_percent.return_value = 77.7
        mock_ps.cpu_count.return_value = 8
        mock_ps.virtual_memory.return_value = types.SimpleNamespace(
            percent=50.0, used=4 * 1024 ** 3, total=8 * 1024 ** 3
        )
        mock_ps.disk_usage.return_value = types.SimpleNamespace(
            percent=30.0, used=60 * 1024 ** 3, total=200 * 1024 ** 3
        )
        mock_ps.boot_time.return_value = fixed_now - 3600
        mock_ps.net_io_counters.return_value = types.SimpleNamespace(
            bytes_sent=0, bytes_recv=0
        )
        mock_ps.pids.return_value = list(range(100))

        with patch.object(ld, "_psutil", mock_ps), \
             patch.object(ld, "_PSUTIL_AVAILABLE", True):
            ai = app.OmarAI()
            ai._client = None
            result = ai.status_summary()

        self.assertIn("77.7", result)
        self.assertIn("8 cores", result)


if __name__ == "__main__":
    unittest.main()
