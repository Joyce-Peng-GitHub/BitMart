package cn.edu.bit.bitmart.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.bit.bitmart.R
import cn.edu.bit.bitmart.core.data.local.LlmConfigStore
import cn.edu.bit.bitmart.core.ui.UiText
import cn.edu.bit.bitmart.llm.LlmConfig
import cn.edu.bit.bitmart.llm.LlmProtocol
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * LLM 设置页状态。所有字段为可编辑的草稿值，保存时组装为 [LlmConfig] 落盘。
 * @param message 一次性提示（保存/清空成功后展示，UI 消费后清空）。
 */
data class LlmSettingsUiState(
    val protocol: LlmProtocol = LlmProtocol.OPENAI_COMPATIBLE,
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val timeoutSeconds: String = LlmConfig.DEFAULT_TIMEOUT_SECONDS.toString(),
    /** 提示词草稿：留空表示识别时按当前应用语言使用内置默认提示词。 */
    val bookPrompt: String = "",
    val generalPrompt: String = "",
    val loaded: Boolean = false,
    val error: UiText? = null,
    val message: UiText? = null,
)

/**
 * LLM 设置 ViewModel：加载已保存配置到可编辑状态，保存或清空（架构 §5.4）。
 * 配置（含 API Key）仅存本地，不上传服务器。
 */
@HiltViewModel
class LlmSettingsViewModel @Inject constructor(
    private val store: LlmConfigStore,
) : ViewModel() {

    private val _state = MutableStateFlow(LlmSettingsUiState())
    val state: StateFlow<LlmSettingsUiState> = _state.asStateFlow()

    /** 可选协议列表（当前仅 OpenAI Compatible，建模为列表以便未来扩展）。 */
    val protocols: List<LlmProtocol> = LlmProtocol.entries

    init {
        viewModelScope.launch {
            val c = store.configFlow.first()
            _state.update {
                it.copy(
                    protocol = c.protocol,
                    baseUrl = c.baseUrl,
                    apiKey = c.apiKey,
                    model = c.model,
                    timeoutSeconds = c.timeoutSeconds.toString(),
                    bookPrompt = c.bookPrompt,
                    generalPrompt = c.generalPrompt,
                    loaded = true,
                )
            }
        }
    }

    fun onProtocol(v: LlmProtocol) = _state.update { it.copy(protocol = v) }
    fun onBaseUrl(v: String) = _state.update { it.copy(baseUrl = v, error = null) }
    fun onApiKey(v: String) = _state.update { it.copy(apiKey = v, error = null) }
    fun onModel(v: String) = _state.update { it.copy(model = v, error = null) }
    fun onTimeout(v: String) = _state.update { it.copy(timeoutSeconds = v.filter { ch -> ch.isDigit() }, error = null) }
    fun onBookPrompt(v: String) = _state.update { it.copy(bookPrompt = v) }
    fun onGeneralPrompt(v: String) = _state.update { it.copy(generalPrompt = v) }

    /** 保存当前草稿为配置。基础校验：Base URL / 模型名不能为空，超时为正整数。 */
    fun save() {
        val s = _state.value
        if (s.baseUrl.isBlank()) { _state.update { it.copy(error = UiText.Res(R.string.llm_error_base_url_required)) }; return }
        if (s.model.isBlank()) { _state.update { it.copy(error = UiText.Res(R.string.llm_error_model_required)) }; return }
        val timeout = s.timeoutSeconds.toIntOrNull()
        if (timeout == null || timeout < 1) { _state.update { it.copy(error = UiText.Res(R.string.llm_error_timeout_invalid)) }; return }

        val config = LlmConfig(
            protocol = s.protocol,
            baseUrl = s.baseUrl.trim(),
            apiKey = s.apiKey.trim(),
            model = s.model.trim(),
            timeoutSeconds = timeout,
            // 提示词原样保存：留空即留空，识别时由 LlmClient 按语言回退到内置默认提示词。
            bookPrompt = s.bookPrompt,
            generalPrompt = s.generalPrompt,
        )
        viewModelScope.launch {
            store.save(config)
            _state.update { it.copy(error = null, message = UiText.Res(R.string.llm_msg_saved)) }
        }
    }

    /** 清空已保存配置并将表单恢复为默认值。 */
    fun clear() {
        viewModelScope.launch {
            store.clear()
            val d = LlmConfig()
            _state.update {
                it.copy(
                    protocol = d.protocol,
                    baseUrl = d.baseUrl,
                    apiKey = d.apiKey,
                    model = d.model,
                    timeoutSeconds = d.timeoutSeconds.toString(),
                    bookPrompt = d.bookPrompt,
                    generalPrompt = d.generalPrompt,
                    error = null,
                    message = UiText.Res(R.string.llm_msg_cleared),
                )
            }
        }
    }

    /** UI 消费一次性提示后调用，避免重复展示。 */
    fun consumeMessage() = _state.update { it.copy(message = null) }
}
