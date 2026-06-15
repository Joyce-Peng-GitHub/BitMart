package cn.edu.bit.bitmart.feature.profile

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * “我的”页：顶部展示昵称/学号/ID（未登录显示“未登录”可点击登录）；右上角邮件图标进入通知页；
 * 下部为常用联系方式、我的商品/收购、设置、关于等栏目入口。
 * @param onLoginClick 未登录时点击进入登录页。
 * @param onNotificationsClick 进入通知页。
 * @param onContactsClick 进入常用联系方式页。
 * @param onMyListingsClick 进入“我的商品”（参数 false）/“我的收购”（参数 true）管理页（占位，#36）。
 * @param onSettingsClick 进入设置页。
 * @param onAboutClick 进入关于页。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onLoginClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onContactsClick: () -> Unit,
    onMyListingsClick: (buy: Boolean) -> Unit,
    onSettingsClick: () -> Unit,
    onAboutClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 每次进入“我的”页（含从通知页返回）刷新未读角标。
    LaunchedEffect(Unit) { viewModel.refreshUnreadCount() }

    // 异常（如网络异常）用 Toast 提示，不再显示在账号信息区。
    LaunchedEffect(state.error) {
        state.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeError()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("我的") },
                actions = {
                    IconButton(onClick = onNotificationsClick) {
                        BadgedBox(
                            badge = {
                                if (state.unreadCount > 0) {
                                    Badge { Text(if (state.unreadCount > 99) "99+" else state.unreadCount.toString()) }
                                }
                            },
                        ) {
                            Icon(Icons.Default.MailOutline, contentDescription = "通知")
                        }
                    }
                },
            )
        },
    ) { padding ->
        // 下拉刷新：重新校验登录态并拉取最新用户信息（网络不佳时主动重试）。
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            ) {
                ProfileHeader(state = state, onLoginClick = onLoginClick)

                SectionRow("常用联系方式", onClick = onContactsClick)
                SectionRow("我的商品", onClick = { onMyListingsClick(false) })
                SectionRow("我的收购", onClick = { onMyListingsClick(true) })
                SectionRow("设置", onClick = onSettingsClick)
                SectionRow("关于", onClick = onAboutClick)
            }
        }
    }
}

@Composable
private fun ProfileHeader(state: ProfileUiState, onLoginClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp).let {
            if (!state.loggedIn) it.clickable(onClick = onLoginClick) else it
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            if (state.loggedIn) {
                val user = state.user
                Text(user?.displayName ?: "我的", style = MaterialTheme.typography.headlineSmall)
                if (user != null) {
                    Text("昵称：${user.nickname ?: "匿名"}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                    Text("学号：${user.studentId}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                    Text("ID：${user.id}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                }
            } else {
                Text("未登录", style = MaterialTheme.typography.titleLarge)
                Text("点击登录", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
private fun SectionRow(title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
    HorizontalDivider()
}
