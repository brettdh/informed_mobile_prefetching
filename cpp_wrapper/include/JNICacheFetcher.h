#ifndef CACHE_FETCHER_H_INCL
#define CACHE_FETCHER_H_INCL

class JNICacheFetcher {
public:
    virtual void* call(long labels) = 0;
    virtual ~JNICacheFetcher() {}
};

#endif
