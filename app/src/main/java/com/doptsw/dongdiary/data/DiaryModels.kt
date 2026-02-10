package com.doptsw.dongdiary.data

import kotlinx.serialization.Serializable

@Serializable
data class DiaryImage(
    val id: String, // UUID
    val filename: String, // filename for reference
    val base64Data: String, // Base64 encoded image data
    val timestamp: String, // ISO-8601 timestamp
)

@Serializable
data class DiaryEntry(
    val id: String, // yyyy-MM-dd
    val date: String, // ISO-8601 yyyy-MM-dd
    val content: String,
    val images: List<DiaryImage> = emptyList(), // Image list
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class DiaryFile(
    val version: Int,
    val entries: List<DiaryEntry>,
) {
    companion object {
        fun empty(): DiaryFile = DiaryFile(
            version = 1,
            entries = emptyList(),
        )
    }
}


