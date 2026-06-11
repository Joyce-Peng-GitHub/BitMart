package cn.edu.bit.bitmart.core.ui

import cn.edu.bit.bitmart.BuildConfig

/**
 * 将服务端返回的相对媒体路径（如 `/static/2026/06/02/x.jpg`）拼接为绝对地址，供 Coil 加载。
 * 静态文件由后端在 `API_BASE_URL + /static/...` 暴露（架构 §8）。已是绝对地址则原样返回。
 */
fun absoluteMediaUrl(path: String?): String? {
    if (path.isNullOrBlank()) return null
    if (path.startsWith("http://") || path.startsWith("https://")) return path
    return BuildConfig.API_BASE_URL.trimEnd('/') + "/" + path.trimStart('/')
}

/**
 * 将上传接口返回的裸 blobKey（如 `2026/06/02/x.jpg`，不含 `/static/` 前缀）
 * 拼接为可显示的绝对地址。提交发布时仍用裸 blobKey，仅展示时需要此转换。
 */
fun blobKeyToMediaUrl(blobKey: String): String? = absoluteMediaUrl("/static/$blobKey")
