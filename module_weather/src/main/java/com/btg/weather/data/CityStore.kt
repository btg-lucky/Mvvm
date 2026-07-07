package com.btg.weather.data

import com.btg.common.storage.PreferenceStore
import kotlinx.coroutines.flow.Flow

/** 选定城市存取抽象：便于测试注入 fake。空串表示未选择过。 */
interface CityStore {
    val currentCity: Flow<String>
    suspend fun save(city: String)
}

/** DataStore 实现：选定城市持久化，冷启动自动恢复。 */
class DataStoreCityStore(private val store: PreferenceStore) : CityStore {

    override val currentCity: Flow<String> = store.getString(KEY_CITY, "")

    override suspend fun save(city: String) {
        store.putString(KEY_CITY, city)
    }

    private companion object {
        const val KEY_CITY = "selected_city"
    }
}
