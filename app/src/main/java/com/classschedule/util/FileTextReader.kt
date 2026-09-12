package com.classschedule.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * 读取用户通过系统文件选择器选中的文本文件。
 *
 * 优先按 UTF-8 严格解码；失败（例如 Windows 记事本保存的 GBK / ANSI）则回退 GB18030，
 * 最后兜底按 UTF-8 宽松解码，尽量不让编码问题挡住导入。
 */
object FileTextReader {

    fun readText(context: Context, uri: Uri): String {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return ""
        val start = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        ) 3 else 0 // 跳过 UTF-8 BOM
        val body = bytes.copyOfRange(start, bytes.size)
        val utf8Decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            utf8Decoder.decode(ByteBuffer.wrap(body)).toString()
        } catch (e: CharacterCodingException) {
            try {
                Charset.forName("GB18030").decode(ByteBuffer.wrap(body)).toString()
            } catch (e2: Exception) {
                String(body, Charsets.UTF_8)
            }
        }
    }

    /** 读取所选文件的显示名，用于提示已读取哪个文件 */
    fun queryFileName(context: Context, uri: Uri): String {
        var name: String? = null
        context.contentResolver.query(
            uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) name = cursor.getString(idx)
            }
        }
        return name ?: uri.lastPathSegment?.substringAfterLast('/') ?: uri.toString()
    }
}
