LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := libeac_native
LOCAL_C_INCLUDES := \
	dalvik/libnativehelper/include/nativehelper/ \
	$(LOCAL_PATH)/include
LOCAL_CFLAGS := -DANDROID
LOCAL_SRC_FILES := \
	src/EnergyAdaptiveCache.cpp \
	src/JNICacheFetcher_wrap.cpp \
	src/Future.cpp \
	src/eac_utility.cpp
LOCAL_PRELINK_MODULE := false
LOCAL_SHARED_LIBRARIES := liblog
include $(BUILD_SHARED_LIBRARY)
