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
import com.btg.mine.databinding.FragmentLoginBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LoginFragment : BaseFragment<FragmentLoginBinding>() {

    private val viewModel: LoginViewModel by viewModels()

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentLoginBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnLogin.setOnDebouncedClickListener {
            viewModel.login(
                binding.usernameEdit.text.toString(),
                binding.passwordEdit.text.toString(),
            )
        }
        binding.btnGoRegister.setOnDebouncedClickListener {
            findNavController().navigate(R.id.action_login_to_register)
        }

        viewModel.isSubmitting.collectOnStarted(viewLifecycleOwner) {
            binding.btnLogin.isEnabled = !it
        }
        viewModel.loginSuccess.collectOnStarted(viewLifecycleOwner) {
            requireContext().toast(getString(R.string.login_success))
            findNavController().popBackStack()
        }
        viewModel.errorEvent.collectOnStarted(viewLifecycleOwner) { requireContext().toast(it) }
    }
}
