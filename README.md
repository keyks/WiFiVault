# WiFiVault

把手机里的文件夹，通过局域网共享给电脑 / 平板 / 电视 / 另一部手机直接观看。
**手机自己就是服务器，共享与播放全程不需要互联网。** 网页界面与播放器都内置在 App 里，飞行模式或纯局域网下也能正常打开。

## 它能做什么

- 在手机上授权一个或多个文件夹（照片、视频、音乐、文档），只共享选中的目录；子目录可继续浏览，`.` 开头的隐藏文件不会列出
- 启动内置 HTTP 服务，同一 WiFi（或手机热点）下的设备用浏览器打开 `http://手机IP:端口` 即可浏览
- 浏览：共享文件夹下拉切换、面包屑导航、当前目录搜索、网格 / 列表视图切换；地址栏会带上当前路径（`#/库/路径`），刷新或分享链接可回到同一目录
- 视频：Range 拖动进度（断点续传）、0.5x–3x 倍速、长按临时倍速、全屏、字幕（自动匹配同名 `.srt` / `.vtt`，可开关）、进度记忆、播完自动连播
- 播放目录：播放器内列出当前目录和一层子目录里的视频，可跨目录切换与连播
- 图片：缩略图网格、大图缩放（按钮 / 滚轮）、左右切换（按钮 / 键盘 / 触摸滑动）；SVG 也可在大图中查看
- 音频：底部播放条，浏览其他目录时继续播放
- 文件预览：PDF 内嵌预览、文本 / 代码预览（最多显示前 256 KB）；无法预览的格式可直接下载
- 手势与键盘：手机端上下滑调音量、左右滑拖进度、长按倍速；播放器内支持空格暂停、方向键快进 / 调音量、`F` 全屏、`M` 静音、`[` `]` 切换视频、`Esc` 关闭
- 大屏 / 电视：文件卡片可聚焦并用 Enter 打开，播放器与预览支持方向键、Esc（实际体验取决于电视浏览器的焦点导航）
- 响应式：从 320px 手机到大屏电视自动调整列数、字号和控件换行，避免横向滚动和手动缩放
- 播放器显示模式：自动 / 完整 / 精简 / 原生控件；自动模式会在 MIUI、ColorOS、内置 WebView 等风险环境下降级为精简，也可在播放器内手动切换
- 常驻通知显示访问地址并可一键停止；通知栏快捷磁贴一键启停；切换网络后自动刷新地址；若上次退出时仍在共享，重新打开 App 会自动恢复
- 可选访问密码（PIN），防止同一网络下其他人随手打开

## 使用方式

1. 安装并打开 App
2. 点「共享文件夹」右侧的「添加」，在系统文件选择器里选中要共享的目录；要共享多个目录时重复添加（每次选择一个）
3. 点击底部的「启动共享」按钮
4. 其他设备连接到同一 WiFi，扫码或浏览器输入显示的地址（如 `http://192.168.1.23:8080`）
5. 右上角「设置」里可修改端口（默认 8080，范围 1024–65535）、访问密码、快进 / 快退秒数（默认 5）、长按倍速（默认 2.0x）、自动连播开关和播放器显示模式

> 共享由前台服务维持，退出界面后仍会继续运行；常驻通知里可随时停止。若被系统省电策略清理，共享会中断，建议允许后台运行或在任务列表锁定本应用。修改端口后需停止并重新启动共享；密码修改后观看端需重新输入。

## 编译

环境：Android Studio Ladybug 或更新版本 + JDK 17（项目使用 AGP 8.6.1、Gradle 8.9、Kotlin 2.0.21）。仓库已带 Gradle Wrapper，若 `gradle/wrapper/gradle-wrapper.jar` 缺失，再执行 `gradle wrapper` 补齐。

```bash
./gradlew assembleDebug     # 调试包
./gradlew assembleRelease   # 发布包（已配置签名）
```

Windows PowerShell 使用 `.\gradlew.bat assembleDebug` 等命令。

### 打包 APK

环境要求：JDK 17 + Android SDK（platform 34 / build-tools 34.0.0），并在 `local.properties` 里写 `sdk.dir=...`。

已经配好 release 签名（密钥 `app/wifivault-release.jks`，密码与别名均为 `wifivault`，仅用于自签分发）。该文件被 `.gitignore` 排除，不在仓库中；新电脑或 CI 构建前必须先从私密备份恢复，不能重新生成。一键打包：

```powershell
powershell -ExecutionPolicy Bypass -File tools\build-apk.ps1
```

脚本会先尝试离线构建，缓存不完整时自动联网重试。产物文件名自动跟随 `versionName`，当前为 `WiFiVault-1.2.0.apk`（release 约 11 MB），传到手机直接安装即可。
要发布到应用商店前，请自行替换 keystore 并修改 `app/build.gradle.kts` 里的 `signingConfigs`。版本号只需修改 `app/build.gradle.kts`，`MainScreen.kt` 与 APK 文件名会自动跟随；改版本后记得同步 `index.html` 里的资源版本号（`?v=`）。

`minSdk 26`（Android 8.0+），`targetSdk 34`。

### 本地预览网页端

调前端样式时不用反复装 APK，可以用内置的 mock 服务在电脑上直接看界面：

```bash
node tools/preview-server.js     # 然后浏览器打开 http://127.0.0.1:8899
```

该服务只返回固定的演示目录与占位缩略图，没有 `/api/config`、`/api/videos` 和真实文件内容，视频播放与下载会失败，仅适合预览布局和交互样式。

## 项目结构

```
app/src/main/java/com/wifivault/app/
├── MainActivity.kt              入口，Compose 宿主与文件夹选择
├── data/
│   ├── Settings.kt              端口 / PIN / 播放设置 / 已授权文件夹持久化
│   └── VaultRepository.kt       SAF 只读虚拟文件系统（目录遍历、缓存、Range 读取）
├── server/
│   ├── VaultHttpServer.kt       零依赖 HTTP 服务器（路由、断点续传、鉴权）
│   └── WebAssets.kt             静态资源 MIME 与类型判断
├── service/
│   ├── ServerService.kt         前台服务 + 常驻通知 + WakeLock/WiFiLock + 地址刷新
│   ├── ServerState.kt           运行状态（StateFlow）
│   └── VaultTileService.kt      通知栏快捷磁贴
├── ui/
│   ├── MainScreen.kt            主界面、设置、使用说明
│   └── theme/Theme.kt           主题
└── util/
    ├── Net.kt                   局域网 IP
    └── Thumbs.kt                图片 / 视频缩略图 + 内存缓存
app/src/main/assets/web/         内置网页端（index.html / app.css / app.js / lock.html / favicon.svg）
tools/                           build-apk.ps1 / preview-server.js
```

## HTTP 接口

| 路径 | 说明 |
| --- | --- |
| `GET /` | 网页端首页 |
| `GET /assets/*`、`/app.css`、`/app.js`、`/favicon.svg` | 内置静态资源 |
| `GET /api/ping` | 健康检查 |
| `GET /api/libraries` | 已共享文件夹列表 |
| `GET /api/config` | 播放器配置（快进步长、长按倍速、自动连播、显示模式） |
| `GET /api/list?lib=&path=` | 目录内容（JSON） |
| `GET /api/videos?lib=&path=` | 播放目录：当前目录 + 一层子目录的视频 |
| `GET /api/raw?lib=&path=` | 文件内容，支持 `Range` / `HEAD` |
| `GET /api/download?lib=&path=` | 附件下载 |
| `GET /api/thumb?lib=&path=` | 图片 / 视频缩略图（JPEG） |
| `GET /api/subtitle?lib=&path=` | 同名 `.srt` / `.vtt` 字幕（转换为 WebVTT） |
| `POST /api/login` | PIN 登录，成功后下发 Cookie |

设置 PIN 后，除 `POST /api/login` 外的接口都需要携带登录 Cookie；未登录的页面请求返回 `lock.html`，接口请求返回 `401` JSON，密码错误返回 `403`。

## 说明与限制

- 只读共享：当前版本不提供从网页端删除 / 上传文件
- HTTP 明文传输，PIN 只用于阻挡同网段的随手访问，不提供 HTTPS 或加密，请只在可信局域网使用
- 路径均为库内相对路径；`.` 开头的隐藏文件 / 目录不会列出
- 字幕需与视频同目录、同名、UTF-8 编码；播放器统一以「中文字幕」轨道加载并支持开关
- Office 文档（doc/docx/xls/xlsx/ppt/pptx）无法在浏览器里渲染，只能下载
- PDF 内嵌预览依赖浏览器自带的 PDF 查看器
- 播放能力取决于观看端浏览器：推荐 MP4(H.264/AAC)、WebM；MKV 在部分浏览器上不可用
- HEIC / AVIF 等格式依赖浏览器解码支持
- 视频不转码，原画质直出，带宽取决于局域网质量；缩略图由手机实时生成，超大目录首次浏览会稍慢
- 精简模式仅保留播放、进度、全屏；音量、拖动进度和长按倍速仍可用手势，字幕会随视频自动加载
- 原生控件模式使用浏览器自带控制条，额外提供倍速、字幕和模式切换
- 热点、双网卡等场景下若获取不到局域网 IPv4，地址可能显示为占位值，以 App / 通知显示的地址为准
- 网页已设置移动端 `viewport`，并按 320px 起自适应；若浏览器开启了「桌面版网站」或系统强制缩放，需在浏览器设置里关闭，页面本身无法覆盖该设置
- 本地预览服务只是 mock，不能替代真机联调
