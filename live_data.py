"""Live system metrics collector for OMAR AI status dashboard.

Collects real-time host metrics (CPU, memory, disk, uptime, network I/O)
using *psutil*.  When psutil is not installed every field falls back to None
so callers can display a graceful degraded view.
"""

from __future__ import annotations

import datetime
import time
from typing import Any

try:
    import psutil as _psutil
    _PSUTIL_AVAILABLE = True
except ImportError:  # pragma: no cover
    _psutil = None  # type: ignore[assignment]
    _PSUTIL_AVAILABLE = False


def collect() -> dict[str, Any]:
    """Return a dict of current host metrics.

    All keys are always present; values are *None* when psutil is unavailable.
    """
    now = datetime.datetime.now(datetime.timezone.utc)
    data: dict[str, Any] = {
        "timestamp": now.strftime("%Y-%m-%d %H:%M:%S UTC"),
        "psutil_available": _PSUTIL_AVAILABLE,
        # infrastructure
        "uptime_str": None,
        "cpu_percent": None,
        "cpu_count": None,
        "memory_percent": None,
        "memory_used_gb": None,
        "memory_total_gb": None,
        "disk_percent": None,
        "disk_used_gb": None,
        "disk_total_gb": None,
        # network
        "net_bytes_sent": None,
        "net_bytes_recv": None,
        "process_count": None,
    }

    if not _PSUTIL_AVAILABLE:
        return data

    # CPU
    data["cpu_percent"] = _psutil.cpu_percent(interval=0.1)
    data["cpu_count"] = _psutil.cpu_count(logical=True)

    # Memory
    mem = _psutil.virtual_memory()
    data["memory_percent"] = mem.percent
    data["memory_used_gb"] = mem.used / (1024 ** 3)
    data["memory_total_gb"] = mem.total / (1024 ** 3)

    # Disk (root partition)
    disk = _psutil.disk_usage("/")
    data["disk_percent"] = disk.percent
    data["disk_used_gb"] = disk.used / (1024 ** 3)
    data["disk_total_gb"] = disk.total / (1024 ** 3)

    # Uptime
    uptime_secs = time.time() - _psutil.boot_time()
    data["uptime_str"] = _fmt_uptime(uptime_secs)

    # Network I/O
    net = _psutil.net_io_counters()
    data["net_bytes_sent"] = net.bytes_sent
    data["net_bytes_recv"] = net.bytes_recv

    # Processes
    data["process_count"] = len(_psutil.pids())

    return data


# ---------------------------------------------------------------------------
# Formatting helpers
# ---------------------------------------------------------------------------

def _fmt_uptime(seconds: float) -> str:
    """Format uptime seconds as a human-readable string (e.g. '3d 5h 12m')."""
    td = datetime.timedelta(seconds=int(seconds))
    days = td.days
    hours, remainder = divmod(td.seconds, 3600)
    minutes, _ = divmod(remainder, 60)
    if days:
        return f"{days}d {hours}h {minutes}m"
    return f"{hours}h {minutes}m"


def fmt_bytes(n: int) -> str:
    """Format a byte count as a human-readable string (e.g. '1.23 GB')."""
    for unit in ("B", "KB", "MB", "GB", "TB"):
        if n < 1024:
            return f"{n:.1f} {unit}"
        n /= 1024  # type: ignore[assignment]
    return f"{n:.1f} PB"
