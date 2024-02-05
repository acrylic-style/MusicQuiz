package xyz.acrylicstyle.musicquiz.config

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlComment
import com.charleskorn.kaml.YamlConfiguration
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import se.michaelthelin.spotify.SpotifyApi
import java.io.File
import java.net.URI

@Serializable
data class BotConfig(
    @YamlComment(
        "次回起動時にbot.ymlを更新するかどうかを指定します。",
        "trueにした場合、次回起動時にbot.ymlは上書きされ、overwrite設定は自動的にfalseになります。",
    )
    var overwrite: Boolean = true,
    val port: Int = 8080,
    val botToken: String = "<bot token here>",
    val clientId: String = "<spotify client id here>",
    val clientSecret: String = "<spotify client secret here>",
    val redirectUri: String = "http://localhost:8080/callback",
) {
    companion object {
        private val yaml = Yaml(configuration = YamlConfiguration(encodeDefaults = true, strictMode = false))

        val config: BotConfig = File("config/bot.yml").let { file ->
            if (!file.parentFile.exists()) file.parentFile.mkdirs()
            if (!file.exists()) file.writeText(yaml.encodeToString(BotConfig()))
            yaml.decodeFromString(serializer(), file.readText())
        }

        init {
            if (config.overwrite) {
                config.overwrite = false
                File("config/bot.yml").writeText(yaml.encodeToString(config))
            }
        }
    }

    fun getSpotifyApi(userConfig: UserConfig? = null) =
        SpotifyApi.builder()
            .setClientId(clientId)
            .setClientSecret(clientSecret)
            .setRedirectUri(URI(redirectUri))
            .setAccessToken(userConfig?.accessToken)
            .setRefreshToken(userConfig?.refreshToken)
            .build()!!
}
