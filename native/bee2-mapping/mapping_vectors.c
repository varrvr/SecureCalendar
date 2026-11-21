#include <stdio.h>
#include <string.h>
#include "mapping.h"

static int decode(unsigned char* out, const char* text, size_t n)
{
    size_t i;
    for (i = 0; i < n; ++i) {
        unsigned int value;
        if (sscanf(text + i * 2, "%2x", &value) != 1) return 0;
        out[i] = (unsigned char)value;
    }
    return 1;
}
int main(void)
{
    /* Upstream test/crypto/bake_test.c, STB 34.101.66 table B.4 derived SWU vector. */
    unsigned char message[32], expected[64], output[64], invalid[64] = {0};
    if (!decode(message, "AD1362A8F9A3D42FBE1B8E6F1C88AAD50F51D91347617C20BD4AB07AEF4F26A1", 32) ||
        !decode(expected, "014417D3355557317D2E2AB6D08754878D19E8D97B71FDC95DBB2A9B894D16D77704A0B5CAA9CDA10791E4760671E1050DDEAB7083A7458447866ADB01473810", 64)) return 1;
    if (sc_bake_map(output, message, 32) != 0 || memcmp(output, expected, 64)) return 2;
    if (!sc_bign_point_valid(output, 64)) return 3;
    if (sc_bign_point_valid(invalid, 64)) return 4;
    memset(invalid, 255, 64);
    if (sc_bign_point_valid(invalid, 64)) return 5;
    if (sc_bake_map(output, message, 31) == 0 || sc_bake_map(output, NULL, 32) == 0 ||
        sc_bake_map(NULL, message, 32) == 0 || sc_bign_point_valid(expected, 63) ||
        sc_bign_point_valid(NULL, 64)) return 6;
    puts("Official BAKE SWU vector and malformed inputs: passed (not a PSI test)");
    return 0;
}
