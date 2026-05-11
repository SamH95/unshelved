package com.samwise.unshelved.service

import android.app.PendingIntent
import android.os.Bundle
import android.util.Log
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import com.google.android.gms.cast.framework.CastContext
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class PlaybackService : MediaLibraryService() {

    @Inject lateinit var autoCallback: AutoMediaLibraryCallback
    @Inject lateinit var playerRepository: PlayerRepository

    private var mediaSession: MediaLibrarySession? = null
    private var cache: SimpleCache? = null
    private var localPlayer: ExoPlayer? = null
    private var localForwardingPlayer: ForwardingPlayer? = null
    private var castPlayer: CastPlayer? = null
    private var castForwardingPlayer: ForwardingPlayer? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()

        val cacheDir = File(cacheDir, "exoplayer_cache")
        val dbProvider = StandaloneDatabaseProvider(this)
        val localCache = SimpleCache(cacheDir, LeastRecentlyUsedCacheEvictor(200L * 1024 * 1024), dbProvider)
        cache = localCache

        val okClient = OkHttpClient.Builder().build()
        val upstreamFactory = OkHttpDataSource.Factory(okClient)
        val cacheFactory: DataSource.Factory = CacheDataSource.Factory()
            .setCache(localCache)
            .setUpstreamDataSourceFactory(upstreamFactory)
        val dataSourceFactory = DefaultDataSource.Factory(this, cacheFactory)

        val extractorsFactory = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        localPlayer = player

        localForwardingPlayer = wrapWithSeekInterception(player)

        try {
            val castContext = CastContext.getSharedInstance(this)
            val cast = CastPlayer(castContext).apply {
                setSessionAvailabilityListener(object : SessionAvailabilityListener {
                    override fun onCastSessionAvailable() = switchToCast()
                    override fun onCastSessionUnavailable() = switchToLocal()
                })
            }
            castPlayer = cast
            castForwardingPlayer = wrapWithSeekInterception(cast)
        } catch (e: Exception) {
            Log.w("PlaybackService", "Cast unavailable: ${e.message}")
        }

        val sessionIntent = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        val jumpBackCmd = SessionCommand(CMD_JUMP_BACK, Bundle.EMPTY)
        val jumpForwardCmd = SessionCommand(CMD_JUMP_FORWARD, Bundle.EMPTY)

        val jumpBackButton = CommandButton.Builder(CommandButton.ICON_SKIP_BACK_10)
            .setDisplayName("Jump Back")
            .setSessionCommand(jumpBackCmd)
            .build()
        val jumpForwardButton = CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD_30)
            .setDisplayName("Jump Forward")
            .setSessionCommand(jumpForwardCmd)
            .build()

        mediaSession = MediaLibrarySession.Builder(this, localForwardingPlayer!!, autoCallback)
            .apply { sessionIntent?.let { setSessionActivity(it) } }
            .setMediaButtonPreferences(listOf(jumpBackButton, jumpForwardButton))
            .build()
    }

    private fun switchToCast() {
        val session = mediaSession ?: return
        val local = localPlayer ?: return
        val cast = castPlayer ?: return
        val castForwarding = castForwardingPlayer ?: cast

        val currentIndex = local.currentMediaItemIndex
        val currentPosition = local.currentPosition
        val wasPlaying = local.isPlaying
        val speed = local.playbackParameters.speed

        local.pause()

        serviceScope.launch {
            val streamingItems = playerRepository.buildStreamingItems()
            if (streamingItems.isNullOrEmpty()) {
                Log.w("PlaybackService", "Cast: no streaming items available, aborting cast switch")
                if (wasPlaying) local.play()
                return@launch
            }
            Log.d("PlaybackService", "Cast: streamingItems=${streamingItems.size}, index=$currentIndex, pos=$currentPosition, wasPlaying=$wasPlaying")
            streamingItems.firstOrNull()?.let { Log.d("PlaybackService", "Cast first item uri=${it.localConfiguration?.uri}, mime=${it.localConfiguration?.mimeType}") }
            cast.setMediaItems(streamingItems, currentIndex, currentPosition)
            cast.playbackParameters = PlaybackParameters(speed)
            cast.prepare()
            session.player = castForwarding
            if (wasPlaying) cast.play()
            playerRepository.setCasting(true)
            Log.d("PlaybackService", "Switched to Cast player")
        }
    }

    private fun switchToLocal() {
        val session = mediaSession ?: return
        val local = localPlayer ?: return
        val forwarding = localForwardingPlayer ?: return
        val cast = castPlayer ?: return

        val currentPosition: Long
        val wasPlaying: Boolean
        val speed: Float
        try {
            currentPosition = cast.currentPosition.coerceAtLeast(0)
            wasPlaying = cast.isPlaying
            speed = cast.playbackParameters.speed
        } catch (e: Exception) {
            Log.w("PlaybackService", "Cast player state unavailable, using PlayerRepository state")
            session.player = forwarding
            playerRepository.setCasting(false)
            return
        }

        session.player = forwarding

        // Update local player position to where cast left off
        if (local.mediaItemCount > 0) {
            local.seekTo(local.currentMediaItemIndex, currentPosition)
            local.playbackParameters = PlaybackParameters(speed)
            if (wasPlaying) local.play()
        }

        playerRepository.setCasting(false)
        Log.d("PlaybackService", "Switched to local player, pos=$currentPosition, wasPlaying=$wasPlaying")
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        mediaSession

    private fun wrapWithSeekInterception(player: Player): ForwardingPlayer =
        object : ForwardingPlayer(player) {
            override fun seekToNext() = playerRepository.seekForward()
            override fun seekToPrevious() = playerRepository.seekBack()
            override fun seekToNextMediaItem() = playerRepository.seekForward()
            override fun seekToPreviousMediaItem() = playerRepository.seekBack()
            override fun seekForward() = playerRepository.seekForward()
            override fun seekBack() = playerRepository.seekBack()
            override fun getSeekForwardIncrement(): Long = 30_000L
            override fun getSeekBackIncrement(): Long = 15_000L
            override fun isCommandAvailable(command: Int): Boolean {
                if (command == COMMAND_SEEK_FORWARD || command == COMMAND_SEEK_BACK ||
                    command == COMMAND_SEEK_TO_NEXT || command == COMMAND_SEEK_TO_PREVIOUS) return true
                return super.isCommandAvailable(command)
            }
            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands().buildUpon()
                    .add(COMMAND_SEEK_FORWARD)
                    .add(COMMAND_SEEK_BACK)
                    .add(COMMAND_SEEK_TO_NEXT)
                    .add(COMMAND_SEEK_TO_PREVIOUS)
                    .build()
            }
        }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        castPlayer?.setSessionAvailabilityListener(null)
        castPlayer?.release()
        castPlayer = null
        castForwardingPlayer = null
        localForwardingPlayer = null
        localPlayer = null
        cache?.release()
        cache = null
        super.onDestroy()
    }
}
