#include <stdexcept>
#include <stdlib.h>
#include <jni.h>
#include "EnergyAdaptiveCache.h"
#include "Future.h"
#include "JNICacheFetcher.h"
#include "eac_utility.h"
#include "jclasses.h"

#include <exception>
#include <exception_defines.h>
#include <cxxabi.h>
#include <typeinfo>

using namespace abi;

static void my_abort()
{
    sigset_t mask;
    sigfillset(&mask);
    sigdelset(&mask, SIGSEGV);
    
    (void)sigprocmask(SIG_SETMASK, &mask, (sigset_t *)NULL);
    
    *((char *)(0xdeadbaad)) = 39;
    _exit(1);
}

// A replacement for the standard terminate_handler which prints
// more information about the terminating exception (if any) on
// stderr. (copied from libsupc++: vterminate.cc)
static void term_handler()
{
    static bool terminating;
    if (terminating)
        {
            eac_dprintf("terminate called recursively\n");
            my_abort();
        }
    terminating = true;
    
    // Make sure there was an exception; terminate is also called for an
    // attempt to rethrow when there is no suitable exception.
    std::type_info *t = __cxa_current_exception_type();
    if (t)
        {
            // Note that "name" is the mangled name.
            char const *name = t->name();
            {
                int status = -1;
                char *dem = 0;
                
                dem = __cxa_demangle(name, 0, 0, &status);
                
                eac_dprintf("terminate called after throwing an instance of '%s'\n",
                            (status == 0) ? dem : name);
                
                if (status == 0)
                    free(dem);
            }
            
            // If the exception is derived from std::exception, we can
            // give more information.
            try { __throw_exception_again; }
            catch (std::exception &exc)
                {
                    char const *w = exc.what();
                    eac_dprintf("  what(): %s\n", w);
                }
            catch (...) { }
        }
    else
        eac_dprintf("terminate called without an active exception\n");
    
    my_abort();
}

static void lib_init(void) __attribute__((constructor));
static void lib_init(void)
{
    std::set_terminate(term_handler);
}


static const char *enumNames[NUM_STRATEGIES] = {
    "AGGRESSIVE",
    "CONSERVATIVE",
    "ADAPTIVE"
};

enum PrefetchStrategyType
getEnumValue(const char *typeStr)
{
    for (int i = 0; i < NUM_STRATEGIES; ++i) {
        if (!strcmp(enumNames[i], typeStr)) {
            return PrefetchStrategyType(i);
        }
    }
    return NUM_STRATEGIES;
}

static jobject
getEnumValue(JNIEnv *jenv, enum PrefetchStrategyType type)
{
    jclass clazz = JClasses::PrefetchStrategyType;
    if (!clazz || JAVA_EXCEPTION_OCCURRED(jenv)) {
        fatal_error("Can't find PrefetchStrategyType class");
    }
    jfieldID fid = jenv->GetStaticFieldID(
        clazz, enumNames[type], "Ledu/umich/eac/PrefetchStrategyType;"
    );
                                          
    if (!fid || JAVA_EXCEPTION_OCCURRED(jenv)) {
        fatal_error("Can't find PrefetchStrategyType constant");
    }
    jobject jobj = jenv->GetStaticObjectField(clazz, fid);
    if (JAVA_EXCEPTION_OCCURRED(jenv)) {
        fatal_error("Can't get PrefetchStrategyType constant");
    }
    return jobj;
}

static const char *prefetchMethodSig = 
    "(Ledu/umich/eac/CacheFetcher;)Ljava/util/concurrent/Future;";
static const char *updateGoalTimeMethodSig = "(I)V";

EnergyAdaptiveCache::EnergyAdaptiveCache(JNIEnv *jenv, jobject context,
                                         enum PrefetchStrategyType type)
{
    struct timeval dummy;
    gettimeofday(&dummy, NULL);
    init(jenv, context, type, dummy, 0, 0);
}

EnergyAdaptiveCache::EnergyAdaptiveCache(JNIEnv *jenv, jobject context,
                                         enum PrefetchStrategyType type,
                                         struct timeval goalTime,
                                         int energyBudget, int dataBudget)
{
    init(jenv, context, type, goalTime, energyBudget, dataBudget);
}

void 
EnergyAdaptiveCache::init(JNIEnv *jenv, jobject context,
                          enum PrefetchStrategyType type,
                          struct timeval goalTime, int energyBudget, int dataBudget)
{
    vm = NULL;
    cacheClazz = NULL;
    realCacheObj = NULL;
    prefetchMID = NULL;
    updateGoalTimeMID = NULL;
    
    jint rc = jenv->GetJavaVM(&vm);
    if (rc != 0) {
        fatal_error("Can't get the Java VM");
    }
    
    JClasses::init(jenv);
    
    cacheClazz = JClasses::EnergyAdaptiveCache;
    prefetchMID = jenv->GetMethodID(cacheClazz, "prefetch", prefetchMethodSig);
    if (!prefetchMID || JAVA_EXCEPTION_OCCURRED(jenv)) {
        fatal_error("Can't find prefetch method");
    }
    prefetchNowMID = jenv->GetMethodID(cacheClazz, "prefetchNow", 
                                       prefetchMethodSig);
    if (!prefetchNowMID || JAVA_EXCEPTION_OCCURRED(jenv)) {
        fatal_error("Can't find prefetchNow method");
    }
    fetchMID = jenv->GetMethodID(cacheClazz, "fetch", 
                                 prefetchMethodSig);
    if (!fetchMID || JAVA_EXCEPTION_OCCURRED(jenv)) {
        fatal_error("Can't find fetch method");
    }
    updateGoalTimeMID = jenv->GetMethodID(cacheClazz, "updateGoalTime", 
                                          updateGoalTimeMethodSig);
    if (!updateGoalTimeMID || JAVA_EXCEPTION_OCCURRED(jenv)) {
        fatal_error("Can't find fetch method");
    }
    jmethodID ctor = jenv->GetMethodID(
        cacheClazz, "<init>", 
        "(Landroid/content/Context;Ledu/umich/eac/PrefetchStrategyType;JII)V"
    );
    if (!ctor || JAVA_EXCEPTION_OCCURRED(jenv)) {
        fatal_error("Can't find EAC constructor");
    }
    jobject prefetchType = getEnumValue(jenv, type);
    jobject local = jenv->NewObject(cacheClazz, ctor, context, prefetchType,
                                    goalTime.tv_sec * 1000 + goalTime.tv_usec / 1000,
                                    energyBudget, dataBudget);
    if (!local || JAVA_EXCEPTION_OCCURRED(jenv) ||
        !(realCacheObj = jenv->NewGlobalRef(local))) {
        fatal_error("Can't create EnergyAdaptiveCache "
                                 "java object");
    }
}

EnergyAdaptiveCache::~EnergyAdaptiveCache()
{
    eac_dprintf("Thread %x attached to JVM in ~EAC()\n", 
                (unsigned int) pthread_self());
    JNIEnv *jenv = NULL;
    jint rc = vm->AttachCurrentThread(&jenv, NULL);
    if (rc == 0) {
        jenv->DeleteGlobalRef(realCacheObj);
        //vm->DetachCurrentThread();
    }
}

void
EnergyAdaptiveCache::updateGoalTime(int startDelayedMillis)
{
    JNIEnv *jenv = getJNIEnv(vm);
    jenv->CallVoidMethod(realCacheObj, updateGoalTimeMID, startDelayedMillis);
    if (JAVA_EXCEPTION_OCCURRED(jenv)) {
        fatal_error("Can't update goal time");
    }
}

Future *
EnergyAdaptiveCache::prefetch(JNICacheFetcherPtr fetcher)
{
    return prefetch(fetcher, false, false);
}

Future *
EnergyAdaptiveCache::prefetchNow(JNICacheFetcherPtr fetcher)
{
    return prefetch(fetcher, true, false);
}

Future *
EnergyAdaptiveCache::fetch(JNICacheFetcherPtr fetcher)
{
    return prefetch(fetcher, true, true);
}

Future *
EnergyAdaptiveCache::prefetch(JNICacheFetcherPtr fetcher, 
                              bool now, bool demand)
{
    JNIEnv *jenv = getJNIEnv(vm);
    
    if (!fetcher) {
        eac_dprintf("Error: prefetch called with NULL fetcher; "
                    "returning NULL\n");
        return NULL;
    }
    
    jclass clazz = JClasses::JNICacheFetcher;
    jmethodID ctor = jenv->GetMethodID(clazz, "<init>", "(IZ)V");
    if (!ctor || JAVA_EXCEPTION_OCCURRED(jenv)) {
        fatal_error("Can't find JNICacheFetcher ctor");
    }
    
    // create new shared_ptr, owned by the java object.
    //  when that object is garbage collected, it will delete
    //  the shared_ptr (not necessarily the raw ptr).
    JNICacheFetcherPtr *newSharedPtr = new JNICacheFetcherPtr(fetcher);
    jobject fetcher_jobj = jenv->NewObject(
        clazz, ctor, (int)newSharedPtr, true
    );
    if (!fetcher_jobj || JAVA_EXCEPTION_OCCURRED(jenv)) {
        // the object wasn't constructed, so it won't be garbage collected,
        //  and this smart ptr would be leaked (also preventing the real data
        //  from ever being freed).  So clean it up.
        delete newSharedPtr;
        fatal_error("Can't create JNICacheFetcher "
                                 "java object");
    }
    
    // create a java Future object by submitting this java Fetcher object
    //  to the java EnergyAdaptiveCache
    jmethodID method = NULL;
    if (demand) {
        method = fetchMID;
    } else {
        if (now) {
            method = prefetchNowMID;
        } else {
            method = prefetchMID;
        }
    }
    jobject local = jenv->CallObjectMethod(realCacheObj, method, fetcher_jobj);
    jobject future_jobj;
    if (!local || JAVA_EXCEPTION_OCCURRED(jenv) ||
        !(future_jobj = jenv->NewGlobalRef(local))) {
        fatal_error("Can't create Future java object");
    }
    
    // Wrap the returned Future in my proxy class
    Future *future = new Future(vm, future_jobj);
    return future;
}

pthread_key_t EnergyAdaptiveCache::detacher::key;

void 
EnergyAdaptiveCache::detacher::addSelf(JavaVM *vm)
{
    if (pthread_getspecific(key) == NULL) {
        eac_dprintf("Thread %x has stored VM pointer for detaching later\n",
                    (unsigned int) pthread_self());
        pthread_setspecific(key, vm);
    }
}

EnergyAdaptiveCache::detacher::~detacher()
{
    JavaVM *vm = (JavaVM *) pthread_getspecific(key);
    if (vm) {
        eac_dprintf("Detaching thread %x from VM\n", 
                    (unsigned int) pthread_self());
        vm->DetachCurrentThread();
        pthread_setspecific(key, NULL);
    }
}

EnergyAdaptiveCache::detacher::initer 
    EnergyAdaptiveCache::detacher::my_initer;

EnergyAdaptiveCache::detacher::initer::initer()
{
    pthread_key_create(&EnergyAdaptiveCache::detacher::key, NULL);
}

EnergyAdaptiveCache::detacher::initer::~initer()
{
    pthread_key_delete(EnergyAdaptiveCache::detacher::key);
}
