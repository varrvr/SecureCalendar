"""Actual Uvicorn sockets and process restart with isolated persistent SQLite."""
import os
from pathlib import Path
import socket
import subprocess
import sys
import time
import httpx
from websockets.sync.client import connect
from test_protocol_integration import register, login, headers, create


def test_live_two_user_exchange_and_process_restart(tmp_path):
    root = Path(__file__).resolve().parents[2]
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        port = sock.getsockname()[1]
    env = dict(os.environ, DB_PATH=str(tmp_path / "persistent.sqlite3"), SECRET_KEY="isolated-integration-test-secret")
    url = f"http://127.0.0.1:{port}"
    def start():
        process = subprocess.Popen([sys.executable, "-m", "uvicorn", "server.main:app", "--host", "127.0.0.1", "--port", str(port), "--no-access-log"],
            cwd=root, env=env, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        for _ in range(100):
            if process.poll() is not None:
                raise AssertionError("Uvicorn failed to start")
            try:
                if httpx.get(url + "/health", timeout=0.2).status_code == 200:
                    return process
            except httpx.HTTPError:
                pass
            time.sleep(0.05)
        process.terminate(); process.wait(timeout=5)
        raise AssertionError("health check timed out")
    process = start()
    try:
        with httpx.Client(base_url=url, timeout=5) as client:
            alice, pa = register(client, "alice")
            bob, pb = register(client, "bob")
            alice, _ = login(client, "alice", pa)
            bob, _ = login(client, "bob", pb)
            with connect(f"ws://127.0.0.1:{port}/ws?token=" + alice["access_token"]) as wa, connect(f"ws://127.0.0.1:{port}/ws?token=" + bob["access_token"]) as wb:
                assert 'connected' in wa.recv(timeout=5)
                assert 'connected' in wb.recv(timeout=5)
                sid = create(client, alice, bob)
                assert 'session_request' in wb.recv(timeout=5)
                assert client.post(f"/sessions/{sid}/accept", headers=headers(bob)).status_code == 200
                assert 'session_accepted' in wa.recv(timeout=5)
        process.terminate(); process.wait(timeout=5)
        process = start()
        with httpx.Client(base_url=url, timeout=5) as client:
            restored, _ = login(client, "alice", pa)
            assert restored["user"]["id"] == alice["user"]["id"]
            assert client.get(f"/sessions/{sid}", headers=headers(restored)).json()["status"] == "accepted"
    finally:
        process.terminate(); process.wait(timeout=5)
