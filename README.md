# Dog 下载器

Dog 是一款 Android 视频下载工具，产品交互参考 [Seal](https://github.com/JunkFood02/Seal)，下载解析由 [yt-dlp](https://github.com/yt-dlp/yt-dlp) 驱动。

## 多语言分层架构

Dog 不会为了“多语言”而把相同逻辑重复写四遍。各语言只负责最适合的工作：

| 语言 | 运行区域 | 职责 |
| --- | --- | --- |
| Kotlin | Android 应用层 | Jetpack Compose 界面、任务状态、前台服务、调用下载引擎 |
| Java | Android 系统层 | MediaStore 公共下载目录、文件发布与 Android 存储 API |
| Python | yt-dlp 运行时和构建工具 | 网站提取器、视频信息解析、平台适配；CI 中校验 APK 内的 Python 运行时 |
| C/C++ | Android NDK 原生层 | FFmpeg 音视频合并/转码、aria2c 分段下载，以及 Dog 自己的 JNI 文件签名探测 |

### 下载时各模块如何协作

1. Kotlin 接收链接、Cookie 和用户操作，启动前台下载服务。
2. yt-dlp 在 APK 内的 CPython 环境中运行，调用对应网站提取器并选择最佳可用音视频流。
3. 网络分段下载可交给原生 aria2c；独立视频流和音频流下载完毕后交给 FFmpeg 合并。
4. Java 调用 Android MediaStore，将完成的文件发布到系统“下载/Dog”目录。
5. Dog 自己的 C++ JNI 小模块读取文件头，为没有可靠扩展名的媒体文件补充 MIME 类型判断。

Android 版不是把桌面 Python 程序机械翻译成 Kotlin；yt-dlp 的 Python 运行时由 Android 依赖打包，FFmpeg 和 aria2c 使用面向 Android ABI 的原生二进制。

## 已实现功能

- 首页只保留一个视频链接输入框和「开始下载」按钮。
- 使用 yt-dlp 解析链接，优先选择最佳可用视频与音频流。
- FFmpeg 合并音视频。
- aria2c 原生下载器。
- 可选 Cookie：在右上角设置中粘贴浏览器 Cookie。
- 前台下载服务、系统通知、下载进度与取消入口。
- 支持系统分享菜单将网页链接交给 Dog。
- Android 10+ 完成后发布到公共“下载/Dog”目录。
- 无自建服务器，解析和下载在 Android 设备本地执行。

## 技术栈

- Kotlin、Java、C++17、Python 3
- Jetpack Compose、Material 3、Android MediaStore
- Android NDK、CMake
- youtubedl-android 0.18.1（包含 CPython、yt-dlp、QuickJS 运行环境）
- FFmpeg Android runtime、aria2c Android runtime

## 构建 APK

环境：Android Studio、Android SDK Platform 35、JDK 17、Android NDK、CMake 3.22.1。

执行：

    ./gradlew :app:assembleDebug

APK 输出：app/build/outputs/apk/debug/app-debug.apk。

GitHub Actions 会构建 APK，并检查各 ABI 是否包含 Python、QuickJS、FFmpeg、aria2c 和 Dog 的 C++ JNI 模块。成功构建后可从 Actions 的 Artifacts 获取 APK。

## 下载保存位置

Android 10 及以上：公共目录“下载/Dog”。

Android 9 及以下：应用专属目录 Android/data/com.sxsbsxs.dog/files/Download/Dog。

## Cookie 提醒

Cookie 是可选的，只在目标网站要求登录或账号权限影响可用画质时填写。Cookie 保存在应用私有偏好设置中；下载时会随请求发送给对应的视频网站。不要把自己的 Cookie 发给其他人。

## 许可证与致谢

- Dog 项目代码：GPL-3.0。
- yt-dlp：The Unlicense，许可文本位于 LICENSES/yt-dlp-UNLICENSE。
- Android 运行时： [youtubedl-android](https://github.com/yausername/youtubedl-android)。
- FFmpeg、aria2c：使用上游 Android 预构建原生组件，相关许可请见上游发布说明。
- UI 与产品思路参考 [Seal](https://github.com/JunkFood02/Seal)。Dog 未使用 Seal 的名称、图标或应用源码。

请仅下载你有权保存的内容，并遵守来源网站的服务条款及当地法律。
