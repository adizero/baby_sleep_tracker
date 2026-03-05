package com.akocis.babysleeptracker.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.akocis.babysleeptracker.ui.component.BigActionButton
import com.akocis.babysleeptracker.viewmodel.SyncViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(
    viewModel: SyncViewModel,
    onBack: () -> Unit
) {
    val isConnected by viewModel.isConnected.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val shareCode by viewModel.shareCode.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sync") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            "Back",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            if (isConnected) {
                ConnectedContent(
                    viewModel = viewModel,
                    isSyncing = isSyncing,
                    shareCode = shareCode,
                    onCopyCode = { code ->
                        clipboardManager.setText(AnnotatedString(code))
                    }
                )
            } else {
                NotConnectedContent(
                    viewModel = viewModel,
                    onOpenBrowser = { url ->
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                )
            }
        }
    }
}

@Composable
private fun ConnectedContent(
    viewModel: SyncViewModel,
    isSyncing: Boolean,
    shareCode: String?,
    onCopyCode: (String) -> Unit
) {
    if (isSyncing) {
        BigActionButton(
            text = "Syncing...",
            onClick = {},
            enabled = false
        )
        CircularProgressIndicator(
            modifier = Modifier
                .padding(16.dp)
                .size(32.dp)
        )
    } else {
        BigActionButton(
            text = "Sync Now",
            onClick = { viewModel.sync() }
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Share access card
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Share Access",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "Generate a code so another device can sync without a Dropbox account",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { viewModel.generateShareCode() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Generate Share Code")
            }
            if (shareCode != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = shareCode,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = { onCopyCode(shareCode) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Copy to Clipboard")
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Disconnect
    OutlinedButton(
        onClick = { viewModel.disconnect() },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Disconnect from Dropbox")
    }
}

@Composable
private fun NotConnectedContent(
    viewModel: SyncViewModel,
    onOpenBrowser: (String) -> Unit
) {
    var appKey by remember { mutableStateOf("") }
    var authCode by remember { mutableStateOf("") }
    var joinCode by remember { mutableStateOf("") }

    // Set up with Dropbox
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Set Up with Dropbox",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "Create a Dropbox app at dropbox.com/developers to get an app key",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = appKey,
                onValueChange = { appKey = it },
                label = { Text("App Key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    if (appKey.isNotBlank()) {
                        val url = viewModel.getAuthorizeUrl(appKey.trim())
                        onOpenBrowser(url)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = appKey.isNotBlank()
            ) {
                Text("Authenticate in Browser")
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = authCode,
                onValueChange = { authCode = it },
                label = { Text("Authorization Code") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    if (appKey.isNotBlank() && authCode.isNotBlank()) {
                        viewModel.exchangeCode(appKey.trim(), authCode.trim())
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = appKey.isNotBlank() && authCode.isNotBlank()
            ) {
                Text("Connect")
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Join with share code
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Join with Share Code",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "Paste a code from another device to sync without a Dropbox account",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = joinCode,
                onValueChange = { joinCode = it },
                label = { Text("Share Code") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    if (joinCode.isNotBlank()) {
                        viewModel.joinWithCode(joinCode.trim())
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = joinCode.isNotBlank()
            ) {
                Text("Join")
            }
        }
    }
}
