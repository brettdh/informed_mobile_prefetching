#include <stdexcept>
#include <jni.h>
#include <time.h>
#include <string.h>

#include <EnergyAdaptiveCache.h>
#include <JNICacheFetcher.h>
#include <Future.h>
#include <eac_utility.h>
#include "utility.h"

void doAssertions(JNIEnv *jenv, jobject jobj, bool futureNotDoneYet,
                  const char *refStr, const char *str,
                  bool futureDone, bool cancelled)
{
    bool stringMatch = (strcmp(refStr, str) == 0);

    jclass clazz = jenv->GetObjectClass(jobj);
    if (!clazz) {
        throw std::runtime_error("Failed to get class from object");
    }
    jmethodID mid = jenv->GetMethodID(clazz, "doAssertions", "(ZZZZ)V");
    if (!mid) {
        throw std::runtime_error("Failed to get doAssertions method id");
    }
    jenv->CallVoidMethod(jobj, mid, futureNotDoneYet, stringMatch, 
                         futureDone, cancelled);
    if (jenv->ExceptionCheck()) {
        jenv->ExceptionDescribe();
        throw std::runtime_error("doAssertions threw an exception");
    }
}

class FakeFetcher : public JNICacheFetcher {
    long delaySeconds;
public:
    static const char msg[];
    
    FakeFetcher(long delaySecs) {
        delaySeconds = delaySecs;
    }
    
    virtual void *call(int labels) {
        if (delaySeconds > 0) {
            thread_sleep(delaySeconds * 1000);
        }
        return (void*)msg;
    }
};

const char FakeFetcher::msg[] = "This is the string you asked for.";

#ifdef __cplusplus
extern "C"
#endif
JNIEXPORT void JNICALL 
Java_edu_umich_eac_tests_NativeTest_testWithDelay(JNIEnv *jenv, jobject jobj, 
                                                  jint delaySecs)
{
    EnergyAdaptiveCache cache(jenv, AGGRESSIVE);

    bool futureNotDoneYet;
    JNICacheFetcherPtr ptr(new FakeFetcher(delaySecs));
    Future *f = cache.prefetch(ptr);
    if (delaySecs > 0) {
        futureNotDoneYet = f->isDone();
    } else {
        futureNotDoneYet = false;
    }
    const char *str = (const char *)f->get();
    bool futureDone = f->isDone();
    bool cancelled = f->isCancelled();

    try {
        // catch AssertionException not necessary here; doesn't use jniunit::asserts
        doAssertions(jenv, jobj, futureNotDoneYet, FakeFetcher::msg, str, 
                     futureDone, cancelled);
    } catch (std::runtime_error& e) {
        eac_dprintf("Fatal error in testWithDelay: %s\n", e.what());
    }
}


