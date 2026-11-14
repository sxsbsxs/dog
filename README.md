# Dog 下载器

Dog 是一款 Android 视频下载工具，交互参考 [Seal](https://github.com/JunkFood02/Seal)，下载解析由 [yt-dlp](https://github.com/yt-dlp/yt-dlp) 驱动。

## 多语言职责分层

项目按职责分配语言，避免把同一功能重复实现：

| 语言 | 模块 | 实际职责 |
| --- | --- | --- |
| Kotlin | app UI | Jetpack Compose 界面与 UI 状态订阅 |
| Java | app runtime | 下载 Service、yt-dlp 请求配置、Cookie 注入、下载进度、取消、Android MediaStore 文件发布和复制完整性校验 |
| Python | yt-dlp runtime / CI | 上游 CPython 运行 yt-dlp 网站提取器；Python 工具验证 APK 中各 ABI 的运行时组件，并与 Python zlib 对照测试 C 校验实现 |
| C | native media | 流式读取媒体文件并计算 IEEE CRC-32 校验值 |
| C++ | Android NDK / JNI | 媒体文件签名识别、JNI 桥接和 C 校验函数调用；FFmpeg/aria2c 使用 Android 原生运行时 |

### 一次下载的调用链

1. Kotlin Compose 界面调用 Java 下载服务。
2. Java 服务构建 yt-dlp 请求并把任务交给 Android yt-dlp 运行时。
3. 上游 Android wrapper 启动 APK 内的 CPython，执行 yt-dlp 网站提取器和格式选择。
4. HTTP 分段下载可由原生 aria2c 执行；下载完成后 FFmpeg 负责合并音视频。
5. Java 使用 MediaStore 把完成的媒体文件写入系统公共“下载/Dog”目录。
6. 发布文件前，C 计算源文件 CRC-32，Java 在复制过程中同步计算校验值并比对；不一致时撤销未完成的 MediaStore 项目。
7. 文件扩展名不足以判断类型时，Java 调用 C++ JNI 模块探测媒体文件签名。

桌面版 yt-dlp 的 Python 代码由 Android 运行时提供，不把它逐行翻译为 Kotlin；Dog 的 C 与 C++ 模块则作为实际的 JNI 原生文件处理代码构建进 APK。

网页登录会话只从 Dog 自己的 WebView 获取，不读取 Chrome 或其他应用的私有数据。部分网站会限制内嵌 WebView 登录，或使用设备绑定/额外验证；这些站点需要平台专属的授权适配，不能保证所有网站都能通过通用 WebView 登录。

## 已实现功能

- 首页只保留一个视频链接输入框；支持剪贴板粘贴和从其他应用分享链接。
- yt-dlp 支持的网站解析、最佳可用音视频流选择、视频 MP4/MKV/WebM、音频 MP3/M4A/Opus/FLAC、4K/1440p/1080p 等清晰度上限、字幕语言选择和可选的整张播放列表下载；默认只下载当前视频。
- 可设置字幕语言、音频封面嵌入、分段并发数，以及跳过本机下载归档中已有的视频。
- FFmpeg 自动合并分离的视频与音频流。
- aria2c 原生分段下载器。
- 应用内 WebView 登录授权；只导出本次登录访问过的相关站点 Cookie，并验证登录入口域名是否实际拿到 Cookie；不再把 WebView 中其他网站的 Cookie 全部导出。
- 授权管理页展示已登录站点，可单独移除某个站点的加密授权，也可一次性清除所有授权。不同站点的会话分别加密保存。
- Cookie 文件使用 Android Keystore AES-GCM 加密保存；下载时临时写入应用私有缓存，任务结束后删除明文临时文件。
- 浅色/深色/跟随系统三种外观模式。
- 连续下载队列：正在下载时仍可提交新链接，任务按顺序执行；取消时清空队列。
- 下载前媒体信息预览：显示标题、作者、时长、分辨率、估算大小和来源提取器。
- 可在设置内检查并更新 yt-dlp 稳定版网站提取器，修复因上游网站变更导致的解析失败。
- 本机下载历史：保存文件名、来源网站、时间、任务状态和系统媒体库 URI；不保存带签名参数的完整视频链接。
- 前台下载服务、系统通知、下载进度、通知栏取消。
- Android 10+ 下载完成后发布到公共“下载/Dog”目录，并校验文件复制完整性。
- Android 9 及更早版本使用系统 Download/Dog 目录，并按需申请旧版存储权限。

## 构建

环境：Android Studio、Android SDK Platform 35、JDK 17、Android NDK 27、CMake 3.22.1、Python 3.12。

    ./gradlew :app:assembleDebug

APK：app/build/outputs/apk/debug/app-debug.apk。

GitHub Actions 会先用 Python 与 zlib 对照测试 C 校验代码，再构建 APK，并检查各 ABI 是否包含 CPython、QuickJS、FFmpeg、aria2c 和 Dog JNI 原生模块。成功后可从 Actions 的 Artifacts 获取 APK。

## 许可证

Dog 项目代码采用 GPL-3.0。yt-dlp 使用 The Unlicense，文本位于 LICENSES/yt-dlp-UNLICENSE。Android yt-dlp runtime、FFmpeg 和 aria2c 的许可与二进制通知请参考各上游项目。Seal 仅作为产品交互和架构参考，未使用其品牌或应用源码。

请仅下载你有权保存的内容，并遵守来源网站的服务条款及当地法律。
