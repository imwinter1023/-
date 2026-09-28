package com.careos.launcher.call

import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * ★ 这是整套设计里唯一一处「做不到」的地方，代码注释写在这里，也写进 README。
 *
 * ============================================================
 * 微信没有开放「给指定联系人发起视频通话」的能力。
 * ============================================================
 * 微信官方 SDK、开放平台、URL Scheme 都没有这个接口。市面上所有号称做到的方案，
 * 只有两种实现：
 *
 *   1) 无障碍服务（AccessibilityService）模拟点击：需要用户手动开启无障碍权限，
 *      依赖微信当前版本的界面结构，微信一次改版就失效；而且老人误触风险高。
 *      → 不建议用在适老产品上。
 *
 *   2) 只把微信拉起来，让老人自己在微信里点进家人聊天，再点「+」「视频通话」。
 *      配合微信自带的「聊天置顶」，打开微信后最上面就是家人。
 *
 * 本骨架走第 2 条路：诚实降级为「一键打开微信」。
 * 也就是：设计稿上的「一键微信视频」→ 真机上是「一键 + 两次轻点」。
 *
 * 如果产品坚持要"真一键"，那只能改需求方向：
 *   把它做成微信小程序（但小程序无法做到系统桌面组件），
 *   或者走运营商/手机厂商的系统级合作（成本高，且依然受限于微信）。
 */
object WeChatHelper {

    private const val WECHAT_PACKAGE = "com.tencent.mm"

    fun isWeChatInstalled(context: Context): Boolean = try {
        // 需要在 AndroidManifest 的 <queries> 里声明包名，否则 Android 11+ 查不到
        context.packageManager.getPackageInfo(WECHAT_PACKAGE, 0)
        true
    } catch (e: Exception) {
        false
    }

    /**
     * 打开微信主界面。
     * 微信曾经支持 weixin:// 这类深链直接落到某个会话，但早已被限制，
     * 现在只能拉起默认入口 —— 不要再花时间试深链了。
     */
    fun openWeChat(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(WECHAT_PACKAGE)
        if (launch == null) {
            Toast.makeText(context, "手机里还没有安装微信", Toast.LENGTH_LONG).show()
            return
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(launch)
        } catch (e: Exception) {
            Toast.makeText(context, "打开微信失败，请手动打开", Toast.LENGTH_LONG).show()
        }
    }
}
