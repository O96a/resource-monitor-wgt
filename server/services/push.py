import logging

import httpx

from config import settings

logger = logging.getLogger(__name__)

FCM_ENDPOINT = "https://fcm.googleapis.com/fcm/send"


async def send_push_notification(title: str, body: str) -> None:
    """Send an FCM push notification using the legacy HTTP API."""
    if not (settings.fcm_enabled and settings.fcm_server_key and settings.fcm_device_token):
        logger.debug("FCM disabled or not configured — skipping push")
        return

    payload = {
        "to": settings.fcm_device_token,
        "priority": "high",
        "notification": {
            "title": title,
            "body": body,
            "sound": "default",
            "android_channel_id": "resource_alarms",
        },
        "data": {
            "type": "resource_alarm",
            "title": title,
            "body": body,
        },
    }

    try:
        async with httpx.AsyncClient(timeout=10.0) as client:
            resp = await client.post(
                FCM_ENDPOINT,
                json=payload,
                headers={
                    "Authorization": f"key={settings.fcm_server_key}",
                    "Content-Type": "application/json",
                },
            )
            resp.raise_for_status()
            logger.info("Push notification sent: %s", title)
    except Exception as exc:
        logger.error("FCM push failed: %s", exc)
