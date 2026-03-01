package com.doptsw.dongdiary.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

data class UserSettings(
    val isLoggedIn: Boolean = false,
    val googleAccountId: String? = null,
    val googleDisplayName: String? = null,
    val googleEmail: String? = null,
    val autoBackupOnExit: Boolean = false,
    val passcodeHash: String? = null,
)

private val Context.dataStore by preferencesDataStore(name = "dong_diary_settings")

private object SettingsKeys {
    val IS_LOGGED_IN: Preferences.Key<Boolean> = booleanPreferencesKey("is_logged_in")
    val GOOGLE_ACCOUNT_ID: Preferences.Key<String> = stringPreferencesKey("google_account_id")
    val GOOGLE_DISPLAY_NAME: Preferences.Key<String> = stringPreferencesKey("google_display_name")
    val GOOGLE_EMAIL: Preferences.Key<String> = stringPreferencesKey("google_email")
    val AUTO_BACKUP_ON_EXIT: Preferences.Key<Boolean> = booleanPreferencesKey("auto_backup_on_exit")
    val PASSCODE_HASH: Preferences.Key<String> = stringPreferencesKey("passcode_hash")
}

class SettingsRepository(
    private val context: Context,
) {

    val settingsFlow: Flow<UserSettings> = context.dataStore.data
        .catch { e ->
            if (e is IOException) {
                emit(emptyPreferences())
            } else {
                throw e
            }
        }
        .map { prefs ->
            UserSettings(
                isLoggedIn = prefs[SettingsKeys.IS_LOGGED_IN] ?: false,
                googleAccountId = prefs[SettingsKeys.GOOGLE_ACCOUNT_ID],
                googleDisplayName = prefs[SettingsKeys.GOOGLE_DISPLAY_NAME],
                googleEmail = prefs[SettingsKeys.GOOGLE_EMAIL],
                autoBackupOnExit = prefs[SettingsKeys.AUTO_BACKUP_ON_EXIT] ?: false,
                passcodeHash = prefs[SettingsKeys.PASSCODE_HASH],
            )
        }

    suspend fun updateAutoBackup(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[SettingsKeys.AUTO_BACKUP_ON_EXIT] = enabled
        }
    }

    suspend fun updateLoginState(
        isLoggedIn: Boolean,
        accountId: String?,
        displayName: String?,
        email: String?,
    ) {
        context.dataStore.edit { prefs ->
            prefs[SettingsKeys.IS_LOGGED_IN] = isLoggedIn
            prefs[SettingsKeys.GOOGLE_ACCOUNT_ID] = accountId.orEmpty()
            prefs[SettingsKeys.GOOGLE_DISPLAY_NAME] = displayName.orEmpty()
            prefs[SettingsKeys.GOOGLE_EMAIL] = email.orEmpty()
        }
    }

    suspend fun setPasscodeHash(hash: String) {
        context.dataStore.edit { prefs ->
            prefs[SettingsKeys.PASSCODE_HASH] = hash
        }
    }

    suspend fun clearPasscodeHash() {
        context.dataStore.edit { prefs ->
            prefs.remove(SettingsKeys.PASSCODE_HASH)
        }
    }

    fun hasPasscode(settings: UserSettings): Boolean {
        return !settings.passcodeHash.isNullOrBlank()
    }
}


