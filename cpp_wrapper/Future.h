#ifndef FUTURE_H_INCL
#define FUTURE_H_INCL

enum TimeUnit {
    MICROSECONDS,
    MILLISECONDS,
    NANOSECONDS,
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
    Future(CacheFetcher *fetcher);
    jobject getGlobalFutureObject();
};

#endif
