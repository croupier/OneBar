package dev.onebar.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import dev.onebar.app.R
import dev.onebar.app.data.ContactEntry
import dev.onebar.app.data.MessengerTile

private const val MENU_ICON_PX = 96
private val MENU_TILE = 36.dp

/**
 * Меню долгого нажатия на имя контакта. Пункты без системных разрешений: звонок и СМС уходят
 * «Телефону» и сообщениям, номер и карточка контакта — тоже стандартными интентами.
 */
@Composable
fun ContactMenu(
    entry: ContactEntry,
    inJournal: Boolean,
    messengers: List<MessengerTile>,
    onDismiss: () -> Unit,
    onCall: () -> Unit,
    onSms: () -> Unit,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMessenger: (MessengerTile) -> Unit,
    onEmail: () -> Unit,
) {
    val hasNumber = entry.number != null
    AlertDialog(
        onDismissRequest = onDismiss,
        // Стандартная ширина AlertDialog переносит подписи пунктов на две строки.
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        confirmButton = {},
        title = { Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                // Пункт 0: показываем номер, по которому сработают «Позвонить» и «СМС».
                Text(
                    text = when {
                        !hasNumber -> "Телефона нет"
                        entry.phoneCount > 1 -> "${entry.number} · ещё ${entry.phoneCount - 1}"
                        else -> entry.number!!
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                MenuRow(R.drawable.ic_call, "Позвонить", enabled = hasNumber, onClick = onCall)
                MenuRow(R.drawable.ic_sms, "Отправить СМС", enabled = hasNumber, onClick = onSms)
                MenuRow(R.drawable.ic_copy, "Скопировать номер", enabled = hasNumber, onClick = onCopy)
                MenuRow(R.drawable.ic_edit, "Изменить контакт", onClick = onEdit)
                MenuRow(R.drawable.ic_delete, "Удалить из истории", enabled = inJournal, onClick = onDelete)

                // Ряд приложений: мессенджеры и почта, если у контакта есть адрес.
                val email = entry.email
                if (messengers.isNotEmpty() || email != null) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        messengers.forEach { tile ->
                            MessengerIcon(tile) { onMessenger(tile) }
                        }
                        if (email != null) {
                            MailIcon(email) { onEmail() }
                        }
                    }
                }
            }
        },
    )
}

/**
 * Публичного API «темированных» системных глифов нет, поэтому свои векторы кладём
 * на квадрат со скруглёнными углами — форма та же, что у значков приложений рядом.
 */
@Composable
private fun MenuRow(iconRes: Int, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val dimmed = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    val tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else dimmed
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 7.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(MENU_TILE)
                .background(
                    if (enabled) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    RoundedCornerShape(10.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = tint,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else dimmed,
        )
    }
}

@Composable
private fun MessengerIcon(tile: MessengerTile, onClick: () -> Unit) {
    val packageManager = LocalContext.current.packageManager
    val bitmap = remember(tile.messenger) {
        runCatching {
            packageManager.getApplicationIcon(tile.messenger.packageName)
                .toBitmap(MENU_ICON_PX, MENU_ICON_PX)
                .asImageBitmap()
        }.getOrNull()
    }
    AppTile(onClick = onClick) {
        if (bitmap == null) {
            TileBackground()
        } else {
            Image(bitmap = bitmap, contentDescription = tile.label, modifier = Modifier.size(MENU_TILE))
        }
    }
}

@Composable
private fun MailIcon(email: String, onClick: () -> Unit) {
    AppTile(onClick = onClick) {
        TileBackground()
        Icon(
            painter = painterResource(R.drawable.ic_mail),
            contentDescription = "Почта: $email",
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AppTile(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(MENU_TILE)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun TileBackground() {
    Box(
        Modifier
            .size(MENU_TILE)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)),
    )
}
