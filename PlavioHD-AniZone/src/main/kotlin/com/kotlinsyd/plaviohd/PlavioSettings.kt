package com.kotlinsyd.plaviohd

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64

/**
 * Persistent store for the plugin's settings.
 *
 * Two ways to drive the addon, never mixed:
 *  1. The user's own configured PlavioHD link (their /u/… link), saved
 *     verbatim — when present it decides everything, exactly like the
 *     configure page does.
 *  2. The in-app pickers (sources + qualities). On save the plugin builds
 *     the same kind of configured link the configure page produces — a
 *     /c/<token> link whose token is base64url JSON with a `providers`
 *     array and a `qset` quality allowlist — entirely on the phone.
 *     Nothing about the addon itself changes either way.
 */
object PlavioSettings {
    private const val PREFS_NAME = "plaviohd_bridge"
    private const val KEY_ADDON_URL = "addon_base_url"
    private const val KEY_SOURCES = "picker_sources"
    private const val KEY_QUALITIES = "picker_qualities"

    const val DEFAULT_ADDON_URL = "https://hd.plavio.site"

    /**
     * Single-source builds (the per-source plugins) bake their one source
     * here; the all-in-one plugin leaves it null and uses the pickers.
     * scripts/generate_source_plugins.py rewrites this one line per plugin.
     */
    val BAKED_SOURCE: String? = "AniZone" // GENERATED-BAKED-SOURCE

    /** Every source the addon offers, in the configure page's order. */
    val ALL_SOURCES: List<String> = listOf(
        "4KHDHub", "Cinejoy", "Cinezo", "Dexter", "HDHub4u", "KmMovies",
        "Mapple", "MovieBox", "Movies4u", "MoviesBazar", "MoviesDrive",
        "MoviesHunt", "Movy", "NetMirror", "Nxsha", "SpaceDom",
        "VegaMovies", "VidFast", "Vidrock",
        "AniKage", "AniZone", "Castle", "CineCalidad", "Dulo",
        "Einthusan", "HackStore", "HDMovie4", "KissKH", "LaMovie",
        "MalluMV", "MaxVOD", "MovieLinkBD", "MoviesMod", "MultiMovies",
        "Obejrzyj", "OndaSpanish", "PremiumSmart", "SoraStream",
        "TamilBlasters", "ToonWorld4All", "UHDMovies", "VAplayer",
        "VidBolt", "Vidlink", "VidNest", "Way2Movies"
    )

    /** The addon's default source set — the pickers' starting state. */
    val DEFAULT_SOURCES: Set<String> = setOf(
        "4KHDHub", "Cinejoy", "Cinezo", "Dexter", "HDHub4u", "KmMovies",
        "Mapple", "MovieBox", "Movies4u", "MoviesBazar", "MoviesDrive",
        "MoviesHunt", "Movy", "NetMirror", "Nxsha", "SpaceDom",
        "VegaMovies", "VidFast", "Vidrock"
    )

    /** Quality heights the addon can allow, best first. */
    val ALL_QUALITIES: List<Int> = listOf(2160, 1080, 720, 480, 360)

    @Volatile
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    /** The user's own saved link, exactly as saved ("" when none). */
    val savedAddonUrl: String
        get() = prefs?.getString(KEY_ADDON_URL, null)?.trim().orEmpty()

    /** Addon base URL of the saved link, never with a trailing slash. */
    val addonBaseUrl: String
        get() {
            val saved = savedAddonUrl.trimEnd('/')
            return if (saved.startsWith("http")) saved else DEFAULT_ADDON_URL
        }

    fun setAddonBaseUrl(context: Context, url: String) {
        init(context)
        val cleaned = url.trim().trimEnd('/').removeSuffix("/manifest.json")
        prefs?.edit()?.putString(KEY_ADDON_URL, cleaned)?.apply()
    }

    /** Sources ticked in the picker; the default set when never saved. */
    val selectedSources: Set<String>
        get() {
            val csv = prefs?.getString(KEY_SOURCES, null) ?: return DEFAULT_SOURCES
            val picked = csv.split(",").map { it.trim() }.filter { it in ALL_SOURCES }.toSet()
            return picked
        }

    /** Quality heights ticked in the picker; all of them when never saved. */
    val selectedQualities: Set<Int>
        get() {
            val csv = prefs?.getString(KEY_QUALITIES, null) ?: return ALL_QUALITIES.toSet()
            val picked = csv.split(",").mapNotNull { it.trim().toIntOrNull() }
                .filter { it in ALL_QUALITIES }.toSet()
            return if (picked.isEmpty()) ALL_QUALITIES.toSet() else picked
        }

    fun savePicker(context: Context, sources: Set<String>, qualities: Set<Int>) {
        init(context)
        val srcCsv = ALL_SOURCES.filter { it in sources }.joinToString(",")
        val qCsv = ALL_QUALITIES.filter { it in qualities }.joinToString(",")
        prefs?.edit()
            ?.putString(KEY_SOURCES, srcCsv)
            ?.putString(KEY_QUALITIES, qCsv)
            ?.apply()
    }

    /**
     * Qualities only. The per-source plugins save through this so their
     * settings sheet can never rewrite the shared source-picker state the
     * all-in-one plugin relies on.
     */
    fun saveQualities(context: Context, qualities: Set<Int>) {
        init(context)
        val qCsv = ALL_QUALITIES.filter { it in qualities }.joinToString(",")
        prefs?.edit()?.putString(KEY_QUALITIES, qCsv)?.apply()
    }

    /** A /c/<token> configured link for the given sources and qualities. */
    private fun configuredUrl(srcs: List<String>, quals: List<Int>): String {
        val providersJson = srcs.joinToString(",") { "\"$it\"" }
        val qsetPart = if (quals.size == ALL_QUALITIES.size) {
            ""
        } else {
            ",\"qset\":[${quals.joinToString(",")}]"
        }
        val json = "{\"providers\":[$providersJson]$qsetPart}"
        val token = Base64.encodeToString(
            json.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )
        return "$DEFAULT_ADDON_URL/c/$token"
    }

    /**
     * The addon base every stream fetch uses: the user's own link when one
     * is saved; otherwise, in a per-source plugin, a /c/<token> link naming
     * only the baked source; otherwise the plain default link when the
     * pickers sit at their defaults; otherwise a /c/<token> link built
     * from the pickers.
     */
    val effectiveBaseUrl: String
        get() {
            val raw = savedAddonUrl.trim().trimEnd('/')
                .removeSuffix("/manifest.json").trimEnd('/')
            if (raw.startsWith("http")) return raw

            val quals = ALL_QUALITIES.filter { it in selectedQualities }

            val baked = BAKED_SOURCE
            if (baked != null) return configuredUrl(listOf(baked), quals)

            val srcs = ALL_SOURCES.filter { it in selectedSources }
            if (srcs.isEmpty()) return DEFAULT_ADDON_URL
            if (srcs.toSet() == DEFAULT_SOURCES && quals.size == ALL_QUALITIES.size) {
                return DEFAULT_ADDON_URL
            }

            return configuredUrl(srcs, quals)
        }
}
