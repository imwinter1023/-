# 夕阳红桌面 CareOS · 安卓骨架

把《夕阳红 OS》的设计稿落成一台**真机上能跑**的适老桌面。

不是演示 demo —— 它注册为系统桌面（Launcher），装上并设为默认主屏幕后，老人按 Home 键看到的就是它；桌面组件点一下就真的拨出去。

---

> **想直接装上手机 →** 看根目录的 **《安装到手机.md》**，从编译到装机的完整步骤都在那儿。

## 一、怎么跑起来

```bash
# 用 Android Studio（Hedgehog 或更新）直接打开这个目录，等 Gradle 同步完
# 然后 Run ▶ 到手机 / 模拟器

# 工程未附带 gradle-wrapper.jar（二进制，无法随源码文本交付），
# 但 gradle/wrapper/gradle-wrapper.properties 已配好（Gradle 8.9）。
# Android Studio 会照它自动下载，无需 gradle-wrapper.jar。
# 若要用命令行 ./gradlew，先在 AS 的 Terminal 里执行一次 `gradle wrapper` 生成。
```

装好之后还有 **两步手动配置**，少一步功能就不完整：

| 步骤 | 路径 | 不做的后果 |
|---|---|---|
| ① 设为默认桌面 | 设置 → 应用 → 默认应用 → **主屏幕应用** → 选「夕阳红桌面」 | 按 Home 还是回系统桌面，白装 |
| ② 允许全屏通知 | 设置 → 应用 → 夕阳红桌面 → **全屏通知** 打开（Android 14+ 才有这一项） | 来电页只会变成一条普通通知，不会盖在锁屏上 |

进桌面时会一次性申请四个运行时权限：`CALL_PHONE`、`READ_CONTACTS`、`POST_NOTIFICATIONS`（Android 13+）、
`READ_PHONE_STATE`。这是**故意**放在首屏就问的：等老人点「打电话」时才弹权限框，
他大概率不知道该怎么办，直接放弃。

---

## 二、骨架里有什么

```
app/src/main/java/com/careos/launcher/
├── MainActivity.kt            ★ 桌面本体（替代系统主屏）
├── IncomingCallActivity.kt    ★ 全屏来电页，盖在锁屏上
├── FontSizeActivity.kt        ★ 字号设置页（七档滑块 + 实时预览）
├── CallTrampolineActivity.kt  ★ 组件点击的透明跳板（关键，见下）
├── call/
│   ├── CallHelper.kt          一键拨号：ACTION_CALL，不弹二次确认
│   ├── WeChatHelper.kt        ★ 「一键微信视频」的能力边界，全写在这
│   └── SosHelper.kt           紧急求助：单击直拨 + 没接听自动改拨下一位
├── data/
│   ├── FamilyContact.kt       家人位的数据模型
│   └── ContactRepository.kt   三个槽位 + 从系统通讯录挑号码
├── prefs/DisplayPrefs.kt      字号档位 / 高对比度 / 加粗
├── widget/
│   ├── CallWidgetProvider.kt      ★ 桌面组件（RemoteViews）
│   └── WidgetConfigureActivity.kt 添加组件时选"给哪位家人"
└── ui/theme/CareOS.kt         设计令牌（画布变量的 Kotlin 投影）
```

画布上的 6 屏，对应关系：

| 画布 | 代码 |
|---|---|
| ① 桌面主页 | `MainActivity` |
| ② 选择联系人 | `ContactRepository.pickIntent()` 调起系统通讯录选择器 |
| ③ 语音通话中 / ④ 微信视频通话中 | 交给系统通话界面（第三方不能自绘通话 UI） |
| ⑤ 收到来电 | `IncomingCallActivity` |
| ⑥ 设置 · 字号调节 | `FontSizeActivity` |

---

## 三、三个必须知道的能力边界

### 1. 「一键微信视频」做不到真一键 —— 这是需求层面的硬限制

微信**没有**开放「给指定联系人发起视频通话」的接口，官方 SDK、开放平台、URL Scheme 都没有。
所有号称做到的方案只有两条路，都不推荐：

- **无障碍服务模拟点击**：要用户手动开无障碍权限，依赖微信当前版本的界面结构，微信一次改版就废；
  而且老人容易误触。→ 适老产品不要用。
- **只把微信拉起来**（本骨架采用）：`WeChatHelper.openWeChat()`。
  配合微信自带的「聊天置顶」，老人打开微信后最上面就是家人，再点两下进视频。
  实际是「一键 + 两次轻点」。

**建议如实写进需求文档。** 否则开发会以为是接口没找对，白耗时间。

### 2. 全屏来电页在 Android 14+ 需要特殊权限

`USE_FULL_SCREEN_INTENT` 从 API 34 起只默认授予「核心功能是通话或闹钟」的应用，其余要用户手动开。
`IncomingCallActivity.fullScreenIntentSettings()` 已经准备好了跳设置的 Intent，
上线前要加一段「检测未授权 → 引导开启」的提示。

### 3. 桌面组件的点击不能直接拨号

Android 10 起禁止后台启动 Activity。在 `AppWidgetProvider.onReceive` 里
`startActivity(ACTION_CALL)` 会被系统**静默丢弃** —— 现象是"点了没反应"，且没有任何报错。

所以按钮挂的是 `PendingIntent.getActivity` 指向 `CallTrampolineActivity`（透明、无 UI、办完就 finish）。
另外 `PendingIntent` 的 `requestCode` 必须按「组件 id + 按钮」编码，否则不同组件的点击会互相覆盖，
表现成"点儿子打给了女儿"。

---

## 四、和设计稿的两处刻意偏差

1. **保留系统状态栏**
   画布上有一根自绘状态栏（时间 + 信号 + 电量）。真机上这里保留了系统状态栏，只把底色染成暖白。
   原因：自绘的电量/信号一旦画错或更新延迟，会让老人误判手机状态。这类信息必须来自系统。

2. **头像用姓名首字，不用灰色占位块**
   设计稿里是灰色占位块（当时确定不生成真实素材）。真机上改成了暖底 + 姓名首字 ——
   老人认字比认色块快，也避免"一块灰色看不懂是什么"。

另外触控热区：画布定的是 88（1x 设计稿坐标）。落到真机，
主要动作按钮用 64dp（≈128px @2x），底部紧急求助用满 88dp。

---

## 五、骨架之外还要补的

- **首次引导**：目前家人位是空的，要引导用户挑三个号码。建议做成一个 3 步向导。
- **来电触发源（最高优先）**：现在是 `IncomingCallActivity.notifyIncoming()` **手动触发** ——
  真实来电不会自己弹出来。要接系统来电广播（`TelephonyManager` 的 `TelephonyCallback.CallStateListener`）
  或微信通知监听。不接这一条，"来电页"在真机上永远是死的。
- **全屏通知权限引导**：Android 14+ 检测未授权时，引导用户去 `IncomingCallActivity.fullScreenIntentSettings()`。
- **号码归一化**：从通讯录取出的号码可能带 `+86`、空格、横线，`ACTION_CALL` 前应清洗成纯数字再拼 `tel:`。
- **数据加密 / 备份策略**：号码存在 SharedPreferences 里，正式版按你的合规要求处理。
- **机型适配**：小米、华为、OPPO、vivo 对 Launcher 和自启动的限制各不相同，
  必须逐机型测「设为默认桌面」和「全屏通知」这两条路径。

---

## 六、关于这份骨架的验证状态

代码是按可编译的标准写的，但**没有在本机实际编译过** ——
这台机器上没有 Android SDK / Gradle 环境。请在 Android Studio 里首次同步时留意编译报错，
多数会集中在 Compose 版本与 Kotlin 版本的匹配上（`app/build.gradle.kts` 里已锁定一组兼容版本：
AGP 8.7.2 / Kotlin 2.0.21 / Compose BOM 2024.10.01）。
