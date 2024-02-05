package xyz.acrylicstyle.musicquiz.commands

import dev.kord.common.annotation.KordVoice
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.VoiceChannelBehavior
import dev.kord.core.behavior.channel.connect
import dev.kord.core.behavior.interaction.respondEphemeral
import dev.kord.core.behavior.interaction.respondPublic
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.Guild
import dev.kord.core.entity.Member
import dev.kord.core.entity.interaction.ApplicationCommandInteraction
import dev.kord.rest.builder.interaction.GlobalMultiApplicationCommandBuilder
import dev.kord.rest.builder.interaction.string
import dev.kord.rest.builder.interaction.subCommand
import dev.kord.voice.AudioProvider
import kotlinx.coroutines.delay
import se.michaelthelin.spotify.model_objects.specification.PlaylistTrack
import xyz.acrylicstyle.musicquiz.config.BotConfig
import xyz.acrylicstyle.musicquiz.config.UsersConfig
import xyz.acrylicstyle.musicquiz.data.YomiageState
import xyz.acrylicstyle.musicquiz.data.YomiageStateStore
import xyz.acrylicstyle.musicquiz.util.Util.getPreviewUrl
import xyz.acrylicstyle.musicquiz.util.Util.optString
import xyz.acrylicstyle.musicquiz.util.Util.optSubcommand
import java.util.*
import java.util.concurrent.atomic.AtomicReference

object MusicQuizCommand : CommandHandler {
    val authStates = mutableMapOf<String, Snowflake>()

    override suspend fun handle(interaction: ApplicationCommandInteraction) {
        val guild = interaction.channel.getGuildOrNull()!!
        val member = guild.getMember(interaction.user.id)
        val state = YomiageStateStore[guild.id]
        interaction.optSubcommand("start")?.let {
            start(interaction, guild, member, it.optString("playlist")!!, it.optString("album"))
        }
        if (interaction.optSubcommand("leave") != null) {
            if (state == null) {
                interaction.respondEphemeral { content = "読み上げ中のセッションがありません。" }
                return
            }
            if (state.textChannelId != interaction.channelId) {
                interaction.respondEphemeral { content = "このチャンネルではleaveコマンドを使用できません。" }
                return
            }
            val removedState = YomiageStateStore.remove(guild.id)
            if (removedState != null) {
                removedState.shutdown()
                interaction.respondPublic { content = "<#${removedState.voiceChannelId}>の読み上げを終了しました" }
            } else {
                interaction.respondEphemeral { content = "読み上げ中のセッションがありません。" }
            }
        }
        if (interaction.optSubcommand("where") != null) {
            if (state != null) {
                interaction.respondEphemeral {
                    content = """
                        下記のチャンネルで読み上げ中です。
                        テキストチャンネル: <#${state.textChannelId}>
                        ボイスチャンネル: <#${state.voiceChannelId}>
                    """.trimIndent()
                }
            } else {
                interaction.respondEphemeral { content = "読み上げ中のセッションがありません。" }
            }
        }
        if (interaction.optSubcommand("skip") != null) {
            if (state != null && state.textChannelId == interaction.channelId) {
                interaction.respondPublic { content = "現在再生中の曲をスキップしました。" }
                state.stopTrack()
            } else {
                interaction.respondEphemeral { content = "読み上げ中のセッションがありません。" }
            }
        }
        if (interaction.optSubcommand("login") != null) {
            val uuid = UUID.randomUUID().toString().replace("-", "")
            authStates[uuid] = member.id
            BotConfig.config.getSpotifyApi().authorizationCodeUri()
                .scope("playlist-read-private playlist-read-collaborative")
                .state(uuid)
                .build()
                .execute()
                .let { interaction.respondEphemeral { content = it.toString() } }
        }
        interaction.optSubcommand("answer")?.let {
            val name = it.optString("name")!!
            if (state == null || state.textChannelId != interaction.channelId) {
                interaction.respondEphemeral { content = "読み上げ中のセッションがありません。" }
                return
            }
            if (state.playing.name.equals(name, ignoreCase = true)) {
                interaction.respondPublic { content = "ぴんぽーん ($name)" }
                state.stopTrack()
            } else {
                interaction.respondPublic { content = "ぶぶー ($name)" }
            }
        }
    }

    @OptIn(KordVoice::class)
    private suspend fun start(interaction: ApplicationCommandInteraction, guild: Guild, member: Member, playlist: String, album: String?) {
        val user = UsersConfig[member.id]
        if (user.accessToken == null || user.refreshToken == null) {
            interaction.respondEphemeral { content = "`/musicquiz login`を実行してください。" }
            return
        }
        if (YomiageStateStore[guild.id] != null) {
            interaction.respondEphemeral { content = "別の場所で読み上げているため、参加できません。" }
            return
        }
        val voiceState = member.getVoiceStateOrNull()
        val channel = (voiceState?.getChannelOrNull() as? VoiceChannelBehavior)?.asChannel()
        if (channel == null) {
            interaction.respondEphemeral { content = "ボイスチャンネルに参加してください。すでに参加している場合は参加しなおしてください。" }
            return
        }
        val defer = interaction.deferPublicResponse()
        try {
            val client = BotConfig.config.getSpotifyApi(user)
            val response = client.authorizationCodeRefresh().grant_type("refresh_token").build().execute()
            println(response)
            user.accessToken = response.accessToken
            user.refreshToken = response.refreshToken
            client.accessToken = user.accessToken
            val playlistInfo = client.getPlaylist(playlist).build().execute()
            val list = mutableListOf<PlaylistTrack>()
            val tracks = client.getPlaylistsItems(playlist).limit(50).build().execute()
            // load all except first 50
            list.addAll(tracks.items)
            if (tracks.items.size == 50) {
                var offset = 50
                while (true) {
                    val next = client.getPlaylistsItems(playlist).offset(offset).limit(50).build().execute()
                    list.addAll(next.items)
                    offset += next.items.size
                    if (next.items.size < 50) break
                }
            }
            val ref = AtomicReference<AudioProvider>()
            val connection = channel.connect {
                selfDeaf = true

                audioProvider {
                    ref.get().provide()
                }
            }
            val state = YomiageState(interaction.kord, guild.id, interaction.channelId, channel.id, connection, ref, list)
            YomiageStateStore.put(guild.id, state)
            defer.respond {
                content = """
                    接続しました。5秒後に再生を開始します。(__**音量注意**__)
                    プレイリスト: ${playlistInfo.name} - ${list.filter { it.track.getPreviewUrl() != null }.size}曲
                    テキストチャンネル: <#${interaction.channelId}>
                    ボイスチャンネル: <#${channel.id}>
                """.trimIndent()
            }
            state.start()
        } catch (e: Exception) {
            defer.respond { content = "エラーが発生しました。" }
            println("Could not join the voice channel ${guild.id} / ${channel.id}")
            e.printStackTrace()
        }
    }

    override fun register(builder: GlobalMultiApplicationCommandBuilder) {
        builder.input("musicquiz", "読み上げコマンド") {
            dmPermission = false

            subCommand("start", "VCに参加します") {
                string("playlist", "プレイリスト") {
                    required = true
                }
                string("album", "アルバム")
            }
            subCommand("leave", "読み上げを終了します")
            subCommand("where", "どこで読み上げているかを表示します")
            subCommand("skip", "現在再生してる読み上げをスキップします")
            subCommand("login", "Spotifyでログインします")
            subCommand("answer", "答えを送信する") {
                string("name", "曲名") {
                    required = true
                }
            }
        }
    }
}
