package cn.edu.bit.bitmart.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 单个左滑动作的固定宽度。露出宽度 = [SwipeActionWidth] × 动作数。 */
val SwipeActionWidth = 72.dp

/**
 * 左滑显露动作的行容器。前景 [content]（通常为 [ListingCard]）默认盖住贴右排列的
 * [actions]；向左拖动露出动作，松手按过半阈值吸附到“全开/收起”。露出状态下点击前景先收起，
 * 避免误触发卡片点击。买卖列表与“我的”列表共用，对“本人发布”的项启用。
 *
 * @param actionCount 动作数量，决定露出宽度。
 * @param actions 贴右排列的动作区；回传 `close` 供动作执行后收起本行。
 * @param content 前景内容（卡片）。
 */
@Composable
fun SwipeRevealRow(
    actionCount: Int,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.(close: () -> Unit) -> Unit,
    content: @Composable () -> Unit,
) {
    val maxOffsetPx = with(LocalDensity.current) { (SwipeActionWidth * actionCount).toPx() }
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    // 露出与否的离散状态（仅在吸附结束时翻转），用于决定是否挂载“点击收起”遮罩，避免逐帧重组。
    var revealed by remember { mutableStateOf(false) }
    val close: () -> Unit = {
        revealed = false
        scope.launch { offset.animateTo(0f) }
    }

    Box(modifier.clipToBounds()) {
        // 背景动作区：高度匹配前景内容，子项贴右。
        Row(
            modifier = Modifier.matchParentSize(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) { actions(close) }

        // 前景内容：水平位移由手势驱动。
        Box(
            modifier = Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        scope.launch { offset.snapTo((offset.value + delta).coerceIn(-maxOffsetPx, 0f)) }
                    },
                    onDragStopped = {
                        val open = offset.value <= -maxOffsetPx / 2f
                        revealed = open
                        scope.launch { offset.animateTo(if (open) -maxOffsetPx else 0f) }
                    },
                ),
        ) {
            content()
            // 露出态下覆盖透明遮罩：点击收起本行（而非进入详情）。收起态不挂载，卡片点击正常生效。
            if (revealed) {
                Box(
                    Modifier
                        .matchParentSize()
                        .pointerInput(Unit) { detectTapGestures(onTap = { close() }) },
                )
            }
        }
    }
}

/**
 * 左滑动作按钮：固定宽度 [SwipeActionWidth]、与卡片等高，图标在上、文字在下。
 *
 * @param containerColor 背景色；删除等危险动作可传 errorContainer。
 */
@Composable
fun RowScope.SwipeAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = contentColorFor(containerColor),
) {
    Surface(
        onClick = onClick,
        color = containerColor,
        contentColor = contentColor,
        modifier = modifier.width(SwipeActionWidth).fillMaxHeight(),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = label)
            Spacer(Modifier.size(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** 动作进行中（如“调整数量”提交中）的占位：与按钮同尺寸的转圈，禁用点击。 */
@Composable
fun RowScope.SwipeActionLoading(
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
) {
    Surface(color = containerColor, modifier = Modifier.width(SwipeActionWidth).fillMaxHeight()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        }
    }
}
