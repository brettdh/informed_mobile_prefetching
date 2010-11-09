LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := libeac_native
LOCAL_C_INCLUDES := \
	dalvik/libnativehelper/include/nativehelper/ \
	$(LOCAL_PATH)/include
LOCAL_SRC_FILES := \
	src/EnergyAdaptiveCache.cpp \
	src/JNICacheFetcher_wrap.cpp \
	src/Future.cpp \
	src/utility.cpp

include $(BUILD_STATIC_LIBRARY)
