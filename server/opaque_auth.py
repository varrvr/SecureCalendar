import opaque_ke_py

_server_setup = None


def get_server_setup():
    global _server_setup
    if _server_setup is None:
        _server_setup = opaque_ke_py.server_setup()
    return _server_setup


def registration_start(username: str, request_bytes: bytes) -> bytes:
    setup = get_server_setup()
    # Передаем байты напрямую, как требует документация
    result = opaque_ke_py.server_registration_start(
        setup,
        request_bytes,
        username.encode("utf-8"),
    )
    return result.get_message()


def registration_finish(upload_bytes: bytes) -> bytes:
    # Передаем байты напрямую
    result = opaque_ke_py.server_registration_finish(upload_bytes)
    return result.get_password_file()


def login_start(
    username: str,
    request_bytes: bytes,
    password_file_bytes: bytes,
):
    setup = get_server_setup()
    # Передаем байты запроса и байты password_file
    result = opaque_ke_py.server_login_start(
        setup,
        password_file_bytes,
        request_bytes,
        username.encode("utf-8"),
    )
    return result.get_message(), result.get_state()


def login_finish(finalization_bytes: bytes, server_state) -> bytes:
    # Передаем байты финализации
    result = opaque_ke_py.server_login_finish(server_state, finalization_bytes)
    return result.get_session_key()