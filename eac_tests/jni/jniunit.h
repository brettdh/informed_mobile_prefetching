#ifndef JNIUNIT_H_INCL
#define JNIUNIT_H_INCL

#include <jni.h>
#include <stdexcept>
#include <eac_utility.h>

namespace jniunit {
    void callNativeFunction(JNIEnv *jenv, 
                            const char *name, const char *sig, ...) {
        ;
        jclass cls = jenv->FindClass("junit/framework/Assert");
        if (!cls || JAVA_EXCEPTION_OCCURRED(jenv)) {
            /* class not found */
            throw std::runtime_error("Assert class not found!");
        }
        jmethodID mid = jenv->GetStaticMethodID(cls, name, sig);
        if (mid == NULL || JAVA_EXCEPTION_OCCURRED(jenv)) {
            /* method not found */
            throw std::runtime_error("Method not found!");
        }

        va_list ap;
        va_start(ap, sig);
        jenv->CallStaticVoidMethodV(cls, mid, ap);
        va_end(ap);
        if (JAVA_EXCEPTION_OCCURRED(jenv)) {
            throw std::runtime_error("Java call threw an exception");
        }
    }
    
    void assertTrue(JNIEnv *jenv, 
                    const char *msg, bool condition) {
        ;
        jstring msgStr = jenv->NewStringUTF(msg);
        callNativeFunction(jenv, "assertTrue", "(Ljava/lang/String;Z)V",
                           msgStr, condition);
        
    }
    
    void assertFalse(JNIEnv *jenv, 
                     const char *msg, bool condition) {
        jstring msgStr = jenv->NewStringUTF(msg);
        callNativeFunction(jenv, "assertFalse", "(Ljava/lang/String;Z)V",
                           msgStr, condition);
    }
    
    void fail(JNIEnv *jenv, const char *msg) {
        jstring msgStr = jenv->NewStringUTF(msg);
        callNativeFunction(jenv, "fail", "()V", msgStr);
    }
}

#endif
