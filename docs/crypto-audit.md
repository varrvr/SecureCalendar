# Bee2 / ECDH-PSI technical audit

Audit basis: checked-in Kotlin and Python sources, OPAQUE UniFFI AAR, Bee2 ELF exports, the Python relay, and executed unit/instrumentation tests. The Bee2 C/C++ JNI source and an agreed PSI protocol specification/test vectors are not present, so properties that require those artifacts remain blocked.

| Component | Implementation | Check performed | Problems | Status |
|---|---|---|---|---|
| Kotlin Bee2 JNI declarations | `Bee2Provider` exposes BELT hash, scalar/point validation, public-key derivation, point multiplication, BIGN sign and verify | Compared Kotlin method names with exported `Java_com_example_calendar_crypto_Bee2Provider_*` symbols in the shared object | Direct callers can bypass wrapper validation; native source not checked | IMPLEMENTED_NOT_TESTED |
| Native artifact / ABI | `app/src/main/jniLibs/arm64-v8a/libbee2bridge.so` | ELF header reports AArch64; dynamic symbol table contains seven JNI exports and Bee2 symbols | No `armeabi-v7a`, `x86`, or `x86_64` library; current attached emulator is x86 | IMPLEMENTED_NOT_TESTED |
| Native memory/error handling | Native implementation is inside the checked-in `.so` | Export list only | No C/C++ source or debug source mapping available to audit JNI array access, release paths, wiping, error mapping, or bounds | BLOCKED |
| BELT hash wrapper | `Bee2Crypto.hash` calls native hash and checks 32-byte output | Wrapper now rejects unexpected output length | No real JNI execution on an arm64 device yet | IMPLEMENTED_NOT_TESTED |
| BIGN scalar generation/validation | SecureRandom rejection sampling calls native scalar validator | Wrapper checks scalar length and rejects native-invalid output | Native scalar format/range has no published project vectors | IMPLEMENTED_NOT_TESTED |
| BIGN point operations | Public derivation and multiplication call JNI and validate returned point encoding | Wrapper input/output size and point validation checks added | Subgroup/infinity/canonical-encoding behavior cannot be established from Kotlin or exports | IMPLEMENTED_NOT_TESTED |
| BIGN sign/verify | JNI sign and verify; wrapper checks key and signature lengths | Added arm64 instrumentation smoke test for valid and modified messages | Instrumentation not run on a supported ABI; no independent BIGN vectors | IMPLEMENTED_NOT_TESTED |
| Hash-to-curve | No JNI operation is exposed; Kotlin now requires an injected `HashToCurve` implementation with a domain separation tag | Checked Kotlin interfaces and native exports | Native `ecpSWU` symbol alone does not specify a complete mapping, expansion/hash suite, byte encoding, or domain separation | MISSING |
| Device key storage | BIGN private key is AES-GCM encrypted using a per-local-user Android Keystore AES key; public key/fingerprint are stored in Room v4 | Migration added; implementation compiles pending verification | Keystore key is device-bound; no export/restore. JNI-backed lifecycle tests are pending | IMPLEMENTED_NOT_TESTED |
| PSI protocol / state machine | Only message/session model and unavailable-engine exception exist | Reviewed `PsiProtocol.kt` | No agreed two-party protocol, steps, authenticated transcript, sequence rules, or real engine | MISSING |
| PSI transport envelope | Existing codec uses Python relay fields `type`, `session_id`, `recipient_id`, Base64 `payload` | Compared with `server/websocket.py` | No application protocol inside payload and no end-to-end PSI session integration | PARTIALLY_IMPLEMENTED |
| PSI opaque payload codec | `PsiRelayCodec` wraps binary payload bytes in the server's JSON/Base64 envelope | Android instrumentation codec test | Transport encoding is tested, but payload is not a PSI transcript | IMPLEMENTED_AND_TESTED |
| OPAQUE ciphersuite parity | Android AAR uses P-256/SHA-256 and Identity KSF; local Python Rust binding is pinned to opaque-ke 4.0.1 and the same suite | Checked AAR Rust source/config and Python fork; Android↔Python registration/login passed | Python wrapper changes need to stay coordinated with AAR config | IMPLEMENTED_AND_TESTED |
| OPAQUE context and identities | UTF-8 username client identity, fixed server identity and application context | Real Android instrumentation registration/login against FastAPI | Values are fixed protocol parameters and must not change independently | IMPLEMENTED_AND_TESTED |
| OPAQUE server setup persistence | Server setup is AES-GCM encrypted in SQLite `server_settings`; DB file is owner-only | Python restart round-trip and wrong-key decryption test | Deployment must preserve `SECRET_KEY` or configured `OPAQUE_SETUP_ENCRYPTION_KEY` | IMPLEMENTED_AND_TESTED |
| Server registration/login lifecycle | Pending user/password file persisted; startup no longer deletes users; login state is per-request, single-use and expires | Python tests plus Android registration/login and wrong-password e2e | Registration is server-side and independent from local account | IMPLEMENTED_AND_TESTED |
| WebSocket relay and comparison invite | Authenticated two-user connection, user search, pending invite, accept event, and opaque test payload relay | Android instrumentation against live FastAPI | Payload test is synthetic transport data; it does not test PSI | IMPLEMENTED_AND_TESTED |
| Production PSI engine | No engine exists | Source review and missing protocol spec | No real intersection can be computed or displayed | MISSING |
| Slot conversion | UTC epoch-based, 15-minute slots; period clipping/deduplication added | JVM tests cover midnight, clipping, overlap and pre-epoch | These tests establish normalization only, not PSI privacy | IMPLEMENTED_AND_TESTED |

## ABI and execution findings

The only packaged native ABI is `arm64-v8a`. The connected Android emulator reports x86, so it cannot load this library. The instrumentation test intentionally skips on unsupported ABIs. An arm64 device/emulator and Bee2 test vectors are required for a real JNI run.

## Immediate security boundary

No plaintext calendar intervals are sent to a network component by this integration. The application continues to report comparison unavailable after an invite is accepted. BIGN signatures and point operations are primitives, not an ECDH-PSI protocol.
