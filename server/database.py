import os
import sqlite3
import time
from contextlib import closing
from typing import Optional

DB_PATH = os.getenv(
    "DB_PATH",
    os.path.join(os.path.dirname(__file__), "secure_calendar.db"),
)


def get_conn() -> sqlite3.Connection:
    conn = sqlite3.connect(DB_PATH, check_same_thread=False)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA foreign_keys = ON")
    return conn


def init_db() -> None:
    with closing(get_conn()) as conn:
        conn.executescript(
            """
            CREATE TABLE IF NOT EXISTS users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                username TEXT NOT NULL UNIQUE,
                display_name TEXT NOT NULL,
                public_key TEXT NOT NULL,
                password_file BLOB,
                created_at INTEGER NOT NULL
            );

            CREATE TABLE IF NOT EXISTS sessions (
                session_id TEXT PRIMARY KEY,
                initiator_id INTEGER NOT NULL,
                responder_id INTEGER NOT NULL,
                status TEXT NOT NULL DEFAULT 'pending'
                    CHECK(status IN (
                        'pending',
                        'accepted',
                        'rejected',
                        'active',
                        'completed',
                        'cancelled'
                    )),
                period_start INTEGER,
                period_end INTEGER,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                FOREIGN KEY(initiator_id) REFERENCES users(id),
                FOREIGN KEY(responder_id) REFERENCES users(id)
            );

            CREATE INDEX IF NOT EXISTS idx_users_username
                ON users(username);

            CREATE INDEX IF NOT EXISTS idx_sessions_initiator
                ON sessions(initiator_id);

            CREATE INDEX IF NOT EXISTS idx_sessions_responder
                ON sessions(responder_id);
            """
        )
        conn.commit()


def create_user_pending(
    username: str,
    display_name: str,
    public_key: str,
) -> dict:
    """
    Создаёт пользователя без password_file.
    Завершение регистрации OPAQUE заполнит password_file позже.
    """
    now = int(time.time())
    try:
        with closing(get_conn()) as conn:
            cur = conn.execute(
                """
                INSERT INTO users(
                    username,
                    display_name,
                    public_key,
                    password_file,
                    created_at
                )
                VALUES (?, ?, ?, NULL, ?)
                """,
                (username, display_name, public_key, now),
            )
            conn.commit()
            user_id = cur.lastrowid
    except sqlite3.IntegrityError as exc:
        raise ValueError("username_taken") from exc

    user = get_user_by_id(user_id)
    if user is None:
        raise RuntimeError("failed_to_create_user")
    return user


def set_password_file(user_id: int, password_file: bytes) -> None:
    with closing(get_conn()) as conn:
        conn.execute(
            "UPDATE users SET password_file = ? WHERE id = ?",
            (password_file, user_id),
        )
        conn.commit()


def get_user_by_id(user_id: int) -> Optional[dict]:
    with closing(get_conn()) as conn:
        row = conn.execute(
            "SELECT * FROM users WHERE id = ?",
            (user_id,),
        ).fetchone()
        return dict(row) if row else None


def get_user_by_username(username: str) -> Optional[dict]:
    with closing(get_conn()) as conn:
        row = conn.execute(
            "SELECT * FROM users WHERE username = ?",
            (username,),
        ).fetchone()
        return dict(row) if row else None


def search_users(query: str, exclude_id: int, limit: int = 50) -> list[dict]:
    q = f"%{query.strip()}%"
    with closing(get_conn()) as conn:
        rows = conn.execute(
            """
            SELECT id, username, display_name, public_key, created_at
            FROM users
            WHERE id != ?
              AND password_file IS NOT NULL
              AND (username LIKE ? OR display_name LIKE ?)
            ORDER BY username
            LIMIT ?
            """,
            (exclude_id, q, q, limit),
        ).fetchall()
        return [dict(row) for row in rows]


# --- Всё, что ниже, без изменений относительно предыдущей версии ---

def create_session(
    session_id: str,
    initiator_id: int,
    responder_id: int,
    period_start: Optional[int] = None,
    period_end: Optional[int] = None,
) -> dict:
    now = int(time.time())
    with closing(get_conn()) as conn:
        conn.execute(
            """
            INSERT INTO sessions(
                session_id,
                initiator_id,
                responder_id,
                status,
                period_start,
                period_end,
                created_at,
                updated_at
            )
            VALUES (?, ?, ?, 'pending', ?, ?, ?, ?)
            """,
            (
                session_id,
                initiator_id,
                responder_id,
                period_start,
                period_end,
                now,
                now,
            ),
        )
        conn.commit()

    session = get_session(session_id)
    if session is None:
        raise RuntimeError("failed_to_create_session")
    return session


def get_session(session_id: str) -> Optional[dict]:
    with closing(get_conn()) as conn:
        row = conn.execute(
            "SELECT * FROM sessions WHERE session_id = ?",
            (session_id,),
        ).fetchone()
        return dict(row) if row else None


def update_session_status(session_id: str, status: str) -> bool:
    now = int(time.time())
    with closing(get_conn()) as conn:
        cur = conn.execute(
            """
            UPDATE sessions
            SET status = ?, updated_at = ?
            WHERE session_id = ?
            """,
            (status, now, session_id),
        )
        conn.commit()
        return cur.rowcount > 0


def list_sessions_for_user(user_id: int) -> list[dict]:
    with closing(get_conn()) as conn:
        rows = conn.execute(
            """
            SELECT *
            FROM sessions
            WHERE initiator_id = ? OR responder_id = ?
            ORDER BY created_at DESC
            """,
            (user_id, user_id),
        ).fetchall()
        return [dict(row) for row in rows]