"""Configuration for OMAR AI Founder Command Center."""

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
        "Analyze long-term opportunities, ecosystem positioning, and potential "
        "partnerships or expansion paths."
    ),
    "operations": (
        "You are in OPERATIONS MODE. "
        "Monitor infrastructure health, system uptime, network performance, "
        "and service reliability."
    ),
    "financial": (
        "You are in FINANCIAL INSIGHT MODE. "
        "Evaluate growth indicators such as adoption trends, usage patterns, "
        "and sustainability metrics."
    ),
    "security": (
        "You are in SECURITY AWARENESS MODE. "
        "Review patterns that may indicate operational risk, instability, "
        "or unusual system behavior."
    ),
    "advisor": (
        "You are in ADVISOR MODE. "
        "Provide structured recommendations based on data and observations."
    ),
}

DEFAULT_MODE: str = "operations"

# ---------------------------------------------------------------------------
# Ecosystem components (used for status placeholders)
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
║          OMAR AI — FOUNDER COMMAND CENTER                ║
║     Strategic Intelligence & Ecosystem Operations       ║
╚══════════════════════════════════════════════════════════╝
"""

HELP_TEXT: str = """
Available commands:
  status                        — Quick combined system status dashboard
  show ecosystem status         — Overview of all ecosystem components
  show infrastructure health    — Node and server health summary
  show network performance      — Latency, throughput, uptime metrics
  show service adoption metrics — Usage and membership activity
  generate operational report   — Full operational status report
  generate strategic analysis   — Long-term strategic recommendations
  switch mode <mode>            — Change operating mode
                                  Modes: strategy | operations | financial |
                                         security | advisor
  help                          — Show this help text
  exit / quit                   — Exit the command center

Any other input is forwarded directly to OMAR AI.
"""
