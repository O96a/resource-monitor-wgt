from fastapi import APIRouter, HTTPException
from db.store import get_active_alarms, acknowledge_alarm

router = APIRouter()


@router.get("", summary="Active alarms")
async def list_alarms():
    alarms = await get_active_alarms()
    return {"count": len(alarms), "alarms": alarms}


@router.post("/{alarm_id}/acknowledge", summary="Acknowledge an alarm")
async def ack_alarm(alarm_id: str):
    await acknowledge_alarm(alarm_id)
    return {"status": "acknowledged", "id": alarm_id}
