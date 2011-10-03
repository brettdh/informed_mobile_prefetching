LOCAL_PATH := $(call my-dir)

MY_ANDROID_SRC_ROOT := $(HOME)/src/android-source
LIBCMM_ROOT := $(MY_ANDROID_SRC_ROOT)/external/bdh_apps/libcmm

include $(CLEAR_VARS)

LOCAL_MODULE := eac_native_static
LOCAL_SRC_FILES := ../../../cpp_wrapper/obj/local/armeabi/libeac_native_static.a
include $(PREBUILT_STATIC_LIBRARY)

include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional
LOCAL_MODULE := eac_native_tests
LOCAL_C_INCLUDES := $(LOCAL_PATH)/../../../cpp_wrapper/include $(LIBCMM_ROOT)
LOCAL_SRC_FILES := \
	native_tests.cpp native_promotion_test.cpp native_cancel_test.cpp\
	jniunit.cpp utility.cpp
LOCAL_STATIC_LIBRARIES := libeac_native_static
LOCAL_CFLAGS := -g -ggdb -O0 -Wall -Werror

LOCAL_LDLIBS := -llog
include $(BUILD_SHARED_LIBRARY)
