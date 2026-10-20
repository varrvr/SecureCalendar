import os
import tempfile
import unittest
from unittest.mock import patch

from server import database as db
from server import opaque_auth
from opaque_snake import OpaqueClient, PasswordFile, RegistrationResponse


class OpaqueServerRoundTripTest(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        db.DB_PATH = os.path.join(self.temp_dir.name, "calendar.sqlite3")
        opaque_auth._server = None
        db.init_db()

    def tearDown(self):
        opaque_auth._server = None
        self.temp_dir.cleanup()

    def test_registration_and_login_survive_server_instance_restart(self):
        password = "correct horse battery staple"
        client = OpaqueClient()
        client_request, client_state = client.start_registration(password)

        response = opaque_auth.registration_start(
            "alice", client_request.to_bytes()
        )
        result = client.finish_registration(
            RegistrationResponse.from_bytes(response), client_state, password
        )
        password_file = opaque_auth.registration_finish(result.upload.to_bytes())

        user_id = db.create_user_pending("alice", "Alice", "public-key-test")["id"]
        self.assertTrue(db.set_password_file(user_id, password_file))
        db.init_db()
        self.assertEqual("alice", db.get_user_by_id(user_id)["username"])

        # Restart the adapter so it reloads the exact persisted server setup.
        opaque_auth._server = None
        login_request, client_login_state = client.start_login(password)
        server = opaque_auth.get_server()
        login_response, server_login_state = server.create_credential_response(
            type(login_request).from_bytes(login_request.to_bytes()),
            "alice",
            PasswordFile.from_bytes(
                db.get_user_by_id(user_id)["password_file"]
            ),
        )
        login_result = client.finish_login(
            login_response, client_login_state, password
        )
        server.finish_login(login_result.finalization, server_login_state)

    def test_server_setup_is_encrypted_and_bound_to_secret_key(self):
        opaque_auth.get_server()
        stored = db.get_setting("opaque_server_setup_p256_v1")
        self.assertTrue(stored.startswith(b"SCOS1"))
        opaque_auth._server = None
        with patch.dict(os.environ, {"SECRET_KEY": "different secret"}):
            with self.assertRaisesRegex(RuntimeError, "decryption_failed"):
                opaque_auth.get_server()
        opaque_auth._server = None
        self.assertIsNotNone(opaque_auth.get_server())

    def test_wrong_password_is_rejected_by_client_finish(self):
        password = "correct horse battery staple"
        client = OpaqueClient()
        request, state = client.start_registration(password)
        response = opaque_auth.registration_start("bob", request.to_bytes())
        result = client.finish_registration(
            RegistrationResponse.from_bytes(response), state, password
        )
        password_file = opaque_auth.registration_finish(result.upload.to_bytes())

        request, state = client.start_login("incorrect password")
        response, _server_state = opaque_auth.get_server().create_credential_response(
            type(request).from_bytes(request.to_bytes()),
            "bob",
            PasswordFile.from_bytes(password_file),
        )
        with self.assertRaises(Exception):
            client.finish_login(
                response, state, "incorrect password"
            )


if __name__ == "__main__":
    unittest.main()
