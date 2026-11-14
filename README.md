# Dog 下载器

Dog 是一款 Android 视频下载工具，交互参考 [Seal](https://github.com/JunkFood02/Seal)，下载解析由 [yt-dlp](https://github.com/yt-dlp/yt-dlp) 驱动。

## 多语言职责分层

项目按职责分配语言，避免把同一功能重复实现：

| 语言 | 模块 | 实际职责 |
| --- | --- | --- |
| Kotlin | app UI | Jetpack Compose 界面、表单状态与 UI 状态订阅 |
| Java | app runtime | 下载 Service、yt-dlp 请求配置、Cookie 注入、下载进度、取消、Android MediaStore 文件发布 |
| Python | yt-dlp runtime / CI | 上游 CPython 运行 yt-dlp 网站提取器；Python 工具验证 APK 中各 ABI 的运行时组件 |
| C++ | Android NDK | Dog JNI 文件签名识别；FFmpeg/aria2c 通过 Android 原生依赖执行媒体处理和外部下载 |

### 一次下载的调用链

1. Kotlin 界面调用 Java 下载服务。
2. Java 服务构建 yt-dlp 请求并把任务交给 Android yt-dlp 运行时。
3. 上游 Android wrapper 启动 APK 内的 CPython，执行 yt-dlp 网站提取器和格式选择。
4. HTTP 分段下载可由原生 aria2c 执行；下载完成后 FFmpeg 负责合并音视频。
5. Java 使用 MediaStore 把完成的媒体文件写入系统公共“下载/Dog”目录。
6. 文件类型不明确时，Java 调用 C++ JNI 模块探测媒体文件签名。

本项目不把桌面 Python 工程逐行翻译为 Kotlin，也不复制 Seal 的应用源码；Android runtime 负责把 yt-dlp、Python 与原生依赖打包进 APK。

## 已实现功能

- 首页一个视频链接输入框和「开始下载」按钮。
- yt-dlp 支持的网站解析和最佳可用音视频流选择。
- FFmpeg 音视频合并。
- aria2c 原生外部下载器。
- 可选 Cookie，保存在应用本机私有设置中。
- 前台下载服务、系统通知、下载进度和取消。
- 分享菜单把网页链接交给 Dog。
- Android 10+ 下载完成后发布到公共“下载/Dog”目录。

## 构建

环境：Android Studio、Android SDK Platform 35、JDK 17、Android NDK 27、CMake 3.22.1。

    ./gradlew :app:assembleDebug

APK：app/build/outputs/apk/debug/app-debug.apk。

GitHub Actions 会构建 APK，并运行 Python 检查各 ABI 是否包含 CPython、QuickJS、FFmpeg、aria2c 和 Dog JNI 原生模块。

## 许可

Dog 项目代码采用 GPL-3.0。yt-dlp 使用 The Unlicense，文本位于 LICENSES/yt-dlp-UNLICENSE。Android yt-dlp runtime、FFmpeg 和 aria2c 的许可与二进制通知请参考各上游项目。Seal 仅作为产品交互和架构参考，未使用其品牌或应用源码。

请仅下载你有权保存的内容，并遵守来源网站的服务条款及当地法律。
