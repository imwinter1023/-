package com.careos.launcher.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.careos.launcher.CallTrampolineActivity
import com.careos.launcher.R
import com.careos.launcher.data.ContactRepository

/**
 * ★ 桌面组件本体 —— 设计稿里「桌面上一键打电话 / 一键微信视频」的落点。
 *
 * 每个组件实例绑定一个家人位（添加组件时由 [WidgetConfigureActivity] 选）。
 * 于是老人可以在桌面上摆三块，分别是儿子、女儿、老伴，各点一下直拨。
 *
 * 三个 RemoteViews 的坑，这里都绕开了：
 *   1) 点击事件必须走 PendingIntent。而且**不能**用广播直接拨号：
 *      Android 10+ 禁止后台启动 Activity，接收器里 startActivity(ACTION_CALL) 会被静默丢弃。
 *      → 所以按钮挂的是 getActivity 指向 CallTrampolineActivity（透明跳板）。
 *   2) PendingIntent 的 requestCode 必须按「组件 id + 按钮」区分开，
 *      否则不同组件的点击会互相覆盖，表现为"点儿子打给了女儿"。
 *   3) RemoteViews 里不能设圆角、不能设自定义字体，圆角只能在布局里用 shape drawable 给。
 */
class CallWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { id -> render(context, appWidgetManager, id) }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        // 组件被移出桌面时清掉它的绑定关系，避免 SharedPreferences 里堆垃圾
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        appWidgetIds.forEach { editor.remove(slotKey(it)) }
        editor.apply()
    }

    companion object {

        private const val PREFS = "careos_widget"
        private const val ACTION_REFRESH = "com.careos.launcher.action.WIDGET_REFRESH"

        private fun slotKey(widgetId: Int) = "widget_slot_$widgetId"

        fun boundSlot(context: Context, widgetId: Int): Int =
            context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(slotKey(widgetId), 0)

        fun bindSlot(context: Context, widgetId: Int, slot: Int) =
            context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putInt(slotKey(widgetId), slot)
                .apply()

        /** 家人位号码变了之后，通知所有组件重画 */
        fun refreshAll(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
            val ids = appWidgetManager.getAppWidgetIds(
                android.content.ComponentName(context, CallWidgetProvider::class.java)
            )
            ids.forEach { render(context, appWidgetManager, it) }
        }

        fun render(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val slot = boundSlot(context, widgetId)
            val contact = ContactRepository.family(context).getOrNull(slot)

            val views = RemoteViews(context.packageName, R.layout.widget_call).apply {
                val name = contact?.name.orEmpty().ifBlank { "家人" }
                setTextViewText(R.id.widget_name, name)
                setTextViewText(R.id.widget_avatar, name.take(1))
                setTextViewText(
                    R.id.widget_number,
                    if (contact?.hasNumber == true) contact.number else "点组件添加号码",
                )

                // requestCode 编码：widgetId * 10 + 动作序号，保证互不冲突
                setOnClickPendingIntent(
                    R.id.widget_btn_call,
                    trampoline(context, widgetId, slot, CallTrampolineActivity.ACTION_CALL, 1),
                )
                setOnClickPendingIntent(
                    R.id.widget_btn_wechat,
                    trampoline(context, widgetId, slot, CallTrampolineActivity.ACTION_WECHAT, 2),
                )
            }

            manager.updateAppWidget(widgetId, views)
        }

        private fun trampoline(
            context: Context,
            widgetId: Int,
            slot: Int,
            action: String,
            offset: Int,
        ): PendingIntent {
            val intent = Intent(context, CallTrampolineActivity::class.java).apply {
                this.action = action
                putExtra(CallTrampolineActivity.EXTRA_SLOT, slot)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            return PendingIntent.getActivity(
                context,
                widgetId * 10 + offset,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
