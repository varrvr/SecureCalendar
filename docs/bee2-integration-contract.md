# Bee2 Android integration contract

The Android application currently has no production PSI engine wired to Bee2. `PsiEngine` is a protocol boundary only; callers must not report a successful comparison until a real implementation completes the protocol.

## Android operations required

The eventual bridge needs operations for key-pair creation and public-key export, signature creation and verification, scalar validation/generation, point validation, point multiplication, and the agreed PSI element transform. Private key material must remain in native memory where practical and must never be returned to UI or serialized into application logs or Room.

## Data and errors

Inputs and outputs are byte arrays with explicit, versioned encodings. The bridge must return typed failures for malformed scalar/point, invalid signature, unsupported encoding/version, native initialization failure, and internal errors. Android must validate input sizes before JNI calls and treat all JNI output as untrusted until checked.

## PSI call sequence

The engine receives a session ID, authenticated peer ID, role, UTC period bounds, and locally normalized slot identifiers. It emits opaque protocol messages to the server relay and consumes messages bound to the same session and peer. The server sees routing metadata and opaque payloads only. Cancellation, timeout, duplicate/out-of-order messages, disconnects, and session rejection must produce non-success terminal states.

No meeting title, owner calendar row, or plaintext interval list may be included in relay payloads. A result can be produced only after both protocol sides finish and all transcript/authentication checks pass.

## Encoding compatibility

Before implementation is accepted, both developers must agree on curve/parameter identifiers, byte order, point and scalar encodings, signature format, hash-to-curve domain separation, slot encoding, and protocol version. The Android/JNI and peer implementation must exchange fixed test vectors for each primitive and complete PSI transcripts. Cross-language vectors must include invalid and boundary encodings as well as valid values.

## Test vectors and acceptance

Required vectors: deterministic scalar-to-public-key, point validation, scalar-point multiplication, sign/verify, invalid point/scalar/signature cases, canonical UTC slot encoding (including negative epoch slots), and at least one two-party PSI transcript with known intersection and one with an empty intersection. Production Android tests must call the real native bridge on supported ABIs; no software stand-in may return synthetic intersections.

## Coordination items

The Bee2 developer must provide the supported ABIs, native library name and load lifecycle, operation signatures, ownership/zeroization guarantees, maximum input sizes, supported parameter set, encoding rules, and the above test vectors before Android wiring is considered complete.
