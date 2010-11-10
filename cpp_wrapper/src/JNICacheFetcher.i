%module eac
%{
/* include header in wrapper code */
#include "JNICacheFetcher.h"
%}

%include <boost_shared_ptr.i>
%shared_ptr(JNICacheFetcher)

/* parse header to generate wrapper */
%include "JNICacheFetcher.h"
