# BiliLive-YOKONEX-App

`BiliLive-YOKONEX-App` 是一个面向 Android 的 B 站直播互动客户端，目标是在移动端独立完成直播事件监听、规则命中和输出触发，不依赖桌面端本地服务。

当前工程采用 `Kotlin + Jetpack Compose + Material 3` 构建 Android 客户端，并通过 `Chaquopy` 集成部分 Python 运行时能力，用于对接第三方直播消息流。

## 当前能力

- 直播监听前台服务骨架
- 仪表盘、直播配置、输出配置、规则、日志、波形库等页面结构
- B 站直播事件模型与规则匹配逻辑
- 蓝牙 EMS 设备接入与协议封包
- 下游 WebSocket 指令发送
- 内置波形库与波形可视化编辑基础能力

## 技术栈

- Kotlin
- Jetpack Compose
- Material 3
- Navigation Compose
- Room
- DataStore
- OkHttp
- kotlinx.serialization
- Chaquopy
- Python 3.11

## 环境要求

- Android Studio 最新稳定版
- JDK 17
- Android SDK 35
- Python 3.11

当前 `app/build.gradle.kts` 默认使用以下 Python 解释器路径：

```text
C:/Users/hosgoo/AppData/Local/Programs/Python/Python311/python.exe
```

如果你的环境不同，请先按本机路径修改 `buildPython(...)` 配置。

## 快速开始

1. 安装 Android Studio、JDK 17 和 Python 3.11。
2. 确认本机已安装 Android SDK 35。
3. 根据需要调整 `app/build.gradle.kts` 中的 `buildPython(...)` 路径。
4. 在项目根目录创建 `local.properties`，让 Android Studio 指向本机 SDK。
5. 使用 Android Studio 打开项目，等待 Gradle 同步完成。
6. 运行以下命令构建调试包：

```powershell
.\gradlew.bat assembleDebug
```

## 签名配置

发布构建会优先读取根目录下的 `keystore.properties`。如果该文件不存在，当前工程会回退到调试签名配置。

`keystore.properties` 预期包含以下字段：

```properties
storeFile=your-release-key.jks
storePassword=***
keyAlias=***
keyPassword=***
```

请不要将签名文件和密钥配置提交到仓库。

## 目录结构

```text
app/
  src/main/java/com/yokonex/bililive/
    app/            Compose 页面、导航与界面状态
    data/           直播、蓝牙、WebSocket、存储实现
    domain/         领域模型、规则与用例
    service/        前台服务与运行协调
  src/main/python/
    live_runtime/   第三方直播消息运行时

docs/superpowers/
  specs/            设计文档
  plans/            实施计划
```

## 相关说明

- `minSdk` 为 29，`targetSdk` 与 `compileSdk` 为 35。
- 当前仓库包含单元测试与部分 Android UI 测试代码。
- 项目默认使用 UTF-8 编码。

