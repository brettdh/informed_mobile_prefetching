#include <jni.h>
#include <eac_utility.h>

/* from intnw */
#include <libcmm_external_ipc.h>
#include <net_interface.h>

#include <vector>
#include <string>
#include <sstream>
#include <stdexcept>
using std::vector;
using std::string;
using std::ostringstream;
using std::runtime_error;

static jobject newNetworkStats(JNIEnv *jenv)
{
    jobject stats = NULL;
    jclass statsClass = jenv->FindClass("edu/umich/eac/NetworkStats");
    if (!statsClass || JAVA_EXCEPTION_OCCURRED(jenv)) {
        throw runtime_error("Can't find NetworkStats class");
    }
    jmethodID ctor = jenv->GetMethodID(
        statsClass, "<init>", "()V"
    );
    if (!ctor || JAVA_EXCEPTION_OCCURRED(jenv)) {
        throw runtime_error("Can't find NetworkStats constructor");
    }
    stats = jenv->NewObject(statsClass, ctor);
    if (!stats || JAVA_EXCEPTION_OCCURRED(jenv)) {
        throw runtime_error("Can't create NetworkStats java object");
    }
    
    return stats;
}

static void fillField(JNIEnv *jenv, jobject obj, string fieldName, int fieldValue)
{
    jclass objClass = jenv->GetObjectClass(obj);
    if (!objClass || JAVA_EXCEPTION_OCCURRED(jenv)) {
        throw runtime_error("GetClass failed in fillField");
    }
    jfieldID fieldID = jenv->GetFieldID(objClass, fieldName.c_str(), "I");
    if (!fieldID || JAVA_EXCEPTION_OCCURRED(jenv)) {
        ostringstream oss;
        oss << "fillField: failed to get field ID for " << fieldName;
        throw runtime_error(oss.str());
    }
    jenv->SetIntField(obj, fieldID, fieldValue);
    if (JAVA_EXCEPTION_OCCURRED(jenv)) {
        ostringstream oss;
        oss << "fillField: failed to set " << fieldName << "field";
        throw runtime_error(oss.str());
    }
}

static void fillStats(JNIEnv *jenv, jobject stats, int bw_down, int bw_up, int rtt_ms)
{
    fillField(jenv, stats, "bandwidthDown", bw_down);
    fillField(jenv, stats, "bandwidthUp", bw_up);
    fillField(jenv, stats, "rttMillis", rtt_ms);
}

extern "C"
JNIEXPORT jobject JNICALL 
Java_edu_umich_eac_NetworkStats_getNetworkStats(JNIEnv *jenv, jclass cls)
{
    // look at all available networks and pick the one with the best bandwidth.

    jobject stats = NULL;
    try {
        stats = newNetworkStats(jenv);
        fillStats(jenv, stats, 0, 0, 0);
    } catch (runtime_error& e) {
        return NULL;
    }
    
    int best_bandwidth = -1;
    int index = -1;

    vector<struct net_interface> ifaces;
    bool result = get_local_interfaces(ifaces);
    if (result) {
        for (size_t i = 0; i < ifaces.size(); ++i) {
            int bandwidth_sum = ifaces[i].bandwidth_down + ifaces[i].bandwidth_up;
            if (bandwidth_sum > best_bandwidth) {
                best_bandwidth = bandwidth_sum;
                index = (int) i;
            }
        }

        if (index != -1) {
            struct net_interface& iface = ifaces[index];
            fillStats(jenv, stats,
                      iface.bandwidth_down, 
                      iface.bandwidth_up,
                      iface.RTT);
        }
    }

    return stats;
}
