package com.hmilyld.daytrail.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * 一段要显示给用户的文本。
 *
 * ViewModel 不能持有 Context（会泄漏 Activity，也不符合分层），但又必须在状态里
 * 表达「显示哪句话」。所以状态里放资源 id + 参数，到 Compose 层再解析。
 *
 * 运行时数据（地点名、地名）走 [Raw]：它们不是资源，而是数据库里的字符串。
 *
 * 这条规则的意义不是洁癖：把文案留在 Kotlin 里，换语言就不可能，
 * 而且同一句话在两处各写一遍迟早会不一致。
 */
sealed interface UiText {
    data class Res(
        @StringRes val id: Int,
        val args: List<Any> = emptyList(),
    ) : UiText

    data class Raw(
        val value: String,
    ) : UiText
}

@Composable
fun UiText.resolve(): String =
    when (this) {
        is UiText.Res -> stringResource(id, *args.toTypedArray())
        is UiText.Raw -> value
    }

/**
 * 把多段文本拼成一句（地图的文字摘要用）。
 *
 * 必须在 Compose 层解析：`joinToString` 的 transform 不是 inline 的，
 * 在里面调 `stringResource` 编译不过。
 */
@Composable
fun List<UiText>.resolveJoined(
    @StringRes separatorRes: Int,
): String {
    val separator = stringResource(separatorRes)
    return map { it.resolve() }.joinToString(separator)
}
