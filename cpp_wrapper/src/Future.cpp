#include "Future.h"
#include "utility.h"
#include <stdexcept>

long 
Future::getPtr(JNIEnv *jenv, jobject swig_voidptr)
{
    jclass clazz = jenv->FindClass("edu/umich/eac/SWIGTYPE_p_void");
    if (!clazz || JAVA_EXCEPTION_OCCURRED(jenv)) {
        throw std::runtime_error("Can't find SWIGTYPE_p_void class");
    }
    jmethodID mid = jenv->GetStaticMethodID(
        clazz, "getCPtr", "(Ledu/umich/eac/SWIGTYPE_p_void;)I"
    );
    if (!mid || JAVA_EXCEPTION_OCCURRED(jenv)) {
        throw std::runtime_error("Can't find getCPtr method");
    }
    long result = jenv->CallStaticLongMethod(clazz, mid, swig_voidptr);
    if (JAVA_EXCEPTION_OCCURRED(jenv)) {
        throw std::runtime_error("Future.getPtr() threw an exception");
    }
    eac_dprintf("getting (void*)long from future returns %x\n", result);
    return result;
}

void* 
Future::get()
{
    JNIEnv *jenv = getJNIEnv(vm);
    jobject jresult = jenv->CallObjectMethod(jfuture, get_mid);
    if (JAVA_EXCEPTION_OCCURRED(jenv)) {
        throw std::runtime_error("Future.get() threw an exception");
    }
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
    jclass clazz = jenv->FindClass("java/util/concurrent/TimeUnit");
    if (!clazz || JAVA_EXCEPTION_OCCURRED(jenv)) {
        throw std::runtime_error("Can't find TimeUnit class");
    }
    jfieldID fid = jenv->GetStaticFieldID(clazz, enumNames[units], 
                                          "Ljava/util/concurrent/TimeUnit;");
                                          
    if (!fid || JAVA_EXCEPTION_OCCURRED(jenv)) {
        throw std::runtime_error("Can't find TimeUnit constant");
    }
    jobject jobj = jenv->GetStaticObjectField(clazz, fid);
    if (JAVA_EXCEPTION_OCCURRED(jenv)) {
        throw std::runtime_error("Future.get() threw an exception");
    }
    return jobj;
}

void* 
Future::get(long timeout, enum TimeUnit units)
{
    JNIEnv *jenv = getJNIEnv(vm);
    jobject enumValue = getEnumValue(jenv, units);
    jobject jresult = jenv->CallObjectMethod(jfuture, getWithTimeout_mid, 
                                             timeout, enumValue);
    if (JAVA_EXCEPTION_OCCURRED(jenv)) {
        throw std::runtime_error("Future.get(timeout) threw an exception");
    }
    return (void*)getPtr(jenv, jresult);
}

void 
Future::cancel(bool mayInterrupt)
{
    JNIEnv *jenv = getJNIEnv(vm);
    jenv->CallVoidMethod(jfuture, cancel_mid, mayInterrupt);
    if (JAVA_EXCEPTION_OCCURRED(jenv)) {
        throw std::runtime_error("Future.cancel() threw an exception");
    }
}

bool 
Future::isCancelled()
{
    JNIEnv *jenv = getJNIEnv(vm);
    bool res = jenv->CallBooleanMethod(jfuture, isCancelled_mid);
    if (JAVA_EXCEPTION_OCCURRED(jenv)) {
        throw std::runtime_error("Future.isCancelled() threw an exception");
    }
    return res;
}

bool 
Future::isDone()
{
    JNIEnv *jenv = getJNIEnv(vm);
    bool res = jenv->CallBooleanMethod(jfuture, isDone_mid);
    if (JAVA_EXCEPTION_OCCURRED(jenv)) {
        throw std::runtime_error("Future.isDone() threw an exception");
    }
    return res;
}

Future::Future(JavaVM *jvm, jobject jfuture_)
{
    vm = jvm;
    jfuture = jfuture_; // global ref; release in destructor
    
    JNIEnv *jenv = getJNIEnv(jvm);
    futureClass = jenv->FindClass("java/util/concurrent/Future");
    if (!futureClass || JAVA_EXCEPTION_OCCURRED(jenv)) {
        throw std::runtime_error("Can't find Future class");
    }
    get_mid = jenv->GetMethodID(futureClass, "get", "()Ljava/lang/Object;");
    if (!get_mid || JAVA_EXCEPTION_OCCURRED(jenv)) {
        throw std::runtime_error("Can't find Future.get() method");
    }
    getWithTimeout_mid = jenv->GetMethodID(
        futureClass, "get", 
        "(JLjava/util/concurrent/TimeUnit;)Ljava/lang/Object;"
    );
    if (!getWithTimeout_mid || JAVA_EXCEPTION_OCCURRED(jenv)) {
        throw std::runtime_error("Can't find Future.get(timeout) method");
    }
    cancel_mid = jenv->GetMethodID(futureClass, "cancel", "(Z)Z");
    if (!cancel_mid || JAVA_EXCEPTION_OCCURRED(jenv)) {
        throw std::runtime_error("Can't find Future.cancel() method");
    }
    isCancelled_mid = jenv->GetMethodID(futureClass, "isCancelled", "()Z");
    if (!isCancelled_mid || JAVA_EXCEPTION_OCCURRED(jenv)) {
        throw std::runtime_error("Can't find Future.isCancelled() method");
    }
    isDone_mid = jenv->GetMethodID(futureClass, "isDone", "()Z");
    if (!isDone_mid || JAVA_EXCEPTION_OCCURRED(jenv)) {
        throw std::runtime_error("Can't find Future.isDone() method");
    }
}

Future::~Future()
{
    if (!isCancelled()) {
        cancel(true);
    }
    JNIEnv *jenv = getJNIEnv(vm);
    jenv->DeleteGlobalRef(jfuture);
}
