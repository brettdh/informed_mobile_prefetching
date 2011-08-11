#include <jni.h>
#include <libcmm_external_ipc.h>

JNIEXPORT jobject JNICALL 
Java_edu_umich_eac_AdaptivePrefetchStrategy_getNetworkStats(JNIEnv *jenv, jobject jobj)
{
    // look at all available networks and pick the one with the best bandwidth.

    jobject stats = NULL;
    
    jclass local = jenv->FindClass("edu/umich/eac/NetworkStats");
    if (!local || jenv->ExceptionCheck()) {
        return stats;
    }
    // TODO: initialize stats object with zeroes

    int best_bandwidth = -1;
    int index = -1;

    vector<struct net_interface> ifaces;
    bool result = get_local_interfaces(ifaces);
    if (result) {
        for (size_t i = 0; i < ifaces.size(); ++i) {
            int bandwidth_sum = ifaces[i].bandwidth_down + ifaces[i].bandwdith_up;
            if (bandwidth_sum > best_bandwidth) {
                best_bandwidth = bandwidth_sum;
                index = (int) i;
            }
        }

        if (index != -1) {
            // TODO: fill in stats object with stats
        }
    }

    return stats;
}
