"""OPAQUE server adapter for the pinned ``opaque-snake`` package.

The package uses typed wire messages. Keep its ephemeral login state in
memory, but persist the OPAQUE server setup and each user's password file.
"""

import base64
import hashlib
import os
import secrets

from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from opaque_snake import (
    CredentialFinalization,
    CredentialRequest,
    OpaqueServer,
    PasswordFile,
    RegistrationRequest,
    RegistrationUpload,
)

from . import database as db

_SERVER_SETUP_KEY = "opaque_server_setup_p256_v1"
_SETUP_ENVELOPE_MAGIC = b"SCOS1"
OPAQUE_CONTEXT = b"com.example.calendar/opaque/v1"
OPAQUE_SERVER_IDENTIFIER = b"secure-calendar-python-server/v1"
_server: OpaqueServer | None = None


def get_server() -> OpaqueServer:
    global _server
    if _server is None:
        encrypted_setup = db.get_setting(_SERVER_SETUP_KEY)
        if encrypted_setup is None:
            generated = OpaqueServer()
            encrypted_setup = db.set_setting_if_absent(
                _SERVER_SETUP_KEY, _encrypt_setup(generated.export_setup())
            )
        setup_bytes = _decrypt_setup(encrypted_setup)
        _server = OpaqueServer(setup_bytes)
    return _server


def _setup_key() -> bytes:
    configured = os.getenv("OPAQUE_SETUP_ENCRYPTION_KEY")
    if configured:
        try:
            key = base64.urlsafe_b64decode(configured + "=" * (-len(configured) % 4))
        except Exception as exc:
            raise RuntimeError("invalid_opaque_setup_encryption_key") from exc
        if len(key) != 32:
            raise RuntimeError("opaque_setup_encryption_key_must_be_32_bytes")
        return key
    secret = os.getenv("SECRET_KEY", "dev-only-change-me").encode("utf-8")
    return hashlib.sha256(b"secure-calendar/opaque-setup/v1\0" + secret).digest()


def _encrypt_setup(setup: bytes) -> bytes:
    nonce = secrets.token_bytes(12)
    encrypted = AESGCM(_setup_key()).encrypt(nonce, setup, _SETUP_ENVELOPE_MAGIC)
    return _SETUP_ENVELOPE_MAGIC + nonce + encrypted


def _decrypt_setup(value: bytes) -> bytes:
    if not value.startswith(_SETUP_ENVELOPE_MAGIC) or len(value) < len(_SETUP_ENVELOPE_MAGIC) + 12 + 16:
        raise RuntimeError("opaque_server_setup_record_invalid")
    offset = len(_SETUP_ENVELOPE_MAGIC)
    nonce = value[offset : offset + 12]
    ciphertext = value[offset + 12 :]
    try:
        return AESGCM(_setup_key()).decrypt(nonce, ciphertext, _SETUP_ENVELOPE_MAGIC)
    except Exception as exc:
        raise RuntimeError("opaque_server_setup_decryption_failed_check_secret_key") from exc


def registration_start(username: str, request_bytes: bytes) -> bytes:
    request = RegistrationRequest.from_bytes(request_bytes)
    response = get_server().create_registration_response(request, username)
    return response.to_bytes()


def registration_finish(upload_bytes: bytes) -> bytes:
    upload = RegistrationUpload.from_bytes(upload_bytes)
    password_file = get_server().finish_registration(upload)
    return password_file.to_bytes()


def login_start(
    username: str,
    request_bytes: bytes,
    password_file_bytes: bytes,
):
    request = CredentialRequest.from_bytes(request_bytes)
    password_file = PasswordFile.from_bytes(password_file_bytes)
    response, state = get_server().create_credential_response(
        request,
        username,
        password_file,
        context=OPAQUE_CONTEXT,
        client_identifier=username.encode("utf-8"),
        server_identifier=OPAQUE_SERVER_IDENTIFIER,
    )
    return response.to_bytes(), state


def login_finish(finalization_bytes: bytes, server_state) -> None:
    finalization = CredentialFinalization.from_bytes(finalization_bytes)
    # finish_login authenticates the client's finalization and raises on failure.
    # Session keys are deliberately not retained by the mediator server.
    get_server().finish_login(finalization, server_state)
