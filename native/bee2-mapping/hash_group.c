/* RFC 9380 5.3, 6.6.2: two field elements, SSWU, point addition.
 * BIGN curve128v1, p=2^256-189, h=1, Z=-11. No hash-times-generator. */
#include "hash_group.h"
#include <bee2/core/blob.h>
#include <bee2/core/err.h>
#include <bee2/core/mem.h>
#include <bee2/core/util.h>
#include <bee2/crypto/bign.h>
#include <bee2/math/ec.h>
#include <bee2/math/ecp.h>
#include <bee2/math/qr.h>
#include <bee2/math/ww.h>
#include <bee2/math/zz.h>
#include "bign_lcl.h"
enum { N = W_OF_O(32), WIDE = W_OF_O(48) };
static void curve_rhs(word* out, const word* x, const ec_o* ec, void* stack) {
    qrSqr(out,x,ec->f,stack); qrAdd(out,out,ec->A,ec->f);
    qrMul(out,out,x,ec->f,stack); qrAdd(out,out,ec->B,ec->f);
}
static int sswu(word out[2*N], const word u[N], const ec_o* ec, void* stack) {
    word z[N], t[N], inv[N], x1[N], x2[N], g1[N], g2[N], y1[N], y2[N], check[N];
    word exp_inv[N], exp_sqrt[N]; unsigned char bytes[32]; int square, sign;
    const qr_o* f=ec->f;
    wwCopy(exp_inv,f->mod,N); zzSubW2(exp_inv,N,2);
    wwCopy(exp_sqrt,f->mod,N); zzAddW2(exp_sqrt,N,1); wwShLo(exp_sqrt,N,2);
    memSetZero(bytes,32);bytes[0]=11;qrFrom(z,bytes,f,stack);qrNeg(z,z,f);
    qrSqr(t,u,f,stack);qrMul(t,t,z,f,stack); /* t = Z*u^2 */
    qrSqr(inv,t,f,stack);qrAdd(inv,inv,t,f);
    qrPower(inv,inv,exp_inv,N,f,stack); /* inv0 */
    qrPower(x1,ec->A,exp_inv,N,f,stack);qrMul(x1,x1,ec->B,f,stack);qrNeg(x1,x1,f);
    if (qrIsZero(inv,f)) {
        qrPower(inv,z,exp_inv,N,f,stack);qrMul(x1,x1,inv,f,stack);qrNeg(x1,x1,f);
    } else {
        qrAdd(inv,inv,f->unity,f);qrMul(x1,x1,inv,f,stack);
    }
    qrMul(x2,t,x1,f,stack);curve_rhs(g1,x1,ec,stack);curve_rhs(g2,x2,ec,stack);
    qrPower(y1,g1,exp_sqrt,N,f,stack);qrPower(y2,g2,exp_sqrt,N,f,stack);
    qrSqr(check,y1,f,stack);square=wwEq(check,g1,N);
    wwCopy(out,square?x1:x2,N);wwCopy(out+N,square?y1:y2,N);
    qrSqr(check,out+N,f,stack);if (!wwEq(check,square?g1:g2,N)) return 1;
    qrTo(bytes,u,f,stack);sign=bytes[0]&1;qrTo(bytes,out+N,f,stack);
    if ((bytes[0]&1)!=sign) qrNeg(out+N,out+N,f);
    memWipe(t,sizeof(t));memWipe(inv,sizeof(inv));memWipe(bytes,sizeof(bytes));
    return 0;
}
int sc_hash_group(unsigned char out[64], const unsigned char* uniform, size_t length) {
    bign_params params; ec_o* ec=NULL; blob_t stack=NULL;
    word wide[WIDE], u[N], points[2][2*N], result[2*N];
    unsigned char input[48], reduced[32];size_t i,j;int code=2;
    if (!out) return 1;
    memSetZero(out,64);if (!uniform || length!=96) return 1;
    if (bignParamsStd(&params,"1.2.112.0.2.0.34.101.45.3.1")!=ERR_OK) goto done;
    if (bignEcCreate(&ec,&params)!=ERR_OK) goto done;
    stack=blobCreate(utilMax(3,qrPower_deep(N,N,ec->f->deep),
        zzMod_deep(WIDE,N),ecpAddAA_deep(N,ec->f->deep)));
    if (!stack) goto done;
    for(i=0;i<2;++i) {
        for(j=0;j<48;++j) input[j]=uniform[48*i+47-j];
        wwFrom(wide,input,48);zzMod(u,wide,WIDE,ec->f->mod,N,stack);
        wwTo(reduced,32,u);if (!qrFrom(u,reduced,ec->f,stack)) goto done;
        if(sswu(points[i],u,ec,stack)) goto done;
    }
    /* Standard group has cofactor 1. Identity cannot enter the OPRF. */
    if (!ecpAddAA(result,points[0],points[1],ec,stack)) { code=3;goto done; }
    qrTo(out,result,ec->f,stack);qrTo(out+32,result+N,ec->f,stack);
    if (bignPubkeyVal(&params,out)!=ERR_OK) goto done;
    code=0;
done:
    blobClose(stack);if(ec)bignEcClose(ec);memWipe(&params,sizeof(params));
    memWipe(wide,sizeof(wide));memWipe(u,sizeof(u));memWipe(points,sizeof(points));
    memWipe(result,sizeof(result));memWipe(input,sizeof(input));memWipe(reduced,sizeof(reduced));
    if(code)memWipe(out,64);return code;
}
