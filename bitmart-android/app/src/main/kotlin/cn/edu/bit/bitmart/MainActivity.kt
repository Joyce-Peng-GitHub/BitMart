package cn.edu.bit.bitmart

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.edu.bit.bitmart.core.designsystem.BitMartTheme
import cn.edu.bit.bitmart.core.ui.ProvideAppLocale
import cn.edu.bit.bitmart.feature.settings.LanguageViewModel
import cn.edu.bit.bitmart.feature.settings.ThemeViewModel
import dagger.hilt.android.AndroidEntryPoint

/** 顶层 Activity：装载语言、主题与导航图。语言与主题取自持久化偏好，应用全局即时生效。 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeVm: ThemeViewModel = hiltViewModel()
            val langVm: LanguageViewModel = hiltViewModel()
            val mode by themeVm.themeMode.collectAsStateWithLifecycle()
            val lang by langVm.language.collectAsStateWithLifecycle()
            ProvideAppLocale(language = lang) {
                BitMartTheme(themeMode = mode) {
                    BitMartNavHost()
                }
            }
        }
    }
}
