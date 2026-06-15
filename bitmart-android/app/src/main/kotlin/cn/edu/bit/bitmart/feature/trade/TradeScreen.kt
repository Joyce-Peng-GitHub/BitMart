package cn.edu.bit.bitmart.feature.trade

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.edu.bit.bitmart.core.domain.model.ListingType
import cn.edu.bit.bitmart.feature.feed.ListingFeedScreen
import cn.edu.bit.bitmart.feature.feed.ListingFeedViewModel

/**
 * “买卖”页：顶部 “商品”/“收购” 两个 tab，界面基本相同，复用 [ListingFeedScreen]。
 * 两 tab 分别对应 [ListingType.SELL]（商品）与 [ListingType.BUY]（收购），
 * 切换 tab 时驱动 [ListingFeedViewModel.setType]。共享同一 ViewModel 使 tab 与列表类型保持一致。
 *
 * @param onEditClick 在列表中左滑“编辑”本人项时进入编辑页。
 * @param listingChanged 本人项编辑返回后置 true，向下透传触发列表刷新。
 * @param onListingChangeConsumed 刷新触发后回调，由上层清除标记。
 */
@Composable
fun TradeScreen(
    onItemClick: (Long) -> Unit,
    onPublishClick: (ListingType) -> Unit,
    onEditClick: (Long) -> Unit = {},
    listingChanged: Boolean = false,
    onListingChangeConsumed: () -> Unit = {},
    viewModel: ListingFeedViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selectedIndex = if (state.type == ListingType.BUY) 1 else 0

    Column(modifier = Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = selectedIndex) {
            Tab(
                selected = selectedIndex == 0,
                onClick = { viewModel.setType(ListingType.SELL) },
                text = { Text("商品") },
            )
            Tab(
                selected = selectedIndex == 1,
                onClick = { viewModel.setType(ListingType.BUY) },
                text = { Text("收购") },
            )
        }
        ListingFeedScreen(
            onItemClick = onItemClick,
            // 发布类型由当前 tab 决定：商品→SELL，收购→BUY。
            onPublishClick = { onPublishClick(state.type) },
            onEditClick = onEditClick,
            refreshSignal = listingChanged,
            onRefreshConsumed = onListingChangeConsumed,
            viewModel = viewModel,
        )
    }
}
