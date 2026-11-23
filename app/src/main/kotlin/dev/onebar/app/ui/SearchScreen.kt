package dev.onebar.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.onebar.app.R
import dev.onebar.app.data.AppRepository
import dev.onebar.app.data.CommunicationKind
import dev.onebar.app.data.ContactRepository
import dev.onebar.app.data.FavoritesStore
import dev.onebar.app.data.HistoryStore
import dev.onebar.app.data.MessengerRepository
import dev.onebar.app.data.SuggestRepository

@Composable
fun SearchScreen(
    onClose: () -> Unit,
    context: Context = LocalContext.current,
    viewModel: SearchViewModel = viewModel {
        SearchViewModel(
            apps = AppRepository(context.packageManager),
            contacts = ContactRepository(context),
            suggestions = SuggestRepository(),
            history = HistoryStore(context),
            favoritesStore = FavoritesStore(context),
        )
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val menuEntry by viewModel.menuEntry.collectAsStateWithLifecycle()
    val menuApp by viewModel.menuApp.collectAsStateWithLifecycle()
    val messengerRepository = remember { MessengerRepository(context) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    val smsIcon = rememberHandlerIcon(Intent.ACTION_SENDTO, "smsto:")
    val callIcon = rememberHandlerIcon(Intent.ACTION_DIAL, "tel:")

    val contactsPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> viewModel.onContactsPermissionResult(granted) }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    // В избранное входим без клавиатуры: тап по звезде не должен оставлять открытый IME.
    LaunchedEffect(state.favoritesMode) {
        if (state.favoritesMode) {
            focusManager.clearFocus()
            keyboard?.hide()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .imePadding(),
    ) {
        SearchBar(
            query = state.query,
            favoritesMode = state.favoritesMode,
            favoritesEmpty = state.favApps.isEmpty() && state.favContactKeys.isEmpty(),
            focusRequester = focusRequester,
            onQueryChange = viewModel::onQueryChange,
            onToggleFavorites = viewModel::onToggleFavoritesMode,
            onFieldTap = {
                focusRequester.requestFocus()
                keyboard?.show()
                viewModel.onHistoryRequested()
            },
            onImeSearch = { viewModel.onQuerySubmitted(state.query) },
            onClearOrClose = {
                if (state.query.isEmpty()) onClose() else viewModel.onQueryChange("")
            },
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            if (state.apps.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(state.apps, key = { it.packageName }) { app ->
                        AppItem(
                            app = app,
                            favorite = !state.favoritesMode && app.packageName in state.favApps,
                            onClick = {
                                viewModel.onAppLaunched(app)
                                SearchActions.openApp(context, app.packageName)
                            },
                            onLongClick = { viewModel.onAppMenuOpen(app) },
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            val showHistory =
                state.historyRequested && state.query.isBlank() && state.queries.isNotEmpty() &&
                    !state.favoritesMode
            if (showHistory) {
                SearchHistorySection(queries = state.queries, onPick = viewModel::onQueryChange)
            }

            val favoritesBlank =
                state.favoritesMode && state.favApps.isEmpty() && state.favContactKeys.isEmpty()
            if (favoritesBlank) {
                FavoritesEmptyHint()
            } else {
                ContactsSection(
                    state = state,
                    smsIcon = smsIcon,
                    callIcon = callIcon,
                    onRequestPermission = { contactsPermission.launch(Manifest.permission.READ_CONTACTS) },
                    onSms = { entry ->
                        viewModel.onCommunication(CommunicationKind.SMS, entry)
                        SearchActions.smsEntry(context, entry)
                    },
                    onCall = { entry ->
                        viewModel.onCommunication(CommunicationKind.CALL, entry)
                        SearchActions.callEntry(context, entry)
                    },
                    onOpen = { entry ->
                        viewModel.onQuerySubmitted(state.query)
                        SearchActions.openContact(context, entry)
                    },
                    onMenu = viewModel::onMenuOpen,
                )

                val noLocalResults = state.query.isNotBlank() &&
                    state.apps.isEmpty() && state.contacts.isEmpty()
                if (noLocalResults) {
                    NothingFoundRow(inFavorites = state.favoritesMode)
                }

                if (state.suggestions.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                WebSuggestionsSection(
                    state = state,
                    onPick = viewModel::onQueryChange,
                    onSearch = { engine, text ->
                        viewModel.onQuerySubmitted(text)
                        SearchActions.webSearch(context, engine, text)
                    },
                )
            }
        }
    }

    val menuTarget = menuEntry
    if (menuTarget != null) {
        // Плитки пересчитываем только при смене контакта: это обращения к PackageManager.
        val tiles = remember(menuTarget) { messengerRepository.tilesFor(menuTarget) }
        ContactMenu(
            entry = menuTarget,
            inJournal = viewModel.inJournal(menuTarget.contactId),
            isFavorite = menuTarget.lookupKey in state.favContactKeys,
            favoritesMode = state.favoritesMode,
            messengers = tiles,
            onDismiss = viewModel::onMenuClose,
            onToggleFavorite = { viewModel.onToggleFavoriteContact(menuTarget) },
            onCall = {
                viewModel.onMenuClose()
                viewModel.onCommunication(CommunicationKind.CALL, menuTarget)
                SearchActions.callEntry(context, menuTarget)
            },
            onSms = {
                viewModel.onMenuClose()
                viewModel.onCommunication(CommunicationKind.SMS, menuTarget)
                SearchActions.smsEntry(context, menuTarget)
            },
            onCopy = {
                viewModel.onMenuClose()
                menuTarget.number?.let { SearchActions.copyNumber(context, it) }
            },
            onEdit = {
                viewModel.onMenuClose()
                SearchActions.editContact(context, menuTarget)
            },
            onDelete = {
                viewModel.onMenuClose()
                viewModel.onRemoveFromJournal(menuTarget.contactId)
            },
            onMessenger = { tile ->
                viewModel.onMenuClose()
                SearchActions.openMessenger(context, tile, menuTarget.number)
            },
            onEmail = {
                viewModel.onMenuClose()
                menuTarget.email?.let { SearchActions.openEmail(context, it) }
            },
        )
    }

    val appTarget = menuApp
    if (appTarget != null) {
        AppMenu(
            app = appTarget,
            isFavorite = appTarget.packageName in state.favApps,
            favoritesMode = state.favoritesMode,
            onDismiss = viewModel::onAppMenuClose,
            onOpen = {
                viewModel.onAppMenuClose()
                viewModel.onAppLaunched(appTarget)
                SearchActions.openApp(context, appTarget.packageName)
            },
            onInfo = {
                viewModel.onAppMenuClose()
                SearchActions.appInfo(context, appTarget.packageName)
            },
            onToggleFavorite = { viewModel.onToggleFavoriteApp(appTarget) },
        )
    }

    if (state.favoritesHint) {
        FavoritesHintDialog(onDismiss = viewModel::onFavoritesHintDismissed)
    }
}

@Composable
private fun SearchBar(
    query: String,
    favoritesMode: Boolean,
    favoritesEmpty: Boolean,
    focusRequester: FocusRequester,
    onQueryChange: (String) -> Unit,
    onToggleFavorites: () -> Unit,
    onFieldTap: () -> Unit,
    onImeSearch: () -> Unit,
    onClearOrClose: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Звезда гаснет, когда в избранном пусто: вход всё равно открыт — там подсказка.
        IconButton(
            onClick = onToggleFavorites,
            modifier = Modifier.alpha(if (favoritesEmpty && !favoritesMode) 0.4f else 1f),
        ) {
            Icon(
                painter = painterResource(if (favoritesMode) R.drawable.ic_star else R.drawable.ic_star_border),
                contentDescription = stringResource(R.string.favorites),
                tint = if (favoritesMode) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineSmall.copy(color = MaterialTheme.colorScheme.onBackground),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onImeSearch() }),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(R.string.search_hint),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                        )
                    }
                    inner()
                },
            )
            // Пустое поле перекрываем прозрачным слоем: BasicTextField сам поглощает тап ради
            // установки курсора, и без слоя историю было бы не вызвать.
            if (query.isEmpty()) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .pointerInput(Unit) { detectTapGestures { onFieldTap() } },
                )
            }
        }
        IconButton(onClick = onClearOrClose) {
            Icon(painter = painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.close))
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/** Пустое избранное: тот же каркас, вместо списков — подсказка про долгое нажатие. */
@Composable
private fun FavoritesEmptyHint() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 34.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_star),
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.fav_empty_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.fav_empty_text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun NothingFoundRow(inFavorites: Boolean) {
    Text(
        text = stringResource(if (inFavorites) R.string.fav_nothing_found else R.string.nothing_found),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 16.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
