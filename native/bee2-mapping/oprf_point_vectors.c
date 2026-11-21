#include <stdio.h>
#include <string.h>
#include "oprf_point.h"
static void hex(unsigned char* out, const char* text, size_t length) {
    size_t i; for (i=0;i<length;++i) { unsigned int v=0; sscanf(text+2*i,"%2x",&v);out[i]=(unsigned char)v; }
}
int main(void) {
    unsigned char point[64], twice[64], triple[64], sixfold[64], inverse[32], order[32];
    unsigned char r[32]={2}, k[32]={3}, zero[32]={0}, actual[64], evaluated[64], result[64], inv[32];
    hex(point,"014417d3355557317d2e2ab6d08754878d19e8d97b71fdc95dbb2a9b894d16d77704a0b5caa9cda10791e4760671e1050ddeab7083a7458447866adb01473810",64);hex(twice,"c54fdfbb4ef1ad0a12e9079232c78d814969607a7f4f8c228e0ded2061821269920949ff1743e549cb0a00900a29b65f4862824472de21657780a2f5e2cd92bb",64);hex(triple,"2f5b956fe8855697076c15aea9f28826e9de1026da77acf0fcbef449f470953b3dac53d15663667f99e666ebe9d8f9ed2af59d61e4e7391ae4ef0fc2b6e61200",64);hex(sixfold,"7259ddc4f169b2516513e753325b39272239f63ceccf0fec79cfe1a194183732c9f2931d007ab90c98f8b824b467a591a47b7def60c3b2c635db6661b1a6e5cc",64);
    hex(inverse,"04b31e93cc5f2d3ffea6fd066b47aeecffffffffffffffffffffffffffffff7f",32);hex(order,"07663d2699bf5a7efc4dfb0dd68e5cd9ffffffffffffffffffffffffffffffff",32);
    if (sc_scalar_inverse(inv,r,32) || memcmp(inv,inverse,32)) return 1;
    if (sc_point_multiply(actual,point,64,r,32) || memcmp(actual,twice,64)) return 2;
    if (sc_point_multiply(evaluated,actual,64,k,32) || memcmp(evaluated,sixfold,64)) return 3;
    if (sc_point_unblind(result,evaluated,64,r,32) || memcmp(result,triple,64)) return 4;
    if (!sc_scalar_inverse(inv,zero,32) || !sc_scalar_inverse(inv,order,32) ||
        !sc_scalar_inverse(inv,r,31) || !sc_scalar_inverse(inv,NULL,32) ||
        !sc_point_unblind(result,evaluated,63,r,32) || !sc_point_unblind(result,evaluated,64,zero,32)) return 5;
    memset(actual,255,64);
    if (!sc_point_multiply(result,actual,64,r,32)) return 6;
    puts("BIGN OPRF group-core independent vectors passed; this is not a full OPRF/PSI security test");
    return 0;
}
