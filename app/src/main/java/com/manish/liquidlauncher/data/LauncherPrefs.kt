package com.manish.liquidlauncher.data

import android.content.Context
import com.manish.liquidlauncher.model.CustomFolder
import com.manish.liquidlauncher.model.IconShapeStyle
import com.manish.liquidlauncher.model.LiquidThemeConfig
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/** SharedPreferences wrapper: theme, wallpaper, dock, hidden apps, folders, focus, usage, backup. */
class LauncherPrefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("liquid_launcher_prefs", Context.MODE_PRIVATE)

    fun loadTheme(): LiquidThemeConfig {
        val d = LiquidThemeConfig()
        val shapes = IconShapeStyle.values()
        return LiquidThemeConfig(
            accentHue = sp.getFloat("hue", d.accentHue),
            secondaryHue = sp.getFloat("hue2", d.secondaryHue),
            refractionOpacity = sp.getFloat("density", d.refractionOpacity),
            glowIntensity = sp.getFloat("glow", d.glowIntensity),
            cornerRadiusDp = sp.getFloat("radius", d.cornerRadiusDp),
            iconScale = sp.getFloat("iconScale", d.iconScale),
            liveAnimations = sp.getBoolean("live", d.liveAnimations),
            bubbles = sp.getBoolean("bubbles", d.bubbles),
            iconBob = sp.getBoolean("bob", d.iconBob),
            tiltEffects = sp.getBoolean("tilt", d.tiltEffects),
            showLabels = sp.getBoolean("labels", d.showLabels),
            haptics = sp.getBoolean("haptics", d.haptics),
            blurDrawer = sp.getBoolean("blur", d.blurDrawer),
            iconShape = shapes.getOrElse(sp.getInt("shape", d.iconShape.ordinal)) { d.iconShape },
            sounds = sp.getBoolean("sounds", d.sounds),
            badges = sp.getBoolean("badges", d.badges)
        )
    }

    fun saveTheme(c: LiquidThemeConfig) {
        sp.edit()
            .putFloat("hue", c.accentHue)
            .putFloat("hue2", c.secondaryHue)
            .putFloat("density", c.refractionOpacity)
            .putFloat("glow", c.glowIntensity)
            .putFloat("radius", c.cornerRadiusDp)
            .putFloat("iconScale", c.iconScale)
            .putBoolean("live", c.liveAnimations)
            .putBoolean("bubbles", c.bubbles)
            .putBoolean("bob", c.iconBob)
            .putBoolean("tilt", c.tiltEffects)
            .putBoolean("labels", c.showLabels)
            .putBoolean("haptics", c.haptics)
            .putBoolean("blur", c.blurDrawer)
            .putInt("shape", c.iconShape.ordinal)
            .putBoolean("sounds", c.sounds)
            .putBoolean("badges", c.badges)
            .apply()
    }

    var wallpaperUri: String?
        get() = sp.getString("wallpaper", null)
        set(value) {
            sp.edit().putString("wallpaper", value).apply()
        }

    var focusMode: Boolean
        get() = sp.getBoolean("focus", false)
        set(value) {
            sp.edit().putBoolean("focus", value).apply()
        }

    /** null = never customised (use smart defaults). */
    fun loadDock(): List<String>? {
        val raw = sp.getString("dock", null) ?: return null
        return raw.split(",").filter { it.isNotBlank() }
    }

    fun saveDock(packages: List<String>) {
        sp.edit().putString("dock", packages.joinToString(",")).apply()
    }

    fun loadHidden(): Set<String> = sp.getStringSet("hidden", emptySet())?.toSet() ?: emptySet()

    fun saveHidden(hidden: Set<String>) {
        sp.edit().putStringSet("hidden", HashSet(hidden)).apply()
    }

    fun loadDistracting(): Set<String> = sp.getStringSet("distracting", emptySet())?.toSet() ?: emptySet()

    fun saveDistracting(set: Set<String>) {
        sp.edit().putStringSet("distracting", HashSet(set)).apply()
    }

    // ---- Custom folders (stored as JSON) ---------------------------------------------------
    fun loadFolders(): List<CustomFolder> {
        val raw = sp.getString("folders", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val pk = o.getJSONArray("apps")
                CustomFolder(o.getString("name"), (0 until pk.length()).map { pk.getString(it) })
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveFolders(folders: List<CustomFolder>) {
        val arr = JSONArray()
        folders.forEach { f ->
            arr.put(JSONObject().put("name", f.name).put("apps", JSONArray(f.packages)))
        }
        sp.edit().putString("folders", arr.toString()).apply()
    }

    // ---- Usage (overall + time-of-day buckets for smart suggestions) -----------------------
    fun currentBucket(): Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY) / 6

    fun launchCounts(): Map<String, Int> {
        val out = HashMap<String, Int>()
        for ((key, value) in sp.all) {
            if (key.startsWith("launch_") && value is Int) {
                out[key.removePrefix("launch_")] = value
            }
        }
        return out
    }

    fun bucketCounts(bucket: Int): Map<String, Int> {
        val prefix = "tb_${bucket}_"
        val out = HashMap<String, Int>()
        for ((key, value) in sp.all) {
            if (key.startsWith(prefix) && value is Int) {
                out[key.removePrefix(prefix)] = value
            }
        }
        return out
    }

    fun bumpLaunch(packageName: String) {
        val key = "launch_$packageName"
        val bucketKey = "tb_${currentBucket()}_$packageName"
        sp.edit()
            .putInt(key, sp.getInt(key, 0) + 1)
            .putInt(bucketKey, sp.getInt(bucketKey, 0) + 1)
            .apply()
    }

    // ---- Backup / restore (everything except the wallpaper URI) ----------------------------
    fun exportJson(): String {
        val root = JSONObject()
        for ((k, v) in sp.all) {
            if (k == "wallpaper") continue
            val o = JSONObject()
            when (v) {
                is Boolean -> o.put("t", "b").put("v", v)
                is Int -> o.put("t", "i").put("v", v)
                is Float -> o.put("t", "f").put("v", v.toDouble())
                is String -> o.put("t", "s").put("v", v)
                is Set<*> -> o.put("t", "ss").put("v", JSONArray(v.map { it.toString() }))
                else -> continue
            }
            root.put(k, o)
        }
        return root.toString()
    }

    fun importJson(json: String): Boolean {
        return try {
            val root = JSONObject(json)
            val edit = sp.edit()
            val keys = root.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val o = root.getJSONObject(k)
                when (o.getString("t")) {
                    "b" -> edit.putBoolean(k, o.getBoolean("v"))
                    "i" -> edit.putInt(k, o.getInt("v"))
                    "f" -> edit.putFloat(k, o.getDouble("v").toFloat())
                    "s" -> edit.putString(k, o.getString("v"))
                    "ss" -> {
                        val arr = o.getJSONArray("v")
                        edit.putStringSet(k, (0 until arr.length()).map { arr.getString(it) }.toSet())
                    }
                }
            }
            edit.apply()
            true
        } catch (_: Exception) {
            false
        }
    }
}
