package com.smartworkalarm.prealarm

import java.time.Instant

data class AlarmOccurrenceKey(
    val kind: AlarmKind,
    val id: Long,
    val scheduledAt: Instant,
) {
    fun encode(): String = "$VERSION:${kind.name}:$id:${scheduledAt.toEpochMilli()}"

    companion object {
        fun decode(encoded: String): AlarmOccurrenceKey? {
            val parts = encoded.split(ENTRY_SEPARATOR)
            if (parts.size != 4 || parts[0] != VERSION) return null

            return runCatching {
                AlarmOccurrenceKey(
                    kind = AlarmKind.valueOf(parts[1]),
                    id = parts[2].toLong(),
                    scheduledAt = Instant.ofEpochMilli(parts[3].toLong()),
                )
            }.getOrNull()
        }

        private const val VERSION = "v1"
        private const val ENTRY_SEPARATOR = ':'
    }
}

enum class AlarmKind {
    REGULAR,
    SHIFT,
}
