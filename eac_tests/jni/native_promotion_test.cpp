#include <stdexcept>
#include <jni.h>
#include <time.h>
#include <string.h>

#include <EnergyAdaptiveCache.h>
#include <JNICacheFetcher.h>
#include <Future.h>
#include <libcmm.h>

#include "jniunit.h"
using jniunit::assertTrue;
using jniunit::assertFalse;
using jniunit::fail;

#ifdef __cplusplus
#define CDECL extern "C"
#else
#define CDECL
#endif

static void thread_sleep(int millis)
{
    struct timespec ts;
    ts.tv_sec = (millis / 1000);
    ts.tv_nsec = (millis - (ts.tv_sec * 1000)) * 1000000;
    nanosleep(&ts, NULL);
}

class PromotionFetcher : public JNICacheFetcher {
public:
    static const char prefetch_str[];
    static const char demand_fetch_str[];
    virtual void * call(int labels) {
        if ((labels & CMM_LABEL_BACKGROUND) != 0) {
            // simulate a background fetch taking longer (exaggerated)
            thread_sleep(3000);
            return (void*)prefetch_str;
        } else {
            return (void*)demand_fetch_str;
        }
    }
};

const char PromotionFetcher::prefetch_str[] = "Got prefetch result";
const char PromotionFetcher::demand_fetch_str[] = "Got demand fetch result";

static EnergyAdaptiveCache *cache;
static JNICacheFetcherPtr fetcher;
static Future *future;

CDECL JNIEXPORT void JNICALL 
Java_edu_umich_eac_tests_NativePromotionTest_setUp(JNIEnv *jenv, jobject jobj)
{
    cache = new EnergyAdaptiveCache(jenv);
    fetcher.reset(new PromotionFetcher());
    future = cache->prefetchNow(fetcher);

    thread_sleep(1000);
    
    assertFalse(jenv, "Future not done yet", future->isDone());
    assertFalse(jenv, "Future not cancelled yet", future->isCancelled());
}

CDECL JNIEXPORT void JNICALL 
Java_edu_umich_eac_tests_NativePromotionTest_tearDown(
    JNIEnv *jenv, jobject jobj)
{
    delete future;
    fetcher.reset();
    delete cache;
}

CDECL JNIEXPORT void JNICALL 
Java_edu_umich_eac_tests_NativePromotionTest_testWaitForPrefetch(
    JNIEnv *jenv, jobject jobj)
{
    try {
         // wait for prefetch to complete
        thread_sleep(3000);
        
        char *msg = (char *)future->get(1, SECONDS);
        assertTrue(jenv, "Did the prefetch", strstr(msg, "prefetch"));
    } catch (Future::TimeoutException e) {
        fail(jenv, "Prefetch shouldn't time out");
    }
}


CDECL JNIEXPORT void JNICALL 
Java_edu_umich_eac_tests_NativePromotionTest_testPromotion(
    JNIEnv *jenv, jobject jobj)
{
    try {
        // should promote BG fetch to FG
        char *msg = (char *)future->get(1, SECONDS);
        assertTrue(jenv, "Did the demand fetch", strstr(msg, "demand"));
    } catch (Future::TimeoutException e) {
        fail(jenv, "Demand fetch shouldn't time out");
    }    
}
