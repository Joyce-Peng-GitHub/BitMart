package cn.edu.bit.bitmart

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import cn.edu.bit.bitmart.core.designsystem.BitMartTheme
import dagger.hilt.android.AndroidEntryPoint

/** 顶层 Activity：装载主题与导航图。 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BitMartTheme {
                BitMartNavHost()
            }
        }
    }
}
