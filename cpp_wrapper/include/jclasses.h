#ifndef CLASSES_H_BKY7BYKE
#define CLASSES_H_BKY7BYKE

#include <jni.h>

class JClasses {
public:
    static void init(JNIEnv *jenv);
    
    static jclass EnergyAdaptiveCache;
    static jclass JNICacheFetcher;
    static jclass SWIGTYPE_p_void;
    static jclass CancellationException;
    static jclass TimeoutException;
    static jclass TimeUnit;
    static jclass Future;
    static jclass Assert;
    static jclass PrefetchStrategyType;
private:
    static jclass find(JNIEnv *jenv, const char *name);
};

#endif /* end of include guard: CLASSES_H_BKY7BYKE */
