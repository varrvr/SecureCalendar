"""Real OPAQUE + SQLite + HTTP + WebSocket. No PSI result is simulated."""
import base64
import os
import time

import pytest
from fastapi.testclient import TestClient
from opaque_snake import OpaqueClient, RegistrationResponse, CredentialResponse
from server import database as db, opaque_auth, main, auth
from server.websocket import manager, psi_limiter


def b64(value):
    return base64.b64encode(value).decode("ascii")


def identities(username):
    return {"client_identifier": username.encode(), "server_identifier": opaque_auth.OPAQUE_SERVER_IDENTIFIER}


@pytest.fixture
def client(tmp_path, monkeypatch):
    monkeypatch.setattr(db, "DB_PATH", str(tmp_path / "test.sqlite3"))
    opaque_auth._server = None
    main._login_states.clear()
    manager.active.clear()
    psi_limiter._events.clear()
    with TestClient(main.app) as test:
        yield test
    opaque_auth._server = None


def register(client, username):
    password = "test-only-password-" + username
    opaque = OpaqueClient()
    request, state = opaque.start_registration(password)
    response = client.post("/auth/register/start", json={
        "username": username, "display_name": username.title(),
        "public_key": "transport-test-key-not-a-PSI-key", "registration_request": b64(request.to_bytes())})
    assert response.status_code == 200, response.status_code
    challenge = response.json()
    result = opaque.finish_registration(RegistrationResponse.from_bytes(base64.b64decode(challenge["registration_response"])),
                                        state, password, **identities(username))
    response = client.post("/auth/register/finish", json={"username": username,
        "registration_state_id": challenge["registration_state_id"], "registration_upload": b64(result.upload.to_bytes())})
    assert response.status_code == 200
    return response.json(), password


def login(client, username, password):
    opaque = OpaqueClient()
    request, state = opaque.start_login(password)
    response = client.post("/auth/login/start", json={"username": username, "credential_request": b64(request.to_bytes())})
    assert response.status_code == 200
    challenge = response.json()
    result = opaque.finish_login(CredentialResponse.from_bytes(base64.b64decode(challenge["credential_response"])),
        state, password, context=opaque_auth.OPAQUE_CONTEXT, **identities(username))
    body = {"username": username, "login_state_id": challenge["login_state_id"],
            "credential_finalization": b64(result.finalization.to_bytes())}
    response = client.post("/auth/login/finish", json=body)
    assert response.status_code == 200
    return response.json(), body


def headers(user):
    return {"Authorization": "Bearer " + user["access_token"]}


def create(client, alice, bob):
    response = client.post("/sessions", headers=headers(alice), json={
        "responder_id": bob["user"]["id"], "period_start": 1795132800, "period_end": 1795219200})
    assert response.status_code == 200
    return response.json()["session_id"]


def test_opaque_authentication_restart_and_invalid_inputs(client):
    alice, password = register(client, "alice")
    authenticated, finalization = login(client, "alice", password)
    assert client.get("/users/me", headers=headers(authenticated)).json()["username"] == "alice"
    assert client.post("/auth/login/finish", json=finalization).status_code == 401
    with pytest.raises(Exception):
        login(client, "alice", "wrong-password")
    opaque_auth._server = None
    db.init_db()
    assert db.get_user_by_username("alice")["password_file"]
    restarted, _ = login(client, "alice", password)
    assert restarted["user"]["id"] == alice["user"]["id"]
    request, _ = OpaqueClient().start_registration(password)
    assert client.post("/auth/register/start", json={"username": "alice", "display_name": "x", "public_key": "key",
        "registration_request": b64(request.to_bytes())}).status_code == 409
    for token in ["broken", "a.b", "e30.invalid"]:
        assert client.get("/users/me", headers={"Authorization": "Bearer " + token}).status_code == 401
    assert client.get("/users").status_code == 401
    assert client.get("/health").json() == {"status": "ok"}
    assert client.get("/openapi.json").status_code == 200


def test_registration_finish_is_bound_to_start(client):
    request, state = OpaqueClient().start_registration("password")
    response = client.post("/auth/register/start", json={"username": "pending", "display_name": "Pending", "public_key": "key",
        "registration_request": b64(request.to_bytes())}).json()
    result = OpaqueClient().finish_registration(RegistrationResponse.from_bytes(base64.b64decode(response["registration_response"])),
        state, "password", **identities("pending"))
    body = {"username": "pending", "registration_upload": b64(result.upload.to_bytes()), "registration_state_id": "x" * 43}
    assert client.post("/auth/register/finish", json=body).status_code == 401
    assert db.get_user_by_username("pending")["password_file"] is None
    body["registration_state_id"] = response["registration_state_id"]
    assert client.post("/auth/register/finish", json=body).status_code == 200
    assert client.post("/auth/register/finish", json=body).status_code == 409


def test_two_users_request_accept_relay_replay_and_foreign_session(client):
    alice, pa = register(client, "alice")
    bob, pb = register(client, "bob")
    mallory, _ = register(client, "mallory")
    alice, _ = login(client, "alice", pa)
    bob, _ = login(client, "bob", pb)
    assert client.get("/users?query=bob", headers=headers(alice)).json()[0]["id"] == bob["user"]["id"]
    assert client.get("/users?query=absent", headers=headers(alice)).json() == []
    with client.websocket_connect("/ws?token=" + alice["access_token"]) as wa, client.websocket_connect("/ws?token=" + bob["access_token"]) as wb:
        assert wa.receive_json()["type"] == wb.receive_json()["type"] == "connected"
        sid = create(client, alice, bob)
        assert wb.receive_json()["type"] == "session_request"
        assert client.post("/sessions", headers=headers(alice), json={"responder_id": bob["user"]["id"],
            "period_start": 1795132800, "period_end": 1795219200}).status_code == 409
        assert client.post(f"/sessions/{sid}/accept", headers=headers(alice)).status_code == 403
        assert client.get(f"/sessions/{sid}", headers=headers(mallory)).status_code == 403
        assert client.post(f"/sessions/{sid}/accept", headers=headers(bob)).status_code == 200
        assert wa.receive_json()["type"] == "session_accepted"
        assert client.post(f"/sessions/{sid}/accept", headers=headers(bob)).status_code == 409
        message = {"type": "psi_transport_test", "session_id": sid, "recipient_id": bob["user"]["id"],
                   "sender_id": mallory["user"]["id"], "payload": b64(b"opaque transport bytes, not PSI")}
        wa.send_json(dict(message, type="session_accepted"))
        assert wa.receive_json()["code"] == "unsupported_type"
        wa.send_json(dict(message, session_id="unknown"))
        assert wa.receive_json()["code"] == "session_not_found"
        wa.send_json(dict(message, recipient_id=mallory["user"]["id"]))
        assert wa.receive_json()["code"] == "invalid_recipient"
        wa.send_json(dict(message, payload="not base64"))
        assert wa.receive_json()["code"] == "invalid_payload"
        wa.send_json(message)
        delivered = wb.receive_json()
        assert delivered["sender_id"] == alice["user"]["id"]
        assert delivered["session_id"] == sid
        wa.send_json(message)
        assert wa.receive_json()["code"] == "duplicate_message"
        with client.websocket_connect("/ws?token=" + mallory["access_token"]) as wm:
            wm.receive_json(); wm.send_json(message)
            assert wm.receive_json()["code"] == "forbidden_session"
        assert client.post(f"/sessions/{sid}/complete", headers=headers(alice)).status_code == 200
        assert wb.receive_json()["type"] == "session_completed"
        wa.send_json(dict(message, payload=b64(b"after complete")))
        assert wa.receive_json()["code"] == "session_not_active"


def test_reject_cancel_offline_delivery_and_timeout(client):
    alice, _ = register(client, "alice")
    bob, _ = register(client, "bob")
    sid = create(client, alice, bob)
    # Invitations are durable even without a WebSocket. Opaque protocol payloads are not queued offline.
    inbox = client.get("/sessions", headers=headers(bob)).json()
    assert any(s["session_id"] == sid for s in inbox)
    assert client.post(f"/sessions/{sid}/reject", headers=headers(bob)).json()["status"] == "rejected"
    sid = create(client, alice, bob)
    assert client.post(f"/sessions/{sid}/cancel", headers=headers(bob)).status_code == 403
    assert client.post(f"/sessions/{sid}/cancel", headers=headers(alice)).json()["status"] == "cancelled"
    sid = create(client, alice, bob)
    client.post(f"/sessions/{sid}/accept", headers=headers(bob))
    with client.websocket_connect("/ws?token=" + alice["access_token"]) as wa:
        wa.receive_json()
        wa.send_json({"type": "psi_test", "session_id": sid, "recipient_id": bob["user"]["id"], "payload": b64(b"transport")})
        assert wa.receive_json()["code"] == "recipient_offline"
    assert client.post(f"/sessions/{sid}/cancel", headers=headers(bob)).status_code == 200
    sid = create(client, alice, bob)
    with db.get_conn() as conn:
        conn.execute("UPDATE sessions SET created_at = ? WHERE session_id = ?", (int(time.time()) - 121, sid))
    assert client.get(f"/sessions/{sid}", headers=headers(alice)).json()["status"] == "cancelled"


def test_unauthenticated_websocket_rejected(client):
    from starlette.websockets import WebSocketDisconnect
    with pytest.raises(WebSocketDisconnect):
        with client.websocket_connect("/ws?token=invalid"):
            pass


def test_cyrillic_username_registration_login_and_search(client):
    sofia, password = register(client, "софия_ё")
    authenticated, _ = login(client, "софия_ё", password)
    assert authenticated["user"]["id"] == sofia["user"]["id"]
    assert client.get("/users/me", headers=headers(authenticated)).json()["username"] == "софия_ё"
    alice, _ = register(client, "alice")
    results = client.get("/users", headers=headers(alice), params={"query": "СОФИЯ"}).json()
    assert [user["username"] for user in results] == ["софия_ё"]


@pytest.mark.parametrize("username", [" софия", "София", "софия пробел", "софия@почта", "ab"])
def test_username_canonical_form_is_still_required(username):
    from pydantic import ValidationError
    with pytest.raises(ValidationError):
        main.RegisterStartRequest(username=username, display_name="София", public_key="key",
                                  registration_request="AA==")
