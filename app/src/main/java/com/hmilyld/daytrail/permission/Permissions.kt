package com.hmilyld.daytrail.permission

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

/** 一项权限的当前状态。 */
enum class PermissionStatus {
    /** 已授予。 */
    GRANTED,

    /** 还没授予，可以弹系统对话框。 */
    NOT_GRANTED,

    /** 用户勾选了「不再询问」，只能去系统设置页手动开。 */
    PERMANENTLY_DENIED,
}

/**
 * 权限检查与跳转的统一入口。
 *
 * 为什么集中在这里：
 * 每一项权限的判定条件不同（后台定位不能直接请求、电池优化不是运行时权限），
 * 散落在页面里很容易漏掉某个 API 级别的分支。
 */
object Permissions {
    /** 前台定位（精确 + 粗略一起请求）。 */
    val LOCATION_FOREGROUND: Array<String> =
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )

    /** 后台定位。Android 10+ 必须单独引导，不能与前台权限一起请求。 */
    val LOCATION_BACKGROUND: String = Manifest.permission.ACCESS_BACKGROUND_LOCATION

    /** 通知权限。Android 13+ 才是运行时权限。 */
    val NOTIFICATION: String = Manifest.permission.POST_NOTIFICATIONS

    /** 是否已授予精确位置。这是记录轨迹的最低要求。 */
    fun hasFineLocation(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    /** 是否已授予后台位置。 */
    fun hasBackgroundLocation(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            LOCATION_BACKGROUND,
        ) == PackageManager.PERMISSION_GRANTED

    /** 是否已授予通知权限。Android 13 以下恒为 true。 */
    fun hasNotification(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                NOTIFICATION,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    /** 是否已加入电池优化白名单。 */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val manager =
            context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                ?: return false
        return manager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * 系统定位开关是否打开。
     *
     * 权限授予了但定位开关关掉，仍然拿不到位置——这是最常见的「没调出 GPS」原因。
     */
    fun isLocationServiceEnabled(context: Context): Boolean {
        val manager =
            context.getSystemService(Context.LOCATION_SERVICE)
                as? android.location.LocationManager ?: return false
        return runCatching {
            manager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
                manager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
        }.getOrDefault(false)
    }

    /** 跳系统应用详情页。用于权限被永久拒绝后的手动开启。 */
    fun openAppSettings(context: Context) {
        val intent =
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** 跳系统定位设置页。 */
    fun openLocationSettings(context: Context) {
        val intent =
            Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** 跳本应用的通知设置页。部分国产 ROM 的运行时弹窗会失败，直接进设置页更稳。 */
    fun openNotificationSettings(context: Context) {
        val intent =
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { openAppSettings(context) }
    }

    /**
     * 申请忽略电池优化。
     *
     * 用 `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 带包名直接弹系统对话框，
     * 比跳列表页少一步操作。
     */
    fun requestIgnoreBatteryOptimizations(context: Context) {
        val intent =
            Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure {
                // 部分 ROM 不支持该 action，退化为电池优化列表
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
    }

    /**
     * 后台定位只能跳系统设置页。
     *
     * Android 10+ 起 `ACCESS_BACKGROUND_LOCATION` 不允许与应用内弹窗合并请求，
     * 必须引导用户到「权限 → 位置信息 → 始终允许」。
     */
    fun openBackgroundLocationGuide(context: Context) {
        openAppSettings(context)
    }
}
