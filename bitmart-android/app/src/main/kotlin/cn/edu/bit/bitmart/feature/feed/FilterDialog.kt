package cn.edu.bit.bitmart.feature.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions

/**
 * 买卖列表的筛选弹窗：价格区间、是否含面议、是否含售罄、标签多选。
 * 支持清空 / 取消 / 确认（架构 §6.3 + UI 规范“筛选弹窗”）。
 *
 * 标签说明：后端按 tagIds(Long) 过滤，而热门标签仅提供名称且无“名称→ID”查询接口，
 * 故标签选择目前仅作回显，确认后不会下发到查询（见 [ListingFeedViewModel.applyFilter]）。
 *
 * @param initial 当前已应用的筛选状态，用于回显。
 * @param loadTags 拉取热门标签（挂起）。
 * @param onConfirm (minPrice, maxPrice, includeNoPrice, includeSold, selectedTags)。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterDialog(
    initial: FeedUiState,
    loadTags: suspend () -> List<String>,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
    onConfirm: (String, String, Boolean, Boolean, List<String>) -> Unit,
) {
    var minPrice by remember { mutableStateOf(initial.minPrice) }
    var maxPrice by remember { mutableStateOf(initial.maxPrice) }
    var includeNoPrice by remember { mutableStateOf(initial.includeNoPrice) }
    var includeSold by remember { mutableStateOf(initial.includeSold) }
    val selectedTags = remember { mutableStateListOf<String>().apply { addAll(initial.selectedTags) } }
    var popularTags by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(Unit) { popularTags = loadTags() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("筛选条件") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = minPrice,
                        onValueChange = { minPrice = it },
                        label = { Text("最低价") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("—")
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = maxPrice,
                        onValueChange = { maxPrice = it },
                        label = { Text("最高价") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(checked = includeNoPrice, onCheckedChange = { includeNoPrice = it })
                    Text("包含面议")
                    Spacer(Modifier.width(16.dp))
                    Checkbox(checked = includeSold, onCheckedChange = { includeSold = it })
                    Text("包含售罄")
                }

                if (popularTags.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("标签（暂不参与过滤）", style = MaterialTheme.typography.labelMedium)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        popularTags.forEach { tag ->
                            val selected = tag in selectedTags
                            FilterChip(
                                selected = selected,
                                onClick = { if (selected) selectedTags.remove(tag) else selectedTags.add(tag) },
                                label = { Text(tag) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(minPrice, maxPrice, includeNoPrice, includeSold, selectedTags.toList())
                onDismiss()
            }) { Text("确认") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    onClear()
                    onDismiss()
                }) { Text("清空") }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}
