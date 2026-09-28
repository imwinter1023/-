package com.careos.launcher

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.careos.launcher.ui.theme.CareOSColor
import com.careos.launcher.ui.theme.CareOSFont

/**
 * ★ 全屏来电页 —— 对应画布上的「⑤ 收到来电」。
 *
 * 全屏大按钮方案：颜色通铺整屏、头像 220dp、接听/拒接各占半行的 96dp 大胶囊。
 * 一眼分得清、误点也不会打错。
 *
 * 怎么"盖在锁屏上"？两步缺一不可：
 *   1) Activity 侧：manifest 里 showWhenLocked + turnScreenOn，
 *      运行期再加 FLAG_KEEP_SCREEN_ON，保证屏幕亮着。
 *   2) 唤醒侧：从后台把界面推出来必须用「全屏 Intent 通知」，
 *      见 [notifyIncoming]。老板键式的 startActivity 在 Android 10+ 会被系统直接吞掉。
 */
class IncomingCallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 锁屏之上显示 + 点亮屏幕。
        // setShowWhenLocked / setTurnScreenOn 是 API 27 才有的便捷方法，而 minSdk 是 26，
        // 所以 Android 8.0 必须退回老的 Window flag —— 否则在那批机器上锁屏来电页根本不显示。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val callerName = intent.getStringExtra(EXTRA_NAME).orEmpty().ifBlank { "家人" }
        val relation = intent.getStringExtra(EXTRA_RELATION).orEmpty()
        val number = intent.getStringExtra(EXTRA_NUMBER).orEmpty()

        setContent {
            IncomingCallScreen(
                callerName = callerName,
                relation = relation,
                onAccept = {
                    // 接听：这里演示用普通语音接通。
                    // 真实工程里"微信视频来电"要接进微信的视频通道——而微信没有开放接口，
                    // 所以真机上这一路只能做成「接听 → 打开微信」，见 WeChatHelper 的说明。
                    com.careos.launcher.call.CallHelper.placeCall(this, number)
                    finish()
                },
                onDecline = { finish() },
            )
        }
    }

    companion object {

        private const val EXTRA_NAME = "extra_name"
        private const val EXTRA_RELATION = "extra_relation"
        private const val EXTRA_NUMBER = "extra_number"

        private const val CHANNEL_ID = "careos_incoming_call"
        private const val NOTIFICATION_ID = 9001

        fun intent(context: Context, name: String, relation: String, number: String): Intent =
            Intent(context, IncomingCallActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                putExtra(EXTRA_NAME, name)
                putExtra(EXTRA_RELATION, relation)
                putExtra(EXTRA_NUMBER, number)
            }

        /**
         * 发一条「全屏 Intent 通知」，系统会在锁屏上直接把来电页推出来。
         *
         * ⚠️ Android 14（API 34）起，USE_FULL_SCREEN_INTENT 变成特殊权限：
         *    只有"核心功能是通话或闹钟"的应用默认拥有，其余应用需要用户手动开启。
         *    所以上线前必须做一件事：检测是否授权，未授权就引导到
         *    [fullScreenIntentSettings] 这个设置页。否则来电页只会变成一条普通通知。
         */
        fun notifyIncoming(context: Context, name: String, relation: String, number: String) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "来电",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "家人来电时的全屏提醒"
                    setShowBadge(false)
                    enableVibration(true)
                }
                nm.createNotificationChannel(channel)
            }

            val fullScreen = PendingIntent.getActivity(
                context,
                0,
                intent(context, name, relation, number),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_call)
                .setContentTitle("${if (relation.isBlank()) name else "$name · $relation"} 来电")
                .setContentText("点一下接听")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setOngoing(true)
                .setAutoCancel(true)
                .setFullScreenIntent(fullScreen, true)
                .build()

            try {
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            } catch (e: SecurityException) {
                // Android 13+ 没给 POST_NOTIFICATIONS：安静失败，别崩
            }
        }

        /** 引导用户去开「全屏通知」特殊权限（Android 14+） */
        fun fullScreenIntentSettings(context: Context): Intent =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                    .setData(android.net.Uri.parse("package:${context.packageName}"))
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(android.net.Uri.parse("package:${context.packageName}"))
            }
    }
}

@Composable
private fun IncomingCallScreen(
    callerName: String,
    relation: String,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    // 通话绿通铺整屏 —— 这是设计稿里定的：绿底 = 有人找你，白底按钮 = 接，红底按钮 = 拒
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CareOSColor.callGreen)
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 顶部状态胶囊
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(100.dp))
                .background(Color.White.copy(alpha = 0.18f))
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF7CF0B0)),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "微信视频来电",
                fontSize = CareOSFont.body.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
            )
        }

        Spacer(Modifier.height(30.dp))

        // 220dp 大头像。没有真实头像时用姓名首字，不留灰块。
        Box(
            modifier = Modifier
                .size(220.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = callerName.take(1),
                fontSize = 96.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = callerName,
            fontSize = CareOSFont.huge.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        if (relation.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = relation,
                fontSize = CareOSFont.action.sp,
                fontWeight = FontWeight.Medium,
                color = CareOSColor.onCallGreenSoft,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "正在呼叫…",
            fontSize = CareOSFont.body.sp,
            color = CareOSColor.onCallGreenSoft,
        )

        Spacer(Modifier.weight(1f))

        // 底部两颗大胶囊：拒接（红） / 接听（白底绿字，最高对比度）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BigCallButton(
                label = "拒接",
                iconRes = R.drawable.ic_hangup,
                background = CareOSColor.hangupRed,
                content = Color.White,
                modifier = Modifier.weight(1f),
                onClick = onDecline,
            )
            BigCallButton(
                label = "接听",
                iconRes = R.drawable.ic_call,
                background = Color.White,
                content = CareOSColor.callGreen,
                modifier = Modifier.weight(1f),
                onClick = onAccept,
            )
        }
    }
}

@Composable
private fun BigCallButton(
    label: String,
    iconRes: Int,
    background: Color,
    content: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .height(96.dp)
            .clip(RoundedCornerShape(48.dp))
            .background(background)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(34.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            fontSize = CareOSFont.pageTitle.sp,
            fontWeight = FontWeight.Bold,
            color = content,
        )
    }
}
