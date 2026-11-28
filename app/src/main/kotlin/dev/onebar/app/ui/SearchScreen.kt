package dev.onebar.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.onebar.app.R
import dev.onebar.app.OneBarApplication
import dev.onebar.app.data.CommunicationKind
import dev.onebar.app.data.ContactEntry
import dev.onebar.app.data.DefaultAppRepository
import dev.onebar.app.data.DefaultContactRepository
import dev.onebar.app.data.DefaultFavoritesStore
import dev.onebar.app.data.DefaultHistoryStore
import dev.onebar.app.data.DefaultSuggestRepository
import dev.onebar.app.data.KeyboardStartup
import dev.onebar.app.data.LaunchableApp
import dev.onebar.app.data.MessengerRepository
import dev.onebar.app.data.SearchPreferencesStore
import kotlinx.coroutines.launch

private const val BACK_CONFIRM_WINDOW_MS = 2_000L

@Composable
fun SearchScreen(
    context: Context = LocalContext.current,
    viewModel: SearchViewModel = viewModel {
        SearchViewModel(
            apps = DefaultAppRepository(context.applicationContext.packageManager),
            contacts = DefaultContactRepository(context.applicationContext),
            suggestions = DefaultSuggestRepository(),
            history = DefaultHistoryStore(context.applicationContext),
            favoritesStore = DefaultFavoritesStore(context.applicationContext),
            searchOptions = (
                (context.applicationContext as? OneBarApplication)?.searchPreferencesStore
                    ?: SearchPreferencesStore(context.applicationContext)
                ).options,
        )
    },
    keyboardStartup: KeyboardStartup = KeyboardStartup.IMMEDIATE,
    settingsReturnCount: Int = 0,
    onOpenSettings: () -> Unit = {},
    onExit: () -> Unit = {},
    onContactsPermissionGranted: () -> Unit = {},
    onWebSearch: (WebEngine, String) -> Unit = { engine, text ->
        SearchActions.webSearch(context, engine, text)
    },
    onCallEntry: (ContactEntry) -> Unit = { SearchActions.callEntry(context, it) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val menuEntry by viewModel.menuEntry.collectAsStateWithLifecycle()
    val menuApp by viewModel.menuApp.collectAsStateWithLifecycle()
    val messengerRepository = remember(context.applicationContext) { MessengerRepository(context.applicationContext) }
    val focusRequester = remember { FocusRequester() }
    val swipeRows = remember { SwipeRowController() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var fieldValue by remember { mutableStateOf(TextFieldValue(state.query)) }
    var wasFavoritesMode by remember { mutableStateOf(state.favoritesMode) }
    var lastBackPressAt by rememberSaveable { mutableStateOf(0L) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val doubleBackMessage = stringResource(R.string.double_back_exit)

    BackHandler(enabled = menuEntry == null && menuApp == null && !state.favoritesHint) {
        if (state.favoritesMode) {
            lastBackPressAt = 0L
            viewModel.onToggleFavoritesMode()
        } else {
            val now = System.currentTimeMillis()
            if (lastBackPressAt != 0L && now - lastBackPressAt <= BACK_CONFIRM_WINDOW_MS) {
                onExit()
            } else {
                lastBackPressAt = now
                scope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(message = doubleBackMessage)
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.onScreenResumed()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state.query) {
        if (fieldValue.text != state.query) {
            fieldValue = TextFieldValue(state.query, selection = TextRange(state.query.length))
        }
    }

    val smsIcon = rememberHandlerIcon(Intent.ACTION_SENDTO, "smsto:")
    val callIcon = rememberHandlerIcon(Intent.ACTION_DIAL, "tel:")

    val contactsPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onContactsPermissionResult(granted)
        if (granted) onContactsPermissionGranted()
    }

    LaunchedEffect(Unit) {
        if (keyboardStartup == KeyboardStartup.IMMEDIATE) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }

    LaunchedEffect(settingsReturnCount) {
        if (settingsReturnCount > 0 && keyboardStartup == KeyboardStartup.IMMEDIATE && !state.favoritesMode) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }

    // В избранное входим без клавиатуры: тап по звезде не должен оставлять открытый IME.
    LaunchedEffect(state.favoritesMode) {
        if (state.favoritesMode) {
            focusManager.clearFocus()
            keyboard?.hide()
        } else if (wasFavoritesMode && keyboardStartup == KeyboardStartup.IMMEDIATE) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
        wasFavoritesMode = state.favoritesMode
    }

    val openApp: (LaunchableApp) -> Unit = { app ->
        viewModel.onAppLaunched(app)
        SearchActions.openApp(context, app.packageName)
    }

    val favoritesBlank =
        state.favoritesMode && state.favApps.isEmpty() && state.favContactKeys.isEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        SearchBar(
            value = fieldValue,
            favoritesMode = state.favoritesMode,
            favoritesEmpty = state.favApps.isEmpty() && state.favContactKeys.isEmpty(),
            focusRequester = focusRequester,
            onOpenSettings = onOpenSettings,
            onValueChange = { input ->
                val textChanged = input.text != fieldValue.text
                fieldValue = input
                if (textChanged) viewModel.onQueryChange(input.text)
            },
            onToggleFavorites = viewModel::onToggleFavoritesMode,
            onFieldFocus = { keyboard?.show() },
            onFieldTap = {
                focusRequester.requestFocus()
                keyboard?.show()
                viewModel.onHistoryRequested()
            },
            onImeSearch = {
                viewModel.onQuerySubmitted(state.query)
                focusManager.clearFocus()
                keyboard?.hide()
            },
            onClear = {
                fieldValue = TextFieldValue("")
                if (state.query.isNotEmpty()) viewModel.onQueryChange("")
                focusRequester.requestFocus()
                keyboard?.show()
            },
        )

        BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
            val hasApps = state.apps.isNotEmpty()
            val hasSuggestions = state.suggestions.isNotEmpty()
            val hasContacts = !favoritesBlank && (!state.contactsGranted || state.contacts.isNotEmpty())
            val rows = panelRows(
                available = maxHeight,
                hasApps = hasApps,
                hasSuggestions = hasSuggestions,
            )
            val webHeight = if (hasSuggestions) {
                SectionDividerHeight + ROW_HEIGHT * rows.webRows
            } else {
                0.dp
            }
            val localHeight = maxHeight - webHeight
            val singleAppsHeight = SectionDividerHeight + AppsRowHeight
            val targetAppsHeight = when {
                !hasContacts -> localHeight
                rows.appRows == 1 -> minOf(singleAppsHeight, localHeight)
                else -> localHeight / 2
            }
            val targetContactsHeight = when {
                !hasApps -> localHeight
                rows.appRows == 1 -> (localHeight - minOf(singleAppsHeight, localHeight)).coerceAtLeast(0.dp)
                else -> localHeight / 2
            }
            val animatedWebHeight by animateDpAsState(webHeight, tween(220), label = "webHeight")
            val animatedLocalHeight by animateDpAsState(localHeight, tween(220), label = "localHeight")
            val animatedAppsHeight by animateDpAsState(targetAppsHeight, tween(220), label = "appsHeight")
            val animatedContactsHeight by animateDpAsState(targetContactsHeight, tween(220), label = "contactsHeight")
            Column(modifier = Modifier.fillMaxSize()) {
                if (hasApps) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(animatedAppsHeight)
                            .testTag("apps-section"),
                    ) {
                        SectionDivider(stringResource(R.string.apps_title))
                        Crossfade(
                            targetState = rows.appRows > 1,
                            animationSpec = tween(180),
                            label = "appsLayout",
                            modifier = Modifier.weight(1f),
                        ) { multipleRows ->
                            if (multipleRows) {
                                AppsGridSection(
                                    apps = state.apps,
                                    rows = rows.appRows,
                                    favoritePackages = state.favApps.toSet(),
                                    onOpen = openApp,
                                    onMenu = viewModel::onAppMenuOpen,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                AppAutoStrip(
                                    apps = state.apps,
                                    favoritePackages = if (state.favoritesMode) emptySet()
                                    else state.favApps.toSet(),
                                    onOpen = openApp,
                                    onMenu = viewModel::onAppMenuOpen,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                )
                            }
                        }
                    }
                }

                if (favoritesBlank) {
                    FavoritesEmptyHint(modifier = Modifier.height(animatedLocalHeight))
                } else {
                    if (hasContacts) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(animatedContactsHeight)
                                .testTag("contacts-section"),
                        ) {
                            SectionDivider(stringResource(R.string.contacts_title))
                            ContactsSection(
                                state = state,
                                modifier = Modifier.fillMaxSize(),
                                swipeRows = swipeRows,
                                smsIcon = smsIcon,
                                callIcon = callIcon,
                                onRequestPermission = { contactsPermission.launch(Manifest.permission.READ_CONTACTS) },
                                onSms = { entry ->
                                    viewModel.onCommunication(CommunicationKind.SMS, entry)
                                    SearchActions.smsEntry(context, entry)
                                },
                                onCall = { entry ->
                                    viewModel.onCommunication(CommunicationKind.CALL, entry)
                                    onCallEntry(entry)
                                },
                                onOpen = { entry ->
                                    SearchActions.openContact(context, entry)
                                },
                                onMenu = viewModel::onMenuOpen,
                            )
                        }
                    }

                    val noLocalResults = state.query.isNotBlank() &&
                        state.apps.isEmpty() && state.contacts.isEmpty()
                    if (noLocalResults) {
                        if (hasContacts) {
                            NothingFoundRow(inFavorites = state.favoritesMode)
                        } else {
                            Box(
                                modifier = Modifier.height(animatedLocalHeight),
                                contentAlignment = Alignment.Center,
                            ) {
                                NothingFoundRow(inFavorites = state.favoritesMode)
                            }
                        }
                    }
                }

                if (hasSuggestions) {
                    Column(modifier = Modifier.height(animatedWebHeight).testTag("web-section")) {
                        SectionDivider(stringResource(R.string.web_search_title))
                        WebSuggestionsSection(
                            state = state,
                            rows = rows.webRows,
                            swipeRows = swipeRows,
                            onPick = { text ->
                                fieldValue = TextFieldValue(text, selection = TextRange(text.length))
                                viewModel.onQueryChange(text)
                                focusRequester.requestFocus()
                                keyboard?.show()
                            },
                            onSearch = { engine, text ->
                                viewModel.onQuerySubmitted(text)
                                onWebSearch(engine, text)
                            },
                        )
                    }
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }

    val menuTarget = menuEntry
    if (menuTarget != null) {
        // Плитки пересчитываем только при смене контакта: это обращения к PackageManager.
        val tiles = remember(menuTarget) { messengerRepository.tilesFor(menuTarget) }
        ContactMenu(
            entry = menuTarget,
            inJournal = viewModel.inJournal(menuTarget.contactId),
            isFavorite = menuTarget.lookupKey in state.favContactKeys,
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
    value: TextFieldValue,
    favoritesMode: Boolean,
    favoritesEmpty: Boolean,
    focusRequester: FocusRequester,
    onOpenSettings: () -> Unit,
    onValueChange: (TextFieldValue) -> Unit,
    onToggleFavorites: () -> Unit,
    onFieldFocus: () -> Unit,
    onFieldTap: () -> Unit,
    onImeSearch: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onOpenSettings) {
            Icon(
                painter = painterResource(R.drawable.ic_settings),
                contentDescription = stringResource(R.string.settings_title),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = if (value.text.isNotBlank()) 48.dp else 0.dp)
                    .focusRequester(focusRequester)
                    .onFocusChanged { if (it.isFocused) onFieldFocus() },
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineSmall.copy(color = MaterialTheme.colorScheme.onBackground),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onImeSearch() }),
                decorationBox = { inner ->
                    if (value.text.isEmpty()) {
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
            if (value.text.isEmpty()) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable(onClick = onFieldTap)
                        .testTag("history-trigger"),
                )
            }
            if (value.text.isNotBlank()) {
                IconButton(
                    onClick = onClear,
                    modifier = Modifier.align(Alignment.CenterEnd),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.clear),
                    )
                }
            }
        }
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
    }
}

@Composable
internal fun SectionDivider(title: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = SectionDividerHeight),
        contentAlignment = Alignment.CenterStart,
    ) {
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.fillMaxWidth().align(Alignment.Center),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(start = 14.dp, end = 12.dp),
        )
    }
}

/** Пустое избранное: тот же каркас, вместо списков — подсказка про долгое нажатие. */
@Composable
private fun FavoritesEmptyHint(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 34.dp, vertical = 48.dp),
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
