#include "Future.h"
#include "utility.h"

long 
Future::getPtr(JNIEnv *jenv, jobject swig_voidptr)
{
    jclass clazz = jenv->FindClass("edu.umich.eac/SWIGTYPE_p_void");
    jmethodID mid = jenv->GetStaticMethodID(
        clazz, "getCPtr", "(Ledu/umich/eac/SWIGTYPE_p_void;)J"
    );
    long result = jenv->CallStaticLongMethod(clazz, mid, swig_voidptr);
    return result;
}

void* 
Future::get()
{
    JNIEnv *jenv = getJNIEnv(vm);
    jobject jresult = jenv->CallObjectMethod(futureClass, get_mid, jfuture);
    return (void*)getPtr(jenv, jresult);
}

static const char *enumNames[SECONDS+1] = {
    "NANOSECONDS",
    "MICROSECONDS",
    "MILLISECONDS",
    "SECONDS"
};

static jobject
getEnumValue(JNIEnv *jenv, enum TimeUnit units)
{
    jclass clazz = jenv->FindClass("java.util.concurrent/TimeUnit");
    jfieldID fid = jenv->GetStaticFieldID(clazz, enumNames[units], 
                                          "Ljava/util/concurrent/TimeUnit;");
    return jenv->GetStaticObjectField(clazz, fid);
}

void* 
Future::get(long timeout, enum TimeUnit units)
{
    JNIEnv *jenv = getJNIEnv(vm);
    jobject enumValue = getEnumValue(jenv, units);
    jobject jresult = jenv->CallObjectMethod(futureClass, getWithTimeout_mid, 
                                             jfuture, timeout, enumValue);
    return (void*)getPtr(jenv, jresult);
}

void 
Future::cancel(bool mayInterrupt)
{
    JNIEnv *jenv = getJNIEnv(vm);
    jenv->CallVoidMethod(futureClass, cancel_mid, jfuture, mayInterrupt);
}

bool 
Future::isCancelled()
{
    JNIEnv *jenv = getJNIEnv(vm);
    return jenv->CallBooleanMethod(futureClass, isCancelled_mid, jfuture);
}

bool 
Future::isDone()
{
    JNIEnv *jenv = getJNIEnv(vm);
    return jenv->CallBooleanMethod(futureClass, isDone_mid, jfuture);
}

Future::Future(JavaVM *jvm, jobject jfuture_)
{
    vm = jvm;
    jfuture = jfuture_; // global ref; release in destructor
    
    JNIEnv *jenv = getJNIEnv(jvm);
    futureClass = jenv->FindClass("java.util.concurrent/Future");
    get_mid = jenv->GetMethodID(futureClass, "get", "()Ljava/lang/Object;");
    getWithTimeout_mid = jenv->GetMethodID(
        futureClass, "get", 
	"(JLjava/util/concurrent/TimeUnit)Ljava/lang/Object;"
    );
    cancel_mid = jenv->GetMethodID(futureClass, "cancel", "(Z)Z");
    isCancelled_mid = jenv->GetMethodID(futureClass, "isCancelled", "()Z");
    isDone_mid = jenv->GetMethodID(futureClass, "isDone", "()Z");
}

Future::~Future()
{
    if (!isCancelled()) {
        cancel(true);
    }
    JNIEnv *jenv = getJNIEnv(vm);
    jenv->DeleteGlobalRef(jfuture);
}
