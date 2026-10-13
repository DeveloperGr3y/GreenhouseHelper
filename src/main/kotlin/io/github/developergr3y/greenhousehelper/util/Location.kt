package io.github.developergr3y.greenhousehelper.util

import net.minecraft.client.Minecraft
import net.minecraft.world.scores.DisplaySlot

/**
 * Whether you're in SkyBlock, and on your Garden. Hypixel's location packet when we have it (exact), otherwise the
 * scoreboard title and the tab list's "Area: Garden" line, checked for a little while after joining a world.
 */
object Location {
    private val areaLine = Regex("^\\s*Area: (.+?)\\s*$")
    private const val CHECK_INTERVAL_MS = 1_000L
    private const val SEARCH_MS = 20_000L

    private var sidebarSkyBlock = false
    private var tabArea: String? = null
    private var lastCheck = 0L
    private var lastLevel: Any? = null
    private var searchUntil = 0L

    val onSkyBlock get() = HypixelLocation.serverType?.let { it == "SKYBLOCK" } ?: sidebarSkyBlock

    /** e.g. "Garden", "Hub"; null if unknown. */
    val area: String? get() = if (HypixelLocation.serverType != null) HypixelLocation.map else tabArea

    /** On a Garden. Without the location packet, an unknown area counts as yes (tab widgets can be turned off). */
    val onGarden: Boolean
        get() {
            if (!onSkyBlock) return false
            if (HypixelLocation.serverType != null) return HypixelLocation.map == "Garden"
            return tabArea?.let { it == "Garden" } ?: true
        }

    fun tick(client: Minecraft) {
        val now = System.currentTimeMillis()
        if (client.level !== lastLevel) {
            lastLevel = client.level
            sidebarSkyBlock = false
            tabArea = null
            searchUntil = now + SEARCH_MS
        }
        if (now - lastCheck < CHECK_INTERVAL_MS) return
        if ((sidebarSkyBlock && tabArea != null) || now > searchUntil) return
        lastCheck = now

        val title = client.level?.scoreboard?.getDisplayObjective(DisplaySlot.SIDEBAR)?.displayName?.string
        sidebarSkyBlock = title?.stripFormatting()?.uppercase()?.contains("SKYBLOCK") == true

        val players = client.connection?.listedOnlinePlayers
        tabArea = if (!sidebarSkyBlock || players == null) {
            null
        } else {
            players.firstNotNullOfOrNull { info ->
                val line = info.tabListDisplayName?.string?.stripFormatting() ?: return@firstNotNullOfOrNull null
                areaLine.find(line)?.groupValues?.get(1)
            }
        }
    }
}
