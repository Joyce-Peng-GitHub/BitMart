package cn.edu.bit.bitmart.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * “我的”页占位屏：已登录时显示昵称/学号/ID，未登录时显示“未登录，点击登录”。
 * 通知、联系方式、设置、关于等栏目为后续任务。
 * @param onLoginClick 未登录时点击登录的导航回调。
 */
@Composable
fun ProfileScreen(
    onLoginClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (state.loggedIn) {
            val user = state.user
            Text(user?.displayName ?: "我的", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            if (user != null) {
                user.nickname?.let {
                    Text("昵称：$it", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(4.dp))
                }
                Text("学号：${user.studentId}", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(4.dp))
                Text("ID：${user.id}", style = MaterialTheme.typography.bodyLarge)
            }
            state.error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        } else {
            Text("未登录", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onLoginClick, modifier = Modifier.fillMaxWidth()) {
                Text("点击登录")
            }
        }
    }
}
