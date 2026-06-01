package cn.edu.bit.bitmart.feature.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.edu.bit.bitmart.core.domain.model.ListingType

/** 详情屏。展示卖家昵称、联系方式、完整描述、书籍信息；含防诈骗提示。 */
@Composable
fun ListingDetailScreen(
    listingId: Long,
    viewModel: ListingDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(listingId) { viewModel.load(listingId) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        when {
            state.loading -> CircularProgressIndicator()
            state.needLogin -> Text("请登录后查看详情", color = MaterialTheme.colorScheme.error)
            state.error != null -> Text(state.error!!, color = MaterialTheme.colorScheme.error)
            state.detail != null -> {
                val d = state.detail!!
                Text(d.title, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                val priceLabel = if (d.type == ListingType.BUY) "期望价" else "售价"
                Text("$priceLabel：${d.unitPrice?.let { "￥$it" } ?: "面议"}")
                Text("数量：${d.quantitySold}/${d.quantityTotal} 已售")
                d.pickupLocation?.let { Text("取货地点：$it") }
                Text("发布者：${d.nickname ?: "匿名"}")
                Spacer(Modifier.height(8.dp))
                Text(d.description, style = MaterialTheme.typography.bodyMedium)

                if (d.book != null) {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                    Text("书籍信息", style = MaterialTheme.typography.titleSmall)
                    d.book.title?.let { Text("书名：$it") }
                    d.book.authors?.let { Text("作者：$it") }
                    d.book.publisher?.let { Text("出版社：$it") }
                    d.book.isbn?.let { Text("ISBN：$it") }
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Text("联系方式", style = MaterialTheme.typography.titleSmall)
                d.contacts.forEach { Text("${it.channel}：${it.value}") }
                Spacer(Modifier.height(8.dp))
                Text(
                    "提示：请自行核实对方身份，谨防诈骗；优先使用微信/QQ 联系，使用手机号存在隐私风险。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
