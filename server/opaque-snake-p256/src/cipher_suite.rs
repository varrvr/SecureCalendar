use opaque_ke::CipherSuite;

/// Matches the project's Android UniFFI OPAQUE configuration exactly.
pub struct DefaultCipherSuite;

impl CipherSuite for DefaultCipherSuite {
    type OprfCs = p256::NistP256;
    type KeyExchange = opaque_ke::TripleDh<p256::NistP256, sha2::Sha256>;
    type Ksf = opaque_ke::ksf::Identity;
}
