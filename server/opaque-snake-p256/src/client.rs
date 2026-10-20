use pyo3::prelude::*;

use crate::cipher_suite::DefaultCipherSuite;
use crate::errors::OpaqueSnakeError;
use crate::messages::{
    ClientLoginState, ClientRegistrationState, CredentialFinalization, CredentialRequest,
    CredentialResponse, LoginResult, RegistrationRequest, RegistrationResponse,
    RegistrationResult, RegistrationUpload, SessionKeys,
};

/// Client-side OPAQUE protocol handler.
///
/// Clients do not need persistent state between sessions.
#[pyclass]
pub struct OpaqueClient;

#[pymethods]
impl OpaqueClient {
    /// Create a new client.
    #[new]
    fn new() -> Self {
        Self
    }

    /// Begin the registration process.
    ///
    /// Args:
    ///     password: The user's password.
    ///
    /// Returns:
    ///     Tuple of (request to send to server, client state for finish).
    fn start_registration(
        &self,
        password: &str,
    ) -> PyResult<(RegistrationRequest, ClientRegistrationState)> {
        let mut rng = rand::thread_rng();
        let result = opaque_ke::ClientRegistration::<DefaultCipherSuite>::start(
            &mut rng,
            password.as_bytes(),
        )
        .map_err(|e| OpaqueSnakeError::Protocol(e.to_string()))?;

        Ok((
            RegistrationRequest::new(result.message),
            ClientRegistrationState::new(result.state),
        ))
    }

    /// Complete registration with the server's response.
    ///
    /// Args:
    ///     response: The server's registration response.
    ///     state: The client state from start_registration.
    ///     password: The same password used in start_registration.
    ///
    /// Returns:
    ///     Registration result containing the upload message and export key.
    ///
    /// Raises:
    ///     ProtocolError: If state has already been used.
    fn finish_registration(
        &self,
        response: &RegistrationResponse,
        state: &mut ClientRegistrationState,
        password: &str,
    ) -> PyResult<RegistrationResult> {
        let mut rng = rand::thread_rng();
        let inner_state = state
            .take()
            .ok_or_else(|| OpaqueSnakeError::Protocol("State has already been used".to_string()))?;

        let result = inner_state
            .finish(
                &mut rng,
                password.as_bytes(),
                response.inner().clone(),
                opaque_ke::ClientRegistrationFinishParameters::default(),
            )
            .map_err(|e| OpaqueSnakeError::Protocol(e.to_string()))?;

        Ok(RegistrationResult::new(
            RegistrationUpload::new(result.message),
            result.export_key.to_vec(),
            result.server_s_pk.serialize().to_vec(),
        ))
    }

    /// Begin the login process.
    ///
    /// Args:
    ///     password: The user's password.
    ///
    /// Returns:
    ///     Tuple of (request to send to server, client state for finish).
    fn start_login(&self, password: &str) -> PyResult<(CredentialRequest, ClientLoginState)> {
        let mut rng = rand::thread_rng();
        let result =
            opaque_ke::ClientLogin::<DefaultCipherSuite>::start(&mut rng, password.as_bytes())
                .map_err(|e| OpaqueSnakeError::Protocol(e.to_string()))?;

        Ok((
            CredentialRequest::new(result.message),
            ClientLoginState::new(result.state),
        ))
    }

    /// Complete login with the server's response.
    ///
    /// Args:
    ///     response: The server's credential response.
    ///     state: The client state from start_login.
    ///     password: The same password used in start_login.
    ///
    /// Returns:
    ///     Login result containing session keys and finalization message.
    ///
    /// Raises:
    ///     AuthenticationError: If authentication fails (wrong password).
    ///     ProtocolError: If state has already been used.
    fn finish_login(
        &self,
        response: &CredentialResponse,
        state: &mut ClientLoginState,
        password: &str,
    ) -> PyResult<LoginResult> {
        let mut rng = rand::thread_rng();
        let inner_state = state
            .take()
            .ok_or_else(|| OpaqueSnakeError::Protocol("State has already been used".to_string()))?;

        let result = inner_state
            .finish(
                &mut rng,
                password.as_bytes(),
                response.inner().clone(),
                opaque_ke::ClientLoginFinishParameters::default(),
            )
            .map_err(|_| OpaqueSnakeError::Authentication)?;

        Ok(LoginResult::new(
            CredentialFinalization::new(result.message),
            SessionKeys::new(
                result.session_key.to_vec(),
                Some(result.export_key.to_vec()),
            ),
        ))
    }
}
