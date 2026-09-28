package com.careos.launcher.data

/**
 * 桌面上的一个家人位。
 *
 * 刻意不给默认号码：宁可第一次是空的、引导用户从通讯录里挑，
 * 也不要塞一个假号码 —— 万一有人直接点了拨打，就是打给陌生人。
 */
data class FamilyContact(
    val index: Int,
    /** 显示名，默认就是关系（儿子 / 女儿 / 老伴） */
    val name: String,
    val number: String,
) {
    val hasNumber: Boolean get() = number.isNotBlank()
}
