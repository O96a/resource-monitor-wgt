from typing import Optional, Dict, Any


class AppState:
    """Shared in-memory state — holds the latest collected snapshot."""
    latest_snapshot: Optional[Dict[str, Any]] = None


state = AppState()
