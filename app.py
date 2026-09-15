"""OMAR AI — Founder Command Center application."""

from __future__ import annotations

import sys
import textwrap
from pathlib import Path
from typing import Optional

try:
    import openai
except ImportError:  # pragma: no cover
    openai = None  # type: ignore[assignment]

import config
import live_data


_PROMPT_FILE = Path(__file__).parent / "system_prompt.md"


def _load_system_prompt() -> str:
    """Return the OMAR AI system prompt from *system_prompt.md*."""
    return _PROMPT_FILE.read_text(encoding="utf-8")


class OmarAI:
    """Stateful chat session for the OMAR AI Founder Command Center."""

    def __init__(self, mode: str = config.DEFAULT_MODE) -> None:
        self._system_prompt: str = _load_system_prompt()
        self._mode: str = mode
        self._history: list[dict[str, str]] = []
        self._client: Optional["openai.OpenAI"] = None

        if openai is not None and config.OPENAI_API_KEY:
            self._client = openai.OpenAI(api_key=config.OPENAI_API_KEY)

    @property
    def mode(self) -> str:
        return self._mode

    def switch_mode(self, mode: str) -> str:
        """Switch the operating mode. Returns a confirmation message."""
        mode = mode.strip().lower()
        if mode not in config.OPERATING_MODES:
            valid = " | ".join(config.OPERATING_MODES)
            return f"Unknown mode '{mode}'. Valid modes: {valid}"
        self._mode = mode
        return f"Operating mode switched to: {mode.upper()} MODE"

    def chat(self, user_input: str) -> str:
        """Send *user_input* to OMAR AI and return the response."""
        mode_directive = config.OPERATING_MODES.get(self._mode, "")
        system_content = f"{self._system_prompt}\n\n{mode_directive}"

        messages: list[dict[str, str]] = [
            {"role": "system", "content": system_content},
            *self._history,
            {"role": "user", "content": user_input},
        ]

        if self._client is None:
            response_text = self._offline_response(user_input)
        else:
            response_text = self._api_call(messages)

        self._history.append({"role": "user", "content": user_input})
        self._history.append({"role": "assistant", "content": response_text})
        return response_text

    def reset_history(self) -> None:
        """Clear conversation history."""
        self._history.clear()

    def status_summary(self) -> str:
        """Return verified local-host metrics and fail closed on external status."""
        metrics = live_data.collect()

        lines = [
            "OMAR AI — SYSTEM STATUS",
            "=" * 50,
            f"  Snapshot       : {metrics['timestamp']}",
            f"  Operating Mode : {self._mode.upper()} MODE",
            f"  AI Backend     : {'CONNECTED' if self._client is not None else 'OFFLINE (no API key)'}",
            "",
            "ECOSYSTEM COMPONENTS (Authoritative telemetry not queried)",
            "-" * 50,
        ]
        for component in config.ECOSYSTEM_COMPONENTS:
            lines.append(f"  • {component}: UNVERIFIED")

        lines += ["", "LOCAL HOST METRICS (Live)", "-" * 50]

        if metrics["psutil_available"]:
            lines += [
                f"  System Uptime      : {metrics['uptime_str']}",
                f"  CPU Usage          : {metrics['cpu_percent']:.1f} %  ({metrics['cpu_count']} cores)",
                f"  Memory Usage       : {metrics['memory_percent']:.1f} %  ({metrics['memory_used_gb']:.1f} / {metrics['memory_total_gb']:.1f} GB)",
                f"  Disk Usage         : {metrics['disk_percent']:.1f} %  ({metrics['disk_used_gb']:.1f} / {metrics['disk_total_gb']:.1f} GB)",
                f"  Net Bytes Sent     : {live_data.fmt_bytes(metrics['net_bytes_sent'])}",
                f"  Net Bytes Received : {live_data.fmt_bytes(metrics['net_bytes_recv'])}",
                f"  Active Processes   : {metrics['process_count']}",
            ]
        else:
            lines.append("  UNAVAILABLE — install psutil for local-host metrics")

        lines += [
            "",
            "Overall Status: UNVERIFIED — no authoritative ecosystem health source was queried.",
        ]
        return "\n".join(lines)

    def _api_call(self, messages: list[dict[str, str]]) -> str:
        """Call the OpenAI chat completions endpoint."""
        try:
            completion = self._client.chat.completions.create(  # type: ignore[union-attr]
                model=config.MODEL,
                messages=messages,  # type: ignore[arg-type]
                max_tokens=config.MAX_TOKENS,
                temperature=config.TEMPERATURE,
            )
            return completion.choices[0].message.content or ""
        except Exception:  # noqa: BLE001
            return "[OMAR AI ERROR] API call failed: provider timeout or service error. Please retry."

    @staticmethod
    def _offline_response(user_input: str) -> str:
        """Return an offline response that never fabricates operational telemetry."""
        cmd = user_input.strip().lower()

        if "ecosystem status" in cmd:
            lines = [
                "ECOSYSTEM STATUS OVERVIEW",
                "=" * 40,
                "Authoritative service telemetry is unavailable in offline mode.",
            ]
            for component in config.ECOSYSTEM_COMPONENTS:
                lines.append(f"  • {component}: UNVERIFIED")
            lines.append("\nNo claim about outages, alerts, or anomalies is made without a live telemetry source.")
            return "\n".join(lines)

        if "infrastructure health" in cmd:
            m = live_data.collect()
            if m["psutil_available"]:
                return "\n".join([
                    "INFRASTRUCTURE HEALTH SUMMARY",
                    "=" * 30,
                    "Scope: LOCAL HOST ONLY",
                    f"  System Uptime    : {m['uptime_str']}",
                    f"  CPU Usage        : {m['cpu_percent']:.1f} %  ({m['cpu_count']} cores)",
                    f"  Memory Usage     : {m['memory_percent']:.1f} %  ({m['memory_used_gb']:.1f} / {m['memory_total_gb']:.1f} GB)",
                    f"  Disk Usage       : {m['disk_percent']:.1f} %  ({m['disk_used_gb']:.1f} / {m['disk_total_gb']:.1f} GB)",
                    f"  Net Bytes Sent   : {live_data.fmt_bytes(m['net_bytes_sent'])}",
                    f"  Net Bytes Recv   : {live_data.fmt_bytes(m['net_bytes_recv'])}",
                    f"  Active Processes : {m['process_count']}",
                    "",
                    f"Snapshot: {m['timestamp']}",
                    "External infrastructure status: UNVERIFIED",
                ])
            return textwrap.dedent("""\
                INFRASTRUCTURE HEALTH SUMMARY
                ==============================
                Local-host metrics: UNAVAILABLE
                External infrastructure status: UNVERIFIED
                No latency, uptime, packet-loss, capacity, alert, or anomaly claim
                can be made without an authoritative telemetry source.
            """)

        if "network performance" in cmd:
            return textwrap.dedent("""\
                NETWORK PERFORMANCE REPORT
                ===========================
                Status: UNVERIFIED
                No live QuranChain, bridge, mesh, session, latency, finality, or
                throughput telemetry is connected in offline mode.
            """)

        if "service adoption" in cmd:
            return textwrap.dedent("""\
                SERVICE ADOPTION METRICS
                =========================
                Status: UNVERIFIED
                No live member, registration, merchant, cardholder, or active-user
                analytics source is connected in offline mode.
            """)

        if "operational report" in cmd:
            return textwrap.dedent("""\
                OPERATIONAL REPORT
                ===================
                [Infrastructure]
                  UNVERIFIED — no authoritative infrastructure health source queried.

                [Network]
                  UNVERIFIED — no live throughput, routing, or availability source queried.

                [Services]
                  UNVERIFIED — no live adoption, payment, or service-health source queried.

                [Security]
                  UNVERIFIED — no alert, incident, or anomaly source queried.

                [Recommendations]
                  • Connect authoritative telemetry before making operational claims.
                  • Keep local-host metrics explicitly scoped to this machine.
                  • Treat missing telemetry as unknown, not healthy.

                Report generated by OMAR AI Founder Command Center in offline mode.
            """)

        if "strategic analysis" in cmd:
            return textwrap.dedent("""\
                STRATEGIC ANALYSIS
                ====================
                Planning guidance only — current operating conditions are UNVERIFIED.

                [Opportunity 1 — Validator Network Expansion]
                  Evaluate validator capacity and geographic coverage after verifying
                  current utilization, reliability, and decentralization metrics.

                [Opportunity 2 — Dar Al-Nas Merchant Partnerships]
                  Evaluate merchant onboarding opportunities using verified demand,
                  compliance, and transaction data.

                [Opportunity 3 — MeshTalk OS Licensing]
                  Evaluate institutional licensing where security, support, and
                  deployment requirements are documented.

                [Risk — Centralized Dependency]
                  Audit third-party dependencies and maintain tested redundancy plans
                  for critical infrastructure services.
            """)

        return (
            "[OMAR AI — OFFLINE MODE]\n"
            "No OPENAI_API_KEY detected. Set the environment variable to enable "
            "full AI responses.\n\n"
            "Recognized commands:\n"
            "  show ecosystem status\n"
            "  show infrastructure health\n"
            "  show network performance\n"
            "  show service adoption metrics\n"
            "  generate operational report\n"
            "  generate strategic analysis\n"
            "  switch mode <mode>\n"
            "  help | exit"
        )


def _handle_built_in(ai: OmarAI, user_input: str) -> Optional[str]:
    """Handle CLI built-in commands. Returns output string or None to pass through."""
    stripped = user_input.strip().lower()

    if stripped in ("exit", "quit"):
        print("\nFOUNDER COMMAND CENTER — Session terminated.")
        sys.exit(0)

    if stripped == "status":
        return ai.status_summary()

    if stripped == "help":
        return config.HELP_TEXT

    if stripped.startswith("switch mode "):
        mode = stripped[len("switch mode "):].strip()
        return ai.switch_mode(mode)

    return None


def main() -> None:
    """Run the OMAR AI interactive command center CLI."""
    print(config.BANNER)
    print(f"Operating Mode : {config.DEFAULT_MODE.upper()} MODE")
    print(f"Model          : {config.MODEL}")
    if not config.OPENAI_API_KEY:
        print("API Key        : NOT SET (running in offline mode)\n")
    else:
        print("API Key        : CONFIGURED\n")
    print('Type "help" for available commands or "exit" to quit.\n')

    ai = OmarAI()

    while True:
        try:
            user_input = input(f"[{ai.mode.upper()}] OMAR AI > ").strip()
        except (EOFError, KeyboardInterrupt):
            print("\nSession terminated.")
            break

        if not user_input:
            continue

        built_in = _handle_built_in(ai, user_input)
        if built_in is not None:
            print(built_in)
            continue

        response = ai.chat(user_input)
        print(f"\n{response}\n")


if __name__ == "__main__":
    main()
