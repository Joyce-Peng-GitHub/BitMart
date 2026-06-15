package cn.edu.bit.bitmart.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import cn.edu.bit.bitmart.core.domain.model.ListingSummary
import cn.edu.bit.bitmart.core.domain.model.ListingType
import coil3.compose.AsyncImage

/**
 * 统一列表项卡片：首图缩略图 + 标题 / 价格 / 标签 / 数量 / 时间。
 * 买卖列表与"我的商品/收购"列表共用，保证两处视觉完全一致。
 * 买卖文案随 [type] 切换（卖品=售价/已售，求购=期望价/已收）。
 *
 * @param item 列表摘要。
 * @param type 当前列表类型，仅用于价格/数量标签文案。
 * @param onClick 点击卡片（通常进入详情）。
 * @param modifier 外部布局修饰；左滑容器通过它传入位移等。
 */
@Composable
fun ListingCard(
    item: ListingSummary,
    type: ListingType,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val soldOut = item.quantitySold >= item.quantityTotal
    Card(modifier = modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val imageUrl = absoluteMediaUrl(item.firstImageUrl)
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = item.title,
                    modifier = Modifier.size(64.dp).clip(MaterialTheme.shapes.small),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium)
                val priceLabel = if (type == ListingType.BUY) "期望价" else "售价"
                val price = item.unitPrice?.let { "￥$it" } ?: "面议"
                Text("$priceLabel：$price", style = MaterialTheme.typography.bodyMedium)
                if (item.tags.isNotEmpty()) {
                    Text("标签：${item.tags.joinToString(" / ")}", style = MaterialTheme.typography.bodySmall)
                }
                // 数量：已售(收) X/Y，售罄时着色提示。
                val soldVerb = if (type == ListingType.BUY) "已收" else "已售"
                val soldLabel = if (type == ListingType.BUY) "已收满" else "售罄"
                Text(
                    "$soldVerb ${item.quantitySold}/${item.quantityTotal}" + if (soldOut) "（$soldLabel）" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (soldOut) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // 发布/过期时间：过期或临期按相应着色。
                ListingTimeInfo(
                    createdAtIso = item.createdAt,
                    expiresAtIso = item.expiresAt,
                )
            }
        }
    }
}
