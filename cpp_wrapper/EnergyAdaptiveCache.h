#ifndef ENERGY_ADAPTIVE_CACHE_H_INCL
#define ENERGY_ADAPTIVE_CACHE_H_INCL

#include <jni.h>
#include "JNICacheFetcher.h"

class EnergyAdaptiveCache {
public:
    EnergyAdaptiveCache(JNIEnv *env);
    
    Future * prefetch(JNICacheFetcher *fetcher);
private:
    JavaVM *vm;
    jclass cacheClazz;
    jobject realCacheObj;
    jmethodID prefetchMethodID;
};

#endif
