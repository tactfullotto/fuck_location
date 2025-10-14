# Xposed GPS Hook 模块

这是一个 Xposed 模块，用于 Hook 系统的 GPS 定位功能，将所有应用获取的 GPS 位置替换为预设的位置。**支持通过界面自定义虚拟位置！**

## 功能特性

- ✅ **可视化界面设置虚拟位置**（无需修改代码）
- ✅ Hook `LocationManager.requestLocationUpdates()` 的多个重载方法
- ✅ Hook `LocationManager.getLastKnownLocation()`
- ✅ Hook `LocationManager.getCurrentLocation()` (Android 11+)
- ✅ Hook `Location.getLatitude()` 和 `Location.getLongitude()` 方法
- ✅ 对所有应用生效
- ✅ 支持 GPS 和 Network 定位提供者
- ✅ 位置设置实时保存，重启目标应用即生效

## 使用要求

- 已安装并激活 Xposed 框架（如 LSPosed、EdXposed 等）
- Android 12 (API 31) 或更高版本
- Root 权限

## 安装步骤

1. 编译并安装此 APK
2. 在 Xposed 管理器中启用此模块
3. 重启设备或重启相关应用的进程
4. 打开 GPS Hook 应用，设置你想要的虚拟位置
5. 打开任何使用 GPS 定位的应用，位置将被替换为你设置的位置

## 使用界面

### 主界面功能

启动应用后，你会看到一个简洁的设置界面：

- **纬度输入框**：输入目标位置的纬度（-90 到 90）
- **经度输入框**：输入目标位置的经度（-180 到 180）
- **保存设置按钮**：保存你设置的虚拟位置
- **恢复默认按钮**：恢复到默认位置（北京故宫）

### 常用位置示例

界面中提供了一些常用位置的坐标供参考：
- **北京故宫**：39.916345, 116.397155
- **上海东方明珠**：31.239663, 121.499809
- **广州塔**：23.108677, 113.319494

### 使用步骤

1. 打开 GPS Hook 应用
2. 输入你想要的纬度和经度
3. 点击"保存设置"按钮
4. 重启你要修改定位的目标应用
5. 目标应用将获取到你设置的虚拟位置

## 工作原理

此模块通过以下方式实现 GPS Hook：

1. **拦截位置监听器**：替换应用注册的 `LocationListener`，在位置更新时返回虚假位置
2. **拦截获取最后位置**：直接返回虚假位置对象
3. **拦截 Location 类方法**：直接修改 `getLatitude()` 和 `getLongitude()` 的返回值
4. **读取用户设置**：从 SharedPreferences 读取用户在界面设置的坐标

## 日志

模块会在 Xposed 日志中输出详细的 Hook 信息，可以通过 Xposed 管理器查看：

- 应用加载信息
- Hook 成功/失败信息
- 位置拦截信息
- 使用的坐标（自定义或默认）

## 技术细节

### 数据存储

- 使用 SharedPreferences 存储用户设置
- 存储模式：`MODE_WORLD_READABLE`，确保其他应用可以读取
- 配置文件：`gps_hook_prefs`
- 存储键：`fake_latitude`、`fake_longitude`

### Hook 实现

- 通过 Xposed 框架的 `XposedHelpers` 进行方法 Hook
- 支持在运行时动态读取用户设置的坐标
- 自动从 LocationManager 获取 Context 以读取配置

## 注意事项

- ⚠️ 此模块会影响系统所有应用的定位功能
- ⚠️ 某些应用可能会检测虚假位置，请谨慎使用
- ⚠️ 修改位置后需要重启目标应用才能生效
- ⚠️ 请确保输入的经纬度在有效范围内
- ⚠️ 仅用于学习和测试目的

## 编译

```bash
./gradlew assembleDebug
```

生成的 APK 位于：`app/build/outputs/apk/debug/app-debug.apk`

## 开发环境

- Android Studio
- Kotlin
- Gradle 8+
- Xposed API 82
- Material Design Components

## 许可证

本项目仅供学习交流使用。
