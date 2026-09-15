"""Security regression for issue #13: status must fail closed when telemetry is absent."""

from __future__ import annotations

import unittest
from pathlib import Path
from unittest.mock import patch

import app
import config


class TestStatusIntegrity(unittest.TestCase):
    def _offline_ai(self) -> app.OmarAI:
        ai = app.OmarAI()
        ai._client = None
        return ai

    @patch("app.live_data.collect")
    def test_status_summary_marks_ecosystem_unverified(self, collect):
        collect.return_value = {
            "timestamp": "2026-09-15 00:00:00 UTC",
            "psutil_available": False,
        }
        result = self._offline_ai().status_summary()
        for component in config.ECOSYSTEM_COMPONENTS:
            self.assertIn(f"{component}: UNVERIFIED", result)
        self.assertNotIn("ALL SYSTEMS OPERATIONAL", result)
        self.assertIn("Overall Status: UNVERIFIED", result)

    def test_offline_ecosystem_status_does_not_claim_health(self):
        result = self._offline_ai().chat("show ecosystem status")
        self.assertIn("UNVERIFIED", result)
        self.assertNotIn("All systems nominal", result)
        self.assertNotIn("No critical alerts detected", result)

    @patch("app.live_data.collect")
    def test_missing_psutil_does_not_fabricate_infrastructure_metrics(self, collect):
        collect.return_value = {"psutil_available": False}
        result = self._offline_ai().chat("show infrastructure health")
        self.assertIn("Local-host metrics: UNAVAILABLE", result)
        self.assertIn("External infrastructure status: UNVERIFIED", result)
        self.assertNotIn("99.9 %", result)
        self.assertNotIn("12 ms", result)
        self.assertNotIn("No anomalies detected", result)

    def test_offline_network_and_adoption_are_unverified(self):
        network = self._offline_ai().chat("show network performance")
        adoption = self._offline_ai().chat("show service adoption metrics")
        self.assertIn("UNVERIFIED", network)
        self.assertIn("UNVERIFIED", adoption)
        self.assertNotIn("4,200 TPS", network)
        self.assertNotIn("1,140", network)
        self.assertNotIn("14,300", adoption)
        self.assertNotIn("Growth trend: POSITIVE", adoption)

    def test_offline_operational_report_never_claims_no_anomalies(self):
        result = self._offline_ai().chat("generate operational report")
        self.assertIn("[Security]", result)
        self.assertIn("UNVERIFIED", result)
        self.assertNotIn("No anomalous patterns detected", result)
        self.assertNotIn("All nodes operational", result)

    def test_source_has_no_known_synthetic_health_phrases(self):
        source = Path(app.__file__).read_text(encoding="utf-8")
        forbidden = (
            "ALL SYSTEMS OPERATIONAL",
            "All systems nominal. No critical alerts detected.",
            "No anomalous patterns detected.",
            "Transaction Throughput : 4,200 TPS",
            "Active Members         : 14,300",
        )
        for phrase in forbidden:
            self.assertNotIn(phrase, source)


if __name__ == "__main__":
    unittest.main()
