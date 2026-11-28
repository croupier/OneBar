package dev.onebar.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.onebar.app.R
import kotlinx.coroutines.delay

private const val AUTO_CLOSE_SECONDS = 15

/** Тег для тестов: крестик диалога и крестик поля поиска имеют одинаковое описание. */
const val TAG_FAVORITES_HINT_CLOSE = "favorites-hint-close"

/**
 * Попап при входе в пустое избранное: объясняет жест добавления и закрывается сам.
 * Отсчёт тикает секундомером в корутине — закрытие крестиком отменяет её вместе с диалогом.
 */
@Composable
fun FavoritesHintDialog(onDismiss: () -> Unit) {
    var seconds by remember { mutableIntStateOf(AUTO_CLOSE_SECONDS) }
    LaunchedEffect(Unit) {
        while (seconds > 0) {
            delay(1000)
            seconds -= 1
        }
        onDismiss()
    }
    val progress by animateFloatAsState(
        targetValue = seconds / AUTO_CLOSE_SECONDS.toFloat(),
        animationSpec = tween(900),
        label = "favoritesHintCountdown",
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                painter = painterResource(R.drawable.ic_star),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.fav_empty_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp).testTag(TAG_FAVORITES_HINT_CLOSE)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.close),
                    )
                }
            }
        },
        text = { Text(stringResource(R.string.fav_empty_text)) },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = stringResource(R.string.fav_auto_close, seconds),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                )
            }
        },
    )
}
