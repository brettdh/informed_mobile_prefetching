#ifndef FUTURE_H_INCL
#define FUTURE_H_INCL

enum TimeUnit {
    NANOSECONDS=0,
    MICROSECONDS,
    MILLISECONDS,
    SECONDS
};

class Future {
public:
    void* get();
    void* get(long timeout, enum TimeUnit units);
    void cancel(bool mayInterrupt);
    bool isCancelled();
    bool isDone();
private:
    friend class EnergyAdaptiveCache;
    
    JavaVM *vm;
    jobject jfuture;
    Future(JavaVM *jvm, jobject jfuture_);
};

#endif
