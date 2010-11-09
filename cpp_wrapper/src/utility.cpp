#include <jni.h>
#include <stdexcept>
#include "utility.h"

JNIEnv *
getJNIEnv(JavaVM *jvm)
{
    JNIEnv *jenv = NULL;
    jint rc = jvm->AttachCurrentThread(&jenv, NULL);
    if (rc == 0) {
        return jenv;
    }
    throw std::runtime_error("Couldn't get JNIEnv!");
}
