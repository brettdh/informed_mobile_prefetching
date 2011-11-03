#ifndef CACHE_FETCHER_H_INCL
#define CACHE_FETCHER_H_INCL

class JNICacheFetcher {
public:
    virtual void* call(int labels) = 0;
    virtual int bytesToTransfer() = 0;
    virtual double estimateFetchTime(int worstBandwidthDown,
                                     int worstBandwidthUp,
                                     int worstRTT) = 0;
    virtual void onCancelled() {}
    virtual ~JNICacheFetcher() {}
};

#include <boost/shared_ptr.hpp>
typedef boost::shared_ptr<JNICacheFetcher> JNICacheFetcherPtr;

#endif
