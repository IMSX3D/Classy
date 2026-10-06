package com.imsx3d.classy.ui.screen.imports

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.imsx3d.classy.MainActivity
import com.imsx3d.classy.SleepyApp
import com.imsx3d.classy.data.parser.ScheduleParser
import com.imsx3d.classy.ui.screen.schedule.ScheduleViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 外部 app (文件管理器、邮件附件、其他课表 app) 通过 ACTION_VIEW 打开 json 课表时, 先到这里:
 * 1. 读 URI 内容
 * 2. 解析为 ParseResult
 * 3. 跳到 MainActivity 并把解析结果通过 Intent extra 传过去, 由 ImportSheet 接收并弹预览对话框
 *
 * 这样:
 * - intent-filter 干净, MainActivity 不需要处理 VIEW, 避免 singleTask launchMode 边界问题
 * - 用户体验: 外部打开 json -> Sleepy 启动 -> 自动弹导入预览 -> 一键确认
 */
class ImportReceiverActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uri: Uri? = intent?.data
        if (uri == null) {
            finishWithError("no_uri")
            return
        }

        lifecycleScope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use {
                        com.imsx3d.classy.util.BoundedImportReader.read(it)
                    }
                }
                if (text.isNullOrBlank()) {
                    finishWithError("empty")
                    return@launch
                }

                val token = withContext(Dispatchers.IO) {
                    com.imsx3d.classy.util.PendingImportStore.save(this@ImportReceiverActivity, text)
                }
                val forward = Intent(this@ImportReceiverActivity, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(EXTRA_FROM_IMPORT_RECEIVER, true)
                    putExtra(EXTRA_IMPORT_TOKEN, token)
                }
                startActivity(forward)
                finish()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("Classy", "external import failed", e)
                finishWithError(e.message ?: "unknown")
            }
        }
    }

    private fun finishWithError(msg: String) {
        android.app.AlertDialog.Builder(this)
            .setTitle("导入失败")
            .setMessage(when (msg) {
                "no_uri" -> "没有收到文件地址，请重新选择课表文件。"
                "empty" -> "文件内容为空，请选择有效的课表文件。"
                else -> msg
            })
            .setPositiveButton("确定") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    companion object {
        const val EXTRA_FROM_IMPORT_RECEIVER = "extra_from_import_receiver"
        const val EXTRA_IMPORT_TOKEN = "extra_import_token"
        const val EXTRA_IMPORT_TEXT = "extra_import_text"
    }
}