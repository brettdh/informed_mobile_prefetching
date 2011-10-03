#include <stdlib.h>
#include <jni.h>
#include <stdexcept>
#include "eac_utility.h"
#include "jclasses.h"

jclass
JClasses::find(JNIEnv *jenv, const char *className)
{
    jclass local = jenv->FindClass(className);
    if (!local || JAVA_EXCEPTION_OCCURRED(jenv)) {
        throw std::runtime_error(className);
    }
    jclass global = (jclass) jenv->NewGlobalRef(local);
    if (!global || JAVA_EXCEPTION_OCCURRED(jenv)) {
        throw std::runtime_error(className);
    }
    return global;
}

jclass JClasses::EnergyAdaptiveCache = NULL;
jclass JClasses::JNICacheFetcher = NULL;
jclass JClasses::SWIGTYPE_p_void = NULL;
jclass JClasses::CancellationException = NULL;
jclass JClasses::TimeoutException = NULL;
jclass JClasses::TimeUnit = NULL;
jclass JClasses::Future = NULL;
jclass JClasses::Assert = NULL;
jclass JClasses::PrefetchStrategyType = NULL;


#ifndef SWIGEXPORT
# if defined(_WIN32) || defined(__WIN32__) || defined(__CYGWIN__)
#   if defined(STATIC_LINKED)
#     define SWIGEXPORT
#   else
#     define SWIGEXPORT __declspec(dllexport)
#   endif
# else
#   if defined(__GNUC__) && defined(GCC_HASCLASSVISIBILITY)
#     define SWIGEXPORT __attribute__ ((visibility("default")))
#   else
#     define SWIGEXPORT
#   endif
# endif
#endif

#ifdef __cplusplus
extern "C" {
#endif
SWIGEXPORT jint JNICALL Java_edu_umich_eac_eacJNI_JNICacheFetcher_1call(JNIEnv *jenv, jclass jcls, jint jarg1, jobject jarg1_, jint jarg2);
SWIGEXPORT jint JNICALL Java_edu_umich_eac_eacJNI_JNICacheFetcher_1bytesToTransfer(JNIEnv *jenv, jclass jcls, jint jarg1, jobject jarg1_);
SWIGEXPORT jdouble JNICALL Java_edu_umich_eac_eacJNI_JNICacheFetcher_1estimateFetchTime(JNIEnv *jenv, jclass jcls, jint jarg1, jobject jarg1_, jint jarg2, jint jarg3, jint jarg4);
SWIGEXPORT void JNICALL Java_edu_umich_eac_eacJNI_delete_1JNICacheFetcher(JNIEnv *jenv, jclass jcls, jint jarg1);

JNIEXPORT jobject JNICALL Java_edu_umich_eac_NetworkStats_getBestNetworkStats(JNIEnv *jenv, jclass jcls);
JNIEXPORT jobject JNICALL Java_edu_umich_eac_NetworkStats_getAllNetworkStatsByIp(JNIEnv *jenv, jclass jcls);
#ifdef __cplusplus
}
#endif

void
JClasses::init(JNIEnv *jenv)
{
    try {
        EnergyAdaptiveCache = find(jenv, "edu/umich/eac/EnergyAdaptiveCache");
        JNICacheFetcher = find(jenv, "edu/umich/eac/JNICacheFetcher");
        SWIGTYPE_p_void = find(jenv, "edu/umich/eac/SWIGTYPE_p_void");
        CancellationException = find(jenv, "java/util/concurrent/CancellationException");
        TimeoutException = find(jenv, "java/util/concurrent/TimeoutException");
        TimeUnit = find(jenv, "java/util/concurrent/TimeUnit");
        Future = find(jenv, "java/util/concurrent/Future");
        Assert = find(jenv, "junit/framework/Assert");
        PrefetchStrategyType = find(jenv, "edu/umich/eac/PrefetchStrategyType");
    } catch (std::runtime_error& e) {
        eac_dprintf("failed loading class: %s\n", e.what());
    }

    // force the linker to pull in these symbols
    // TODO: there's got to be a better way.  find it.
    Java_edu_umich_eac_eacJNI_JNICacheFetcher_1call(NULL, NULL, 0, NULL, 0);
    Java_edu_umich_eac_eacJNI_JNICacheFetcher_1bytesToTransfer(NULL, NULL, 0, NULL);
    Java_edu_umich_eac_eacJNI_JNICacheFetcher_1estimateFetchTime(NULL, NULL, 0, NULL, 0, 0, 0);
    Java_edu_umich_eac_eacJNI_delete_1JNICacheFetcher(NULL, NULL, 0);
    Java_edu_umich_eac_NetworkStats_getBestNetworkStats(NULL, NULL);
    Java_edu_umich_eac_NetworkStats_getAllNetworkStatsByIp(NULL, NULL);
}
