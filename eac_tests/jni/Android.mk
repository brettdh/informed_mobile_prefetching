LOCAL_PATH := $(call my-dir)

MY_ANDROID_SRC_ROOT := $(HOME)/src/android-source
#MY_OUT := $(MY_ANDROID_SRC_ROOT)/out/target/product/generic
LIBCMM_ROOT := $(MY_ANDROID_SRC_ROOT)/external/bdh_apps/libcmm

include $(CLEAR_VARS)

LOCAL_MODULE := eac_native
LOCAL_SRC_FILES := ../../cpp_wrapper/libs/armeabi/libeac_native.so
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional
LOCAL_MODULE := eac_native_tests
LOCAL_C_INCLUDES := $(LOCAL_PATH)/../../cpp_wrapper/include $(LIBCMM_ROOT)
LOCAL_SRC_FILES := \
	native_tests.cpp native_promotion_test.cpp native_cancel_test.cpp\
	jniunit.cpp utility.cpp
LOCAL_SHARED_LIBRARIES := libeac_native
#LOCAL_LDFLAGS := -L$(MY_OUT)/obj/lib -Wl,-rpath-link=$(MY_OUT)/obj/lib\
#		 -leac_native

include $(BUILD_SHARED_LIBRARY)
