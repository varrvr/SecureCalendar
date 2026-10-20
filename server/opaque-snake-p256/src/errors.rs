use pyo3::create_exception;
use pyo3::prelude::*;
use thiserror::Error;

// Define Python exception hierarchy
create_exception!(opaque_snake, OpaqueError, pyo3::exceptions::PyException);
create_exception!(opaque_snake, ProtocolError, OpaqueError);
create_exception!(opaque_snake, AuthenticationError, OpaqueError);
create_exception!(opaque_snake, SerializationError, OpaqueError);

/// Internal error type that maps to Python exceptions.
#[derive(Error, Debug)]
pub enum OpaqueSnakeError {
    #[error("Protocol error: {0}")]
    Protocol(String),

    #[error("Authentication failed")]
    Authentication,

    #[error("Serialization error: {0}")]
    Serialization(String),
}

impl From<OpaqueSnakeError> for PyErr {
    fn from(err: OpaqueSnakeError) -> PyErr {
        match err {
            OpaqueSnakeError::Protocol(msg) => ProtocolError::new_err(msg),
            OpaqueSnakeError::Authentication => {
                AuthenticationError::new_err("Authentication failed")
            }
            OpaqueSnakeError::Serialization(msg) => SerializationError::new_err(msg),
        }
    }
}

/// Register exception types with the Python module.
pub fn register_exceptions(py: Python<'_>, m: &Bound<'_, PyModule>) -> PyResult<()> {
    m.add("OpaqueError", py.get_type::<OpaqueError>())?;
    m.add("ProtocolError", py.get_type::<ProtocolError>())?;
    m.add("AuthenticationError", py.get_type::<AuthenticationError>())?;
    m.add("SerializationError", py.get_type::<SerializationError>())?;
    Ok(())
}
