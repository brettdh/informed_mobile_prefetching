#ifndef ENERGY_ADAPTIVE_CACHE_H_INCL
#define ENERGY_ADAPTIVE_CACHE_H_INCL

#include <jni.h>
#include "CacheFetcher.h"

class EnergyAdaptiveCache {
public:
    EnergyAdaptiveCache(JNIEnv *env);
    
    Future * prefetch(CacheFetcher *fetcher);
private:
    JavaVM *vm;
    jobject realCacheObj;
    jmethodID prefetchMethodID;
};

#endif
