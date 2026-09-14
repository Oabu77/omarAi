"""Provider-free regression for fail-closed Omar AI status reporting."""

from pathlib import Path


SOURCE = (Path(__file__).parent / "app.py").read_text(encoding="utf-8")


def test_unverified_status_boundary() -> None:
    required = [
        "UNVERIFIED — no authoritative component telemetry configured",
        "LOCAL HOST METRICS",
        "Overall Status: UNVERIFIED",
        "Security alerts/anomalies: UNVERIFIED",
        "No external operational or security telemetry was queried in offline mode.",
    ]
    for marker in required:
        assert marker in SOURCE, f"missing fail-closed marker: {marker}"

    forbidden = [
        'lines.append(f"  • {component}: OPERATIONAL")',
        "Overall Status: ALL SYSTEMS OPERATIONAL",
        "All systems nominal. No critical alerts detected.",
        "No anomalous patterns detected.",
        "Transaction Throughput : 4,200 TPS",
        "Active Members         : 14,300",
        "Halal Card Holders     : 9,100",
    ]
    for marker in forbidden:
        assert marker not in SOURCE, f"unverified healthy/synthetic claim returned: {marker}"


if __name__ == "__main__":
    test_unverified_status_boundary()
    print("security status truthfulness regression: PASS")
