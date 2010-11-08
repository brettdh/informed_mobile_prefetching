#include "Future.h"

long 
Future::getPtr(JNIEnv *jenv, jobject swig_voidptr)
{
    jclass clazz = jenv->FindClass("edu.umich.eac/SWIGTYPE_p_void");
    jmethodID mid = jenv->GetStaticMethodID(
        clazz, "getCPtr", "(Ledu/umich/eac/SWIGTYPE_p_void;)J"
    );
    long result = jenv->CallStaticLongMethod(clazz, mid, swig_voidptr)
    return result;
}

void* 
Future::get()
{
    JNIEnv *jenv = getJNIEnv(vm);
    jobject jresult = jenv->CallObjectMethod(clazz, get_mid, jfuture);
    return getPtr(jenv, jresult);
}

static const char *enumStrings[SECONDS+1] = {
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
    jobject jresult = jenv->CallObjectMethod(clazz, getWithTimeout_mid, 
                                             jfuture, timeout, enumValue);
    return getPtr(jenv, jresult);
}

void 
Future::cancel(bool mayInterrupt)
{
    JNIEnv *jenv = getJNIEnv(vm);
    jenv->CallVoidMethod(clazz, cancel_mid, jfuture, mayInterrupt);
}

bool 
Future::isCancelled()
{
    JNIEnv *jenv = getJNIEnv(vm);
    jenv->CallBooleanMethod(clazz, isCancelled_mid, jfuture);
}

bool Future::isDone()
{
    JNIEnv *jenv = getJNIEnv(vm);
    jenv->CallBooleanMethod(clazz, isDone_mid, jfuture);
}

Future::Future(JavaVM *jvm, jobject jfuture_)
{
    vm = jvm;
    jfuture = jfuture_; // global ref; release in destructor
    
    JNIEnv *jenv = getJNIEnv(jvm);
    clazz = jenv->FindClass("java.util.concurrent/Future");
    get_mid = jenv->GetMethodID(clazz, "get", "()Ljava/lang/Object;");
    getWithTimeout_mid = jenv->GetMethodID(
        clazz, "get", "(JLjava/util/concurrent/TimeUnit)Ljava/lang/Object;"
    );
    cancel_mid = jenv->GetMethodID(clazz, "cancel", "(Z)Z");
    isCancelled_mid = jenv->GetMethodID(clazz, "isCancelled", "()Z");
    isDone_mid = jenv->GetMethodID(clazz, "isDone", "()Z");
    
    
}

~Future()
{
    if (!isCancelled()) {
        cancel(true);
    }
    jenv->DeleteGlobalRef(jfuture);
}
