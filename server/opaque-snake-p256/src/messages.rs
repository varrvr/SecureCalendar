use pyo3::prelude::*;
use pyo3::types::PyBytes;

use crate::cipher_suite::DefaultCipherSuite;
use crate::errors::OpaqueSnakeError;

// Type aliases for opaque-ke message types
type OpaqueRegistrationRequest =
    opaque_ke::RegistrationRequest<DefaultCipherSuite>;
type OpaqueRegistrationResponse =
    opaque_ke::RegistrationResponse<DefaultCipherSuite>;
type OpaqueRegistrationUpload =
    opaque_ke::RegistrationUpload<DefaultCipherSuite>;
type OpaqueCredentialRequest =
    opaque_ke::CredentialRequest<DefaultCipherSuite>;
type OpaqueCredentialResponse =
    opaque_ke::CredentialResponse<DefaultCipherSuite>;
type OpaqueCredentialFinalization =
    opaque_ke::CredentialFinalization<DefaultCipherSuite>;
type OpaqueServerRegistration =
    opaque_ke::ServerRegistration<DefaultCipherSuite>;

// --- Registration Messages ---

/// Client's initial registration message.
#[pyclass]
#[derive(Clone)]
pub struct RegistrationRequest {
    inner: OpaqueRegistrationRequest,
}

impl RegistrationRequest {
    pub fn new(inner: OpaqueRegistrationRequest) -> Self {
        Self { inner }
    }

    pub fn inner(&self) -> &OpaqueRegistrationRequest {
        &self.inner
    }
}

#[pymethods]
impl RegistrationRequest {
    /// Serialize for network transmission.
    fn to_bytes<'py>(&self, py: Python<'py>) -> Bound<'py, PyBytes> {
        PyBytes::new(py, &self.inner.serialize())
    }

    /// Deserialize from network transmission.
    #[staticmethod]
    fn from_bytes(data: &[u8]) -> PyResult<Self> {
        let inner = OpaqueRegistrationRequest::deserialize(data)
            .map_err(|e| OpaqueSnakeError::Serialization(e.to_string()))?;
        Ok(Self { inner })
    }
}

/// Server's registration response message.
#[pyclass]
#[derive(Clone)]
pub struct RegistrationResponse {
    inner: OpaqueRegistrationResponse,
}

impl RegistrationResponse {
    pub fn new(inner: OpaqueRegistrationResponse) -> Self {
        Self { inner }
    }

    pub fn inner(&self) -> &OpaqueRegistrationResponse {
        &self.inner
    }
}

#[pymethods]
impl RegistrationResponse {
    fn to_bytes<'py>(&self, py: Python<'py>) -> Bound<'py, PyBytes> {
        PyBytes::new(py, &self.inner.serialize())
    }

    #[staticmethod]
    fn from_bytes(data: &[u8]) -> PyResult<Self> {
        let inner = OpaqueRegistrationResponse::deserialize(data)
            .map_err(|e| OpaqueSnakeError::Serialization(e.to_string()))?;
        Ok(Self { inner })
    }
}

/// Client's final registration message.
#[pyclass]
#[derive(Clone)]
pub struct RegistrationUpload {
    inner: OpaqueRegistrationUpload,
}

impl RegistrationUpload {
    pub fn new(inner: OpaqueRegistrationUpload) -> Self {
        Self { inner }
    }

    pub fn inner(&self) -> &OpaqueRegistrationUpload {
        &self.inner
    }
}

#[pymethods]
impl RegistrationUpload {
    fn to_bytes<'py>(&self, py: Python<'py>) -> Bound<'py, PyBytes> {
        PyBytes::new(py, &self.inner.serialize())
    }

    #[staticmethod]
    fn from_bytes(data: &[u8]) -> PyResult<Self> {
        let inner = OpaqueRegistrationUpload::deserialize(data)
            .map_err(|e| OpaqueSnakeError::Serialization(e.to_string()))?;
        Ok(Self { inner })
    }
}

// --- Login Messages ---

/// Client's initial login message.
#[pyclass]
#[derive(Clone)]
pub struct CredentialRequest {
    inner: OpaqueCredentialRequest,
}

impl CredentialRequest {
    pub fn new(inner: OpaqueCredentialRequest) -> Self {
        Self { inner }
    }

    pub fn inner(&self) -> &OpaqueCredentialRequest {
        &self.inner
    }
}

#[pymethods]
impl CredentialRequest {
    fn to_bytes<'py>(&self, py: Python<'py>) -> Bound<'py, PyBytes> {
        PyBytes::new(py, &self.inner.serialize())
    }

    #[staticmethod]
    fn from_bytes(data: &[u8]) -> PyResult<Self> {
        let inner = OpaqueCredentialRequest::deserialize(data)
            .map_err(|e| OpaqueSnakeError::Serialization(e.to_string()))?;
        Ok(Self { inner })
    }
}

/// Server's login response message.
#[pyclass]
#[derive(Clone)]
pub struct CredentialResponse {
    inner: OpaqueCredentialResponse,
}

impl CredentialResponse {
    pub fn new(inner: OpaqueCredentialResponse) -> Self {
        Self { inner }
    }

    pub fn inner(&self) -> &OpaqueCredentialResponse {
        &self.inner
    }
}

#[pymethods]
impl CredentialResponse {
    fn to_bytes<'py>(&self, py: Python<'py>) -> Bound<'py, PyBytes> {
        PyBytes::new(py, &self.inner.serialize())
    }

    #[staticmethod]
    fn from_bytes(data: &[u8]) -> PyResult<Self> {
        let inner = OpaqueCredentialResponse::deserialize(data)
            .map_err(|e| OpaqueSnakeError::Serialization(e.to_string()))?;
        Ok(Self { inner })
    }
}

/// Client's final login message.
#[pyclass]
#[derive(Clone)]
pub struct CredentialFinalization {
    inner: OpaqueCredentialFinalization,
}

impl CredentialFinalization {
    pub fn new(inner: OpaqueCredentialFinalization) -> Self {
        Self { inner }
    }

    pub fn inner(&self) -> &OpaqueCredentialFinalization {
        &self.inner
    }
}

#[pymethods]
impl CredentialFinalization {
    fn to_bytes<'py>(&self, py: Python<'py>) -> Bound<'py, PyBytes> {
        PyBytes::new(py, &self.inner.serialize())
    }

    #[staticmethod]
    fn from_bytes(data: &[u8]) -> PyResult<Self> {
        let inner = OpaqueCredentialFinalization::deserialize(data)
            .map_err(|e| OpaqueSnakeError::Serialization(e.to_string()))?;
        Ok(Self { inner })
    }
}

// --- Stored Types ---

/// Stored credential for a registered user.
///
/// This must be stored securely on the server. It contains no
/// information that would allow recovering the user's password.
#[pyclass]
#[derive(Clone)]
pub struct PasswordFile {
    inner: OpaqueServerRegistration,
}

impl PasswordFile {
    pub fn new(inner: OpaqueServerRegistration) -> Self {
        Self { inner }
    }

    pub fn inner(&self) -> &OpaqueServerRegistration {
        &self.inner
    }
}

#[pymethods]
impl PasswordFile {
    /// Serialize for storage.
    fn to_bytes<'py>(&self, py: Python<'py>) -> Bound<'py, PyBytes> {
        PyBytes::new(py, &self.inner.serialize())
    }

    /// Deserialize from storage.
    #[staticmethod]
    fn from_bytes(data: &[u8]) -> PyResult<Self> {
        let inner = OpaqueServerRegistration::deserialize(data)
            .map_err(|e| OpaqueSnakeError::Serialization(e.to_string()))?;
        Ok(Self { inner })
    }
}

// --- Result Types ---

/// Keys derived from a successful authentication.
#[pyclass]
#[derive(Clone)]
pub struct SessionKeys {
    session_key: Vec<u8>,
    export_key: Option<Vec<u8>>,
}

impl SessionKeys {
    pub fn new(session_key: Vec<u8>, export_key: Option<Vec<u8>>) -> Self {
        Self {
            session_key,
            export_key,
        }
    }
}

#[pymethods]
impl SessionKeys {
    /// 64-byte shared session key.
    #[getter]
    fn session_key<'py>(&self, py: Python<'py>) -> Bound<'py, PyBytes> {
        PyBytes::new(py, &self.session_key)
    }

    /// 64-byte export key (may not be available on server side).
    #[getter]
    fn export_key<'py>(&self, py: Python<'py>) -> Option<Bound<'py, PyBytes>> {
        self.export_key.as_ref().map(|k| PyBytes::new(py, k))
    }
}

/// Result of completing client registration.
#[pyclass]
pub struct RegistrationResult {
    upload: RegistrationUpload,
    export_key: Vec<u8>,
    server_s_pk: Vec<u8>,
}

impl RegistrationResult {
    pub fn new(upload: RegistrationUpload, export_key: Vec<u8>, server_s_pk: Vec<u8>) -> Self {
        Self {
            upload,
            export_key,
            server_s_pk,
        }
    }
}

#[pymethods]
impl RegistrationResult {
    /// Message to send to server to complete registration.
    #[getter]
    fn upload(&self) -> RegistrationUpload {
        self.upload.clone()
    }

    /// Export key that can be used for additional key derivation.
    #[getter]
    fn export_key<'py>(&self, py: Python<'py>) -> Bound<'py, PyBytes> {
        PyBytes::new(py, &self.export_key)
    }

    /// Server's static public key.
    #[getter]
    fn server_s_pk<'py>(&self, py: Python<'py>) -> Bound<'py, PyBytes> {
        PyBytes::new(py, &self.server_s_pk)
    }
}

/// Result of completing client login.
#[pyclass]
pub struct LoginResult {
    finalization: CredentialFinalization,
    session_keys: SessionKeys,
}

impl LoginResult {
    pub fn new(finalization: CredentialFinalization, session_keys: SessionKeys) -> Self {
        Self {
            finalization,
            session_keys,
        }
    }
}

#[pymethods]
impl LoginResult {
    /// Message to send to server to complete login.
    #[getter]
    fn finalization(&self) -> CredentialFinalization {
        self.finalization.clone()
    }

    /// Derived session keys.
    #[getter]
    fn session_keys(&self) -> SessionKeys {
        self.session_keys.clone()
    }
}

// --- State Types ---

/// Opaque state for client registration (do not serialize).
/// This state can only be used once - calling finish_registration consumes it.
#[pyclass]
pub struct ClientRegistrationState {
    inner: Option<opaque_ke::ClientRegistration<DefaultCipherSuite>>,
}

impl ClientRegistrationState {
    pub fn new(inner: opaque_ke::ClientRegistration<DefaultCipherSuite>) -> Self {
        Self { inner: Some(inner) }
    }

    /// Take the inner state, leaving None behind.
    /// Returns None if the state has already been taken.
    pub fn take(&mut self) -> Option<opaque_ke::ClientRegistration<DefaultCipherSuite>> {
        self.inner.take()
    }
}

/// Opaque state for client login (do not serialize).
/// This state can only be used once - calling finish_login consumes it.
#[pyclass]
pub struct ClientLoginState {
    inner: Option<opaque_ke::ClientLogin<DefaultCipherSuite>>,
}

impl ClientLoginState {
    pub fn new(inner: opaque_ke::ClientLogin<DefaultCipherSuite>) -> Self {
        Self { inner: Some(inner) }
    }

    /// Take the inner state, leaving None behind.
    /// Returns None if the state has already been taken.
    pub fn take(&mut self) -> Option<opaque_ke::ClientLogin<DefaultCipherSuite>> {
        self.inner.take()
    }
}

/// Opaque state for server login (do not serialize).
/// This state can only be used once - calling finish_login consumes it.
#[pyclass]
pub struct ServerLoginState {
    inner: Option<opaque_ke::ServerLogin<DefaultCipherSuite>>,
    pub context: Option<Vec<u8>>,
    pub client_identifier: Option<Vec<u8>>,
    pub server_identifier: Option<Vec<u8>>,
}

impl ServerLoginState {
    pub fn new(
        inner: opaque_ke::ServerLogin<DefaultCipherSuite>,
        context: Option<Vec<u8>>,
        client_identifier: Option<Vec<u8>>,
        server_identifier: Option<Vec<u8>>,
    ) -> Self {
        Self {
            inner: Some(inner),
            context,
            client_identifier,
            server_identifier,
        }
    }

    /// Take the inner state, leaving None behind.
    /// Returns None if the state has already been taken.
    pub fn take(&mut self) -> Option<opaque_ke::ServerLogin<DefaultCipherSuite>> {
        self.inner.take()
    }
}
