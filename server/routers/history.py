from fastapi import APIRouter, Query
from db.store import get_history

router = APIRouter()

WINDOWS = {"30m": 30, "1h": 60, "6h": 360, "24h": 1440, "48h": 2880}

VALID_METRICS = [
    "os.cpu_percent",
    "os.ram_percent",
    "os.disk_percent",
    "os.load_avg_1m",
    "os.load_avg_5m",
    "oracle.session_percent",
    "oracle.tablespace_percent",
    "oracle.redo_switches_per_hour",
    "oracle.slow_queries_count",
]


@router.get("", summary="Historical metric data")
async def get_history_data(
    metric: str = Query(
        default="os.cpu_percent",
        description=f"One of: {', '.join(VALID_METRICS)}",
    ),
    window: str = Query(
        default="30m",
        description=f"Time window — one of: {', '.join(WINDOWS.keys())}",
    ),
):
    window_minutes = WINDOWS.get(window, 30)
    data = await get_history(metric, window_minutes)
    return {"metric": metric, "window": window, "points": len(data), "data": data}
