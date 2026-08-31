package com.example.itantra.system

import android.content.Context
import android.media.AudioManager
import android.app.NotificationManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Singleton utility for triggering and stopping an emergency audio/haptic override.
 *
 * Emergency override behaviour:
 * - Sets [AudioManager.STREAM_MUSIC] to its hardware maximum volume.
 * - Disables Do-Not-Disturb (DND) by switching the interruption filter to
 *   [NotificationManager.INTERRUPTION_FILTER_ALL] (requires
 *   `android.permission.ACCESS_NOTIFICATION_POLICY`).
 * - Starts a repeating vibration pattern that persists until [stopEmergencyOverride]
 *   is called.
 *
 * API-level handling:
 * - [VibratorManager] (API 31+) is used when available; [Vibrator] is used on
 *   older devices where [VibratorManager] is not present.
 *
 * Required permissions (declare in AndroidManifest.xml):
 * ```xml
 * <uses-permission android:name="android.permission.VIBRATE" />
 * <uses-permission android:name="android.permission.ACCESS_NOTIFICATION_POLICY" />
 * <uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
 * ```
 */
object AlertManager {

    /**
     * Repeating vibration pattern: [delay, on, off, on, off, …] in milliseconds.
     * Index 0 is the initial delay before the first pulse.
     */
    private val VIBRATION_PATTERN = longArrayOf(0L, 500L, 300L, 500L, 300L)

    /** Repeat the vibration pattern starting from index 0 indefinitely. */
    private const val VIBRATION_REPEAT_INDEX = 0

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Activates the emergency override:
     * 1. Maximises [AudioManager.STREAM_MUSIC] volume.
     * 2. Lifts Do-Not-Disturb restrictions (if the app holds the policy-access grant).
     * 3. Starts an indefinitely repeating vibration pattern.
     *
     * Safe to call multiple times – redundant calls are harmless.
     *
     * @param context Any valid [Context] (Application context is recommended to avoid leaks).
     */
    fun triggerEmergencyOverride(context: Context) {
        maximiseMusicVolume(context)
        liftDoNotDisturb(context)
        startVibration(context)
    }

    /**
     * Deactivates the emergency override:
     * 1. Cancels any active vibration.
     *
     * Audio volume and DND policy are intentionally left as-is so that the
     * calling component can restore them to previous values if desired.
     *
     * @param context Any valid [Context].
     */
    fun stopEmergencyOverride(context: Context) {
        cancelVibration(context)
    }

    // -------------------------------------------------------------------------
    // Audio helpers
    // -------------------------------------------------------------------------

    private fun maximiseMusicVolume(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audioManager.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            maxVolume,
            0 // no UI flag – silent system-level change
        )
    }

    // -------------------------------------------------------------------------
    // DND helpers
    // -------------------------------------------------------------------------

    private fun liftDoNotDisturb(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        // Guard: we can only change the filter if the app has been granted
        // notification policy access by the user.
        if (nm.isNotificationPolicyAccessGranted) {
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        }
    }

    // -------------------------------------------------------------------------
    // Vibration helpers
    // -------------------------------------------------------------------------

    /**
     * Starts a repeating vibration using the correct API for the device's SDK level.
     *
     * - API 31+ → [VibratorManager] (replaces the deprecated top-level [Vibrator] service).
     * - API 26–30 → [VibrationEffect.createWaveform] via the legacy [Vibrator] service.
     * - API < 26  → legacy `vibrate(pattern, repeat)` (minSdk is 26 so this branch
     *               exists only as a defensive fallback; it will never be reached in
     *               practice but suppresses the compiler warning).
     */
    private fun startVibration(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // API 31+: VibratorManager
            val vibratorManager =
                context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    ?: return
            val vibrator = vibratorManager.defaultVibrator
            val effect = VibrationEffect.createWaveform(VIBRATION_PATTERN, VIBRATION_REPEAT_INDEX)
            vibrator.vibrate(effect)
        } else {
            // API 26–30: deprecated Vibrator service but VibrationEffect is available
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            val effect = VibrationEffect.createWaveform(VIBRATION_PATTERN, VIBRATION_REPEAT_INDEX)
            vibrator.vibrate(effect)
        }
    }

    /**
     * Cancels any active vibration, using the correct API for the device's SDK level.
     */
    private fun cancelVibration(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager =
                context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    ?: return
            vibratorManager.defaultVibrator.cancel()
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            vibrator.cancel()
        }
    }
}
