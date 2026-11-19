package com.edgeedu.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.edgeedu.app.session.Subject

/** Per-subject visuals from the design kit: an emoji and a card gradient. */
data class SubjectStyle(val emoji: String, val gradient: Brush, val tint: Color)

fun Subject.style(): SubjectStyle = when (this) {
    Subject.Maths1 -> SubjectStyle(
        "📐",
        Brush.linearGradient(listOf(Color(0xFF4A55E8), Color(0xFF8B5CF6))),
        Color(0xFF4A55E8),
    )
    Subject.Maths2 -> SubjectStyle(
        "📐",
        Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFFA855F7))),
        Color(0xFF6366F1),
    )
    Subject.Science -> SubjectStyle(
        "⚗️",
        Brush.linearGradient(listOf(Color(0xFF00C9A7), Color(0xFF0EA5E9))),
        Color(0xFF00C9A7),
    )
    Subject.Science1 -> SubjectStyle(
        "⚗️",
        Brush.linearGradient(listOf(Color(0xFF00C9A7), Color(0xFF0EA5E9))),
        Color(0xFF00C9A7),
    )
    Subject.Science2 -> SubjectStyle(
        "🔬",
        Brush.linearGradient(listOf(Color(0xFF06B6D4), Color(0xFF3B82F6))),
        Color(0xFF06B6D4),
    )
    Subject.Geography -> SubjectStyle(
        "🌍",
        Brush.linearGradient(listOf(Color(0xFF10B981), Color(0xFF34D399))),
        Color(0xFF10B981),
    )
    Subject.SocialStudies -> SubjectStyle(
        "📜",
        Brush.linearGradient(listOf(Color(0xFFF59E0B), Color(0xFFEF4444))),
        Color(0xFFF59E0B),
    )
}
