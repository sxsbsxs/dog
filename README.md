# Dog 下载器

Dog 是一款 Android 视频下载工具，交互参考 Seal（https://github.com/JunkFood02/Seal），下载解析由 yt-dlp（https://github.com/yt-dlp/yt-dlp）驱动。

## 已提交的功能

- 首页只保留一个视频链接输入框和「开始下载」按钮。
- 使用 Android 版 yt-dlp 运行时解析链接，优先选择最佳可用视频与音频流。
- 集成 FFmpeg，用于合并分离的视频和音频流。
- 可选 Cookie：在右上角设置中粘贴浏览器 Cookie，请求时发给对应的视频网站。
- 前台下载服务、系统通知、下载进度与取消入口。
- 支持系统分享菜单将网页链接交给 Dog。
- 无自建服务器，解析和下载在 Android 设备本地执行。

## 实现方式

本项目没有把桌面版 yt-dlp 的 Python 源码逐文件翻译成 Kotlin。Android APK 使用上游 youtubedl-android 运行时，将 Python、yt-dlp 以及 Android 原生依赖打包进应用，再由 Kotlin 调用 yt-dlp。这样复用 yt-dlp 的站点提取器和命令行功能。

主要依赖：
- io.github.junkfood02.youtubedl-android:library:0.18.1
- io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1
- Kotlin、Jetpack Compose、Material 3

## 构建 APK

环境：Android Studio、Android SDK Platform 35、JDK 17。

执行：

    ./gradlew :app:assembleDebug

APK 输出：app/build/outputs/apk/debug/app-debug.apk。

GitHub Actions 已配置 Android Debug 构建，推送到 main 或提交 Pull Request 时会构建并上传 APK Artifact。

## 保存位置

文件保存在应用专属目录：

    Android/data/com.sxsbsxs.dog/files/Download/Dog

## Cookie 提醒

Cookie 是可选的，只在目标网站要求登录或账号权限影响可用画质时填写。Cookie 保存在应用私有偏好设置中；下载时会随请求发送给目标网站。不要把自己的 Cookie 发给其他人。

## 许可证与致谢

- Dog 项目代码：GPL-3.0。
- yt-dlp：The Unlicense，许可文本位于 LICENSES/yt-dlp-UNLICENSE。
- Android 运行时：youtubedl-android（https://github.com/yausername/youtubedl-android）。
- UI 与产品思路参考 Seal（https://github.com/JunkFood02/Seal）。Dog 未使用 Seal 的名称、图标或应用源码。

请仅下载你有权保存的内容，并遵守来源网站的服务条款及当地法律。
