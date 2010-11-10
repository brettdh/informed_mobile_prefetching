#include "utility.h"
#include <time.h>

void thread_sleep(int millis)
{
    struct timespec ts;
    ts.tv_sec = (millis / 1000);
    ts.tv_nsec = (millis - (ts.tv_sec * 1000)) * 1000000;
    nanosleep(&ts, NULL);
}
