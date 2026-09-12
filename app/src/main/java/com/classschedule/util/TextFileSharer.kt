package com.classschedule.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.classschedule.data.share.PeriodSharing
import java.io.File

/**
 * 把文本写成文件并交给系统分享面板。
 *
 * 文件写在 App 私有缓存目录，通过 FileProvider 授权给接收方；
 * 无法分享时（没有可接收的应用等）返回 false，由界面提示用户。
 */
object TextFileSharer {

    private const val SHARE_DIR = "share"

    fun shareText(
        context: Context,
        fileName: String,
        content: String,
        mimeType: String = "text/plain",
        chooserTitle: String = "分享课时时间"
    ): Boolean {
        return try {
            val uri = writeToShareCache(context, fileName, content)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, fileName)
                putExtra(Intent.EXTRA_TEXT, "课时时间分享（本App可直接导入）")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                Intent.createChooser(intent, chooserTitle).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            true
        } catch (e: Exception) {
            android.util.Log.e("PeriodShare", "分享失败: ${e.message}", e)
            false
        }
    }

    /**
     * 写入缓存目录并返回可分享的 uri。
     * 文本统一带 UTF-8 BOM：Windows 记事本按 BOM 识别编码，打开不会乱码。
     */
    fun writeToShareCache(context: Context, fileName: String, content: String): android.net.Uri {
        val dir = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }
        val safeName = fileName.replace(Regex("""[\\/:*?"<>|]"""), "_")
        val file = File(dir, safeName)
        file.writeBytes(PeriodSharing.BOM + content.toByteArray(Charsets.UTF_8))
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }
}
