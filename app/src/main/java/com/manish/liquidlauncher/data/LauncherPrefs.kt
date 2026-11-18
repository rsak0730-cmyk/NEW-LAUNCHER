package com.manish.liquidlauncher.data

import android.content.Context
import com.manish.liquidlauncher.model.LiquidThemeConfig

/** Tiny SharedPreferences wrapper so theme, wallpaper, dock, hidden apps and usage survive restarts. */
class LauncherPrefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("liquid_launcher_prefs", Context.MODE_PRIVATE)

    fun loadTheme(): LiquidThemeConfig {
        val d = LiquidThemeConfig()
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
            blurDrawer = sp.getBoolean("blur", d.blurDrawer)
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
            .apply()
    }

    var wallpaperUri: String?
        get() = sp.getString("wallpaper", null)
        set(value) {
            sp.edit().putString("wallpaper", value).apply()
        }

    /** null = never customised (use smart defaults). */
    fun loadDock(): List<String>? {
        val raw = sp.getString("dock", null) ?: return null
        return raw.split(",").filter { it.isNotBlank() }
    }

    fun saveDock(packages: List<String>) {
        sp.edit().putString("dock", packages.joinToString(",")).apply()
    }


    /** Saved user order for apps shown on home pages. Missing packages are ignored on rebuild. */
    fun loadHomeOrder(): List<String> = sp.getString("home_order", null)
        ?.split(",")
        ?.filter { it.isNotBlank() }
        ?: emptyList()

    fun saveHomeOrder(packages: List<String>) {
        sp.edit().putString("home_order", packages.distinct().joinToString(",")).apply()
    }

    fun loadHidden(): Set<String> = sp.getStringSet("hidden", emptySet())?.toSet() ?: emptySet()

    fun saveHidden(hidden: Set<String>) {
        sp.edit().putStringSet("hidden", HashSet(hidden)).apply()
    }

    fun launchCounts(): Map<String, Int> {
        val out = HashMap<String, Int>()
        for ((key, value) in sp.all) {
            if (key.startsWith("launch_") && value is Int) {
                out[key.removePrefix("launch_")] = value
            }
        }
        return out
    }

    fun bumpLaunch(packageName: String) {
        val key = "launch_$packageName"
        sp.edit().putInt(key, sp.getInt(key, 0) + 1).apply()
    }
}
