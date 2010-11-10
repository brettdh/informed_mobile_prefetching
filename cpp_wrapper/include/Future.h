#ifndef FUTURE_H_INCL
#define FUTURE_H_INCL

#include <jni.h>
#include <stdexcept>

enum TimeUnit {
    NANOSECONDS=0,
    MICROSECONDS,
    MILLISECONDS,
    SECONDS
};
    
class Future {
public:
    void* get();
    void* get(long long timeout, enum TimeUnit units);
    bool cancel(bool mayInterrupt);
    bool isCancelled();
    bool isDone();

    ~Future();
    
    class TimeoutException : public std::runtime_error {};
private:
    friend class EnergyAdaptiveCache;
    
    JavaVM *vm;
    jobject jfuture;
    Future(JavaVM *jvm, jobject jfuture_);
    long getPtr(JNIEnv *jenv, jobject swig_voidptr);

    jclass futureClass;
    jmethodID get_mid;
    jmethodID getWithTimeout_mid;
    jmethodID cancel_mid;
    jmethodID isCancelled_mid;
    jmethodID isDone_mid;
};

#endif
