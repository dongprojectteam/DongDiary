package com.doptsw.dongdiary.data

import java.time.LocalDate
import java.time.LocalDateTime

class DiaryRepository(
    private val store: LocalJsonStore,
) {

    fun getAll(): DiaryFile = store.load()

    fun getAllSortedByUpdatedAtDesc(): List<DiaryEntry> {
        val formatter = java.time.format.DateTimeFormatter.ISO_DATE_TIME
        return store.load().entries.sortedWith(
            compareByDescending<DiaryEntry> { entry ->
                runCatching { LocalDateTime.parse(entry.updatedAt, formatter) }.getOrNull()
            }.thenByDescending { entry ->
                runCatching { LocalDate.parse(entry.date) }.getOrNull()
            }.thenByDescending { it.updatedAt }
        )
    }

    fun getEntriesForDate(date: LocalDate): List<DiaryEntry> {
        val id = date.toString()
        return store.load().entries.filter { it.id == id }
    }

    fun getHistoricalEntriesForSameDay(date: LocalDate): List<DiaryEntry> {
        val month = date.monthValue
        val day = date.dayOfMonth
        return store.load().entries.filter { entry ->
            val d = LocalDate.parse(entry.date)
            d.monthValue == month && d.dayOfMonth == day && d.year < date.year
        }
    }

    fun saveOrUpdateToday(content: String, images: List<DiaryImage> = emptyList()) {
        val today = LocalDate.now()
        val now = LocalDateTime.now().toString()
        val todayId = today.toString()
        val current = store.load()

        val existing = current.entries.find { it.id == todayId }
        val updatedEntries = if (existing == null) {
            current.entries + DiaryEntry(
                id = todayId,
                date = today.toString(),
                content = content,
                images = images,
                createdAt = now,
                updatedAt = now,
            )
        } else {
            current.entries.map {
                if (it.id == todayId) {
                    it.copy(
                        content = content,
                        images = images,
                        updatedAt = now,
                    )
                } else {
                    it
                }
            }
        }

        val nextVersion = current.version + 1
        val nextFile = DiaryFile(
            version = nextVersion,
            entries = updatedEntries,
        )
        store.save(nextFile)
    }

    fun addImagesToToday(images: List<DiaryImage>) {
        val todayId = LocalDate.now().toString()
        val current = store.load()
        val existing = current.entries.find { it.id == todayId }

        if (existing != null) {
            val newImages = existing.images + images
            val updatedEntries = current.entries.map {
                if (it.id == todayId) {
                    it.copy(
                        images = newImages,
                        updatedAt = LocalDateTime.now().toString(),
                    )
                } else {
                    it
                }
            }
            val nextFile = current.copy(
                version = current.version + 1,
                entries = updatedEntries,
            )
            store.save(nextFile)
        }
    }

    fun deleteImageFromToday(imageId: String) {
        val todayId = LocalDate.now().toString()
        val current = store.load()
        val existing = current.entries.find { it.id == todayId }

        if (existing != null) {
            val newImages = existing.images.filterNot { it.id == imageId }
            val updatedEntries = current.entries.map {
                if (it.id == todayId) {
                    it.copy(
                        images = newImages,
                        updatedAt = LocalDateTime.now().toString(),
                    )
                } else {
                    it
                }
            }
            val nextFile = current.copy(
                version = current.version + 1,
                entries = updatedEntries,
            )
            store.save(nextFile)
        }
    }

    fun deleteToday() {
        val todayId = LocalDate.now().toString()
        val current = store.load()
        if (current.entries.none { it.id == todayId }) return

        val updatedEntries = current.entries.filterNot { it.id == todayId }
        val nextFile = current.copy(
            version = current.version + 1,
            entries = updatedEntries,
        )
        store.save(nextFile)
    }

    fun overwriteAll(from: DiaryFile) {
        store.save(from)
    }
}


