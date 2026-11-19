package com.edgeedu.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.edgeedu.app.AppViewModel
import com.edgeedu.app.ModelStatus
import com.edgeedu.app.content.Profile
import com.edgeedu.app.ui.theme.EdgeEduGradients
import com.edgeedu.app.ui.theme.ThemeMode

/**
 * Settings (PRD §16): profile summary, offline status, and Logout — which
 * deletes downloaded content but keeps bookmarks and notes (§12.4).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: AppViewModel,
    profile: Profile,
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
) {
    val str = LocalStrings.current
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(EdgeEduGradients.Header, RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(52.dp).background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        profile.name.firstOrNull()?.uppercase() ?: "S",
                        color = Color.White, fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
                Column(Modifier.padding(start = 14.dp)) {
                    Text(profile.name, color = Color.White, style = MaterialTheme.typography.titleLarge)
                    Text(
                        profile.scope.label,
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(str.localProfile, style = MaterialTheme.typography.titleSmall)
                    Text(
                        str.localProfileDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(str.offlineTitle, style = MaterialTheme.typography.titleSmall)
                    Text(
                        str.offlineDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            val modelStatus by viewModel.modelStatus.collectAsState()
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(str.aiTutor, style = MaterialTheme.typography.titleSmall)
                    Text(
                        str.aiTutorDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    when (val s = modelStatus) {
                        is ModelStatus.Idle -> Button(
                            onClick = { viewModel.downloadModel() },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(str.downloadModel) }

                        is ModelStatus.Downloading -> {
                            val frac = if (s.mbTotal > 0) s.mbDone.toFloat() / s.mbTotal else null
                            if (frac != null) {
                                LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth())
                                Text(
                                    "${str.downloading} ${s.mbDone} / ${s.mbTotal} MB (${(frac * 100).toInt()}%)",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            } else {
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                                Text(str.startingDownload, style = MaterialTheme.typography.labelSmall)
                            }
                            OutlinedButton(
                                onClick = { viewModel.cancelModelDownload() },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(str.cancel) }
                        }

                        is ModelStatus.Installed -> {
                            Text(
                                str.modelInstalled,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            if (!viewModel.nativeInferenceAvailable) {
                                Text(
                                    str.modelNotRunnable,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        is ModelStatus.Failed -> {
                            Text(
                                "${str.downloadFailed}: ${s.reason}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Button(
                                onClick = { viewModel.downloadModel() },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(str.retryDownload) }
                        }
                    }
                }
            }

            AccountCard(viewModel)

            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(str.appearance, style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            val modeLabel = when (mode) {
                                ThemeMode.System -> str.themeSystem
                                ThemeMode.Light -> str.themeLight
                                ThemeMode.Dark -> str.themeDark
                            }
                            FilterChip(
                                selected = themeMode == mode,
                                onClick = { onThemeChange(mode) },
                                label = { Text(modeLabel) },
                            )
                        }
                    }
                }
            }

            Button(
                onClick = { viewModel.logout() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Text(str.logOut)
            }
            Text(
                str.logOutDesc,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The optional backup account (PRD §17): create/sign in, see how many
 * offline answers are waiting, sync on demand. History is recorded locally
 * either way; sync also runs automatically when the device comes online.
 */
@Composable
private fun AccountCard(viewModel: AppViewModel) {
    val str = LocalStrings.current
    val email by viewModel.accountEmail.collectAsState()
    val status by viewModel.syncStatus.collectAsState()
    val pending by viewModel.pendingHistory.collectAsState()

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(str.accountTitle, style = MaterialTheme.typography.titleSmall)
            Text(
                str.accountDesc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!viewModel.syncConfigured) {
                Text(
                    str.accountNotConfigured,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            when (val s = status) {
                is com.edgeedu.app.SyncStatus.Working -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(str.accountSyncing, style = MaterialTheme.typography.labelSmall)
                }
                is com.edgeedu.app.SyncStatus.Error -> Text(
                    "${str.accountSyncFailed}: ${s.reason}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
                is com.edgeedu.app.SyncStatus.Done -> Text(
                    "✓ ${s.uploaded} ${str.accountSynced}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                is com.edgeedu.app.SyncStatus.Idle -> Unit
            }

            if (email == null) {
                var emailInput by androidx.compose.runtime.remember {
                    androidx.compose.runtime.mutableStateOf("")
                }
                var passwordInput by androidx.compose.runtime.remember {
                    androidx.compose.runtime.mutableStateOf("")
                }
                androidx.compose.material3.OutlinedTextField(
                    value = emailInput,
                    onValueChange = { emailInput = it },
                    label = { Text(str.accountEmailLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                androidx.compose.material3.OutlinedTextField(
                    value = passwordInput,
                    onValueChange = { passwordInput = it },
                    label = { Text(str.accountPasswordLabel) },
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.createAccount(emailInput, passwordInput) },
                        enabled = status !is com.edgeedu.app.SyncStatus.Working,
                        modifier = Modifier.weight(1f),
                    ) { Text(str.accountCreate) }
                    OutlinedButton(
                        onClick = { viewModel.signInAccount(emailInput, passwordInput) },
                        enabled = status !is com.edgeedu.app.SyncStatus.Working,
                        modifier = Modifier.weight(1f),
                    ) { Text(str.accountSignIn) }
                }
                Text(
                    str.accountNeedsInternet,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    "${str.accountSignedInAs} $email",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    if (pending == 0) str.accountAllSynced else "$pending ${str.accountPending}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (pending == 0) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.syncNow() },
                        enabled = status !is com.edgeedu.app.SyncStatus.Working,
                        modifier = Modifier.weight(1f),
                    ) { Text(str.accountSyncNow) }
                    OutlinedButton(
                        onClick = { viewModel.signOutAccount() },
                        modifier = Modifier.weight(1f),
                    ) { Text(str.accountSignOut) }
                }
            }
        }
    }
}
