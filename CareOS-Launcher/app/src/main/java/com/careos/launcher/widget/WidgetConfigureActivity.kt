package com.careos.launcher.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.careos.launcher.data.ContactRepository
import com.careos.launcher.data.FamilyContact
import com.careos.launcher.ui.theme.CareOSColor
import com.careos.launcher.ui.theme.CareOSFont
import com.careos.launcher.ui.theme.CareOSSize

/**
 * 添加桌面组件时的配置页：先把"这个组件给哪位家人"问清楚。
 *
 * 契约（写错了组件就会消失或卡住）：
 *   - 必须在 AndroidManifest 里给本 Activity 挂 APPWIDGET_CONFIGURE 的 intent-filter；
 *   - 必须在 appwidget-provider XML 里用 android:configure 指过来；
 *   - 结束时 **必须** setResult(RESULT_OK, intent 带上 EXTRA_APPWIDGET_ID)，
 *     否则宿主会认为配置被取消，直接把刚加的组件删掉。
 */
class WidgetConfigureActivity : ComponentActivity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setResult(RESULT_CANCELED)
        widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            val context = LocalContext.current
            val contacts = remember { ContactRepository.family(context) }
            ConfigureScreen(
                contacts = contacts,
                onPick = { slot ->
                    CallWidgetProvider.bindSlot(context, widgetId, slot)
                    val manager = AppWidgetManager.getInstance(context)
                    CallWidgetProvider.render(context, manager, widgetId)
                    val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    setResult(RESULT_OK, result)
                    finish()
                },
                onCancel = { finish() },
            )
        }
    }
}

@Composable
private fun ConfigureScreen(
    contacts: List<FamilyContact>,
    onPick: (Int) -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CareOSColor.bg)
            .padding(horizontal = 22.dp, vertical = 26.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "这个组件给哪位家人？",
            fontSize = CareOSFont.pageTitle.sp,
            fontWeight = FontWeight.Bold,
            color = CareOSColor.textPrimary,
        )
        Text(
            text = "选好之后，桌面组件上点一下就拨给他",
            fontSize = CareOSFont.caption.sp,
            color = CareOSColor.textSecondary,
        )

        contacts.forEach { contact ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CareOSSize.touchPrimary.dp)
                    .clip(RoundedCornerShape(CareOSSize.radiusCard.dp))
                    .background(CareOSColor.surface)
                    .border(1.dp, CareOSColor.border, RoundedCornerShape(CareOSSize.radiusCard.dp))
                    .clickable { onPick(contact.index) }
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(CareOSColor.primarySoft),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = contact.name.take(1),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = CareOSColor.primaryDark,
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = contact.name,
                        fontSize = CareOSFont.action.sp,
                        fontWeight = FontWeight.Bold,
                        color = CareOSColor.textPrimary,
                    )
                    Text(
                        text = if (contact.hasNumber) contact.number else "还没有号码，先回桌面添一下",
                        fontSize = 16.sp,
                        color = CareOSColor.textSecondary,
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(CareOSSize.radiusButton.dp))
                .clickable(onClick = onCancel),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "取消",
                fontSize = CareOSFont.action.sp,
                fontWeight = FontWeight.Medium,
                color = CareOSColor.textSecondary,
            )
        }
    }
}
