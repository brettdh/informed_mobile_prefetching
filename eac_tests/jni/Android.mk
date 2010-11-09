LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LIBEAC_NATIVE := $(LOCAL_PATH)/../../cpp_wrapper/lib/libeac_native.a

LOCAL_MODULE := eac_native_tests
LOCAL_C_INCLUDES := $(LOCAL_PATH)/../../cpp_wrapper/include
LOCAL_SRC_FILES := native_tests.cpp
LOCAL_LDFLAGS := $(LIBEAC_NATIVE)

include $(BUILD_SHARED_LIBRARY)
