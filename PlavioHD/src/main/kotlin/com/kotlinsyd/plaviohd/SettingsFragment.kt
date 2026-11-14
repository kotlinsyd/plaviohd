package com.kotlinsyd.plaviohd

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Settings sheet: the user's own PlavioHD link (optional), plus in-app
 * pickers for sources and qualities so nobody has to visit the addon's
 * configure page just to change them. A filled link field always wins;
 * clear it and the pickers decide.
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
        val ctx = context ?: return
        PlavioSettings.init(ctx)

        val title: TextView? = view.findViewByName("settingsTitle")
        val hint: TextView? = view.findViewByName("settingsHint")
        val edit: EditText? = view.findViewByName("editAddonUrl")
        val save: Button? = view.findViewByName("buttonSave")
        val count: TextView? = view.findViewByName("sourcesCount")
        val boxDefaults: LinearLayout? = view.findViewByName("containerDefaultSources")
        val boxMore: LinearLayout? = view.findViewByName("containerMoreSources")
        val btnAll: Button? = view.findViewByName("buttonAll")
        val btnDefaults: Button? = view.findViewByName("buttonDefaults")
        val btnNone: Button? = view.findViewByName("buttonNone")

        getString("settings_title")?.let { title?.text = it }
        getString("settings_hint")?.let { hint?.text = it }
        getString("settings_save")?.let { save?.text = it }
        getString("settings_edit_hint")?.let { edit?.hint = it }

        // Empty when no link is saved, so the field itself shows which
        // mode is active: filled = the link rules, empty = pickers rule.
        edit?.setText(PlavioSettings.savedAddonUrl)

        val sourceBoxes = LinkedHashMap<String, CheckBox>()
        fun updateCount() {
            val on = sourceBoxes.values.count { it.isChecked }
            count?.text = "$on of ${PlavioSettings.ALL_SOURCES.size} on"
        }
        fun addSourceBox(container: LinearLayout?, name: String, checked: Boolean) {
            if (container == null) return
            val box = CheckBox(ctx).apply {
                text = name
                isChecked = checked
                textSize = 16f
                setPadding(0, 6, 0, 6)
                setOnCheckedChangeListener { _, _ -> updateCount() }
            }
            container.addView(box)
            sourceBoxes[name] = box
        }
        val picked = PlavioSettings.selectedSources
        for (name in PlavioSettings.ALL_SOURCES) {
            val isDefault = name in PlavioSettings.DEFAULT_SOURCES
            addSourceBox(if (isDefault) boxDefaults else boxMore, name, name in picked)
        }
        updateCount()

        btnAll?.setOnClickListener {
            sourceBoxes.values.forEach { it.isChecked = true }
            updateCount()
        }
        btnDefaults?.setOnClickListener {
            sourceBoxes.forEach { (name, box) ->
                box.isChecked = name in PlavioSettings.DEFAULT_SOURCES
            }
            updateCount()
        }
        btnNone?.setOnClickListener {
            sourceBoxes.values.forEach { it.isChecked = false }
            updateCount()
        }

        val qualityBoxes = listOf(
            2160 to (view.findViewByName("quality2160") as? CheckBox),
            1080 to (view.findViewByName("quality1080") as? CheckBox),
            720 to (view.findViewByName("quality720") as? CheckBox),
            480 to (view.findViewByName("quality480") as? CheckBox),
            360 to (view.findViewByName("quality360") as? CheckBox)
        )
        val pickedQualities = PlavioSettings.selectedQualities
        qualityBoxes.forEach { (height, box) -> box?.isChecked = height in pickedQualities }

        save?.setOnClickListener {
            PlavioSettings.setAddonBaseUrl(ctx, edit?.text?.toString().orEmpty())
            val sources = sourceBoxes.filterValues { it.isChecked }.keys
            val qualities = qualityBoxes.filter { it.second?.isChecked == true }
                .map { it.first }.toSet()
            PlavioSettings.savePicker(ctx, sources, qualities)
            dismiss()
        }
    }
}
