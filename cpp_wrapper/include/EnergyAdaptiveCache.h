#ifndef ENERGY_ADAPTIVE_CACHE_H_INCL
#define ENERGY_ADAPTIVE_CACHE_H_INCL

#include <jni.h>

class Future;

#include "JNICacheFetcher.h"

class EnergyAdaptiveCache {
public:
    EnergyAdaptiveCache(JNIEnv *env);
    ~EnergyAdaptiveCache();
    
    Future * prefetch(JNICacheFetcherPtr fetcher);
    Future * prefetchNow(JNICacheFetcherPtr fetcher);
private:
    Future * prefetch(JNICacheFetcherPtr fetcher, bool now);
    
    JavaVM *vm;
    jclass cacheClazz;
    jobject realCacheObj;
    jmethodID prefetchMID;
    jmethodID prefetchNowMID;
};

#endif
