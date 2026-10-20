# Security limitations and threat notes

- There is no production PSI implementation in this repository. Comparison must remain unavailable; never show SUCCESS or EMPTY without a verified engine result.
- Android↔Python OPAQUE interoperability is implemented and exercised end to end. The exact suite is P-256 OPRF, TripleDH over P-256/SHA-256, Identity KSF, with username as client identity and fixed context/server identity. The local `opaque-snake-p256` Rust wrapper is project-specific and has not received an independent security audit.
- The Android OPAQUE key-stretching function is Identity. OPAQUE prevents sending the password to the server, but this configuration does not add a memory-hard password stretching cost. Use strong account passwords and keep both the database and server setup key protected.
- OPAQUE setup is AES-GCM encrypted in SQLite. The AES key is derived from `SECRET_KEY` unless `OPAQUE_SETUP_ENCRYPTION_KEY` is configured. A deployment must preserve the key; changing it makes all stored setup data unusable.
- Debug builds permit HTTP/WS for the emulator address. Release builds reject cleartext and require an HTTPS server URL supplied through `secureCalendarServerUrl`; the matching WebSocket endpoint must be WSS.
- The present Bee2 artifact is a prebuilt arm64 shared object without corresponding JNI source. Kotlin can check ABI names and method exports, but cannot establish native memory safety, constant-time behavior, implementation provenance, or correct error handling.
- Native code is only packaged for arm64-v8a. Other ABIs cannot create keys or use Bee2.
- Hash-to-curve is missing at the JNI boundary. `ecpSWU` in the ELF is not enough to define the complete protocol mapping. No `hash(data) * generator` substitute is used.
- BIGN key bytes are wrapped by AES-GCM with a device-bound Android Keystore key. Reinstall/device migration restores are intentionally disabled; loss of the Keystore key makes an existing encrypted scalar unavailable. There is no key export/recovery mechanism.
- Public-key fingerprints are identifiers for out-of-band comparison only. They do not establish trust until users verify them. No server-provided key is implicitly trusted.
- Even a future correct PSI can reveal participation, approximate set sizes/message counts, timing, and the permitted intersection output. That leakage requires product-level agreement.
- Production server trust/key pinning policy is not implemented. The server returns public Bee2 keys but the app does not yet establish that a returned key belongs to the intended user.
- `SlotConverter` canonicalizes 15-minute UTC slots but adds no cryptographic privacy.
