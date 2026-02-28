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

import kotlin.math.pow

/**
 * Easing controller for smooth animation deceleration.
 *
 * Uses quartic ease-out function for a satisfying stop.
 * The animation starts at full speed and gradually slows down to a complete stop.
 */
class EasingController {
    companion object {
        /**
         * Quartic ease-out function.
         * t: normalized time (0.0 to 1.0)
         * Returns: eased value (0.0 to 1.0)
         *
         * This creates smooth deceleration where:
         * - At t=0: value=0 (start)
         * - At t=1: value=1 (complete, with zero velocity)
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

        /**
         * Get speed ratio given elapsed time ratio.
         * @param elapsedMs Time elapsed since animation started
         * @param totalMs Total animation duration
         * @return Speed ratio from 1.0 (full speed) to 0.0 (stopped)
         *
         * Uses ease-out quartic to smoothly reduce speed to zero.
         * At t=1, speedRatio=0 ensuring animation comes to complete stop.
         */
        fun getSpeedRatio(elapsedMs: Long, totalMs: Long): Double {
            if (totalMs <= 0 || elapsedMs >= totalMs) return 0.0
            val ratio = (elapsedMs.toDouble() / totalMs.toDouble()).coerceIn(0.0, 1.0)
            // easeOutQuartic gives [0,1] where slope approaches 0 at t=1
            // We use (1 - easeOutQuartic) to get speed ratio [1,0]
            return 1.0 - easeOutQuartic(ratio)
        }
    }
}

/**
 * Interval controller with automatic deceleration to complete stop.
 *
 * When timed animation is enabled:
 * - Starts at full speed (base interval)
 * - Gradually increases interval (slows down) over the animation duration
 * - Ends with interval = 0 (complete stop) to save battery
 *
 * The easing uses ease-out quartic to ensure:
 * - Smooth visual deceleration
 * - Zero velocity at the end (no jump/rebound)
 * - Complete stop after duration elapses
 */
class TimedIntervalController(
    private val totalDurationMs: Long,
    private val baseIntervalMs: Long,
) {
    private var mStartTime: Long = 0
    private var mIsRunning: Boolean = false

    fun start() {
        mStartTime = System.currentTimeMillis()
        mIsRunning = true
    }

    fun reset() {
        mIsRunning = false
    }

    fun cancel() {
        mIsRunning = false
    }

    /**
     * Get current interval, taking deceleration into account.
     * Returns 0 when animation should stop.
     *
     * During animation:
     * - At start (ratio=0): interval = baseInterval (full speed)
     * - During middle: interval gradually increases (slows down)
     * - At end (ratio=1): interval = baseInterval / 0.05 = baseInterval * 20
     * - After duration: interval = 0 (complete stop)
     *
     * The easing ensures smooth transition with zero velocity at the end,
     * preventing any rebound or jump when the animation stops.
     */
    fun getInterval(): Double {
        if (!mIsRunning) return 0.0

        val elapsed = System.currentTimeMillis() - mStartTime

        if (elapsed >= totalDurationMs) {
            // Animation complete - return 0 to stop completely
            mIsRunning = false
            return 0.0
        }

        // Get the speed ratio - starts at 1.0, smoothly decreases to 0
        val speedRatio = EasingController.getSpeedRatio(elapsed, totalDurationMs)

        // Convert speed ratio to interval
        // speedRatio=1.0 → interval = baseInterval (full speed)
        // speedRatio=0.05 → interval = baseInterval / 0.05 = 20x (slow)
        // speedRatio approaches 0 smoothly, interval goes to infinity
        // We clamp minimum speedRatio to 0.05 to avoid extreme intervals
        val clampedSpeedRatio = speedRatio.coerceAtLeast(0.05)
        return baseIntervalMs / clampedSpeedRatio
    }

    /**
     * Get the actual speed ratio being used.
     * This represents how fast the animation is running as a percentage.
     * @return Value from 0.0 (stopped) to 1.0 (full speed)
     */
    fun getSpeedRatioActual(): Double {
        if (!mIsRunning) return 0.0

        val elapsed = System.currentTimeMillis() - mStartTime

        if (elapsed >= totalDurationMs) {
            return 0.0
        }

        return EasingController.getSpeedRatio(elapsed, totalDurationMs)
    }

    fun isActive(): Boolean = mIsRunning
}
