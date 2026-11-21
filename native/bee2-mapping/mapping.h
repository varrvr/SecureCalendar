#ifndef SECURE_CALENDAR_MAPPING_H
#define SECURE_CALENDAR_MAPPING_H
#include <stddef.h>
/* Fixed STB 34.101.45 group 1.2.112.0.2.0.34.101.45.3.1; little-endian x || y. */
int sc_bake_map(unsigned char out[64], const unsigned char* input, size_t length);
int sc_bign_point_valid(const unsigned char* point, size_t length);
#endif
