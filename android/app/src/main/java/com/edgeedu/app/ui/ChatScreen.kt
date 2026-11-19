package com.edgeedu.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import com.edgeedu.app.AppViewModel
import com.edgeedu.app.ChatItem
import com.edgeedu.app.data.ChunkSource
import kotlinx.coroutines.delay

@Composable
fun ChatScreen(viewModel: AppViewModel) {
    val chat by viewModel.chat.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val session by viewModel.session.collectAsState()
    val str = LocalStrings.current
    // The placeholder example follows the active subject (maths vs science vs
    // geography vs history), so it never prompts an equation in a non-maths one.
    val placeholder = str.chatPlaceholder(session?.subject)
    var input by remember { mutableStateOf(TextFieldValue("")) }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        ImportBar(viewModel)

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(chat) { item ->
                when (item) {
                    is ChatItem.User -> Box(Modifier.fillMaxWidth()) {
                        Text(
                            item.text,
                            Modifier
                                .align(Alignment.CenterEnd)
                                .background(
                                    MaterialTheme.colorScheme.primary,
                                    RoundedCornerShape(12.dp),
                                )
                                .padding(10.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                    is ChatItem.Bot -> Column(
                        Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(12.dp),
                            )
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(item.reply.text, style = MaterialTheme.typography.bodyMedium)
                        item.reply.latex?.let {
                            KatexView(it, Modifier.fillMaxWidth())
                        }
                        item.reply.steps.forEach { step ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (step.verified) "✓ " else "• ",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                Column {
                                    Text(step.text, style = MaterialTheme.typography.bodySmall)
                                    KatexView(step.latex, Modifier.fillMaxWidth())
                                }
                            }
                        }
                        if (item.reply.sources.isNotEmpty()) {
                            Text(
                                str.sources + item.reply.sources.joinToString(" · ") { s ->
                                    when (s.source) {
                                        ChunkSource.Notes -> "📝 your notes: ${s.chunk.heading}"
                                        ChunkSource.Textbook ->
                                            "📄 [${s.chunk.chunk_id}] ${s.chunk.heading}"
                                    }
                                },
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
        }

        MathSymbolBar(onInsert = { input = input.insert(it) })

        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(placeholder) },
            )
            Button(
                onClick = {
                    viewModel.ask(input.text.trim(), language = null)
                    input = TextFieldValue("")
                },
                enabled = !busy && input.text.isNotBlank(),
            ) {
                Text(if (busy) "…" else str.ask)
            }
        }
    }
}

/**
 * A scrollable row of maths symbols students can drop into the question box:
 * square root and the superscript powers (²,³…) so an expression like
 * "x² − 2x − 1" can be typed without a maths keyboard. The superscripts are
 * real Unicode characters, so they render small inline — in the input, in the
 * sent message, and in retrieval — with no extra formatting layer.
 */
@Composable
private fun MathSymbolBar(onInsert: (String) -> Unit) {
    // label shown on the chip → exact text inserted at the cursor
    val symbols = listOf(
        "√" to "√", "∛" to "∛", "x²" to "²", "x³" to "³", "xⁿ" to "^",
        "π" to "π", "θ" to "θ", "×" to "×", "÷" to "÷", "½" to "½",
        "≤" to "≤", "≥" to "≥", "≠" to "≠", "°" to "°", "( )" to "()",
    )
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        symbols.forEach { (label, insert) ->
            SuggestionChip(
                onClick = { onInsert(insert) },
                label = { Text(label) },
                shape = RoundedCornerShape(10.dp),
            )
        }
    }
}

/** Inserts [s] at the current cursor (or over the selection) and keeps the
 * caret just after what was inserted. */
private fun TextFieldValue.insert(s: String): TextFieldValue {
    val start = selection.start.coerceIn(0, text.length)
    val end = selection.end.coerceIn(0, text.length)
    val newText = text.substring(0, start) + s + text.substring(end)
    // For "()" drop the caret between the brackets; otherwise after the insert.
    val caret = if (s == "()") start + 1 else start + s.length
    return TextFieldValue(newText, TextRange(caret))
}

/**
 * Bring-your-own-notes (PRD §8): import a .txt/.md file into the active subject,
 * list imported files, and surface a short status line. The picker is restricted
 * to text MIME types — the only formats this build parses.
 */
@Composable
private fun ImportBar(viewModel: AppViewModel) {
    val imported by viewModel.importedFiles.collectAsState()
    val notice by viewModel.notice.collectAsState()
    val str = LocalStrings.current
    // OpenDocument lets us offer several types (text, JSON, PDF) at once.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importNotes(uri)
    }
    val importTypes = arrayOf("text/*", "application/json", "application/pdf", "image/*")

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = { picker.launch(importTypes) }) { Text(str.importNotes) }
            if (imported.isNotEmpty()) {
                Text(
                    "${imported.size} file(s) in this subject",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        imported.forEach { file ->
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "📝 ${file.name} · ${file.chunkCount} chunks",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { viewModel.deleteImport(file) }) { Text(str.remove) }
            }
        }
        notice?.let { msg ->
            Text(msg, style = MaterialTheme.typography.labelMedium)
            LaunchedEffect(msg) {
                delay(2500)
                viewModel.clearNotice()
            }
        }
    }
}
