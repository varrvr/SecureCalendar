"""Tests for the opaque-snake library."""

import pytest


class TestRegistrationFlow:
    """Test the complete registration flow."""

    def test_registration_success(self):
        """Test complete registration flow."""
        from opaque_snake import OpaqueServer, OpaqueClient

        server = OpaqueServer()
        client = OpaqueClient()

        # Step 1: Client starts registration
        request, client_state = client.start_registration("password123")
        assert request is not None
        assert client_state is not None

        # Step 2: Server responds
        response = server.create_registration_response(request, "testuser")
        assert response is not None

        # Step 3: Client finishes
        result = client.finish_registration(response, client_state, "password123")
        assert result is not None
        assert result.upload is not None
        assert len(result.export_key) == 64

        # Step 4: Server stores
        password_file = server.finish_registration(result.upload)
        assert password_file is not None

    def test_registration_serialization_roundtrip(self):
        """Test that messages survive serialization."""
        from opaque_snake import OpaqueServer, OpaqueClient, RegistrationRequest

        server = OpaqueServer()
        client = OpaqueClient()

        request, state = client.start_registration("password123")

        # Serialize and deserialize
        request_bytes = request.to_bytes()
        assert len(request_bytes) > 0

        restored = RegistrationRequest.from_bytes(request_bytes)

        # Should still work with restored request
        response = server.create_registration_response(restored, "testuser")
        assert response is not None


class TestLoginFlow:
    """Test the complete login flow."""

    def test_login_success(self):
        """Test successful login after registration."""
        from opaque_snake import OpaqueServer, OpaqueClient

        server = OpaqueServer()
        client = OpaqueClient()

        # First register
        req, state = client.start_registration("correct_password")
        resp = server.create_registration_response(req, "user1")
        result = client.finish_registration(resp, state, "correct_password")
        password_file = server.finish_registration(result.upload)

        # Now login
        login_req, login_state = client.start_login("correct_password")
        login_resp, server_state = server.create_credential_response(
            login_req, "user1", password_file
        )
        login_result = client.finish_login(login_resp, login_state, "correct_password")
        server_keys = server.finish_login(login_result.finalization, server_state)

        # Keys should match
        assert login_result.session_keys.session_key == server_keys.session_key
        assert len(login_result.session_keys.session_key) == 64

    def test_login_wrong_password(self):
        """Test that wrong password raises AuthenticationError."""
        from opaque_snake import OpaqueServer, OpaqueClient, AuthenticationError

        server = OpaqueServer()
        client = OpaqueClient()

        # Register
        req, state = client.start_registration("correct_password")
        resp = server.create_registration_response(req, "user1")
        result = client.finish_registration(resp, state, "correct_password")
        password_file = server.finish_registration(result.upload)

        # Try login with wrong password
        login_req, login_state = client.start_login("wrong_password")
        login_resp, server_state = server.create_credential_response(
            login_req, "user1", password_file
        )

        with pytest.raises(AuthenticationError):
            client.finish_login(login_resp, login_state, "wrong_password")


class TestServerPersistence:
    """Test server setup persistence."""

    def test_server_setup_roundtrip(self):
        """Test that server setup can be serialized and restored."""
        from opaque_snake import OpaqueServer, OpaqueClient

        # Create server and register a user
        server1 = OpaqueServer()
        setup_bytes = server1.export_setup()
        assert len(setup_bytes) > 0

        client = OpaqueClient()
        req, state = client.start_registration("password")
        resp = server1.create_registration_response(req, "user")
        result = client.finish_registration(resp, state, "password")
        password_file = server1.finish_registration(result.upload)

        # Create new server from saved setup
        server2 = OpaqueServer(setup_bytes)

        # Login should work with the restored server
        login_req, login_state = client.start_login("password")
        login_resp, server_state = server2.create_credential_response(
            login_req, "user", password_file
        )
        login_result = client.finish_login(login_resp, login_state, "password")
        server_keys = server2.finish_login(login_result.finalization, server_state)

        assert login_result.session_keys.session_key == server_keys.session_key


class TestPasswordFilePersistence:
    """Test password file persistence."""

    def test_password_file_roundtrip(self):
        """Test that password file can be serialized and restored."""
        from opaque_snake import OpaqueServer, OpaqueClient, PasswordFile

        server = OpaqueServer()
        client = OpaqueClient()

        # Register
        req, state = client.start_registration("password")
        resp = server.create_registration_response(req, "user")
        result = client.finish_registration(resp, state, "password")
        password_file = server.finish_registration(result.upload)

        # Serialize and restore
        pf_bytes = password_file.to_bytes()
        assert len(pf_bytes) > 0

        restored_pf = PasswordFile.from_bytes(pf_bytes)

        # Login should work with restored password file
        login_req, login_state = client.start_login("password")
        login_resp, server_state = server.create_credential_response(
            login_req, "user", restored_pf
        )
        login_result = client.finish_login(login_resp, login_state, "password")
        server_keys = server.finish_login(login_result.finalization, server_state)

        assert login_result.session_keys.session_key == server_keys.session_key


class TestErrors:
    """Test error handling."""

    def test_deserialize_invalid_data(self):
        """Test that invalid data raises SerializationError."""
        from opaque_snake import RegistrationRequest, SerializationError

        with pytest.raises(SerializationError):
            RegistrationRequest.from_bytes(b"invalid data")

    def test_deserialize_empty_data(self):
        """Test that empty data raises SerializationError."""
        from opaque_snake import RegistrationRequest, SerializationError

        with pytest.raises(SerializationError):
            RegistrationRequest.from_bytes(b"")

    def test_state_reuse_raises_error(self):
        """Test that reusing state raises ProtocolError."""
        from opaque_snake import OpaqueServer, OpaqueClient, ProtocolError

        server = OpaqueServer()
        client = OpaqueClient()

        # Start registration
        req, state = client.start_registration("password")
        resp = server.create_registration_response(req, "user")

        # First use should succeed
        result = client.finish_registration(resp, state, "password")
        assert result is not None

        # Second use should fail
        resp2 = server.create_registration_response(req, "user2")
        with pytest.raises(ProtocolError):
            client.finish_registration(resp2, state, "password")


class TestExportKey:
    """Test export key functionality."""

    def test_export_key_available(self):
        """Test that export key is available after registration and login."""
        from opaque_snake import OpaqueServer, OpaqueClient

        server = OpaqueServer()
        client = OpaqueClient()

        # Registration export key
        req, state = client.start_registration("password")
        resp = server.create_registration_response(req, "user")
        result = client.finish_registration(resp, state, "password")
        password_file = server.finish_registration(result.upload)

        registration_export_key = result.export_key
        assert len(registration_export_key) == 64

        # Login export key
        login_req, login_state = client.start_login("password")
        login_resp, server_state = server.create_credential_response(
            login_req, "user", password_file
        )
        login_result = client.finish_login(login_resp, login_state, "password")

        login_export_key = login_result.session_keys.export_key
        assert login_export_key is not None
        assert len(login_export_key) == 64

        # Export keys should be the same (deterministic from password)
        assert registration_export_key == login_export_key
