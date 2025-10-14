#!/system/bin/sh
# 测试配置文件权限脚本

PREFS_FILE="/data/data/com.example.xposedgpshook/shared_prefs/gps_hook_prefs.xml"

echo "========================================"
echo "检查 Xposed GPS Hook 配置文件权限"
echo "========================================"

# 检查文件是否存在
if [ -f "$PREFS_FILE" ]; then
    echo "✓ 配置文件存在: $PREFS_FILE"
else
    echo "✗ 配置文件不存在: $PREFS_FILE"
    exit 1
fi

# 显示文件权限
echo ""
echo "文件权限信息:"
ls -l "$PREFS_FILE"

# 显示 SELinux 上下文
echo ""
echo "SELinux 上下文:"
ls -Z "$PREFS_FILE"

# 检查目录权限
echo ""
echo "目录权限信息:"
ls -ld "/data/data/com.example.xposedgpshook"
ls -ld "/data/data/com.example.xposedgpshook/shared_prefs"

# 尝试读取文件内容
echo ""
echo "文件内容预览:"
cat "$PREFS_FILE"

echo ""
echo "========================================"
echo "建议的修复命令（如果权限不正确）:"
echo "========================================"
echo "chmod 755 /data/data/com.example.xposedgpshook"
echo "chmod 755 /data/data/com.example.xposedgpshook/shared_prefs"
echo "chmod 644 $PREFS_FILE"
echo "chcon u:object_r:app_data_file:s0 $PREFS_FILE"

