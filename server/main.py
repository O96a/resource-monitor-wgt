import logging
from contextlib import asynccontextmanager
from datetime import datetime

from apscheduler.schedulers.asyncio import AsyncIOScheduler
from fastapi import Depends, FastAPI, HTTPException, Security
from fastapi.middleware.cors import CORSMiddleware
from fastapi.security.api_key import APIKeyHeader

from config import settings
from db.store import init_db, insert_metrics
from metrics.oracle_collector import collect_oracle_metrics
from metrics.os_collector import collect_os_metrics
from routers.alarms import router as alarms_router
from routers.history import router as history_router
from routers.metrics import router as metrics_router
from services.alarm_engine import check_alarms
from state import state

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s | %(levelname)-8s | %(name)s — %(message)s",
)
logger = logging.getLogger(__name__)


# ── Collection cycle ──────────────────────────────────────────────

async def collect_cycle() -> None:
    """Collect OS + Oracle metrics, persist history, evaluate alarms."""
    try:
        os_data = collect_os_metrics()
        oracle_data = None
        if settings.oracle_enabled:
            oracle_data = await collect_oracle_metrics(
                settings.oracle_user, settings.oracle_password, settings.oracle_dsn
            )

        snapshot = {
            "timestamp": datetime.utcnow().isoformat() + "Z",
            "server_name": settings.server_name,
            "os": os_data,
            "oracle": oracle_data,
        }
        state.latest_snapshot = snapshot

        # Flatten numerics for SQLite history
        flat: dict = {f"os.{k}": v for k, v in os_data.items() if isinstance(v, (int, float))}
        if oracle_data:
            flat.update(
                {f"oracle.{k}": v for k, v in oracle_data.items() if isinstance(v, (int, float))}
            )
        await insert_metrics(flat)

        # Alarm evaluation — pass nested structure so dot-path resolution works
        alarm_ctx = {**os_data, "oracle": oracle_data or {}}
        await check_alarms(alarm_ctx)

        logger.debug("Collection cycle complete")
    except Exception as exc:
        logger.error("Collection cycle error: %s", exc)


# ── Application lifecycle ─────────────────────────────────────────

scheduler = AsyncIOScheduler(timezone="UTC")


@asynccontextmanager
async def lifespan(_: FastAPI):
    await init_db()
    await collect_cycle()                                      # immediate first run
    scheduler.add_job(
        collect_cycle,
        "interval",
        seconds=settings.collection_interval_seconds,
        id="collect",
    )
    scheduler.start()
    logger.info(
        "Resource Monitor started — collecting every %ds", settings.collection_interval_seconds
    )
    yield
    scheduler.shutdown(wait=False)


# ── App setup ─────────────────────────────────────────────────────

app = FastAPI(
    title="Resource Monitor API",
    description="Oracle server resource monitoring backend",
    version="1.0.0",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["GET", "POST"],
    allow_headers=["*"],
)

# API key auth
_api_key_header = APIKeyHeader(name="X-API-Key", auto_error=True)


async def require_api_key(api_key: str = Security(_api_key_header)) -> str:
    if api_key != settings.api_key:
        raise HTTPException(status_code=403, detail="Invalid API key")
    return api_key


_auth = [Depends(require_api_key)]

app.include_router(metrics_router, prefix="/metrics", tags=["metrics"],  dependencies=_auth)
app.include_router(history_router, prefix="/history", tags=["history"],  dependencies=_auth)
app.include_router(alarms_router,  prefix="/alarms",  tags=["alarms"],   dependencies=_auth)


@app.get("/health", tags=["health"], summary="Health check (no auth)")
async def health():
    return {
        "status": "ok",
        "server": settings.server_name,
        "oracle_enabled": settings.oracle_enabled,
        "collection_interval_s": settings.collection_interval_seconds,
    }


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host=settings.host, port=settings.port, reload=False)
