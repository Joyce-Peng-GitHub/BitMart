package cn.edu.bit.bitmart.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * 账号设置页：修改昵称、修改密码（统一身份认证）、退出登录、注销账号。
 * 未登录时跳转登录页（由 onRequireLogin 处理）。
 * @param onBack 返回上一页。
 * @param onRequireLogin 未登录时跳转登录页。
 * @param onLoggedOut 退出登录 / 注销成功后的回调（通常返回上一页）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSettingsScreen(
    onBack: () -> Unit,
    onRequireLogin: () -> Unit,
    onLoggedOut: () -> Unit,
    viewModel: AccountSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // 冷进入时未登录 → 跳转登录页。主动退出/注销由下方 onLoggedOut 单独处理，
    // 不在此重复导航（否则与 loggedOut 回调竞争，可能把刚登出的用户留在登录页）。
    LaunchedEffect(state.loggedIn, state.loggedOut) {
        if (!state.loggedIn && !state.loggedOut) onRequireLogin()
    }
    // 退出 / 注销成功 → 回调。
    LaunchedEffect(state.loggedOut) {
        if (state.loggedOut) onLoggedOut()
    }

    var nickname by remember(state.user?.id) { mutableStateOf(state.user?.nickname ?: "") }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("账号设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
        ) {
            state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Text("修改昵称", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it },
                label = { Text("昵称（留空显示为匿名）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Button(
                onClick = { viewModel.updateNickname(nickname) },
                enabled = !state.loading,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) { Text("保存昵称") }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            Button(
                onClick = { showPasswordDialog = true },
                enabled = !state.loading,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("修改密码") }

            OutlinedButton(
                onClick = { viewModel.logout() },
                enabled = !state.loading,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) { Text("退出登录") }

            OutlinedButton(
                onClick = { showDeleteDialog = true },
                enabled = !state.loading,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) { Text("注销账号", color = MaterialTheme.colorScheme.error) }
        }
    }

    if (showPasswordDialog) {
        ChangePasswordDialog(
            defaultStudentId = state.user?.studentId ?: "",
            onConfirm = { sid, unified, newPwd ->
                viewModel.changePassword(sid, unified, newPwd)
                showPasswordDialog = false
            },
            onDismiss = { showPasswordDialog = false },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("注销账号") },
            text = { Text("注销后账号与数据将无法恢复，确认继续？") },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; viewModel.deleteAccount() }) {
                    Text("确认注销", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun ChangePasswordDialog(
    defaultStudentId: String,
    onConfirm: (studentId: String, unifiedPassword: String, newPassword: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var studentId by remember { mutableStateOf(defaultStudentId) }
    var unifiedPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改密码") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("经统一身份认证后可修改 BitMart 密码。", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(studentId, { studentId = it }, label = { Text("学号") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    unifiedPassword, { unifiedPassword = it },
                    label = { Text("统一身份认证密码") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    newPassword, { newPassword = it },
                    label = { Text("新的 BitMart 密码") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(studentId, unifiedPassword, newPassword) }) { Text("确认") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
