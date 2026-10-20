# Bee2 Android integration

## Available bridge

The app packages `libbee2bridge.so` for `arm64-v8a`, loaded by `Bee2Provider`. The library exports JNI methods matching Kotlin declarations for BELT hashing, BIGN scalar and point validation, public key derivation, point multiplication, signing, and signature verification. The ELF also contains Bee2 symbols, including `ecpSWU`, but the Kotlin/JNI API does not expose a complete hash-to-curve operation.

`Bee2Crypto` validates input/output lengths and returned points before returning them to callers. `AndroidBee2KeyRepository` stores the public key and fingerprint in Room. It encrypts the private scalar with AES-GCM under a non-exportable per-account key from Android Keystore and binds ciphertext with per-account AAD. The plaintext scalar buffer is cleared after key creation/signing. Android backup is disabled because the wrapping key is device-bound and cannot be restored onto another device.

## Required native artifacts from the crypto developer

- C/C++ JNI source and build instructions, or an auditable source release matching the `.so`;
- supported ABI list and reproducible build hashes;
- guarantees for JNI array bounds, release paths, output ownership, zeroization and error handling;
- parameter identifiers, canonical encodings and boundary/negative vectors;
- a complete agreed hash-to-curve API and vectors, including domain separation;
- BIGN sign/verify vectors and point/scalar/subgroup validation vectors.

The arm64 instrumentation smoke test must be run on an arm64 Android device. Current x86 emulator cannot execute this artifact.

## Minimum missing PSI JNI contract

After the parties agree the mapping suite, expose a method with the Kotlin-level shape `hashToCurve(message: ByteArray, domainSeparationTag: ByteArray): ByteArray`, returning the agreed canonical point encoding (currently the bridge wrapper expects 64 bytes). The suite must also document deterministic vectors. Before using the existing native `validatePoint` for PSI, its owner must confirm and test canonical encoding, non-identity and subgroup checks; otherwise implement a separately named `validatePsiPoint(point: ByteArray): Boolean` with those guarantees. The exported `ecpSWU` symbol by itself does not specify this method's hash/expand/domain rules.
