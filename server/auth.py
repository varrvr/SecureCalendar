import base64
import hashlib
import hmac
import json
import os
import time
from typing import Optional

from fastapi import Depends, HTTPException
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from . import database as db

SECRET_KEY = os.getenv("SECRET_KEY", "dev-only-change-me")
TOKEN_TTL_SECONDS = int(os.getenv("TOKEN_TTL_SECONDS", "86400"))

bearer_scheme = HTTPBearer(auto_error=False)


def _b64e(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).decode("ascii").rstrip("=")


def _b64d(data: str) -> bytes:
    padding = "=" * (-len(data) % 4)
    return base64.urlsafe_b64decode(data + padding)


def create_token(user_id: int) -> str:
    payload = {
        "uid": user_id,
        "exp": int(time.time()) + TOKEN_TTL_SECONDS,
    }
    payload_bytes = json.dumps(
        payload,
        separators=(",", ":"),
        ensure_ascii=False,
    ).encode("utf-8")

    payload_b64 = _b64e(payload_bytes)
    signature = hmac.new(
        SECRET_KEY.encode("utf-8"),
        payload_b64.encode("ascii"),
        hashlib.sha256,
    ).digest()

    return f"{payload_b64}.{_b64e(signature)}"


def decode_token(token: str) -> int:
    try:
        payload_b64, signature_b64 = token.split(".", 1)
    except ValueError as exc:
        raise ValueError("invalid_token_format") from exc

    expected_signature = hmac.new(
        SECRET_KEY.encode("utf-8"),
        payload_b64.encode("ascii"),
        hashlib.sha256,
    ).digest()

    actual_signature = _b64d(signature_b64)
    if not hmac.compare_digest(actual_signature, expected_signature):
        raise ValueError("invalid_signature")

    payload = json.loads(_b64d(payload_b64).decode("utf-8"))

    if int(payload.get("exp", 0)) < int(time.time()):
        raise ValueError("token_expired")

    return int(payload["uid"])


def get_current_user(
    credentials: Optional[HTTPAuthorizationCredentials] = Depends(bearer_scheme),
) -> dict:
    if credentials is None:
        raise HTTPException(status_code=401, detail="missing_token")

    try:
        user_id = decode_token(credentials.credentials)
    except ValueError:
        raise HTTPException(status_code=401, detail="invalid_or_expired_token")

    user = db.get_user_by_id(user_id)
    if user is None:
        raise HTTPException(status_code=401, detail="user_not_found")

    return user


def get_user_by_ws_token(token: str) -> Optional[dict]:
    try:
        user_id = decode_token(token)
    except ValueError:
        return None

    return db.get_user_by_id(user_id)