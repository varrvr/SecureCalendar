"""
opaque-snake: Pythonic OPAQUE password-authenticated key exchange.

This library provides Python bindings for the OPAQUE protocol (RFC 9807),
enabling secure password-based authentication without exposing passwords
to the server.

Example usage:
    >>> from opaque_snake import OpaqueServer, OpaqueClient
    >>>
    >>> # Server setup (do this once, persist the setup)
    >>> server = OpaqueServer()
    >>>
    >>> # Client registration
    >>> client = OpaqueClient()
    >>> request, state = client.start_registration("password123")
    >>> response = server.create_registration_response(request, "user@example.com")
    >>> result = client.finish_registration(response, state, "password123")
    >>> password_file = server.finish_registration(result.upload)
    >>>
    >>> # Store password_file.to_bytes() in your database
    >>>
    >>> # Client login
    >>> request, state = client.start_login("password123")
    >>> response, server_state = server.create_credential_response(
    ...     request, "user@example.com", password_file
    ... )
    >>> result = client.finish_login(response, state, "password123")
    >>> keys = server.finish_login(result.finalization, server_state)
    >>>
    >>> # Both client and server now have matching session keys
    >>> assert result.session_keys.session_key == keys.session_key
"""

from opaque_snake._core import (
    # Core classes
    OpaqueServer,
    OpaqueClient,
    # Message types
    RegistrationRequest,
    RegistrationResponse,
    RegistrationUpload,
    CredentialRequest,
    CredentialResponse,
    CredentialFinalization,
    # Result and state types
    PasswordFile,
    SessionKeys,
    RegistrationResult,
    LoginResult,
    ClientRegistrationState,
    ClientLoginState,
    ServerLoginState,
    # Exceptions
    OpaqueError,
    ProtocolError,
    AuthenticationError,
    SerializationError,
)

__all__ = [
    # Core classes
    "OpaqueServer",
    "OpaqueClient",
    # Message types
    "RegistrationRequest",
    "RegistrationResponse",
    "RegistrationUpload",
    "CredentialRequest",
    "CredentialResponse",
    "CredentialFinalization",
    # Result and state types
    "PasswordFile",
    "SessionKeys",
    "RegistrationResult",
    "LoginResult",
    "ClientRegistrationState",
    "ClientLoginState",
    "ServerLoginState",
    # Exceptions
    "OpaqueError",
    "ProtocolError",
    "AuthenticationError",
    "SerializationError",
]

__version__ = "0.1.0"
