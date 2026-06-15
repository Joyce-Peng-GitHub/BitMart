package cn.edu.bit.bitmart.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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

/** 单个圆形动作按钮的直径。 */
val SwipeActionSize = 48.dp

/** 动作按钮之间及两端的间距。单侧露出宽度 = 直径×N + 间距×(N+1)。 */
private val SwipeActionGap = 12.dp

private fun sideWidth(count: Int) =
    if (count > 0) SwipeActionSize * count + SwipeActionGap * (count + 1) else 0.dp

/**
 * 可双向左右滑显露动作的行容器。前景 [content]（通常为 [ListingCard]）默认盖住两侧居中排列的
 * 圆形动作：右滑显露起始侧（左）[startActions]，左滑显露末尾侧（右）[endActions]；松手按过半阈值
 * 吸附到“某侧全开 / 收起”。露出状态下点击前景先收起，避免误触发卡片点击。动作为居中圆形、四周留空，
 * 故卡片圆角处不会露出按钮底色。某一方向无动作（count=0）时该方向不可滑出。
 *
 * @param startActionCount 起始侧（右滑显露，居左）动作数量。
 * @param endActionCount 末尾侧（左滑显露，居右）动作数量。
 * @param startActions 起始侧动作区；回传 `close` 供动作执行后收起本行。
 * @param endActions 末尾侧动作区；回传 `close` 供动作执行后收起本行。
 * @param content 前景内容（卡片）。
 */
@Composable
fun SwipeRevealRow(
    modifier: Modifier = Modifier,
    startActionCount: Int = 0,
    endActionCount: Int = 0,
    startActions: @Composable RowScope.(close: () -> Unit) -> Unit = {},
    endActions: @Composable RowScope.(close: () -> Unit) -> Unit = {},
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val startMaxPx = with(density) { sideWidth(startActionCount).toPx() }   // 右滑上界（正向）
    val endMaxPx = with(density) { sideWidth(endActionCount).toPx() }       // 左滑下界（负向）
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    // 露出与否的离散状态（仅在吸附结束时翻转），用于决定是否挂载“点击收起”遮罩，避免逐帧重组。
    var revealed by remember { mutableStateOf(false) }
    val close: () -> Unit = {
        revealed = false
        scope.launch { offset.animateTo(0f) }
    }

    Box(modifier.clipToBounds()) {
        // 起始侧（右滑显露）：圆形按钮贴左排列、垂直居中。
        if (startActionCount > 0) {
            Row(
                modifier = Modifier.matchParentSize().padding(horizontal = SwipeActionGap),
                horizontalArrangement = Arrangement.spacedBy(SwipeActionGap, Alignment.Start),
                verticalAlignment = Alignment.CenterVertically,
            ) { startActions(close) }
        }
        // 末尾侧（左滑显露）：圆形按钮贴右排列、垂直居中。
        if (endActionCount > 0) {
            Row(
                modifier = Modifier.matchParentSize().padding(horizontal = SwipeActionGap),
                horizontalArrangement = Arrangement.spacedBy(SwipeActionGap, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) { endActions(close) }
        }

        // 前景内容：水平位移由手势驱动，区间 [-endMaxPx, +startMaxPx]。
        Box(
            modifier = Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        scope.launch { offset.snapTo((offset.value + delta).coerceIn(-endMaxPx, startMaxPx)) }
                    },
                    onDragStopped = {
                        val target = when {
                            startMaxPx > 0f && offset.value >= startMaxPx / 2f -> startMaxPx
                            endMaxPx > 0f && offset.value <= -endMaxPx / 2f -> -endMaxPx
                            else -> 0f
                        }
                        revealed = target != 0f
                        scope.launch { offset.animateTo(target) }
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
 * 左滑动作按钮：直径 [SwipeActionSize] 的圆形图标按钮，垂直居中。
 *
 * @param label 无障碍描述（图标 contentDescription）。
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
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        modifier = modifier.size(SwipeActionSize),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label)
        }
    }
}

/** 动作进行中（如“调整数量”提交中）的占位：与按钮同尺寸的圆形转圈，禁用点击。 */
@Composable
fun RowScope.SwipeActionLoading(
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
) {
    Surface(shape = CircleShape, color = containerColor, modifier = Modifier.size(SwipeActionSize)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        }
    }
}
