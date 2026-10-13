package io.github.developergr3y.greenhousehelper.config

import com.google.gson.annotations.Expose
import io.github.developergr3y.greenhousehelper.GreenhouseHelper
import io.github.notenoughupdates.moulconfig.Config
import io.github.notenoughupdates.moulconfig.annotations.Category
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDropdown
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorInfoText
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.common.text.StructuredText

/** Everything shown in /greenhouse. */
class ModConfig : Config() {
    override fun getTitle(): StructuredText = StructuredText.of("GreenhouseHelper ${GreenhouseHelper.version} §7by §bGr3y_VVolf")

    override fun saveNow() = GreenhouseHelper.saveConfig()

    @Expose
    @JvmField
    @Category(name = "General", desc = "The layout overlay on your greenhouse.")
    var general = GeneralConfig()
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
            "Then §e/greenhouse load <skylayouts.io link>§7, or §e/greenhouse use <name>§7 for one you've saved.",
    )
    @ConfigEditorInfoText(infoTitle = "Info")
    var info = false

    @Expose
    @JvmField
    @ConfigOption(name = "Enabled", desc = "Show the layout in your greenhouse.")
    @ConfigEditorBoolean
    var enabled = true

    @Expose
    @JvmField
    @ConfigOption(name = "Layer", desc = "The floor (watered or not), the crops, or both. Also: §e/greenhouse layer§7.")
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

    @Expose
    @JvmField
    @ConfigOption(name = "Plant Codes", desc = "Show each plant's short code (e.g. MJLB) above it.")
    @ConfigEditorBoolean
    var labels = true

    @Expose
    @JvmField
    @ConfigOption(name = "Code Distance", desc = "Only show codes within this many blocks.")
    @ConfigEditorSlider(minValue = 4f, maxValue = 24f, minStep = 1f)
    var labelDistance = 12f

    @Expose
    @JvmField
    @ConfigOption(name = "Update Notice", desc = "Say in chat when a newer version is out on GitHub (checked once per game).")
    @ConfigEditorBoolean
    var updateNotice = true
}
