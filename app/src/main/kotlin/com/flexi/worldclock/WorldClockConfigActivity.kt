package com.flexi.worldclock

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import kotlin.math.roundToInt

/** Configuration screen of the flexible world clock widget. */
class WorldClockConfigActivity : Activity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var confirmed = false
    private lateinit var settings: WidgetPrefs.WorldClockSettings
    private lateinit var zoneList: LinearLayout
    private lateinit var zonesHeader: TextView
    private var previewHost: FrameLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        appWidgetId = readAppWidgetId(intent)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContentView(R.layout.activity_world_clock_config)
        Ui.applyStatusBarInset(findViewById(R.id.screen_root))
        settings = WidgetPrefs.loadWorld(this, appWidgetId)

        previewHost = findViewById(R.id.preview_host)
        zoneList = findViewById(R.id.zone_list)
        zonesHeader = findViewById(R.id.zones_header)

        bindFont(R.id.city_font, settings.city.family) { family -> settings.city.family = family }
        bindFont(R.id.time_font, settings.time.family) { family -> settings.time.family = family }

        Ui.Slider.sp(
            this,
            findViewById<SeekBar>(R.id.city_size),
            findViewById<TextView>(R.id.city_size_value),
            MIN_CITY_SP,
            MAX_CITY_SP,
            settings.city.sizeSp.roundToInt(),
        ) { value ->
            settings.city.sizeSp = value.toFloat()
            renderPreview()
        }
        Ui.Slider.sp(
            this,
            findViewById<SeekBar>(R.id.time_size),
            findViewById<TextView>(R.id.time_size_value),
            MIN_TIME_SP,
            MAX_TIME_SP,
            settings.time.sizeSp.roundToInt(),
        ) { value ->
            settings.time.sizeSp = value.toFloat()
            renderPreview()
        }
        Ui.Slider.sp(
            this,
            findViewById<SeekBar>(R.id.offset_size),
            findViewById<TextView>(R.id.offset_size_value),
            MIN_OFFSET_SP,
            MAX_OFFSET_SP,
            settings.offset.sizeSp.roundToInt(),
        ) { value ->
            settings.offset.sizeSp = value.toFloat()
            renderPreview()
        }
        Ui.Slider.dp(
            this,
            findViewById<SeekBar>(R.id.spacing),
            findViewById<TextView>(R.id.spacing_value),
            0,
            MAX_SPACING_DP,
            settings.spacingDp.roundToInt(),
        ) { value ->
            settings.spacingDp = value.toFloat()
            renderPreview()
        }
        Ui.Slider.cells(
            this,
            findViewById<SeekBar>(R.id.width),
            findViewById<TextView>(R.id.width_value),
            WidgetPrefs.WorldClockSettings.MIN_WIDTH_CELLS,
            WidgetPrefs.WorldClockSettings.MAX_WIDTH_CELLS,
            settings.widthCells,
        ) { value ->
            settings.widthCells = value
            renderPreview()
        }

        val showOffset = findViewById<CheckBox>(R.id.show_offset)
        showOffset.isChecked = settings.showOffset
        showOffset.setOnCheckedChangeListener { _, checked ->
            settings.showOffset = checked
            findViewById<View>(R.id.offset_size).isEnabled = checked
            findViewById<View>(R.id.offset_size_value).alpha = if (checked) 1f else 0.4f
            renderPreview()
        }
        findViewById<View>(R.id.offset_size).isEnabled = settings.showOffset
        findViewById<View>(R.id.offset_size_value).alpha = if (settings.showOffset) 1f else 0.4f

        findViewById<View>(R.id.add_zone).setOnClickListener { showPicker(null) }
        findViewById<View>(R.id.save).setOnClickListener { save() }
        findViewById<View>(R.id.cancel).setOnClickListener { finish() }

        Ui.bindBackground(window.decorView, settings.background) { background ->
            settings.background = background
            renderPreview()
        }

        DragReorder.attach(zoneList, settings.zones, ::onOrderChanged)
        renderZoneList()
        renderPreview()
    }

    private fun bindFont(spinnerId: Int, current: String, onFamily: (String) -> Unit) {
        val spinner = findViewById<Spinner>(spinnerId)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, Fonts.displayNames())
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        spinner.setSelection(Fonts.indexOf(current))
        spinner.onItemSelectedListener = object : Ui.OnItemSelected() {
            override fun onSelected(position: Int) {
                onFamily(Fonts.familyAt(position))
                renderPreview()
            }
        }
    }

    /** Called live while the user drags a row, and once when the drag ends. */
    private fun onOrderChanged() {
        // The dragged row's own view must not be rebuilt mid-gesture, so only the labels of the
        // already-swapped rows and the widget preview are refreshed.
        refreshZoneLabels()
        renderPreview()
    }

    private fun renderZoneList() {
        zoneList.removeAllViews()
        if (settings.zones.isEmpty()) {
            zoneList.addView(
                TextView(this).apply {
                    setText(R.string.no_zones)
                    setTextColor(getColor(R.color.text_secondary))
                    textSize = 13f
                    setPadding(0, dp(12f), 0, dp(12f))
                },
            )
        }

        val now = System.currentTimeMillis()
        settings.zones.indices.forEach { index -> zoneList.addView(buildZoneRow(index, now)) }
        updateZonesHeader()
        findViewById<View>(R.id.add_zone).isEnabled =
            settings.zones.size < WidgetPrefs.WorldClockSettings.MAX_ZONES
    }

    /** Creates one row; the drag handle needs the index, so rows are built here. */
    private fun buildZoneRow(index: Int, now: Long): View {
        val zone = settings.zones[index]
        val row = LayoutInflater.from(this).inflate(R.layout.item_zone_row, zoneList, false)

        row.findViewById<TextView>(R.id.zone_city_label).text = zone.label
        row.findViewById<TextView>(R.id.zone_meta_label).text = zoneMeta(zone, now)

        row.findViewById<View>(R.id.zone_pick).setOnClickListener { showPicker(index) }
        row.findViewById<View>(R.id.zone_remove).setOnClickListener {
            settings.zones.removeAt(index)
            renderZoneList()
            renderPreview()
        }
        DragReorder.markHandle(row.findViewById(R.id.zone_handle), index)
        return row
    }

    /** After a drag the rows keep their views but must show the new order's data. */
    private fun refreshZoneLabels() {
        val now = System.currentTimeMillis()
        var i = 0
        while (i < zoneList.childCount && i < settings.zones.size) {
            val row = zoneList.getChildAt(i)
            val zone = settings.zones[i]
            row.findViewById<TextView>(R.id.zone_city_label).text = zone.label
            row.findViewById<TextView>(R.id.zone_meta_label).text = zoneMeta(zone, now)
            DragReorder.markHandle(row.findViewById(R.id.zone_handle), i)
            i++
        }
        updateZonesHeader()
    }

    private fun zoneMeta(zone: WidgetPrefs.ZoneEntry, now: Long): String =
        "${zone.zoneId}  ·  ${ClockFormats.utcLabel(zone.zoneId, now)}"

    private fun updateZonesHeader() {
        zonesHeader.text = getString(
            R.string.zones,
            settings.zones.size,
            WidgetPrefs.WorldClockSettings.MAX_ZONES,
        )
    }

    /** [replaceIndex] of `null` appends a new zone, otherwise replaces that zone. */
    private fun showPicker(replaceIndex: Int?) {
        if (replaceIndex == null &&
            settings.zones.size >= WidgetPrefs.WorldClockSettings.MAX_ZONES
        ) {
            Toast.makeText(
                this,
                getString(
                    R.string.zone_limit_reached,
                    WidgetPrefs.WorldClockSettings.MAX_ZONES,
                ),
                Toast.LENGTH_SHORT,
            ).show()
            return
        }

        val content = LayoutInflater.from(this).inflate(R.layout.dialog_zone_picker, null)
        val search = content.findViewById<EditText>(R.id.zone_search)
        val results = content.findViewById<ListView>(R.id.zone_results)

        val emptyView = TextView(this).apply {
            setText(R.string.no_search_results)
            setTextColor(getColor(R.color.text_secondary))
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, dp(24f), 0, dp(24f))
        }
        (results.parent as ViewGroup).addView(emptyView)
        results.emptyView = emptyView

        val adapter = ZoneAdapter(this, zoneChoices())
        results.adapter = adapter

        val dialog = AlertDialog.Builder(this, R.style.PickerDialog)
            .setCustomTitle(
                titleView(
                    if (replaceIndex == null) {
                        R.string.add_timezone_title
                    } else {
                        R.string.change_timezone_title
                    },
                ),
            )
            .setView(content)
            .setNegativeButton(R.string.cancel, null)
            .create()

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) =
                Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.update(ZoneCatalog.search(s?.toString()))
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })

        results.setOnItemClickListener { _, _, position, _ ->
            val zone = adapter.getItem(position) ?: return@setOnItemClickListener
            val entry = WidgetPrefs.ZoneEntry(zone.label, zone.id)
            if (replaceIndex == null) {
                settings.zones.add(entry)
            } else {
                settings.zones[replaceIndex] = entry
            }
            renderZoneList()
            renderPreview()
            dialog.dismiss()
        }

        dialog.show()
    }

    /** Catalogue entries plus the device's own zone when it is not part of the catalogue. */
    private fun zoneChoices(): List<ZoneCatalog.Zone> {
        val zones = ZoneCatalog.all().toMutableList()
        val deviceZone = ClockFormats.deviceTimeZoneId()
        if (zones.any { it.id == deviceZone }) {
            return zones
        }
        if (ClockFormats.isValidZone(deviceZone)) {
            zones.add(0, ZoneCatalog.Zone("Local", deviceZone))
        }
        return zones
    }

    private fun renderPreview() {
        val host = previewHost ?: return
        val widthDp = settings.widthCells * WorldClockWidgetViews.CELL_DP
        PreviewRenderer.show(
            host,
            WorldClockWidgetViews.build(
                this,
                settings,
                AppWidgetManager.INVALID_APPWIDGET_ID,
                PreviewRenderer.options(widthDp, PREVIEW_HEIGHT_DP),
            ),
            widthDp,
        )
    }

    private fun save() {
        WidgetPrefs.saveWorld(this, appWidgetId, settings)
        WorldClockWidgetViews.updateNow(this, appWidgetId)
        // The rows are ordered as configured, so the home screen reflects the drag result at once.
        confirmed = true
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        finish()
    }

    override fun finish() {
        if (!confirmed) {
            setResult(RESULT_CANCELED)
        }
        super.finish()
    }

    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).roundToInt()

    private fun titleView(textRes: Int): View =
        (LayoutInflater.from(this).inflate(R.layout.dialog_title, null) as TextView)
            .apply { setText(textRes) }

    /** Timezone picker list: city label on top, zone id and UTC offset underneath. */
    private class ZoneAdapter(
        private val context: Context,
        zones: List<ZoneCatalog.Zone>,
    ) : BaseAdapter() {

        private var zones: List<ZoneCatalog.Zone> = ArrayList(zones)
        private val now = System.currentTimeMillis()

        fun update(updated: List<ZoneCatalog.Zone>) {
            zones = ArrayList(updated)
            notifyDataSetChanged()
        }

        override fun getCount(): Int = zones.size

        override fun getItem(position: Int): ZoneCatalog.Zone? =
            if (position in zones.indices) zones[position] else null

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = convertView ?: LayoutInflater.from(context)
                .inflate(android.R.layout.simple_list_item_2, parent, false)
            val zone = zones[position]
            // Resolved here rather than inside the apply blocks below, where an unqualified
            // `context` would bind to the row's own View#getContext() instead of this adapter's.
            val titleColor = context.getColor(R.color.text_primary)
            val subtitleColor = context.getColor(R.color.text_secondary)
            row.findViewById<TextView>(android.R.id.text1).apply {
                text = zone.label
                setTextColor(titleColor)
            }
            row.findViewById<TextView>(android.R.id.text2).apply {
                text = "${zone.id}  ·  ${ClockFormats.utcLabel(zone.id, now)}"
                setTextColor(subtitleColor)
            }
            return row
        }
    }

    companion object {
        private const val MIN_CITY_SP = 6
        private const val MAX_CITY_SP = 30
        private const val MIN_TIME_SP = 8
        private const val MAX_TIME_SP = 48
        private const val MIN_OFFSET_SP = 6
        private const val MAX_OFFSET_SP = 20
        private const val MAX_SPACING_DP = 48

        /** Height the single-row widget occupies on a launcher grid, used for the preview. */
        private const val PREVIEW_HEIGHT_DP = 56f

        fun readAppWidgetId(intent: Intent?): Int = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
    }
}
