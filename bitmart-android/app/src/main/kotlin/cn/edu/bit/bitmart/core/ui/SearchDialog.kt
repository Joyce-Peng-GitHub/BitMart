package cn.edu.bit.bitmart.core.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import cn.edu.bit.bitmart.R

/**
 * 搜索弹窗：单行搜索框 + 清空/取消/确认（与筛选弹窗一致）。
 * 回车（ImeAction.Search）或点击确认即应用搜索；清空即清除搜索词回到全部。
 * 买卖列表与"我的"列表共用。
 */
@Composable
fun SearchDialog(
    initialQuery: String,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
    onConfirm: (String) -> Unit,
    label: String = stringResource(R.string.search_dialog_label),
) {
    var query by remember { mutableStateOf(initialQuery) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.search_dialog_title)) },
        text = {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(label) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onConfirm(query) }),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(query) }) { Text(stringResource(R.string.common_confirm)) } },
        dismissButton = {
            Row {
                TextButton(onClick = onClear) { Text(stringResource(R.string.common_clear)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
            }
        },
    )
}
