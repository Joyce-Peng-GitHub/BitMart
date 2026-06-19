package cn.edu.bit.bitmart.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.edu.bit.bitmart.R
import cn.edu.bit.bitmart.core.domain.model.AppLanguage
import cn.edu.bit.bitmart.core.domain.model.ThemeMode

/**
 * 设置页：账号设置、LLM 设置、语言设置、主题设置。
 * @param onBack 返回上一页。
 * @param onAccountClick 进入账号设置（若未登录由账号设置页跳转登录）。
 * @param onLlmClick 进入 LLM 设置。
 * @param onComingSoon 点击尚未实现的项时的占位回调（展示提示）。
 * @param themeViewModel 主题设置 ViewModel：读取当前模式并即时切换。
 * @param languageViewModel 语言设置 ViewModel：读取当前语言并即时切换。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onAccountClick: () -> Unit,
    onLlmClick: () -> Unit,
    onComingSoon: (String) -> Unit,
    themeViewModel: ThemeViewModel = hiltViewModel(),
    languageViewModel: LanguageViewModel = hiltViewModel(),
) {
    val themeMode by themeViewModel.themeMode.collectAsStateWithLifecycle()
    val language by languageViewModel.language.collectAsStateWithLifecycle()
    var themeDialogVisible by remember { mutableStateOf(false) }
    var showLangDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            SettingRow(stringResource(R.string.settings_account), onClick = onAccountClick)
            SettingRow(stringResource(R.string.settings_llm), onClick = onLlmClick)
            SettingRow(
                stringResource(R.string.settings_language),
                subtitle = language.label(),
                onClick = { showLangDialog = true },
            )
            SettingRow(
                stringResource(R.string.settings_theme),
                subtitle = themeMode.label(),
                onClick = { themeDialogVisible = true },
            )
        }
    }

    if (themeDialogVisible) {
        ThemeModeDialog(
            current = themeMode,
            onSelect = { themeViewModel.setMode(it); themeDialogVisible = false },
            onDismiss = { themeDialogVisible = false },
        )
    }

    if (showLangDialog) {
        LanguageDialog(
            current = language,
            onSelect = { languageViewModel.setLanguage(it); showLangDialog = false },
            onDismiss = { showLangDialog = false },
        )
    }
}

/** 主题模式的标签。映射置于 UI 层，领域枚举保持无显示文案。 */
@Composable
private fun ThemeMode.label(): String = when (this) {
    ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
    ThemeMode.LIGHT -> stringResource(R.string.theme_light)
    ThemeMode.DARK -> stringResource(R.string.theme_dark)
}

/** 语言偏好的标签。映射置于 UI 层，领域枚举保持无显示文案。 */
@Composable
private fun AppLanguage.label(): String = when (this) {
    AppLanguage.SYSTEM -> stringResource(R.string.language_system)
    AppLanguage.ZH -> stringResource(R.string.language_zh)
    AppLanguage.EN -> stringResource(R.string.language_en)
}

/** 主题选择弹窗：三个单选项，选中即应用并关闭。 */
@Composable
private fun ThemeModeDialog(
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_theme)) },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                ThemeMode.entries.forEach { mode ->
                    val selected = mode == current
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selected,
                                role = Role.RadioButton,
                                onClick = { onSelect(mode) },
                            )
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected, onClick = null)
                        Text(mode.label(), modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

/** 语言选择弹窗：跟随系统 / 中文 / English，选中即应用并关闭。 */
@Composable
private fun LanguageDialog(
    current: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        title = { Text(stringResource(R.string.settings_language)) },
        text = {
            Column(Modifier.selectableGroup()) {
                AppLanguage.entries.forEach { lang ->
                    Row(
                        Modifier.fillMaxWidth()
                            .selectable(
                                selected = lang == current,
                                role = Role.RadioButton,
                                onClick = { onSelect(lang); onDismiss() },
                            )
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = lang == current, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text(lang.label())
                    }
                }
            }
        },
    )
}

@Composable
private fun SettingRow(title: String, subtitle: String? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(end = 8.dp))
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
    HorizontalDivider()
}
