LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional
LOCAL_MODULE := libeac_native
LOCAL_C_INCLUDES := \
	$(LOCAL_PATH)/../include
LOCAL_CFLAGS := -DANDROID
LOCAL_SRC_FILES := $(addprefix ../, \
	src/EnergyAdaptiveCache.cpp \
	src/JNICacheFetcher_wrap.cpp \
	src/Future.cpp \
	src/eac_utility.cpp \
	src/jclasses.cpp)
LOCAL_PRELINK_MODULE := false
#LOCAL_SHARED_LIBRARIES := liblog
LOCAL_LDLIBS := -llog
include $(BUILD_SHARED_LIBRARY)
