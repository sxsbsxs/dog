#ifndef DOG_MEDIA_CRC32_H
#define DOG_MEDIA_CRC32_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Read a file and calculate its byte count and IEEE CRC-32 checksum.
 * Returns 0 on success and -1 on an I/O error.
 */
int dog_crc32_file(const char* path, uint64_t* byte_count, uint32_t* checksum);

#ifdef __cplusplus
}
#endif

#endif
