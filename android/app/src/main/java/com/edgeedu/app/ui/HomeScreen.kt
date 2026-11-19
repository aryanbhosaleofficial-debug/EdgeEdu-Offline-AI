package com.edgeedu.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.edgeedu.app.AppViewModel
import com.edgeedu.app.CorpusInfo
import com.edgeedu.app.content.Profile
import com.edgeedu.app.notes.CustomSubject
import com.edgeedu.app.session.Subject
import com.edgeedu.app.session.SubjectRef
import com.edgeedu.app.ui.theme.EdgeEduGradients
import com.edgeedu.app.ui.theme.style

/**
 * Home (PRD §6.1), matching the product sketch: a "Hello <name> / Std N"
 * header, then every subject as a full-width row with its study-progress
 * bar, then "Your own subjects"; the menu is the bottom navigation bar.
 */
@Composable
fun HomeScreen(
    viewModel: AppViewModel,
    info: CorpusInfo,
    profile: Profile,
    onSessionStarted: () -> Unit,
    onSubjectClicked: (Subject) -> Unit,
) {
    val customSubjects by viewModel.customSubjects.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val str = LocalStrings.current
    var showAddDialog by remember { mutableStateOf(false) }

    Column(
        Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Header(info, profile)

        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(str.subjects, style = MaterialTheme.typography.titleLarge)

            // Only show subjects that actually have content for this class
            // (Std 9 Science is one combined book; Std 10 splits into 1 & 2).
            val subjects = Subject.entries.filter { (info.subjectChunkCounts[it] ?: 0) > 0 }
            if (subjects.isEmpty()) return@Column
            subjects.forEach { subject ->
                SubjectRow(
                    subject = subject,
                    chunks = info.subjectChunkCounts[subject] ?: 0,
                    progress = progress[SubjectRef.Builtin(subject).key] ?: 0f,
                ) {
                    // Tapping a subject opens its chapter picker (the caller
                    // decides whether to skip it when there's nothing to pick).
                    onSubjectClicked(subject)
                }
            }

            // ---- Your own subjects ----
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(str.yourOwnSubjects, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            }
            Text(
                str.yourOwnSubjectsDesc,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            customSubjects.forEach { subject ->
                CustomSubjectCard(
                    subject = subject,
                    progress = progress[SubjectRef.Custom(subject.id, subject.name).key] ?: 0f,
                    onOpen = {
                        viewModel.startSession(SubjectRef.Custom(subject.id, subject.name))
                        onSessionStarted()
                    },
                    onDelete = { viewModel.deleteCustomSubject(subject) },
                )
            }

            AddSubjectCard { showAddDialog = true }

            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(str.trustworthyMaths, style = MaterialTheme.typography.titleSmall)
                    Text(
                        str.trustworthyMathsDesc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddSubjectDialog(
            onDismiss = { showAddDialog = false },
            onCreate = { name ->
                showAddDialog = false
                viewModel.createCustomSubject(name)
                onSessionStarted()
            },
        )
    }
}

@Composable
private fun AddSubjectDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    val str = LocalStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(str.addSubject) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    str.nameYourSubject,
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(str.subjectNameLabel) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onCreate(name) }) { Text(str.create) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(str.cancel) } },
    )
}

@Composable
private fun CustomSubjectCard(
    subject: CustomSubject,
    progress: Float,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val str = LocalStrings.current
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(36.dp).background(EdgeEduGradients.Header, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) { Text("✏️") }
            Column(
                Modifier.padding(start = 12.dp).weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(subject.name, style = MaterialTheme.typography.titleSmall)
                if (subject.chunkCount > 0) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(
                    if (subject.chunkCount == 0) str.noNotesYet else "${subject.chunkCount} ${str.topicsWord}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onDelete) { Text(str.remove) }
        }
    }
}

@Composable
private fun AddSubjectCard(onClick: () -> Unit) {
    val str = LocalStrings.current
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(36.dp).background(EdgeEduGradients.Header, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) { Text("+", color = Color.White, style = MaterialTheme.typography.titleLarge) }
            Column(Modifier.padding(start = 12.dp)) {
                Text(str.addSubject, style = MaterialTheme.typography.titleSmall)
                Text(
                    str.buildFromNotes,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Header(info: CorpusInfo, profile: Profile) {
    val str = LocalStrings.current
    Box(
        Modifier
            .fillMaxWidth()
            .background(EdgeEduGradients.Header, RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
            .padding(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(44.dp)
                        .background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        profile.name.firstOrNull()?.uppercase() ?: "S",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    // The sketch's greeting: "Hello (User name)" over "Std: 9th".
                    Text(
                        "${str.helloUser} ${profile.name} 👋",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        "${str.classWord}: ${profile.standard}  ·  v${com.edgeedu.app.BuildConfig.VERSION_NAME}",
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Pill(str.offline)
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Stat("${info.chunkCount}", str.statTopics)
                Stat("${info.verifiedSolutionChunks}", str.statVerified)
                Stat("${info.subjectChunkCounts.size}", str.statSubjects)
            }
        }
    }
}

@Composable
private fun Pill(text: String) {
    Box(
        Modifier
            .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineSmall)
        Text(label, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall)
    }
}

/**
 * One subject as a full-width row (the sketch's list): emoji + name +
 * topic count on the left, and a study-progress bar with its percentage.
 */
@Composable
private fun SubjectRow(subject: Subject, chunks: Int, progress: Float, onClick: () -> Unit) {
    val style = subject.style()
    val str = LocalStrings.current
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).background(style.tint.copy(alpha = 0.12f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) { Text(style.emoji, style = MaterialTheme.typography.titleMedium) }
            Column(Modifier.padding(start = 14.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        str.subjectName(subject),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = style.tint,
                    )
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = style.tint,
                    trackColor = style.tint.copy(alpha = 0.15f),
                )
                Text(
                    "$chunks ${str.topicsWord}" + if (subject.isMath) " ${str.verifiedMathEngine}" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
