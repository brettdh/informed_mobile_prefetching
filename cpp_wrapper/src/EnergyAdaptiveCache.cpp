#include <stdexcept>
#include <stdlib.h>
#include <jni.h>
#include "EnergyAdaptiveCache.h"
#include "Future.h"
#include "JNICacheFetcher.h"
#include "eac_utility.h"
#include "jclasses.h"

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
    
    JClasses::init(jenv);
    
    cacheClazz = JClasses::EnergyAdaptiveCache;
    prefetchMID = jenv->GetMethodID(cacheClazz, "prefetch", prefetchMethodSig);
    if (!prefetchMID || JAVA_EXCEPTION_OCCURRED(jenv)) {
        throw std::runtime_error("Can't find prefetch method");
    }
    prefetchNowMID = jenv->GetMethodID(cacheClazz, "prefetchNow", 
                                       prefetchMethodSig);
    if (!prefetchNowMID || JAVA_EXCEPTION_OCCURRED(jenv)) {
        throw std::runtime_error("Can't find prefetchNow method");
    }
    fetchMID = jenv->GetMethodID(cacheClazz, "fetch", 
                                 prefetchMethodSig);
    if (!fetchMID || JAVA_EXCEPTION_OCCURRED(jenv)) {
        throw std::runtime_error("Can't find fetch method");
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
    eac_dprintf("Thread %x attached to JVM in ~EAC()\n", 
                (unsigned int) pthread_self());
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
    return prefetch(fetcher, false, false);
}

Future *
EnergyAdaptiveCache::prefetchNow(JNICacheFetcherPtr fetcher)
{
    return prefetch(fetcher, true, false);
}

Future *
EnergyAdaptiveCache::fetch(JNICacheFetcherPtr fetcher)
{
    return prefetch(fetcher, true, true);
}

Future *
EnergyAdaptiveCache::prefetch(JNICacheFetcherPtr fetcher, 
                              bool now, bool demand)
{
    JNIEnv *jenv = getJNIEnv(vm);
    
    if (!fetcher) {
        eac_dprintf("Error: prefetch called with NULL fetcher; "
                    "returning NULL\n");
        return NULL;
    }
    
    jclass clazz = JClasses::JNICacheFetcher;
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
    jmethodID method = NULL;
    if (demand) {
        method = fetchMID;
    } else {
        if (now) {
            method = prefetchNowMID;
        } else {
            method = prefetchMID;
        }
    }
    jobject local = jenv->CallObjectMethod(realCacheObj, method, fetcher_jobj);
    jobject future_jobj;
    if (!local || JAVA_EXCEPTION_OCCURRED(jenv) ||
        !(future_jobj = jenv->NewGlobalRef(local))) {
        throw std::runtime_error("Can't create Future java object");
    }
    
    // Wrap the returned Future in my proxy class
    Future *future = new Future(vm, future_jobj);
    return future;
}

pthread_key_t EnergyAdaptiveCache::detacher::key;

void 
EnergyAdaptiveCache::detacher::addSelf(JavaVM *vm)
{
    if (pthread_getspecific(key) == NULL) {
        eac_dprintf("Thread %x has stored VM pointer for detaching later\n",
                    (unsigned int) pthread_self());
        pthread_setspecific(key, vm);
    }
}

EnergyAdaptiveCache::detacher::~detacher()
{
    JavaVM *vm = (JavaVM *) pthread_getspecific(key);
    if (vm) {
        eac_dprintf("Detaching thread %x from VM\n", 
                    (unsigned int) pthread_self());
        vm->DetachCurrentThread();
        pthread_setspecific(key, NULL);
    }
}

EnergyAdaptiveCache::detacher::initer 
    EnergyAdaptiveCache::detacher::my_initer;

EnergyAdaptiveCache::detacher::initer::initer()
{
    pthread_key_create(&EnergyAdaptiveCache::detacher::key, NULL);
}

EnergyAdaptiveCache::detacher::initer::~initer()
{
    pthread_key_delete(EnergyAdaptiveCache::detacher::key);
}

