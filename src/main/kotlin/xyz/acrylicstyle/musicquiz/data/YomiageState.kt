package xyz.acrylicstyle.musicquiz.data

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.event.TrackEndEvent
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason
import dev.kord.common.annotation.KordVoice
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.entity.channel.MessageChannel
import dev.kord.voice.AudioFrame
import dev.kord.voice.AudioProvider
import dev.kord.voice.VoiceConnection
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import se.michaelthelin.spotify.model_objects.IPlaylistItem
import se.michaelthelin.spotify.model_objects.specification.PlaylistTrack
import xyz.acrylicstyle.musicquiz.data.YomiageStateStore.playTrack
import xyz.acrylicstyle.musicquiz.util.Util.getPreviewUrl
import java.util.concurrent.atomic.AtomicReference

@OptIn(KordVoice::class)
data class YomiageState(
    val kord: Kord,
    val guildId: Snowflake,
    val textChannelId: Snowflake,
    val voiceChannelId: Snowflake,
    val connection: VoiceConnection,
    var audioProvider: AtomicReference<AudioProvider>,
    var tracks: List<PlaylistTrack>,
) {
    private val audioPlayer: AudioPlayer = YomiageStateStore.audioPlayerManager.createPlayer()
    var playing: IPlaylistItem = tracks.random().track
        private set
    private var started: Boolean = false

    init {
        tracks = tracks.filter { it.track.getPreviewUrl() != null }
        audioPlayer.addListener { event ->
            if (event is TrackEndEvent) {
                runBlocking { playNext(event.endReason == AudioTrackEndReason.FINISHED) }
            }
        }
        audioProvider.set(AudioProvider {
            AudioFrame.fromData(audioPlayer.provide()?.data)
        })
    }

    suspend fun start() {
        if (started) return
        started = true
        playNext()
    }

    private suspend fun playNext(sendMessage: Boolean = false) {
        try {
            if (sendMessage) {
                kord.getChannelOf<MessageChannel>(textChannelId)?.createMessage("正解は ${playing.name} でした！")
            }
            delay(5000)
            playing = tracks.filter { it.track != playing }.random().track
            YomiageStateStore.audioPlayerManager.playTrack(playing.getPreviewUrl()!!, audioPlayer)
        } catch (e: Exception) {
            println("Error playing next track")
            e.printStackTrace()
            playNext()
        }
    }

    fun stopTrack() {
        audioPlayer.stopTrack()
    }

    suspend fun shutdown() {
        audioPlayer.destroy()
        connection.shutdown()
    }
}
