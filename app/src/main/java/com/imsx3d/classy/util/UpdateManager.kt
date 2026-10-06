package com.imsx3d.classy.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import com.imsx3d.classy.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/** 拉 GitHub/镜像 release 信息、下载 APK、清理旧 APK。不含 UI 状态。 */
object UpdateManager {
    private const val TAG = "UpdateManager"
    // 仓库 owner/name 来自 AppIdentity —— 改一处即可（关于页的"开源地址"同步生效）。
    // 不再写死上游 lingion/sleepy：本包签名与上游不同，查到上游新版会下载装不上的官方包。
    private const val GITHUB_API = "https://api.github.com/repos/${AppIdentity.REPO_SLUG}/releases/latest"
    private const val MIRROR_RELEASE = "https://gh.qdp.qzz.io/${AppIdentity.REPO_SLUG}/releases/latest"
    private const val MIRROR_PREFIX = "https://gh.qdp.qzz.io/${AppIdentity.REPO_SLUG}/releases/download/"

    private fun currentAbiAsset(): String = when {
        Build.SUPPORTED_ABIS.any { it == "arm64-v8a" } -> "app-arm64-v8a-release.apk"
        Build.SUPPORTED_ABIS.any { it == "armeabi-v7a" } -> "app-armeabi-v7a-release.apk"
        Build.SUPPORTED_ABIS.any { it == "x86_64" } -> "app-x86_64-release.apk"
        else -> "app-arm64-v8a-release.apk"
    }

    private fun currentAbi(): String = currentAbiAsset()
        .removePrefix("app-").removeSuffix("-release.apk")

    /** 只拉 release 信息,不下载。GitHub 不通回退镜像。 */
    suspend fun fetchUpdateInfo(context: Context): UpdateInfo = withContext(Dispatchers.IO) {
        // 仓库未配置 → 直接拒绝：宁可报"检查失败"，也不能去查上游 Releases
        // （查到会提示新版并下载官方包，签名不一致装不上，用户还可能被引导去卸载 → 课表数据全丢）
        check(AppIdentity.hasRepo) { "开源仓库未配置，更新检查已停用" }
        val abi = currentAbi()
        val abiAsset = currentAbiAsset()
        val json = try {
            readText(GITHUB_API)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // Preserve the existing mirror release page fallback, but never fabricate an APK URL.
            val page = readText(MIRROR_RELEASE)
            val tag = Regex("/${AppIdentity.REPO_SLUG}/releases/tag/(v[0-9A-Za-z.+_-]+)")
                .find(page)?.groupValues?.get(1)
                ?: throw IllegalStateException(context.getString(com.imsx3d.classy.R.string.error_no_version_found))
            val assetPath = "/${AppIdentity.REPO_SLUG}/releases/download/$tag/$abiAsset"
            val link = Regex("href=\"([^\"]+)\"").findAll(page).map { it.groupValues[1] }
                .firstOrNull { it.endsWith(assetPath) }
            val url = when {
                link == null -> ""
                link.startsWith("/") -> "https://github.com$link"
                isValidDownloadUrl(link) -> link
                else -> ""
            }
            return@withContext UpdateInfo(tag.removePrefix("v"), parseMirrorPage(page, tag), url,
                VersionUtils.compare(tag.removePrefix("v"), BuildConfig.VERSION_NAME) > 0)
        }
        parseReleaseJson(json, BuildConfig.VERSION_NAME, abi)
    }

    /** 下载 APK 到 cacheDir,带进度回调(0-100)。协程 cancel 时删半截文件。 */
    suspend fun downloadApk(
        context: Context, info: UpdateInfo, onProgress: (Int) -> Unit
    ): File = withContext(Dispatchers.IO) {
        require(info.canDownload) { "当前版本尚无适用于此设备的安装包，请稍后重试或查看发布页" }
        val target = File(context.cacheDir, "sleepy-update-${currentAbiAsset()}")
        try {
            downloadOnce(info.downloadUrl, target, onProgress)
        } catch (primary: Exception) {
            if (primary is kotlinx.coroutines.CancellationException) throw primary
            // 镜像下载失败 → GitHub 直连回退 (信息源/下载源各回退一次, 用户 2026-09-05 令)
            val direct = toDirectGithubUrl(info.downloadUrl)
            if (direct == info.downloadUrl) throw primary
            Log.w(TAG, "mirror download failed, falling back to github direct", primary)
            target.delete()
            downloadOnce(direct, target, onProgress)
        }
        if (!target.isFile || target.length() == 0L)
            throw IllegalStateException(context.getString(com.imsx3d.classy.R.string.error_empty_download))
        target
    }

    private suspend fun downloadOnce(
        url: String, target: File, onProgress: (Int) -> Unit
    ) {
        val conn = request(url)
        val total = conn.contentLengthLong.coerceAtLeast(1L)
        try {
            conn.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buf = ByteArray(8 * 1024)
                    var downloaded = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        downloaded += n
                        onProgress((downloaded * 100 / total).toInt().coerceIn(0, 100))
                    }
                }
            }
        } catch (e: Exception) {
            target.delete()
            throw e
        } finally {
            conn.disconnect()
        }
    }

    /** 启动时清理 cacheDir 中旧安装包。 */
    fun cleanOldApk(context: Context) {
        context.cacheDir.listFiles { it.name.startsWith("sleepy-update-") }
            ?.forEach { it.delete() }
    }

    fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    private fun readText(url: String): String {
        val conn = request(url)
        return try { conn.inputStream.bufferedReader().use { it.readText() } }
        finally { conn.disconnect() }
    }

    private fun request(url: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 60_000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "Sleepy/${BuildConfig.VERSION_NAME}")
        conn.setRequestProperty("Accept", "application/json,text/html,*/*")
        if (conn.responseCode !in 200..299) {
            conn.disconnect()
            throw IllegalStateException("HTTP ${conn.responseCode}")
        }
        return conn
    }
}
