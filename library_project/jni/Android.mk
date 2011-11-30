LOCAL_PATH := $(call my-dir)

CPP_WRAPPER_RELPATH := ../../cpp_wrapper
CPP_WRAPPER_ROOT := $(LOCAL_PATH)/$(CPP_WRAPPER_RELPATH)
INTNW_RELPATH := ../../../libcmm
INTNW_ROOT := $(LOCAL_PATH)/$(INTNW_RELPATH)

include $(CLEAR_VARS)
LOCAL_MODULE := libeac_support
LOCAL_C_INCLUDES := $(CPP_WRAPPER_ROOT)/include $(INTNW_ROOT)

LOCAL_SRC_FILES := $(addprefix $(CPP_WRAPPER_RELPATH)/src/, \
	jclasses.cpp \
	eac_utility.cpp \
	JNICacheFetcher_wrap.cpp \
	intnw_ipc.cpp\
	EnergyAdaptiveCache.cpp \
	Future.cpp) \
	$(addprefix $(INTNW_RELPATH)/, libcmm_external_ipc.cpp debug.cpp)
LOCAL_CFLAGS := -DANDROID -DNDK_BUILD -g -ggdb -Wall -Werror
LOCAL_LDLIBS := -llog

include $(BUILD_SHARED_LIBRARY)
