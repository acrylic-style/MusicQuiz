package xyz.acrylicstyle.musicquiz

import dev.kord.core.Kord
import dev.kord.core.event.gateway.ReadyEvent
import dev.kord.core.event.interaction.ApplicationCommandInteractionCreateEvent
import dev.kord.core.event.user.VoiceStateUpdateEvent
import dev.kord.core.on
import dev.kord.gateway.Intent
import dev.kord.gateway.Intents
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import xyz.acrylicstyle.musicquiz.commands.MusicQuizCommand
import xyz.acrylicstyle.musicquiz.config.BotConfig
import xyz.acrylicstyle.musicquiz.config.UsersConfig
import xyz.acrylicstyle.musicquiz.data.YomiageStateStore

suspend fun main() {
    // headless mode
    System.setProperty("java.awt.headless", "true")

    // load config
    BotConfig
    UsersConfig.load()

    val client = Kord(BotConfig.config.botToken)

    val commands = mapOf(
        "musicquiz" to MusicQuizCommand,
    )

    client.createGlobalApplicationCommands {
        commands.values.distinct().forEach { it.register(this) }
    }

    client.on<ApplicationCommandInteractionCreateEvent> {
        if (interaction.user.isBot) return@on
        commands.forEach { (name, command) ->
            if (interaction.invokedCommandName == name) {
                command.handle(interaction)
            }
        }
    }

    client.on<ReadyEvent> {
        println("Logged in as ${kord.getSelf().tag}!")
    }

    client.on<VoiceStateUpdateEvent> {
        if (state.userId == kord.selfId && state.channelId == null) {
            // handle server side "disconnect"
            YomiageStateStore.remove(state.guildId)?.shutdown()
        }
    }

    embeddedServer(
        Netty,
        port = BotConfig.config.port,
        host = "0.0.0.0",
        module = Application::appModule,
    ).start(wait = false)

    client.login {
        this.intents = Intents(
            Intent.GuildVoiceStates,
        )
    }
}

fun Application.appModule() {
    install(CallLogging)

    routing {
        get("/callback") {
            val state = call.request.queryParameters["state"] ?: return@get call.respondText("Invalid state", status = HttpStatusCode.BadRequest)
            val code = call.request.queryParameters["code"] ?: return@get call.respondText("Invalid code", status = HttpStatusCode.BadRequest)
            val userId = MusicQuizCommand.authStates.remove(state) ?: return@get call.respondText("Invalid state", status = HttpStatusCode.BadRequest)
            val token = BotConfig.config.getSpotifyApi().authorizationCode(code).build().execute()
            val user = UsersConfig[userId]
            user.accessToken = token.accessToken
            user.refreshToken = token.refreshToken
            UsersConfig.save()
            call.respondText("You have successfully logged in to Spotify. You can close this window now.")
        }
    }

    println("Listening on port ${BotConfig.config.port}")
}
