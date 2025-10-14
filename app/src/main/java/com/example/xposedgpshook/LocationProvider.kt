package com.example.xposedgpshook

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri

/**
 * LocationProvider - 用于跨进程共享位置配置数据
 *
 * 这个 ContentProvider 允许目标应用（如地图 App）通过 Xposed Hook
 * 安全地读取我们设置的虚假位置坐标，而不需要直接访问文件系统。
 */
class LocationProvider : ContentProvider() {

    companion object {
        // ContentProvider 的 Authority，必须与 AndroidManifest.xml 中声明的一致
        const val AUTHORITY = "com.example.xposedgpshook.provider"

        // 完整的 Content URI
        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/location")

        // 列名定义
        const val COLUMN_LATITUDE = "latitude"
        const val COLUMN_LONGITUDE = "longitude"
        const val COLUMN_ENABLED = "enabled"

        // URI 匹配器
        private const val LOCATION = 1
        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, "location", LOCATION)
        }
    }

    override fun onCreate(): Boolean {
        // 初始化，返回 true 表示成功
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        return when (uriMatcher.match(uri)) {
            LOCATION -> {
                // 从 SharedPreferences 读取配置
                val prefs = context?.getSharedPreferences(
                    MainActivity.PREFS_NAME,
                    Context.MODE_PRIVATE
                ) ?: return null

                val latitude = prefs.getFloat(
                    MainActivity.KEY_LATITUDE,
                    MainActivity.DEFAULT_LATITUDE.toFloat()
                ).toDouble()

                val longitude = prefs.getFloat(
                    MainActivity.KEY_LONGITUDE,
                    MainActivity.DEFAULT_LONGITUDE.toFloat()
                ).toDouble()

                val enabled = prefs.getBoolean(
                    MainActivity.KEY_HOOK_ENABLED,
                    true
                )

                // 创建一个 MatrixCursor 返回数据
                val cursor = MatrixCursor(arrayOf(
                    COLUMN_LATITUDE,
                    COLUMN_LONGITUDE,
                    COLUMN_ENABLED
                ))

                cursor.addRow(arrayOf(latitude, longitude, if (enabled) 1 else 0))

                cursor
            }
            else -> null
        }
    }

    override fun getType(uri: Uri): String? {
        return when (uriMatcher.match(uri)) {
            LOCATION -> "vnd.android.cursor.item/vnd.$AUTHORITY.location"
            else -> null
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        // 不支持插入操作
        throw UnsupportedOperationException("Insert not supported")
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        // 不支持删除操作
        throw UnsupportedOperationException("Delete not supported")
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        // 不支持更新操作
        throw UnsupportedOperationException("Update not supported")
    }
}

