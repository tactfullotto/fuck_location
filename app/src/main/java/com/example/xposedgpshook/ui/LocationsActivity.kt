package com.example.xposedgpshook.ui

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.Window
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.xposedgpshook.R
import com.google.android.material.textfield.TextInputEditText

class LocationsActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: LocationAdapter
    private lateinit var locationStorage: LocationStorage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_locations)

        locationStorage = LocationStorage(this)
        val locations = locationStorage.getLocations().toMutableList()

        recyclerView = findViewById(R.id.rv_locations)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = LocationAdapter(locations,
            onItemClick = { location ->
                val resultIntent = Intent()
                resultIntent.putExtra("latitude", location.latitude)
                resultIntent.putExtra("longitude", location.longitude)
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            },
            onEditClick = { location, position ->
                showEditLocationDialog(location, position)
            },
            onDeleteClick = { location ->
                locationStorage.removeLocation(location)
                adapter.removeLocation(location)
            }
        )
        recyclerView.adapter = adapter

        findViewById<Button>(R.id.btn_add_location).setOnClickListener {
            showAddLocationDialog()
        }
    }

    private fun showAddLocationDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_edit_location)

        // 设置对话框窗口参数
        dialog.window?.let { window ->
            val layoutParams = window.attributes
            layoutParams.width = (resources.displayMetrics.widthPixels * 0.9).toInt()
            layoutParams.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            window.attributes = layoutParams
        }

        val etName = dialog.findViewById<TextInputEditText>(R.id.et_location_name)
        val etLatitude = dialog.findViewById<TextInputEditText>(R.id.et_latitude)
        val etLongitude = dialog.findViewById<TextInputEditText>(R.id.et_longitude)
        val btnCancel = dialog.findViewById<Button>(R.id.btn_cancel)
        val btnSave = dialog.findViewById<Button>(R.id.btn_save)

        // 获取从 MainActivity 传递过来的经纬度
        val currentLat = intent.getDoubleExtra("current_latitude", 0.0)
        val currentLng = intent.getDoubleExtra("current_longitude", 0.0)

        // 如果经纬度有效，则填充
        if (currentLat != 0.0 || currentLng != 0.0) {
            etLatitude.setText(currentLat.toString())
            etLongitude.setText(currentLng.toString())
        }

        // 不填充任何默认值，让用户输入新位置

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnSave.setOnClickListener {
            val name = etName.text.toString().trim()
            val latStr = etLatitude.text.toString().trim()
            val lngStr = etLongitude.text.toString().trim()

            if (name.isEmpty()) {
                Toast.makeText(this, "请输入位置名称", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val latitude = latStr.toDoubleOrNull()
            val longitude = lngStr.toDoubleOrNull()

            if (latitude == null || longitude == null) {
                Toast.makeText(this, "请输入有效的经纬度", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (latitude < -90 || latitude > 90) {
                Toast.makeText(this, "纬度范围应在 -90 到 90 之间", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (longitude < -180 || longitude > 180) {
                Toast.makeText(this, "经度范围应在 -180 到 180 之间", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 添加新位置
            val newLocation = LocationItem(name, latitude, longitude)
            locationStorage.addLocation(newLocation)
            adapter.addLocation(newLocation)

            Toast.makeText(this, "位置已添加", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showEditLocationDialog(location: LocationItem, position: Int) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_edit_location)

        // 设置对话框窗口参数，确保在小屏幕设备上也能正常显示
        dialog.window?.let { window ->
            val layoutParams = window.attributes
            layoutParams.width = (resources.displayMetrics.widthPixels * 0.9).toInt()
            layoutParams.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            window.attributes = layoutParams
        }

        val etName = dialog.findViewById<TextInputEditText>(R.id.et_location_name)
        val etLatitude = dialog.findViewById<TextInputEditText>(R.id.et_latitude)
        val etLongitude = dialog.findViewById<TextInputEditText>(R.id.et_longitude)
        val btnCancel = dialog.findViewById<Button>(R.id.btn_cancel)
        val btnSave = dialog.findViewById<Button>(R.id.btn_save)

        // 填充当前位置信息
        etName.setText(location.name)
        etLatitude.setText(location.latitude.toString())
        etLongitude.setText(location.longitude.toString())

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnSave.setOnClickListener {
            val name = etName.text.toString().trim()
            val latStr = etLatitude.text.toString().trim()
            val lngStr = etLongitude.text.toString().trim()

            if (name.isEmpty()) {
                Toast.makeText(this, "请输入位置名称", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val latitude = latStr.toDoubleOrNull()
            val longitude = lngStr.toDoubleOrNull()

            if (latitude == null || longitude == null) {
                Toast.makeText(this, "请输入有效的经纬度", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (latitude < -90 || latitude > 90) {
                Toast.makeText(this, "纬度范围应在 -90 到 90 之间", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (longitude < -180 || longitude > 180) {
                Toast.makeText(this, "经度范围应在 -180 到 180 之间", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 保存原始位置信息用于更新存储
            val oldLocation = location.copy()

            // 更新位置信息
            val updatedLocation = LocationItem(name, latitude, longitude)
            locationStorage.updateLocation(oldLocation, updatedLocation)
            adapter.updateLocation(position, updatedLocation)

            Toast.makeText(this, "位置已更新", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }
}
