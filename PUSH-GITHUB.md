# 用 GitHub 云端编译出 APK（本机什么都不用装）

本机没有 git、没有 JDK、没有 Android SDK，**全部不需要**。GitHub 的服务器有这些，免费给你用。

---

## 第一步：建仓库

打开 https://github.com/new

- **Repository name**：随便，比如 `shop-pos`
- **Public / Private**：选 **Public**
  （Private 也能用，但 Actions 需要额外去 Settings → Actions → General 里点开允许，多一步）
- 不要勾 "Add a README file"、不要选 .gitignore 模板
- 点 **Create repository**

---

## 第二步：上传文件

进到刚建好的空仓库页面，点 **uploading an existing file** 这个链接。

然后打开 `F:\999\shop-pos\`，**全选里面的所有文件和文件夹**（不是选中 shop-pos 这一层），拖到浏览器上传区。

> 必须包含 `.github` 这个文件夹，Actions 的配置就在里面。
> Windows 资源管理器默认可能看不到以点开头的项——如果 `.github` 没显示，在资源管理器「查看」里勾上「隐藏的项目」。

上传区应该出现这些条目：

```
.github/
app/
.gitignore
build.gradle.kts
gradle.properties
README.md
settings.gradle.kts
```

**不要上传** `local.properties`（里面有 Windows 路径，会覆盖云端的 SDK 路径）、`urls.txt`、`DOWNLOAD-WINDOWS.md`、`shop-pos.zip`。漏传了也没事——下面的 workflow 里有 `rm -f local.properties` 兜底。

底下 Commit changes 框随便写一句，点 **Commit changes**。

---

## 第三步：等 Actions 跑

1. 点仓库顶部的 **Actions** 标签。
2. 左边应该有一个叫 **build-apk** 的 workflow，右边有一次运行，黄色转圈 = 正在跑。
3. 点进去，能看到实时日志。整体约 **6–10 分钟**（首次依赖全下，后续有缓存会快）。

每一步的意义：

| 步骤 | 正常表现 | 挂了说明什么 |
|---|---|---|
| Setup JDK 17 | 绿 | — |
| Setup Android SDK | 绿 | — |
| Install SDK packages | 下载 200–300 MB | 网络问题，重跑一次 |
| Install Gradle 8.7 | 下载 128 MB | 同上 |
| **Build debug APK** | **这一步是关键** | 代码/依赖有问题，日志里会有 `error:` |
| Upload APK | 绿 | 说明 APK 生成成功 |

---

## 第四步：把 APK 弄下来装到手机

1. 在跑完的那次运行页面，**滚到最底部**，找到 **Artifacts** 区域。
2. 点 **shop-pos-debug-apk** 下载（得到的是一个 zip）。
3. 解压，里面就是 `app-debug.apk`。
4. 传到安卓手机（微信文件传输助手 / QQ / 数据线都行）。
5. 手机上点开安装，允许"安装未知来源应用"。

---

## 出错了怎么办

黄色三角 = 失败。点进失败的那一步，日志最底下会有：

```
FAILURE: Build failed with an exception.
* What went wrong:
...
```

**把从 `* What went wrong:` 开始到日志结尾的整段贴给我**，不用自己判断。常见几类：

| 日志里的关键字 | 含义 |
|---|---|
| `Unresolved reference:` | 我代码里的 API 名字写错了，告诉我哪一行 |
| `Cannot find implementation for com.fl.pos.PosDb` | KSP 没生成 Room 代码 |
| `Could not resolve androidx...` | 依赖下载失败，重跑 workflow 即可（右上角 Re-run all jobs） |
| `Compilation error. See log for more details` | Kotlin 语法错误，会指出文件:行号 |
| `SDK location not found` | `local.properties` 被上传了，删掉仓库里那个文件重跑 |

---

## 装到手机之后怎么验

按这四关走，每关有明确判据：

1. **开台** — 点"开台"，输个桌号，顶部应该显示 `单号 1 · 桌 A1`
2. **点菜合计** — 去"菜品"页先加两道菜（比如 鱼香肉丝 28、米饭 2），回"点单"点它们，明细应该出现 `鱼香肉丝 28.00 x 1 = 28.00`，底部合计同步变
3. **蓝牙授权** — 点"选设备"，系统应弹出蓝牙权限请求；允许后能看到你手机里已配对的设备名
4. **打票** — 选中小票机 → 点"结账打票" → 选微信 → 出票，中文不是方块

第 3 关如果列表是空的：先去**手机系统设置 → 蓝牙**里把打印机配对好，再回 App。

---

## 以后要改代码

网页改：仓库里点开文件 → 右上角铅笔图标 → 改完 Commit，Actions 会自动重跑。
或者装 [GitHub Desktop](https://desktop.github.com/)，克隆到本地改，提交后同样自动触发。
