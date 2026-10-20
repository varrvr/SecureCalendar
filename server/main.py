import base64
import binascii
import logging
import re
import time
import uuid
from dataclasses import dataclass
from typing import Optional

from fastapi import Depends, FastAPI, HTTPException
from pydantic import BaseModel, Field

from . import database as db
from . import opaque_auth
from .auth import create_token, get_current_user
from .websocket import manager, router as websocket_router


_TOKEN_QUERY = re.compile(r"(?i)([?&]token=)[^&\s\"']+")


class _WebSocketTokenLogFilter(logging.Filter):
    """Uvicorn logs websocket paths from both access and protocol loggers."""

    def filter(self, record: logging.LogRecord) -> bool:
        if record.name not in {"uvicorn.access", "uvicorn.error"}:
            return True
        if isinstance(record.args, tuple):
            record.args = tuple(
                _TOKEN_QUERY.sub(r"\1[REDACTED]", value) if isinstance(value, str) else value
                for value in record.args
            )
        record.msg = _TOKEN_QUERY.sub(r"\1[REDACTED]", str(record.msg))
        return True


_token_log_filter = _WebSocketTokenLogFilter()
logging.getLogger("uvicorn.access").addFilter(_token_log_filter)
logging.getLogger("uvicorn.error").addFilter(_token_log_filter)

app = FastAPI(
    title="Secure Calendar Mediator Server (OPAQUE)",
    version="0.2.0",
)

app.include_router(websocket_router)


@app.on_event("startup")
def on_startup() -> None:
    db.init_db()
    # Server setup is stable across restarts and is stored with the user DB.
    opaque_auth.get_server()


# ---------- Модели запросов ----------

class RegisterStartRequest(BaseModel):
    username: str = Field(min_length=3, max_length=64)
    display_name: str = Field(min_length=1, max_length=128)
    public_key: str = Field(min_length=1, max_length=8192)
    registration_request: str  # base64


class RegisterFinishRequest(BaseModel):
    username: str
    registration_upload: str  # base64


class LoginStartRequest(BaseModel):
    username: str
    credential_request: str  # base64


class LoginFinishRequest(BaseModel):
    username: str
    login_state_id: str
    credential_finalization: str  # base64


# ---------- Вспомогательные ----------

def public_user(user: dict) -> dict:
    return {
        "id": user["id"],
        "username": user["username"],
        "display_name": user["display_name"],
        "public_key": user["public_key"],
        "created_at": user.get("created_at"),
    }


def public_session(session: dict) -> dict:
    return {
        "session_id": session["session_id"],
        "initiator_id": session["initiator_id"],
        "responder_id": session["responder_id"],
        "status": session["status"],
        "period_start": session["period_start"],
        "period_end": session["period_end"],
        "created_at": session["created_at"],
        "updated_at": session["updated_at"],
    }


def get_session_for_user(session_id: str, current_user: dict) -> dict:
    session = db.get_session(session_id)
    if session is None:
        raise HTTPException(status_code=404, detail="session_not_found")

    participants = {session["initiator_id"], session["responder_id"]}
    if current_user["id"] not in participants:
        raise HTTPException(status_code=403, detail="forbidden")

    return session


# In-memory хранилище server_state для шага /login/finish.
@dataclass
class _PendingLogin:
    username: str
    state: object
    created_at: float


_login_states: dict[str, _PendingLogin] = {}
_LOGIN_STATE_TTL_SECONDS = 120


def _decode_message(value: str) -> bytes:
    try:
        return base64.b64decode(value, validate=True)
    except (binascii.Error, ValueError) as exc:
        raise HTTPException(status_code=400, detail="invalid_base64") from exc


def _prune_login_states() -> None:
    cutoff = time.monotonic() - _LOGIN_STATE_TTL_SECONDS
    for state_id, pending in list(_login_states.items()):
        if pending.created_at < cutoff:
            _login_states.pop(state_id, None)


# ---------- Health ----------

@app.get("/health")
async def health() -> dict:
    return {"status": "ok"}


# ---------- OPAQUE: регистрация ----------

@app.post("/auth/register/start")
async def register_start(request: RegisterStartRequest) -> dict:
    raw_request = _decode_message(request.registration_request)
    try:
        response_bytes = opaque_auth.registration_start(request.username, raw_request)
    except Exception as exc:
        # Don't expose native/parser internals or request contents to clients.
        raise HTTPException(
            status_code=400,
            detail=f"invalid_registration_request:{type(exc).__name__}:{len(raw_request)}",
        ) from exc

    try:
        user = db.create_user_pending(
            username=request.username,
            display_name=request.display_name,
            public_key=request.public_key,
        )
    except ValueError:
        raise HTTPException(status_code=409, detail="username_already_exists")

    return {
        "user_id": user["id"],
        "registration_response": base64.b64encode(response_bytes).decode("ascii"),
    }


@app.post("/auth/register/finish")
async def register_finish(request: RegisterFinishRequest) -> dict:
    user = db.get_user_by_username(request.username)
    if user is None:
        raise HTTPException(status_code=404, detail="user_not_found")
    if user["password_file"] is not None:
        raise HTTPException(status_code=409, detail="already_registered")

    raw_upload = _decode_message(request.registration_upload)
    try:
        password_file_bytes = opaque_auth.registration_finish(raw_upload)
    except Exception as exc:
        raise HTTPException(status_code=400, detail="invalid_registration_upload") from exc

    if not db.set_password_file(user["id"], password_file_bytes):
        raise HTTPException(status_code=409, detail="already_registered")
    user = db.get_user_by_id(user["id"])

    token = create_token(user["id"])
    return {
        "access_token": token,
        "token_type": "bearer",
        "user": public_user(user),
    }


# ---------- OPAQUE: вход ----------

@app.post("/auth/login/start")
async def login_start(request: LoginStartRequest) -> dict:
    user = db.get_user_by_username(request.username)
    if user is None or user["password_file"] is None:
        raise HTTPException(status_code=401, detail="invalid_credentials")

    raw_request = _decode_message(request.credential_request)
    try:
        response_bytes, server_state = opaque_auth.login_start(
            request.username,
            raw_request,
            user["password_file"],
        )
    except Exception as exc:
        raise HTTPException(status_code=400, detail="invalid_credential_request") from exc

    _prune_login_states()
    login_state_id = str(uuid.uuid4())
    _login_states[login_state_id] = _PendingLogin(
        username=request.username,
        state=server_state,
        created_at=time.monotonic(),
    )

    return {
        "user_id": user["id"],
        "login_state_id": login_state_id,
        "credential_response": base64.b64encode(response_bytes).decode("ascii"),
    }


@app.post("/auth/login/finish")
async def login_finish(request: LoginFinishRequest) -> dict:
    _prune_login_states()
    pending = _login_states.pop(request.login_state_id, None)
    if pending is None or pending.username != request.username:
        raise HTTPException(status_code=401, detail="login_state_missing_or_expired")

    user = db.get_user_by_username(request.username)
    if user is None or user["password_file"] is None:
        raise HTTPException(status_code=401, detail="invalid_credentials")

    raw_finalization = _decode_message(request.credential_finalization)
    try:
        opaque_auth.login_finish(raw_finalization, pending.state)
    except Exception as exc:
        raise HTTPException(status_code=401, detail="invalid_credentials") from exc

    token = create_token(user["id"])
    return {
        "access_token": token,
        "token_type": "bearer",
        "user": public_user(user),
    }


# ---------- Пользователи ----------

@app.get("/users/me")
async def me(current_user: dict = Depends(get_current_user)) -> dict:
    return public_user(current_user)


@app.get("/users")
async def list_users(
    query: str = "",
    current_user: dict = Depends(get_current_user),
) -> list[dict]:
    users = db.search_users(query=query, exclude_id=current_user["id"])
    return [public_user(user) for user in users]


@app.get("/users/{user_id}")
async def get_user(
    user_id: int,
    current_user: dict = Depends(get_current_user),
) -> dict:
    user = db.get_user_by_id(user_id)
    if user is None:
        raise HTTPException(status_code=404, detail="user_not_found")
    return public_user(user)


# ---------- Сессии ----------

class CreateSessionRequest(BaseModel):
    responder_id: int
    period_start: Optional[int] = None
    period_end: Optional[int] = None


@app.post("/sessions")
async def create_session(
    request: CreateSessionRequest,
    current_user: dict = Depends(get_current_user),
) -> dict:
    if request.responder_id == current_user["id"]:
        raise HTTPException(status_code=400, detail="cannot_compare_with_self")

    responder = db.get_user_by_id(request.responder_id)
    if responder is None:
        raise HTTPException(status_code=404, detail="responder_not_found")

    if (
        request.period_start is not None
        and request.period_end is not None
        and request.period_start >= request.period_end
    ):
        raise HTTPException(status_code=400, detail="invalid_period")

    session_id = str(uuid.uuid4())
    session = db.create_session(
        session_id=session_id,
        initiator_id=current_user["id"],
        responder_id=request.responder_id,
        period_start=request.period_start,
        period_end=request.period_end,
    )

    await manager.send_to_user(
        request.responder_id,
        {
            "type": "session_request",
            "session_id": session_id,
            "sender_id": current_user["id"],
            "recipient_id": request.responder_id,
            "payload": {
                "initiator": public_user(current_user),
                "period_start": request.period_start,
                "period_end": request.period_end,
            },
            "server_ts": int(time.time() * 1000),
        },
    )

    return public_session(session)


@app.get("/sessions")
async def list_sessions(
    current_user: dict = Depends(get_current_user),
) -> list[dict]:
    sessions = db.list_sessions_for_user(current_user["id"])
    return [public_session(session) for session in sessions]


@app.get("/sessions/{session_id}")
async def get_session(
    session_id: str,
    current_user: dict = Depends(get_current_user),
) -> dict:
    session = get_session_for_user(session_id, current_user)
    return public_session(session)


@app.post("/sessions/{session_id}/accept")
async def accept_session(
    session_id: str,
    current_user: dict = Depends(get_current_user),
) -> dict:
    session = get_session_for_user(session_id, current_user)

    if current_user["id"] != session["responder_id"]:
        raise HTTPException(status_code=403, detail="only_responder_can_accept")

    if session["status"] != "pending":
        raise HTTPException(status_code=409, detail="invalid_session_status")

    db.update_session_status(session_id, "accepted")
    session = db.get_session(session_id)

    await manager.send_to_user(
        session["initiator_id"],
        {
            "type": "session_accepted",
            "session_id": session_id,
            "sender_id": current_user["id"],
            "recipient_id": session["initiator_id"],
            "payload": {},
            "server_ts": int(time.time() * 1000),
        },
    )

    return public_session(session)


@app.post("/sessions/{session_id}/reject")
async def reject_session(
    session_id: str,
    current_user: dict = Depends(get_current_user),
) -> dict:
    session = get_session_for_user(session_id, current_user)

    if current_user["id"] != session["responder_id"]:
        raise HTTPException(status_code=403, detail="only_responder_can_reject")

    if session["status"] != "pending":
        raise HTTPException(status_code=409, detail="invalid_session_status")

    db.update_session_status(session_id, "rejected")
    session = db.get_session(session_id)

    await manager.send_to_user(
        session["initiator_id"],
        {
            "type": "session_rejected",
            "session_id": session_id,
            "sender_id": current_user["id"],
            "recipient_id": session["initiator_id"],
            "payload": {},
            "server_ts": int(time.time() * 1000),
        },
    )

    return public_session(session)


@app.post("/sessions/{session_id}/cancel")
async def cancel_session(
    session_id: str,
    current_user: dict = Depends(get_current_user),
) -> dict:
    session = get_session_for_user(session_id, current_user)

    if current_user["id"] != session["initiator_id"]:
        raise HTTPException(status_code=403, detail="only_initiator_can_cancel")

    if session["status"] not in ("pending", "accepted"):
        raise HTTPException(status_code=409, detail="invalid_session_status")

    db.update_session_status(session_id, "cancelled")
    session = db.get_session(session_id)

    await manager.send_to_user(
        session["responder_id"],
        {
            "type": "session_cancelled",
            "session_id": session_id,
            "sender_id": current_user["id"],
            "recipient_id": session["responder_id"],
            "payload": {},
            "server_ts": int(time.time() * 1000),
        },
    )

    return public_session(session)


@app.post("/sessions/{session_id}/complete")
async def complete_session(
    session_id: str,
    current_user: dict = Depends(get_current_user),
) -> dict:
    session = get_session_for_user(session_id, current_user)

    if session["status"] != "active":
        raise HTTPException(status_code=409, detail="invalid_session_status")

    db.update_session_status(session_id, "completed")
    session = db.get_session(session_id)

    other_id = (
        session["responder_id"]
        if current_user["id"] == session["initiator_id"]
        else session["initiator_id"]
    )

    await manager.send_to_user(
        other_id,
        {
            "type": "session_completed",
            "session_id": session_id,
            "sender_id": current_user["id"],
            "recipient_id": other_id,
            "payload": {},
            "server_ts": int(time.time() * 1000),
        },
    )

    return public_session(session)
