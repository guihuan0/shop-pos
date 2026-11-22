# 小店点单 · shop-pos

安卓单机餐饮点单/收银 App。无服务器、无扫码点单、无联网。数据存本机 SQLite。

## 功能

**点单页**
- 开台（可填桌号）
- 点菜品卡片，每点一下数量 +1，同名菜自动合并
- 明细行显示 `菜名  单价 x 数量 = 小计`，可 -/+/删
- 底部实时合计
- 结账：选现金/微信/支付宝/银行卡 → 落账 → 自动打小票
- 作废单（不计入收入）

**日报页**
- 今日收入合计、客单数、客单价
- 按支付方式拆分：现金/微信各多少元、多少单
- 菜品销量排行（份数 + 金额）
- 今日已结单列表，点任意一行重打那张小票
- 一键打印"营业日报"纸单，给老板对账

**设置页**
- **台号管理**：预设台号（A1 / A2 / 大厅1 …），开台时点一下就行，不用每次手输；点台号可删除
- 菜品管理：增删菜品（菜名、单价、分类）

## 出 APK 的两条路

### A. 云端编译（不用在本机装任何东西，推荐）

1. 在 GitHub 新建一个空仓库（私有公开都行）。
2. 把这个目录的全部文件推上去：
   ```bash
   cd shop-pos
   git init
   git add .
   git commit -m "init"
   git branch -M main
   git remote add origin https://github.com/<你的用户名>/<仓库名>.git
   git push -u origin main
   ```
3. 打开仓库的 **Actions** 标签，等 `build-apk` 跑完（约 5-8 分钟，首次会下载依赖）。
4. 点进那次运行，页面底部 **Artifacts** 下载 `shop-pos-debug-apk`，解压得到 `app-debug.apk`。
5. 传到安卓手机安装（需允许"安装未知来源应用"）。

工作流文件已在 `.github/workflows/build.yml`，不需要改。

### B. 本地编译（需要 Android Studio）

1. 安装 [Android Studio](https://developer.android.com/studio)（含 JDK 17 和 Android SDK）。
2. `File → Open` 选这个 `shop-pos` 目录，等 Gradle Sync 完成。
3. 菜单 `Build → Build Bundle(s)/APK(s) → Build APK(s)`。
4. 产物：`app/build/outputs/apk/debug/app-debug.apk`。

命令行方式（已装好 SDK 和 JDK17）：
```bash
gradle wrapper --gradle-version 8.7
./gradlew assembleDebug
```

## 更新 APK（不会丢数据）

仓库里固定了 `app/debug.keystore`，所以每次 GitHub 构建出的 APK **签名一致**，可以直接覆盖安装：

```bash
adb install -r app-debug.apk
```

或者把新 APK 传到手机点安装——系统会提示「更新」而不是「安装」。

**不要先卸载**。卸载会清空 Room 数据库（全部订单）和 SharedPreferences（预设台号），归零。

> 这个坑踩过：不加固定 keystore 的话，GitHub runner 每次构建都会随机生成 `~/.android/debug.keystore`，
> 新 APK 签名和旧版不匹配，`INSTALL_FAILED_UPDATE_INCOMPATIBLE`，只能卸载重装 —— 店里的历史数据全没了。
> 已通过 `signingConfigs.create("fixed")` + `buildTypes.debug.signingConfig` 显式指定修掉，
> CI 日志里会打印 keystore 和 APK 的实际 SHA-256 供核对。

## 硬件

小票打印走**蓝牙 ESC/POS**，需要一台 58mm 蓝牙热敏小票机：
- 芯烨 XP-58IIH、佳博 GP-58MB、爱宝 A-58 之类，100–250 元
- 先在手机系统蓝牙设置里和打印机配对，再进 App 点"选设备"

如果是商米/天波等自带打印机的安卓一体机，把 `Printer.kt` 里的 `BtPrinter.write()` 换成厂商 SDK（商米是 `com.sunmi:printerlibrary` 的 `SunmiPrinterService`）即可，其余界面和统计逻辑不用动。

## 关于中文乱码

小票用 GBK 编码。若打印出方块/乱码，把 `Printer.kt` 里 `Esc.text()` 的 `charset("GBK")` 改成 `charset("GB18030")`。国产 58mm 机基本两者都支持。

## 技术栈

Kotlin 2.0.20 · Jetpack Compose (BOM 2024.09) · Room 2.6.1 · KSP · minSdk 26 / targetSdk 34 · AGP 8.5.2 / Gradle 8.7
