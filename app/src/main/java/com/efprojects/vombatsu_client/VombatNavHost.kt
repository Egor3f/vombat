package com.efprojects.vombatsu_client

import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.paging.compose.collectAsLazyPagingItems
import com.efprojects.vombatsu_client.about.AboutScreen
import com.efprojects.vombatsu_client.actions.ActionContext
import com.efprojects.vombatsu_client.actions.LocalActions
import com.efprojects.vombatsu_client.auth.FeedFilters
import com.efprojects.vombatsu_client.auth.LoginScreen
import com.efprojects.vombatsu_client.auth.LoginViewModel
import com.efprojects.vombatsu_client.auth.Session
import com.efprojects.vombatsu_client.auth.SessionViewModel
import com.efprojects.vombatsu_client.content.LocalVideoJobs
import com.efprojects.vombatsu_client.editor.EditorScreen
import com.efprojects.vombatsu_client.editor.EditorViewModel
import com.efprojects.vombatsu_client.feed.FeedFilterMenu
import com.efprojects.vombatsu_client.feed.FeedScreen
import com.efprojects.vombatsu_client.feed.FeedViewModel
import com.efprojects.vombatsu_client.ignore.IgnoreListScreen
import com.efprojects.vombatsu_client.ignore.IgnoreListViewModel
import com.efprojects.vombatsu_client.navigation.InternalLink
import com.efprojects.vombatsu_client.navigation.parseInternalLink
import com.efprojects.vombatsu_client.notifications.NotificationsScreen
import com.efprojects.vombatsu_client.notifications.NotificationsViewModel
import com.efprojects.vombatsu_client.post.PostScreen
import com.efprojects.vombatsu_client.post.PostTarget
import com.efprojects.vombatsu_client.post.PostViewModel
import com.efprojects.vombatsu_client.profile.ProfileScreen
import com.efprojects.vombatsu_client.profile.ProfileViewModel
import com.efprojects.vombatsu_client.search.SearchScreen
import com.efprojects.vombatsu_client.search.SearchViewModel
import com.efprojects.vombatsu_client.settings.SettingsScreen
import com.efprojects.vombatsu_client.tag.TagScreen
import com.efprojects.vombatsu_client.tag.TagViewModel
import com.efprojects.vombatsu_client.web.WebPageScreen
import kotlinx.coroutines.delay

private const val ROUTE_FEED = "feed"
private const val ROUTE_LOGIN = "login"
private const val ROUTE_SEARCH = "search"
private const val ROUTE_NOTIFICATIONS = "notifications"
private const val ROUTE_IGNORE = "ignore"
private const val ROUTE_EDITOR = "editor"
private const val ROUTE_PROFILE_SETTINGS = "profile-settings"
private const val ROUTE_BATTLE = "battle"
private const val ROUTE_POSTPONED = "postponed"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_ABOUT = "about"
private const val ARG_ID = "id"
private const val ARG_NAME = "name"
private const val ARG_COMMENT = "comment"
private const val NO_COMMENT = -1
private const val COMMENTS_SECTION = 0
private const val ROUTE_POST = "post/{$ARG_ID}?$ARG_COMMENT={$ARG_COMMENT}"
private const val SETTINGS_URL = "https://vombat.su/settings"
private const val BATTLE_URL = "https://vombat.su/battle"
private const val POSTPONED_URL = "https://vombat.su/postponed"
private const val ROUTE_USER = "user/{$ARG_NAME}"
private const val ROUTE_TAG = "tag/{$ARG_NAME}"
private const val NOTIFICATIONS_POLL_MS = 60_000L
private const val TRANSITION_MS = 300
private val FEED_TOP_BAR_HEIGHT = 48.dp
private const val BACKDROP_SHIFT_DIVISOR = 4

private fun backdropReturn() = slideInHorizontally(tween(TRANSITION_MS)) { -it / BACKDROP_SHIFT_DIVISOR }
private fun screenDismiss() = slideOutHorizontally(tween(TRANSITION_MS)) { it }

private fun NavController.openPost(id: Int) = navigate("post/$id")
private fun NavController.openComments(postId: Int) = navigate("post/$postId?$ARG_COMMENT=$COMMENTS_SECTION")
private fun NavController.openComment(postId: Int, commentId: Int?) =
    navigate("post/$postId?$ARG_COMMENT=${commentId ?: NO_COMMENT}")
private fun NavController.openUser(login: String) = navigate("user/" + Uri.encode(login))
private fun NavController.openTag(tag: String) = navigate("tag/" + Uri.encode(tag))

@Composable
fun VombatNavHost() {
    val graph = (LocalContext.current.applicationContext as VombatApp).graph
    val sessionViewModel: SessionViewModel = viewModel(
        factory = viewModelFactory { initializer { SessionViewModel(graph.auth) } },
    )
    val session by sessionViewModel.session.collectAsStateWithLifecycle()
    val savingFilters by sessionViewModel.savingFilters.collectAsStateWithLifecycle()
    val unread by graph.notifications.unread.collectAsStateWithLifecycle()
    val battleUnread by graph.battle.unread.collectAsStateWithLifecycle()
    val bugsEnabled by graph.settings.bugsEnabled.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val loggedIn = session is Session.LoggedIn
    val ownLogin = (session as? Session.LoggedIn)?.login
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(loggedIn, bugsEnabled) {
        if (!loggedIn) {
            graph.notifications.clearCount()
            graph.battle.clear()
            return@LaunchedEffect
        }
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                graph.notifications.refreshCount()
                if (bugsEnabled) graph.battle.refresh()
                delay(NOTIFICATIONS_POLL_MS)
            }
        }
    }

    val actionContext = remember(loggedIn) {
        ActionContext(
            repository = graph.actions,
            loggedIn = loggedIn,
            onRequireLogin = { navController.navigate(ROUTE_LOGIN) },
            onOpenComments = navController::openComments,
        )
    }
    val focusManager = LocalFocusManager.current
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { focusManager.clearFocus() }
    val systemUriHandler = LocalUriHandler.current
    val uriHandler = remember(systemUriHandler) {
        object : UriHandler {
            override fun openUri(uri: String) {
                when (val link = parseInternalLink(uri)) {
                    is InternalLink.Post -> navController.openPost(link.id)
                    is InternalLink.User -> navController.openUser(link.login)
                    is InternalLink.Tag -> navController.openTag(link.name)
                    null -> systemUriHandler.openUri(uri)
                }
            }
        }
    }

    CompositionLocalProvider(
        LocalActions provides actionContext,
        LocalUriHandler provides uriHandler,
        LocalVideoJobs provides graph.videoJobs,
    ) {
        NavHost(
            navController = navController,
            startDestination = ROUTE_FEED,
            enterTransition = { slideInHorizontally(tween(TRANSITION_MS)) { it } },
            exitTransition = { slideOutHorizontally(tween(TRANSITION_MS)) { -it / BACKDROP_SHIFT_DIVISOR } },
            popEnterTransition = { backdropReturn() },
            popExitTransition = { screenDismiss() },
            predictivePopEnterTransition = { _ -> backdropReturn() },
            predictivePopExitTransition = { _ -> screenDismiss() },
        ) {
            composable(ROUTE_FEED) {
                val feedViewModel: FeedViewModel = viewModel(
                    factory = viewModelFactory { initializer { FeedViewModel(graph.apollo) } },
                )
                val posts = feedViewModel.posts.collectAsLazyPagingItems()
                val context = LocalContext.current
                LaunchedEffect(loggedIn) {
                    if (loggedIn && bugsEnabled) graph.battle.refresh()
                }
                Scaffold(
                    topBar = {
                        VombatTopBar(
                            session = session,
                            hasNotifications = unread > 0,
                            showBattle = bugsEnabled,
                            hasBattleEvents = battleUnread > 0,
                            savingFilters = savingFilters,
                            onFiltersChange = { changed ->
                                sessionViewModel.saveFilters(changed) { saved ->
                                    if (saved) {
                                        posts.refresh()
                                    } else {
                                        Toast.makeText(context, R.string.filter_save_error, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onSearch = { navController.navigate(ROUTE_SEARCH) },
                            onBattle = { navController.navigate(ROUTE_BATTLE) },
                            onNotifications = { navController.navigate(ROUTE_NOTIFICATIONS) },
                            onSignIn = { navController.navigate(ROUTE_LOGIN) },
                            onProfile = { navController.openUser(it) },
                            onIgnoreList = { navController.navigate(ROUTE_IGNORE) },
                            onPostponed = { navController.navigate(ROUTE_POSTPONED) },
                            onSettings = { navController.navigate(ROUTE_SETTINGS) },
                            onAbout = { navController.navigate(ROUTE_ABOUT) },
                            onSignOut = sessionViewModel::logout,
                        )
                    },
                    floatingActionButton = {
                        if (loggedIn) {
                            FloatingActionButton(onClick = { navController.navigate(ROUTE_EDITOR) }) {
                                Text(stringResource(R.string.new_post_symbol), style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    },
                ) { padding ->
                    FeedScreen(
                        viewModel = feedViewModel,
                        posts = posts,
                        loggedIn = loggedIn,
                        onOpenPost = navController::openPost,
                        contentPadding = padding,
                    )
                }
            }
            composable(
                route = ROUTE_POST,
                arguments = listOf(
                    navArgument(ARG_ID) { type = NavType.IntType },
                    navArgument(ARG_COMMENT) {
                        type = NavType.IntType
                        defaultValue = NO_COMMENT
                    },
                ),
            ) { entry ->
                val postId = entry.arguments?.getInt(ARG_ID) ?: return@composable
                val target = when (val comment = entry.arguments?.getInt(ARG_COMMENT)) {
                    null, NO_COMMENT -> null
                    COMMENTS_SECTION -> PostTarget.Comments
                    else -> PostTarget.Comment(comment)
                }
                val postViewModel: PostViewModel = viewModel(
                    factory = viewModelFactory { initializer { PostViewModel(graph.apollo, graph.actions, graph.uploader, postId, target) } },
                )
                PostScreen(
                    viewModel = postViewModel,
                    loggedIn = loggedIn,
                    onSignIn = { navController.navigate(ROUTE_LOGIN) },
                    onBack = navController::popBackStack,
                )
            }
            composable(ROUTE_USER) { entry ->
                val login = entry.arguments?.getString(ARG_NAME) ?: return@composable
                val profileViewModel: ProfileViewModel = viewModel(
                    factory = viewModelFactory { initializer { ProfileViewModel(graph.apollo, graph.ignore, graph.battle, login) } },
                )
                ProfileScreen(
                    viewModel = profileViewModel,
                    title = login,
                    ownLogin = ownLogin,
                    bugsEnabled = bugsEnabled,
                    onOpenPost = navController::openPost,
                    onEditProfile = { navController.navigate(ROUTE_PROFILE_SETTINGS) },
                    onBack = navController::popBackStack,
                )
            }
            composable(ROUTE_PROFILE_SETTINGS) {
                WebPageScreen(SETTINGS_URL, R.string.edit_profile, graph.cookieJar, navController::popBackStack)
            }
            composable(ROUTE_BATTLE) {
                WebPageScreen(BATTLE_URL, R.string.battle, graph.cookieJar, navController::popBackStack)
            }
            composable(ROUTE_POSTPONED) {
                WebPageScreen(POSTPONED_URL, R.string.postponed_posts, graph.cookieJar, navController::popBackStack)
            }
            composable(ROUTE_SETTINGS) {
                SettingsScreen(graph.settings, navController::popBackStack)
            }
            composable(ROUTE_ABOUT) {
                AboutScreen(navController::popBackStack)
            }
            composable(ROUTE_TAG) { entry ->
                val tag = entry.arguments?.getString(ARG_NAME) ?: return@composable
                val tagViewModel: TagViewModel = viewModel(
                    factory = viewModelFactory { initializer { TagViewModel(graph.apollo, tag) } },
                )
                TagScreen(tag, tagViewModel, navController::openPost, navController::popBackStack)
            }
            composable(ROUTE_SEARCH) {
                val searchViewModel: SearchViewModel = viewModel(
                    factory = viewModelFactory { initializer { SearchViewModel(graph.apollo) } },
                )
                SearchScreen(searchViewModel, navController::openPost, navController::popBackStack)
            }
            composable(ROUTE_NOTIFICATIONS) {
                val notificationsViewModel: NotificationsViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer { NotificationsViewModel(graph.apollo, graph.notifications) }
                    },
                )
                NotificationsScreen(notificationsViewModel, navController::openComment, navController::popBackStack)
            }
            composable(ROUTE_EDITOR) {
                val editorViewModel: EditorViewModel = viewModel(
                    factory = viewModelFactory { initializer { EditorViewModel(graph.apollo, graph.uploader) } },
                )
                EditorScreen(
                    viewModel = editorViewModel,
                    onPublished = { postId ->
                        navController.popBackStack()
                        postId?.let(navController::openPost)
                    },
                    onBack = navController::popBackStack,
                )
            }
            composable(ROUTE_IGNORE) {
                val ignoreViewModel: IgnoreListViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer { IgnoreListViewModel(graph.ignore, graph.actions) }
                    },
                )
                IgnoreListScreen(ignoreViewModel, navController::popBackStack)
            }
            composable(ROUTE_LOGIN) {
                val loginViewModel: LoginViewModel = viewModel(
                    factory = viewModelFactory { initializer { LoginViewModel(graph.auth) } },
                )
                LoginScreen(loginViewModel, graph.cookieJar, onDone = navController::popBackStack)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VombatTopBar(
    session: Session,
    hasNotifications: Boolean,
    showBattle: Boolean,
    hasBattleEvents: Boolean,
    savingFilters: Boolean,
    onFiltersChange: (FeedFilters) -> Unit,
    onSearch: () -> Unit,
    onBattle: () -> Unit,
    onNotifications: () -> Unit,
    onSignIn: () -> Unit,
    onProfile: (String) -> Unit,
    onIgnoreList: () -> Unit,
    onPostponed: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
    onSignOut: () -> Unit,
) {
    TopAppBar(
        title = { Text(stringResource(R.string.app_name)) },
        expandedHeight = FEED_TOP_BAR_HEIGHT,
        actions = {
            if (session is Session.LoggedIn) {
                FeedFilterMenu(session.filters, enabled = !savingFilters, onChange = onFiltersChange)
            }
            when (session) {
                Session.Unknown -> Unit
                Session.Anonymous -> ProfileButton(onSignIn)
                is Session.LoggedIn -> {
                    DotIconButton(
                        icon = Icons.Outlined.Notifications,
                        description = stringResource(R.string.notifications),
                        dot = hasNotifications,
                        onClick = onNotifications,
                    )
                    ProfileButton { onProfile(session.login) }
                }
            }
            if (showBattle) {
                DotIconButton(
                    icon = Icons.Outlined.BugReport,
                    description = stringResource(R.string.battle),
                    dot = hasBattleEvents,
                    onClick = onBattle,
                )
            }
            MainMenu(session is Session.LoggedIn, onSearch, onIgnoreList, onPostponed, onSettings, onAbout, onSignOut)
        },
    )
}

@Composable
private fun DotIconButton(icon: ImageVector, description: String, dot: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        BadgedBox(badge = { if (dot) Badge() }) {
            Icon(icon, contentDescription = description)
        }
    }
}

@Composable
private fun ProfileButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.Outlined.Person, contentDescription = stringResource(R.string.profile))
    }
}

@Composable
private fun MainMenu(
    loggedIn: Boolean,
    onSearch: () -> Unit,
    onIgnoreList: () -> Unit,
    onPostponed: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
    onSignOut: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.menu))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.search_hint)) },
                onClick = {
                    open = false
                    onSearch()
                },
            )
            if (loggedIn) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.ignore_list)) },
                    onClick = {
                        open = false
                        onIgnoreList()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.postponed_posts)) },
                    onClick = {
                        open = false
                        onPostponed()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.settings)) },
                onClick = {
                    open = false
                    onSettings()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.about)) },
                onClick = {
                    open = false
                    onAbout()
                },
            )
            if (loggedIn) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.sign_out)) },
                    onClick = {
                        open = false
                        confirmSignOut = true
                    },
                )
            }
        }
    }
    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text(stringResource(R.string.sign_out_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmSignOut = false
                        onSignOut()
                    },
                ) { Text(stringResource(R.string.sign_out)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmSignOut = false }) { Text(stringResource(R.string.reply_cancel)) }
            },
        )
    }
}
