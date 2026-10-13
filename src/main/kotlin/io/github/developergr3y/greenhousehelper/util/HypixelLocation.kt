package io.github.developergr3y.greenhousehelper.util

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.loader.api.FabricLoader
import net.hypixel.modapi.HypixelModAPI
import net.hypixel.modapi.packet.impl.clientbound.event.ClientboundLocationPacket

/**
 * Where Hypixel says you are, from the location packet it sends on every server change: server type "SKYBLOCK",
 * map "Dwarven Mines", "Dungeon"... This comes from the Hypixel Mod API mod, Hypixel's official way for mods to get
 * it; we only listen. Without that mod everything stays null and [Location] falls back to the tab list.
 */
object HypixelLocation {
    /** e.g. "SKYBLOCK", "MAIN"; null until the first packet (or without the Hypixel Mod API mod). */
    @Volatile
    var serverType: String? = null
        private set

    /** e.g. "Dwarven Mines", "Crystal Hollows", "Dungeon"; null outside SkyBlock or until the first packet. */
    @Volatile
    var map: String? = null
        private set

    fun register() {
        if (!FabricLoader.getInstance().isModLoaded("hypixel-mod-api")) return
        ModApi.register()
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            serverType = null
            map = null
        }
    }

    /** Separate so the Mod API's classes are only loaded when the mod is installed. */
    private object ModApi {
        fun register() {
            val api = HypixelModAPI.getInstance()
            api.subscribeToEventPacket(ClientboundLocationPacket::class.java)
            api.createHandler(ClientboundLocationPacket::class.java) { packet ->
                serverType = packet.serverType.map { it.name() }.orElse("")
                map = packet.map.orElse(null)
            }
        }
    }
}
