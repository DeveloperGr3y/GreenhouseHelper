package io.github.developergr3y.greenhousehelper.layout

import com.google.gson.GsonBuilder
import io.github.developergr3y.greenhousehelper.GreenhouseHelper
import java.io.File

/**
 * Layouts you've saved under a name, one file each in config/greenhousehelper/layouts/<name>.json. They never leave
 * your computer, so a private layout stays private.
 */
object SavedLayouts {
    private val gson = GsonBuilder().excludeFieldsWithoutExposeAnnotation().setPrettyPrinting().create()
    private val folder get() = File(GreenhouseHelper.configDir, "layouts")
    private val validName = Regex("[A-Za-z0-9 _.-]{1,40}")

    fun isValidName(name: String) = validName.matches(name) && !name.startsWith(".")

    fun names(): List<String> =
        folder.listFiles { f -> f.isFile && f.name.endsWith(".json") }?.map { it.name.removeSuffix(".json") }?.sorted().orEmpty()

    fun save(name: String, layout: Layout) {
        require(isValidName(name)) { "names can use letters, numbers, spaces and _ . -" }
        folder.mkdirs()
        val copy = gson.fromJson(gson.toJson(layout), Layout::class.java).also { it.name = name }
        File(folder, "$name.json").writeText(gson.toJson(copy))
    }

    fun load(name: String): Layout? {
        if (!isValidName(name)) return null
        val file = File(folder, "$name.json")
        if (!file.isFile) return null
        return runCatching { gson.fromJson(file.readText(), Layout::class.java) }.getOrNull()
    }

    fun delete(name: String) = isValidName(name) && File(folder, "$name.json").delete()
}
