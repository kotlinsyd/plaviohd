package com.kotlinsyd.plaviohd

import android.content.Context
import android.content.SharedPreferences

/**
 * Tiny persistent store for the bridge's one setting: the PlavioHD addon
 * base URL. Defaults to the public default-config addon; a user can paste
 * their own configured link (their /u/… link, without /manifest.json) in
 * the plugin settings to make the bridge answer with their configuration.
 */
object PlavioSettings {
    private const val PREFS_NAME = "plaviohd_bridge"
    private const val KEY_ADDON_URL = "addon_base_url"

    const val DEFAULT_ADDON_URL = "https://hd.plavio.site"

    @Volatile
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    /** Addon base URL, never with a trailing slash. Falls back to default. */
    val addonBaseUrl: String
        get() {
            val saved = prefs?.getString(KEY_ADDON_URL, null)
                ?.trim()?.trimEnd('/')
            return if (saved != null && saved.startsWith("http")) saved else DEFAULT_ADDON_URL
        }

    fun setAddonBaseUrl(context: Context, url: String) {
        init(context)
        val cleaned = url.trim().trimEnd('/').removeSuffix("/manifest.json")
        prefs?.edit()?.putString(KEY_ADDON_URL, cleaned)?.apply()
    }
}
