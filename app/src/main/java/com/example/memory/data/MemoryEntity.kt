package com.example.memory.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class MemoryCategory(val displayName: String, val colorHex: Long) {
    FACT("Personal Fact", 0xFF00A3FF),
    PREFERENCE("Preference", 0xFF10B981),
    PROJECT("Project & Work", 0xFFF59E0B),
    LINGUISTIC("Language & Style", 0xFF8B5CF6),
    EPISODIC("Experience & Event", 0xFFEC4899),
    GENERAL("General Note", 0xFF94A3B8);

    companion object {
        fun fromString(name: String): MemoryCategory {
            return entries.find { it.name.equals(name, ignoreCase = true) } ?: GENERAL
        }
    }
}

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val category: String = MemoryCategory.GENERAL.name,
    val importance: Int = 3, // 1 (minor detail) to 5 (critical core fact)
    val timestamp: Long = System.currentTimeMillis(),
    val lastAccessed: Long = System.currentTimeMillis(),
    val accessCount: Int = 0,
    val keywords: String = "", // Comma-separated search keywords
    val source: String = "USER_NOTE", // "USER_NOTE" or "AUTO_EXTRACTED"
    val embeddingJson: String? = null // Serialized List<Float>
) {
    val categoryEnum: MemoryCategory
        get() = MemoryCategory.fromString(category)

    val keywordList: List<String>
        get() = keywords.split(",").map { it.trim().lowercase() }.filter { it.isNotBlank() }
}
