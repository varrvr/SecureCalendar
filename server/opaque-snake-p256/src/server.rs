use pyo3::prelude::*;
use pyo3::types::PyBytes;

use crate::cipher_suite::DefaultCipherSuite;
use crate::errors::OpaqueSnakeError;
use crate::messages::{
    CredentialFinalization, CredentialRequest, CredentialResponse, PasswordFile,
    RegistrationRequest, RegistrationResponse, RegistrationUpload, ServerLoginState, SessionKeys,
};
use opaque_ke::{Identifiers, ServerLoginParameters};

type OpaqueServerSetup = opaque_ke::ServerSetup<DefaultCipherSuite>;

/// Server-side OPAQUE protocol handler.
///
/// The server maintains a ServerSetup that should be persisted across
/// restarts. User password files are stored separately.
#[pyclass]
pub struct OpaqueServer {
    setup: OpaqueServerSetup,
}

#[pymethods]
impl OpaqueServer {
    /// Create a new server or restore from serialized setup.
    ///
    /// Args:
    ///     setup_bytes: Previously serialized server setup, or None to create new.
    #[new]
    #[pyo3(signature = (setup_bytes=None))]
    fn new(setup_bytes: Option<&[u8]>) -> PyResult<Self> {
        let setup = match setup_bytes {
            Some(bytes) => OpaqueServerSetup::deserialize(bytes)
                .map_err(|e: opaque_ke::errors::ProtocolError| {
                    OpaqueSnakeError::Serialization(e.to_string())
                })?,
            None => {
                let mut rng = rand::thread_rng();
                OpaqueServerSetup::new(&mut rng)
            }
        };
        Ok(Self { setup })
    }

    /// Serialize the server setup for persistent storage.
    ///
    /// This contains the server's private key and must be kept secret.
    /// Store this securely and restore it when recreating the server.
    fn export_setup<'py>(&self, py: Python<'py>) -> Bound<'py, PyBytes> {
        PyBytes::new(py, &self.setup.serialize())
    }

    /// Process a client registration request.
    ///
    /// Args:
    ///     request: The client's registration request message.
    ///     user_id: Unique identifier for this user (e.g., username or email).
    ///
    /// Returns:
    ///     Response to send back to the client.
    fn create_registration_response(
        &self,
        request: &RegistrationRequest,
        user_id: &str,
    ) -> PyResult<RegistrationResponse> {
        let result = opaque_ke::ServerRegistration::<DefaultCipherSuite>::start(
            &self.setup,
            request.inner().clone(),
            user_id.as_bytes(),
        )
        .map_err(|e| OpaqueSnakeError::Protocol(e.to_string()))?;

        Ok(RegistrationResponse::new(result.message))
    }

    /// Complete registration and create a password file.
    ///
    /// Args:
    ///     upload: The client's registration upload message.
    ///
    /// Returns:
    ///     Password file to store for this user. Store this securely.
    fn finish_registration(&self, upload: &RegistrationUpload) -> PyResult<PasswordFile> {
        let password_file =
            opaque_ke::ServerRegistration::<DefaultCipherSuite>::finish(upload.inner().clone());
        Ok(PasswordFile::new(password_file))
    }

    /// Process a client login request.
    ///
    /// Args:
    ///     request: The client's credential request message.
    ///     user_id: The user's identifier (must match registration).
    ///     password_file: The stored password file for this user.
    ///
    /// Returns:
    ///     Tuple of (response to send to client, server state for finish_login).
    #[pyo3(signature = (request, user_id, password_file, context=None, client_identifier=None, server_identifier=None))]
    fn create_credential_response(
        &self,
        request: &CredentialRequest,
        user_id: &str,
        password_file: &PasswordFile,
        context: Option<Vec<u8>>,
        client_identifier: Option<Vec<u8>>,
        server_identifier: Option<Vec<u8>>,
    ) -> PyResult<(CredentialResponse, ServerLoginState)> {
        let mut rng = rand::thread_rng();
        let parameters = ServerLoginParameters {
            context: context.as_deref(),
            identifiers: Identifiers {
                client: client_identifier.as_deref(),
                server: server_identifier.as_deref(),
            },
        };
        let result = opaque_ke::ServerLogin::start(
            &mut rng,
            &self.setup,
            Some(password_file.inner().clone()),
            request.inner().clone(),
            user_id.as_bytes(),
            parameters,
        )
        .map_err(|e| OpaqueSnakeError::Protocol(e.to_string()))?;

        Ok((
            CredentialResponse::new(result.message),
            ServerLoginState::new(
                result.state,
                context,
                client_identifier,
                server_identifier,
            ),
        ))
    }

    /// Complete the login and derive session keys.
    ///
    /// Args:
    ///     finalization: The client's credential finalization message.
    ///     state: The server state from create_credential_response.
    ///
    /// Returns:
    ///     Session keys shared with the client.
    ///
    /// Raises:
    ///     AuthenticationError: If authentication fails.
    ///     ProtocolError: If state has already been used.
    fn finish_login(
        &self,
        finalization: &CredentialFinalization,
        state: &mut ServerLoginState,
    ) -> PyResult<SessionKeys> {
        let inner_state = state
            .take()
            .ok_or_else(|| OpaqueSnakeError::Protocol("State has already been used".to_string()))?;

        let result = inner_state
            .finish(
                finalization.inner().clone(),
                ServerLoginParameters {
                    context: state.context.as_deref(),
                    identifiers: Identifiers {
                        client: state.client_identifier.as_deref(),
                        server: state.server_identifier.as_deref(),
                    },
                },
            )
            .map_err(|_| OpaqueSnakeError::Authentication)?;

        Ok(SessionKeys::new(result.session_key.to_vec(), None))
    }
}
