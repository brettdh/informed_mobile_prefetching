LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

MY_ANDROID_SRC_ROOT := $(HOME)/src/android-source
MY_OUT := $(MY_ANDROID_SRC_ROOT)/out/target/product/generic

LOCAL_MODULE := eac_native_tests
LOCAL_C_INCLUDES := $(LOCAL_PATH)/../../cpp_wrapper/include
LOCAL_SRC_FILES := native_tests.cpp
LOCAL_LDFLAGS := -L$(MY_OUT)/obj/lib -Wl,-rpath-link=$(MY_OUT)/obj/lib\
		 -leac_native

include $(BUILD_SHARED_LIBRARY)
