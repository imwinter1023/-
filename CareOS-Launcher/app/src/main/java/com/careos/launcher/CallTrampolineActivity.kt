package com.careos.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.careos.launcher.call.CallHelper
import com.careos.launcher.call.WeChatHelper
import com.careos.launcher.data.ContactRepository

/**
 * 桌面组件的「跳板」Activity —— 一个完全没有界面的中转站。
 *
 * 为什么需要它？这是 App Widget 这块最容易踩的坑：
 *   Android 10（API 29）起禁止应用在后台启动 Activity。
 *   如果直接在 AppWidgetProvider.onReceive 里 startActivity(ACTION_CALL)，
 *   系统会把这次启动静默丢弃 —— 表现就是"点了没反应"，而且没有任何报错，很难查。
 *
 * 正确解法：组件按钮挂 PendingIntent.getActivity 指向本 Activity。
 * 用户点组件属于前台用户操作，系统会放行这次 Activity 启动；
 * 我们在 onCreate 里把事办完（拨号 / 打开微信）随后立刻 finish，
 * 配合 Theme.CareOS.Trampoline 的透明主题，用户完全感知不到中间这一步。
 */
class CallTrampolineActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        when (intent.action) {
            ACTION_CALL -> {
                val slot = intent.getIntExtra(EXTRA_SLOT, 0)
                // 以组件配置时存下的槽位号为准，而不是组件当前显示的内容 —— 避免状态不同步
                val contact = ContactRepository.family(this).getOrNull(slot)
                if (contact != null && contact.hasNumber) {
                    CallHelper.placeCall(this, contact.number)
                }
            }

            ACTION_WECHAT -> WeChatHelper.openWeChat(this)
        }

        finish()
    }

    companion object {
        const val ACTION_CALL = "com.careos.launcher.action.WIDGET_CALL"
        const val ACTION_WECHAT = "com.careos.launcher.action.WIDGET_WECHAT"
        const val EXTRA_SLOT = "extra_slot"
    }
}
