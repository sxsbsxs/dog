#include "media_crc32.h"

#include <stdio.h>
#include <stdint.h>

static void build_crc32_table(uint32_t table[256]) {
    for (uint32_t i = 0; i < 256; ++i) {
        uint32_t value = i;
        for (int bit = 0; bit < 8; ++bit) {
            value = (value & 1U) ? (0xEDB88320U ^ (value >> 1U)) : (value >> 1U);
        }
        table[i] = value;
    }
}

int dog_crc32_file(const char* path, uint64_t* byte_count, uint32_t* checksum) {
    if (path == NULL || byte_count == NULL || checksum == NULL) {
        return -1;
    }

    FILE* input = fopen(path, "rb");
    if (input == NULL) {
        return -1;
    }

    uint32_t table[256];
    build_crc32_table(table);

    unsigned char buffer[64 * 1024];
    uint32_t crc = 0xFFFFFFFFU;
    uint64_t total = 0;
    size_t read_count;

    while ((read_count = fread(buffer, 1, sizeof(buffer), input)) > 0) {
        total += (uint64_t) read_count;
        for (size_t index = 0; index < read_count; ++index) {
            const uint32_t table_index = (crc ^ buffer[index]) & 0xFFU;
            crc = table[table_index] ^ (crc >> 8U);
        }
    }

    const int read_failed = ferror(input);
    fclose(input);
    if (read_failed) {
        return -1;
    }

    *byte_count = total;
    *checksum = crc ^ 0xFFFFFFFFU;
    return 0;
}
