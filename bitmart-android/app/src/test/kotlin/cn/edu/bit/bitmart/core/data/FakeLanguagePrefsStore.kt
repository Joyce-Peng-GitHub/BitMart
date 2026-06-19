package cn.edu.bit.bitmart.core.data

import cn.edu.bit.bitmart.core.data.local.LanguagePrefsStore
import cn.edu.bit.bitmart.core.domain.model.AppLanguage
import kotlinx.coroutines.flow.MutableStateFlow

/** 内存语言偏好存储，供 JVM 单测使用。 */
class FakeLanguagePrefsStore(initial: AppLanguage = AppLanguage.SYSTEM) : LanguagePrefsStore {
    private val flow = MutableStateFlow(initial)
    override val languageFlow = flow

    override suspend fun setLanguage(lang: AppLanguage) {
        flow.value = lang
    }
}
