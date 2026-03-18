import aiosqlite
from datetime import datetime, timedelta
from typing import List, Dict, Any

from config import settings

DB = settings.metrics_db_path


async def init_db() -> None:
    async with aiosqlite.connect(DB) as db:
        await db.execute("""
            CREATE TABLE IF NOT EXISTS metrics_history (
                id        INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp TEXT    NOT NULL,
                metric    TEXT    NOT NULL,
                value     REAL    NOT NULL
            )
        """)
        await db.execute(
            "CREATE INDEX IF NOT EXISTS idx_metric_ts ON metrics_history(metric, timestamp)"
        )
        await db.execute("""
            CREATE TABLE IF NOT EXISTS alarms (
                id           TEXT    PRIMARY KEY,
                metric       TEXT    NOT NULL,
                value        REAL    NOT NULL,
                threshold    REAL    NOT NULL,
                severity     TEXT    NOT NULL,
                message      TEXT    NOT NULL,
                triggered_at TEXT    NOT NULL,
                acknowledged INTEGER DEFAULT 0
            )
        """)
        await db.commit()


async def insert_metrics(flat: Dict[str, float]) -> None:
    """Insert a flat dict of {metric_name: value} with the current UTC timestamp."""
    now = datetime.utcnow().isoformat()
    rows = [(now, k, v) for k, v in flat.items()]
    cutoff = (
        datetime.utcnow() - timedelta(hours=settings.history_retention_hours)
    ).isoformat()

    async with aiosqlite.connect(DB) as db:
        await db.executemany(
            "INSERT INTO metrics_history (timestamp, metric, value) VALUES (?, ?, ?)",
            rows,
        )
        await db.execute("DELETE FROM metrics_history WHERE timestamp < ?", (cutoff,))
        await db.commit()


async def get_history(metric: str, window_minutes: int) -> List[Dict[str, Any]]:
    cutoff = (datetime.utcnow() - timedelta(minutes=window_minutes)).isoformat()
    async with aiosqlite.connect(DB) as db:
        db.row_factory = aiosqlite.Row
        async with db.execute(
            "SELECT timestamp, value FROM metrics_history "
            "WHERE metric = ? AND timestamp > ? ORDER BY timestamp ASC",
            (metric, cutoff),
        ) as cur:
            rows = await cur.fetchall()
    return [{"timestamp": r["timestamp"], "value": r["value"]} for r in rows]


async def upsert_alarm(alarm: Dict[str, Any]) -> None:
    async with aiosqlite.connect(DB) as db:
        await db.execute(
            """
            INSERT OR REPLACE INTO alarms
              (id, metric, value, threshold, severity, message, triggered_at, acknowledged)
            VALUES
              (:id, :metric, :value, :threshold, :severity, :message, :triggered_at, 0)
            """,
            alarm,
        )
        await db.commit()


async def get_active_alarms() -> List[Dict[str, Any]]:
    async with aiosqlite.connect(DB) as db:
        db.row_factory = aiosqlite.Row
        async with db.execute(
            "SELECT * FROM alarms WHERE acknowledged = 0 ORDER BY triggered_at DESC"
        ) as cur:
            rows = await cur.fetchall()
    return [dict(r) for r in rows]


async def acknowledge_alarm(alarm_id: str) -> None:
    async with aiosqlite.connect(DB) as db:
        await db.execute(
            "UPDATE alarms SET acknowledged = 1 WHERE id = ?", (alarm_id,)
        )
        await db.commit()


async def clear_alarm(alarm_id: str) -> None:
    async with aiosqlite.connect(DB) as db:
        await db.execute("DELETE FROM alarms WHERE id = ?", (alarm_id,))
        await db.commit()
