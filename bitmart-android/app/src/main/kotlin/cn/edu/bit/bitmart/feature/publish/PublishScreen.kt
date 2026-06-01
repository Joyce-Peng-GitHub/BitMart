package cn.edu.bit.bitmart.feature.publish

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.edu.bit.bitmart.core.domain.model.ContactChannel
import cn.edu.bit.bitmart.core.domain.model.ListingType

/** 发布屏。买/卖共用，价格标签随类型切换；提交成功后回调。 */
@Composable
fun PublishScreen(
    onPublished: (Long) -> Unit,
    viewModel: PublishViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.publishedId) { state.publishedId?.let(onPublished) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(state.type == ListingType.SELL, { viewModel.setType(ListingType.SELL) }, { Text("我要卖") })
            FilterChip(state.type == ListingType.BUY, { viewModel.setType(ListingType.BUY) }, { Text("我要买") })
        }
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(state.title, viewModel::onTitle, label = { Text("标题") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(state.description, viewModel::onDescription, label = { Text("描述") }, modifier = Modifier.fillMaxWidth())
        val priceLabel = if (state.type == ListingType.BUY) "期望价（可留空面议）" else "售价（可留空面议）"
        OutlinedTextField(state.unitPrice, viewModel::onUnitPrice, label = { Text(priceLabel) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(state.quantityTotal, viewModel::onQuantity, label = { Text("件数") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(state.pickupLocation, viewModel::onPickup, label = { Text("取货地点") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(state.tags, viewModel::onTags, label = { Text("标签（逗号分隔）") }, singleLine = true, modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(8.dp))
        Text("联系方式", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ContactChannel.entries.take(3).forEach { ch ->
                FilterChip(state.contactChannel == ch, { viewModel.onContactChannel(ch) }, { Text(ch.name) })
            }
        }
        OutlinedTextField(state.contactValue, viewModel::onContactValue, label = { Text("联系方式内容") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text(
            "提示：优先使用微信/QQ；填写手机号存在隐私风险。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )

        state.error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.error) }

        Spacer(Modifier.height(16.dp))
        Button(onClick = viewModel::submit, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) {
            if (state.loading) CircularProgressIndicator(modifier = Modifier.height(20.dp)) else Text("发布")
        }
    }
}
