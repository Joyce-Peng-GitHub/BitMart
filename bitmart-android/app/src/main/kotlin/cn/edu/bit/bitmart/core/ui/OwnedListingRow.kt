package cn.edu.bit.bitmart.core.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cn.edu.bit.bitmart.core.domain.model.ListingSummary
import cn.edu.bit.bitmart.core.domain.model.ListingType

/**
 * “本人发布”列表项：统一 [ListingCard] 外层包一层 [SwipeRevealRow]，左滑显露
 * 调整数量 / 编辑 / 删除。买卖列表（仅本人项）与“我的商品/收购”列表共用，确保交互一致。
 *
 * @param item 列表摘要。
 * @param type 列表类型，仅用于卡片价格文案。
 * @param adjusting true 时“数量”位显示转圈（调整提交中），并禁用该动作。
 * @param onClick 点击卡片（进入详情）。
 * @param onAdjustClick 触发“调整数量”。
 * @param onEditClick 触发“编辑”。
 * @param onDeleteClick 触发“删除”。
 */
@Composable
fun OwnedListingRow(
    item: ListingSummary,
    type: ListingType,
    adjusting: Boolean,
    onClick: () -> Unit,
    onAdjustClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SwipeRevealRow(
        actionCount = 3,
        modifier = modifier,
        actions = { close ->
            if (adjusting) {
                SwipeActionLoading()
            } else {
                SwipeAction(Icons.Default.Numbers, "数量", onClick = { close(); onAdjustClick() })
            }
            SwipeAction(Icons.Default.Edit, "编辑", onClick = { close(); onEditClick() })
            SwipeAction(
                Icons.Default.Delete,
                "删除",
                onClick = { close(); onDeleteClick() },
                containerColor = MaterialTheme.colorScheme.errorContainer,
            )
        },
    ) {
        ListingCard(item = item, type = type, onClick = onClick)
    }
}
