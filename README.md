# VideoFetch

一款面向 Android 的视频与音频下载器。界面采用 Jetpack Compose 与 Material Design 3，下载解析由 yt-dlp 提供支持。

## 功能

- 单一链接入口：粘贴链接或从其他应用分享链接，自动解析媒体信息和源站实际提供的格式。
- 画质与格式：选择源站可用的最高画质或指定分辨率；支持 MP4、MKV、WebM，以及 MP3、M4A、Opus、FLAC。
- 音视频合并：使用 FFmpeg 自动合并分离的视频流与音频流。
- 分段下载：可使用内置 aria2c，加快支持分段请求的媒体下载。
- 下载队列：下载进行时可继续添加任务，支持查看进度、取消任务和清空等待队列。
- 字幕和播放列表：可选字幕语言，也可选择下载整个播放列表；默认只下载当前视频。
- 登录授权：通过应用内 WebView 登录目标站点；站点 Cookie 分别使用 Android Keystore 与 AES-GCM 加密保存。
- 隐私保护：不读取 Chrome 或其他应用的私有 Cookie；下载时仅在应用私有缓存中短暂生成解密后的 Cookie 文件，任务结束后清理。
- 下载历史：记录文件名、来源、时间和状态，不保存带签名参数的完整视频地址。
- 文件完整性：发布到系统下载目录前执行 CRC-32 校验；必要时使用 JNI 媒体签名探测。
- 主题：浅色、深色和跟随系统；Android 12 及以上支持动态配色。

## 技术结构

| 技术 | 职责 |
| --- | --- |
| Kotlin | Jetpack Compose 界面、状态管理和页面导航 |
| Java | 下载 Service、yt-dlp 请求、Cookie 注入、进度通知、文件发布 |
| Python | 由 Android 运行时提供的 yt-dlp 提取器与站点解析 |
| C | 媒体文件流式读取与 CRC-32 校验 |
| C++ / JNI | 媒体签名识别与原生校验函数桥接 |

下载调用链：Compose 页面 -> Java 下载服务 -> Android yt-dlp runtime / CPython -> yt-dlp 提取器 -> aria2c / FFmpeg -> Android MediaStore。

## 构建

环境要求：Android Studio、Android SDK Platform 35、JDK 17、Android NDK 27、CMake 3.22.1、Python 3.12。

```bash
./gradlew :app:assembleDebug
```

APK 输出目录：`app/build/outputs/apk/debug/`。GitHub Actions 会运行原生校验和站点提取器离线测试，然后构建各 ABI 的 APK 并检查运行时组件。

## 保存位置

下载完成后会发布到系统公共目录 `Download/VideoFetch`。

## 许可证

本项目代码采用 GPL-3.0。yt-dlp 使用 The Unlicense，文本位于 `LICENSES/yt-dlp-UNLICENSE`。Android yt-dlp runtime、FFmpeg 和 aria2c 的许可证与二进制通知请参考各上游项目。

请仅下载你有权保存的内容，并遵守来源网站的服务条款及当地法律。
