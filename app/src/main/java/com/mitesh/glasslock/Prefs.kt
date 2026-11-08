package com.mitesh.glasslock

import android.content.Context
import android.content.SharedPreferences

class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("glass_lock", Context.MODE_PRIVATE)

    /** Whether the glass lock screen should appear. */
    var enabled: Boolean
        get() = sp.getBoolean("enabled", false)
        set(value) = sp.edit().putBoolean("enabled", value).apply()

    /** Show notification text while locked (off = app name only). */
    var showContent: Boolean
        get() = sp.getBoolean("show_content", true)
        set(value) = sp.edit().putBoolean("show_content", value).apply()

    /**
     * Prepare the lock screen as the display turns off, so it is already there on wake.
     * Off = show it as the display turns on (keeps the Always-on display undisturbed).
     */
    var launchOnScreenOff: Boolean
        get() = sp.getBoolean("launch_on_screen_off", true)
        set(value) = sp.edit().putBoolean("launch_on_screen_off", value).apply()

    /** Bumped whenever the wallpaper changes so caches refresh. */
    var wallpaperVersion: Int
        get() = sp.getInt("wallpaper_version", 0)
        set(value) = sp.edit().putInt("wallpaper_version", value).apply()
}
