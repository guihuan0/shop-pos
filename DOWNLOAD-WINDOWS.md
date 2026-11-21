# Windows 本地编译 · 完整下载与操作清单

全部直链已实测返回 HTTP 200 并读到真实 Content-Length（2026-11-22 验证）。

## 一、要下载的东西（共 3 个包，435 MB）

| # | 文件 | 大小 | 直链 |
|---|---|---|---|
| 1 | `OpenJDK17U-jdk_x64_windows_hotspot_17.0.21.1_2.msi` | 168,755,200 B（161 MB） | `https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.21.1%2B2/OpenJDK17U-jdk_x64_windows_hotspot_17.0.21.1_2.msi` |
| 2 | `commandlinetools-win-11076708_latest.zip` | 153,583,359 B（146 MB） | `https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip` |
| 3 | `gradle-8.7-bin.zip` | 134,184,980 B（128 MB） | `https://mirrors.cloud.tencent.com/gradle/gradle-8.7-bin.zip` |

备用源（Gradle 官方，会 302 到 GitHub）：
`https://services.gradle.org/distributions/gradle-8.7-bin.zip`

> 同一目录下的 `urls.txt` 可以直接在 IDM 里 `任务 → 从文件导入`，一次把三个都排进队列。

**不需要下载**：Android Studio、Android SDK 平台包、build-tools——这些第 4 步用 `sdkmanager` 自己下（它会自己解析正确的包名，比手拼 URL 靠谱）。

## 二、安装（按顺序，每步有验证方法）

### ① 装 JDK 17

双击 `OpenJDK17U-jdk_x64_windows_hotspot_17.0.21.1_2.msi`，安装时**勾上这两个**：
- `Set JAVA_HOME variable`
- `Add to PATH`

验证（开新的 cmd 窗口）：
```
java -version
```
成功：输出 `openjdk version "17.0.21.1"` 这样一行。
失败：`'java' 不是内部或外部命令` → 环境变量没生效，重启一次 cmd，或手动加 `JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.21.1.2-hotspot`。

### ② 放好 cmdline-tools

```
mkdir C:\Android\Sdk\cmdline-tools
```
把 `commandlinetools-win-11076708_latest.zip` 解压，会得到一层 `cmdline-tools` 目录。把**里面**的内容移到 `C:\Android\Sdk\cmdline-tools\latest\`。

最终必须长这样（`latest` 这一层名字不能改，sdkmanager 认它）：
```
C:\Android\Sdk\cmdline-tools\latest\bin\sdkmanager.bat
C:\Android\Sdk\cmdline-tools\latest\bin\avdmanager.bat
```

验证：
```
dir C:\Android\Sdk\cmdline-tools\latest\bin\sdkmanager.bat
```
成功：列出这个文件。

### ③ 装 Android SDK 组件

```
set ANDROID_HOME=C:\Android\Sdk
C:\Android\Sdk\cmdline-tools\latest\bin\sdkmanager.bat --sdk_root=C:\Android\Sdk --licenses
```
一路输入 `y` 接受所有协议。

然后装三个组件：
```
C:\Android\Sdk\cmdline-tools\latest\bin\sdkmanager.bat --sdk_root=C:\Android\Sdk "platform-tools" "platforms;android-34" "build-tools;34.0.0"
```
约 200–300 MB，走 dl.google.com，国内通常 1–5 MB/s。

验证：
```
dir C:\Android\Sdk\platforms\android-34\android.jar
dir C:\Android\Sdk\build-tools\34.0.0\aapt2.exe
```
两个都存在就对了。失败信号：`Warning: Failed to find package platforms;android-34` → 多半是 ② 的目录层级错了（少了 `latest` 那层）。

### ④ 解压 Gradle

把 `gradle-8.7-bin.zip` 解压到 `C:\Gradle\`，得到 `C:\Gradle\gradle-8.7\`。

验证：
```
C:\Gradle\gradle-8.7\bin\gradle.bat -v
```
成功：输出 `Gradle 8.7`，且 `JVM: 17.0.21.1`。

### ⑤ 告诉工程 SDK 在哪

在 `F:\999\shop-pos\` 下新建文件 `local.properties`，内容一行：
```
sdk.dir=C:/Android/Sdk
```
（正斜杠，不要用反斜杠，不用转义）

### ⑥ 编译

```
cd /d F:\999\shop-pos
C:\Gradle\gradle-8.7\bin\gradle.bat assembleDebug --stacktrace
```

首次会下载依赖（AGP、Kotlin、Compose、Room 等约 300–500 MB）。工程里已经配好阿里云 maven 镜像，国内速度快很多。**首次 15–40 分钟正常**，第二次起 1–2 分钟。

成功信号：
```
BUILD SUCCESSFUL in 12m 34s
```
且文件存在：
```
dir F:\999\shop-pos\app\build\outputs\apk\debug\app-debug.apk
```

### ⑦ 装到手机

```
adb install -r F:\999\shop-pos\app\build\outputs\apk\debug\app-debug.apk
```
（`adb` 在 `C:\Android\Sdk\platform-tools\adb.exe`；手机要先开 USB 调试）

或者直接把 `app-debug.apk` 拷到手机点击安装，允许"未知来源"。

## 三、编译报错怎么定位

| 报错片段 | 原因 | 处理 |
|---|---|---|
| `SDK location not found` | 缺 `local.properties` 或路径写错 | 回到 ⑤ |
| `Unsupported class file major version` | Gradle 跑在旧 JDK 上 | `gradle -v` 看 JVM 是不是 17 |
| `Failed to find target with hash string 'android-34'` | SDK 组件没装全 | 回到 ③ |
| `Could not resolve androidx.compose...` | 网络/镜像问题 | 检查 `settings.gradle.kts` 里阿里云镜像是否在；或挂代理重试 |
| `Cannot find implementation for com.fl.pos.PosDb` | KSP 没跑起来 | 确认 `app/build.gradle.kts` 里有 `ksp("androidx.room:room-compiler:2.6.1")` 且插件是 `com.google.devtools.ksp` |
| `Unresolved reference: HorizontalDivider` | Compose BOM 版本不对 | 确认是 `2024.06.00` |
| 编译过了但装上闪退 | 真机问题 | 用 `adb logcat | findstr AndroidRuntime` 抓堆栈发我 |

## 四、不想碰命令行的替代方案

装 **Android Studio**（约 1.2 GB）：`https://developer.android.com/studio`
里面 `File → Open` 选 `F:\999\shop-pos` → 自动 Sync → `Build → Build APK(s)`。
SDK 由 Studio 的 SDK Manager 图形界面装，不用手打 sdkmanager 命令。

代价是多下 800 MB，好处是零命令行。
