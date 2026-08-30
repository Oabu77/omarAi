"""OMAR AI command-line prototype with verification-first status reporting."""

from __future__ import annotations

import sys
from pathlib import Path
from typing import Any, Mapping, Optional

try:
    import openai
except ImportError:  # pragma: no cover
    openai = None  # type: ignore[assignment]

import config
import live_data

# ---------------------------------------------------------------------------
# Load system prompt from markdown file
# ---------------------------------------------------------------------------
_PROMPT_FILE = Path(__file__).parent / "system_prompt.md"


def _load_system_prompt() -> str:
    """Return the OMAR AI system prompt from *system_prompt.md*."""
    return _PROMPT_FILE.read_text(encoding="utf-8")


def _format_local_metrics(
    metrics: Mapping[str, Any], indent: str = "  "
) -> list[str]:
    """Format only metrics that the local collector actually measured."""

    unknown = "UNKNOWN — collection unavailable"
    if metrics.get("uptime_str") is None:
        uptime = unknown
    else:
        uptime = str(metrics["uptime_str"])

    if metrics.get("cpu_percent") is None or metrics.get("cpu_count") is None:
        cpu = unknown
    else:
        cpu = f"{metrics['cpu_percent']:.1f} % ({metrics['cpu_count']} logical CPUs)"

    memory_values = (
        metrics.get("memory_percent"),
        metrics.get("memory_used_gb"),
        metrics.get("memory_total_gb"),
    )
    if any(value is None for value in memory_values):
        memory = unknown
    else:
        memory = (
            f"{metrics['memory_percent']:.1f} % "
            f"({metrics['memory_used_gb']:.1f} / {metrics['memory_total_gb']:.1f} GB)"
        )

    disk_values = (
        metrics.get("disk_percent"),
        metrics.get("disk_used_gb"),
        metrics.get("disk_total_gb"),
    )
    if any(value is None for value in disk_values):
        disk = unknown
    else:
        disk = (
            f"{metrics['disk_percent']:.1f} % "
            f"({metrics['disk_used_gb']:.1f} / {metrics['disk_total_gb']:.1f} GB)"
        )

    sent = metrics.get("net_bytes_sent")
    sent_text = unknown if sent is None else live_data.fmt_bytes(sent)
    received = metrics.get("net_bytes_recv")
    received_text = unknown if received is None else live_data.fmt_bytes(received)
    processes = metrics.get("process_count")
    processes_text = unknown if processes is None else str(processes)

    return [
        f"{indent}Runtime uptime      : {uptime}",
        f"{indent}CPU sample          : {cpu}",
        f"{indent}Memory snapshot     : {memory}",
        f"{indent}Root disk snapshot  : {disk}",
        f"{indent}Host bytes sent     : {sent_text} (cumulative)",
        f"{indent}Host bytes received : {received_text} (cumulative)",
        f"{indent}Visible processes   : {processes_text}",
    ]


# ---------------------------------------------------------------------------
# Chat session
# ---------------------------------------------------------------------------

class OmarAI:
    """Stateful chat session for the OMAR AI command-line prototype."""

    def __init__(self, mode: str = config.DEFAULT_MODE) -> None:
        self._system_prompt: str = _load_system_prompt()
        self._mode: str = mode
        self._history: list[dict[str, str]] = []
        self._client: Optional["openai.OpenAI"] = None

        if openai is not None and config.OPENAI_API_KEY:
            self._client = openai.OpenAI(api_key=config.OPENAI_API_KEY)

    # ------------------------------------------------------------------
    # Public API
    # ------------------------------------------------------------------

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
        # Compose messages for the API call
        mode_directive = config.OPERATING_MODES.get(self._mode, "")
        system_content = f"{self._system_prompt}\n\n{mode_directive}"

        messages: list[dict[str, str]] = [
            {"role": "system", "content": system_content},
            *self._history,
            {"role": "user", "content": user_input},
        ]

        if self._is_evidence_command(user_input):
            # Status and report commands stay deterministic even when an AI
            # provider is configured. A language model is not a telemetry source.
            response_text = self._offline_response(user_input)
        elif self._client is None:
            # No API key available — return a helpful offline message
            response_text = self._offline_response(user_input)
        else:
            response_text = self._api_call(messages)

        # Append to history
        self._history.append({"role": "user", "content": user_input})
        self._history.append({"role": "assistant", "content": response_text})

        return response_text

    def reset_history(self) -> None:
        """Clear conversation history."""
        self._history.clear()

    def status_summary(self) -> str:
        """Return measured local metrics and honest external-service states.

        Creating an OpenAI client proves only that client configuration exists; it
        does not prove that credentials, network access, or the remote API work.
        Likewise, this CLI has no health probes for the named ecosystem systems.
        """
        metrics = live_data.collect()

        if self._client is None:
            ai_provider = "DISCONNECTED — OPENAI_API_KEY is not configured"
        else:
            ai_provider = "CONFIGURED — connectivity and credentials not verified"

        lines = [
            "OMAR AI — SYSTEM STATUS",
            "=" * 50,
            f"  Snapshot       : {metrics['timestamp']}",
            f"  Operating Mode : {self._mode.upper()} MODE",
            f"  AI Provider    : {ai_provider}",
            "",
            "EXTERNAL COMPONENTS",
            "-" * 50,
        ]
        for component in config.ECOSYSTEM_COMPONENTS:
            lines.append(f"  • {component}: UNKNOWN — no health probe configured")

        lines += [
            "",
            "LOCAL RUNTIME METRICS (MEASURED)",
            "-" * 50,
            f"  Scope              : {metrics['measurement_scope']}",
            f"  Source             : {metrics['measurement_source']}",
        ]

        if metrics["psutil_available"]:
            lines.extend(_format_local_metrics(metrics))
        else:
            lines.append(
                "  Metrics            : UNKNOWN — psutil is not installed in this runtime"
            )

        if metrics["collection_errors"]:
            lines.append("  Collection errors  :")
            for field, error in metrics["collection_errors"].items():
                lines.append(f"    - {field}: {error}")

        lines += [
            "",
            "External Status: UNKNOWN — no external health checks were run",
            "Overall Result : PARTIAL LOCAL VISIBILITY; production state not verified",
        ]
        return "\n".join(lines)

    # ------------------------------------------------------------------
    # Internal helpers
    # ------------------------------------------------------------------

    @staticmethod
    def _is_evidence_command(user_input: str) -> bool:
        """Return whether a request must use deterministic local evidence."""
        cmd = user_input.strip().lower()
        return any(
            phrase in cmd
            for phrase in (
                "ecosystem status",
                "infrastructure health",
                "network performance",
                "service adoption",
                "operational report",
                "strategic analysis",
            )
        )

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
        except Exception as exc:  # noqa: BLE001
            return f"[OMAR AI ERROR] API call failed: {exc}"

    @staticmethod
    def _offline_response(user_input: str) -> str:
        """Return evidence-bounded responses without inventing remote data."""
        cmd = user_input.strip().lower()

        if "ecosystem status" in cmd:
            lines = ["ECOSYSTEM STATUS OVERVIEW", "=" * 40]
            for component in config.ECOSYSTEM_COMPONENTS:
                lines.append(f"  • {component}: UNKNOWN — no health probe configured")
            lines.extend([
                "",
                "Result: NOT VERIFIED",
                "Evidence: this CLI has no external component telemetry or health-check integrations.",
                "Actions performed: none.",
            ])
            return "\n".join(lines)

        if "infrastructure health" in cmd:
            m = live_data.collect()
            lines = [
                "INFRASTRUCTURE OBSERVATION",
                "=" * 30,
                f"  Snapshot           : {m['timestamp']}",
                f"  Scope              : {m['measurement_scope']}",
                f"  Source             : {m['measurement_source']}",
            ]
            if m["psutil_available"]:
                lines.extend(_format_local_metrics(m))
            else:
                lines.append(
                    "  Metrics            : UNKNOWN — psutil is not installed in this runtime"
                )
            if m["collection_errors"]:
                lines.append("  Collection errors  :")
                for field, error in m["collection_errors"].items():
                    lines.append(f"    - {field}: {error}")
            lines.extend([
                "",
                "Health assessment: UNKNOWN — no service probes or health thresholds are configured.",
                "The measurements above describe only the CLI runtime, not QuranChain, DarCloud, or other external systems.",
            ])
            return "\n".join(lines)

        if "network performance" in cmd:
            return "\n".join([
                "NETWORK PERFORMANCE STATUS",
                "=" * 30,
                "  Transaction throughput : UNKNOWN",
                "  Block finality          : UNKNOWN",
                "  Bridge latency          : UNKNOWN",
                "  Mesh node count         : UNKNOWN",
                "  Service uptime          : UNKNOWN",
                "",
                "Evidence: no blockchain, bridge, mesh, or service telemetry endpoint is configured.",
                "Local host byte counters are cumulative I/O and do not prove application throughput or health.",
                "Actions performed: none.",
            ])

        if "service adoption" in cmd:
            return "\n".join([
                "SERVICE ADOPTION STATUS",
                "=" * 30,
                "  Active users         : UNKNOWN",
                "  Active members       : UNKNOWN",
                "  Registrations        : UNKNOWN",
                "  Merchant accounts    : UNKNOWN",
                "  Paid subscriptions   : UNKNOWN",
                "",
                "Evidence: no analytics, CRM, billing, or membership data source is connected to this CLI.",
                "Trend: NOT DETERMINED.",
                "Actions performed: none.",
            ])

        if "operational report" in cmd:
            m = live_data.collect()
            lines = [
                "OPERATIONAL VERIFICATION REPORT",
                "=" * 35,
                f"Snapshot: {m['timestamp']}",
                "",
                "[External infrastructure, network, services, and security]",
                "  State: UNKNOWN",
                "  Evidence: no external probes, logs, analytics, or security feeds are connected.",
                "",
                "[Local CLI runtime]",
            ]
            if m["psutil_available"]:
                lines.extend(_format_local_metrics(m, indent="  "))
            else:
                lines.append("  Metrics: UNKNOWN — psutil is not installed in this runtime")
            lines.extend([
                "",
                "[Action record]",
                "  No external action was attempted or completed.",
                "",
                "Conclusion: production operational status cannot be determined from this CLI.",
            ])
            return "\n".join(lines)

        if "strategic analysis" in cmd:
            return "\n".join([
                "STRATEGIC ANALYSIS — DATA LIMITED",
                "=" * 35,
                "Verified business, adoption, financial, and infrastructure data: NONE CONNECTED",
                "",
                "Prepared next steps (recommendations only):",
                "  • Connect read-only telemetry and define service-level thresholds.",
                "  • Connect authoritative analytics and billing sources before evaluating adoption.",
                "  • Validate market demand, unit economics, legal constraints, and security risks before expansion.",
                "  • Require approval and provider confirmation for every consequential external action.",
                "",
                "Result state: PREPARED — nothing was submitted or completed.",
            ])

        # Default — ask user to configure API key for full AI responses
        return (
            "[OMAR AI — AI PROVIDER DISCONNECTED]\n"
            "Evidence: OPENAI_API_KEY is not configured. The local CLI can show "
            "measured host metrics and prepare guidance, but it cannot execute or "
            "verify external actions.\n\n"
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


# ---------------------------------------------------------------------------
# CLI entry point
# ---------------------------------------------------------------------------

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

    return None  # pass through to AI


def main() -> None:
    """Run the OMAR AI interactive command-line prototype."""
    print(config.BANNER)
    print(f"Operating Mode : {config.DEFAULT_MODE.upper()} MODE")
    print(f"Model          : {config.MODEL}")
    if not config.OPENAI_API_KEY:
        print("AI Provider    : DISCONNECTED (OPENAI_API_KEY not set)\n")
    else:
        print("AI Provider    : CONFIGURED (connection not yet verified)\n")
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
