package io.github.developergr3y.greenhousehelper.util

import net.minecraft.client.Camera
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.ChatComponent
import net.minecraft.client.gui.screens.Screen

/**
 * The only place that knows about Minecraft API differences between versions.
 * The `//?` comments are Stonecutter switches: when building 26.1 it swaps which branch is commented out.
 */
object Compat {
    private val mc get() = Minecraft.getInstance()

    //? if >=26.2 {
    val screen: Screen? get() = mc.gui.screen()
    fun setScreen(screen: Screen?) = mc.gui.setScreen(screen)
    val chat: ChatComponent get() = mc.gui.hud.chat
    val camera: Camera get() = mc.gameRenderer.mainCamera()
    //?} else {
    /*val screen: Screen? get() = mc.screen
    fun setScreen(screen: Screen?) = mc.setScreen(screen)
    val chat: ChatComponent get() = mc.gui.chat
    val camera: Camera get() = mc.gameRenderer.getMainCamera()
    *///?}
}
