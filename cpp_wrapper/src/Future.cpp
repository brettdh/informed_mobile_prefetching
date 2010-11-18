#include "Future.h"
#include "eac_utility.h"
#include "jclasses.h"
#include <stdexcept>
#include <sstream>
using std::ostringstream;

long 
Future::getPtr(JNIEnv *jenv, jobject swig_voidptr)
{
    jclass clazz = JClasses::SWIGTYPE_p_void;
    jmethodID mid = jenv->GetStaticMethodID(
        clazz, "getCPtr", "(Ledu/umich/eac/SWIGTYPE_p_void;)I"
    );
    if (!mid || JAVA_EXCEPTION_OCCURRED(jenv)) {
        throw std::runtime_error("Can't find getCPtr method");
    }
    long result = jenv->CallStaticIntMethod(clazz, mid, swig_voidptr);
    if (JAVA_EXCEPTION_OCCURRED(jenv)) {
        throw std::runtime_error("Future.getPtr() threw an exception");
    }
    eac_dprintf("getting (void*)long from future returns %lx\n", result);
    return result;
}

static void throwNativeException(JNIEnv *jenv, jthrowable err)
{
    jenv->ExceptionDescribe();
    jenv->ExceptionClear();

    jclass throwable = jenv->GetObjectClass(err);
    jclass cancel_exception = JClasses::CancellationException;
    jclass timeout_exception = JClasses::TimeoutException;
    jmethodID get_message
        = jenv->GetMethodID(throwable, "getMessage", "()Ljava/lang/String;");
        
    jmethodID to_string
        = jenv->GetMethodID(throwable, "toString", "()Ljava/lang/String;");
    
    if (!throwable || !cancel_exception || 
        !timeout_exception || !get_message ||
        !to_string || JAVA_EXCEPTION_OCCURRED(jenv)) {
        eac_dprintf("Failed to init exception-related class handles\n");
        abort();
    }
    
    ostringstream oss;
    oss << "Exception class: ";
    jstring clsName = static_cast<jstring>(jenv->CallObjectMethod(err, to_string));
    if (clsName) {
        const char *chars = jenv->GetStringUTFChars(clsName, NULL);
        if (chars) {
            oss << chars;
            jenv->ReleaseStringUTFChars(clsName, chars);
        } else {
            oss << "(unknown; failed to get string chars)";
        }
    } else {
        oss << "(unknown; toString failed)";
    }
        
    oss << " Message: ";
    jstring jstr = (jstring)jenv->CallObjectMethod(err, get_message);
    if (jstr) {
        const char *chars = jenv->GetStringUTFChars(jstr, NULL);
        if (chars) {
            oss << chars;
            jenv->ReleaseStringUTFChars(jstr, chars);
        } else {
            oss << "(unknown; failed to get string chars)";
        }
    } else {
        oss << "(unknown; getMessage failed)";
    }
    
    if (jenv->IsInstanceOf(err, cancel_exception)) {
        oss << " Future::CancellationException";
        throw Future::CancellationException(oss.str());
    } else if (jenv->IsInstanceOf(err, timeout_exception)) {
        oss << " Future::TimeoutException";
        throw Future::TimeoutException(oss.str());
    } else {
        jenv->Throw(err);
        oss << " (some other java.util.concurrent.Future exception)";
        throw std::runtime_error(oss.str());
    }
}

static void checkExceptions(JNIEnv *jenv)
{
    jthrowable err = jenv->ExceptionOccurred();
    if (err) {
        throwNativeException(jenv, err);;
    }
}

void* 
Future::get()
{
    JNIEnv *jenv = getJNIEnv(vm);
    jobject jresult = jenv->CallObjectMethod(jfuture, get_mid);
    checkExceptions(jenv);
    return (void*)getPtr(jenv, jresult);
}

static const char *enumNames[SECONDS+1] = {
    "NANOSECONDS",
    "MICROSECONDS",
    "MILLISECONDS",
    "SECONDS"
};

jobject
getEnumValue(JNIEnv *jenv, enum TimeUnit units)
{
    jclass clazz = JClasses::TimeUnit;
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
        throw std::runtime_error("Can't get TimeUnit constant");
    }
    return jobj;
}

void* 
Future::get(long long timeout, enum TimeUnit units)
{
    JNIEnv *jenv = getJNIEnv(vm);
    jobject enumValue = getEnumValue(jenv, units);
    jobject jresult = jenv->CallObjectMethod(jfuture, getWithTimeout_mid, 
                                             timeout, enumValue);
    checkExceptions(jenv);
    return (void*)getPtr(jenv, jresult);
}

bool
Future::cancel(bool mayInterrupt)
{
    JNIEnv *jenv = getJNIEnv(vm);
    bool ret = jenv->CallBooleanMethod(jfuture, cancel_mid, mayInterrupt);
    checkExceptions(jenv);
    return ret;
}

bool 
Future::isCancelled()
{
    JNIEnv *jenv = getJNIEnv(vm);
    bool res = jenv->CallBooleanMethod(jfuture, isCancelled_mid);
    checkExceptions(jenv);
    return res;
}

bool 
Future::isDone()
{
    JNIEnv *jenv = getJNIEnv(vm);
    bool res = jenv->CallBooleanMethod(jfuture, isDone_mid);
    checkExceptions(jenv);
    return res;
}

Future::Future(JavaVM *jvm, jobject jfuture_)
{
    vm = jvm;
    jfuture = jfuture_; // global ref; release in destructor
    
    JNIEnv *jenv = getJNIEnv(jvm);
    jclass futureClass = JClasses::Future;
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
