import logging
from datetime import datetime
from typing import Dict, Any

from config import settings
from db.store import upsert_alarm, clear_alarm
from services.push import send_push_notification

logger = logging.getLogger(__name__)

# (dot-path into the snapshot dict, display label, threshold getter, severity)
ALARM_RULES = [
    ("cpu_percent",              "CPU",        lambda: settings.alarm_cpu_threshold,        "warning"),
    ("ram_percent",              "RAM",        lambda: settings.alarm_ram_threshold,        "warning"),
    ("disk_percent",             "DISK",       lambda: settings.alarm_disk_threshold,       "critical"),
    ("oracle.session_percent",   "SESSIONS",   lambda: settings.alarm_session_threshold,    "warning"),
    ("oracle.tablespace_percent","TABLESPACE", lambda: settings.alarm_tablespace_threshold, "critical"),
]


def _resolve(path: str, data: Dict[str, Any]):
    """Walk a dot-separated path into a nested dict."""
    value = data
    for key in path.split("."):
        if not isinstance(value, dict):
            return None
        value = value.get(key)
    return value


async def check_alarms(metrics: Dict[str, Any]) -> None:
    for metric_path, label, threshold_fn, severity in ALARM_RULES:
        value = _resolve(metric_path, metrics)
        if value is None:
            continue

        threshold = threshold_fn()
        alarm_id = f"{label.lower()}_high"

        if value >= threshold:
            alarm = {
                "id": alarm_id,
                "metric": metric_path,
                "value": round(float(value), 1),
                "threshold": threshold,
                "severity": severity,
                "message": f"{label} at {value:.0f}% — threshold {threshold:.0f}% breached",
                "triggered_at": datetime.utcnow().isoformat(),
            }
            await upsert_alarm(alarm)
            logger.warning("ALARM: %s", alarm["message"])

            if settings.fcm_enabled:
                await send_push_notification(
                    title=f"{'⚠' if severity == 'warning' else '🔴'} Resource Alarm — {label}",
                    body=alarm["message"],
                )
        else:
            await clear_alarm(alarm_id)
