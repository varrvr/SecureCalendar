#include <jni.h>
#include <string.h>
#include "mapping.h"
#include "oprf_point.h"
#include <bee2/core/mem.h>

JNIEXPORT jbyteArray JNICALL
Java_com_example_calendar_crypto_Bee2MappingProvider_mapPrehashed32(JNIEnv* env, jobject self, jbyteArray input)
{
    unsigned char message[32], point[64];
    jbyteArray output;
    (void)self;
    if (!input || (*env)->GetArrayLength(env, input) != 32) {
        jclass type = (*env)->FindClass(env, "java/lang/IllegalArgumentException");
        if (type) (*env)->ThrowNew(env, type, "BAKE SWU requires exactly 32 bytes");
        return NULL;
    }
    (*env)->GetByteArrayRegion(env, input, 0, 32, (jbyte*)message);
    if ((*env)->ExceptionCheck(env)) return NULL;
    if (sc_bake_map(point, message, sizeof(message)) != 0) {
        jclass type = (*env)->FindClass(env, "java/lang/IllegalStateException");
        if (type) (*env)->ThrowNew(env, type, "Bee2 BAKE SWU mapping failed");
        return NULL;
    }
    output = (*env)->NewByteArray(env, 64);
    if (output) (*env)->SetByteArrayRegion(env, output, 0, 64, (jbyte*)point);
    return output;
}

JNIEXPORT jboolean JNICALL
Java_com_example_calendar_crypto_Bee2MappingProvider_validatePoint(JNIEnv* env, jobject self, jbyteArray input)
{
    unsigned char point[64];
    (void)self;
    if (!input || (*env)->GetArrayLength(env, input) != 64) return JNI_FALSE;
    (*env)->GetByteArrayRegion(env, input, 0, 64, (jbyte*)point);
    if ((*env)->ExceptionCheck(env)) return JNI_FALSE;
    return sc_bign_point_valid(point, sizeof(point)) ? JNI_TRUE : JNI_FALSE;
}

static jbyteArray point_operation(JNIEnv* env, jbyteArray point_array, jbyteArray scalar_array, int unblind)
{
    unsigned char point[64] = {0}, scalar[32] = {0}, result[64] = {0};
    jbyteArray output = NULL;
    int code = 1;
    if (!point_array || !scalar_array || (*env)->GetArrayLength(env, point_array) != 64 ||
        (*env)->GetArrayLength(env, scalar_array) != 32) goto error;
    (*env)->GetByteArrayRegion(env, point_array, 0, 64, (jbyte*)point);
    if ((*env)->ExceptionCheck(env)) goto cleanup;
    (*env)->GetByteArrayRegion(env, scalar_array, 0, 32, (jbyte*)scalar);
    if ((*env)->ExceptionCheck(env)) goto cleanup;
    code = unblind ? sc_point_unblind(result, point, 64, scalar, 32) :
        sc_point_multiply(result, point, 64, scalar, 32);
    if (code) goto error;
    output = (*env)->NewByteArray(env, 64);
    if (output) (*env)->SetByteArrayRegion(env, output, 0, 64, (jbyte*)result);
    goto cleanup;
error:
    {
        jclass type = (*env)->FindClass(env, code == 1 ? "java/lang/IllegalArgumentException" : "java/lang/IllegalStateException");
        if (type) (*env)->ThrowNew(env, type, code == 1 ? "Invalid BIGN point or scalar" : "Bee2 point operation failed");
    }
cleanup:
    memWipe(scalar, sizeof(scalar)); memWipe(result, sizeof(result)); memWipe(point, sizeof(point));
    return output;
}
JNIEXPORT jbyteArray JNICALL
Java_com_example_calendar_crypto_Bee2MappingProvider_pointMultiply(JNIEnv* env, jobject self, jbyteArray point, jbyteArray scalar)
{
    (void)self;
    return point_operation(env, point, scalar, 0);
}
JNIEXPORT jbyteArray JNICALL
Java_com_example_calendar_crypto_Bee2MappingProvider_pointUnblind(JNIEnv* env, jobject self, jbyteArray point, jbyteArray scalar)
{
    (void)self;
    return point_operation(env, point, scalar, 1);
}

#include "hash_group.h"
JNIEXPORT jbyteArray JNICALL
Java_com_example_calendar_crypto_Bee2MappingProvider_hashUniform96(JNIEnv* env, jobject self, jbyteArray input) {
    unsigned char uniform[96]={0}, point[64]={0};jbyteArray out=NULL;int code;
    (void)self;
    if(!input || (*env)->GetArrayLength(env,input)!=96) {
        jclass cls=(*env)->FindClass(env,"java/lang/IllegalArgumentException");
        if(cls)(*env)->ThrowNew(env,cls,"HashToGroup requires 96 uniform bytes");return NULL;
    }
    (*env)->GetByteArrayRegion(env,input,0,96,(jbyte*)uniform);
    if((*env)->ExceptionCheck(env)) goto done;
    code=sc_hash_group(point,uniform,96);
    if(code) {
        jclass cls=(*env)->FindClass(env,"java/lang/IllegalStateException");
        if(cls)(*env)->ThrowNew(env,cls,"Bee2 RFC 9380 mapping failed");goto done;
    }
    out=(*env)->NewByteArray(env,64);
    if(out)(*env)->SetByteArrayRegion(env,out,0,64,(jbyte*)point);
done:
    memWipe(uniform,sizeof(uniform));memWipe(point,sizeof(point));return out;
}
