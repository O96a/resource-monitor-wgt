from fastapi import APIRouter
from services.alarm_engine import ACTIVE_ALARMS

router = APIRouter()

@router.get("", summary="Active alarms")
async def list_alarms():
    alarms = list(ACTIVE_ALARMS.values())
    return {"count": len(alarms), "alarms": alarms}

@router.post("/{alarm_id}/acknowledge", summary="Acknowledge an alarm")
async def ack_alarm(alarm_id: str):
    if alarm_id in ACTIVE_ALARMS:
        del ACTIVE_ALARMS[alarm_id]
    return {"status": "acknowledged", "id": alarm_id}
