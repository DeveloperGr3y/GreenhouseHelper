package io.github.developergr3y.greenhousehelper.util

import com.google.gson.JsonParser
import io.github.developergr3y.greenhousehelper.GreenhouseHelper
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Once per game, the first time you're in SkyBlock: asks GitHub for the latest release and, if it's newer than this
 * one, says so in chat with a link to it. Only reads the public release list; nothing about you is sent.
 */
object UpdateCheck {
    private const val REPO = "DeveloperGr3y/GreenhouseHelper"
    private val api = URI.create("https://api.github.com/repos/$REPO/releases/latest")

    private var checked = false

    fun tick() {
        if (checked || !Location.onSkyBlock || !GreenhouseHelper.config.general.updateNotice) return
        checked = true
        val request = HttpRequest.newBuilder(api)
            .header("User-Agent", "GreenhouseHelper/${GreenhouseHelper.version}")
            .header("Accept", "application/vnd.github+json")
            .timeout(Duration.ofSeconds(10))
            .build()
        HttpClient.newHttpClient().sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenAccept { response ->
                if (response.statusCode() != 200) return@thenAccept
                val release = JsonParser.parseString(response.body()).asJsonObject
                val latest = release.get("tag_name")?.asString?.removePrefix("v") ?: return@thenAccept
                val page = release.get("html_url")?.asString ?: "https://github.com/$REPO/releases"
                if (compare(numbers(latest), numbers(GreenhouseHelper.version)) > 0) {
                    Minecraft.getInstance().execute { announce(base(latest), page) }
                }
            }
            .exceptionally { null } // offline, or GitHub unreachable: say nothing
    }

    private fun announce(latest: String, page: String) {
        val link = Component.literal("§b§n[Download]").withStyle {
            it.withClickEvent(ClickEvent.OpenUrl(URI.create(page)))
                .withHoverEvent(HoverEvent.ShowText(Component.literal("Open the release on GitHub")))
        }
        val message = Component.literal("§b[GreenhouseHelper] §7Version §a$latest§7 is out (you have §f${base(GreenhouseHelper.version)}§7). ")
            .append(link)
        Compat.chat.addClientSystemMessage(message)
    }

    /** "0.5.0+mc26.1" -> "0.5.0" */
    private fun base(version: String) = version.substringBefore('+')

    /** "0.5.0+mc26.1" -> [0, 5, 0] */
    private fun numbers(version: String) = base(version).split('.', '-').mapNotNull { it.toIntOrNull() }

    private fun compare(a: List<Int>, b: List<Int>): Int {
        for (i in 0 until maxOf(a.size, b.size)) {
            val c = a.getOrElse(i) { 0 }.compareTo(b.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        return 0
    }
}
