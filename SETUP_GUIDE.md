# 🔧 国内环境配置指南

## 问题说明

在国内网络环境下，无法直接访问 `dl.google.com` 下载 Android SDK 组件。

## 解决方案

### 方案一：配置 Android Studio 使用国内镜像（推荐）

1. **打开 Android Studio**
2. **进入设置**: `File` → `Settings` (或 `Ctrl+Alt+S`)
3. **找到 SDK 设置**: `Appearance & Behavior` → `System Settings` → `Android SDK`
4. **配置 HTTP Proxy**:
   - 进入 `Appearance & Behavior` → `System Settings` → `HTTP Proxy`
   - 选择 `Manual proxy configuration`
   - 配置代理（如果有的话），或者使用国内镜像

### 方案二：手动下载 SDK 组件

1. **查看需要的 SDK 版本**:
   - SDK Platform: Android 13 (API 33)
   - Build Tools: 33.0.0

2. **下载地址**（使用迅雷或 IDM）:
   - SDK Platform 33: `https://dl.google.com/android/repository/platform-33-ext5_r04.zip`
   - Build Tools 33.0.0: `https://dl.google.com/android/repository/build-tools_r33-windows.zip`

3. **安装位置**:
   ```
   %LOCALAPPDATA%\Android\Sdk\platforms\android-33\
   %LOCALAPPDATA%\Android\Sdk\build-tools\33.0.0\
   ```

### 方案三：使用国内镜像站点

#### 阿里云镜像
```bash
# 在 Android Studio 的 Terminal 中执行
mkdir -p %USERPROFILE%\.android
echo "### Generic template
# Host name
### setting for aliyun mirror
sdkman.use.preferred=true
" > %USERPROFILE%\.android\settings
```

#### 使用 AndroidDevTools
访问 https://www.androiddevtools.cn/ 下载离线包

### 方案四：修改 hosts 文件（临时方案）

1. 以管理员身份打开记事本
2. 打开文件: `C:\Windows\System32\drivers\etc\hosts`
3. 添加以下内容:
```
203.208.41.37 dl.google.com
203.208.41.37 dl-ssl.google.com
```
4. 保存后刷新 DNS: `ipconfig /flushdns`

## 验证配置

配置完成后，在 Android Studio 中:

1. 点击 `File` → `Sync Project with Gradle Files`
2. 等待同步完成
3. 点击 `Build` → `Clean Project`
4. 点击 `Build` → `Rebuild Project`

## 常见问题

### Q: 提示 "Failed to find Build Tools"
A: 需要安装对应版本的 Build Tools。在 `SDK Manager` → `SDK Tools` 中勾选 `Android SDK Build-Tools` 并安装。

### Q: Gradle Sync 超时
A: 检查网络连接，或配置代理。也可以尝试使用移动热点。

### Q: 找不到 local.properties
A: 这个文件是自动生成的。如果没有，Android Studio 会提示你配置 SDK路径。

## 联系支持

如仍有问题，请提供:
1. Android Studio 版本
2. 错误日志（`Help` → `Show Log in Explorer`）
3. 网络环境说明
