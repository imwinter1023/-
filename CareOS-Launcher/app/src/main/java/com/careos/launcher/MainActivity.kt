package com.careos.launcher

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.careos.launcher.call.CallHelper
import com.careos.launcher.call.SosHelper
import com.careos.launcher.call.WeChatHelper
import com.careos.launcher.data.ContactRepository
import com.careos.launcher.data.FamilyContact
import com.careos.launcher.prefs.DisplayPrefs
import com.careos.launcher.ui.theme.CareOSColor
import com.careos.launcher.ui.theme.CareOSFont
import com.careos.launcher.ui.theme.CareOSSize
import com.careos.launcher.widget.CallWidgetProvider
import java.util.Calendar

/**
 * ★ 桌面本体。
 *
 * 这个 Activity 之所以能成为「系统桌面」，全靠 AndroidManifest 里那条
 * HOME + DEFAULT 的 intent-filter。装上之后引导用户
 * 「设置 → 应用 → 默认应用 → 主屏幕应用」选它，就替代了系统桌面。
 *
 * 设计稿 vs 真机的一处刻意偏差：
 *   画布上有一根自绘状态栏（时间 + 信号 + 电量）。真机这里**保留了系统状态栏**，
 *   只是把底色染成暖白、图标转深色。原因：自绘的电量/信号如果画错或延迟更新，
 *   会让老人误判手机状态 —— 这类信息必须来自系统，不能是设计的装饰。
 */
class MainActivity : ComponentActivity() {

    private val requestCallPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val requestContactsPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val requestPhoneStatePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    /** 正在等待用户从通讯录里挑号码的槽位 */
    private var pendingSlot = -1

    /**
     * 数据版本号：挑完联系人 / 从设置页回来时 +1，让 Compose 重新读一遍家人位与显示偏好。
     *
     * 桌面是长期存活的 Activity（launchMode=singleTask，几乎不会被销毁重建），
     * onResume 时 Compose **不会**自动重组 —— 没有这个计数器，
     * 用户把字号拉大、返回桌面，会看到字根本没变，像是设置没生效。
     */
    private val dataVersion = mutableIntStateOf(0)

    private val pickContact =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val slot = pendingSlot
            pendingSlot = -1
            if (slot < 0) return@registerForActivityResult
            val picked = ContactRepository.readPicked(this, result.data?.data) ?: return@registerForActivityResult
            val (name, number) = picked
            ContactRepository.save(this, slot, name = name, number = number)
            // 桌面上可能已经摆了这位家人的组件，号码变了组件也得跟着换
            CallWidgetProvider.refreshAll(this)
            dataVersion.intValue++ // 让桌面立刻刷新
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 进桌面就把三个关键权限一次问完。
        // 为什么不等用户点按钮时再问：老人点「打电话」被系统权限弹窗拦住，
        // 大概率不知道该怎么办，直接就放弃了。
        if (!CallHelper.hasCallPermission(this)) {
            requestCallPermission.launch(Manifest.permission.CALL_PHONE)
        }
        if (!ContactRepository.hasContactsPermission(this)) {
            requestContactsPermission.launch(Manifest.permission.READ_CONTACTS)
        }
        // Android 13+ 发通知必须运行时授权。少了这一步，来电的全屏通知会被系统直接丢掉，
        // 表现是「来电页压根不弹」，而且不报错 —— 这类坑不申请就永远查不出来。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        // 紧急求助的「第一位没接听就自动改拨下一位」靠通话状态回调实现。
        // 没这个权限不会崩，但会安静退化成「只拨第一位」——功能少一半，且没有任何提示。
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPhoneStatePermission.launch(Manifest.permission.READ_PHONE_STATE)
        }

        // 桌面不该被返回键退出 —— 吞掉它
        onBackPressedDispatcher.addCallback(this) { /* 桌面无返回语义 */ }

        setContent {
            val context = LocalContext.current

            // dataVersion 变化 → 重新读一次家人位（挑完联系人后能立刻看到）
            val contacts = remember(dataVersion.intValue) { ContactRepository.family(context) }
            val fontScale = DisplayPrefs.fontScale(context)
            val highContrast = DisplayPrefs.highContrast(context)
            val bold = DisplayPrefs.bold(context)

            // ★ 字号缩放的实现核心：
            //   只改 CompositionLocal 里的 fontScale，子树中所有 sp 自动等比放大。
            //   比手动给每个 fontSize 乘系数可靠得多 —— 不会漏掉任何一处文字。
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, fontScale)
            ) {
                HomeScreen(
                    contacts = contacts,
                    bold = bold,
                    highContrast = highContrast,
                    onOpenFontSettings = {
                        startActivity(Intent(this, FontSizeActivity::class.java))
                    },
                    onCall = { contact -> handleCall(contact.index, contact.number) },
                    onWeChat = { WeChatHelper.openWeChat(context) },
                    onSos = { SosHelper.start(context, contacts) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 回到桌面 = 这一轮紧急求助结束了，停掉重拨链
        SosHelper.stop(this)
        // ★ 让 Compose 重读一遍显示偏好。
        //   桌面是长期存活 Activity，从设置页改完字号返回时不会自动重组；
        //   少了这一行，用户把字拉大、回到桌面发现字没变，看起来就是「设置没生效」。
        dataVersion.intValue++
    }

    /** 有号码就直拨；没号码就先带用户去通讯录挑一个 */
    private fun handleCall(slot: Int, number: String) {
        if (number.isBlank()) {
            pendingSlot = slot
            try {
                pickContact.launch(ContactRepository.pickIntent())
            } catch (e: Exception) {
                // 没有联系人应用 / 没有权限：安静失败，按钮上已经写了"点「打电话」添加号码"
            }
            return
        }
        CallHelper.placeCall(this, number)
    }
}

@Composable
private fun HomeScreen(
    contacts: List<FamilyContact>,
    bold: Boolean,
    highContrast: Boolean,
    onOpenFontSettings: () -> Unit,
    onCall: (FamilyContact) -> Unit,
    onWeChat: () -> Unit,
    onSos: () -> Unit,
) {
    val bg = if (highContrast) Color.White else CareOSColor.bg
    val fg = if (highContrast) Color.Black else CareOSColor.textPrimary
    val sub = if (highContrast) Color(0xFF333333) else CareOSColor.textSecondary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp)
            .padding(top = 10.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = greeting(),
                    fontSize = CareOSFont.pageTitle.sp,
                    fontWeight = FontWeight.Bold,
                    color = fg,
                )
                Text(
                    text = today(),
                    fontSize = CareOSFont.caption.sp,
                    color = sub,
                )
            }
            Text(
                text = "字号",
                fontSize = CareOSFont.body.sp,
                fontWeight = FontWeight.Medium,
                color = CareOSColor.primaryDark,
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .background(CareOSColor.primarySoft)
                    .clickable(onClick = onOpenFontSettings)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }

        contacts.forEach { contact ->
            ContactCard(
                contact = contact,
                bold = bold,
                onCall = { onCall(contact) },
                onWeChat = onWeChat,
            )
        }

        SosButton(onClick = onSos)
    }
}

@Composable
private fun ContactCard(
    contact: FamilyContact,
    bold: Boolean,
    onCall: () -> Unit,
    onWeChat: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CareOSSize.radiusCard.dp))
            .background(CareOSColor.surface)
            .border(1.dp, CareOSColor.border, RoundedCornerShape(CareOSSize.radiusCard.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 上排：头像 + 姓名 / 号码
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(CareOSColor.primarySoft),
                contentAlignment = Alignment.Center,
            ) {
                // 没有真实头像时不放灰色块，用姓名首字 —— 老人认字比认色块快
                Text(
                    text = contact.name.take(1),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = CareOSColor.primaryDark,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = contact.name,
                    fontSize = CareOSFont.cardTitle.sp,
                    fontWeight = FontWeight.Bold,
                    color = CareOSColor.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (contact.hasNumber) contact.number else "点「打电话」添加号码",
                    fontSize = CareOSFont.caption.sp,
                    color = if (contact.hasNumber) CareOSColor.textSecondary else CareOSColor.primaryDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // 下排：两个整行大按钮。
        // 这里刻意不用「并排的两个小圆形图标」—— 那是设计稿早期版本踩过的坑：
        // 390 宽的行里塞头像 + 两个 64px 圆钮，中间文字只剩 78px，号码必然换行撑破行高。
        ActionButton("打电话", R.drawable.ic_call, CareOSColor.callGreen, bold, onCall)
        ActionButton("微信视频", R.drawable.ic_video, CareOSColor.wechatGreen, bold, onWeChat)
    }
}

@Composable
private fun ActionButton(
    label: String,
    iconRes: Int,
    color: Color,
    bold: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(CareOSSize.touchPrimary.dp)
            .clip(RoundedCornerShape(CareOSSize.radiusButton.dp))
            .background(color)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(26.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            fontSize = CareOSFont.action.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
            color = Color.White,
        )
    }
}

@Composable
private fun SosButton(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(CareOSSize.touchCritical.dp)
            .clip(RoundedCornerShape(CareOSSize.radiusCard.dp))
            .background(CareOSColor.hangupRed)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "紧急求助",
            fontSize = CareOSFont.pageTitle.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        Text(
            text = "点一下就开始呼叫家人",
            fontSize = CareOSFont.caption.sp,
            color = Color(0xFFFFE0DC),
        )
    }
}

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..8 -> "早上好"
    in 9..11 -> "上午好"
    in 12..13 -> "中午好"
    in 14..17 -> "下午好"
    in 18..22 -> "晚上好"
    else -> "夜深了"
}

private fun today(): String {
    val cal = Calendar.getInstance()
    val week = "日一二三四五六"[cal.get(Calendar.DAY_OF_WEEK) - 1]
    return "${cal.get(Calendar.MONTH) + 1}月${cal.get(Calendar.DAY_OF_MONTH)}日 星期$week"
}
