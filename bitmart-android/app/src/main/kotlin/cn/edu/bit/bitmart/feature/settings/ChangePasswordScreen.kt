package cn.edu.bit.bitmart.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.edu.bit.bitmart.R
import cn.edu.bit.bitmart.core.ui.PasswordField

/**
 * 修改密码全屏页（格式与注册页一致）：学号 + 统一身份认证密码 + 新密码 + 确认新密码。
 * 复用 [AccountSettingsViewModel.changePassword]。改密成功会吊销会话（本地登出），
 * 故成功后回调跳转登录页。
 * @param onBack 返回上一页。
 * @param onPasswordChanged 改密成功后的导航回调（跳登录页）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangePasswordScreen(
    onBack: () -> Unit,
    onPasswordChanged: () -> Unit,
    viewModel: AccountSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    var studentId by remember(state.user?.id) { mutableStateOf(state.user?.studentId ?: "") }
    var unifiedPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    LaunchedEffect(state.passwordChanged) {
        if (state.passwordChanged) onPasswordChanged()
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.account_change_password)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.account_change_password_hint), style = MaterialTheme.typography.bodySmall)

            OutlinedTextField(
                value = studentId,
                onValueChange = { studentId = it },
                label = { Text(stringResource(R.string.auth_student_id)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            PasswordField(
                value = unifiedPassword,
                onValueChange = { unifiedPassword = it },
                label = stringResource(R.string.account_unified_password),
                modifier = Modifier.fillMaxWidth(),
            )
            PasswordField(
                value = newPassword,
                onValueChange = { newPassword = it },
                label = stringResource(R.string.account_new_password),
                modifier = Modifier.fillMaxWidth(),
            )
            PasswordField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = stringResource(R.string.account_confirm_new_password),
                modifier = Modifier.fillMaxWidth(),
            )

            state.error?.let { Text(it.asString(), color = MaterialTheme.colorScheme.error) }

            Button(
                onClick = { viewModel.changePassword(studentId, unifiedPassword, newPassword, confirmPassword) },
                enabled = !state.loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.loading) CircularProgressIndicator(modifier = Modifier.height(20.dp))
                else Text(stringResource(R.string.account_action_change_password))
            }
        }
    }
}
