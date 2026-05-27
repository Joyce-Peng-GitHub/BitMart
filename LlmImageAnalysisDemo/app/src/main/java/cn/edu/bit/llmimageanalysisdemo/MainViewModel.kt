package cn.edu.bit.llmimageanalysisdemo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.edu.bit.llmimageanalysisdemo.model.AnalysisResult
import cn.edu.bit.llmimageanalysisdemo.network.LlmApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import androidx.core.graphics.scale

data class UiState(
    val baseUrl: String = "",
    val apiKey: String = "",
    val modelName: String = "",
    val imageUri: Uri? = null,
    val isLoading: Boolean = false,
    val result: AnalysisResult? = null,
    val error: String? = null
)

private const val MAX_IMAGE_DIMENSION = 1024
private const val JPEG_QUALITY = 80

class MainViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    fun updateBaseUrl(url: String) {
        _uiState.value = _uiState.value.copy(baseUrl = url)
    }

    fun updateApiKey(key: String) {
        _uiState.value = _uiState.value.copy(apiKey = key)
    }

    fun updateModelName(name: String) {
        _uiState.value = _uiState.value.copy(modelName = name)
    }

    fun setImageUri(uri: Uri?) {
        _uiState.value = _uiState.value.copy(imageUri = uri, result = null, error = null)
    }

    fun analyzeImage(context: Context) {
        val state = _uiState.value
        val uri = state.imageUri ?: return

        if (state.baseUrl.isBlank() || state.apiKey.isBlank() || state.modelName.isBlank()) {
            _uiState.value = state.copy(error = "请填写 API Base URL、API Key 和模型名称")
            return
        }

        _uiState.value = state.copy(isLoading = true, error = null, result = null)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val base64 = compressAndEncode(context, uri)
                val client = LlmApiClient(state.baseUrl, state.apiKey)
                val result = client.analyzeImage(base64, state.modelName)

                result.fold(
                    onSuccess = { analysisResult ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            result = analysisResult
                        )
                    },
                    onFailure = { exception ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = exception.message ?: "Unknown error"
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Unknown error"
                )
            }
        }
    }

    private fun compressAndEncode(context: Context, uri: Uri): String {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Cannot open image URI")

        val originalBitmap = BitmapFactory.decodeStream(inputStream)
        inputStream.close()

        val scaledBitmap = if (originalBitmap.width > MAX_IMAGE_DIMENSION || originalBitmap.height > MAX_IMAGE_DIMENSION) {
            val scale = MAX_IMAGE_DIMENSION.toFloat() / maxOf(originalBitmap.width, originalBitmap.height)
            val newWidth = (originalBitmap.width * scale).toInt()
            val newHeight = (originalBitmap.height * scale).toInt()
            originalBitmap.scale(newWidth, newHeight).also {
                if (it != originalBitmap) originalBitmap.recycle()
            }
        } else {
            originalBitmap
        }

        val outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
        scaledBitmap.recycle()

        val bytes = outputStream.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}
