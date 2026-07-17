package com.rhymo.music

import android.os.Bundle
import android.app.Activity
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.rhymo.music.R
import com.rhymo.music.ui.theme.*
import com.rhymo.music.auth.GoogleAuthService
import com.rhymo.music.playback.rememberPlaybackController
import com.rhymo.music.data.DemoMusicRepository
import com.rhymo.music.data.SaavnMusicRepository
import com.rhymo.music.model.Song
import com.rhymo.music.ui.components.musicItems
import com.rhymo.music.ui.navigation.AppDestination
import com.rhymo.music.ui.navigation.TopLevelDestinations
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.C
import androidx.media3.common.Player
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.fragment.app.FragmentActivity
import com.rhymo.music.notifications.NotificationFragment
import androidx.core.net.toUri
import coil3.compose.AsyncImage

private val fallbackSongs = DemoMusicRepository.songs

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep system icons readable regardless of the phone's light/dark setting.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )
        setContent {
            RhymoTheme {
                // A root Surface provides the correct content color to every
                // Compose screen. Without it, text directly inside a Box can
                // inherit Android's black default and disappear on dark UI.
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Ink,
                    contentColor = Paper
                ) {
                    RhymoApp()
                }
            }
        }
    }
}

private typealias AppTab = AppDestination
private val navigationTabs = TopLevelDestinations

@Composable
fun RhymoApp() {
    val activity = LocalContext.current as FragmentActivity
    val auth = remember(activity) { GoogleAuthService(activity) }
    val playbackController = rememberPlaybackController(activity)
    val scope = rememberCoroutineScope()
    var onboarded by remember { mutableStateOf(auth.currentUser() != null) }
    var signingIn by remember { mutableStateOf(false) }
    var authMessage by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf(AppTab.Home) }
    var catalog by remember { mutableStateOf(fallbackSongs) }
    var activeQueue by remember { mutableStateOf(fallbackSongs) }
    var selectedSongIndex by remember { mutableIntStateOf(0) }
    var playerOrigin by remember { mutableStateOf(AppTab.Home) }

    LaunchedEffect(onboarded) {
        if (onboarded) {
            SaavnMusicRepository.trending().onSuccess { remoteSongs ->
                if (remoteSongs.isNotEmpty()) catalog = remoteSongs
            }
        }
    }

    val openSong: (Song, List<Song>) -> Unit = { song, queue ->
        val playableQueue = queue.ifEmpty { listOf(song) }
        activeQueue = playableQueue
        selectedSongIndex = playableQueue.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        playerOrigin = tab
        tab = AppTab.Swipe
    }
    if (!onboarded) {
        WelcomeScreen(
            signingIn = signingIn,
            authMessage = authMessage,
            onGoogleSignIn = {
                signingIn = true
                authMessage = null
                scope.launch {
                    auth.signIn()
                        .onSuccess {
                            onboarded = true
                            Toast.makeText(activity, activity.getString(R.string.auth_success), Toast.LENGTH_SHORT).show()
                        }
                        .onFailure { error -> authMessage = error.message ?: "Unable to sign in. Please try again." }
                    signingIn = false
                }
            },
            onGuest = { onboarded = true }
        )
    } else {
        // Tabs are destinations inside one activity. Back returns to Home
        // first; only a second back from Home exits the app.
        BackHandler(enabled = tab != AppTab.Home) {
            tab = if (tab == AppTab.Swipe) playerOrigin else AppTab.Home
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val useRail = maxWidth >= 600.dp && tab != AppTab.Swipe
            Row(Modifier.fillMaxSize()) {
                if (useRail) RhymoRail(tab) { tab = it }
                Scaffold(
                    modifier = Modifier.weight(1f),
                    containerColor = Ink,
                    contentColor = Paper,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    bottomBar = { if (!useRail && tab != AppTab.Swipe) RhymoNav(tab) { tab = it } }
                ) { padding ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        AnimatedContent(
                            targetState = tab,
                            modifier = Modifier.fillMaxHeight().widthIn(max = 920.dp).padding(padding),
                            transitionSpec = {
                                (fadeIn(tween(320)) + slideInHorizontally(tween(380)) { it / 10 })
                                    .togetherWith(fadeOut(tween(180)))
                            },
                            label = "screen"
                        ) { current ->
                            when (current) {
                                AppTab.Home -> HomeScreen(
                                    songs = catalog,
                                    openPlayer = openSong,
                                    openSearch = { tab = AppTab.Search },
                                    openNotifications = { NotificationFragment().show(activity.supportFragmentManager, NotificationFragment.TAG) },
                                    openProfile = { tab = AppTab.Profile }
                                )
                                AppTab.Search -> SearchScreen(popularSongs = catalog, openPlayer = openSong)
                                AppTab.Swipe -> SwipePlayer(
                                    songs = activeQueue,
                                    initialSongIndex = selectedSongIndex,
                                    controller = playbackController,
                                    onClose = { tab = playerOrigin }
                                )
                                AppTab.Library -> LibraryScreen(songs = catalog, openPlayer = openSong)
                                AppTab.Notifications -> HomeScreen(
                                    songs = catalog,
                                    openPlayer = openSong,
                                    openSearch = { tab = AppTab.Search },
                                    openNotifications = { NotificationFragment().show(activity.supportFragmentManager, NotificationFragment.TAG) },
                                    openProfile = { tab = AppTab.Profile }
                                )
                                AppTab.Profile -> ProfileScreen { auth.signOut(); onboarded = false }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeScreen(
    signingIn: Boolean,
    authMessage: String?,
    onGoogleSignIn: () -> Unit,
    onGuest: () -> Unit
) {
    val darkMode = isSystemInDarkTheme()
    val motion = rememberInfiniteTransition(label = "welcome ambience")
    val float = motion.animateFloat(0f, 26f, infiniteRepeatable(tween(3200), RepeatMode.Reverse), label = "float")
    
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    
    val contentAlpha by animateFloatAsState(if (visible) 1f else 0f, tween(1000, easing = FastOutSlowInEasing), label = "alpha")
    val contentOffset by animateDpAsState(if (visible) 0.dp else 32.dp, tween(1000, easing = EaseOutCubic), label = "offset")

    val welcomeGradient = if (darkMode) listOf(Color(0xFF241B35), Color(0xFF111019), DarkInk) else listOf(Color(0xFFFFF0FA), Color(0xFFF0ECFF), Color(0xFFE9FAFF), Color(0xFFFFF8EC))
    val headlineGradient = if (darkMode) listOf(DarkPaper, Color(0xFFD7CAFF), DarkPaper) else listOf(Color(0xFF17121D), Color(0xFF65359A), Color(0xFFD62F92))

    CompositionLocalProvider(LocalContentColor provides Paper) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(welcomeGradient))) {
        Box(Modifier.align(Alignment.TopEnd).offset(x = 90.dp, y = (-40).dp).graphicsLayer { translationY = float.value }.size(280.dp).clip(CircleShape).background(Brush.radialGradient(listOf(HotPink.copy(if (darkMode) .4f else .24f), Violet.copy(if (darkMode) .15f else .10f), Color.Transparent))))
        Box(Modifier.align(Alignment.CenterStart).offset(x = (-80).dp, y = 180.dp).graphicsLayer { translationX = float.value }.size(210.dp).clip(CircleShape).background(Brush.radialGradient(listOf(NeonBlue.copy(if (darkMode) .2f else .14f), Color.Transparent))))
        Box(Modifier.align(Alignment.CenterEnd).offset(x = 105.dp, y = (-30).dp).size(230.dp).clip(CircleShape).border(32.dp, Violet.copy(if (darkMode) .14f else .08f), CircleShape))
        Icon(Icons.Filled.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary.copy(.18f), modifier = Modifier.align(Alignment.CenterEnd).offset(x = 10.dp, y = 105.dp).graphicsLayer { rotationZ = -12f }.size(72.dp))
        
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp)
                .graphicsLayer { alpha = contentAlpha }
                .offset(y = contentOffset), 
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) { 
                BrandMark(size = 44.dp)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "RHYMO",
                    color = if (darkMode) Lime else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Black,
                    fontSize = 22.sp,
                    letterSpacing = 2.sp
                )
            }
            
            Column {
                Text(
                    "YOUR NEXT\nFAVORITE SONG\nIS ONE SWIPE AWAY.", 
                    style = MaterialTheme.typography.displayLarge.copy(
                        brush = Brush.linearGradient(headlineGradient),
                        fontSize = 52.sp,
                        lineHeight = 54.sp,
                        letterSpacing = (-2).sp
                    )
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    "A music discovery feed tuned to your taste. Swipe, save, and keep listening.", 
                    color = Muted, 
                    fontSize = 19.sp, 
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = onGoogleSignIn, 
                    enabled = !signingIn, 
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(62.dp)
                        .shadow(if (signingIn) 0.dp else 16.dp, RoundedCornerShape(20.dp), spotColor = Paper.copy(.25f)), 
                    shape = RoundedCornerShape(20.dp), 
                    colors = ButtonDefaults.buttonColors(containerColor = Paper, contentColor = Ink),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                ) {
                    if (signingIn) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp, color = Ink)
                    } else {
                        // Colored G representation (simple but effective for UX)
                        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                            Text("G", fontWeight = FontWeight.Black, fontSize = 22.sp, color = Ink)
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Text(if (signingIn) "Connecting…" else "Continue with Google", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
                
                if (authMessage != null) {
                    Surface(color = Coral.copy(.14f), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) { 
                        Text(authMessage, color = Color(0xFFFFAAA2), fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(14.dp)) 
                    }
                }
                
                OutlinedButton(
                    onClick = onGuest, 
                    enabled = !signingIn, 
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(.28f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                ) { 
                    Text("Preview as guest", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary) 
                }
                
                Text(
                    "By continuing, you agree to our Terms and Privacy Policy.", 
                    color = Muted.copy(.6f), 
                    fontSize = 11.sp, 
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp)
                )
            }
        }
    }
    }
}

@Composable
private fun HomeScreen(
    modifier: Modifier = Modifier,
    songs: List<Song>,
    openPlayer: (Song, List<Song>) -> Unit,
    openSearch: () -> Unit,
    openNotifications: () -> Unit,
    openProfile: () -> Unit
) {
    val trendTags = listOf("night drive", "soft pop", "indie summer", "desi mix")
    var selectedTrend by rememberSaveable { mutableStateOf(trendTags.first()) }
    LazyColumn(
        modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, top = 3.dp, end = 20.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        item { Row(verticalAlignment = Alignment.CenterVertically) { BrandMark(48.dp); Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text("FRIDAY, JUL 17", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp); Text("Hey, listener.", style = MaterialTheme.typography.headlineLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) }; Spacer(Modifier.width(10.dp)); NotificationButton(openNotifications); Spacer(Modifier.width(10.dp)); Box(Modifier.size(48.dp).clip(CircleShape).background(Brush.linearGradient(listOf(HotPink, Violet))).clickable(onClick = openProfile), contentAlignment = Alignment.Center) { Text("V", color = DarkInk, fontWeight = FontWeight.Bold) } } }
        item { Text("What should we play?", color = Muted); Spacer(Modifier.height(12.dp)); Surface(shape = RoundedCornerShape(20.dp), color = InkSoft, modifier = Modifier.fillMaxWidth().shadow(8.dp, RoundedCornerShape(20.dp), ambientColor = Violet.copy(.14f), spotColor = Violet.copy(.12f)).border(1.dp, MaterialTheme.colorScheme.primary.copy(.10f), RoundedCornerShape(20.dp)).clickable(onClick = openSearch)) { Row(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(34.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(.10f)), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) }; Spacer(Modifier.width(12.dp)); Text("Songs, artists, albums…", color = Muted) } } }
        item { Column { SectionTitle("TRENDING NOW", "See all"); Spacer(Modifier.height(6.dp)); Text("Pick a mood to tune your recommendations.", color = Muted, fontSize = 13.sp) } }
        item { LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(end = 12.dp)) { items(trendTags) { tag -> Chip(text = tag, selected = selectedTrend == tag, showHash = true) { selectedTrend = tag } } } }
        item { FeaturedCard(songs.first(), openPlayer = { openPlayer(songs.first(), songs) }) }
        item { SectionTitle("FRESH PICKS", "Updated today") }
        musicItems(songs.drop(1)) { song -> openPlayer(song, songs) }
    }
}

@Composable private fun SectionTitle(title: String, trailing: String) = Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.width(4.dp).height(22.dp).clip(CircleShape).background(Brush.verticalGradient(listOf(HotPink, MaterialTheme.colorScheme.primary)))); Spacer(Modifier.width(10.dp)); Text(title, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) }; if (trailing.isNotEmpty()) Surface(color = MaterialTheme.colorScheme.primary.copy(.08f), shape = CircleShape) { Text(trailing, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)) } }

@Composable private fun Chip(text: String, selected: Boolean = false, showHash: Boolean = false, onClick: () -> Unit = {}) = Surface(shape = RoundedCornerShape(50), color = if (selected) MaterialTheme.colorScheme.primary else InkSoft, modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(if (selected) .45f else .14f), RoundedCornerShape(50)).clickable(onClick = onClick)) { Row(Modifier.padding(horizontal = 15.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(7.dp).clip(CircleShape).background(if (selected) Brush.linearGradient(listOf(Color.White, Color.White)) else Brush.linearGradient(listOf(HotPink, MaterialTheme.colorScheme.primary)))); Spacer(Modifier.width(8.dp)); Text(if (showHash) "# $text" else text, color = if (selected) MaterialTheme.colorScheme.onPrimary else Paper, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) } }

@Composable
private fun FeaturedCard(song: Song, openPlayer: () -> Unit) {
    var saved by rememberSaveable(song.id) { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().height(310.dp).shadow(14.dp, RoundedCornerShape(28.dp), ambientColor = HotPink.copy(.16f), spotColor = Violet.copy(.18f)).clip(RoundedCornerShape(28.dp)).background(Brush.linearGradient(song.colors)).clickable(onClick = openPlayer)) {
        if (song.artworkUrl != null) {
            AsyncImage(
                model = song.artworkUrl,
                contentDescription = "${song.title} artwork",
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().graphicsLayer { alpha = .55f }
            )
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Black.copy(.08f), Color.Black.copy(.82f)))))
        }
        Box(Modifier.align(Alignment.TopStart).offset(x = (-35).dp, y = (-35).dp).size(150.dp).clip(CircleShape).border(24.dp, NeonBlue.copy(.18f), CircleShape))
        Text("01", Modifier.align(Alignment.TopEnd).padding(22.dp), color = Color.White.copy(.45f), style = MaterialTheme.typography.headlineLarge)
        Column(Modifier.align(Alignment.BottomStart).padding(22.dp)) { Text("EDITOR'S PICK", color = Lime, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp); Text(song.title, color = DarkPaper, style = MaterialTheme.typography.headlineLarge); Text(song.artist, color = DarkPaper.copy(.78f)); Spacer(Modifier.height(7.dp)); Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null, tint = DarkPaper.copy(.72f), modifier = Modifier.size(15.dp)); Spacer(Modifier.width(6.dp)); Text("Trending · 18.4K saves", color = DarkPaper.copy(.68f), fontSize = 11.sp) }; Spacer(Modifier.height(14.dp)); Row(verticalAlignment = Alignment.CenterVertically) { Button(onClick = openPlayer, colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = DarkInk), shape = CircleShape) { Icon(Icons.Filled.PlayArrow, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Play now", fontWeight = FontWeight.Bold) }; Spacer(Modifier.width(10.dp)); IconButton(onClick = { saved = !saved }, modifier = Modifier.size(48.dp).background(Color.Black.copy(.26f), CircleShape).border(1.dp, Color.White.copy(.22f), CircleShape)) { Icon(if (saved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder, contentDescription = if (saved) "Remove from saved" else "Save song", tint = if (saved) Lime else DarkPaper) } } }
    }
}

@Composable
private fun SearchScreen(
    modifier: Modifier = Modifier,
    popularSongs: List<Song>,
    openPlayer: (Song, List<Song>) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(popularSongs) }
    var loading by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var retryKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(query, popularSongs, retryKey) {
        if (query.isBlank()) {
            results = popularSongs
            loading = false
            searchError = null
            return@LaunchedEffect
        }
        loading = true
        searchError = null
        delay(450)
        SaavnMusicRepository.search(query)
            .onSuccess { results = it }
            .onFailure {
                results = emptyList()
                searchError = "Couldn’t reach the music service. Check your connection and try again."
            }
        loading = false
    }

    LazyColumn(modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(start = 20.dp, top = 6.dp, end = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { Text("Find your sound", style = MaterialTheme.typography.headlineLarge); Text("Songs, artists, moods — all in one place.", color = Muted) }
        item { Surface(shape = RoundedCornerShape(18.dp), color = InkSoft) { Row(Modifier.padding(horizontal = 18.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(12.dp)); BasicTextField(query, { query = it }, Modifier.weight(1f), textStyle = MaterialTheme.typography.bodyLarge.copy(color = Paper), cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), singleLine = true, decorationBox = { inner -> if (query.isEmpty()) Text("Search a song or artist", color = Muted); inner() }); if (loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) else if (query.isNotEmpty()) IconButton(onClick = { query = "" }, modifier = Modifier.size(36.dp)) { Icon(Icons.Filled.Close, contentDescription = "Clear search") } } } }
        item { LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) { items(listOf("Arijit Singh", "Pop", "Indie", "Bollywood", "Punjabi")) { suggestion -> Chip(suggestion, selected = query.equals(suggestion, true)) { query = suggestion } } } }
        item { SectionTitle(if (query.isBlank()) "POPULAR RIGHT NOW" else if (loading) "SEARCHING…" else "${results.size} RESULTS", "") }
        if (!loading && searchError == null) {
            musicItems(results) { song -> openPlayer(song, results) }
        }
        if (!loading && searchError != null) item {
            Surface(color = InkSoft, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("Search is offline", fontWeight = FontWeight.Bold)
                    Text(searchError!!, color = Muted, fontSize = 13.sp, lineHeight = 18.sp)
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = { retryKey++ }) { Text("Try again") }
                }
            }
        }
        if (!loading && searchError == null && results.isEmpty()) item { Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("No rhythm found", style = MaterialTheme.typography.headlineMedium); Text("Try another song or artist.", color = Muted) } }
    }
}

@Composable
private fun SwipePlayer(
    songs: List<Song>,
    initialSongIndex: Int,
    controller: androidx.media3.session.MediaController?,
    onClose: () -> Unit
) {
    val pager = rememberPagerState(initialPage = initialSongIndex.coerceIn(0, songs.lastIndex)) { songs.size }
    LaunchedEffect(pager.currentPage, controller, songs) {
        val song = songs[pager.currentPage]
        controller?.apply {
            setMediaItem(
                MediaItem.Builder()
                    .setMediaId(song.id)
                    .setUri(song.streamUrl)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(song.title)
                            .setArtist(song.artist)
                            .setAlbumTitle(song.album)
                            .apply { song.artworkUrl?.let { setArtworkUri(it.toUri()) } }
                            .build()
                    )
                    .build()
            )
            prepare()
            play()
        }
    }
    VerticalPager(state = pager, modifier = Modifier.fillMaxSize()) { page -> PlayerPage(songs[page], controller, onClose) }
}

@Composable
private fun PlayerPage(song: Song, controller: androidx.media3.session.MediaController?, onClose: () -> Unit) {
    var playing by remember(controller) { mutableStateOf(controller?.isPlaying == true) }
    var liked by rememberSaveable(song.id) { mutableStateOf(false) }
    var saved by rememberSaveable(song.id) { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var seekFraction by remember { mutableFloatStateOf(0f) }
    var draggingSeek by remember { mutableStateOf(false) }
    var showPlayPause by remember { mutableStateOf(true) }
    val beat = rememberInfiniteTransition(label = "player beat").animateFloat(1f, 1.09f, infiniteRepeatable(tween(680), RepeatMode.Reverse), label = "beat")
    val interactionSource = remember { MutableInteractionSource() }
    val togglePlayback: () -> Unit = {
        showPlayPause = true
        if (controller?.isPlaying == true) controller.pause() else controller?.play()
        Unit
    }
    LaunchedEffect(showPlayPause, playing) {
        if (showPlayPause && playing) {
            delay(3_000)
            showPlayPause = false
        }
    }
    DisposableEffect(controller) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
        }
        controller?.addListener(listener)
        playing = controller?.isPlaying == true
        onDispose { controller?.removeListener(listener) }
    }
    LaunchedEffect(controller, song.id) {
        while (true) {
            if (!draggingSeek) {
                val currentDuration = controller?.duration?.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
                val currentPosition = controller?.currentPosition?.coerceAtLeast(0L) ?: 0L
                durationMs = currentDuration
                positionMs = currentPosition.coerceAtMost(if (currentDuration > 0) currentDuration else currentPosition)
                seekFraction = if (currentDuration > 0) (positionMs.toFloat() / currentDuration).coerceIn(0f, 1f) else 0f
            }
            delay(250)
        }
    }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(song.colors)).clickable(interactionSource = interactionSource, indication = null, onClick = togglePlayback)) {
        if (song.artworkUrl != null) {
            AsyncImage(
                model = song.artworkUrl,
                contentDescription = "${song.title} artwork",
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().graphicsLayer { alpha = .52f }
            )
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, DarkInk.copy(.18f), DarkInk.copy(.94f)))))
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onClose, modifier = Modifier.size(44.dp).background(Color.Black.copy(.25f), CircleShape)) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Close player", tint = DarkPaper) }; Spacer(Modifier.weight(1f)); IconButton(onClick = {}) { Icon(Icons.Filled.MoreVert, contentDescription = "More options", tint = DarkPaper) } }
        AnimatedVisibility(visible = showPlayPause || !playing, modifier = Modifier.align(Alignment.Center), enter = fadeIn(tween(180)), exit = fadeOut(tween(280))) {
            Box(Modifier.graphicsLayer { scaleX = if (playing) beat.value else 1f; scaleY = if (playing) beat.value else 1f }.size(76.dp).clip(CircleShape).background(Brush.linearGradient(listOf(HotPink.copy(.75f), Violet.copy(.75f)))).clickable(onClick = togglePlayback), contentAlignment = Alignment.Center) { Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = if (playing) "Pause" else "Play", tint = DarkPaper, modifier = Modifier.size(36.dp)) }
        }
        Row(Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) { Text(song.tag, color = Lime, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.2.sp); Spacer(Modifier.height(5.dp)); Text(song.title, color = DarkPaper, style = MaterialTheme.typography.headlineLarge, maxLines = 1, overflow = TextOverflow.Ellipsis); Text("${song.artist}  ·  Follow", color = DarkPaper.copy(.75f)); Spacer(Modifier.height(10.dp)); Slider(value = seekFraction, onValueChange = { draggingSeek = true; seekFraction = it }, onValueChangeFinished = { if (durationMs > 0) controller?.seekTo((durationMs * seekFraction).toLong()); draggingSeek = false }, modifier = Modifier.fillMaxWidth().height(32.dp), colors = SliderDefaults.colors(thumbColor = Lime, activeTrackColor = Lime, inactiveTrackColor = Color.White.copy(.22f))); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(formatPlaybackTime(if (draggingSeek) (durationMs * seekFraction).toLong() else positionMs), color = DarkMuted, fontSize = 11.sp); Text(formatPlaybackTime(durationMs), color = DarkMuted, fontSize = 11.sp) }; Text("Swipe up for the next track · Tap anywhere to pause", color = DarkPaper.copy(.6f), fontSize = 11.sp, modifier = Modifier.padding(top = 12.dp)) }
            Spacer(Modifier.width(18.dp)); Column(verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) { Action(if (liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, "18.4K", liked) { liked = !liked }; Action(if (saved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder, "Save", saved) { saved = !saved }; Action(Icons.Outlined.Share, "Share") {} }
        }
    }
}

private fun formatPlaybackTime(milliseconds: Long): String {
    if (milliseconds <= 0L) return "0:00"
    val totalSeconds = milliseconds / 1000
    return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
}

@Composable private fun Action(icon: ImageVector, label: String, active: Boolean = false, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (active) 1.18f else 1f, tween(220), label = "action icon")
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(min = 56.dp).clickable(onClick = onClick)) { Box(Modifier.size(52.dp).clip(CircleShape).background(if (active) Lime else Color.Black.copy(.3f)), contentAlignment = Alignment.Center) { Icon(icon, contentDescription = label, tint = if (active) DarkInk else DarkPaper, modifier = Modifier.size(26.dp).graphicsLayer { scaleX = scale; scaleY = scale }) }; Text(label, color = DarkPaper, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp)) }
}

@Composable
private fun BrandMark(size: androidx.compose.ui.unit.Dp) {
    val pulse = rememberInfiniteTransition(label = "logo pulse").animateFloat(0.94f, 1.04f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "logo scale")
    Box(
        Modifier.size(size).graphicsLayer { scaleX = pulse.value; scaleY = pulse.value }
            .clip(RoundedCornerShape(size * .3f))
            .background(Brush.linearGradient(listOf(Violet, HotPink, Tangerine)))
            .border(1.dp, Color.White.copy(.25f), RoundedCornerShape(size * .3f)),
        contentAlignment = Alignment.Center
    ) {
        Text("R", color = Paper, fontWeight = FontWeight.Black, fontSize = (size.value * .5f).sp, letterSpacing = (-1).sp)
        Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = Lime, modifier = Modifier.offset(y = size * .24f).size(size * .38f))
    }
}

@Composable
private fun LibraryScreen(
    modifier: Modifier = Modifier,
    songs: List<Song>,
    openPlayer: (Song, List<Song>) -> Unit
) {
    LazyColumn(modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(start = 20.dp, top = 6.dp, end = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Your library", style = MaterialTheme.typography.headlineLarge); Text("The music you kept close.", color = Muted) }
        item { Spacer(Modifier.height(8.dp)); LibraryCard(Icons.Filled.Favorite, "Liked songs", "48 tracks", Coral) }
        item { LibraryCard(Icons.Filled.Download, "Downloads", "Listen offline", Violet) }
        item { LibraryCard(Icons.Filled.Add, "Create a playlist", "Start something new", Lime) }
        item { Spacer(Modifier.height(12.dp)); SectionTitle("RECENTLY SAVED", "See all") }
        musicItems(songs.take(3)) { song -> openPlayer(song, songs) }
    }
}

@Composable private fun LibraryCard(icon: ImageVector, title: String, subtitle: String, color: Color, onClick: (() -> Unit)? = null) = Surface(color = InkSoft, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)) { Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(color), contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, tint = DarkInk, modifier = Modifier.size(25.dp)) }; Spacer(Modifier.width(16.dp)); Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold); Spacer(Modifier.height(2.dp)); Text(subtitle, color = Muted, fontSize = 13.sp) }; Spacer(Modifier.width(12.dp)); Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Muted, modifier = Modifier.size(22.dp)) } }

@Composable
private fun NotificationButton(onClick: () -> Unit) {
    val ring = rememberInfiniteTransition(label = "notification ring").animateFloat(-7f, 7f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "bell rotation")
    Box(Modifier.size(48.dp).clip(CircleShape).background(InkSoft).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.Notifications, contentDescription = "Notifications", tint = Paper, modifier = Modifier.size(24.dp).graphicsLayer { rotationZ = ring.value })
        Box(Modifier.align(Alignment.TopEnd).offset(x = (-3).dp, y = 3.dp).size(10.dp).clip(CircleShape).background(HotPink).border(2.dp, Ink, CircleShape))
    }
}

@Composable
internal fun NotificationsScreen(modifier: Modifier = Modifier, onClose: () -> Unit = {}) {
    val alerts = listOf(
        Triple("Fresh Friday is live", "12 new tracks picked for your mood", Lime),
        Triple("Afterglow is trending", "Mira Vale just entered the top 10", HotPink),
        Triple("Your mix is ready", "A neon night-drive playlist made for you", NeonBlue)
    )
    LazyColumn(modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(start = 20.dp, top = 6.dp, end = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(stringResource(R.string.notification_title), style = MaterialTheme.typography.headlineLarge); Text("Fresh drops, artist updates and your music activity.", color = Muted) }; IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close notifications") } } }
        items(alerts) { alert ->
            Surface(Modifier.fillMaxWidth(), color = InkSoft, shape = RoundedCornerShape(20.dp)) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(alert.third)); Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) { Text(alert.first, fontWeight = FontWeight.Bold); Text(alert.second, color = Muted, fontSize = 13.sp, lineHeight = 18.sp) }
                    Text("now", color = alert.third, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun ProfileScreen(modifier: Modifier = Modifier, onSignOut: () -> Unit) {
    LazyColumn(modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(start = 20.dp, top = 6.dp, end = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Your profile", style = MaterialTheme.typography.headlineLarge); Text("Taste, playback and account controls.", color = Muted) }
        item { Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Brush.linearGradient(listOf(Violet, HotPink))).padding(24.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { BrandMark(68.dp); Spacer(Modifier.width(18.dp)); Column { Text("Rhymo listener", style = MaterialTheme.typography.headlineMedium); Text("Early listener · Level 04", color = Paper.copy(.75f)) } } } }
        item { LibraryCard(Icons.Outlined.History, "Listening activity", "Recently played and stats", NeonBlue) }
        item { LibraryCard(Icons.Outlined.Settings, "Playback settings", "Audio quality and data saver", Lime) }
        item { OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Coral)) { Text("Sign out", fontWeight = FontWeight.Bold) } }
    }
}

@Composable
private fun RhymoNav(selected: AppTab, onSelect: (AppTab) -> Unit) {
    NavigationBar(containerColor = Ink.copy(.98f), tonalElevation = 0.dp) { navigationTabs.forEach { tab ->
        val scale by animateFloatAsState(if (selected == tab) 1.16f else 1f, tween(200), label = "nav icon")
        NavigationBarItem(selected = selected == tab, onClick = { onSelect(tab) }, icon = { Icon(tab.icon, contentDescription = tab.label, modifier = Modifier.size(24.dp).graphicsLayer { scaleX = scale; scaleY = scale }) }, label = { Text(tab.label) }, colors = NavigationBarItemDefaults.colors(selectedIconColor = MaterialTheme.colorScheme.primary, selectedTextColor = MaterialTheme.colorScheme.primary, indicatorColor = MaterialTheme.colorScheme.primary.copy(.12f), unselectedIconColor = Muted, unselectedTextColor = Muted))
    } }
}

@Composable
private fun RhymoRail(selected: AppTab, onSelect: (AppTab) -> Unit) {
    NavigationRail(containerColor = InkSoft) {
        Spacer(Modifier.height(18.dp)); BrandMark(42.dp); Spacer(Modifier.height(24.dp))
        navigationTabs.forEach { tab ->
            NavigationRailItem(
                selected = selected == tab,
                onClick = { onSelect(tab) },
                icon = { Icon(tab.icon, contentDescription = tab.label, modifier = Modifier.size(24.dp)) },
                label = { Text(tab.label) },
                colors = NavigationRailItemDefaults.colors(selectedIconColor = MaterialTheme.colorScheme.primary, selectedTextColor = MaterialTheme.colorScheme.primary, indicatorColor = MaterialTheme.colorScheme.primary.copy(.12f), unselectedIconColor = Muted, unselectedTextColor = Muted)
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0C0F)
@Composable
private fun PreviewRhymo() { RhymoTheme { WelcomeScreen(false, null, {}, {}) } }
