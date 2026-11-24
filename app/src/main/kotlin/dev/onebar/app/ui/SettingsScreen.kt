package dev.onebar.app.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.onebar.app.R

private enum class LanguageOption {
    SYSTEM,
    RUSSIAN,
    ENGLISH,
}

private enum class ThemeOption {
    SYSTEM,
    LIGHT,
    DARK,
}

private data class SearchOptions(
    val favoritesFirst: Boolean = true,
    val prefix: Boolean = true,
    val contains: Boolean = true,
    val phone: Boolean = true,
    val yo: Boolean = true,
    val translit: Boolean = true,
    val extraFields: Boolean = true,
)

private enum class InfoTopic(val titleRes: Int, val textRes: Int) {
    SEARCH_FAVORITES(R.string.settings_search_favorites, R.string.settings_search_favorites_info),
    SEARCH_PREFIX(R.string.settings_search_prefix, R.string.settings_search_prefix_info),
    SEARCH_CONTAINS(R.string.settings_search_contains, R.string.settings_search_contains_info),
    SEARCH_PHONE(R.string.settings_search_phone, R.string.settings_search_phone_info),
    SEARCH_YO(R.string.settings_search_yo, R.string.settings_search_yo_info),
    SEARCH_TRANSLIT(R.string.settings_search_translit, R.string.settings_search_translit_info),
    SEARCH_EXTRA(R.string.settings_search_extra, R.string.settings_search_extra_info),
    HISTORY_SAVE(R.string.settings_history_save, R.string.settings_history_save_info),
}

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    var language by rememberSaveable { mutableStateOf(LanguageOption.SYSTEM) }
    var theme by rememberSaveable { mutableStateOf(ThemeOption.SYSTEM) }
    var searchOptions by remember { mutableStateOf(SearchOptions()) }
    var savedSearchOptions by remember { mutableStateOf(SearchOptions()) }
    var saveHistory by rememberSaveable { mutableStateOf(true) }
    var savedSaveHistory by remember { mutableStateOf(true) }
    var showLanguageDialog by rememberSaveable { mutableStateOf(false) }
    var showThemeDialog by rememberSaveable { mutableStateOf(false) }
    var showClearHistoryDialog by rememberSaveable { mutableStateOf(false) }
    var showUnsavedChangesDialog by rememberSaveable { mutableStateOf(false) }
    var infoTopic by rememberSaveable { mutableStateOf<InfoTopic?>(null) }
    var feedback by rememberSaveable { mutableStateOf("") }

    val hasUnsavedChanges = searchOptions != savedSearchOptions || saveHistory != savedSaveHistory
    val requestBack = {
        if (hasUnsavedChanges) showUnsavedChangesDialog = true else onBack()
    }
    BackHandler(enabled = hasUnsavedChanges && !showUnsavedChangesDialog) {
        showUnsavedChangesDialog = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .imePadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = requestBack) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.settings_back),
                )
            }
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                SettingsCard(title = stringResource(R.string.settings_appearance)) {
                    ChoiceRow(
                        label = stringResource(R.string.settings_language),
                        value = language.label(),
                        onClick = { showLanguageDialog = true },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ChoiceRow(
                        label = stringResource(R.string.settings_theme),
                        value = theme.label(),
                        onClick = { showThemeDialog = true },
                    )
                }
            }
            item {
                SearchSettingsCard(
                    options = searchOptions,
                    onOptionsChange = { searchOptions = it },
                    onInfoClick = { infoTopic = it },
                    onDefaults = { searchOptions = SearchOptions() },
                    onSave = { savedSearchOptions = searchOptions },
                )
            }
            item {
                SettingsCard(title = stringResource(R.string.settings_history)) {
                    SettingsCheckbox(
                        label = stringResource(R.string.settings_history_save),
                        checked = saveHistory,
                        onCheckedChange = { saveHistory = it },
                        onInfoClick = { infoTopic = InfoTopic.HISTORY_SAVE },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    OutlinedButton(
                        onClick = { showClearHistoryDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.settings_history_clear))
                    }
                }
            }
            item { AboutCard() }
            item {
                FeedbackCard(
                    message = feedback,
                    onMessageChange = { feedback = it },
                    onSend = {},
                )
            }
        }
    }

    if (showLanguageDialog) {
        ChoiceDialog(
            title = stringResource(R.string.settings_language),
            options = listOf(
                stringResource(R.string.settings_language_system),
                stringResource(R.string.settings_language_russian),
                stringResource(R.string.settings_language_english),
            ),
            selected = language.ordinal,
            onSelect = {
                language = LanguageOption.values()[it]
                showLanguageDialog = false
            },
            onDismiss = { showLanguageDialog = false },
        )
    }
    if (showThemeDialog) {
        ChoiceDialog(
            title = stringResource(R.string.settings_theme),
            options = listOf(
                stringResource(R.string.settings_theme_system),
                stringResource(R.string.settings_theme_light),
                stringResource(R.string.settings_theme_dark),
            ),
            selected = theme.ordinal,
            onSelect = {
                theme = ThemeOption.values()[it]
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false },
        )
    }
    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            icon = {
                Icon(
                    painter = painterResource(R.drawable.ic_history),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            title = { Text(stringResource(R.string.settings_history_clear_title)) },
            text = { Text(stringResource(R.string.settings_history_clear_message)) },
            confirmButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text(stringResource(R.string.settings_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text(stringResource(R.string.settings_cancel))
                }
            },
        )
    }
    if (showUnsavedChangesDialog) {
        UnsavedChangesDialog(
            onSave = {
                showUnsavedChangesDialog = false
                savedSearchOptions = searchOptions
                savedSaveHistory = saveHistory
                onBack()
            },
            onDiscard = {
                showUnsavedChangesDialog = false
                onBack()
            },
            onCancel = { showUnsavedChangesDialog = false },
        )
    }
    infoTopic?.let { topic ->
        InfoDialog(
            title = stringResource(topic.titleRes),
            text = stringResource(topic.textRes),
            onDismiss = { infoTopic = null },
        )
    }
}

@Composable
private fun UnsavedChangesDialog(
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onCancel: () -> Unit,
) {
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.9f),
                shape = RoundedCornerShape(20.dp),
                tonalElevation = 6.dp,
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.settings_unsaved_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = stringResource(R.string.settings_unsaved_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(onClick = onSave) { Text(stringResource(R.string.settings_yes)) }
                        OutlinedButton(onClick = onDiscard) { Text(stringResource(R.string.settings_no)) }
                        TextButton(onClick = onCancel) { Text(stringResource(R.string.settings_cancel)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoDialog(
    title: String,
    text: String,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.32f))
                .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) },
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.86f),
                shape = RoundedCornerShape(20.dp),
                tonalElevation = 6.dp,
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_info),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CONTACT_CARD_RADIUS),
        colors = CardDefaults.cardColors(containerColor = contactCardColor()),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(CONTACT_ROW_GAP),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            content()
        }
    }
}

@Composable
private fun ChoiceRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            painter = painterResource(R.drawable.ic_submit),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ChoiceDialog(
    title: String,
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                options.forEachIndexed { index, option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = index == selected,
                                role = Role.RadioButton,
                                onClick = { onSelect(index) },
                            )
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        RadioButton(selected = index == selected, onClick = null)
                        Text(text = option, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) }
        },
    )
}

@Composable
private fun SearchSettingsCard(
    options: SearchOptions,
    onOptionsChange: (SearchOptions) -> Unit,
    onInfoClick: (InfoTopic) -> Unit,
    onDefaults: () -> Unit,
    onSave: () -> Unit,
) {
    SettingsCard(title = stringResource(R.string.settings_search)) {
        SettingsCheckbox(
            label = stringResource(R.string.settings_search_favorites),
            checked = options.favoritesFirst,
            onCheckedChange = { onOptionsChange(options.copy(favoritesFirst = it)) },
            onInfoClick = { onInfoClick(InfoTopic.SEARCH_FAVORITES) },
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        SettingsCheckbox(
            label = stringResource(R.string.settings_search_prefix),
            checked = options.prefix,
            onCheckedChange = { onOptionsChange(options.copy(prefix = it)) },
            onInfoClick = { onInfoClick(InfoTopic.SEARCH_PREFIX) },
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        SettingsCheckbox(
            label = stringResource(R.string.settings_search_contains),
            checked = options.contains,
            onCheckedChange = { onOptionsChange(options.copy(contains = it)) },
            onInfoClick = { onInfoClick(InfoTopic.SEARCH_CONTAINS) },
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        SettingsCheckbox(
            label = stringResource(R.string.settings_search_phone),
            checked = options.phone,
            onCheckedChange = { onOptionsChange(options.copy(phone = it)) },
            onInfoClick = { onInfoClick(InfoTopic.SEARCH_PHONE) },
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        SettingsCheckbox(
            label = stringResource(R.string.settings_search_yo),
            checked = options.yo,
            onCheckedChange = { onOptionsChange(options.copy(yo = it)) },
            onInfoClick = { onInfoClick(InfoTopic.SEARCH_YO) },
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        SettingsCheckbox(
            label = stringResource(R.string.settings_search_translit),
            checked = options.translit,
            onCheckedChange = { onOptionsChange(options.copy(translit = it)) },
            onInfoClick = { onInfoClick(InfoTopic.SEARCH_TRANSLIT) },
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        SettingsCheckbox(
            label = stringResource(R.string.settings_search_extra),
            summary = stringResource(R.string.settings_search_extra_summary),
            checked = options.extraFields,
            onCheckedChange = { onOptionsChange(options.copy(extraFields = it)) },
            onInfoClick = { onInfoClick(InfoTopic.SEARCH_EXTRA) },
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = onDefaults, modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_defaults),
                    fontSize = 12.sp,
                    maxLines = 1,
                    softWrap = false,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
            Button(onClick = onSave, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_save), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}

@Composable
private fun SettingsCheckbox(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    summary: String? = null,
    onInfoClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (onInfoClick != null) {
            IconButton(onClick = onInfoClick) {
                Icon(
                    painter = painterResource(R.drawable.ic_info),
                    contentDescription = stringResource(R.string.settings_info, label),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AboutCard() {
    SettingsCard(title = stringResource(R.string.settings_about)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colorResource(R.color.launcher_background)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.settings_about_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = stringResource(R.string.settings_email),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun FeedbackCard(
    message: String,
    onMessageChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    val context = LocalContext.current
    val versionName = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }
            .getOrDefault("")
    }
    val debugText = stringResource(
        R.string.settings_debug,
        versionName.ifBlank { "—" },
        Build.MANUFACTURER,
        Build.MODEL,
        Build.VERSION.RELEASE,
        Build.VERSION.SDK_INT,
    )

    SettingsCard(title = stringResource(R.string.settings_feedback)) {
        Text(
            text = stringResource(R.string.settings_feedback_subject),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.settings_feedback_notice),
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = message,
            onValueChange = onMessageChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.settings_feedback_hint)) },
            minLines = 4,
        )
        Text(
            text = stringResource(R.string.settings_feedback_debug),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(12.dp),
        ) {
            SelectionContainer {
                Text(
                    text = debugText,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Button(onClick = onSend, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_feedback_send))
        }
    }
}

@Composable
private fun LanguageOption.label(): String = stringResource(
    when (this) {
        LanguageOption.SYSTEM -> R.string.settings_language_system
        LanguageOption.RUSSIAN -> R.string.settings_language_russian
        LanguageOption.ENGLISH -> R.string.settings_language_english
    }
)

@Composable
private fun ThemeOption.label(): String = stringResource(
    when (this) {
        ThemeOption.SYSTEM -> R.string.settings_theme_system
        ThemeOption.LIGHT -> R.string.settings_theme_light
        ThemeOption.DARK -> R.string.settings_theme_dark
    }
)
