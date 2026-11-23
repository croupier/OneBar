package dev.onebar.app.ui

import android.graphics.drawable.Drawable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.onebar.app.R
import dev.onebar.app.data.CommunicationKind
import dev.onebar.app.data.ContactEntry

@Composable
fun ContactsSection(
    state: SearchState,
    smsIcon: Drawable?,
    callIcon: Drawable?,
    onRequestPermission: () -> Unit,
    onSms: (ContactEntry) -> Unit,
    onCall: (ContactEntry) -> Unit,
    onOpen: (ContactEntry) -> Unit,
    onMenu: (ContactEntry) -> Unit,
) {
    if (!state.contactsGranted) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.contacts_denied), modifier = Modifier.weight(1f))
            Button(onClick = onRequestPermission) { Text(stringResource(R.string.allow)) }
        }
        return
    }
    if (state.contacts.isEmpty()) return

    LazyColumn(modifier = Modifier.fillMaxWidth().height(ROW_HEIGHT * 3)) {
        items(state.contacts, key = { it.entry.contactId }) { row ->
            ContactRow(
                row = row,
                smsIcon = smsIcon,
                callIcon = callIcon,
                favorite = !state.favoritesMode && row.entry.lookupKey in state.favContactKeys,
                onSms = { onSms(row.entry) },
                onCall = { onCall(row.entry) },
                onOpen = { onOpen(row.entry) },
                onMenu = { onMenu(row.entry) },
            )
        }
    }
}

@Composable
private fun ContactRow(
    row: ContactRowUi,
    smsIcon: Drawable?,
    callIcon: Drawable?,
    favorite: Boolean,
    onSms: () -> Unit,
    onCall: () -> Unit,
    onOpen: () -> Unit,
    onMenu: () -> Unit,
) {
    val canAct = row.entry.number != null
    // Подпись строки журнала собирается здесь, а не во ViewModel: названия действий локализуемы.
    val detail = when {
        row.logKind != null -> {
            val kind = stringResource(
                if (row.logKind == CommunicationKind.CALL) R.string.caption_call else R.string.caption_sms
            )
            "$kind · ${row.logTime} · ${row.entry.number}"
        }
        else -> row.entry.number.orEmpty()
    }
    SwipeableRow(enabled = canAct, onTrigger = onCall) { labels ->
        Box(Modifier.fillMaxWidth().height(ROW_HEIGHT)) {
            Row(
                modifier = Modifier.fillMaxWidth().height(ROW_HEIGHT).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .combinedClickable(onClick = onOpen, onLongClick = onMenu),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = row.entry.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (detail.isNotBlank()) {
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                if (canAct) {
                    ActionIcon(
                        handlerIcon = smsIcon,
                        fallbackIcon = painterResource(R.drawable.ic_sms),
                        contentDescription = stringResource(R.string.sms_action),
                        onClick = onSms,
                        caption = if (labels) stringResource(R.string.caption_sms) else null,
                    )
                    ActionIcon(
                        handlerIcon = callIcon,
                        fallbackIcon = painterResource(R.drawable.ic_call),
                        contentDescription = stringResource(R.string.call_action),
                        onClick = onCall,
                        caption = if (labels) stringResource(R.string.caption_call) else null,
                    )
                } else {
                    Spacer(Modifier.size(AppItemSize * 2 + 10.dp))
                }
            }
            // Звезда — наложение в углу строки: как у иконок приложений, она не должна сдвигать текст.
            if (favorite) {
                Icon(
                    painter = painterResource(R.drawable.ic_star),
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.TopStart).padding(start = 2.dp, top = 2.dp).size(14.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * Свайп справа налево по строке: до половины порога — показать подписи под кнопками,
 * за порогом (50 % ширины) — выполнить правое действие сразу.
 */
@Composable
private fun SwipeableRow(
    enabled: Boolean,
    onTrigger: () -> Unit,
    content: @Composable (labels: Boolean) -> Unit,
) {
    var width by remember { mutableIntStateOf(0) }
    var labels by remember { mutableStateOf(false) }
    var drag by remember { mutableFloatStateOf(0f) }
    val shift by animateFloatAsState(drag, animationSpec = tween(120), label = "rowSwipe")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { width = it.width }
            .graphicsLayer { translationX = shift }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                val minDrag = MIN_DRAG_DP.dp.toPx()
                detectHorizontalDragGestures(
                    onHorizontalDrag = { _, amount -> drag = (drag + amount).coerceIn(-width * TRIGGER, 0f) },
                    onDragEnd = {
                        val pulled = -drag
                        drag = 0f
                        when {
                            width > 0 && pulled >= width * TRIGGER -> onTrigger()
                            pulled >= minDrag -> labels = true
                        }
                    },
                )
            },
    ) {
        content(labels)
    }
}

private const val MIN_DRAG_DP = 30
private const val TRIGGER = 0.5f

@Composable
fun SearchHistorySection(
    queries: List<String>,
    onPick: (String) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxWidth().height(ROW_HEIGHT * 3)) {
        items(queries, key = { it }) { query ->
            HistoryRow(text = query, onPick = { onPick(query) })
        }
    }
}

@Composable
private fun HistoryRow(
    text: String,
    onPick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(ROW_HEIGHT),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_history),
            contentDescription = null,
            modifier = Modifier.padding(start = 14.dp).size(22.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = text,
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onPick)
                .padding(vertical = 8.dp),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            painter = painterResource(R.drawable.ic_submit),
            contentDescription = stringResource(R.string.history_restore),
            modifier = Modifier
                .padding(end = 14.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onPick)
                .padding(6.dp)
                .size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun WebSuggestionsSection(
    state: SearchState,
    onPick: (String) -> Unit,
    onSearch: (WebEngine, String) -> Unit,
) {
    if (state.suggestions.isEmpty()) return

    LazyColumn(modifier = Modifier.fillMaxWidth().height(ROW_HEIGHT * 3)) {
        items(state.suggestions, key = { it }) { suggestion ->
            WebRow(
                text = suggestion,
                onPick = { onPick(suggestion) },
                onSearch = { engine -> onSearch(engine, suggestion) },
            )
        }
    }
}

@Composable
private fun WebRow(
    text: String,
    onPick: () -> Unit,
    onSearch: (WebEngine) -> Unit,
) {
    SwipeableRow(enabled = true, onTrigger = { onSearch(WebEngine.GOOGLE) }) { labels ->
        Row(
            modifier = Modifier.fillMaxWidth().height(ROW_HEIGHT).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = text,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onPick)
                    .padding(vertical = 8.dp),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            BrandBadge(
                letter = "Я",
                badgeBackground = Color(0xFFFC3F1D),
                badgeContentColor = Color.White,
                label = stringResource(R.string.search_yandex),
                onClick = { onSearch(WebEngine.YANDEX) },
                caption = if (labels) "Яндекс" else null,
            )
            BrandBadge(
                letter = "G",
                badgeBackground = Color(0xFFF1F3F4),
                badgeContentColor = Color(0xFF4285F4),
                label = stringResource(R.string.search_google),
                onClick = { onSearch(WebEngine.GOOGLE) },
                caption = if (labels) "Google" else null,
            )
        }
    }
}
