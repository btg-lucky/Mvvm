package com.btg.mvvm.ui.demo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.btg.common.base.BaseFragment
import com.btg.common.ext.collectOnStarted
import com.btg.common.storage.PreferenceStore
import com.btg.common.storage.SecurePreferences
import com.btg.mvvm.data.local.AppDatabase
import com.btg.mvvm.data.local.NewsFavorite
import com.btg.mvvm.databinding.FragmentStorageBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 存储演示：DataStore 计数、加密存 token、Room 收藏增查。 */
class StorageFragment : BaseFragment<FragmentStorageBinding>() {

    private val prefs by lazy { PreferenceStore(requireContext()) }
    private val secure by lazy { SecurePreferences(requireContext()) }
    private val db by lazy {
        Room.databaseBuilder(
            requireContext().applicationContext,
            AppDatabase::class.java,
            "app.db",
        ).build()
    }

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentStorageBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnDataStore.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val current = prefs.getInt("count").first()
                prefs.putInt("count", current + 1)
                appendOutput("DataStore count = ${current + 1}")
            }
        }

        binding.btnSecure.setOnClickListener {
            secure.putString("token", "secret-token-123")
            appendOutput("加密读回 token = ${secure.getString("token")}")
        }

        binding.btnRoom.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                db.favoriteDao().insert(
                    NewsFavorite(
                        url = "https://example.com/${System.currentTimeMillis()}",
                        title = "收藏示例",
                        source = "demo",
                        date = "2026-07-02",
                        imageUrl = null,
                    ),
                )
                val count = db.favoriteDao().getAll().first().size
                appendOutput("Room 收藏总数 = $count")
            }
        }

        db.favoriteDao().getAll().collectOnStarted(viewLifecycleOwner) { list ->
            appendOutput("收藏 Flow 更新，共 ${list.size} 条")
        }
    }

    private fun appendOutput(line: String) {
        binding.output.text = "${binding.output.text}\n$line".trim()
    }
}
