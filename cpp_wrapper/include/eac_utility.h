#ifndef EAC_CPP_WRAPPER_UTILITY_H_INCL
#define EAC_CPP_WRAPPER_UTILITY_H_INCL

#include <jni.h>

JNIEnv * getJNIEnv(JavaVM *jvm);

#ifdef __cplusplus
#define CDECL extern "C"
#else
#define CDECL 
#endif

CDECL void eac_dprintf(const char *format, ...)
  __attribute__((format(printf, 1, 2)));
CDECL void eac_dprintf_plain(const char *format, ...)
  __attribute__((format(printf, 1, 2)));

bool javaExceptionOccurred(JNIEnv *jenv, const char *file, int line);

#define JAVA_EXCEPTION_OCCURRED(jenv) javaExceptionOccurred(jenv, __FILE__, __LINE__)

#endif
