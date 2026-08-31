package com.example.itantra.system

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Centralised helper for runtime permission management.
 *
 * The set of required permissions is computed dynamically at runtime so that
 * API-level-specific permissions are only requested on devices that support them.
 *
 * | Permission                          | Min API | Notes                                    |
 * |-------------------------------------|---------|------------------------------------------|
 * | RECORD_AUDIO                        | Any     | Required for all audio capture           |
 * | ACCESS_FINE_LOCATION                | Any     | Required for Wi-Fi peer discovery        |
 * | NEARBY_WIFI_DEVICES                 | 33+     | Replaces location for Wi-Fi scan on S+   |
 *
 * Usage:
 * ```kotlin
 * if (!PermissionHandler.hasAllPermissions(context)) {
 *     ActivityCompat.requestPermissions(
 *         activity,
 *         PermissionHandler.getRequiredPermissions(),
 *         MY_REQUEST_CODE
 *     )
 * }
 * ```
 */
object PermissionHandler {

    /**
     * Builds the array of runtime permissions required by this application.
     *
     * [Manifest.permission.NEARBY_WIFI_DEVICES] was introduced in API 33 (Android 13).
     * On devices running API 33+, it is required in place of (or in addition to)
     * [Manifest.permission.ACCESS_FINE_LOCATION] for Wi-Fi peer device discovery.
     *
     * @return A non-empty array of permission strings appropriate for the current device.
     */
    fun getRequiredPermissions(): Array<String> {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // API 33+ (Android 13 / TIRAMISU): NEARBY_WIFI_DEVICES is mandatory for
            // initiating Wi-Fi connections without the broad location permission.
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }

        return permissions.toTypedArray()
    }

    /**
     * Returns `true` only when **every** permission returned by [getRequiredPermissions]
     * has been granted by the user.
     *
     * Uses [ContextCompat.checkSelfPermission] so it is safe to call from any thread
     * without needing an [Activity] reference.
     *
     * @param context Any valid [Context] (Application context is fine).
     * @return `true` if all required permissions are [PackageManager.PERMISSION_GRANTED].
     */
    fun hasAllPermissions(context: Context): Boolean {
        return getRequiredPermissions().all { permission ->
            ContextCompat.checkSelfPermission(context, permission) ==
                    PackageManager.PERMISSION_GRANTED
        }
    }
}
