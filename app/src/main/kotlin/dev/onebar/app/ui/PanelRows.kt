package dev.onebar.app.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Геометрия панели: ряды плиток и нужна ли внешняя прокрутка. Клавиатура снимает почти половину экрана
 * (на Pixel 8a 402 dp из 914), поэтому число рядов нельзя задать одной константой:
 * два ряда плиток даём только когда контактам остаётся не меньше пяти строк, а внешний
 * скролл оставляем лишь тогда, когда не влезает даже минимум.
 */
data class PanelRows(val appRows: Int, val scroll: Boolean)

/** Ряд плиток: сама плитка и вертикальные поля строки приложений. */
val AppsRowHeight = AppItemHeight + 12.dp

val SectionDividerHeight = 20.dp
private const val MIN_CONTACT_ROWS = 3
private const val SIDE_LIST_ROWS = 3

/** Плитки теснят контакты: второй ряд оправдан, только когда результатов видно хотя бы пять. */
private const val APP_GRID_MIN_CONTACT_ROWS = 5

internal fun panelRows(
    available: Dp,
    hasApps: Boolean,
    hasHistory: Boolean,
    hasSuggestions: Boolean,
): PanelRows {
    // Высота блоков, которые не растут: плитки, история запросов, веб-подсказки.
    fun fixed(appRows: Int): Dp {
        val apps = if (hasApps && appRows > 0) AppsRowHeight * appRows + SectionDividerHeight else 0.dp
        val history = if (hasHistory) ROW_HEIGHT * SIDE_LIST_ROWS else 0.dp
        val suggestions = if (hasSuggestions) ROW_HEIGHT * SIDE_LIST_ROWS + SectionDividerHeight else 0.dp
        return apps + history + suggestions
    }

    val minContacts = ROW_HEIGHT * MIN_CONTACT_ROWS + SectionDividerHeight
    val appRows = when {
        !hasApps -> 0
        available - fixed(2) >= ROW_HEIGHT * APP_GRID_MIN_CONTACT_ROWS -> 2
        else -> 1
    }
    val rest = available - fixed(appRows)
    val scroll = rest < minContacts
    return PanelRows(appRows, scroll)
}
