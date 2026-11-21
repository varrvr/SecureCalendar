#ifndef SECURE_CALENDAR_OPRF_POINT_H
#define SECURE_CALENDAR_OPRF_POINT_H
#include <stddef.h>
/* Group operations only: no hash suite, PRF finalization or proof verification is implied. */
int sc_scalar_inverse(unsigned char out[32], const unsigned char* scalar, size_t length);
int sc_point_multiply(unsigned char out[64], const unsigned char* point, size_t point_length,
    const unsigned char* scalar, size_t scalar_length);
int sc_point_unblind(unsigned char out[64], const unsigned char* point, size_t point_length,
    const unsigned char* blind, size_t blind_length);
#endif
