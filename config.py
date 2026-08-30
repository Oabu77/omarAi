"""Configuration for the OMAR AI command-line prototype."""

import os

# ---------------------------------------------------------------------------
# OpenAI / LLM settings
# ---------------------------------------------------------------------------
OPENAI_API_KEY: str = os.environ.get("OPENAI_API_KEY", "")
MODEL: str = os.environ.get("OMAR_AI_MODEL", "gpt-4o")
MAX_TOKENS: int = int(os.environ.get("OMAR_AI_MAX_TOKENS", "2048"))
TEMPERATURE: float = float(os.environ.get("OMAR_AI_TEMPERATURE", "0.3"))

# ---------------------------------------------------------------------------
# Operating modes
# ---------------------------------------------------------------------------
OPERATING_MODES: dict[str, str] = {
    "strategy": (
        "You are in STRATEGY MODE. "
        "Prepare evidence-bounded analysis of long-term opportunities, ecosystem "
        "positioning, and potential partnerships or expansion paths."
    ),
    "operations": (
        "You are in OPERATIONS MODE. "
        "Assess only supplied or verified infrastructure evidence. Never infer "
        "uptime, network performance, deployment, or service reliability."
    ),
    "financial": (
        "You are in FINANCIAL INSIGHT MODE. "
        "Explain supplied financial information and clearly label unavailable "
        "data, assumptions, estimates, and regulated-advice boundaries."
    ),
    "security": (
        "You are in SECURITY AWARENESS MODE. "
        "Review supplied evidence for potential risk. Do not claim that a system "
        "is secure or anomaly-free without appropriate verified telemetry."
    ),
    "advisor": (
        "You are in ADVISOR MODE. "
        "Provide structured recommendations based on data and observations."
    ),
}

DEFAULT_MODE: str = "operations"

# ---------------------------------------------------------------------------
# Optional ecosystem targets. The CLI has no health probes for these names.
# ---------------------------------------------------------------------------
ECOSYSTEM_COMPONENTS: list[str] = [
    "QuranChain™ — Blockchain Infrastructure",
    "QuranChain™ — Smart Contract Engine",
    "QuranChain™ — Validator Node Network",
    "QuranChain™ — Cross-Chain Bridge",
    "Dar Al-Nas™ — Membership Governance",
    "Dar Al-Nas™ — Halal Banking Infrastructure",
    "DarCloud™ — Identity & Authentication",
    "DarCloud™ — Cloud Storage",
    "MeshTalk OS™ — Mesh Routing Network",
    "MeshTalk OS™ — Encrypted Communications",
    "Halal Card™ — Payment Infrastructure",
]

# ---------------------------------------------------------------------------
# CLI display
# ---------------------------------------------------------------------------
BANNER: str = """
╔══════════════════════════════════════════════════════════╗
║              OMAR AI — COMMAND-LINE PROTOTYPE            ║
║        Evidence-Bounded Planning & Local Status          ║
╚══════════════════════════════════════════════════════════╝
"""

HELP_TEXT: str = """
Available commands:
  status                        — Measured local metrics + unverified external states
  show ecosystem status         — External component verification availability
  show infrastructure health    — Measured local runtime snapshot only
  show network performance      — Network telemetry availability (no estimates)
  show service adoption metrics — Analytics-source availability (no estimates)
  generate operational report   — Evidence-bounded verification report
  generate strategic analysis   — Data-limited planning recommendations
  switch mode <mode>            — Change operating mode
                                  Modes: strategy | operations | financial |
                                         security | advisor
  help                          — Show this help text
  exit / quit                   — Exit the command center

Any other input is forwarded directly to OMAR AI.
"""
