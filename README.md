# BiliLive-YOKONEX-App

`BiliLive-YOKONEX-App` 是一个面向 Android 的 B 站直播互动客户端，目标是在移动端独立完成直播事件监听、规则命中与输出触发，不依赖桌面端本地服务。

项目当前基于 `Kotlin + Jetpack Compose + Material 3` 构建 Android 客户端，并通过 `Chaquopy` 集成 Python 运行时，用于承载第三方直播消息接入链路。

## 项目定位

- 在 Android 设备上独立运行直播监听与事件处理流程
- 对接 B 站直播消息和第三方运行时能力
- 基于规则引擎将直播事件转换为下游输出指令
- 支持蓝牙 EMS 设备与 WebSocket 两类输出通道
- 提供波形库、日志、输出配置等配套管理界面

## 当前能力

- 前台直播监听服务与重启恢复机制
- 仪表盘、直播配置、输出配置、规则、日志、波形库页面
- B 站直播事件模型、分类与规则匹配逻辑
- Chaquopy + Python 第三方直播运行时接入
- 蓝牙 EMS 设备发现、分类、协议封包与混合波形输出
- WebSocket 下游指令发送能力
- 波形可视化编辑与波形库基础能力

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
- Python 3.12（推荐）

## 环境要求

- Android Studio 最新稳定版
- JDK 17
- Android SDK 35
- Python 3.12

当前工程配置如下：

- `minSdk = 29`
- `targetSdk = 35`
- `compileSdk = 35`
- 支持 ABI：`arm64-v8a`、`x86_64`

## 本地配置

### 1. 配置 Android SDK

在项目根目录创建或维护 `local.properties`：

```properties
sdk.dir=C:\\Users\\你的用户名\\AppData\\Local\\Android\\Sdk
```

### 2. 配置 Python 解释器

Chaquopy 构建阶段需要本机可用的 Python。项目会按以下顺序自动探测解释器：

1. 环境变量 `CHAQUOPY_BUILD_PYTHON`
2. `D:/Users/<当前用户名>/anaconda3/python.exe`
3. `C:/Users/<当前用户名>/AppData/Local/Programs/Python/Python312/python.exe`
4. `C:/Users/<当前用户名>/AppData/Local/Programs/Python/Python311/python.exe`

推荐显式设置环境变量，避免不同开发机路径差异影响 Gradle 同步：

```powershell
$env:CHAQUOPY_BUILD_PYTHON="C:\Users\你的用户名\AppData\Local\Programs\Python\Python312\python.exe"
```

如果未找到可用解释器，Gradle 会直接失败并提示设置 `CHAQUOPY_BUILD_PYTHON`。

### 3. 配置发布签名（可选）

发布构建会优先读取根目录下的 `keystore.properties`。如果该文件不存在，当前工程会回退到调试签名配置，便于本地联调。

`keystore.properties` 示例：

```properties
storeFile=your-release-key.jks
storePassword=***
keyAlias=***
keyPassword=***
```

不要将签名文件和密钥配置提交到仓库。

## 快速开始

1. 安装 Android Studio、JDK 17、Android SDK 35 和 Python 3.12。
2. 配置 `local.properties` 指向本机 Android SDK。
3. 配置 `CHAQUOPY_BUILD_PYTHON`，或确认自动探测路径中存在可用解释器。
4. 使用 Android Studio 打开项目并等待 Gradle 同步完成。
5. 连接 Android 设备或启动模拟器。
6. 执行调试构建或直接从 Android Studio 运行应用。

## 常用命令

### 调试构建

```powershell
.\gradlew.bat assembleDebug
```

### 安装调试包

```powershell
.\gradlew.bat installDebug
```

### 运行单元测试

```powershell
.\gradlew.bat testDebugUnitTest
```

### 运行 Android UI 测试

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

### 构建发布包

```powershell
.\gradlew.bat assembleRelease
```

## 权限与运行说明

应用当前依赖以下能力：

- 网络访问
- 前台服务
- 开机恢复监听
- 通知权限
- 蓝牙扫描与蓝牙连接
- 电池优化忽略申请

首次在 Android 12 及以上设备运行时，需要重点关注以下权限授权：

- `POST_NOTIFICATIONS`
- `BLUETOOTH_SCAN`
- `BLUETOOTH_CONNECT`

如果直播监听或蓝牙连接异常，优先检查系统权限、前台服务状态和电池优化限制。

## 项目结构

```text
app/
  src/main/java/com/yokonex/bililive/
    app/            Compose 页面、导航与界面状态
    data/           直播、蓝牙、WebSocket、存储实现
    domain/         领域模型、规则与用例
    service/        前台服务与运行恢复逻辑
  src/main/python/
    live_runtime/   第三方直播消息运行时
  src/test/         JVM 单元测试
  src/androidTest/  Android UI 测试

docs/superpowers/
  specs/            设计文档
  plans/            分阶段实施计划
```

## 测试现状

当前仓库已包含以下测试覆盖方向：

- 导航与页面 ViewModel 测试
- 蓝牙设备分类、协议封包与运行时测试
- B 站直播消息协议与房间客户端测试
- Python 第三方直播网关测试
- 仪表盘与波形页面的 Android UI 测试

## 常见问题

### Gradle 同步时报找不到 Python

优先检查：

- 是否安装了 Python 3.12
- `CHAQUOPY_BUILD_PYTHON` 是否指向真实存在的解释器
- 解释器路径是否包含空格、权限限制或被安全软件拦截

### 发布构建没有正式签名

如果根目录不存在 `keystore.properties`，工程会自动回退为调试签名。这是当前项目的本地开发兜底策略，不适合正式发布。

### 蓝牙功能无法工作

优先确认：

- 设备系统版本是否支持目标蓝牙权限模型
- 蓝牙是否已打开
- 应用是否已获得扫描和连接权限
- 设备是否被系统电池优化策略限制

## 相关文档

- 设计文档位于 [docs/superpowers/specs](./docs/superpowers/specs)
- 实施计划位于 [docs/superpowers/plans](./docs/superpowers/plans)

## 备注

- 项目默认使用 UTF-8 编码。
- 当前仓库同时包含 Android 侧逻辑与 Python 运行时代码。
- 如需补充接入流程、架构图或页面截图，建议继续在 `README` 中新增专项章节，而不是把细节堆叠到简介部分。
