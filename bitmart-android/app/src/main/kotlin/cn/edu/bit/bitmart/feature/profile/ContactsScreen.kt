package cn.edu.bit.bitmart.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.edu.bit.bitmart.core.domain.model.ContactChannel

/**
 * 常用联系方式页：本地保存的联系方式列表（不上传后端），可新增与删除。
 * @param onBack 返回上一页。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    onBack: () -> Unit,
    viewModel: ContactsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var channel by remember { mutableStateOf(ContactChannel.WECHAT) }
    var value by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("常用联系方式") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("新增联系方式（仅保存在本机）", style = MaterialTheme.typography.titleSmall)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                viewModel.channels.take(3).forEach { ch ->
                    FilterChip(channel == ch, { channel = ch }, { Text(ch.name) })
                }
            }
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text("内容（如微信号 / 手机号）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            OutlinedButton(
                onClick = { viewModel.add(channel, value); value = "" },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) { Text("添加") }

            Text("已保存", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
            if (state.contacts.isEmpty()) {
                Text("暂无联系方式", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    itemsIndexed(state.contacts) { index, c ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text("${c.channel}：${c.value}", style = MaterialTheme.typography.bodyLarge)
                                IconButton(onClick = { viewModel.removeAt(index) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "删除")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
