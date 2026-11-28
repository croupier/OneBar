package dev.onebar.app.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Ряд плиток: сама плитка и вертикальные поля строки приложений. */
val AppsRowHeight = AppItemHeight + 12.dp
val AppsRowGap = 4.dp

val SectionDividerHeight = 20.dp
private const val MAX_APP_ROWS = 6
private val APP_ROWS_HEIGHT_SLACK = 12.dp
private const val WEB_ROWS_WITH_KEYBOARD = 1
private const val WEB_ROWS_WITHOUT_KEYBOARD = 3

data class PanelRows(val appRows: Int, val webRows: Int)

internal fun appsBlockHeight(rows: Int): Dp =
    AppsRowHeight * rows + AppsRowGap * (rows - 1).coerceAtLeast(0)

/**
 * Веб-поиск забирает фиксированную высоту, а оставшееся место поровну делят приложения
 * и контакты. Число рядов приложений считаем по половине этой высоты, а не по флагу IME.
 */
internal fun panelRows(
    available: Dp,
    hasApps: Boolean,
    hasSuggestions: Boolean,
): PanelRows {
    val webRows = when {
        !hasSuggestions -> 0
        available < 600.dp -> WEB_ROWS_WITH_KEYBOARD
        else -> WEB_ROWS_WITHOUT_KEYBOARD
    }
    val webHeight = if (webRows > 0) ROW_HEIGHT * webRows + SectionDividerHeight else 0.dp
    val half = (available - webHeight) / 2
    val appHalfHeight = half - APP_ROWS_HEIGHT_SLACK
    var appRows = 0
    if (hasApps) {
        appRows = 1
        while (appRows < MAX_APP_ROWS &&
            SectionDividerHeight + appsBlockHeight(appRows + 1) <= appHalfHeight
        ) {
            appRows++
        }
    }
    return PanelRows(appRows = appRows, webRows = webRows)
}
