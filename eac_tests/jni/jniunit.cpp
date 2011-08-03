#include <jni.h>
#include <stdexcept>
#include <sstream>
using std::ostringstream;
#include <eac_utility.h>
#include "jniunit.h"
#include <jclasses.h>

namespace jniunit {
    void callNativeFunction(JNIEnv *jenv, 
                            const char *name, const char *sig, 
                            const char *msg, ...) {
        jclass cls = JClasses::Assert;
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
        va_start(ap, msg);
        jenv->CallStaticVoidMethodV(cls, mid, ap);
        va_end(ap);
        jthrowable err = jenv->ExceptionOccurred();
        if (err) {
            jenv->ExceptionClear();

            jclass err_cls = jenv->GetObjectClass(err);
            if (!err_cls) {
                throw std::runtime_error("Throwable class not found!");
            }
            jmethodID get_message = jenv->GetMethodID(
                err_cls, "getMessage", "()Ljava/lang/String;"
            );
            if (!get_message) {
                throw std::runtime_error("Throwable.getMessage not found!");
            }

            ostringstream oss;
            oss << "Testcase failed: " << msg;
            jstring jstr = (jstring)jenv->CallObjectMethod(err, get_message);
            if (jstr) {
                const char *chars = jenv->GetStringUTFChars(jstr, NULL);;
                if (chars) {
                    oss << chars;
                    jenv->ReleaseStringUTFChars(jstr, chars);
                } else {
                    eac_dprintf("GetStringUTFChars failed!\n");
                }
            } else {
                eac_dprintf("getMessage failed!\n");
            }

            jenv->Throw(err);
            throw AssertionException(oss.str());
        }
    }
    
    void assertTrue(JNIEnv *jenv, const char *msg, bool condition) {
        jstring msgStr = jenv->NewStringUTF(msg);
        try {
            callNativeFunction(jenv, "assertTrue", "(Ljava/lang/String;Z)V",
                               msg, msgStr, condition);
            eac_dprintf("Assertion succeeded: %s\n", msg);
        } catch (AssertionException e) {
            eac_dprintf("Assertion failed : %s\n", msg);
            throw;
        } catch (std::runtime_error& e) {
            eac_dprintf("Fatal error in assertTrue: %s\n", e.what());
            throw;
        }
    }
    
    void assertFalse(JNIEnv *jenv, const char *msg, bool condition) {
        jstring msgStr = jenv->NewStringUTF(msg);
        try {
            callNativeFunction(jenv, "assertFalse", "(Ljava/lang/String;Z)V",
                               msg, msgStr, condition);
            eac_dprintf("Assertion succeeded: %s\n", msg);
        } catch (AssertionException e) {
            eac_dprintf("Assertion failed : %s\n", msg);
            throw;
        } catch (std::runtime_error& e) {
            eac_dprintf("Fatal error in assertFalse: %s\n", e.what());
            throw;
        }
    }
    
    void fail(JNIEnv *jenv, const char *msg) {
        jstring msgStr = jenv->NewStringUTF(msg);
        try {
            callNativeFunction(jenv, "fail", "()V", msg, msgStr);
        } catch (AssertionException e) {
            eac_dprintf("Assertion failed : %s\n", msg);
            throw;
        } catch (std::runtime_error& e) {
            eac_dprintf("Fatal error in fail: %s\n", e.what());
            throw;
        }
    }
}
