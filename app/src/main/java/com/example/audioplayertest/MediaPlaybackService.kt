package com.example.audioplayertest

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Binder
import android.os.IBinder
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.MutableLiveData
import coil.imageLoader
import coil.request.ImageRequest
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector
import com.google.android.exoplayer2.ext.mediasession.TimelineQueueNavigator
import com.google.android.exoplayer2.ui.PlayerNotificationManager
import kotlinx.coroutines.*

class MediaPlaybackService : Service() {

    private lateinit var exoplayer: ExoPlayer
    private lateinit var playerNotificationManager: PlayerNotificationManager
    private var mediaSession: MediaSessionCompat? = null
    private lateinit var stateBuilder: PlaybackStateCompat.Builder
    private val binder = LocalBinder()
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)

    @Suppress("PropertyName")
    val EMPTY_PLAYBACK_STATE: PlaybackStateCompat = PlaybackStateCompat.Builder()
        .setState(PlaybackStateCompat.STATE_NONE, 0, 0f)
        .build()

    @Suppress("PropertyName")
    val NOTHING_PLAYING: MediaMetadataCompat = MediaMetadataCompat.Builder()
        .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, "")
        .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, 0)
        .build()
    private val playbackState = MutableLiveData<PlaybackStateCompat>()
        .apply { postValue(EMPTY_PLAYBACK_STATE) }

    private val mediaMetadata = MutableLiveData<MediaMetadataCompat>()
        .apply { postValue(NOTHING_PLAYING) }

    inner class LocalBinder : Binder() {
        fun getService(): MediaPlaybackService = this@MediaPlaybackService
        fun getCurrentMediaMetadata(): MutableLiveData<MediaMetadataCompat> = mediaMetadata
        fun getStateLiveData(): MutableLiveData<PlaybackStateCompat> = playbackState
        fun getTransportControls(): MediaControllerCompat.TransportControls = transportControls
    }

    override fun onBind(intent: Intent?): IBinder? {
        return binder
    }

    private lateinit var transportControls: MediaControllerCompat.TransportControls

    override fun onCreate() {
        super.onCreate()
        Log.d("MediaPlayer", "Init service")
        exoplayer = ExoPlayer.Builder(applicationContext)
            .setSeekForwardIncrementMs(10000)
            .setSeekBackIncrementMs(10000).build()
        exoplayer.playWhenReady = true
        val audioAttributes: AudioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.CONTENT_TYPE_SPEECH)
            .build()
        exoplayer.setAudioAttributes(audioAttributes, true)
        mediaSession = MediaSessionCompat(this, "vom_media_session")
        val mediaSessionConnector = MediaSessionConnector(mediaSession!!).apply {
            setPlayer(exoplayer)
            setQueueNavigator(object : TimelineQueueNavigator(mediaSession) {
                override fun getMediaDescription(
                    player: Player,
                    windowIndex: Int
                ): MediaDescriptionCompat {
                    Log.d("mediaItemAtIndex", windowIndex.toString())
                    val mediaItemAtIndex = player.getMediaItemAt(windowIndex)

                    return MediaDescriptionCompat.Builder()
                        .setTitle(mediaItemAtIndex.mediaMetadata.albumTitle)
                        .setSubtitle(mediaItemAtIndex.mediaMetadata.albumTitle)
                        .setIconUri(mediaItemAtIndex.mediaMetadata.artworkUri)
                        .build()
                }
            })

            setMediaMetadataProvider(CustomMediaMetadataProvider())
        }
        mediaSessionConnector.mediaSession.controller?.registerCallback(
            object :
                MediaControllerCompat.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
                    super.onPlaybackStateChanged(state)
                    playbackState.postValue(state ?: EMPTY_PLAYBACK_STATE)

                }

                override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
                    super.onMetadataChanged(metadata)
                    mediaMetadata.postValue(metadata)

                }

            })

        transportControls = mediaSession?.controller?.transportControls!!
        playerNotificationManager = PlayerNotificationManager.Builder(
            this, 100,
            "media_playback_channel",
        )
            .setMediaDescriptionAdapter(
                DescriptionAdapter(mediaSessionConnector.mediaSession.controller)
            )
            .setNotificationListener(object : PlayerNotificationManager.NotificationListener {
                override fun onNotificationCancelled(
                    notificationId: Int,
                    dismissedByUser: Boolean
                ) {
                    super.onNotificationCancelled(notificationId, dismissedByUser)
                    if (dismissedByUser) {
                        stopSelf()
                    }

                }

                override fun onNotificationPosted(
                    notificationId: Int,
                    notification: Notification,
                    ongoing: Boolean
                ) {
                    super.onNotificationPosted(notificationId, notification, ongoing)
                    startForeground(notificationId, notification)
                }
            })
            .setChannelNameResourceId(R.string.app_name)
            .build()

        playerNotificationManager.setUseRewindAction(false)
        playerNotificationManager.setPlayer(exoplayer)
        playerNotificationManager.setMediaSessionToken(mediaSession!!.sessionToken)
        mediaSession?.isActive = true


    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        val mediaMetadata =
            MediaMetadata.Builder()
                .setDisplayTitle("Title of Audio")
                .setTitle("Title of Audio")
                .setSubtitle("Subtitle")
                .setAlbumTitle("Album Title")
                .setArtist("Meeps")
                .setArtworkUri(Uri.parse("https://cdn.britannica.com/84/206384-050-00698723/Javan-gliding-tree-frog.jpg"))
                .build()
        val mediaItem = MediaItem.Builder()
            .setUri("https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3")
            .setMediaMetadata(mediaMetadata)
            .build()


        exoplayer.setMediaItem(mediaItem)
        exoplayer.prepare()
        exoplayer.play()

        return super.onStartCommand(intent, flags, startId)

    }

    override fun onDestroy() {
        super.onDestroy()
        stopForeground(true)
        mediaSession?.isActive = false
        mediaSession?.release()
        playerNotificationManager.setPlayer(null)
        exoplayer.release();
        job.cancel()
    }


    private inner class DescriptionAdapter(private val controller: MediaControllerCompat) :
        PlayerNotificationManager.MediaDescriptionAdapter {

        var currentIconUri: Uri? = null
        var currentBitmap: Bitmap? = null
        override fun getCurrentContentTitle(player: Player): CharSequence {

            // return controller.metadata.description.title.toString()
            return "tested"
        }


        override fun createCurrentContentIntent(player: Player): PendingIntent? {
            //  return controller.sessionActivity
            return null
        }

        override fun getCurrentContentText(player: Player): CharSequence? {
//            return controller.metadata.description.subtitle.toString()
            return "hello"
        }

        override fun getCurrentLargeIcon(
            player: Player,
            callback: PlayerNotificationManager.BitmapCallback
        ): Bitmap? {
            val iconUri = controller.metadata.description.iconUri
            return if (currentIconUri != iconUri || currentBitmap == null) {

                currentIconUri = iconUri
                scope.launch {
                    currentBitmap = iconUri?.let {
                        resolveUriAsBitmap(it)
                    }
                    currentBitmap?.let { callback.onBitmap(it) }
                }
                null
            } else {
                currentBitmap
            }
        }


        private suspend fun resolveUriAsBitmap(uri: Uri): Bitmap? {
            return withContext(Dispatchers.IO) {
                // Block on downloading artwork.
                val request = ImageRequest.Builder(this@MediaPlaybackService)
                    .data(uri)
                    .size(144, 144)
                    .build()
                val drawable = this@MediaPlaybackService.imageLoader.execute(request).drawable
                return@withContext drawable?.toBitmap()
            }
        }
    }

    private inner class CustomMediaMetadataProvider :
        MediaSessionConnector.MediaMetadataProvider {
        override fun getMetadata(player: Player): MediaMetadataCompat {
            val mediaItem = player.currentMediaItem
            val mediaMetadataCompat = MediaMetadataCompat.Builder().apply {

                putString(
                    MediaMetadataCompat.METADATA_KEY_TITLE,
                    mediaItem?.mediaMetadata?.title.toString()
                )
                putString(
                    MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE,
                    mediaItem?.mediaMetadata?.displayTitle.toString()
                )
                putString(
                    MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE,
                    mediaItem?.mediaMetadata?.subtitle.toString()
                )
                putString(
                    MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION,
                    mediaItem?.mediaMetadata?.description.toString()
                )
                putString(
                    MediaMetadataCompat.METADATA_KEY_ARTIST,
                    mediaItem?.mediaMetadata?.artist.toString()
                )
                putString(
                    MediaMetadataCompat.METADATA_KEY_ALBUM,
                    mediaItem?.mediaMetadata?.albumTitle.toString()
                )
                putString(
                    MediaMetadataCompat.METADATA_KEY_MEDIA_URI,
                    mediaItem?.mediaMetadata?.mediaUri.toString()
                )
                putString(
                    MediaMetadataCompat.METADATA_KEY_ART_URI,
                    mediaItem?.mediaMetadata?.artworkUri.toString()
                )
                putLong(
                    MediaMetadataCompat.METADATA_KEY_DURATION,
                    if (player.isCurrentMediaItemDynamic || player.duration === C.TIME_UNSET) -1 else player.duration
                )

            }.build()
            return mediaMetadataCompat

        }
    }
}