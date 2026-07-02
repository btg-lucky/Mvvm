package com.btg.mvvm

import android.view.LayoutInflater
import com.btg.common.base.BaseActivity
import com.btg.mvvm.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint

/** 单 Activity 宿主：承载 Navigation 图，各能力演示在 Fragment 中。 */
@AndroidEntryPoint
class MainActivity : BaseActivity<ActivityMainBinding>() {
    override fun inflateBinding(inflater: LayoutInflater) = ActivityMainBinding.inflate(inflater)
}
