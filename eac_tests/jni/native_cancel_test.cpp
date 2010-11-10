#include <stdexcept>
#include <jni.h>
#include <time.h>
#include <string.h>

#include <EnergyAdaptiveCache.h>
#include <JNICacheFetcher.h>
#include <Future.h>
#include <libcmm.h>
#include <eac_utility.h>

#include "utility.h"
#include "jniunit.h"
using jniunit::assertTrue;
using jniunit::assertFalse;
using jniunit::fail;

#ifdef __cplusplus
#define CDECL extern "C"
#else
#define CDECL
#endif

class CancelFetcher : public JNICacheFetcher {
public:
    void *call(int labels) {
        // Never return; just wait to be cancelled.
        while (true) {
            thread_sleep(3600 * 1000);
        }
    }
};

//public class CancelTest extends InstrumentationTestCase {

static EnergyAdaptiveCache *cache;
static JNICacheFetcherPtr fetcher;
static Future *future;
    
CDECL JNIEXPORT void JNICALL 
Java_edu_umich_eac_tests_NativeCancelTest_setUp(JNIEnv *jenv, jobject jobj)
{
    cache = new EnergyAdaptiveCache(jenv);
    fetcher.reset(new CancelFetcher());
    future = cache->prefetch(fetcher);
    
    thread_sleep(1000);
    try {
        assertFalse(jenv, "Future not done yet", future->isDone());
        assertFalse(jenv, "Future not cancelled yet", future->isCancelled());
    } catch (jniunit::AssertionException& e) {
        // test failed; junit will detect it
    }
}

CDECL JNIEXPORT void JNICALL 
Java_edu_umich_eac_tests_NativeCancelTest_tearDown(
    JNIEnv *jenv, jobject jobj)
{
    delete future;
    fetcher.reset();
    delete cache;
}

CDECL JNIEXPORT void JNICALL 
Java_edu_umich_eac_tests_NativeCancelTest_testGetTimeout(
    JNIEnv *jenv, jobject jobj)
{
    try {
        try {
            void *nothing = future->get(1, SECONDS);
            fail(jenv, "Should have thrown TimeoutException");
        } catch (Future::TimeoutException e) {
            assertFalse(jenv, "Future not done after timeout", future->isDone());
            assertFalse(jenv, "Future not cancelled after timeout", 
                        future->isCancelled());
        }
    } catch (jniunit::AssertionException& e) {
        // test failed; junit will detect it
    }
}
    
CDECL JNIEXPORT void JNICALL 
Java_edu_umich_eac_tests_NativeCancelTest_testCancel(
    JNIEnv *jenv, jobject jobj)
{
    try {
        try {
            bool ret = future->cancel(true);
            assertTrue(jenv, "cancel succeeds", ret);
            assertTrue(jenv, "Future is cancelled", future->isCancelled());
            assertTrue(jenv, "Future is not done after cancel", future->isDone());
            try {
                void *nothing = future->get(1, SECONDS);
                fail(jenv, "Cancelled Future.get() should throw CancellationException");
            } catch (Future::TimeoutException& e) {
                fail(jenv, "Cancelled Future.get() should fail outright, not time out");
            } catch (Future::CancellationException& e) {
                // success
            }
        } catch (jniunit::AssertionException& e) {
            // test failed; junit will detect it
        }
    } catch (jniunit::AssertionException& e) {
        // test failed; junit will detect it
    }
}

