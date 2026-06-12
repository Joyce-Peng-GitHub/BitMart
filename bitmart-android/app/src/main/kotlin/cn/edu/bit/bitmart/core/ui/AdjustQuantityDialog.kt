package cn.edu.bit.bitmart.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * 调整数量对话框：出售调“已售出数量”，收购调“已收购数量”，输入 0..[quantityTotal] 的整数。
 * 文案随 [buy] 切换；卖品/求购共用同一交互。
 * @param quantityTotal 总数量上限。
 * @param currentQuantitySold 当前已成交数量（作为输入初始值）。
 * @param buy true 表示求购（BUY），false 表示出售（SELL）。
 * @param onConfirm 输入合法时回传新的已成交数量。
 */
@Composable
fun AdjustQuantityDialog(
    quantityTotal: Int,
    currentQuantitySold: Int,
    buy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var text by remember { mutableStateOf(currentQuantitySold.toString()) }
    val parsed = text.toIntOrNull()
    val valid = parsed != null && parsed in 0..quantityTotal
    val noun = if (buy) "已收购数量" else "已售出数量"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("调整$noun") },
        text = {
            Column {
                Text("总数：$quantityTotal", style = MaterialTheme.typography.bodyMedium)
                androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(noun) },
                    singleLine = true,
                    isError = !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                if (!valid) {
                    Text(
                        "请输入 0 到 $quantityTotal 之间的整数",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { parsed?.let(onConfirm) }, enabled = valid) { Text("确认") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
