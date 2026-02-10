package com.doptsw.dongdiary.data

import android.content.Context
import kotlinx.serialization.json.Json
import java.io.File

class LocalJsonStore(
    private val context: Context,
    private val json: Json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    },
) {

    private fun diaryDir(): File = File(context.filesDir, "diary").apply {
        if (!exists()) mkdirs()
    }

    private fun diaryFile(): File = File(diaryDir(), "diary.json")

    fun load(): DiaryFile {
        val file = diaryFile()
        if (!file.exists()) {
            return DiaryFile.empty()
        }
        val text = file.readText()
        return try {
            json.decodeFromString(DiaryFile.serializer(), text)
        } catch (_: Exception) {
            DiaryFile.empty()
        }
    }

    fun save(diaryFile: DiaryFile) {
        val file = diaryFile()
        val text = json.encodeToString(DiaryFile.serializer(), diaryFile)
        file.writeText(text)
    }
}


