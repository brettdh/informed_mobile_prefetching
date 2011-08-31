#ifndef ENERGY_ADAPTIVE_CACHE_H_INCL
#define ENERGY_ADAPTIVE_CACHE_H_INCL

#include <pthread.h>
#include <jni.h>

class Future;

#include "JNICacheFetcher.h"

enum PrefetchStrategyType {
    AGGRESSIVE,
    CONSERVATIVE,
    ADAPTIVE,
    // ...
    NUM_STRATEGIES
};

enum PrefetchStrategyType getEnumValue(const char *typeStr);

class EnergyAdaptiveCache {
public:
    EnergyAdaptiveCache(JNIEnv *env, jobject context, PrefetchStrategyType type);
    EnergyAdaptiveCache(JNIEnv *env, jobject context, PrefetchStrategyType type,
                        struct timeval goalTime, int energyBudget, int dataBudget);
    ~EnergyAdaptiveCache();
    
    Future * prefetch(JNICacheFetcherPtr fetcher);
    Future * prefetchNow(JNICacheFetcherPtr fetcher);
    Future * fetch(JNICacheFetcherPtr fetcher);

    // Each thread that will use an EAC or a Future must create
    //   a detacher on the stack in its thread function, so that
    //   before the thread exits, the detacher's destructor will
    //   be called and the thread will be detached from the Java VM
    //   (if necessary) before exiting.
    // This unforunate kludge would be unnecessary if we were 
    //   targeting Android 2.0 or above, since Dalvik would
    //   allow us to run our own pthread_key destructor before
    //   aborting due to a still-attached native thread exiting.
    class detacher {
    public:
        static void addSelf(JavaVM *vm);
        ~detacher();
    private:
        static pthread_key_t key;
        
        class initer {
        public: 
            initer();
            ~initer();
        };
        static initer my_initer;
    };
private:
    void init(JNIEnv *env, jobject context, PrefetchStrategyType type,
              struct timeval goalTime, int energyBudget, int dataBudget);
    Future * prefetch(JNICacheFetcherPtr fetcher, bool now, bool demand);
    
    JavaVM *vm;
    jclass cacheClazz;
    jobject realCacheObj;
    jmethodID prefetchMID;
    jmethodID prefetchNowMID;
    jmethodID fetchMID;
};

#endif
