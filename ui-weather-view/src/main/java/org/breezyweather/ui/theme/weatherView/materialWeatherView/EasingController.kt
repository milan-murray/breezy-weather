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

package org.breezyweather.ui.theme.weatherView.materialWeatherView

import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.round

/**
 * Easing controller for smooth animation deceleration.
 *
 * Uses quartic ease-out function for a satisfying stop.
 * The animation starts at full speed and gradually slows down.
 */
class EasingController {
    companion object {
        /**
         * Quartic ease-out function.
         * t: normalized time (0.0 to 1.0)
         * Returns: eased value (1.0 to 0.0)
         */
        fun easeOutQuartic(t: Double): Double {
            return 1.0 - (1.0 - t).pow(4.0)
        }

        /**
         * Calculate elapsed time ratio.
         * @param elapsedMs Time elapsed since animation started
         * @param totalMs Total animation duration
         * @return Ratio from 0.0 to 1.0
         */
        fun getElapsedRatio(elapsedMs: Long, totalMs: Long): Double {
            if (totalMs <= 0) return 0.0
            return (elapsedMs.toDouble() / totalMs.toDouble()).coerceIn(0.0, 1.0)
        }
    }
}

/**
 * Interval controller with automatic deceleration.
 *
 * When timed animation is enabled:
 * - Starts at full speed
 * - In the last 20% of duration, gradually reduces interval (slows down)
 * - Ends with interval = 0 (stopped)
 */
class TimedIntervalController(
    private val totalDurationMs: Long,
    private val baseIntervalMs: Long,
) {
    private var mStartTime: Long = 0
    private var mLastInterval: Double = 0.0
    private var mIsRunning: Boolean = false

    fun start() {
        mStartTime = System.currentTimeMillis()
        mLastInterval = baseIntervalMs.toDouble()
        mIsRunning = true
    }

    fun reset() {
        mIsRunning = false
        mLastInterval = 0.0
    }

    fun cancel() {
        mIsRunning = false
    }

    /**
     * Get current interval, taking deceleration into account.
     * Returns 0 when animation should stop.
     */
    fun getInterval(): Double {
        if (!mIsRunning) return 0.0

        val elapsed = System.currentTimeMillis() - mStartTime

        if (elapsed >= totalDurationMs) {
            // Animation complete - stop
            mIsRunning = false
            return 0.0
        }

        val ratio = EasingController.getElapsedRatio(elapsed, totalDurationMs)

        // In the last 20% of animation, start decelerating
        val decelerateStart = 0.8
        if (ratio > decelerateStart) {
            // Map [0.8, 1.0] to [0.0, 1.0] for easing
            val easeRatio = (ratio - decelerateStart) / (1.0 - decelerateStart)
            val easeFactor = EasingController.easeOutQuartic(easeRatio)

            // easeOutQuartic returns [1.0, 0.0] but we want [1.0, 0.05]
            // Interpolate: start at 100% speed, end at 5% speed
            val speedRatio = 0.05 + (1.0 - 0.05) * (1.0 - easeFactor)
            return baseIntervalMs * speedRatio
        }

        // Full speed for first 80%
        return baseIntervalMs.toDouble()
    }

    fun isActive(): Boolean = mIsRunning
}
