import asyncio
import json
import os
import time
from collections import defaultdict, deque
from typing import Dict, Set

from fastapi import APIRouter, Query, WebSocket, WebSocketDisconnect

from . import database as db
from .auth import get_user_by_ws_token

MAX_MESSAGE_CHARS = int(os.getenv("MAX_MESSAGE_CHARS", "2000000"))
PSI_RATE_MAX = int(os.getenv("PSI_RATE_MAX", "30"))
PSI_RATE_WINDOW = int(os.getenv("PSI_RATE_WINDOW", "60"))

router = APIRouter()


class ConnectionManager:
    def __init__(self) -> None:
        self.active: Dict[int, Set[WebSocket]] = defaultdict(set)
        self._lock = asyncio.Lock()

    async def connect(self, user_id: int, websocket: WebSocket) -> None:
        await websocket.accept()
        async with self._lock:
            self.active[int(user_id)].add(websocket)

    async def disconnect(self, user_id: int, websocket: WebSocket) -> None:
        async with self._lock:
            connections = self.active.get(int(user_id))
            if connections is None:
                return
            connections.discard(websocket)
            if not connections:
                self.active.pop(int(user_id), None)

    async def send_to_user(self, user_id: int, data: dict) -> bool:
        async with self._lock:
            connections = list(self.active.get(int(user_id), set()))

        sent = False
        dead: list[WebSocket] = []

        for websocket in connections:
            try:
                await websocket.send_json(data)
                sent = True
            except Exception:
                dead.append(websocket)

        for websocket in dead:
            await self.disconnect(int(user_id), websocket)

        return sent


class RateLimiter:
    def __init__(self, max_events: int, window_sec: int) -> None:
        self.max_events = max_events
        self.window_sec = window_sec
        self._events: Dict[int, deque] = defaultdict(deque)
        self._lock = asyncio.Lock()

    async def allow(self, key: int) -> bool:
        now = time.monotonic()
        async with self._lock:
            queue = self._events[int(key)]
            while queue and queue[0] <= now - self.window_sec:
                queue.popleft()

            if len(queue) >= self.max_events:
                return False

            queue.append(now)
            return True


manager = ConnectionManager()
psi_limiter = RateLimiter(PSI_RATE_MAX, PSI_RATE_WINDOW)


async def _send_error(
    websocket: WebSocket,
    code: str,
    message: str = "",
    session_id: str | None = None,
) -> None:
    await websocket.send_json(
        {
            "type": "error",
            "code": code,
            "message": message,
            "session_id": session_id,
            "server_ts": int(time.time() * 1000),
        }
    )


@router.websocket("/ws")
async def websocket_endpoint(
    websocket: WebSocket,
    token: str = Query(...),
) -> None:
    user = get_user_by_ws_token(token)
    if user is None:
        await websocket.close(code=1008)
        return

    user_id = int(user["id"])
    await manager.connect(user_id, websocket)

    await websocket.send_json(
        {
            "type": "connected",
            "user_id": user_id,
            "server_ts": int(time.time() * 1000),
        }
    )

    try:
        while True:
            raw = await websocket.receive_text()

            if len(raw) > MAX_MESSAGE_CHARS:
                await _send_error(websocket, "message_too_large")
                continue

            try:
                message = json.loads(raw)
            except json.JSONDecodeError:
                await _send_error(websocket, "invalid_json")
                continue

            if not isinstance(message, dict):
                await _send_error(websocket, "invalid_message")
                continue

            message_type = message.get("type")
            if message_type == "ping":
                await websocket.send_json(
                    {
                        "type": "pong",
                        "server_ts": int(time.time() * 1000),
                    }
                )
                continue

            if not isinstance(message_type, str) or not message_type:
                await _send_error(websocket, "missing_type")
                continue

            session_id = message.get("session_id")
            recipient_raw = message.get("recipient_id")

            if not isinstance(session_id, str) or not session_id:
                await _send_error(websocket, "missing_session_id")
                continue

            if recipient_raw is None:
                await _send_error(
                    websocket,
                    "missing_recipient_id",
                    session_id=session_id,
                )
                continue

            try:
                recipient_id = int(recipient_raw)
            except (TypeError, ValueError):
                await _send_error(
                    websocket,
                    "invalid_recipient_id",
                    session_id=session_id,
                )
                continue

            if not await psi_limiter.allow(user_id):
                await _send_error(
                    websocket,
                    "rate_limited",
                    "too many PSI messages",
                    session_id,
                )
                continue

            session = db.get_session(session_id)
            if session is None:
                await _send_error(
                    websocket,
                    "session_not_found",
                    session_id=session_id,
                )
                continue

            participants = {int(session["initiator_id"]), int(session["responder_id"])}
            if user_id not in participants:
                await _send_error(
                    websocket,
                    "forbidden_session",
                    session_id=session_id,
                )
                continue

            other_id = (
                int(session["responder_id"])
                if user_id == int(session["initiator_id"])
                else int(session["initiator_id"])
            )

            if recipient_id != other_id:
                await _send_error(
                    websocket,
                    "invalid_recipient",
                    session_id=session_id,
                )
                continue

            if session["status"] not in ("accepted", "active"):
                await _send_error(
                    websocket,
                    "session_not_active",
                    session_id=session_id,
                )
                continue

            if session["status"] == "accepted":
                db.update_session_status(session_id, "active")

            outgoing = {
                "type": message_type,
                "session_id": session_id,
                "sender_id": user_id,
                "recipient_id": other_id,
                "payload": message.get("payload"),
                "server_ts": int(time.time() * 1000),
            }

            delivered = await manager.send_to_user(other_id, outgoing)
            if not delivered:
                await _send_error(
                    websocket,
                    "recipient_offline",
                    session_id=session_id,
                )

    except WebSocketDisconnect:
        pass
    finally:
        await manager.disconnect(user_id, websocket)