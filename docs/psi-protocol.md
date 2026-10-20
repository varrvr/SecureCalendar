# PSI protocol status

## Current status

The repository contains a raw WebSocket relay, a `PsiEngine` interface, and a fail-closed `PsiSessionStateMachine` lifecycle/routing guard, but no approved ECDH-PSI protocol specification or implementation. The guard checks local session transitions and relay sender/recipient/session identifiers; it does not define cryptographic rounds or validate payload meaning. Therefore the Android app does not create PSI messages, exchange calendar slots, or report successful/empty comparisons. There is no demo set intersection.

The Python relay forwards a JSON envelope with `type`, `session_id`, `recipient_id`, and `payload`, and adds `sender_id`, `recipient_id`, and server time on delivery. It checks that the authenticated account is a participant in an accepted/active server session. Payload semantics are opaque to the server. This transport envelope is not itself a PSI protocol.

## Required protocol agreement

Before implementing a real engine, both developers must agree and record:

- exact PSI construction and security goal (semi-honest or malicious security);
- curve and subgroup, scalar/point encoding and validation rules;
- hash-to-curve suite, domain separation and test vectors;
- role and round sequence, message size limits, replay handling and cancellation;
- transcript authentication/signature binding to protocol version, session, peers, direction and round;
- which party learns the intersection, whether cardinality leaks, and any padding/bucketing policy;
- canonical slot encoding and cross-language transcript vectors.

## Current Android boundary

`PsiCrypto` delegates hash-to-curve to an explicitly injected implementation. No implementation is supplied. It does not fall back to `hash(element) * G`. `SlotConverter` prepares canonical sorted UTC slot identifiers locally; callers must not serialize that set directly to the server. `PsiUnavailableException` remains the expected behavior until an engine is reviewed and tested.
