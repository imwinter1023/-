package com.careos.launcher.call

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat

/**
 * 一键拨号。
 *
 * ACTION_CALL  = 拿到 CALL_PHONE 权限后**直接拨出**，不弹系统二次确认 —— 这才是"一键"。
 * ACTION_DIAL  = 只打开拨号盘并预填号码，老人还得再按一下绿色键。
 *
 * 所以无权限时降级到 ACTION_DIAL，而不是直接失败：宁可多一步，也不能点不动。
 */
object CallHelper {

    fun hasCallPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
                PackageManager.PERMISSION_GRANTED

    fun placeCall(context: Context, rawNumber: String): Boolean {
        val number = normalize(rawNumber)
        if (number.isEmpty()) return false

        val action = if (hasCallPermission(context)) Intent.ACTION_CALL else Intent.ACTION_DIAL
        val intent = Intent(action, Uri.fromParts("tel", number, null)).apply {
            // 从 CallTrampolineActivity / 接收器发起时需要；在 Activity 里发起也无害
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    /** 只保留数字和开头的 +，顺手清掉老人手输时带进来的空格、横杠、括号 */
    private fun normalize(raw: String): String {
        val trimmed = raw.trim()
        val hasPlus = trimmed.startsWith("+")
        val digits = trimmed.filter { it.isDigit() }
        return if (hasPlus) "+$digits" else digits
    }
}
