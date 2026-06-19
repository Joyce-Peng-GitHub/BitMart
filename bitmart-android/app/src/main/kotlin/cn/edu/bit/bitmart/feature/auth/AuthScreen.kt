package cn.edu.bit.bitmart.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.edu.bit.bitmart.R
import cn.edu.bit.bitmart.core.ui.PasswordField

/**
 * 登录/注册屏。注册模式下额外要求统一身份认证密码（用于 verify）与可选昵称。
 * @param onAuthenticated 登录/注册成功后的导航回调。
 * @param onBack 返回上一页。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    onAuthenticated: () -> Unit,
    onBack: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var registerMode by remember { mutableStateOf(false) }
    var unifiedPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    LaunchedEffect(state.loggedIn) {
        if (state.loggedIn) onAuthenticated()
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (registerMode) R.string.auth_title_register else R.string.auth_title_login)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            OutlinedTextField(
                value = state.studentId,
                onValueChange = viewModel::onStudentIdChange,
                label = { Text(stringResource(R.string.auth_student_id)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))

            if (registerMode) {
                PasswordField(
                    value = unifiedPassword,
                    onValueChange = { unifiedPassword = it },
                    label = stringResource(R.string.auth_unified_password),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
            }

            PasswordField(
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                label = stringResource(if (registerMode) R.string.auth_password_set else R.string.auth_password),
                modifier = Modifier.fillMaxWidth(),
            )

            if (registerMode) {
                Spacer(Modifier.height(12.dp))
                PasswordField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = stringResource(R.string.auth_password_confirm),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.nickname,
                    onValueChange = viewModel::onNicknameChange,
                    label = { Text(stringResource(R.string.auth_nickname)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            state.error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it.asString(), color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { if (registerMode) viewModel.register(unifiedPassword, confirmPassword) else viewModel.login() },
                enabled = !state.loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.loading) CircularProgressIndicator(modifier = Modifier.height(20.dp))
                else Text(stringResource(if (registerMode) R.string.auth_action_register else R.string.auth_action_login))
            }

            TextButton(onClick = { registerMode = !registerMode }) {
                Text(stringResource(if (registerMode) R.string.auth_switch_to_login else R.string.auth_switch_to_register))
            }
        }
    }
}
