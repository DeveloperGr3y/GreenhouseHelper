package io.github.developergr3y.greenhousehelper.config

import com.google.gson.annotations.Expose
import io.github.developergr3y.greenhousehelper.GreenhouseHelper
import io.github.developergr3y.greenhousehelper.hud.HudPosition
import io.github.notenoughupdates.moulconfig.Config
import io.github.notenoughupdates.moulconfig.annotations.Category
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDropdown
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorInfoText
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.common.text.StructuredText

/** Everything shown in /greenhouse. @Category = a tab on the left, @ConfigOption = one setting. */
class ModConfig : Config() {
    override fun getTitle(): StructuredText = StructuredText.of("GreenhouseHelper ${GreenhouseHelper.version} §7by §bGr3y_VVolf")

    override fun saveNow() = GreenhouseHelper.saveConfig()

    @Expose
    @JvmField
    @Category(name = "General", desc = "Getting started, and update notices.")
    var general = GeneralConfig()

    @Expose
    @JvmField
    @Category(name = "Overlay", desc = "The layout drawn on your greenhouse: the floor, then the crops.")
    var overlay = OverlayConfig()

    @Expose
    @JvmField
    @Category(name = "Progress", desc = "What's planted already, what's wrong, and how the mutations are doing.")
    var progress = ProgressConfig()

    @Expose
    @JvmField
    @Category(name = "Plant Codes", desc = "Short codes (e.g. MJLB) floating above each plant.")
    var labels = LabelsConfig()

    @Expose
    @JvmField
    @Category(name = "Shopping List", desc = "A panel with what the layout needs.")
    var shopping = ShoppingConfig()
}

enum class Layer(private val label: String) {
    FLOOR("Floor"),
    CROPS("Crops"),
    BOTH("Both"),
    ;

    override fun toString() = label
}

class GeneralConfig {
    @Transient
    @JvmField
    @ConfigOption(
        name = "Getting started",
        desc = "In a greenhouse, run §e/greenhouse setup§7 and pick the grid's top-left and bottom-right ground blocks. " +
            "Then §e/greenhouse load <skylayouts.io link>§7, or §e/greenhouse use <name>§7 for one you've saved. " +
            "§e/greenhouse help§7 lists every command.",
    )
    @ConfigEditorInfoText(infoTitle = "Info")
    var info = false

    @Expose
    @JvmField
    @ConfigOption(name = "Update Notice", desc = "Say in chat when a newer version is out on GitHub (checked once per game).")
    @ConfigEditorBoolean
    var updateNotice = true
}

class OverlayConfig {
    @Expose
    @JvmField
    @ConfigOption(name = "Enabled", desc = "Show the layout in your greenhouse.")
    @ConfigEditorBoolean
    var enabled = true

    @Expose
    @JvmField
    @ConfigOption(name = "Layer", desc = "The floor (the ground block each cell needs), the crops, or both. Also: §e/greenhouse layer§7.")
    @ConfigEditorDropdown
    var layer = Layer.BOTH

    @Expose
    @JvmField
    @ConfigOption(name = "Floor Opacity", desc = "How strong the floor tiles are, in percent.")
    @ConfigEditorSlider(minValue = 10f, maxValue = 90f, minStep = 5f)
    var floorOpacity = 45f

    @Expose
    @JvmField
    @ConfigOption(name = "Crop Opacity", desc = "How strong the crop boxes are, in percent.")
    @ConfigEditorSlider(minValue = 10f, maxValue = 90f, minStep = 5f)
    var cropOpacity = 30f
}

class ProgressConfig {
    @Expose
    @JvmField
    @ConfigOption(name = "Only Show What's Left", desc = "Hide plants that are already in the right place, ground that's already right, and mutations that have grown.")
    @ConfigEditorBoolean
    var hideDone = true

    @Expose
    @JvmField
    @ConfigOption(name = "Mark Mistakes", desc = "Show the wrong plant in a cell in red, and outline ground that's the wrong block.")
    @ConfigEditorBoolean
    var markWrong = true

    @Expose
    @JvmField
    @ConfigOption(name = "Highlight What You're Holding", desc = "Holding a plant's seeds brightens the cells it still needs to go in and fades the rest.")
    @ConfigEditorBoolean
    var highlightHeld = true

    @Expose
    @JvmField
    @ConfigOption(name = "Ring Status", desc = "A green post on mutation spots whose whole ring is planted, and how much of each ring is done above the spot.")
    @ConfigEditorBoolean
    var ringStatus = true

    @Expose
    @JvmField
    @ConfigOption(name = "Growth", desc = "Show a grown mutation's stage above it, and a gold post when it's ready to harvest.")
    @ConfigEditorBoolean
    var growth = true
}

class LabelsConfig {
    @Expose
    @JvmField
    @ConfigOption(name = "Show Codes", desc = "Show each plant's short code (e.g. MJLB) above it.")
    @ConfigEditorBoolean
    var enabled = true

    @Expose
    @JvmField
    @ConfigOption(name = "Distance", desc = "Only show codes within this many blocks.")
    @ConfigEditorSlider(minValue = 4f, maxValue = 24f, minStep = 1f)
    var distance = 12f
}

class ShoppingConfig {
    @Expose
    @JvmField
    @ConfigOption(name = "Show Panel", desc = "While you're in a greenhouse with a layout: every plant it needs, and how many.")
    @ConfigEditorBoolean
    var enabled = true

    @Expose
    @JvmField
    @ConfigOption(name = "Count Down", desc = "Show how many of each plant are still missing, rather than the layout's totals. Green when they're all in.")
    @ConfigEditorBoolean
    var countDown = true

    @Expose
    @JvmField
    @ConfigOption(name = "Progress Lines", desc = "Lines under the list: plants in place, rings complete, mutations grown or ready, and mistakes.")
    @ConfigEditorBoolean
    var progress = true

    @Expose
    @JvmField
    @ConfigOption(name = "Panel Background", desc = "Draw a dark background behind the panel.")
    @ConfigEditorBoolean
    var background = true

    @Transient
    @JvmField
    @ConfigOption(name = "Move Panel", desc = "Drag it to move it, scroll to resize. Also: §e/greenhouse gui")
    @ConfigEditorButton(buttonText = "Edit")
    val edit = Runnable { GreenhouseHelper.openPanelEditor() }

    @Expose @JvmField var position = HudPosition(x = 5, y = 80)
}
