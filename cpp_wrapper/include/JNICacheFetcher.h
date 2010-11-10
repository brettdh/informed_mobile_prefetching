#ifndef CACHE_FETCHER_H_INCL
#define CACHE_FETCHER_H_INCL

class JNICacheFetcher {
public:
    virtual void* call(int labels) = 0;
    virtual ~JNICacheFetcher() {}
};

#include <boost/shared_ptr.hpp>
typedef boost::shared_ptr<JNICacheFetcher> JNICacheFetcherPtr;

#endif
