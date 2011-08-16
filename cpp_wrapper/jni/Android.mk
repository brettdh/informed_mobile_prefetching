LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

INTNW_ROOT := ../../../libcmm

LOCAL_MODULE_TAGS := optional
LOCAL_MODULE := libeac_native
LOCAL_C_INCLUDES := \
	$(LOCAL_PATH)/../include \
	$(LOCAL_PATH)/$(INTNW_ROOT)
LOCAL_CFLAGS := -DANDROID -DNDK_BUILD
LOCAL_SRC_FILES := $(addprefix ../, \
	src/EnergyAdaptiveCache.cpp \
	src/JNICacheFetcher_wrap.cpp \
	src/Future.cpp \
	src/eac_utility.cpp \
	src/jclasses.cpp \
	src/intnw_ipc.cpp) \
	$(addprefix $(INTNW_ROOT)/, libcmm_external_ipc.cpp debug.cpp)
LOCAL_PRELINK_MODULE := false

LOCAL_LDLIBS := -llog
include $(BUILD_SHARED_LIBRARY)
