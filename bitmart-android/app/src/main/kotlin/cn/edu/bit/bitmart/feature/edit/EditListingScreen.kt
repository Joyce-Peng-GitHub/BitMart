package cn.edu.bit.bitmart.feature.edit

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.edu.bit.bitmart.core.domain.model.ListingType
import cn.edu.bit.bitmart.core.domain.model.PublishConfig

/**
 * 编辑 listing 页：修改标题、描述、价格、取货地点、有效期。
 * 图片、标签、联系方式、类别、总数量不可在此修改（后端 PATCH 不支持）；
 * 已成交数量请用列表/详情页的“调整数量”。
 * @param listingId 待编辑的条目 id。
 * @param onSaved 保存成功回调（pop back，并触发上游列表刷新）。
 * @param onBack 返回上一页。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditListingScreen(
    listingId: Long,
    onSaved: () -> Unit,
    onBack: () -> Unit,
    viewModel: EditListingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(listingId) { viewModel.load(listingId) }
    LaunchedEffect(state.saved) { if (state.saved) onSaved() }

    // 校验/保存失败等瞬时错误用 Toast 展示（不再固定在输入区上方，避免被忽略）。
    LaunchedEffect(state.formError) {
        state.formError?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeFormError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("编辑") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                state.loadError != null ->
                    Text(state.loadError!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.Center).padding(24.dp))
                else -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(state.title, viewModel::onTitle, label = { Text("标题") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(state.description, viewModel::onDescription, label = { Text("描述") }, minLines = 2, modifier = Modifier.fillMaxWidth())

                    val priceLabel = if (state.type == ListingType.BUY) "期望价（可留空面议）" else "售价（可留空面议）"
                    OutlinedTextField(state.unitPrice, viewModel::onUnitPrice, label = { Text(priceLabel) }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(state.pickupLocation, viewModel::onPickup, label = { Text("取货地点") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        state.expiresInDays,
                        viewModel::onExpiresInDays,
                        label = { Text("有效期（天，留空不修改）") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "提示：图片、标签、联系方式、类别、总数量不可在此修改。已成交数量请用“调整数量”。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Button(onClick = viewModel::save, enabled = !state.saving, modifier = Modifier.fillMaxWidth()) {
                        if (state.saving) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Text("保存")
                    }
                }
            }
        }
    }
}
