package io.github.developergr3y.greenhousehelper

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import io.github.developergr3y.greenhousehelper.config.Layer
import io.github.developergr3y.greenhousehelper.config.ModConfig
import io.github.developergr3y.greenhousehelper.greenhouse.Capture
import io.github.developergr3y.greenhousehelper.greenhouse.Dump
import io.github.developergr3y.greenhousehelper.greenhouse.Greenhouse
import io.github.developergr3y.greenhousehelper.greenhouse.Greenhouses
import io.github.developergr3y.greenhousehelper.greenhouse.Scanner
import io.github.developergr3y.greenhousehelper.hud.HudEditScreen
import io.github.developergr3y.greenhousehelper.hud.LayoutsScreen
import io.github.developergr3y.greenhousehelper.hud.Panels
import io.github.developergr3y.greenhousehelper.layout.Catalog
import io.github.developergr3y.greenhousehelper.layout.Layout
import io.github.developergr3y.greenhousehelper.layout.SavedLayouts
import io.github.developergr3y.greenhousehelper.layout.ShareCodes
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
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
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
            Scanner.tick()
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

        // A share code someone posts in chat gets [Preview] and [Load] buttons after it.
        ClientReceiveMessageEvents.MODIFY_GAME.register { message, overlay ->
            if (overlay) return@register message
            val layout = ShareCodes.PATTERN.find(message.string)?.value?.let(ShareCodes::decode) ?: return@register message
            val id = rememberShared(layout)
            message.copy().append(Component.literal(" "))
                .append(button("§b[Preview]", "/greenhouse shared $id", "Look at it before using it"))
                .append(Component.literal(" "))
                .append(button("§a[Load]", "/greenhouse shared $id use", "Put it on the greenhouse you're in"))
        }

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
        .then(ClientCommands.literal("setup")
            .executes {
                if (!Location.onGarden) chat("§cGo to your Garden first.")
                else if (!Greenhouses.autoSetup()) {
                    chat("§7Couldn't find a 10×10 grid by itself here, so pick its corners.")
                    Greenhouses.startSetup()
                }
                1
            }
            .then(ClientCommands.literal("manual").executes { Greenhouses.startSetup(); 1 }))
        .then(ClientCommands.literal("corner").executes { Greenhouses.pickCorner(lookedAtGround()); 1 })
        .then(ClientCommands.literal("cancel").executes { Greenhouses.cancelSetup(); chat("Setup cancelled."); 1 })
        .then(ClientCommands.literal("list").executes { list(); 1 })
        .then(ClientCommands.literal("remove").executes {
            inGreenhouse()?.let { Greenhouses.remove(it); chat("Forgot the ${it.label()}.") }
            1
        })
        .then(ClientCommands.literal("load").executes { usage("load <skylayouts.io link or share code>", "loads a layout onto the greenhouse you're in"); 1 }.then(ClientCommands.argument("link", StringArgumentType.greedyString()).executes { ctx ->
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
            nextTick = { Compat.setScreen(LayoutsScreen()) }
            1
        })
        .then(ClientCommands.literal("share").executes { inGreenhouse()?.let { g -> g.layout?.let(::share) ?: chat("§cThis greenhouse has no layout.") }; 1 }
            .then(ClientCommands.argument("name", StringArgumentType.greedyString())
                .suggests { _, builder -> SavedLayouts.names().forEach(builder::suggest); builder.buildFuture() }
                .executes { ctx ->
                    val name = StringArgumentType.getString(ctx, "name").trim()
                    SavedLayouts.load(name)?.let(::share) ?: chat("§cNo saved layout called $name.")
                    1
                }))
        .then(ClientCommands.literal("paste").executes { paste(); 1 })
        .then(ClientCommands.literal("shared").then(ClientCommands.argument("id", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
            .executes { ctx -> preview(sharedLayouts[com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "id")]); 1 }
            .then(ClientCommands.literal("use").executes { ctx ->
                sharedLayouts[com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "id")]?.let(::apply) ?: chat("§cThat shared layout has gone (it's only kept until you leave)."); 1
            })))
        .then(ClientCommands.literal("capture").executes { usage("capture <name>", "saves what's planted in this greenhouse as a layout"); 1 }
            .then(ClientCommands.argument("name", StringArgumentType.greedyString()).executes { ctx ->
                capture(StringArgumentType.getString(ctx, "name").trim())
                1
            }))
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
        ShareCodes.PATTERN.find(link)?.let { code ->
            val layout = ShareCodes.decode(code.value)
            if (layout == null) chat("§cThat share code is broken or cut off. If it's long, copy it and use §e/greenhouse paste§c.") else apply(layout)
            return
        }
        val greenhouse = inGreenhouse() ?: return
        val parsed = SkyLayouts.parse(link)
        if (parsed == null) {
            chat("§cThat isn't a skylayouts.io layout link, e.g. https://skylayouts.io/layout?m=ALL_IN_ALOE&i=7 (share links work too)")
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

    private fun shopping(layout: Layout) =
        "Needs: " + layout.shoppingList().entries.sortedByDescending { it.value }
            .joinToString("§7, ") { (plant, n) -> "§f${plant.label} §7×$n" }

    private fun list() {
        val all = Greenhouses.list()
        if (all.isEmpty()) {
            chat("No greenhouses set up yet. Stand in one and run §e/greenhouse setup§r.")
            return
        }
        chat("Your greenhouses:")
        for (g in all) {
            val layout = g.layout
            val progress = if (layout == null || g.planted < 0) "" else buildString {
                append(" §8- §f${g.planted}§7/${g.plantTotal} planted")
                if (g.spotTotal > 0) append(", §f${g.grown}§7/${g.spotTotal} grown")
            }
            chat(" §f${g.label()}§7: ${layout?.name ?: "no layout"}$progress")
        }
    }

    /** Copies the layout's share code. Paste it in chat or Discord; anyone with the mod can load it. */
    private fun share(layout: Layout) {
        val code = ShareCodes.encode(layout)
        Minecraft.getInstance().keyboardHandler.setClipboard(code)
        val copy = Component.literal("§a[Copy again]").withStyle {
            it.withClickEvent(ClickEvent.CopyToClipboard(code)).withHoverEvent(HoverEvent.ShowText(Component.literal(code)))
        }
        val tooLong = if (code.length > 240) " §7It's too long for one chat message, so share it in Discord; they can load it with §e/greenhouse paste§7." else " §7Paste it in chat or Discord."
        Compat.chat.addClientSystemMessage(
            Component.literal("§2[GreenhouseHelper] §rCopied the share code for §f${layout.shortName()}§r (${code.length} characters).$tooLong ").append(copy),
        )
    }

    /** Layouts shared in chat this session, by the number their buttons use. */
    private val sharedLayouts = HashMap<Int, Layout>()

    private fun rememberShared(layout: Layout): Int {
        val id = sharedLayouts.size + 1
        sharedLayouts[id] = layout
        return id
    }

    private fun button(text: String, command: String, hover: String): Component = Component.literal(text).withStyle {
        it.withClickEvent(ClickEvent.RunCommand(command)).withHoverEvent(HoverEvent.ShowText(Component.literal(hover)))
    }

    private fun preview(layout: Layout?) {
        if (layout == null) {
            chat("§cThat shared layout has gone (it's only kept until you leave).")
            return
        }
        nextTick = { Compat.setScreen(LayoutsScreen(layout.copy())) }
    }

    /** A share code from your clipboard, e.g. copied from Discord: shown in the layouts screen to use or save. */
    private fun paste() {
        val text = Minecraft.getInstance().keyboardHandler.clipboard
        val layout = ShareCodes.PATTERN.find(text)?.value?.let(ShareCodes::decode)
        if (layout == null) chat("§cYour clipboard doesn't have a GH1: share code in it.") else preview(layout)
    }

    /** Puts a layout on the greenhouse you're in. */
    private fun apply(layout: Layout) {
        val greenhouse = inGreenhouse() ?: return
        greenhouse.layout = layout.copy()
        Greenhouses.save()
        chat("Loaded §f${layout.shortName()}§r onto the ${greenhouse.label()}. ${shopping(layout)}")
    }

    private fun capture(name: String) {
        val greenhouse = inGreenhouse() ?: return
        if (!SavedLayouts.isValidName(name)) {
            chat("§cNames can use letters, numbers, spaces and _ . - (up to 40).")
            return
        }
        val cells = Scanner.read(greenhouse, 10)
        chat("Reading what's planted…")
        Catalog.get().whenComplete { catalog, error ->
            Minecraft.getInstance().execute {
                if (error != null || catalog == null) {
                    chat("§cCouldn't get the plant list from skylayouts.io: ${(error?.cause ?: error)?.message}")
                    return@execute
                }
                val layout = Capture.build(cells, catalog, 10)
                if (layout.plants.isEmpty()) {
                    chat("§cDidn't recognise anything planted here.")
                    return@execute
                }
                layout.source = "captured from the ${greenhouse.label()}"
                SavedLayouts.save(name, layout)
                val target = layout.spots.firstOrNull()?.let { " for §f${it.label}§r (${layout.spots.size} spots)" } ?: ""
                chat("Saved §f$name§r$target: ${layout.plants.size} plants. ${shopping(layout)}")
            }
        }
    }

    private fun usage(command: String, what: String) = chat("§e/greenhouse $command§r $what.")

    private fun help() {
        chat("Commands (also §e/gh§r):")
        for ((command, what) in listOf(
            "" to "settings",
            "setup" to "set up the greenhouse you're in (finds the grid, or asks for its corners)",
            "setup manual" to "set it up by picking its corners",
            "load <skylayouts.io link>" to "load a layout onto it (share links too)",
            "use <name>" to "put a saved layout on it",
            "save <name>" to "save its layout under a name",
            "capture <name>" to "save what's planted in it as a layout",
            "layouts" to "your layouts: preview, use, rename, rotate, share, delete",
            "share [name]" to "copy a share code for this greenhouse's (or a saved) layout",
            "load <share code>" to "load a code someone shared (or §e/greenhouse paste§7 from your clipboard)",
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
