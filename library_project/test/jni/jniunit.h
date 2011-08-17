#ifndef JNIUNIT_H_INCL
#define JNIUNIT_H_INCL

#include <jni.h>
#include <stdexcept>

namespace jniunit {
    void callNativeFunction(JNIEnv *jenv, const char *name, const char *sig, 
                            ...);
    void assertTrue(JNIEnv *jenv, const char *msg, bool condition);
    
    void assertFalse(JNIEnv *jenv, const char *msg, bool condition);
    
    void fail(JNIEnv *jenv, const char *msg);

    class AssertionException : public std::runtime_error {
    public:
        AssertionException(const std::string& s) : std::runtime_error(s) {}
    };
}

#endif
