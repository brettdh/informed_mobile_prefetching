#include <stdexcept>
#include <stdlib.h>
#include <jni.h>
#include "EnergyAdaptiveCache.h"
#include "Future.h"
#include "JNICacheFetcher.h"
#include "utility.h"

static const char *prefetchMethodName = "prefetch";
static const char *prefetchMethodSig = 
    "(Ledu/umich/eac/CacheFetcher;)Ljava/util/concurrent/Future;";

EnergyAdaptiveCache::EnergyAdaptiveCache(JNIEnv *jenv)
    : vm(NULL), cacheClazz(NULL), realCacheObj(NULL), prefetchMethodID(NULL) 
{
    vm = NULL;
    jint rc = jenv->GetJavaVM(&vm);
    if (rc != 0) {
        throw std::runtime_error("Can't get the Java VM");
    }
    
    cacheClazz = jenv->FindClass("edu/umich/eac/EnergyAdaptiveCache");
    if (!cacheClazz || JAVA_EXCEPTION_OCCURRED(jenv)) {
        throw std::runtime_error("Can't find EnergyAdaptiveCache class");
    }
    prefetchMethodID = jenv->GetMethodID(cacheClazz, 
                                         prefetchMethodName, 
                                         prefetchMethodSig);
    if (!prefetchMethodID || JAVA_EXCEPTION_OCCURRED(jenv)) {
        throw std::runtime_error("Can't find prefetch method");
    }
    jmethodID ctor = jenv->GetMethodID(cacheClazz, "<init>", "()V");
    if (!ctor || JAVA_EXCEPTION_OCCURRED(jenv)) {
        throw std::runtime_error("Can't find EAC constructor");
    }
    jobject local = jenv->NewObject(cacheClazz, ctor);
    if (!local || JAVA_EXCEPTION_OCCURRED(jenv) ||
        !(realCacheObj = jenv->NewGlobalRef(local))) {
        throw std::runtime_error("Can't create EnergyAdaptiveCache "
                                 "java object");
    }
}

EnergyAdaptiveCache::~EnergyAdaptiveCache()
{
    JNIEnv *jenv = NULL;
    jint rc = vm->AttachCurrentThread(&jenv, NULL);
    if (rc == 0) {
        jenv->DeleteGlobalRef(realCacheObj);
        vm->DetachCurrentThread();
    }
}

Future *
EnergyAdaptiveCache::prefetch(JNICacheFetcher *fetcher)
{
    JNIEnv *jenv = getJNIEnv(vm);
    
    if (!fetcher) {
        eac_dprintf("Error: prefetch called with NULL fetcher; "
                    "returning NULL\n");
        return NULL;
    }
    
    jclass clazz = jenv->FindClass("edu/umich/eac/JNICacheFetcher");
    if (!clazz || JAVA_EXCEPTION_OCCURRED(jenv)) {
        throw std::runtime_error("Can't find JNICacheFetcher class");
    }
    jmethodID ctor = jenv->GetMethodID(clazz, "<init>", "(JZ)V");
    if (!ctor || JAVA_EXCEPTION_OCCURRED(jenv)) {
        throw std::runtime_error("Can't find JNICacheFetcher ctor");
    }
    jobject fetcher_jobj = jenv->NewObject(clazz, ctor, (long)fetcher, true);
    if (!fetcher_jobj || JAVA_EXCEPTION_OCCURRED(jenv)) {
        throw std::runtime_error("Can't create JNICacheFetcher "
                                 "java object");
    }
    
    // create a java Future object by submitting this java Fetcher object
    //  to the java EnergyAdaptiveCache
    eac_dprintf("cacheClazz: %p  prefetchMethodID: %p "
                "realCacheObj %p fetcher_jobj: %p native_fetcher %p\n",
                cacheClazz, prefetchMethodID, realCacheObj, fetcher_jobj,
                fetcher);
    jobject local = jenv->CallObjectMethod(realCacheObj, prefetchMethodID,
                                           fetcher_jobj);
    jobject future_jobj;
    if (!local || JAVA_EXCEPTION_OCCURRED(jenv) ||
        !(future_jobj = jenv->NewGlobalRef(local))) {
        throw std::runtime_error("Can't create Future java object");
    }
    
    // Wrap the returned Future in my proxy class
    Future *future = new Future(vm, future_jobj);
    return future;
}
