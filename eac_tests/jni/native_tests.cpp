#include <jni.h>
#include <time.h>
#include <string.h>

#include <EnergyAdaptiveCache.h>
#include <JNICacheFetcher.h>
#include <Future.h>

void doAssertions(JNIEnv *jenv, jobject jobj, bool futureNotDoneYet,
		  const char *refStr, const char *str,
		  bool futureDone, bool cancelled)
{
    bool stringMatch = (strcmp(refStr, str) == 0);

    jclass clazz = jenv->GetObjectClass(jobj);
    jmethodID mid = jenv->GetMethodID(clazz, "doAssertions", "(ZZZZ)V");
    jenv->CallVoidMethod(clazz, mid, jobj, futureNotDoneYet, stringMatch, 
			 futureDone, cancelled);
}

class FakeFetcher : public JNICacheFetcher {
    long delaySeconds;
public:
    static const char msg[];
    
    FakeFetcher(long delaySecs) {
	delaySeconds = delaySecs;
    }
    
    virtual void *call(long labels) {
	if (delaySeconds > 0) {
	    struct timespec timeout;
	    timeout.tv_sec = delaySeconds;
	    timeout.tv_nsec = 0;
	    nanosleep(&timeout, NULL);
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
						  jlong delaySecs)
{
    EnergyAdaptiveCache cache(jenv);

    bool futureNotDoneYet;
    Future *f = cache.prefetch(new FakeFetcher(delaySecs));
    if (delaySecs > 0) {
	futureNotDoneYet = f->isDone();
    } else {
	futureNotDoneYet = false;
    }
    const char *str = (const char *)f->get();
    bool futureDone = f->isDone();
    bool cancelled = f->isCancelled();
    doAssertions(jenv, jobj, futureNotDoneYet, FakeFetcher::msg, str, 
		 futureDone, cancelled);
//} catch (Exception e) {
//	e.printStackTrace();
//	fail("Unexpected exception:" + e.toString());
//    }
}
