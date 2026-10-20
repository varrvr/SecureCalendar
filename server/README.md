# Python mediator

Install from the repository root. The local Rust binding is part of this repo because the published `opaque-snake` wheel uses a different OPAQUE suite.

```sh
python3 -m venv .venv
. .venv/bin/activate
pip install -r server/requirements.txt
SECRET_KEY='a-long-random-secret' DB_PATH='./server/secure_calendar.db' \
  uvicorn server.main:app --host 0.0.0.0 --port 8000
```

`SECRET_KEY` must remain stable between restarts. It signs access tokens and, unless `OPAQUE_SETUP_ENCRYPTION_KEY` is provided, derives the AES-GCM key that protects the OPAQUE server setup stored in SQLite. For a dedicated setup key, set `OPAQUE_SETUP_ENCRYPTION_KEY` to URL-safe Base64 of exactly 32 random bytes. Store that key outside the database and keep it stable; losing or changing it makes the existing OPAQUE setup unreadable. The SQLite database and newly created DB file are restricted to owner read/write permissions. Backups must receive equivalent protection.

The Rust binding pins `opaque-ke = 4.0.1` and the same suite as the Android AAR: P-256 OPRF, TripleDH over P-256 with SHA-256, and Identity KSF. Android and server use the following byte strings:

- client identifier: normalized lowercase username encoded as UTF-8;
- server identifier: `secure-calendar-python-server/v1`;
- login context: `com.example.calendar/opaque/v1`.

These values are protocol configuration. Changing them requires a coordinated client/server version change, and existing users must re-register if the OPAQUE suite changes.

For Android emulator development, the debug build targets `http://10.0.2.2:8000`. Production builds use `-PsecureCalendarServerUrl=https://your-host`. Plain HTTP and WS are enabled only for the debug build. Production endpoints must use HTTPS and WSS.

The WebSocket server forwards the documented JSON envelope and Base64 payloads. It does not parse or transform PSI payload contents. The repository currently has no real PSI engine, so the app must never send calendar slots or claim an intersection result.
