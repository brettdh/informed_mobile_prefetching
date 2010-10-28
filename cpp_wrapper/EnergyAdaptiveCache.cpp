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
    
    clazz = jenv->FindClass("edu.umich.eac/EnergyAdaptiveCache");
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

static JNIEnv *
getJNIEnv()
{
    JNIEnv *jenv = NULL;
    jint rc = vm->AttachCurrentThread(&jenv, NULL);
    if (rc == 0) {
        return jenv;
    }
    throw std::runtime_error("Couldn't get JNIEnv!");
}

Future *
EnergyAdaptiveCache::prefetch(CacheFetcher *fetcher)
{
    JNIEnv *jenv = getJNIEnv();
    
    Future *future = new Future(fetcher);
    // TODO: create a Java class with a native method that wraps
    //       the call of this fetcher's native method
    // XXX: wondering if it might be easier to just modify the
    //      native application's prefetch thread, since
    //      I have to create a Java wrapper app for it anyway.
}
