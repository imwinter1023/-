package com.careos.launcher.call

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.careos.launcher.data.FamilyContact

/**
 * 紧急求助：单击即拨，不做二次确认（产品决策，已确认）。
 *
 * 行为：
 *   1. 立刻拨出第一位有号码的家人；
 *   2. 若在 [RING_TIMEOUT_MS] 内没有进入「已接通」（没有 OFFHOOK），自动改拨下一位；
 *   3. 一旦接通就停手，不再往后拨。
 *
 * 注意这里是"有没有被接起来"，不是"有没有振铃" —— 判断依据是是否出现过 OFFHOOK。
 * 各家 OEM 的通话状态回调时序不完全一致（有的拨号瞬间就报一次 IDLE），
 * 所以用"有没有见过 OFFHOOK"作为唯一真相，比数状态次数稳。
 */
object SosHelper {

    private const val RING_TIMEOUT_MS = 20_000L

    private val main = Handler(Looper.getMainLooper())
    private var timeout: Runnable? = null
    private var callback: TelephonyCallback? = null
    private var alive = false

    fun start(context: Context, contacts: List<FamilyContact>) {
        val app = context.applicationContext
        val reachable = contacts.filter { it.hasNumber }
        if (reachable.isEmpty()) return

        stop(app) // 连点两次就重新开始，不要叠出两条呼叫链

        alive = true
        var cursor = 0

        fun callNext() {
            if (!alive || cursor >= reachable.size) {
                stop(app)
                return
            }
            val target = reachable[cursor]
            CallHelper.placeCall(app, target.number)
            cursor++

            timeout?.let { main.removeCallbacks(it) }
            timeout = Runnable { if (alive) callNext() }.also {
                main.postDelayed(it, RING_TIMEOUT_MS)
            }
        }

        if (registerCallState(app) { state ->
                if (state == TelephonyManager.CALL_STATE_OFFHOOK) {
                    // 接通了：取消后续改拨
                    timeout?.let { main.removeCallbacks(it) }
                }
            }
        ) {
            callNext()
        } else {
            // 拿不到通话状态（没给 READ_PHONE_STATE）：退化成"只拨第一位"，别把功能做没了
            reachable.firstOrNull()?.let { CallHelper.placeCall(app, it.number) }
        }
    }

    fun stop(context: Context) {
        alive = false
        timeout?.let { main.removeCallbacks(it) }
        timeout = null
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        callback?.let { cb ->
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    tm?.unregisterTelephonyCallback(cb)
                } else {
                    @Suppress("DEPRECATION")
                    tm?.listen(null, TelephonyManager.LISTEN_NONE)
                }
            } catch (e: Exception) {
                // 注销失败无所谓，下次 start 会重建
            }
        }
        callback = null
    }

    /** 注册通话状态监听。返回是否注册成功。 */
    private fun registerCallState(context: Context, onState: (Int) -> Unit): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return false
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) = onState(state)
                }
                tm.registerTelephonyCallback(context.mainExecutor, cb)
                callback = cb
                true
            } else {
                @Suppress("DEPRECATION")
                tm.listen({ state, _, _ -> onState(state) }, TelephonyManager.LISTEN_CALL_STATE)
                true
            }
        } catch (e: Exception) {
            false
        }
    }
}
