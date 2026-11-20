
package com.example.calendar.presentation.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.calendar.R
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import android.util.Log

data class ProfileUiState(
    val username: String = "",
    val login: String = "",
    val isConnected: Boolean = false,
    val serverBaseUrl: String = "",
    val serverRegistrationStatus: String = "NOT_REGISTERED",
    val serverAuthenticated: Boolean = false,
    val serverConfigured: Boolean = false,
    val publicKeyFingerprint: String? = null,
    val isCreatingKey: Boolean = false,
    val keyError: String? = null,
    val isServerAuthLoading: Boolean = false,
    val serverAuthError: String? = null
)

@Composable
fun ProfileScreen(
    state: ProfileUiState,
    onShowQrClick: () -> Unit,
    onConnectClick: (String) -> Unit,
    onCreateKeyClick: () -> Unit,
    onServerAddressSave: (String) -> Unit,
    onLogoutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val logTag = "SecureCalendar.ProfileUi"
    var showPasswordDialog by remember { mutableStateOf(false) }
    var serverPassword by remember { mutableStateOf("") }
    var editedServerUrl by remember(state.serverBaseUrl) { mutableStateOf(state.serverBaseUrl) }
    var awaitingServerAuth by remember { mutableStateOf(false) }
    LaunchedEffect(state.serverAuthenticated, state.isServerAuthLoading, state.serverAuthError, awaitingServerAuth) {
        if (awaitingServerAuth && state.serverAuthenticated && !state.isServerAuthLoading) {
            Log.i(logTag, "Server authentication succeeded; closing password dialog")
            serverPassword = ""
            showPasswordDialog = false
            awaitingServerAuth = false
        } else if (awaitingServerAuth && state.serverAuthError != null) {
            awaitingServerAuth = false
        }
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = context.getString(R.string.profile_title),
            style = MaterialTheme.typography.headlineMedium
        )

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(context.getString(R.string.profile_server_address), style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = editedServerUrl,
                    onValueChange = { editedServerUrl = it },
                    label = { Text(context.getString(R.string.profile_server_url)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    context.getString(R.string.profile_address_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = {
                        Log.i(logTag, "Save server endpoint clicked; address contents omitted")
                        onServerAddressSave(editedServerUrl)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(context.getString(R.string.profile_save_address)) }
            }
        }

        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    context.getString(R.string.profile_name),
                    style = MaterialTheme.typography.labelMedium
                )

                Text(
                    state.username,
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    context.getString(R.string.profile_username),
                    style = MaterialTheme.typography.labelMedium
                )

                Text(state.login)

                Text(
                    context.getString(R.string.profile_connection_prefix) + if (state.isConnected) {
                        context.getString(R.string.profile_online)
                    } else {
                        context.getString(R.string.profile_offline)
                    }
                )

                HorizontalDivider()

                Text("OPAQUE: ${if (state.serverRegistrationStatus == "REGISTERED") context.getString(R.string.profile_registered) else context.getString(R.string.profile_not_registered)}")
                Text(context.getString(R.string.profile_auth_state, context.getString(if (state.serverAuthenticated) R.string.profile_authenticated else R.string.profile_not_authenticated)))
                Text(context.getString(R.string.profile_fingerprint, state.publicKeyFingerprint ?: context.getString(R.string.profile_key_missing)))
            }
        }

        if (!state.serverConfigured) {
            Text(
                context.getString(R.string.profile_unconfigured),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (state.publicKeyFingerprint == null) {
            Button(
                onClick = onCreateKeyClick,
                enabled = !state.isCreatingKey,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isCreatingKey) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                }
                com.example.calendar.presentation.components.AnimatedBee(Modifier.size(36.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (state.isCreatingKey) context.getString(R.string.profile_creating_key) else context.getString(R.string.profile_create_key))
            }
        }
        state.keyError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Button(
            onClick = {
                Log.i(logTag, "Connect-to-server button clicked; configured=${state.serverConfigured}")
                showPasswordDialog = true
            },
            enabled = state.serverConfigured && !state.isServerAuthLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.isServerAuthLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text(if (state.isConnected) context.getString(R.string.profile_refresh_session) else if (state.serverConfigured) context.getString(R.string.profile_connect) else context.getString(R.string.profile_server_missing))
        }
        state.serverAuthError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        OutlinedButton(
            onClick = onShowQrClick,
            enabled = false,
            modifier = Modifier.fillMaxWidth()
        ) { Text(context.getString(R.string.profile_qr_unavailable)) }

        OutlinedButton(
            onClick = onLogoutClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(context.getString(R.string.profile_logout))
        }
    }

    if (state.isServerAuthLoading) {
        ConnectionCleaningDialog()
    }

    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!state.isServerAuthLoading) {
                    showPasswordDialog = false
                    serverPassword = ""
                }
            },
            title = { Text(context.getString(R.string.profile_connect_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(context.getString(R.string.profile_password_help))
                    state.serverAuthError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                    OutlinedTextField(
                        value = serverPassword,
                        onValueChange = { serverPassword = it },
                        label = { Text(context.getString(R.string.profile_password)) },
                        singleLine = true,
                        enabled = !state.isServerAuthLoading,
                        visualTransformation = PasswordVisualTransformation()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = serverPassword.isNotEmpty() && !state.isServerAuthLoading,
                    onClick = {
                        val password = serverPassword
                        serverPassword = ""
                        awaitingServerAuth = true
                        Log.i(logTag, "Server authentication submitted; password omitted")
                        onConnectClick(password)
                    }
                ) { Text(context.getString(R.string.profile_continue)) }
            },
            dismissButton = {
                TextButton(
                    enabled = !state.isServerAuthLoading,
                    onClick = {
                        serverPassword = ""
                        showPasswordDialog = false
                    }
                ) { Text(context.getString(R.string.profile_cancel)) }
            }
        )
    }
}
