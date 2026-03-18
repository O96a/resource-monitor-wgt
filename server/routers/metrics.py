from fastapi import APIRouter
from state import state

router = APIRouter()


@router.get("", summary="Current resource snapshot")
@router.get("/", include_in_schema=False)
async def get_metrics():
    if state.latest_snapshot is None:
        return {"status": "collecting", "message": "First collection in progress…"}
    return state.latest_snapshot
