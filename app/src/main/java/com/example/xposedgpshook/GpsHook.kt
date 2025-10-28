package com.example.xposedgpshook

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.SystemClock
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.util.Random

class GpsHook : IXposedHookLoadPackage {

    // 默认位置 (zhongnanhai, beijing)
    private val defaultLatitude = 39.9120899
    private val defaultLongitude = 116.383597

    private val targetPackages = setOf(
        "com.google.android.apps.maps", // 谷歌地图
        "com.baidu.BaiduMap",           // 百度地图
        "com.autonavi.minimap",         // 高德地图
        "com.tencent.map",              // 腾讯地图
        "com.tencent.qqminimap",        // 腾讯地图（轻量版）
        "com.sougou.map.android.maps",  // 搜狗地图
        "com.google.android.gms",        // Google Play Services (融合定位服务)
        "android"                        // 系统进程 (部分系统服务可能运行在此包名下)
    )

    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        // 只对目标地图应用生效
        if (lpparam.packageName !in targetPackages) {
            return
        }

        XposedBridge.log("GpsHook: Loaded app: ${lpparam.packageName}")

        // 检查是否启用了 Hook
        if (!isHookEnabled()) {
            XposedBridge.log("GpsHook: Hook is disabled by user, skipping...")
            return
        }

        hookLocationManager(lpparam.classLoader)
        hookWifiManager(lpparam.classLoader)
        hookTelephonyManager(lpparam.classLoader)
        hookLocationManagerService(lpparam.classLoader)

        // Hook Google Play Services 融合定位
        if (lpparam.packageName == "com.google.android.gms") {
            hookFusedLocationProvider(lpparam.classLoader)
        }
    }

    /**
     * 检查用户是否启用了 Hook
     */
    private fun isHookEnabled(): Boolean {
        return try {
            val prefsPath = "/data/data/com.example.xposedgpshook/shared_prefs/gps_hook_prefs.xml"
            val prefsFile = java.io.File(prefsPath)

            if (prefsFile.exists() && prefsFile.canRead()) {
                val content = prefsFile.readText()
                val pattern = "<boolean name=\"hook_enabled\" value=\"([^\"]+)\"".toRegex()
                val matchResult = pattern.find(content)
                val enabled = matchResult?.groupValues?.get(1)?.toBoolean() ?: true
                XposedBridge.log("GpsHook: Hook enabled status: $enabled")
                enabled
            } else {
                XposedBridge.log("GpsHook: Config file not found, assuming enabled")
                true
            }
        } catch (e: Throwable) {
            XposedBridge.log("GpsHook: Failed to read hook enabled status, assuming enabled")
            XposedBridge.log(e)
            true
        }
    }

    private fun hookLocationManager(classLoader: ClassLoader) {
        try {
            val locationManagerClass = XposedHelpers.findClass(
                "android.location.LocationManager",
                classLoader
            )

            // 1. Hook requestLocationUpdates 方法（带 LocationListener 参数）
            XposedHelpers.findAndHookMethod(
                locationManagerClass,
                "requestLocationUpdates",
                String::class.java,      // provider
                Long::class.javaPrimitiveType,        // minTime
                Float::class.javaPrimitiveType,       // minDistance
                LocationListener::class.java, // listener
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val originalListener = param.args[3] as? LocationListener ?: return

                        // 创建一个我们自己的监听器代理
                        val hookedListener = object : LocationListener {
                            override fun onLocationChanged(location: Location) {
                                XposedBridge.log("GpsHook: onLocationChanged intercepted!")
                                val fakeLocation = createFakeLocation(param.thisObject as LocationManager)
                                // 调用原始监听器的方法，但传入的是我们的虚假位置
                                originalListener.onLocationChanged(fakeLocation)
                            }

                            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
                                originalListener.onStatusChanged(provider, status, extras)
                            }

                            override fun onProviderEnabled(provider: String) {
                                originalListener.onProviderEnabled(provider)
                            }

                            override fun onProviderDisabled(provider: String) {
                                originalListener.onProviderDisabled(provider)
                            }
                        }
                        // 将原始的监听器替换为我们的代理
                        param.args[3] = hookedListener
                        XposedBridge.log("GpsHook: requestLocationUpdates hooked for ${param.args[0]}")
                    }
                }
            )

            // 2. Hook requestLocationUpdates 方法（带 Looper 参数）
            try {
                XposedHelpers.findAndHookMethod(
                    locationManagerClass,
                    "requestLocationUpdates",
                    String::class.java,      // provider
                    Long::class.javaPrimitiveType,        // minTime
                    Float::class.javaPrimitiveType,       // minDistance
                    LocationListener::class.java, // listener
                    android.os.Looper::class.java, // looper
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val originalListener = param.args[3] as? LocationListener ?: return

                            val hookedListener = object : LocationListener {
                                override fun onLocationChanged(location: Location) {
                                    XposedBridge.log("GpsHook: onLocationChanged (with Looper) intercepted!")
                                    originalListener.onLocationChanged(createFakeLocation(param.thisObject as LocationManager))
                                }

                                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
                                    originalListener.onStatusChanged(provider, status, extras)
                                }

                                override fun onProviderEnabled(provider: String) {
                                    originalListener.onProviderEnabled(provider)
                                }

                                override fun onProviderDisabled(provider: String) {
                                    originalListener.onProviderDisabled(provider)
                                }
                            }
                            param.args[3] = hookedListener
                        }
                    }
                )
            } catch (e: Throwable) {
                XposedBridge.log("GpsHook: requestLocationUpdates (with Looper) not found or failed")
            }

            // 3. Hook getLastKnownLocation 方法
            XposedHelpers.findAndHookMethod(
                locationManagerClass,
                "getLastKnownLocation",
                String::class.java, // provider
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        XposedBridge.log("GpsHook: getLastKnownLocation intercepted for ${param.args[0]}")
                        // 直接返回我们的虚假位置
                        param.result = createFakeLocation(param.thisObject as LocationManager)
                    }
                }
            )

            // 4. Hook getCurrentLocation 方法（Android 11+）
            try {
                XposedHelpers.findAndHookMethod(
                    locationManagerClass,
                    "getCurrentLocation",
                    String::class.java, // provider
                    android.os.CancellationSignal::class.java, // cancellationSignal
                    java.util.concurrent.Executor::class.java, // executor
                    java.util.function.Consumer::class.java, // consumer
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            val originalConsumer = param.args[3] as? java.util.function.Consumer<Location> ?: return

                            val hookedConsumer = java.util.function.Consumer<Location> { location ->
                                XposedBridge.log("GpsHook: getCurrentLocation intercepted!")
                                originalConsumer.accept(createFakeLocation(param.thisObject as LocationManager))
                            }
                            param.args[3] = hookedConsumer
                        }
                    }
                )
            } catch (e: Throwable) {
                XposedBridge.log("GpsHook: getCurrentLocation not found (requires Android 11+)")
            }

            /*
            // 5. Hook Location 类的 getLatitude 和 getLongitude 方法 (REMOVED due to instability)
            try {
                val locationClass = XposedHelpers.findClass("android.location.Location", classLoader)

                XposedHelpers.findAndHookMethod(
                    locationClass,
                    "getLatitude",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val location = param.thisObject as Location
                            val context = getContextFromLocation(location)
                            val coords = getUserSettings(context)
                            param.result = coords.first
                        }
                    }
                )

                XposedHelpers.findAndHookMethod(
                    locationClass,
                    "getLongitude",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val location = param.thisObject as Location
                            val context = getContextFromLocation(location)
                            val coords = getUserSettings(context)
                            param.result = coords.second
                        }
                    }
                )

                XposedBridge.log("GpsHook: Location.getLatitude/getLongitude hooked")
            } catch (e: Throwable) {
                XposedBridge.log("GpsHook: Failed to hook Location class methods")
                XposedBridge.log(e)
            }
            */

            XposedBridge.log("GpsHook: Successfully hooked LocationManager.")

        } catch (e: Throwable) {
            XposedBridge.log("GpsHook: Failed to hook LocationManager.")
            XposedBridge.log(e)
        }
    }

    private fun hookWifiManager(classLoader: ClassLoader) {
        try {
            val wifiManagerClass = XposedHelpers.findClass("android.net.wifi.WifiManager", classLoader)

            // Hook getScanResults 方法
            XposedHelpers.findAndHookMethod(
                wifiManagerClass,
                "getScanResults",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        param.result = createFakeWifiScanResults()
                        XposedBridge.log("GpsHook: Faked WiFi scan results.")
                    }
                }
            )

            // Hook WifiInfo to prevent getting real connection details
            try {
                val wifiInfoClass = XposedHelpers.findClass("android.net.wifi.WifiInfo", classLoader)

                // 伪造 IP 地址
                XposedHelpers.findAndHookMethod(wifiInfoClass, "getIpAddress", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        param.result = 0 // 返回 0.0.0.0
                        XposedBridge.log("GpsHook: Faked WifiInfo getIpAddress.")
                    }
                })

                // 伪造 SSID
                XposedHelpers.findAndHookMethod(wifiInfoClass, "getSSID", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        param.result = "FakeConnectedSSID"
                        XposedBridge.log("GpsHook: Faked WifiInfo getSSID.")
                    }
                })

                // 伪造 BSSID
                XposedHelpers.findAndHookMethod(wifiInfoClass, "getBSSID", object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        param.result = "02:00:00:00:00:00"
                        XposedBridge.log("GpsHook: Faked WifiInfo getBSSID.")
                    }
                })
            } catch (e: Throwable) {
                XposedBridge.log("GpsHook: Failed to hook WifiInfo methods.")
                XposedBridge.log(e)
            }

            XposedBridge.log("GpsHook: Successfully hooked WifiManager.")
        } catch (e: Throwable) {
            XposedBridge.log("GpsHook: Failed to hook WifiManager.")
            XposedBridge.log(e)
        }
    }

    private fun hookTelephonyManager(classLoader: ClassLoader) {
        try {
            val telephonyManagerClass = XposedHelpers.findClass("android.telephony.TelephonyManager", classLoader)

            // Hook getAllCellInfo (for modern apps)
            XposedHelpers.findAndHookMethod(
                telephonyManagerClass,
                "getAllCellInfo",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val context = getContextFromTelephonyManager(param.thisObject as android.telephony.TelephonyManager)
                        param.result = createFakeCellInfo(context)
                        XposedBridge.log("GpsHook: Faked getAllCellInfo.")
                    }
                }
            )

            // Hook getCellLocation (for older apps)
            XposedHelpers.findAndHookMethod(
                telephonyManagerClass,
                "getCellLocation",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        param.result = createFakeCellLocation()
                        XposedBridge.log("GpsHook: Faked getCellLocation.")
                    }
                }
            )

            // Hook other sensitive info to return null or empty
            val methodsToHook = listOf(
                "getSubscriberId", "getDeviceId", "getLine1Number", "getSimSerialNumber",
                "getSimOperator", "getSimOperatorName", "getNetworkOperator", "getNetworkOperatorName"
            )
            for (methodName in methodsToHook) {
                try {
                    XposedHelpers.findAndHookMethod(
                        telephonyManagerClass,
                        methodName,
                        object : XC_MethodHook() {
                            override fun afterHookedMethod(param: MethodHookParam) {
                                param.result = null
                                XposedBridge.log("GpsHook: Faked $methodName to null.")
                            }
                        }
                    )
                } catch (e: NoSuchMethodError) {
                    // Some methods might not exist on all API levels, ignore.
                } catch (e: Throwable) {
                    XposedBridge.log("GpsHook: Failed to hook $methodName")
                    XposedBridge.log(e)
                }
            }

            // Hook getNeighboringCellInfo (旧版 API，百度地图可能使用)
            try {
                XposedHelpers.findAndHookMethod(
                    telephonyManagerClass,
                    "getNeighboringCellInfo",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            param.result = createFakeNeighboringCellInfo()
                            XposedBridge.log("GpsHook: Faked getNeighboringCellInfo.")
                        }
                    }
                )
            } catch (e: Throwable) {
                XposedBridge.log("GpsHook: getNeighboringCellInfo not found or failed")
            }

            // Hook getServiceState (服务状态)
            try {
                XposedHelpers.findAndHookMethod(
                    telephonyManagerClass,
                    "getServiceState",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            // 返回正常的服务状态，表示信号良好
                            XposedBridge.log("GpsHook: getServiceState intercepted.")
                        }
                    }
                )
            } catch (e: Throwable) {
                XposedBridge.log("GpsHook: getServiceState not found or failed")
            }

            XposedBridge.log("GpsHook: Successfully hooked TelephonyManager.")
        } catch (e: Throwable) {
            XposedBridge.log("GpsHook: Failed to hook TelephonyManager.")
            XposedBridge.log(e)
        }
    }

    /**
     * Hook 系统的 LocationManagerService (系统定位服务核心)
     */
    private fun hookLocationManagerService(classLoader: ClassLoader) {
        try {
            // Hook LocationManagerService 类
            val locationManagerServiceClass = XposedHelpers.findClass(
                "com.android.server.LocationManagerService",
                classLoader
            )

            // Hook getLastLocation 方法
            try {
                XposedHelpers.findAndHookMethod(
                    locationManagerServiceClass,
                    "getLastLocation",
                    String::class.java,
                    String::class.java,
                    String::class.java,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            val fakeLocation = createFakeLocationSimple()
                            param.result = fakeLocation
                            XposedBridge.log("GpsHook: LocationManagerService.getLastLocation hooked")
                        }
                    }
                )
            } catch (e: Throwable) {
                XposedBridge.log("GpsHook: Failed to hook getLastLocation in LocationManagerService")
                XposedBridge.log(e)
            }

            // Hook requestLocationUpdates
            try {
                XposedHelpers.findAndHookMethod(
                    locationManagerServiceClass,
                    "requestLocationUpdates",
                    android.location.LocationRequest::class.java,
                    android.location.LocationListener::class.java,
                    android.app.PendingIntent::class.java,
                    String::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            XposedBridge.log("GpsHook: LocationManagerService.requestLocationUpdates intercepted")
                        }
                    }
                )
            } catch (e: Throwable) {
                XposedBridge.log("GpsHook: Failed to hook requestLocationUpdates in LocationManagerService")
            }

            XposedBridge.log("GpsHook: Successfully hooked LocationManagerService")
        } catch (e: Throwable) {
            XposedBridge.log("GpsHook: Failed to find LocationManagerService class")
            XposedBridge.log(e)
        }
    }

    /**
     * 创建简单的虚假位置（用于系统服务）
     */
    private fun createFakeLocationSimple(): Location {
        val coords = getUserSettings(null)

        val location = Location(LocationManager.GPS_PROVIDER)
        location.latitude = coords.first
        location.longitude = coords.second
        location.accuracy = 10.0f
        location.altitude = 50.0
        location.bearing = 0f
        location.speed = 0f
        location.time = System.currentTimeMillis()
        location.elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        return location
    }

    /**
     * 创建一个虚假的 Location 对象
     */
    private fun createFakeLocation(locationManager: LocationManager): Location {
        val context = getContextFromLocationManager(locationManager)
        val coords = getUserSettings(context)

        val location = Location(LocationManager.GPS_PROVIDER)
        location.latitude = coords.first
        location.longitude = coords.second
        location.accuracy = 10.0f // 精度（米）
        location.altitude = 50.0 // 海拔（米）
        location.bearing = 0f // 方向
        location.speed = 0f // 速度（米/秒）
        location.time = System.currentTimeMillis()
        // for API 17+
        location.elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        return location
    }

    /**
     * 从 TelephonyManager 获取 Context
     */
    private fun getContextFromTelephonyManager(telephonyManager: android.telephony.TelephonyManager): Context? {
        return try {
            val contextField = XposedHelpers.findField(telephonyManager.javaClass, "mContext")
            contextField.get(telephonyManager) as? Context
        } catch (e: Throwable) {
            null
        }
    }

    /**
     * 从 LocationManager 获取 Context
     */
    private fun getContextFromLocationManager(locationManager: LocationManager): Context? {
        return try {
            val contextField = XposedHelpers.findField(locationManager.javaClass, "mContext")
            contextField.get(locationManager) as? Context
        } catch (e: Throwable) {
            null
        }
    }

    /**
     * 从 Location 获取 Context (尝试多种方法)
     */
    private fun getContextFromLocation(location: Location): Context? {
        return null // Location 对象本身不包含 Context，这里返回 null
    }

    /**
     * 获取用户设置的坐标 - 通过 ContentProvider 跨进程读取
     */
    private fun getUserSettings(context: Context?): Pair<Double, Double> {
        return try {
            // 尝试通过 ContentProvider 读取配置
            val uri = android.net.Uri.parse("content://com.example.xposedgpshook.provider/location")

            XposedBridge.log("GpsHook: Attempting to read config via ContentProvider: $uri")

            // 如果没有 Context，使用默认值
            if (context == null) {
                XposedBridge.log("GpsHook: No context available, using default location")
                return Pair(defaultLatitude, defaultLongitude)
            }

            val cursor = context.contentResolver.query(
                uri,
                null,
                null,
                null,
                null
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val latitudeIndex = it.getColumnIndex("latitude")
                    val longitudeIndex = it.getColumnIndex("longitude")
                    val enabledIndex = it.getColumnIndex("enabled")

                    if (latitudeIndex >= 0 && longitudeIndex >= 0 && enabledIndex >= 0) {
                        val latitude = it.getDouble(latitudeIndex)
                        val longitude = it.getDouble(longitudeIndex)
                        val enabled = it.getInt(enabledIndex) == 1

                        if (!enabled) {
                            XposedBridge.log("GpsHook: Hook is disabled by user")
                            // 如果禁用了，也返回默认值（或者可以返回真实位置）
                        }

                        XposedBridge.log("GpsHook: Successfully read location via ContentProvider: Latitude=$latitude, Longitude=$longitude, Enabled=$enabled")
                        return Pair(latitude, longitude)
                    } else {
                        XposedBridge.log("GpsHook: Invalid column indices in cursor")
                    }
                } else {
                    XposedBridge.log("GpsHook: Cursor is empty")
                }
            }

            XposedBridge.log("GpsHook: Failed to read from ContentProvider, using default location")
            Pair(defaultLatitude, defaultLongitude)

        } catch (e: Throwable) {
            XposedBridge.log("GpsHook: Exception reading from ContentProvider: ${e.message}")
            XposedBridge.log("GpsHook: Exception type: ${e.javaClass.name}")
            XposedBridge.log(e)
            XposedBridge.log("GpsHook: Falling back to default location")
            Pair(defaultLatitude, defaultLongitude)
        }
    }

    /**
     * 从 XML 内容中提取 float 值
     */
    private fun extractFloatValue(xml: String, key: String): Float? {
        return try {
            val pattern = "<float name=\"$key\" value=\"([^\"]+)\"".toRegex()
            val matchResult = pattern.find(xml)
            matchResult?.groupValues?.get(1)?.toFloat()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 创建一个虚假的 WiFi 扫描结果列表
     */
    private fun createFakeWifiScanResults(): List<Any> {
        val fakeResults = mutableListOf<Any>()
        val random = Random()
        val numResults = 10 + random.nextInt(10) // 生成 10-19 个结果

        for (i in 1..numResults) {
            try {
                val scanResultClass = XposedHelpers.findClass("android.net.wifi.ScanResult", null)
                val scanResult = scanResultClass.newInstance()

                // 生成随机 BSSID (MAC 地址)
                val bssid = String.format(
                    "%02x:%02x:%02x:%02x:%02x:%02x",
                    random.nextInt(256),
                    random.nextInt(256),
                    random.nextInt(256),
                    random.nextInt(256),
                    random.nextInt(256),
                    random.nextInt(256)
                )

                XposedHelpers.setObjectField(scanResult, "SSID", "FakeWiFi_$i")
                XposedHelpers.setObjectField(scanResult, "BSSID", bssid)
                XposedHelpers.setObjectField(scanResult, "capabilities", "[WPA2-PSK-CCMP]")
                XposedHelpers.setObjectField(scanResult, "level", -30 - random.nextInt(60)) // 信号强度 -30 to -89 dBm
                XposedHelpers.setObjectField(scanResult, "frequency", if (i % 2 == 0) 2450 else 5200) // 2.4GHz or 5GHz
                XposedHelpers.setObjectField(scanResult, "timestamp", System.currentTimeMillis() * 1000)

                fakeResults.add(scanResult)
            } catch (e: Throwable) {
                XposedBridge.log("GpsHook: Failed to create a fake ScanResult object.")
                XposedBridge.log(e)
            }
        }
        return fakeResults
    }

    /**
     * 创建一个虚假的 CellInfo 列表
     */
    private fun createFakeCellInfo(context: Context?): List<Any> {
        val fakeCellInfos = mutableListOf<Any>()
        val random = Random()
        val numCells = 2 + random.nextInt(2) // 2-3 个基站

        for (i in 1..numCells) {
            try {
                val cellInfoLteClass = XposedHelpers.findClass("android.telephony.CellInfoLte", null)
                val cellIdentityLteClass = XposedHelpers.findClass("android.telephony.CellIdentityLte", null)
                val cellSignalStrengthLteClass = XposedHelpers.findClass("android.telephony.CellSignalStrengthLte", null)

                val cellIdentity = cellIdentityLteClass.newInstance()
                XposedHelpers.setIntField(cellIdentity, "mMcc", 460) // MCC for China
                XposedHelpers.setIntField(cellIdentity, "mMnc", 0)   // MNC for China Mobile
                XposedHelpers.setIntField(cellIdentity, "mCi", random.nextInt(65535))
                XposedHelpers.setIntField(cellIdentity, "mPci", random.nextInt(504))
                XposedHelpers.setIntField(cellIdentity, "mTac", random.nextInt(65535)) // Tracking Area Code

                val cellSignalStrength = cellSignalStrengthLteClass.newInstance()
                XposedHelpers.setIntField(cellSignalStrength, "mSignalStrength", random.nextInt(31) + 2) // ASU level 2-31
                XposedHelpers.setIntField(cellSignalStrength, "mRsrp", -110 + random.nextInt(30)) // RSRP
                XposedHelpers.setIntField(cellSignalStrength, "mRsrq", -15 + random.nextInt(10))  // RSRQ
                XposedHelpers.setIntField(cellSignalStrength, "mRssnr", random.nextInt(200))
                XposedHelpers.setIntField(cellSignalStrength, "mCqi", random.nextInt(15))
                XposedHelpers.setIntField(cellSignalStrength, "mTimingAdvance", random.nextInt(1282))

                val cellInfo = cellInfoLteClass.newInstance()
                XposedHelpers.setBooleanField(cellInfo, "mRegistered", i == 1) // 第一个设为已注册
                XposedHelpers.setObjectField(cellInfo, "mCellIdentity", cellIdentity)
                XposedHelpers.setObjectField(cellInfo, "mCellSignalStrength", cellSignalStrength)

                fakeCellInfos.add(cellInfo)
            } catch (e: Throwable) {
                XposedBridge.log("GpsHook: Failed to create a fake CellInfoLte object.")
                XposedBridge.log(e)
            }
        }
        return fakeCellInfos
    }

    /**
     * 创建一个虚假的 GsmCellLocation
     */
    private fun createFakeCellLocation(): Any {
        val gsmCellLocationClass = XposedHelpers.findClass("android.telephony.gsm.GsmCellLocation", null)
        val gsmCellLocation = gsmCellLocationClass.newInstance()
        val random = Random()
        XposedHelpers.callMethod(gsmCellLocation, "setLacAndCid", random.nextInt(65535), random.nextInt(65535))
        return gsmCellLocation
    }

    /**
     * 创建虚假的邻近基站信息列表（旧版 API）
     */
    private fun createFakeNeighboringCellInfo(): List<Any> {
        val fakeNeighboringCells = mutableListOf<Any>()
        val random = Random()
        val numCells = 2 + random.nextInt(2) // 2-3 个邻近基站

        try {
            val neighboringCellInfoClass = XposedHelpers.findClass("android.telephony.NeighboringCellInfo", null)

            for (i in 1..numCells) {
                val constructor = neighboringCellInfoClass.getConstructor(
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                )
                val rssi = -110 + random.nextInt(30) // 信号强度
                val cid = random.nextInt(65535) // Cell ID
                val neighboringCell = constructor.newInstance(rssi, cid)
                fakeNeighboringCells.add(neighboringCell)
            }
        } catch (e: Throwable) {
            XposedBridge.log("GpsHook: Failed to create fake NeighboringCellInfo.")
            XposedBridge.log(e)
        }

        return fakeNeighboringCells
    }

    /**
     * Hook Google Play Services 的 FusedLocationProvider
     * 这是现代 Android 应用最常用的定位服务
     */
    private fun hookFusedLocationProvider(classLoader: ClassLoader) {
        XposedBridge.log("GpsHook: Starting to hook Google Play Services FusedLocationProvider APIs...")

        // Hook FusedLocationProviderClient (现代 API)
        hookFusedLocationProviderClient(classLoader)

        // Hook FusedLocationProviderApi (旧版 API)
        hookFusedLocationProviderApi(classLoader)

        // Hook LocationServices
        hookLocationServices(classLoader)

        // Hook zzd (内部实现类)
        hookInternalLocationClasses(classLoader)
    }

    /**
     * Hook FusedLocationProviderClient (Google Play Services 最新 API)
     */
    private fun hookFusedLocationProviderClient(classLoader: ClassLoader) {
        try {
            // 1. Hook FusedLocationProviderClient
            val fusedClientClass = XposedHelpers.findClass(
                "com.google.android.gms.location.FusedLocationProviderClient",
                classLoader
            )

            // Hook getLastLocation() -> Task<Location>
            try {
                XposedHelpers.findAndHookMethod(
                    fusedClientClass,
                    "getLastLocation",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            XposedBridge.log("GpsHook: FusedLocationProviderClient.getLastLocation() intercepted")
                            try {
                                // 创建一个成功的 Task，返回虚假位置
                                val taskClass = XposedHelpers.findClass("com.google.android.gms.tasks.Tasks", classLoader)
                                val fakeLocation = createFakeLocationSimple()
                                param.result = XposedHelpers.callStaticMethod(taskClass, "forResult", fakeLocation)
                            } catch (e: Throwable) {
                                XposedBridge.log("GpsHook: Failed to create fake Task for getLastLocation")
                                XposedBridge.log(e)
                            }
                        }
                    }
                )
                XposedBridge.log("GpsHook: Hooked FusedLocationProviderClient.getLastLocation()")
            } catch (e: Throwable) {
                XposedBridge.log("GpsHook: Failed to hook getLastLocation: ${e.message}")
            }

            // Hook getCurrentLocation(int priority, CancellationToken) -> Task<Location>
            try {
                XposedHelpers.findAndHookMethod(
                    fusedClientClass,
                    "getCurrentLocation",
                    Int::class.javaPrimitiveType,
                    XposedHelpers.findClass("com.google.android.gms.tasks.CancellationToken", classLoader),
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            XposedBridge.log("GpsHook: FusedLocationProviderClient.getCurrentLocation() intercepted")
                            try {
                                val taskClass = XposedHelpers.findClass("com.google.android.gms.tasks.Tasks", classLoader)
                                val fakeLocation = createFakeLocationSimple()
                                param.result = XposedHelpers.callStaticMethod(taskClass, "forResult", fakeLocation)
                            } catch (e: Throwable) {
                                XposedBridge.log("GpsHook: Failed to create fake Task for getCurrentLocation")
                                XposedBridge.log(e)
                            }
                        }
                    }
                )
                XposedBridge.log("GpsHook: Hooked FusedLocationProviderClient.getCurrentLocation()")
            } catch (e: Throwable) {
                XposedBridge.log("GpsHook: Failed to hook getCurrentLocation: ${e.message}")
            }

            // Hook requestLocationUpdates(LocationRequest, LocationCallback, Looper)
            try {
                val locationCallbackClass = XposedHelpers.findClass(
                    "com.google.android.gms.location.LocationCallback",
                    classLoader
                )

                XposedHelpers.findAndHookMethod(
                    fusedClientClass,
                    "requestLocationUpdates",
                    XposedHelpers.findClass("com.google.android.gms.location.LocationRequest", classLoader),
                    locationCallbackClass,
                    android.os.Looper::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            XposedBridge.log("GpsHook: FusedLocationProviderClient.requestLocationUpdates() intercepted")

                            // 替换 LocationCallback 为我们的代理
                            val originalCallback = param.args[1]
                            if (originalCallback != null) {
                                val hookedCallback = createHookedLocationCallback(originalCallback, classLoader)
                                param.args[1] = hookedCallback
                            }
                        }
                    }
                )
                XposedBridge.log("GpsHook: Hooked FusedLocationProviderClient.requestLocationUpdates()")
            } catch (e: Throwable) {
                XposedBridge.log("GpsHook: Failed to hook requestLocationUpdates: ${e.message}")
            }

            // Hook requestLocationUpdates(LocationRequest, PendingIntent)
            try {
                XposedHelpers.findAndHookMethod(
                    fusedClientClass,
                    "requestLocationUpdates",
                    XposedHelpers.findClass("com.google.android.gms.location.LocationRequest", classLoader),
                    android.app.PendingIntent::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            XposedBridge.log("GpsHook: FusedLocationProviderClient.requestLocationUpdates(PendingIntent) intercepted")
                        }
                    }
                )
            } catch (e: Throwable) {
                XposedBridge.log("GpsHook: Failed to hook requestLocationUpdates with PendingIntent")
            }

            XposedBridge.log("GpsHook: Successfully hooked FusedLocationProviderClient")
        } catch (e: Throwable) {
            XposedBridge.log("GpsHook: Failed to hook FusedLocationProviderClient: ${e.message}")
            XposedBridge.log(e)
        }
    }

    /**
     * 创建一个被 Hook 的 LocationCallback 代理
     */
    private fun createHookedLocationCallback(originalCallback: Any, classLoader: ClassLoader): Any {
        return try {
            val locationCallbackClass = XposedHelpers.findClass(
                "com.google.android.gms.location.LocationCallback",
                classLoader
            )

            val locationResultClass = XposedHelpers.findClass(
                "com.google.android.gms.location.LocationResult",
                classLoader
            )

            // 创建代理对象
            java.lang.reflect.Proxy.newProxyInstance(
                classLoader,
                arrayOf(locationCallbackClass),
                java.lang.reflect.InvocationHandler { _, method, args ->
                    when (method.name) {
                        "onLocationResult" -> {
                            XposedBridge.log("GpsHook: LocationCallback.onLocationResult intercepted")
                            if (args != null && args.isNotEmpty()) {
                                // 创建虚假的 LocationResult
                                val fakeLocation = createFakeLocationSimple()
                                val fakeResult = createFakeLocationResult(fakeLocation, locationResultClass)
                                // 调用原始回调，但传入虚假位置
                                method.invoke(originalCallback, fakeResult)
                            }
                            null
                        }
                        "onLocationAvailability" -> {
                            XposedBridge.log("GpsHook: LocationCallback.onLocationAvailability intercepted")
                            if (args != null && args.isNotEmpty()) {
                                method.invoke(originalCallback, *args)
                            }
                            null
                        }
                        else -> {
                            if (args != null) {
                                method.invoke(originalCallback, *args)
                            } else {
                                method.invoke(originalCallback)
                            }
                        }
                    }
                }
            )
        } catch (e: Throwable) {
            XposedBridge.log("GpsHook: Failed to create hooked LocationCallback, returning original")
            XposedBridge.log(e)
            originalCallback
        }
    }

    /**
     * 创建虚假的 LocationResult
     */
    private fun createFakeLocationResult(fakeLocation: Location, locationResultClass: Class<*>): Any {
        return try {
            // LocationResult 包含一个 Location 列表
            val locations = listOf(fakeLocation)
            val constructor = locationResultClass.getConstructor(List::class.java)
            constructor.newInstance(locations)
        } catch (e: Throwable) {
            XposedBridge.log("GpsHook: Failed to create LocationResult")
            XposedBridge.log(e)
            throw e
        }
    }

    /**
     * Hook FusedLocationProviderApi (旧版 Google Play Services API)
     */
    private fun hookFusedLocationProviderApi(classLoader: ClassLoader) {
        try {
            val fusedApiClass = XposedHelpers.findClass(
                "com.google.android.gms.location.FusedLocationProviderApi",
                classLoader
            )

            XposedBridge.log("GpsHook: Found FusedLocationProviderApi, attempting to hook...")
        } catch (e: Throwable) {
            XposedBridge.log("GpsHook: FusedLocationProviderApi not found or failed to hook")
        }
    }

    /**
     * Hook LocationServices
     */
    private fun hookLocationServices(classLoader: ClassLoader) {
        try {
            val locationServicesClass = XposedHelpers.findClass(
                "com.google.android.gms.location.LocationServices",
                classLoader
            )

            XposedBridge.log("GpsHook: Found LocationServices class")

            // LocationServices 主要是提供 FusedLocationProviderClient 的入口
            // 我们已经 Hook 了 FusedLocationProviderClient，所以这里不需要额外操作
        } catch (e: Throwable) {
            XposedBridge.log("GpsHook: LocationServices not found")
        }
    }

    /**
     * Hook Google Play Services 内部实现类
     */
    private fun hookInternalLocationClasses(classLoader: ClassLoader) {
        try {
            // Hook zzd 类 (Google Play Services 的混淆内部类)
            // 注意：这些类名可能随 GMS 版本变化而变化
            XposedBridge.log("GpsHook: Attempting to hook internal GMS location classes...")

            // 尝试 Hook 可能的内部实现类
            val possibleClasses = listOf(
                "com.google.android.gms.location.internal.FusedLocationProviderImpl",
                "com.google.android.gms.location.zzd",
                "com.google.android.gms.location.zzz"
            )

            for (className in possibleClasses) {
                try {
                    val clazz = XposedHelpers.findClass(className, classLoader)
                    XposedBridge.log("GpsHook: Found internal class: $className")
                } catch (e: Throwable) {
                    // 类不存在，继续尝试其他类
                }
            }
        } catch (e: Throwable) {
            XposedBridge.log("GpsHook: Failed to hook internal location classes")
        }
    }
}
