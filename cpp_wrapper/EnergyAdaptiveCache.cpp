#include <jni.h>
#include "Future.h"
#include "CacheFetcher.h"

static const char *prefetchMethodName = "prefetch";
static const char *prefetchMethodSig = 
    "(Ledu/umich/eac/CacheFetcher;)Ljava/util/concurrent/Future;";

EnergyAdaptiveCache::EnergyAdaptiveCache(JNIEnv *jenv)
{
    vm = NULL;
    jint rc = jenv->GetJavaVM(&vm);
    if (rc != 0) {
        throw std::runtime_error("Can't get the Java VM");
    }
    
    cacheClazz = jenv->FindClass("edu.umich.eac/EnergyAdaptiveCache");
    prefetchMethodID = jenv->GetMethodID(clazz, 
                                         prefetchMethodName, 
                                         prefetchMethodSig);
    
    jmethodID ctor = jenv->GetMethodID(clazz, "<init>", "()V");
    jobject local = jenv->NewObject(clazz, ctor);
    if (!local || 
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
    
    jclass clazz = jenv->FindClass("edu.umich.eac/JNICacheFetcher");
    jmethodID ctor = jenv->GetMethodID(clazz, "<init>", "(JZ)V");
    jobject fetcher_jobj = jenv->NewObject(clazz, ctor, (long)fetcher, true);
    if (!fetcher_jobj) {
        throw std::runtime_error("Can't create JNICacheFetcher "
                                 "java object");
    }
    
    // create a java Future object by submitting this java Fetcher object
    //  to the java EnergyAdaptiveCache
    jobject local = jenv->CallObjectMethod(cacheClazz, prefetchMethodID,
                                           fetcher_jobj);
    jobject future_jobj;
    if (!local ||
        !(future_jobj = jenv->NewGlobalRef(local))) {
        throw std::runtime_error("Can't create Future java object");
    }
    
    // Wrap the returned Future in my proxy class
    Future *future = new Future(vm, future_jobj);
    return future;
}
