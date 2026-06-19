package cn.edu.bit.bitmart.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.edu.bit.bitmart.R
import cn.edu.bit.bitmart.llm.LlmProtocol

/**
 * LLM 设置页（架构 §5.4 / UI 规范「LLM 设置」）：协议下拉、Base URL、API Key、
 * 模型名、超时阈值、书籍/一般商品识别提示词；可保存或清空。
 *
 * API Key 提示不上传服务器：整份配置仅存本地，BitMart 后端不感知。
 * @param onBack 返回上一页。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LlmSettingsScreen(
    onBack: () -> Unit,
    viewModel: LlmSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var apiKeyVisible by remember { mutableStateOf(false) }

    // 一次性提示展示后清空（这里直接消费；如需 Snackbar 可在此接入）。
    LaunchedEffect(state.message) {
        if (state.message != null) viewModel.consumeMessage()
    }

    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text(stringResource(R.string.settings_llm)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.message?.let { Text(it.asString(), color = MaterialTheme.colorScheme.primary) }
            state.error?.let { Text(it.asString(), color = MaterialTheme.colorScheme.error) }

            ProtocolDropdown(
                selected = state.protocol,
                options = viewModel.protocols,
                onSelected = viewModel::onProtocol,
            )

            OutlinedTextField(
                value = state.baseUrl,
                onValueChange = viewModel::onBaseUrl,
                label = { Text(stringResource(R.string.llm_base_url)) },
                placeholder = { Text("https://api.example.com") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.apiKey,
                onValueChange = viewModel::onApiKey,
                label = { Text(stringResource(R.string.llm_api_key)) },
                singleLine = true,
                visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                        Icon(
                            imageVector = if (apiKeyVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = stringResource(if (apiKeyVisible) R.string.llm_hide else R.string.llm_show),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(R.string.llm_api_key_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )

            OutlinedTextField(
                value = state.model,
                onValueChange = viewModel::onModel,
                label = { Text(stringResource(R.string.llm_model)) },
                placeholder = { Text("gpt-4o-mini") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.timeoutSeconds,
                onValueChange = viewModel::onTimeout,
                label = { Text(stringResource(R.string.llm_timeout)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.bookPrompt,
                onValueChange = viewModel::onBookPrompt,
                label = { Text(stringResource(R.string.llm_book_prompt)) },
                placeholder = { Text(stringResource(R.string.llm_prompt_hint)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.generalPrompt,
                onValueChange = viewModel::onGeneralPrompt,
                label = { Text(stringResource(R.string.llm_general_prompt)) },
                placeholder = { Text(stringResource(R.string.llm_prompt_hint)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = viewModel::save, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.common_save)) }
                OutlinedButton(onClick = viewModel::clear, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.common_clear)) }
            }
        }
    }
}

/** 协议下拉选择。当前仅 OpenAI Compatible，以列表呈现以便未来扩展。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProtocolDropdown(
    selected: LlmProtocol,
    options: List<LlmProtocol>,
    onSelected: (LlmProtocol) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.llm_protocol)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.displayName) },
                    onClick = { onSelected(option); expanded = false },
                )
            }
        }
    }
}
