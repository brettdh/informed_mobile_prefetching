LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

INTNW_ROOT := ../../../libcmm

LOCAL_MODULE := libeac_native_static
LOCAL_C_INCLUDES := \
	$(LOCAL_PATH)/../include \
	$(LOCAL_PATH)/$(INTNW_ROOT)
LOCAL_CFLAGS := -DANDROID -DNDK_BUILD -g -ggdb -Wall -Werror
LOCAL_SRC_FILES := $(addprefix ../, \
	src/EnergyAdaptiveCache.cpp \
	src/JNICacheFetcher_wrap.cpp \
	src/Future.cpp \
	src/eac_utility.cpp \
	src/jclasses.cpp \
	src/intnw_ipc.cpp) \
	$(addprefix $(INTNW_ROOT)/, libcmm_external_ipc.cpp debug.cpp)

LOCAL_LDLIBS := -llog
include $(BUILD_STATIC_LIBRARY)

# This target is necessary in order to build the static library at all.
include $(CLEAR_VARS)
LOCAL_MODULE := libeac_native
LOCAL_STATIC_LIBRARIES := eac_native_static

include $(BUILD_SHARED_LIBRARY)
