package com.btg.mine.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.btg.common.base.BaseFragment
import com.btg.common.ext.collectOnStarted
import com.btg.common.ext.setOnDebouncedClickListener
import com.btg.mine.R
import com.btg.mine.databinding.FragmentMineBinding
import dagger.hilt.android.AndroidEntryPoint

/** 我的页：依登录态切换 未登录入口 / 用户信息 + 退出登录。 */
@AndroidEntryPoint
class MineFragment : BaseFragment<FragmentMineBinding>() {

    private val viewModel: MineViewModel by viewModels()

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentMineBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnGoLogin.setOnDebouncedClickListener {
            findNavController().navigate(R.id.action_mine_to_login)
        }
        binding.btnLogout.setOnDebouncedClickListener { viewModel.logout() }

        viewModel.currentUser.collectOnStarted(viewLifecycleOwner) { user ->
            binding.guestGroup.isVisible = user == null
            binding.userGroup.isVisible = user != null
            binding.usernameText.text = user.orEmpty()
        }
    }
}
