package com.example.xposedgpshook

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.DataOutputStream
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var etLatitude: EditText
    private lateinit var etLongitude: EditText
    private lateinit var btnSave: Button
    private lateinit var btnReset: Button
    private lateinit var switchEnableHook: Switch

    companion object {
        const val PREFS_NAME = "gps_hook_prefs"
        const val KEY_LATITUDE = "fake_latitude"
        const val KEY_LONGITUDE = "fake_longitude"
        const val KEY_HOOK_ENABLED = "hook_enabled"
        const val DEFAULT_LATITUDE = 39.916345 // zhongnanhai
        const val DEFAULT_LONGITUDE = 116.383597
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 检查 root 权限
        checkRootPermission()

        initViews()
        loadSettings()
        setupListeners()
    }

    /**
     * 检查并请求 root 权限
     */
    private fun checkRootPermission() {
        if (isRooted()) {
            Toast.makeText(this, "已获取 Root 权限", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "警告：未获取 Root 权限，部分功能可能无法使用", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * 检查设备是否已 Root
     */
    private fun isRooted(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            os.writeBytes("exit\n")
            os.flush()
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    private fun initViews() {
        etLatitude = findViewById(R.id.et_latitude)
        etLongitude = findViewById(R.id.et_longitude)
        btnSave = findViewById(R.id.btn_save)
        btnReset = findViewById(R.id.btn_reset)
        switchEnableHook = findViewById(R.id.switch_enable_hook)
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val latitude = prefs.getFloat(KEY_LATITUDE, DEFAULT_LATITUDE.toFloat())
        val longitude = prefs.getFloat(KEY_LONGITUDE, DEFAULT_LONGITUDE.toFloat())
        val hookEnabled = prefs.getBoolean(KEY_HOOK_ENABLED, true)

        etLatitude.setText(latitude.toString())
        etLongitude.setText(longitude.toString())
        switchEnableHook.isChecked = hookEnabled
    }

    private fun setupListeners() {
        btnSave.setOnClickListener {
            saveSettings()
        }

        btnReset.setOnClickListener {
            resetToDefault()
        }

        switchEnableHook.setOnCheckedChangeListener { _, isChecked ->
            handleHookSwitch(isChecked)
        }
    }

    /**
     * 处理模拟开关的切换
     */
    private fun handleHookSwitch(enabled: Boolean) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean(KEY_HOOK_ENABLED, enabled)
            apply()
        }

        makeWorldReadable()

        if (enabled) {
            // 开启模拟时，清空基站缓存
            clearCellLocationCache()
            Toast.makeText(this, "已启用虚拟位置模拟，并清空基站缓存", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "已禁用虚拟位置模拟", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 使用 root 权限清空基站缓存
     */
    private fun clearCellLocationCache() {
        if (!isRooted()) {
            Toast.makeText(this, "需要 Root 权限来清空基站缓存", Toast.LENGTH_SHORT).show()
            return
        }

        Thread {
            try {
                val process = Runtime.getRuntime().exec("su")
                val os = DataOutputStream(process.outputStream)

                // 清空基站缓存相关的系统数据
                os.writeBytes("rm -rf /data/data/com.android.phone/databases/*\n")
                os.writeBytes("rm -rf /data/data/com.android.phone/cache/*\n")
                os.writeBytes("rm -rf /data/data/com.android.phone/shared_prefs/*\n")

                // 清空各个地图应用的缓存和数据（包括定位缓存）
                os.writeBytes("rm -rf /data/data/com.baidu.BaiduMap/cache/*\n")
                os.writeBytes("rm -rf /data/data/com.baidu.BaiduMap/databases/*\n")
                os.writeBytes("rm -rf /data/data/com.baidu.BaiduMap/files/locSDK*\n")

                os.writeBytes("rm -rf /data/data/com.tencent.map/cache/*\n")
                os.writeBytes("rm -rf /data/data/com.tencent.map/databases/*\n")
                os.writeBytes("rm -rf /data/data/com.tencent.map/files/tencent*\n")

                os.writeBytes("rm -rf /data/data/com.autonavi.minimap/cache/*\n")
                os.writeBytes("rm -rf /data/data/com.autonavi.minimap/databases/*\n")

                // 清空系统定位服务缓存
                os.writeBytes("rm -rf /data/system/location/*\n")
                os.writeBytes("rm -rf /data/misc/location/*\n")

                // 停止并清空定位相关服务
                os.writeBytes("pm clear com.android.phone\n")

                // 重启无线电和定位服务（更彻底）
                os.writeBytes("setprop ctl.restart radio\n")
                os.writeBytes("setprop ctl.restart location\n")

                // 强制停止目标应用
                os.writeBytes("am force-stop com.baidu.BaiduMap\n")
                os.writeBytes("am force-stop com.tencent.map\n")
                os.writeBytes("am force-stop com.autonavi.minimap\n")

                os.writeBytes("exit\n")
                os.flush()
                process.waitFor()

                runOnUiThread {
                    Toast.makeText(this, "已清空基站和定位缓存，请重启地图应用", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this, "清空基站缓存失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun saveSettings() {
        val latitudeStr = etLatitude.text.toString().trim()
        val longitudeStr = etLongitude.text.toString().trim()

        if (latitudeStr.isEmpty() || longitudeStr.isEmpty()) {
            Toast.makeText(this, "请输入完整的经纬度信息", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val latitude = latitudeStr.toDouble()
            val longitude = longitudeStr.toDouble()

            // 验证经纬度范围
            if (latitude < -90 || latitude > 90) {
                Toast.makeText(this, "纬度范围应在 -90 到 90 之间", Toast.LENGTH_SHORT).show()
                return
            }

            if (longitude < -180 || longitude > 180) {
                Toast.makeText(this, "经度范围应在 -180 到 180 之间", Toast.LENGTH_SHORT).show()
                return
            }

            // 保存设置
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().apply {
                putFloat(KEY_LATITUDE, latitude.toFloat())
                putFloat(KEY_LONGITUDE, longitude.toFloat())
                apply()
            }

            // 通知 ContentProvider 数据已更新（通过发送广播或其他方式）
            // ContentProvider 会在被查询时自动读取最新的 SharedPreferences 数据

            // 如果开关已启用，清空基站缓存
            if (switchEnableHook.isChecked) {
                clearCellLocationCache()
            }

            Toast.makeText(this, "保存成功！配置已通过 ContentProvider 共享\n请重启目标应用使其生效", Toast.LENGTH_LONG).show()

        } catch (e: NumberFormatException) {
            Toast.makeText(this, "请输入有效的数字", Toast.LENGTH_SHORT).show()
        }
    }

    private fun resetToDefault() {
        etLatitude.setText(DEFAULT_LATITUDE.toString())
        etLongitude.setText(DEFAULT_LONGITUDE.toString())

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putFloat(KEY_LATITUDE, DEFAULT_LATITUDE.toFloat())
            putFloat(KEY_LONGITUDE, DEFAULT_LONGITUDE.toFloat())
            apply()
        }

        // 如果开关已启用，清空基站缓存
        if (switchEnableHook.isChecked) {
            clearCellLocationCache()
        }

        Toast.makeText(this, "已恢复默认位置（zhongnanhai）", Toast.LENGTH_SHORT).show()
    }


    @Deprecated("不再需要，已改用 ContentProvider")
    private fun makeWorldReadable() {
        // 使用 ContentProvider 后，不再需要手动设置文件权限
        // ContentProvider 会自动处理跨进程访问
        android.util.Log.d("GpsHook", "Using ContentProvider, no need to set file permissions")
    }
}
