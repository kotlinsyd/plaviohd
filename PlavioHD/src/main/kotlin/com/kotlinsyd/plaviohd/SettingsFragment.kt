package com.kotlinsyd.plaviohd

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Settings sheet with a single field: the PlavioHD addon URL.
 * Follows the official template's pattern of resolving plugin resources
 * by name through the plugin's own Resources object.
 */
class SettingsFragment(private val plugin: PlavioHDPlugin) : BottomSheetDialogFragment() {

    @SuppressLint("DiscouragedApi")
    private fun resId(name: String, type: String): Int =
        plugin.resources?.getIdentifier(name, type, BuildConfig.LIBRARY_PACKAGE_NAME) ?: 0

    @SuppressLint("DiscouragedApi")
    private fun getString(name: String): String? {
        val id = resId(name, "string")
        return if (id == 0) null else plugin.resources?.getString(id)
    }

    @SuppressLint("DiscouragedApi")
    private fun <T : View> View.findViewByName(name: String): T? {
        val id = resId(name, "id")
        return if (id == 0) null else findViewById(id)
    }

    @SuppressLint("DiscouragedApi")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout from this plugin's resources, as in the template.
        val layoutId = resId("fragment_plaviohd_settings", "layout")
        if (layoutId == 0) return null
        val parser = plugin.resources?.getLayout(layoutId) ?: return null
        return inflater.inflate(parser, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val title: TextView? = view.findViewByName("settingsTitle")
        val hint: TextView? = view.findViewByName("settingsHint")
        val edit: EditText? = view.findViewByName("editAddonUrl")
        val save: Button? = view.findViewByName("buttonSave")

        // Set texts from plugin resources as well, like the template does.
        getString("settings_title")?.let { title?.text = it }
        getString("settings_hint")?.let { hint?.text = it }
        getString("settings_save")?.let { save?.text = it }
        getString("settings_edit_hint")?.let { edit?.hint = it }

        edit?.setText(PlavioSettings.addonBaseUrl)

        save?.setOnClickListener {
            val ctx = context ?: return@setOnClickListener
            PlavioSettings.setAddonBaseUrl(ctx, edit?.text?.toString().orEmpty())
            dismiss()
        }
    }
}
