# opaque-snake-p256 (Secure Calendar fork)

Python bindings for the OPAQUE password-authenticated key exchange protocol, configured to match the Android UniFFI AAR.

OPAQUE is an asymmetric password-authenticated key exchange (aPAKE) protocol that enables secure password-based authentication without exposing plaintext credentials to the server. This fork wraps Rust `opaque-ke = 4.0.1` with P-256 OPRF, TripleDH over P-256/SHA-256, and Identity KSF.

The server's `create_credential_response` additionally accepts context, client identifier, and server identifier. Its one-shot in-memory login state retains these values so `finish_login` authenticates using the same parameters. Do not change them independently from Android; rerun the cross-platform registration/login tests after protocol configuration changes.

## Quick Start

### Registration

```python
from opaque_snake import OpaqueServer, OpaqueClient

# Server setup (do this once, persist the setup)
server = OpaqueServer()
setup_bytes = server.export_setup()  # Save this securely

# Client registration
client = OpaqueClient()
request, state = client.start_registration("password123")

# Server processes registration
response = server.create_registration_response(request, "user@example.com")

# Client completes registration
result = client.finish_registration(response, state, "password123")

# Client gets an export key for client-side encryption
export_key = result.export_key  # 64 bytes, derived from password

# Server stores the password file
password_file = server.finish_registration(result.upload)
# Save password_file.to_bytes() in your database
```

### Login

```python
from opaque_snake import OpaqueServer, OpaqueClient, PasswordFile

# Restore server from saved setup
server = OpaqueServer(saved_setup_bytes)

# Client starts login
client = OpaqueClient()
request, state = client.start_login("password123")

# Server processes login (load password file from database)
password_file = PasswordFile.from_bytes(saved_password_file_bytes)
response, server_state = server.create_credential_response(
    request, "user@example.com", password_file
)

# Client completes login
result = client.finish_login(response, state, "password123")

# Server verifies and gets session keys
server_keys = server.finish_login(result.finalization, server_state)

# Both parties now have matching session keys
assert result.session_keys.session_key == server_keys.session_key

# Client can also retrieve the export key (same as during registration)
export_key = result.session_keys.export_key  # 64 bytes
```

### Export Keys

Export keys are derived deterministically from the user's password and can be used for client-side encryption. The server never sees this key.

```python
# During registration, save encrypted data using the export key
from opaque_snake import OpaqueServer, OpaqueClient

server = OpaqueServer()
client = OpaqueClient()

# Registration
req, state = client.start_registration("password123")
resp = server.create_registration_response(req, "user@example.com")
result = client.finish_registration(resp, state, "password123")
password_file = server.finish_registration(result.upload)

# Use export_key to encrypt user data client-side
export_key = result.export_key  # 64 bytes
# encrypted_data = encrypt(user_secret_data, export_key)
# Store encrypted_data on server - server cannot decrypt it

# During login, recover the same export key
req, state = client.start_login("password123")
resp, server_state = server.create_credential_response(req, "user@example.com", password_file)
result = client.finish_login(resp, state, "password123")
server.finish_login(result.finalization, server_state)

# Same export key is recovered
export_key = result.session_keys.export_key
# user_secret_data = decrypt(encrypted_data, export_key)
```

## Features

- Secure password-authenticated key exchange (RFC 9807)
- No password transmitted to server - uses OPRF
- Resistant to pre-computation attacks
- Export keys for additional key derivation
- Session keys for encrypted communication

## License

MIT OR Apache-2.0
