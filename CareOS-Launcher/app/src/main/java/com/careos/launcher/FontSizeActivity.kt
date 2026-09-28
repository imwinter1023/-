package com.careos.launcher

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.careos.launcher.prefs.DisplayPrefs
import com.careos.launcher.ui.theme.CareOSColor
import com.careos.launcher.ui.theme.CareOSFont
import com.careos.launcher.ui.theme.CareOSSize
import kotlin.math.roundToInt

/**
 * ★ 字号设置页 —— 对应画布上的「⑥ 设置 · 字号调节」。
 *
 * 两个设计决策在这里落地：
 *   1) 滑块七档，但显示的是「小 / 较小 / 标准 / 较大 / 大 / 特大 / 最大」这种口语化档位名，
 *      不给用户看 1.4x、1.55x 这种数字 —— 老人不需要知道倍率。
 *   2) 拖动时下方的示例文字**实时跟着变**，所见即所得，不用退出去看效果。
 */
class FontSizeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val context = LocalContext.current

            var highContrast by remember { mutableStateOf(DisplayPrefs.highContrast(context)) }
            var bold by remember { mutableStateOf(DisplayPrefs.bold(context)) }

            // 滑块当前位置。预览必须**跟手**，所以它直接驱动下面的 scale / tag，
            // 而不是等松手才更新 —— 否则「拖动时实时预览」这句注释就是假的。
            var slider by remember { mutableFloatStateOf(DisplayPrefs.level(context).toFloat()) }
            val shown = slider.roundToInt().coerceIn(0, DisplayPrefs.LEVEL_COUNT - 1)

            // 设置页自己也跟着字号走 —— 不然用户在「小」档位下看这个页面一样吃力
            val scale = DisplayPrefs.scaleOf(shown)

            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, scale)
            ) {
                FontSizeScreen(
                    slider = slider,
                    onSliderChange = { slider = it },
                    onSliderFinished = {
                        // 松手才落盘：拖动过程中每帧写 SharedPreferences 是纯浪费
                        DisplayPrefs.setLevel(context, slider.roundToInt())
                    },
                    tag = DisplayPrefs.tag(shown),
                    highContrast = highContrast,
                    bold = bold,
                    onHighContrast = {
                        highContrast = it
                        DisplayPrefs.setHighContrast(context, it)
                    },
                    onBold = {
                        bold = it
                        DisplayPrefs.setBold(context, it)
                    },
                    onBack = { finish() },
                )
            }
        }
    }
}

@Composable
private fun FontSizeScreen(
    slider: Float,
    onSliderChange: (Float) -> Unit,
    onSliderFinished: () -> Unit,
    tag: String,
    highContrast: Boolean,
    bold: Boolean,
    onHighContrast: (Boolean) -> Unit,
    onBold: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CareOSColor.bg)
            .verticalScroll(rememberScrollState()),
    ) {
        // 页头
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(CareOSColor.surface)
                    .border(1.dp, CareOSColor.border, CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_back),
                    contentDescription = "返回",
                    tint = CareOSColor.textPrimary,
                    modifier = Modifier.size(28.dp),
                )
            }
            Text(
                text = "显示与字号",
                fontSize = CareOSFont.pageTitle.sp,
                fontWeight = FontWeight.Bold,
                color = CareOSColor.textPrimary,
            )
        }

        Column(
            modifier = Modifier.padding(horizontal = 22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 卡片一：字号滑块
            Card {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "字号大小",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = CareOSColor.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = tag,
                        fontSize = CareOSFont.body.sp,
                        fontWeight = FontWeight.Bold,
                        color = CareOSColor.primaryDark,
                        modifier = Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .background(CareOSColor.primarySoft)
                            .padding(horizontal = 18.dp, vertical = 8.dp),
                    )
                }

                Slider(
                    value = slider,
                    onValueChange = onSliderChange,
                    onValueChangeFinished = onSliderFinished,
                    valueRange = 0f..(DisplayPrefs.LEVEL_COUNT - 1).toFloat(),
                    steps = DisplayPrefs.LEVEL_COUNT - 2,
                    colors = SliderDefaults.colors(
                        thumbColor = CareOSColor.surface,
                        activeTrackColor = CareOSColor.primary,
                        inactiveTrackColor = CareOSColor.track,
                        activeTickColor = Color.Transparent,
                        inactiveTickColor = Color.Transparent,
                    ),
                )

                // 四个口语化刻度名，对应画布上的刻度条
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    listOf("小", "标准", "大", "最大").forEach { t ->
                        val on = tag == t || (t == "标准" && tag == "较小") || (t == "大" && tag == "较大")
                        Text(
                            text = t,
                            fontSize = 18.sp,
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                            color = if (on) CareOSColor.primaryDark else CareOSColor.textSecondary,
                        )
                    }
                }
            }

            // 卡片二：实时预览
            Card {
                Text(
                    text = "效果预览",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = CareOSColor.textPrimary,
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(CareOSColor.track)
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "常用联系人",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = CareOSColor.textPrimary,
                    )
                    Text(
                        text = "儿子 · 138 0013 8000",
                        fontSize = CareOSFont.body.sp,
                        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                        color = CareOSColor.textPrimary,
                    )
                    Text(
                        text = "点一下名字就能打电话",
                        fontSize = 18.sp,
                        color = CareOSColor.textSecondary,
                    )
                }
            }

            // 卡片三：其余显示开关
            Card(padding = 0.dp) {
                SettingRow("高对比度文字", highContrast, onHighContrast)
                Divider()
                SettingRow("文字加粗显示", bold, onBold)
            }

            // 完成
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp)
                    .clip(RoundedCornerShape(CareOSSize.radiusCard.dp))
                    .background(CareOSColor.primary)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "完成",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }

            Spacer(Modifier.height(22.dp))
        }
    }
}

@Composable
private fun Card(
    padding: androidx.compose.ui.unit.Dp = 20.dp,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CareOSSize.radiusCard.dp))
            .background(CareOSColor.surface)
            .border(1.dp, CareOSColor.border, RoundedCornerShape(CareOSSize.radiusCard.dp))
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        content()
    }
}

@Composable
private fun SettingRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    // 整行可点，不是只有开关能点 —— 老人手指粗，开关本体 44dp 太窄
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = CareOSFont.action.sp,
            fontWeight = FontWeight.Medium,
            color = CareOSColor.textPrimary,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = CareOSColor.primary,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = CareOSColor.track,
            ),
        )
    }
}

@Composable
private fun Divider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(CareOSColor.track),
    )
}
