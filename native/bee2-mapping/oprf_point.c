#include "oprf_point.h"
#include <bee2/core/blob.h>
#include <bee2/core/err.h>
#include <bee2/core/mem.h>
#include <bee2/crypto/bign.h>
#include <bee2/math/ww.h>
#include <bee2/math/zz.h>

static const char* const GROUP = "1.2.112.0.2.0.34.101.45.3.1";

int sc_scalar_inverse(unsigned char out[32], const unsigned char* scalar, size_t length)
{
    bign_params params;
    enum { N = W_OF_O(32) };
    word value[N] = {0}, order[N] = {0}, exponent[N] = {0}, inverse[N] = {0};
    blob_t stack = NULL;
    int code = 2;
    if (!out) return 1;
    memSetZero(out, 32);
    if (!scalar || length != 32) return 1;
    if (bignParamsStd(&params, GROUP) != ERR_OK || params.l != 128) goto cleanup;
    wwFrom(value, scalar, 32);
    wwFrom(order, params.q, 32);
    if (wwIsZero(value, N) || wwCmp(value, order, N) >= 0) { code = 1; goto cleanup; }
    wwCopy(exponent, order, N);
    zzSubW2(exponent, N, 2);
    stack = blobCreate(zzPowerMod_deep(N, N));
    if (!stack) goto cleanup;
    /* Fermat inverse with public fixed exponent q-2, not Euclid on the private blind. */
    zzPowerMod(inverse, value, N, exponent, N, order, stack);
    if (wwIsZero(inverse, N)) goto cleanup;
    wwTo(out, 32, inverse);
    code = 0;
cleanup:
    blobClose(stack);
    memWipe(value, sizeof(value)); memWipe(inverse, sizeof(inverse));
    memWipe(order, sizeof(order)); memWipe(exponent, sizeof(exponent));
    memWipe(&params, sizeof(params));
    if (code) memWipe(out, 32);
    return code;
}

int sc_point_multiply(unsigned char out[64], const unsigned char* point, size_t point_length,
    const unsigned char* scalar, size_t scalar_length)
{
    bign_params params;
    err_t result;
    if (!out) return 1;
    memSetZero(out, 64);
    if (!point || !scalar || point_length != 64 || scalar_length != 32) return 1;
    result = bignParamsStd(&params, GROUP);
    if (result == ERR_OK && params.l != 128) result = ERR_BAD_PARAMS;
    if (result == ERR_OK) result = bignDH(out, &params, scalar, point, 64);
    memWipe(&params, sizeof(params));
    if (result != ERR_OK) memWipe(out, 64);
    if (result == ERR_BAD_PRIVKEY || result == ERR_BAD_PUBKEY) return 1;
    return result == ERR_OK ? 0 : 2;
}

int sc_point_unblind(unsigned char out[64], const unsigned char* point, size_t point_length,
    const unsigned char* blind, size_t blind_length)
{
    unsigned char inverse[32] = {0};
    int code;
    if (!out) return 1;
    memSetZero(out, 64);
    if (!point || point_length != 64) return 1;
    code = sc_scalar_inverse(inverse, blind, blind_length);
    if (!code) code = sc_point_multiply(out, point, point_length, inverse, sizeof(inverse));
    memWipe(inverse, sizeof(inverse));
    return code;
}
