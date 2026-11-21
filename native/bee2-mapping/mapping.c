/* Application adapter; Bee2 itself retains its upstream Apache-2.0 license. */
#include "mapping.h"
#include <bee2/crypto/bake.h>
#include <bee2/crypto/bign.h>
#include <bee2/core/mem.h>
#include <bee2/core/err.h>

static const char* const GROUP = "1.2.112.0.2.0.34.101.45.3.1";

int sc_bake_map(unsigned char out[64], const unsigned char* input, size_t length)
{
    bign_params params;
    err_t result;
    if (!out || !input || length != 32) return 1;
    memSetZero(out, 64);
    result = bignParamsStd(&params, GROUP);
    if (result == ERR_OK && params.l != 128) result = ERR_BAD_PARAMS;
    if (result == ERR_OK) result = bakeSWU(out, &params, input);
    if (result == ERR_OK) result = bignPubkeyVal(&params, out);
    memWipe(&params, sizeof(params));
    if (result != ERR_OK) memWipe(out, 64);
    return result == ERR_OK ? 0 : 2;
}

int sc_bign_point_valid(const unsigned char* point, size_t length)
{
    bign_params params;
    err_t result;
    if (!point || length != 64) return 0;
    result = bignParamsStd(&params, GROUP);
    if (result == ERR_OK && params.l != 128) result = ERR_BAD_PARAMS;
    if (result == ERR_OK) result = bignPubkeyVal(&params, point);
    memWipe(&params, sizeof(params));
    return result == ERR_OK;
}
