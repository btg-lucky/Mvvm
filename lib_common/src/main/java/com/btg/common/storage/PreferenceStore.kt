package com.btg.common.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * DataStore(Preferences) 封装：类型化 KV，读为 Flow、写为 suspend。
 * 每个 name 对应一个独立文件；同名请复用同一实例。
 */
class PreferenceStore(context: Context, name: String = "app_prefs") {

    private val appContext = context.applicationContext
    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        produceFile = { appContext.preferencesDataStoreFile(name) },
    )

    fun getString(key: String, default: String = ""): Flow<String> =
        dataStore.data.map { it[stringPreferencesKey(key)] ?: default }

    suspend fun putString(key: String, value: String) {
        dataStore.edit { it[stringPreferencesKey(key)] = value }
    }

    fun getInt(key: String, default: Int = 0): Flow<Int> =
        dataStore.data.map { it[intPreferencesKey(key)] ?: default }

    suspend fun putInt(key: String, value: Int) {
        dataStore.edit { it[intPreferencesKey(key)] = value }
    }

    fun getBoolean(key: String, default: Boolean = false): Flow<Boolean> =
        dataStore.data.map { it[booleanPreferencesKey(key)] ?: default }

    suspend fun putBoolean(key: String, value: Boolean) {
        dataStore.edit { it[booleanPreferencesKey(key)] = value }
    }

    fun getLong(key: String, default: Long = 0L): Flow<Long> =
        dataStore.data.map { it[longPreferencesKey(key)] ?: default }

    suspend fun putLong(key: String, value: Long) {
        dataStore.edit { it[longPreferencesKey(key)] = value }
    }

    fun getFloat(key: String, default: Float = 0f): Flow<Float> =
        dataStore.data.map { it[floatPreferencesKey(key)] ?: default }

    suspend fun putFloat(key: String, value: Float) {
        dataStore.edit { it[floatPreferencesKey(key)] = value }
    }

    suspend fun remove(key: String) {
        dataStore.edit { prefs ->
            prefs.asMap().keys.filter { it.name == key }.forEach { prefs.remove(it) }
        }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }
}
