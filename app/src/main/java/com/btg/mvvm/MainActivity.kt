package com.btg.mvvm

import android.os.Bundle
import android.view.LayoutInflater
import androidx.core.view.isVisible
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.btg.common.base.BaseActivity
import com.btg.mvvm.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint
import com.btg.mine.R as MineR
import com.btg.news.R as NewsR
import com.btg.weather.R as WeatherR

/** 单 Activity 壳：底部导航承载 新闻 / 天气 / 我的 三个业务模块。 */
@AndroidEntryPoint
class MainActivity : BaseActivity<ActivityMainBinding>() {

    /** 三个 Tab 的顶级页面：只有停留在这些页面时才显示底部导航。 */
    private val topLevelDestinations = setOf(
        NewsR.id.newsListFragment,
        WeatherR.id.weatherFragment,
        MineR.id.mineFragment,
    )

    override fun inflateBinding(inflater: LayoutInflater) = ActivityMainBinding.inflate(inflater)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val navHost = supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment
        val navController = navHost.navController
        binding.bottomNav.setupWithNavController(navController)
        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.bottomNav.isVisible = destination.id in topLevelDestinations
        }
    }
}
