package com.kotlinsyd.plaviohd

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class PlavioHDPlugin : Plugin() {
    private var activity: AppCompatActivity? = null

    override fun load(context: Context) {
        activity = context as? AppCompatActivity

        PlavioSettings.init(context)

        // All providers should be added in this manner
        registerMainAPI(PlavioHDProvider())

        openSettings = {
            val frag = SettingsFragment(this)
            activity?.let {
                frag.show(it.supportFragmentManager, "PlavioHDSettings")
            }
        }
    }
}
