#include <jni.h>

#include <array>
#include <cstdint>
#include <fstream>
#include <iomanip>
#include <sstream>
#include <string>

#include "media_crc32.h"

namespace {

bool starts_with(const std::array<std::uint8_t, 512>& data,
                 std::size_t length,
                 const char* magic,
                 std::size_t magic_length) {
    if (length < magic_length) return false;
    for (std::size_t i = 0; i < magic_length; ++i) {
        if (data[i] != static_cast<std::uint8_t>(magic[i])) return false;
    }
    return true;
}

std::string detect_mime(const std::string& path) {
    std::ifstream input(path, std::ios::binary);
    if (!input) return "";

    std::array<std::uint8_t, 512> header{};
    input.read(reinterpret_cast<char*>(header.data()), static_cast<std::streamsize>(header.size()));
    const auto length = static_cast<std::size_t>(input.gcount());

    if (length >= 12 &&
        header[4] == 'f' && header[5] == 't' &&
        header[6] == 'y' && header[7] == 'p') {
        return "video/mp4";
    }
    if (starts_with(header, length, "\x1A\x45\xDF\xA3", 4)) return "video/webm";
    if (starts_with(header, length, "FLV", 3)) return "video/x-flv";
    if (starts_with(header, length, "OggS", 4)) return "application/ogg";
    if (starts_with(header, length, "fLaC", 4)) return "audio/flac";
    if (starts_with(header, length, "ID3", 3)) return "audio/mpeg";

    if (length >= 12 && starts_with(header, length, "RIFF", 4)) {
        if (header[8] == 'W' && header[9] == 'A' &&
            header[10] == 'V' && header[11] == 'E') return "audio/wav";
        if (header[8] == 'A' && header[9] == 'V' &&
            header[10] == 'I' && header[11] == ' ') return "video/x-msvideo";
    }

    if (length > 188 && header[0] == 0x47 && header[188] == 0x47) {
        return "video/mp2t";
    }
    if (length >= 2 && header[0] == 0xFF &&
        (header[1] & 0xF6) == 0xF0) return "audio/aac";
    if (length >= 2 && header[0] == 0xFF &&
        (header[1] & 0xE0) == 0xE0) return "audio/mpeg";

    return "";
}

}  // namespace

extern "C"
JNIEXPORT jstring JNICALL
Java_com_sxsbsxs_dog_NativeMediaProbe_detectMimeType(
        JNIEnv* env, jclass, jstring absolute_path) {
    if (absolute_path == nullptr) return env->NewStringUTF("");

    const char* chars = env->GetStringUTFChars(absolute_path, nullptr);
    if (chars == nullptr) return env->NewStringUTF("");

    const std::string mime = detect_mime(chars);
    env->ReleaseStringUTFChars(absolute_path, chars);
    return env->NewStringUTF(mime.c_str());
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_sxsbsxs_dog_NativeMediaProbe_calculateFingerprint(
        JNIEnv* env, jclass, jstring absolute_path) {
    if (absolute_path == nullptr) return env->NewStringUTF("");

    const char* chars = env->GetStringUTFChars(absolute_path, nullptr);
    if (chars == nullptr) return env->NewStringUTF("");

    std::uint64_t byte_count = 0;
    std::uint32_t checksum = 0;
    const int result = dog_crc32_file(chars, &byte_count, &checksum);
    env->ReleaseStringUTFChars(absolute_path, chars);

    if (result != 0) return env->NewStringUTF("");

    std::ostringstream fingerprint;
    fingerprint << byte_count << ":" << std::hex << std::nouppercase
                << std::setw(8) << std::setfill('0') << checksum;
    return env->NewStringUTF(fingerprint.str().c_str());
}
