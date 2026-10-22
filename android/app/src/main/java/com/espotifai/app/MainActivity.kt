package com.espotifai.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.ImageLoader
import com.espotifai.app.data.ApiClient
import com.espotifai.app.ui.AlbumsScreen
import com.espotifai.app.ui.ArtistsScreen
import com.espotifai.app.ui.MiniPlayerBar
import com.espotifai.app.ui.PlayerScreen
import com.espotifai.app.ui.PlaylistDetailScreen
import com.espotifai.app.ui.PlaylistsScreen
import com.espotifai.app.ui.QueueScreen
import com.espotifai.app.ui.SettingsScreen
import com.espotifai.app.ui.TracksScreen
import com.espotifai.app.ui.theme.LocalImageLoader
import com.espotifai.app.ui.theme.EspotifaiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermission()
        setContent {
            EspotifaiTheme {
                val vm: MusicViewModel = viewModel()
                val configured by vm.configured.collectAsState()
                val context = LocalContext.current
                val token by vm.token.collectAsState()
                val imageLoader = remember(token) {
                    ImageLoader.Builder(context)
                        .okHttpClient(ApiClient.buildOkHttp(token))
                        .crossfade(true)
                        .build()
                }
                CompositionLocalProvider(LocalImageLoader provides imageLoader) {
                    if (!configured) {
                        SettingsScreen(vm)
                    } else {
                        App(vm)
                    }
                }
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        }
    }
}

@Composable
private fun App(vm: MusicViewModel) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val currentTrack by vm.currentTrack.collectAsState()
    val showMiniPlayer = route != "player" && currentTrack != null

    Scaffold(
        bottomBar = {
            if (showMiniPlayer) {
                MiniPlayerBar(vm) { navController.navigate("player") }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "artists",
            modifier = Modifier.padding(padding)
        ) {
            composable("artists") {
                ArtistsScreen(
                    vm = vm,
                    onArtistClick = { id -> navController.navigate("albums/$id") },
                    onAlbumClick = { albumId -> navController.navigate("tracks/$albumId") },
                    onSettings = { vm.openSettings() },
                    onPlaylists = { navController.navigate("playlists") },
                    onQueue = { navController.navigate("queue") }
                )
            }
            composable("albums/{artistId}") { entry ->
                val id = entry.arguments?.getString("artistId") ?: ""
                AlbumsScreen(
                    vm = vm,
                    artistId = id,
                    onAlbumClick = { albumId -> navController.navigate("tracks/$albumId") },
                    onBack = { navController.popBackStack() }
                )
            }
            composable("tracks/{albumId}") { entry ->
                val id = entry.arguments?.getString("albumId") ?: ""
                TracksScreen(
                    vm = vm,
                    albumId = id,
                    onBack = { navController.popBackStack() }
                )
            }
            composable("player") {
                PlayerScreen(
                    vm = vm,
                    onBack = { navController.popBackStack() },
                    onQueue = { navController.navigate("queue") }
                )
            }
            composable("playlists") {
                PlaylistsScreen(
                    vm = vm,
                    onPlaylistClick = { id -> navController.navigate("playlist/$id") },
                    onBack = { navController.popBackStack() }
                )
            }
            composable("playlist/{playlistId}") { entry ->
                val id = entry.arguments?.getString("playlistId") ?: ""
                PlaylistDetailScreen(
                    vm = vm,
                    playlistId = id,
                    onBack = { navController.popBackStack() }
                )
            }
            composable("queue") {
                QueueScreen(vm = vm, onBack = { navController.popBackStack() })
            }
        }
    }
}
