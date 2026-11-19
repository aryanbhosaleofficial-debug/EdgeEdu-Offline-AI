package com.edgeedu.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.edgeedu.app.R
import com.edgeedu.app.content.ContentScope
import com.edgeedu.app.ui.theme.EdgeEduGradients

private val MEDIUM_LABELS = mapOf("English" to "English", "Hindi" to "हिंदी", "Marathi" to "मराठी")

/**
 * Login screen (PRD §12.1, §16) with the two-tier, Netflix-style flow:
 *
 *  1. the CLOUD account (optional email/password — backs up Q&A history when
 *     online; skippable, everything works offline without it), then
 *  2. the LOCAL profiles — "Who's studying?": the device's saved profiles as
 *     one-tap tiles, plus "Add profile" for the name + class + medium form.
 *
 * Picking or creating a profile triggers the one-time content download.
 */
@Composable
fun LoginScreen(viewModel: com.edgeedu.app.AppViewModel) {
    val profiles by viewModel.profiles.collectAsState()
    var showForm by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var standard by remember { mutableIntStateOf(10) }
    var medium by remember { mutableStateOf("English") }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    ) {
        // Hero
        Box(
            Modifier
                .fillMaxWidth()
                .background(EdgeEduGradients.Header, RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                .padding(top = 48.dp, bottom = 32.dp, start = 24.dp, end = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painter = painterResource(R.drawable.edgeedu_logo),
                    contentDescription = "EdgeEdu logo",
                    modifier = Modifier.size(72.dp).clip(RoundedCornerShape(20.dp)),
                )
                Text(
                    "EdgeEdu",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text(
                    "Your offline learning companion",
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Box(
                    Modifier
                        .padding(top = 14.dp)
                        .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        "Works 100% offline after setup",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }

        // Tier 1: the cloud account (optional — like a streaming account).
        AccountSection(viewModel)

        // Tier 2: the local profiles ("Who's studying?"), Netflix-style.
        if (!showForm && profiles.isNotEmpty()) {
            Column(
                Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Who's studying?", style = MaterialTheme.typography.titleLarge)
                profiles.forEach { stored ->
                    ProfileTile(
                        stored = stored,
                        onPick = { viewModel.selectProfile(stored) },
                        onDelete = { viewModel.deleteProfile(stored) },
                    )
                }
                Button(
                    onClick = { showForm = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) { Text("＋  Add profile") }
            }
            return@Column
        }

        // Add-profile form (also the first-run state when no profiles exist).
        Column(
            Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column {
                Text("Set up your profile", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Set once — remembered until you log out.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (profiles.isNotEmpty()) {
                    Text(
                        "← Back to profiles",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 6.dp).clickable { showForm = false },
                    )
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Your name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Class", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ContentScope.STANDARDS.forEach { std ->
                    SelectChip(
                        label = "Class $std",
                        selected = standard == std,
                        gradient = EdgeEduGradients.Header,
                        modifier = Modifier.weight(1f),
                    ) { standard = std }
                }
            }

            Text("Medium of instruction", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ContentScope.MEDIUMS.forEach { med ->
                    SelectChip(
                        label = MEDIUM_LABELS[med] ?: med,
                        selected = medium == med,
                        gradient = EdgeEduGradients.Accent,
                        modifier = Modifier.weight(1f),
                    ) { medium = med }
                }
            }

            Button(
                onClick = { viewModel.login(name, standard, medium) },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            if (name.isNotBlank()) EdgeEduGradients.Header
                            else Brush.linearGradient(
                                listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surfaceVariant)
                            ),
                            RoundedCornerShape(16.dp),
                        )
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Start Learning  →",
                        color = if (name.isNotBlank()) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Text(
                "First login needs internet once to download Class $standard · " +
                    "${MEDIUM_LABELS[medium] ?: medium} content. After that, everything " +
                    "works fully offline until you log out.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Tier-1 cloud sign-in: shown only when this build has a sync backend. It is
 * optional and skippable — the local profiles below work fully offline.
 */
@Composable
private fun AccountSection(viewModel: com.edgeedu.app.AppViewModel) {
    if (!viewModel.syncConfigured) return
    val email by viewModel.accountEmail.collectAsState()
    val status by viewModel.syncStatus.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }

    Column(
        Modifier.padding(horizontal = 20.dp).padding(top = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when {
            email != null -> Box(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
                    .padding(14.dp),
            ) {
                Text(
                    "☁  Signed in as $email",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            !expanded -> Box(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
                    .clickable { expanded = true }
                    .padding(14.dp),
            ) {
                Column {
                    Text(
                        "☁  Sign in to your EdgeEdu account",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Optional — backs up your questions & answers when online. " +
                            "Tap to sign in, or just pick a profile below.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            else -> Column(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = emailInput, onValueChange = { emailInput = it },
                    label = { Text("Email") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = passwordInput, onValueChange = { passwordInput = it },
                    label = { Text("Password") }, singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (status is com.edgeedu.app.SyncStatus.Error) {
                    Text(
                        (status as com.edgeedu.app.SyncStatus.Error).reason,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.signInAccount(emailInput, passwordInput) },
                        enabled = status !is com.edgeedu.app.SyncStatus.Working,
                        modifier = Modifier.weight(1f),
                    ) { Text("Sign in") }
                    Button(
                        onClick = { viewModel.createAccount(emailInput, passwordInput) },
                        enabled = status !is com.edgeedu.app.SyncStatus.Working,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                    ) { Text("Create account") }
                }
                Text(
                    "Skip — continue offline",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { expanded = false },
                )
            }
        }
    }
}

/** One saved profile in the "Who's studying?" list. */
@Composable
private fun ProfileTile(
    stored: com.edgeedu.app.content.StoredProfile,
    onPick: () -> Unit,
    onDelete: () -> Unit,
) {
    val p = stored.profile
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .clickable(onClick = onPick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp).background(EdgeEduGradients.Header, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                p.name.firstOrNull()?.uppercase() ?: "S",
                color = Color.White, fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(p.name, style = MaterialTheme.typography.titleSmall)
            Text(
                "Class ${p.standard} · ${MEDIUM_LABELS[p.medium] ?: p.medium}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "✕",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clickable(onClick = onDelete).padding(8.dp),
        )
    }
}

@Composable
private fun SelectChip(
    label: String,
    selected: Boolean,
    gradient: Brush,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val base = if (selected) {
        modifier.background(gradient, RoundedCornerShape(16.dp))
    } else {
        modifier
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
    }
    Box(
        base.clickable(onClick = onClick).padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
    }
}
