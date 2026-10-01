package com.dani.assistant.domain.model

enum class PriorityLevel(
    val level: Int,
    val arabicTitle: String,
    val colorHex: Long
) {
    URGENT_CRITICAL(1, "عاجل ومهم", 0xFFEF4444), // Red
    IMPORTANT(2, "مهم", 0xFFF59E0B),           // Amber
    MEDIUM(3, "متوسط", 0xFF3B82F6),            // Blue
    LOW(4, "منخفض", 0xFF10B981),               // Green
    SOMEDAY(5, "لاحقاً", 0xFF64748B);          // Slate Gray

    companion object {
        fun fromLevel(level: Int): PriorityLevel {
            return entries.find { it.level == level } ?: MEDIUM
        }
    }
}
