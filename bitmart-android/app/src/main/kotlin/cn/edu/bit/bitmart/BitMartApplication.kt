package cn.edu.bit.bitmart

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/** 应用入口。Hilt 依赖注入容器根节点。 */
@HiltAndroidApp
class BitMartApplication : Application()
