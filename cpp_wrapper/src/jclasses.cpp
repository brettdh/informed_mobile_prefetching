#include <stdlib.h>
#include <jni.h>
#include <stdexcept>
#include "eac_utility.h"
#include "jclasses.h"

jclass
JClasses::find(JNIEnv *jenv, const char *className)
{
    jclass local = jenv->FindClass(className);
    if (!local || JAVA_EXCEPTION_OCCURRED(jenv)) {
        throw std::runtime_error(className);
    }
    jclass global = (jclass) jenv->NewGlobalRef(local);
    if (!global || JAVA_EXCEPTION_OCCURRED(jenv)) {
        throw std::runtime_error(className);
    }
    return global;
}

jclass JClasses::EnergyAdaptiveCache = NULL;
jclass JClasses::JNICacheFetcher = NULL;
jclass JClasses::SWIGTYPE_p_void = NULL;
jclass JClasses::CancellationException = NULL;
jclass JClasses::TimeoutException = NULL;
jclass JClasses::TimeUnit = NULL;
jclass JClasses::Future = NULL;
jclass JClasses::Assert = NULL;

void
JClasses::init(JNIEnv *jenv)
{
    try {
        EnergyAdaptiveCache = find(jenv, "edu/umich/eac/EnergyAdaptiveCache");
        JNICacheFetcher = find(jenv, "edu/umich/eac/JNICacheFetcher");
        SWIGTYPE_p_void = find(jenv, "edu/umich/eac/SWIGTYPE_p_void");
        CancellationException = find(jenv, "java/util/concurrent/CancellationException");
        TimeoutException = find(jenv, "java/util/concurrent/TimeoutException");
        TimeUnit = find(jenv, "java/util/concurrent/TimeUnit");
        Future = find(jenv, "java/util/concurrent/Future");
        Assert = find(jenv, "junit/framework/Assert");
    } catch (std::runtime_error& e) {
        eac_dprintf("failed loading class: %s\n", e.what());
    }
}
