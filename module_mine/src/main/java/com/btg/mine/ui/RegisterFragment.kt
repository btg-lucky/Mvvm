package com.btg.mine.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.btg.common.base.BaseFragment
import com.btg.common.ext.collectOnStarted
import com.btg.common.ext.setOnDebouncedClickListener
import com.btg.common.ui.toast
import com.btg.mine.R
import com.btg.mine.databinding.FragmentRegisterBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class RegisterFragment : BaseFragment<FragmentRegisterBinding>() {

    private val viewModel: RegisterViewModel by viewModels()

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentRegisterBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnRegister.setOnDebouncedClickListener {
            viewModel.register(
                binding.usernameEdit.text.toString(),
                binding.passwordEdit.text.toString(),
                binding.confirmEdit.text.toString(),
            )
        }

        viewModel.isSubmitting.collectOnStarted(viewLifecycleOwner) {
            binding.btnRegister.isEnabled = !it
        }
        viewModel.registerSuccess.collectOnStarted(viewLifecycleOwner) {
            requireContext().toast(getString(R.string.register_success))
            // 注册即自动登录，直接回到我的页
            findNavController().popBackStack(R.id.mineFragment, false)
        }
        viewModel.errorEvent.collectOnStarted(viewLifecycleOwner) { requireContext().toast(it) }
    }
}
