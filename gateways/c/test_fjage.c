/******************************************************************************

Copyright (c) 2018, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

////////////////////////////////////////////////////////////////////
//
// To run tests, first run the fjage container and then the tests:
//
// In one terminal window:
// $ gradle
// $ ./fjage
//
// In another terminal window:
// $ cd fjage/main/c
// $ make test
//
////////////////////////////////////////////////////////////////////

#include <stdio.h>
#include <stdlib.h>
#include <fcntl.h>
#include <string.h>
#include <math.h>

#if defined(_LINUX) || defined (_DARWIN)
#include <unistd.h>
#include <pthread.h>
#include <sys/time.h>
#endif

#ifdef _WIN32
#include <io.h>
#endif

#include "fjage.h"

int main(int argc, char* argv[]) {
  printf("\n");
  fjage_gw_t gw;
  gw = fjage_tcp_open("localhost", 5081);
  return 0;
}
