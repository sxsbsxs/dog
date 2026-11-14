# Dog Downloader

一个面向 Android 的轻量级视频 / 音频下载器。项目参考 Seal 的产品思路，使用 yt-dlp 作为解析与下载引擎，并在设备本地完成下载，不依赖自建服务器。

## 当前实现

- 单输入框：粘贴视频或播放列表链接，点击下载即可。
- yt-dlp 引擎：使用 `youtubedl-android` Android 运行时封装，APK 内运行 Python 与 yt-dlp。
- FFmpeg：支持 yt-dlp 的音视频合并与后处理。
- 本地 Cookie：可在右上角 Cookie 设置中粘贴 Cookie 请求头；仅保存在应用私有偏好设置中，并在本机传递给下载引擎。
- 前台下载服务：切换应用后继续下载，显示系统通知与实时进度。
- 最高可用画质：默认优先选择最佳视频流和音频流并合并。
- 播放列表：保留 yt-dlp 对播放列表链接的默认处理能力。

## 技术栈

- Kotlin、Jetpack Compose、Material 3
- Android Gradle Plugin
- `io.github.junkfood02.youtubedl-android`（yt-dlp Android wrapper）
- FFmpeg Android runtime

这不是把桌面版 yt-dlp 的 Python 源码逐文件改写成 Kotlin。Android 版通过上游 Android wrapper 将 Python 运行时、yt-dlp 可执行脚本和所需原生组件打包到 APK，并调用 yt-dlp 的命令行参数执行下载。这样可以复用 yt-dlp 的站点提取器与更新机制。

## 构建

1. 使用 Android Studio 打开仓库。
2. 安装 Android SDK Platform 35 和 JDK 17。
3. 等待 Gradle 同步完成。
4. 运行 `app` 的 debug 变体，或执行：

   ```bash
   ./gradlew :app:assembleDebug
   ```

APK 输出位置：`app/build/outputs/apk/debug/app-debug.apk`

## 下载位置

下载文件保存在应用专属外部下载目录：`Android/data/com.sxsbsxs.dog/files/Download/Dog`。此目录由系统沙盒管理，不需要申请广泛存储权限。

## Cookie 使用

Cookie 是可选项。只有遇到登录后才能访问的内容或需要账号可用画质时才需要设置。请只粘贴自己账号的 Cookie，不要分享给他人。Cookie 存储在本应用私有数据中；清除应用数据或在设置中清空 Cookie 即可删除。

## 许可与致谢

- 本项目按 GPL-3.0-or-later 发布。
- 视频提取和下载由 [yt-dlp](https://github.com/yt-dlp/yt-dlp) 提供。
- Android 适配层使用 [youtubedl-android](https://github.com/yausername/youtubedl-android)。
- 产品交互思路参考 [Seal](https://github.com/JunkFood02/Seal)，未使用 Seal 品牌或复制其应用源码。

请仅下载你有权保存的内容，并遵守来源网站的服务条款及当地法律。
