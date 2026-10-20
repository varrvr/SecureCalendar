use pyo3::prelude::*;

mod cipher_suite;
mod client;
mod errors;
mod messages;
mod server;

use client::OpaqueClient;
use messages::{
    ClientLoginState, ClientRegistrationState, CredentialFinalization, CredentialRequest,
    CredentialResponse, LoginResult, PasswordFile, RegistrationRequest, RegistrationResponse,
    RegistrationResult, RegistrationUpload, ServerLoginState, SessionKeys,
};
use server::OpaqueServer;

/// Python bindings for the OPAQUE password-authenticated key exchange protocol.
#[pymodule]
fn _core(py: Python<'_>, m: &Bound<'_, PyModule>) -> PyResult<()> {
    // Core classes
    m.add_class::<OpaqueServer>()?;
    m.add_class::<OpaqueClient>()?;

    // Message types
    m.add_class::<RegistrationRequest>()?;
    m.add_class::<RegistrationResponse>()?;
    m.add_class::<RegistrationUpload>()?;
    m.add_class::<CredentialRequest>()?;
    m.add_class::<CredentialResponse>()?;
    m.add_class::<CredentialFinalization>()?;

    // Result and state types
    m.add_class::<PasswordFile>()?;
    m.add_class::<SessionKeys>()?;
    m.add_class::<RegistrationResult>()?;
    m.add_class::<LoginResult>()?;
    m.add_class::<ClientRegistrationState>()?;
    m.add_class::<ClientLoginState>()?;
    m.add_class::<ServerLoginState>()?;

    // Exceptions
    errors::register_exceptions(py, m)?;

    Ok(())
}
