/*
 * This file is part of Breezy Weather.
 *
 * Breezy Weather is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, version 3 of the License.
 *
 * Breezy Weather is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Breezy Weather. If not, see <https://www.gnu.org/licenses/>.
 */

package org.breezyweather.wallpaper

import android.content.Context
import org.breezyweather.domain.settings.ConfigStore

class LiveWallpaperConfigManager(context: Context) {
    val weatherKind: String
    val dayNightType: String
    val animationsEnabled: Boolean
    val drawInterval: Int
    val resolution: Float
    val sensorsEnabled: Boolean
    val timedAnimationEnabled: Boolean
    val timedAnimationDuration: Int

    init {
        val config = ConfigStore(context, SP_LIVE_WALLPAPER_CONFIG)
        weatherKind = config.getString(KEY_WEATHER_KIND, null) ?: "auto"
        dayNightType = config.getString(KEY_DAY_NIGHT_TYPE, null) ?: "auto"
        animationsEnabled = config.getBoolean(KEY_ANIMATIONS_ENABLED, false)
        drawInterval = config.getInt(KEY_DRAW_INTERVAL, 60)
        resolution = config.getFloat(KEY_RESOLUTION, 1.0f)
        sensorsEnabled = config.getBoolean(KEY_SENSORS_ENABLED, true)
        timedAnimationEnabled = config.getBoolean(KEY_TIMED_ANIMATION_ENABLED, false)
        timedAnimationDuration = config.getInt(KEY_TIMED_ANIMATION_DURATION, 5)
    }

    companion object {
        private const val SP_LIVE_WALLPAPER_CONFIG = "live_wallpaper_config"
        private const val KEY_WEATHER_KIND = "weather_kind"
        private const val KEY_DAY_NIGHT_TYPE = "day_night_type"
        private const val KEY_ANIMATIONS_ENABLED = "animations_enabled"
        private const val KEY_DRAW_INTERVAL = "draw_interval"
        private const val KEY_RESOLUTION = "resolution"
        private const val KEY_SENSORS_ENABLED = "sensors_enabled"
        private const val KEY_TIMED_ANIMATION_ENABLED = "timed_animation_enabled"
        private const val KEY_TIMED_ANIMATION_DURATION = "timed_animation_duration"

        fun update(
            context: Context,
            weatherKind: String?,
            dayNightType: String?,
            animationsEnabled: Boolean,
            drawInterval: Int = 60,
            resolution: Float = 1.0f,
            sensorsEnabled: Boolean = true,
            timedAnimationEnabled: Boolean = false,
            timedAnimationDuration: Int = 5
        ) {
            ConfigStore(context, SP_LIVE_WALLPAPER_CONFIG)
                .edit()
                .putString(KEY_WEATHER_KIND, weatherKind)
                .putString(KEY_DAY_NIGHT_TYPE, dayNightType)
                .putBoolean(KEY_ANIMATIONS_ENABLED, animationsEnabled)
                .putInt(KEY_DRAW_INTERVAL, drawInterval)
                .putFloat(KEY_RESOLUTION, resolution)
                .putBoolean(KEY_SENSORS_ENABLED, sensorsEnabled)
                .putBoolean(KEY_TIMED_ANIMATION_ENABLED, timedAnimationEnabled)
                .putInt(KEY_TIMED_ANIMATION_DURATION, timedAnimationDuration)
                .apply()
        }
    }
}
