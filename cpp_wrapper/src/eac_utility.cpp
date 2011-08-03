#include <jni.h>
#include <stdexcept>
#include "eac_utility.h"
#include <stdio.h>
#include <stdarg.h>
#include <pthread.h>
#include <unistd.h>
#include <sstream>
#include <iomanip>
#include <string>
#include <sys/time.h>
#include "EnergyAdaptiveCache.h"
using std::ostringstream; 
using std::setw; using std::setfill; using std::hex;
using std::string;


JNIEnv *
getJNIEnv(JavaVM *jvm)
{
    eac_dprintf("Thread %x attached to JVM\n", 
                (unsigned int) pthread_self());
    JNIEnv *jenv = NULL;
    jint rc = jvm->AttachCurrentThread(&jenv, NULL);
    if (rc == 0) {
        EnergyAdaptiveCache::detacher::addSelf(jvm);
        return jenv;
    }
    throw std::runtime_error("Couldn't get JNIEnv!");
}

#ifdef ANDROID
#include <android/log.h>
#define VFPRINTF_FUNCTION(file, fmt, ap)             \
    do {                                             \
        __android_log_vprint(ANDROID_LOG_INFO,       \
                             "EnergyAdaptiveCachingNative", \
                             fmt, ap);               \
    } while(0)
#else
#define VFPRINTF_FUNCTION vfprintf
#endif

static void eac_vdprintf(bool plain, const char *fmt, va_list ap)
{
    ostringstream stream;
    if (!plain) {
        struct timeval now;
        gettimeofday(&now, NULL);
        stream << "[" << now.tv_sec << "." << setw(6) << setfill('0') << now.tv_usec << "]";
        stream << "[" << getpid() << "]";
        stream << "[" << hex << pthread_self() << "] ";
    }
    string fmtstr(stream.str());
    fmtstr += fmt;
    
    VFPRINTF_FUNCTION(stderr, fmtstr.c_str(), ap);
}

void eac_dprintf(const char *fmt, ...)
{
    va_list ap;
    va_start(ap, fmt);
    eac_vdprintf(false, fmt, ap);
    va_end(ap);
}

void eac_dprintf_plain(const char *fmt, ...)
{
    va_list ap;
    va_start(ap, fmt);
    eac_vdprintf(true, fmt, ap);
    va_end(ap);
}

bool
javaExceptionOccurred(JNIEnv *jenv, const char *file, int line)
{
    if (jenv->ExceptionCheck()) {
        eac_dprintf("Java exception at %s:%d\n", file, line);
        jenv->ExceptionDescribe();
        return true;
    } else {
        return false;
    }
}

void
fatal_error(const string& str)
{
    eac_dprintf("Fatal error: %s\n");
    throw std::runtime_error(str);
}
