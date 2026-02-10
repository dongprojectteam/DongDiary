package com.doptsw.dongdiary.cloud

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import com.doptsw.dongdiary.data.DiaryFile
import com.doptsw.dongdiary.data.DiaryRepository
import com.doptsw.dongdiary.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import com.google.api.client.http.ByteArrayContent

enum class BackupResult {
    SUCCESS,
    NOT_LOGGED_IN,
    ERROR,
}

sealed class RestoreResult {
    object Success : RestoreResult()
    object NotLoggedIn : RestoreResult()
    object NoBackup : RestoreResult()
    object OlderThanLocal : RestoreResult()
    object Error : RestoreResult()
}

/**
 * 현재는 실제 Google Drive 대신 앱 내부의 \"가상 클라우드\" 파일에 저장합니다.
 * 추후 Google Drive appDataFolder 연동 시 이 클래스를 확장하면 됩니다.
 */
class CloudBackupManager(
    private val context: Context,
    private val diaryRepository: DiaryRepository,
    private val settingsRepository: SettingsRepository,
    private val json: Json = Json { prettyPrint = true; ignoreUnknownKeys = true },
) {

    private suspend fun getDriveService(): Drive = withContext(Dispatchers.IO) {
        val settings = settingsRepository.settingsFlow.first()

        val account = GoogleSignIn.getLastSignedInAccount(context)
            ?: throw IllegalStateException(
                "Drive init failed: GoogleSignIn account is NULL | isLoggedIn=${settings.isLoggedIn}"
            )

        if (account.account == null) {
            throw IllegalStateException(
                "Drive init failed: account.account is NULL | email=${account.email}"
            )
        }

        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            setOf(DriveScopes.DRIVE_FILE, DriveScopes.DRIVE_APPDATA),
        ).apply {
            selectedAccount = account.account
        }

        Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName("DongDiary")
            .build()
    }

    suspend fun backupToCloud(): BackupResult {
        val settings = settingsRepository.settingsFlow.first()
        if (!settings.isLoggedIn) return BackupResult.NOT_LOGGED_IN

        val drive = getDriveService()

        val diary = diaryRepository.getAll()
        val jsonText = json.encodeToString(DiaryFile.serializer(), diary)
        val contentStream = ByteArrayContent.fromString("application/json", jsonText)

        withContext(Dispatchers.IO) {
            // appDataFolder 내에 같은 이름 파일이 있는지 확인
            val existing = drive.files().list()
                .setSpaces("appDataFolder")
                .setQ("name = 'diary.json' and trashed = false")
                .setFields("files(id, name)")
                .execute()

            if (existing.files.isNullOrEmpty()) {
                // 새 파일 생성
                val metadata = File().apply {
                    name = "diary.json"
                    parents = listOf("appDataFolder")
                }
                drive.files()
                    .create(metadata, contentStream)
                    .setFields("id")
                    .execute()
            } else {
                // 첫 번째 파일 업데이트
                val fileId = existing.files[0].id
                drive.files()
                    .update(fileId, null, contentStream)
                    .execute()
            }
        }

        return BackupResult.SUCCESS
    }

    suspend fun restoreFromCloudIfNewer(): RestoreResult {
        val settings = settingsRepository.settingsFlow.first()
        if (!settings.isLoggedIn) return RestoreResult.NotLoggedIn

        val drive = getDriveService()

        val result = withContext(Dispatchers.IO) {
            val existing = drive.files().list()
                .setSpaces("appDataFolder")
                .setQ("name = 'diary.json' and trashed = false")
                .setFields("files(id, name)")
                .execute()

            if (existing.files.isNullOrEmpty()) {
                return@withContext RestoreResult.NoBackup
            }

            val fileId = existing.files[0].id
            val outputStream = java.io.ByteArrayOutputStream()
            drive.files()
                .get(fileId)
                .executeMediaAndDownloadTo(outputStream)

            val cloudText = outputStream.toString("UTF-8")
            val cloudDiary = json.decodeFromString(DiaryFile.serializer(), cloudText)
            val localDiary = diaryRepository.getAll()

            if (cloudDiary.version > localDiary.version) {
                diaryRepository.overwriteAll(cloudDiary)
                RestoreResult.Success
            } else {
                RestoreResult.OlderThanLocal
            }
        }

        return result
    }
}


