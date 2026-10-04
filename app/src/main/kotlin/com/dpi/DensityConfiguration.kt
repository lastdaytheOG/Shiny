package com.dpi

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.util.Log
import com.shiny.music.ui.liquid.appearance.effectiveDensityScale
import timber.log.Timber
import kotlin.math.roundToInt


internal class DensityConfiguration(
    private val requestedScale: Float
) : ActivityLifecycleManager() {

    private var originalDensityDpi: Int = 0

    /**
     * The factor actually applied. An enlarged size is capped on small screens so the
     * interface never ends up too narrow or too short for the player and the tab bar;
     * shrinking is applied as asked.
     */
    private var densityScale: Float = requestedScale


    @SuppressLint("LogNotTimber")
    fun applyDensityScaling(context: Context) {
        if (requestedScale == 1.0f) return

        try {
            val resources = context.resources
            val config = Configuration(resources.configuration)
            densityScale = effectiveDensityScale(
                scale = requestedScale,
                smallestWidthDp = config.smallestScreenWidthDp,
                longSideDp = maxOf(config.screenWidthDp, config.screenHeightDp),
            )
            if (densityScale == 1.0f) return
            onCreate()
            originalDensityDpi = config.densityDpi
            updateDensityDpi(config, resources)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to apply configuration", e)
        }
    }


    private fun updateDensityDpi(config: Configuration, resources: Resources) {
        val newDensityDpi = (originalDensityDpi * densityScale).roundToInt()
        config.densityDpi = newDensityDpi
        Timber.tag(TAG).i("Updated densityDpi to: $newDensityDpi")
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)
    }


    override fun onActivityCreated(activity: Activity) {
        applyDensityToActivity(activity)
    }


    override fun onActivityResumed(activity: Activity) {
        applyDensityToActivity(activity)
    }


    override fun onActivityStarted(activity: Activity) {
        applyDensityToActivity(activity)
    }


    private fun applyDensityToActivity(activity: Activity) {
        try {
            updateDensityDpi(activity.resources.configuration, activity.resources)
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to update density for activity")
        }
    }

    companion object {
        private val TAG = DensityConfiguration::class.java.simpleName
    }
}
