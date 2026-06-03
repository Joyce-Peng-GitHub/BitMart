package cn.edu.bit.bitmart.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * “我的商品 / 我的收购”管理页占位（完整的增删改查为后续任务 #36）。
 * 暂以“即将上线”提示占位，保留入口与路由。
 * @param buy true 表示“我的收购”，false 表示“我的商品”。
 * @param onBack 返回上一页。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyListingsPlaceholderScreen(buy: Boolean, onBack: () -> Unit) {
    val title = if (buy) "我的收购" else "我的商品"
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
            Text("即将上线", style = MaterialTheme.typography.titleLarge)
            Text("$title 的管理功能正在开发中。", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
