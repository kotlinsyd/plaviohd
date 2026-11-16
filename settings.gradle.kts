rootProject.name = "CloudstreamPlugins"

// This file sets what projects are included.
// All new projects should get automatically included unless specified in the "disabled" variable.

// "PlavioHD" is the main all-in-one plugin AND the generator's template
// (scripts/generate_source_plugins.py copies it into one module per source).
val disabled = listOf<String>()

File(rootDir, ".").eachDir { dir ->
    if (!disabled.contains(dir.name) && File(dir, "build.gradle.kts").exists()) {
        include(dir.name)
    }
}

fun File.eachDir(block: (File) -> Unit) {
    listFiles()?.filter { it.isDirectory }?.forEach { block(it) }
}

// To only include a single project, comment out the previous lines (except the first one), and include your plugin like so:
// include("PluginName")
