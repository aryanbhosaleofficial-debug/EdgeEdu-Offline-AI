package com.edgeedu.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.edgeedu.app.session.ChapterInfo
import com.edgeedu.app.session.Subject
import com.edgeedu.app.ui.theme.EdgeEduGradients
import com.edgeedu.app.ui.theme.style

/**
 * The chapter picker shown when a subject is tapped (PRD §6.1). Choosing a
 * chapter starts a session focused on just that chapter, so the tutor's
 * retrieval stays tight and doubt-solving is more precise; "Whole subject"
 * keeps the old behaviour of asking across every chapter.
 */
@Composable
fun ChapterScreen(
    subject: Subject,
    chapters: List<ChapterInfo>,
    onBack: () -> Unit,
    onChapterChosen: (chapterId: Int?) -> Unit,
) {
    val str = LocalStrings.current
    val style = subject.style()

    Column(Modifier.fillMaxSize()) {
        // Subject header, echoing the home cards so the transition reads as
        // "drilling into" the tapped subject.
        Box(
            Modifier
                .fillMaxWidth()
                .background(style.gradient, RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                .padding(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Text(style.emoji, style = MaterialTheme.typography.titleMedium) }
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(
                            str.subjectName(subject),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            "${chapters.size} ${str.chaptersWord}",
                            color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
        ) {
            item {
                TextButton(onClick = onBack, modifier = Modifier.padding(bottom = 2.dp)) {
                    Text("← ${str.chooseSubject}")
                }
                Text(str.chooseChapter, style = MaterialTheme.typography.titleMedium)
                Text(
                    str.chooseChapterDesc,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }

            // "Whole subject" — the previous behaviour, kept as the first option.
            item {
                ChapterCard(
                    lead = "📚",
                    tint = style.tint,
                    title = str.allChapters,
                    subtitle = str.allChaptersDesc,
                    highlighted = true,
                    onClick = { onChapterChosen(null) },
                )
            }

            items(chapters) { chapter ->
                ChapterCard(
                    lead = "${chapter.id}",
                    tint = style.tint,
                    title = chapter.title.ifBlank { "${str.chapterWord} ${chapter.id}" },
                    subtitle = "${chapter.chunkCount} ${str.topicsWord}",
                    highlighted = false,
                    onClick = { onChapterChosen(chapter.id) },
                )
            }
        }
    }
}

@Composable
private fun ChapterCard(
    lead: String,
    tint: Color,
    title: String,
    subtitle: String,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .background(
                        if (highlighted) EdgeEduGradients.Header
                        else androidx.compose.ui.graphics.SolidColor(tint.copy(alpha = 0.12f)),
                        RoundedCornerShape(12.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    lead,
                    color = if (highlighted) Color.White else tint,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
