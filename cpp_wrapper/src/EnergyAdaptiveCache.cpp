#include <stdexcept>
#include <stdlib.h>
#include <jni.h>
#include "EnergyAdaptiveCache.h"
#include "Future.h"
#include "JNICacheFetcher.h"
#include "eac_utility.h"

static const char *prefetchMethodSig = 
    "(Ledu/umich/eac/CacheFetcher;)Ljava/util/concurrent/Future;";

EnergyAdaptiveCache::EnergyAdaptiveCache(JNIEnv *jenv)
    : vm(NULL), cacheClazz(NULL), realCacheObj(NULL), prefetchMID(NULL) 
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
    prefetchMID = jenv->GetMethodID(cacheClazz, "prefetch", prefetchMethodSig);
    if (!prefetchMID || JAVA_EXCEPTION_OCCURRED(jenv)) {
        throw std::runtime_error("Can't find prefetch method");
    }
    prefetchNowMID = jenv->GetMethodID(cacheClazz, "prefetchNow", 
                                       prefetchMethodSig);
    if (!prefetchNowMID || JAVA_EXCEPTION_OCCURRED(jenv)) {
        throw std::runtime_error("Can't find prefetchNow method");
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
        //vm->DetachCurrentThread();
    }
}

Future *
EnergyAdaptiveCache::prefetch(JNICacheFetcherPtr fetcher)
{
    return prefetch(fetcher, false);
}

Future *
EnergyAdaptiveCache::prefetchNow(JNICacheFetcherPtr fetcher)
{
    return prefetch(fetcher, true);
}

Future *
EnergyAdaptiveCache::prefetch(JNICacheFetcherPtr fetcher, bool now)
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
    jmethodID ctor = jenv->GetMethodID(clazz, "<init>", "(IZ)V");
    if (!ctor || JAVA_EXCEPTION_OCCURRED(jenv)) {
        throw std::runtime_error("Can't find JNICacheFetcher ctor");
    }
    
    // create new shared_ptr, owned by the java object.
    //  when that object is garbage collected, it will delete
    //  the shared_ptr (not necessarily the raw ptr).
    JNICacheFetcherPtr *newSharedPtr = new JNICacheFetcherPtr(fetcher);
    jobject fetcher_jobj = jenv->NewObject(
        clazz, ctor, (int)newSharedPtr, true
    );
    if (!fetcher_jobj || JAVA_EXCEPTION_OCCURRED(jenv)) {
        // the object wasn't constructed, so it won't be garbage collected,
        //  and this smart ptr would be leaked (also preventing the real data
        //  from ever being freed).  So clean it up.
        delete newSharedPtr;
        throw std::runtime_error("Can't create JNICacheFetcher "
                                 "java object");
    }
    
    // create a java Future object by submitting this java Fetcher object
    //  to the java EnergyAdaptiveCache
    eac_dprintf("cacheClazz: %p  prefetchMID: %p "
                "realCacheObj %p fetcher_jobj: %p native_fetcher %p\n",
                cacheClazz, prefetchMID, realCacheObj, fetcher_jobj,
                get_pointer(fetcher));
    jobject local = jenv->CallObjectMethod(realCacheObj, 
                                           now ? prefetchNowMID : prefetchMID,
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
