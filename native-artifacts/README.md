# Supplied native artifact

`unverified/armeabi-v7a/libbee2bridge.so` preserves the exact 32-bit ARM binary previously placed under `app/src/main/jniLibs/arm64-v8a`. It is not packaged: its ELF class/machine are ARM32, and it does not expose the seven expected Bee2Provider JNI exports.

The packaged arm64 JNI bridge is restored from repository commit `82c163d`, SHA-256 `c97cf35187e879c2cfe72bedcc4b2761173e570d03a63d61754cddcc6e164c29`. No cryptographic implementation was substituted. Matching native sources and full PSI primitives are still required.
