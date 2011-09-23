#include <jni.h>
#include <eac_utility.h>

/* from intnw */
#include <libcmm_external_ipc.h>
#include <net_interface.h>

#include <netinet/in.h>
#include <arpa/inet.h>

#include <vector>
#include <string>
#include <sstream>
#include <stdexcept>
using std::vector;
using std::string;
using std::ostringstream;
using std::runtime_error;

static jclass networkStatsClass;
static jmethodID networkStatsCtor;
static jclass hashMapClass;
static jmethodID hashMapCtor;
static jmethodID hashMapPut;
static jclass integerClass;
static jmethodID integerCtor;

static void
checkJavaError(JNIEnv *jenv, bool fail_condition, const char *err_msg)
{
    if (fail_condition || JAVA_EXCEPTION_OCCURRED(jenv)) {
        throw runtime_error(err_msg);
    }
}

static void initCachedJNIData(JNIEnv *jenv)
{
    networkStatsClass = jenv->FindClass("edu/umich/eac/NetworkStats");
    checkJavaError(jenv, !networkStatsClass, "Can't find NetworkStats class");

    networkStatsCtor = jenv->GetMethodID(networkStatsClass, "<init>", "()V");
    checkJavaError(jenv, !networkStatsCtor, "Can't find NetworkStats constructor");
    
    hashMapClass = jenv->FindClass("java/util/HashMap");
    checkJavaError(jenv, !hashMapClass, "Can't find HashMap class");

    hashMapCtor = jenv->GetMethodID(hashMapClass, "<init>", "()V");
    checkJavaError(jenv, !hashMapCtor, "Can't find HashMap constructor");

    const char *putSignature = "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;";
    hashMapPut = jenv->GetMethodID(hashMapClass, "put", putSignature);
    checkJavaError(jenv, !hashMapPut, "Can't find HashMap put method");
    
    integerClass = jenv->FindClass("java/lang/Integer");
    integerCtor = jenv->GetMethodID(integerClass, "<init>", "(I)V");
}

static jobject newNetworkStats(JNIEnv *jenv)
{
    eac_dprintf("JNIEnv ptr: %p\n", jenv);
    eac_dprintf("networkStatsClass ptr: %p\n", networkStatsClass);
    eac_dprintf("networkStatsCtor ptr: %p\n", networkStatsCtor);
    jobject stats = jenv->NewObject(networkStatsClass, networkStatsCtor);
    checkJavaError(jenv, !stats, "Can't create NetworkStats java object");
    return stats;
}

static jobject newStatsMap(JNIEnv *jenv)
{
    eac_dprintf("JNIEnv ptr: %p\n", jenv);
    eac_dprintf("hashMapClass ptr: %p\n", hashMapClass);
    eac_dprintf("hashMapCtor ptr: %p\n", hashMapCtor);
    jobject theMap = jenv->NewObject(hashMapClass, hashMapCtor);
    checkJavaError(jenv, !theMap, "Can't create HashMap java object");
    return theMap;
}

static void addMapping(JNIEnv *jenv, jobject mapObj, 
                       int ip_addr, jobject stats)
{
    jobject ipAddrInteger = jenv->NewObject(integerClass, integerCtor, ip_addr);
    checkJavaError(jenv, !ipAddrInteger, "Can't create Java Integer");

    jobject ret = jenv->CallObjectMethod(mapObj, hashMapPut, ipAddrInteger, stats);
    checkJavaError(jenv, false, "HashMap.put threw an exception");
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
Java_edu_umich_eac_NetworkStats_getBestNetworkStats(JNIEnv *jenv, jclass cls)
{
    initCachedJNIData(jenv);

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

extern "C"
JNIEXPORT jobject JNICALL 
Java_edu_umich_eac_NetworkStats_getAllNetworkStatsByIp(JNIEnv *jenv, jclass cls)
{
    initCachedJNIData(jenv);

    // look at all available networks and bundle them all as a HashMap of
    //  (IP, stats) pairs.

    jobject theMap = NULL;
    try {
        theMap = newStatsMap(jenv);
    
        vector<struct net_interface> ifaces;
        bool result = get_local_interfaces(ifaces);
        if (result) {
            for (size_t i = 0; i < ifaces.size(); ++i) {
                struct net_interface& iface = ifaces[i];

                uint32_t ip_addr = iface.ip_addr.s_addr;
                jobject stats = newNetworkStats(jenv);
                fillStats(jenv, stats,
                          iface.bandwidth_down, 
                          iface.bandwidth_up,
                          iface.RTT);

                addMapping(jenv, theMap, ip_addr, stats);
            }
        }
    } catch (runtime_error& e) {
        return NULL;
    }

    return theMap;
}
