package cn.edu.bit.bitmart.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cn.edu.bit.bitmart.core.domain.repository.TagInfo

/**
 * 列表筛选条件（买卖列表与"我的"列表共用）。价格用字符串以匹配后端 NUMERIC 文本，空串视为未设置。
 * 默认值面向公开列表；"我的"列表初始化时通常将 [includeSold]/[includeExpired] 设为 true。
 */
data class FilterState(
    val minPrice: String = "",
    val maxPrice: String = "",
    val includeNoPrice: Boolean = true,
    val includeSold: Boolean = false,
    val includeExpired: Boolean = false,
    val selectedTagIds: List<Long> = emptyList(),
)

/**
 * 列表筛选弹窗：价格区间、是否含面议、是否含售罄、是否显示过期项、标签多选。
 * 公开买卖列表与"我的商品/我的收购"共用。
 *
 * @param initial 当前已应用的筛选状态，用于回显。
 * @param loadTags 拉取热门标签（挂起），返回 id+name 对。
 * @param showExpiredToggle 是否展示"显示过期项"开关。公开列表不展示（过期项不公开），"我的"列表展示。
 * @param onConfirm 确认时回传新的 [FilterState]。
 * @param onClear 点击清空（重置为各自上下文的默认）。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ListingFilterDialog(
    initial: FilterState,
    loadTags: suspend () -> List<TagInfo>,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
    onConfirm: (FilterState) -> Unit,
    showExpiredToggle: Boolean = true,
) {
    var minPrice by remember { mutableStateOf(initial.minPrice) }
    var maxPrice by remember { mutableStateOf(initial.maxPrice) }
    var includeNoPrice by remember { mutableStateOf(initial.includeNoPrice) }
    var includeSold by remember { mutableStateOf(initial.includeSold) }
    var includeExpired by remember { mutableStateOf(initial.includeExpired) }
    val selectedTagIds = remember { mutableStateListOf<Long>().apply { addAll(initial.selectedTagIds) } }
    var popularTags by remember { mutableStateOf<List<TagInfo>>(emptyList()) }

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
                if (showExpiredToggle) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Checkbox(checked = includeExpired, onCheckedChange = { includeExpired = it })
                        Text("显示过期项")
                    }
                }

                if (popularTags.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("标签", style = MaterialTheme.typography.labelMedium)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 160.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        popularTags.forEach { tag ->
                            val selected = tag.id in selectedTagIds
                            FilterChip(
                                selected = selected,
                                onClick = { if (selected) selectedTagIds.remove(tag.id) else selectedTagIds.add(tag.id) },
                                label = { Text(tag.name) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(
                    FilterState(
                        minPrice = minPrice.trim(),
                        maxPrice = maxPrice.trim(),
                        includeNoPrice = includeNoPrice,
                        includeSold = includeSold,
                        includeExpired = includeExpired,
                        selectedTagIds = selectedTagIds.toList(),
                    ),
                )
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
