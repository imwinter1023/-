package com.careos.launcher.data

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

/**
 * 家人位的数据来源。
 *
 * 三个固定槽位（儿子 / 女儿 / 老伴），存在 SharedPreferences 里。
 * 没有走 Room：三个槽位用数据库属于过度设计，而且桌面要在 onCreate 里同步取数。
 */
object ContactRepository {

    private const val FILE = "careos_family"
    private const val SLOT_COUNT = 3

    private val DEFAULT_NAMES = arrayOf("儿子", "女儿", "老伴")

    fun family(context: Context): List<FamilyContact> {
        val p = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        return (0 until SLOT_COUNT).map { i ->
            FamilyContact(
                index = i,
                name = p.getString("slot${i}_name", null) ?: DEFAULT_NAMES[i],
                number = p.getString("slot${i}_number", null).orEmpty(),
            )
        }
    }

    fun save(
        context: Context,
        index: Int,
        name: String? = null,
        number: String? = null,
    ) {
        val p = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val e = p.edit()
        name?.let { e.putString("slot${index}_name", it) }
        number?.let { e.putString("slot${index}_number", it) }
        e.apply()
    }

    fun hasContactsPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
                PackageManager.PERMISSION_GRANTED

    /**
     * 打开系统联系人选择器，让老人挑一个号码，而不是手输 11 位数字。
     * 返回的 Uri 用 [readPicked] 解析。
     */
    fun pickIntent(): Intent =
        Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)

    /** 解析选择器返回的 Uri，取出「姓名 + 号码」 */
    fun readPicked(context: Context, uri: Uri?): Pair<String, String>? {
        if (uri == null) return null
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
        )
        var cursor: Cursor? = null
        return try {
            cursor = context.contentResolver.query(uri, projection, null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val name = cursor.getString(0).orEmpty()
                val number = cursor.getString(1).orEmpty()
                if (number.isBlank()) null else name to number
            } else null
        } catch (e: Exception) {
            null
        } finally {
            cursor?.close()
        }
    }
}
