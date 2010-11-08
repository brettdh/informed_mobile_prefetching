LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := eac_native
LOCAL_SRC_FILES := EnergyAdaptiveCache.cpp JNICacheFetcher_wrap.cpp

include $(BUILD_STATIC_LIBRARY)
