package com.dani.assistant.core.search

/** يحفظ العنصر اللي لمسه المستخدم في البحث الشامل باش الشاشة المقصودة تفتحو (مرة وحدة). */
object SearchFocus {
    @Volatile private var taskId: Long? = null
    @Volatile private var text: String? = null
    @Volatile private var textKind: String? = null

    fun remember(h: Hit) {
        taskId = null; text = null; textKind = null
        when (h.kind) {
            "مهمة" -> taskId = h.ref.toLongOrNull()
            "معلومة", "معرفة" -> if (h.ref.isNotBlank()) { text = h.ref; textKind = h.kind }
        }
    }

    /** المهمة المطلوبة (بدون مسح). تتمسح بـ clearTask لما الشاشة تلقاها. */
    fun peekTask(): Long? = taskId
    fun clearTask() { taskId = null }

    /** النص المطلوب في الذاكرة + نوعو، ويتمسح. */
    fun takeText(): Pair<String, String>? {
        val t = text; val k = textKind
        text = null; textKind = null
        return if (t != null && k != null) t to k else null
    }
}
