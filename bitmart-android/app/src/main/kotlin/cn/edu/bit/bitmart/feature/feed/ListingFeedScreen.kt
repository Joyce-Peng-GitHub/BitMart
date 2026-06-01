package cn.edu.bit.bitmart.feature.feed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.edu.bit.bitmart.core.domain.model.ListingSummary
import cn.edu.bit.bitmart.core.domain.model.ListingType

/** 商品/求购列表屏。买/卖共用，文案随类型切换。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListingFeedScreen(
    onItemClick: (Long) -> Unit,
    viewModel: ListingFeedViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { if (state.items.isEmpty()) viewModel.refresh() }

    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                FilterChip(
                    selected = state.type == ListingType.SELL,
                    onClick = { viewModel.setType(ListingType.SELL) },
                    label = { Text("在售") },
                )
                FilterChip(
                    selected = state.type == ListingType.BUY,
                    onClick = { viewModel.setType(ListingType.BUY) },
                    label = { Text("求购") },
                )
            }

            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                label = { Text("搜索商品名 / 描述") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp)) }

            if (state.loading) {
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    items(state.items, key = { it.id }) { item ->
                        ListingCard(item, type = state.type, onClick = { onItemClick(item.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ListingCard(item: ListingSummary, type: ListingType, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium)
            val priceLabel = if (type == ListingType.BUY) "期望价" else "售价"
            val price = item.unitPrice?.let { "￥$it" } ?: "面议"
            Text("$priceLabel：$price", style = MaterialTheme.typography.bodyMedium)
            if (item.tags.isNotEmpty()) Text("标签：${item.tags.joinToString(" / ")}", style = MaterialTheme.typography.bodySmall)
            item.pickupLocation?.let { Text("取货：$it", style = MaterialTheme.typography.bodySmall) }
        }
    }
}
