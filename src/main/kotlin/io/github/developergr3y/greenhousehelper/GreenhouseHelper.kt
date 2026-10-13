package io.github.developergr3y.greenhousehelper

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import io.github.developergr3y.greenhousehelper.config.Layer
import io.github.developergr3y.greenhousehelper.config.ModConfig
import io.github.developergr3y.greenhousehelper.greenhouse.Dump
import io.github.developergr3y.greenhousehelper.greenhouse.Greenhouse
import io.github.developergr3y.greenhousehelper.greenhouse.Greenhouses
import io.github.developergr3y.greenhousehelper.hud.HudEditScreen
import io.github.developergr3y.greenhousehelper.hud.Panels
import io.github.developergr3y.greenhousehelper.layout.SavedLayouts
import io.github.developergr3y.greenhousehelper.layout.SkyLayouts
import io.github.developergr3y.greenhousehelper.render.LayoutRender
import io.github.developergr3y.greenhousehelper.util.Compat
import io.github.developergr3y.greenhousehelper.util.HypixelLocation
import io.github.developergr3y.greenhousehelper.util.Location
import io.github.developergr3y.greenhousehelper.util.UpdateCheck
import io.github.notenoughupdates.moulconfig.managed.ManagedConfig
import io.github.notenoughupdates.moulconfig.platform.MoulConfigScreenComponent
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.resources.Identifier
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import org.slf4j.LoggerFactory
import java.io.File

object GreenhouseHelper : ClientModInitializer {
    const val MOD_ID = "greenhousehelper"

    val logger = LoggerFactory.getLogger("GreenhouseHelper")
    val configDir = File(FabricLoader.getInstance().configDir.toFile(), MOD_ID)
    val version: String = FabricLoader.getInstance().getModContainer(MOD_ID)
        .map { it.metadata.version.friendlyString }.orElse("dev")

    private lateinit var managedConfig: ManagedConfig<ModConfig>
    val config: ModConfig get() = managedConfig.instance

    /** Opening a screen straight from a chat command gets undone when the chat closes, so wait a tick. */
    private var nextTick: (() -> Unit)? = null

    override fun onInitializeClient() {
        configDir.mkdirs()
        managedConfig = ManagedConfig.create(File(configDir, "config.json"), ModConfig::class.java) {
            checkExpose = false
        }
        HypixelLocation.register()
        Greenhouses.register()

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            nextTick?.let {
                nextTick = null
                it()
            }
            Location.tick(client)
            UpdateCheck.tick()
        }

        // MoulConfig's openConfigGui() doesn't pass on the close event it saves on, so save when our screen closes.
        ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
            if (screen is MoulConfigScreenComponent) ScreenEvents.remove(screen).register { saveConfig() }
        }

        LevelRenderEvents.COLLECT_SUBMITS.register { ctx -> LayoutRender.renderWorld(ctx.poseStack(), ctx.submitNodeCollector()) }
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath(MOD_ID, "labels"), LayoutRender::renderLabels)
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath(MOD_ID, "panels"), Panels::render)
        Panels.register()

        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(command("greenhouse"))
            dispatcher.register(command("gh"))
        }
    }

    private fun command(name: String): LiteralArgumentBuilder<FabricClientCommandSource> = ClientCommands.literal(name)
        .executes {
            nextTick = { managedConfig.openConfigGui() }
            1
        }
        .then(ClientCommands.literal("help").executes { help(); 1 })
        .then(ClientCommands.literal("gui").executes { openPanelEditor(); 1 })
        .then(ClientCommands.literal("dump").executes { dump(); 1 })
        .then(ClientCommands.literal("setup").executes { Greenhouses.startSetup(); 1 })
        .then(ClientCommands.literal("corner").executes { Greenhouses.pickCorner(lookedAtGround()); 1 })
        .then(ClientCommands.literal("cancel").executes { Greenhouses.cancelSetup(); chat("Setup cancelled."); 1 })
        .then(ClientCommands.literal("list").executes { list(); 1 })
        .then(ClientCommands.literal("remove").executes {
            inGreenhouse()?.let { Greenhouses.remove(it); chat("Forgot the ${it.label()}.") }
            1
        })
        .then(ClientCommands.literal("load").executes { usage("load <skylayouts.io link>", "loads a layout onto the greenhouse you're in"); 1 }.then(ClientCommands.argument("link", StringArgumentType.greedyString()).executes { ctx ->
            load(StringArgumentType.getString(ctx, "link"))
            1
        }))
        .then(ClientCommands.literal("save").executes { usage("save <name>", "saves this greenhouse's layout under a name"); 1 }.then(ClientCommands.argument("name", StringArgumentType.greedyString()).executes { ctx ->
            save(StringArgumentType.getString(ctx, "name").trim())
            1
        }))
        .then(ClientCommands.literal("use").executes { usage("use <name>", "puts a saved layout on the greenhouse you're in"); 1 }.then(ClientCommands.argument("name", StringArgumentType.greedyString())
            .suggests { _, builder -> SavedLayouts.names().forEach(builder::suggest); builder.buildFuture() }
            .executes { ctx ->
                use(StringArgumentType.getString(ctx, "name").trim())
                1
            }))
        .then(ClientCommands.literal("layouts").executes {
            val names = SavedLayouts.names()
            chat(if (names.isEmpty()) "No saved layouts yet. §e/greenhouse save <name>§r saves the one you're using." else "Saved layouts: §f${names.joinToString("§7, §f")}")
            1
        })
        .then(ClientCommands.literal("delete").executes { usage("delete <name>", "deletes a saved layout. To take the layout off this greenhouse, use §e/greenhouse clear§r"); 1 }.then(ClientCommands.argument("name", StringArgumentType.greedyString())
            .suggests { _, builder -> SavedLayouts.names().forEach(builder::suggest); builder.buildFuture() }
            .executes { ctx ->
                val name = StringArgumentType.getString(ctx, "name").trim()
                chat(if (SavedLayouts.delete(name)) "Deleted §f$name§r." else "§cNo saved layout called $name.")
                1
            }))
        .then(ClientCommands.literal("clear").executes {
            inGreenhouse()?.let { it.layout = null; Greenhouses.save(); chat("Cleared the ${it.label()}'s layout.") }
            1
        })
        .then(ClientCommands.literal("rotate").executes {
            inGreenhouse()?.let {
                it.rotation = (it.rotation + 1) and 3
                Greenhouses.save()
                chat("Turned the layout a quarter turn (${it.rotation * 90}°).")
            }
            1
        })
        .then(ClientCommands.literal("layer").executes { usage("layer <floor|crops|both>", "which layers to show"); 1 }.then(ClientCommands.argument("layer", StringArgumentType.word())
            .suggests { _, builder -> Layer.entries.forEach { builder.suggest(it.name.lowercase()) }; builder.buildFuture() }
            .executes { ctx ->
                val layer = Layer.entries.firstOrNull { it.name.equals(StringArgumentType.getString(ctx, "layer"), true) }
                if (layer == null) {
                    chat("§cUse floor, crops or both.")
                } else {
                    config.overlay.layer = layer
                    saveConfig()
                    chat("Showing: §f$layer")
                }
                1
            }))

    /** The ground block under your crosshair: if you're looking at a plant, the block it sits on. */
    private fun lookedAtGround(): BlockPos? {
        val mc = Minecraft.getInstance()
        val hit = mc.hitResult as? BlockHitResult ?: return null
        if (hit.type != HitResult.Type.BLOCK) return null
        val level = mc.level ?: return null
        val pos = hit.blockPos
        return if (level.getBlockState(pos).getCollisionShape(level, pos).isEmpty) pos.below() else pos
    }

    private fun inGreenhouse(): Greenhouse? =
        Greenhouses.current() ?: run {
            chat("§cStand in a greenhouse you've set up first (§e/greenhouse setup§c).")
            null
        }

    private fun load(link: String) {
        val greenhouse = inGreenhouse() ?: return
        val parsed = SkyLayouts.parse(link)
        if (parsed == null) {
            chat("§cThat isn't a skylayouts.io layout link, e.g. https://skylayouts.io/layout.html?m=ALL_IN_ALOE&i=7")
            return
        }
        chat("Loading from skylayouts.io…")
        SkyLayouts.load(parsed, link.trim()).whenComplete { layout, error ->
            Minecraft.getInstance().execute {
                if (error != null || layout == null) {
                    val cause = (error?.cause ?: error)?.message ?: "no layout"
                    chat("§cCouldn't load it: $cause")
                    return@execute
                }
                greenhouse.layout = layout
                Greenhouses.save()
                chat("Loaded §f${layout.name}§r. ${shopping(layout)}")
            }
        }
    }

    private fun save(name: String) {
        val layout = inGreenhouse()?.layout ?: run {
            chat("§cThis greenhouse has no layout to save.")
            return
        }
        if (!SavedLayouts.isValidName(name)) {
            chat("§cNames can use letters, numbers, spaces and _ . - (up to 40).")
            return
        }
        SavedLayouts.save(name, layout)
        chat("Saved as §f$name§r. Load it anywhere with §e/greenhouse use $name§r.")
    }

    private fun use(name: String) {
        val greenhouse = inGreenhouse() ?: return
        val layout = SavedLayouts.load(name) ?: run {
            chat("§cNo saved layout called $name. §e/greenhouse layouts§c lists them.")
            return
        }
        greenhouse.layout = layout
        Greenhouses.save()
        chat("Using §f${layout.name}§r. ${shopping(layout)}")
    }

    private fun shopping(layout: io.github.developergr3y.greenhousehelper.layout.Layout) =
        "Needs: " + layout.shoppingList().entries.sortedByDescending { it.value }
            .joinToString("§7, ") { (plant, n) -> "§f${plant.label} §7×$n" }

    private fun list() {
        val all = Greenhouses.list()
        if (all.isEmpty()) {
            chat("No greenhouses set up yet. Stand in one and run §e/greenhouse setup§r.")
            return
        }
        chat("Your greenhouses:")
        for (g in all) chat(" §f${g.label()}§7: ${g.layout?.name ?: "no layout"}")
    }

    private fun usage(command: String, what: String) = chat("§e/greenhouse $command§r $what.")

    private fun help() {
        chat("Commands (also §e/gh§r):")
        for ((command, what) in listOf(
            "" to "settings",
            "setup" to "set up the greenhouse you're in (pick its corners)",
            "load <skylayouts.io link>" to "load a layout onto it",
            "use <name>" to "put a saved layout on it",
            "save <name>" to "save its layout under a name",
            "layouts" to "list your saved layouts",
            "delete <name>" to "delete a saved layout",
            "clear" to "take the layout off this greenhouse",
            "layer <floor|crops|both>" to "which layers to show",
            "rotate" to "turn the layout a quarter turn",
            "list" to "your greenhouses and their layouts",
            "remove" to "forget the greenhouse you're in",
            "gui" to "move the shopping list",
            "dump" to "write what's in each cell to a file (to teach the mod your plants)",
        )) chat(" §e/greenhouse $command§7 - $what")
    }

    private fun dump() {
        val greenhouse = inGreenhouse() ?: return
        runCatching { Dump.write(greenhouse) }
            .onSuccess { chat("Wrote §f${it.name}§r to §fconfig/greenhousehelper/dumps/§r.") }
            .onFailure { chat("§cCouldn't write the dump: ${it.message}"); logger.warn("Dump failed", it) }
    }

    fun openPanelEditor() {
        nextTick = { Compat.setScreen(HudEditScreen()) }
    }

    fun resetPanels() {
        for (panel in Panels.all) panel.position = panel.defaultPosition()
        saveConfig()
    }

    /** "[Set corner]": runs /greenhouse corner, which this mod handles itself (it never reaches Hypixel). */
    fun cornerButton() {
        val button = Component.literal("§a§l[Set corner]").withStyle {
            it.withClickEvent(ClickEvent.RunCommand("/greenhouse corner"))
                .withHoverEvent(HoverEvent.ShowText(Component.literal("Use the block you're looking at")))
        }
        Compat.chat.addClientSystemMessage(Component.literal("§2[GreenhouseHelper] ").append(button)
            .append(Component.literal(" §7or §c[Cancel]").withStyle { it.withClickEvent(ClickEvent.RunCommand("/greenhouse cancel")) }))
    }

    fun saveConfig() = managedConfig.saveToFile()

    fun chat(message: String) {
        Compat.chat.addClientSystemMessage(Component.literal("§2[GreenhouseHelper] §r$message"))
    }
}
