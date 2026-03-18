import logging
from typing import Dict, Any, Optional

import oracledb

logger = logging.getLogger(__name__)


async def collect_oracle_metrics(
    user: str, password: str, dsn: str
) -> Optional[Dict[str, Any]]:
    """
    Collect Oracle DB-level metrics using thin mode (no Instant Client needed).

    Required grants on monitor_user:
        GRANT CREATE SESSION TO monitor_user;
        GRANT SELECT ON V_$SESSION TO monitor_user;
        GRANT SELECT ON V_$INSTANCE TO monitor_user;
        GRANT SELECT ON V_$LOG_HISTORY TO monitor_user;
        GRANT SELECT ON DBA_TABLESPACE_USAGE_METRICS TO monitor_user;
        GRANT SELECT ON V_$PARAMETER TO monitor_user;
    """
    try:
        conn = oracledb.connect(user=user, password=password, dsn=dsn)
        cur = conn.cursor()

        # ── Active sessions ──────────────────────────────────────
        cur.execute(
            "SELECT COUNT(*) FROM V$SESSION WHERE STATUS = 'ACTIVE' AND TYPE = 'USER'"
        )
        active_sessions: int = cur.fetchone()[0]

        cur.execute(
            "SELECT TO_NUMBER(VALUE) FROM V$PARAMETER WHERE NAME = 'sessions'"
        )
        row = cur.fetchone()
        max_sessions: int = int(row[0]) if row else 150

        session_percent = round((active_sessions / max_sessions) * 100, 1)

        # ── Tablespace usage (aggregate all tablespaces) ─────────
        cur.execute(
            """
            SELECT
              ROUND(SUM(used_space * 8192) / POWER(1024, 3), 2),
              ROUND(SUM(tablespace_size * 8192) / POWER(1024, 3), 2)
            FROM dba_tablespace_usage_metrics
            """
        )
        ts_row = cur.fetchone()
        ts_used: float = float(ts_row[0] or 0)
        ts_total: float = float(ts_row[1] or 1)
        ts_percent = round((ts_used / ts_total) * 100, 1) if ts_total > 0 else 0.0

        # ── Redo log switches in last hour ───────────────────────
        cur.execute(
            "SELECT COUNT(*) FROM V$LOG_HISTORY WHERE FIRST_TIME >= SYSDATE - 1/24"
        )
        redo_switches: int = cur.fetchone()[0]

        # ── Long-running queries (> 5 s) ─────────────────────────
        cur.execute(
            """
            SELECT COUNT(*) FROM V$SESSION
            WHERE STATUS = 'ACTIVE' AND TYPE = 'USER' AND LAST_CALL_ET > 5
            """
        )
        slow_queries: int = cur.fetchone()[0]

        # ── Instance status & version ────────────────────────────
        cur.execute("SELECT STATUS, VERSION_FULL FROM V$INSTANCE")
        inst_row = cur.fetchone()
        db_status: str = inst_row[0] if inst_row else "UNKNOWN"
        db_version: str = inst_row[1] if inst_row else "Unknown"

        cur.close()
        conn.close()

        return {
            "active_sessions": active_sessions,
            "max_sessions": max_sessions,
            "session_percent": session_percent,
            "tablespace_used_gb": ts_used,
            "tablespace_total_gb": ts_total,
            "tablespace_percent": ts_percent,
            "redo_switches_per_hour": redo_switches,
            "slow_queries_count": slow_queries,
            "db_status": db_status,
            "db_version": db_version,
        }

    except Exception as exc:
        logger.error("Oracle metrics collection failed: %s", exc)
        return None
