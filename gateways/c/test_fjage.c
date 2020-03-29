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

#ifdef _WIN32
#include <Windows.h>
#else
#include <unistd.h>
#include <pthread.h>
#include <sys/time.h>
#endif

#include "fjage.h"

static int passed = 0;
static int failed = 0;

static void test_assert(const char* name, int pass) {
  if (pass) {
    printf("%s: PASSED\n", name);
    passed++;
  } else {
    printf("%s: FAILED\n", name);
    failed++;
  }
}

static void test_summary(void) {
  printf("\n*** %d test(s) PASSED, %d test(s) FAILED ***\n\n", passed, failed);
}

static int error(const char* msg) {
  printf("\n*** ERROR: %s\n\n", msg);
  return -1;
}

static double current_time(void) {
#ifdef _WIN32
  return GetTickCount()/1000.0;
#else
  struct timeval tv;
  gettimeofday(&tv, NULL);
  return tv.tv_sec + tv.tv_usec/1e6;
#endif
}

static void* intr_thread(void* p) {
  fjage_gw_t gw = (fjage_gw_t)p;
  usleep(500000);
  fjage_interrupt(gw);
  return NULL;
}

int main(int argc, char* argv[]) {
  printf("\n");
  fjage_gw_t gw;
  if (argc > 1) {
#ifdef _WIN32
    return error("Connection over serial port not supported on Windows");
#else
    gw = fjage_rs232_open(argv[1], 9600, "N81");
    if (gw == NULL) return error("Could not connect to fjage master container on serial port");
#endif
  } else {
    gw = fjage_tcp_open("localhost", 5081);
    if (gw == NULL) return error("Could not connect to fjage master container on localhost:5081");
  }
  fjage_aid_t myaid = fjage_get_agent_id(gw);
  if (myaid != NULL) printf("get_agent_id> %s\n", myaid);
  test_assert("get_agent_id", myaid != NULL);
  fjage_aid_t topic = fjage_aid_topic("mytopic");
  test_assert("aid_topic", topic != NULL && !strcmp(topic, "#mytopic"));
  test_assert("is_subscribed (-)", !fjage_is_subscribed(gw, topic));
  test_assert("subscribe", fjage_subscribe(gw, topic) == 0);
  test_assert("is_subscribed (+)", fjage_is_subscribed(gw, topic));
  test_assert("unsubscribe", fjage_unsubscribe(gw, topic) == 0 && !fjage_is_subscribed(gw, topic));
  test_assert("agent_for_service (-)", fjage_agent_for_service(gw, "unknown") == NULL);
  fjage_aid_t aid = fjage_agent_for_service(gw, "org.arl.fjage.shell.Services.SHELL");
  test_assert("agent_for_service", aid != NULL && !strcmp(aid, "shell"));
  fjage_aid_destroy(aid);
  aid = NULL;
  int n = fjage_agents_for_service(gw, "org.arl.fjage.shell.Services.SHELL", NULL, 0);
  int m = fjage_agents_for_service(gw, "org.arl.fjage.shell.Services.SHELL", &aid, 1);
  test_assert("agents_for_service", n == 1 && m == 1 && aid != NULL && !strcmp(aid, "shell"));
  fjage_aid_destroy(aid);
  aid = fjage_aid_create("shell");
  test_assert("aid_create", aid != NULL && !strcmp(aid, "shell"));
  fjage_msg_t msg = fjage_receive(gw, NULL, NULL, 1000);
  test_assert("receive (-)", msg == NULL);
  msg = fjage_msg_create("org.arl.fjage.test.TestMessage", FJAGE_INFORM);
  test_assert("msg_create", msg != NULL);
  const char* mid = fjage_msg_get_id(msg);
  if (mid != NULL) printf("msg_get_id> %s\n", mid);
  test_assert("msg_get_id", mid != NULL);
  fjage_msg_set_recipient(msg, myaid);
  fjage_msg_add_string(msg, "mystring", "myvalue");
  fjage_msg_add_int(msg, "myint", 7);
  fjage_msg_add_long(msg, "mylong", 77);
  fjage_msg_add_float(msg, "myfloat", 2.7);
  fjage_msg_add_bool(msg, "mytbool", true);
  fjage_msg_add_bool(msg, "myfbool", false);
  unsigned char data[7] = { 7,6,5,4,3,2,1 };
  fjage_msg_add_byte_array(msg, "mydata", data, 7);
  float signal[7] = { 3,1,4,1,5,9,2 };
  fjage_msg_add_float_array(msg, "mysignal", signal, 7);
  test_assert("send", fjage_send(gw, msg) == 0);
  msg = fjage_receive(gw, NULL, NULL, 1000);
  test_assert("receive (+)", msg != NULL);
  const char* clazz = fjage_msg_get_clazz(msg);
  test_assert("msg_get_clazz", clazz != NULL && !strcmp(clazz, "org.arl.fjage.test.TestMessage"));
  test_assert("msg_get_performative", fjage_msg_get_performative(msg) == FJAGE_INFORM);
  const char* s = fjage_msg_get_string(msg, "mystring");
  test_assert("msg_get_string", s != NULL && !strcmp(s, "myvalue"));
  test_assert("msg_get_int", fjage_msg_get_int(msg, "myint", -1) == 7);
  test_assert("msg_get_long", fjage_msg_get_long(msg, "mylong", -1) == 77);
  test_assert("msg_get_float", fabs(fjage_msg_get_float(msg, "myfloat", 0)-2.7) < 0.01);
  test_assert("msg_get_bool", fjage_msg_get_bool(msg, "mytbool", false) && !fjage_msg_get_bool(msg, "myfbool", true));
  test_assert("msg_get_byte_array (len)", fjage_msg_get_byte_array(msg, "mydata", NULL, 0) == 7);
  test_assert("msg_get_float_array (len)", fjage_msg_get_float_array(msg, "mysignal", NULL, 0) == 7);
  memset(data, 0, sizeof(data));
  memset(signal, 0, sizeof(signal));
  fjage_msg_get_byte_array(msg, "mydata", data, 7);
  fjage_msg_get_float_array(msg, "mysignal", signal, 7);
  test_assert("msg_get_byte_array", data[0] == 7 && data[1] == 6 && data[2] == 5 && data[3] == 4 && data[4] == 3 && data[5] == 2 && data[6] == 1);
  test_assert("msg_get_float_array", signal[0] == 3 && signal[1] == 1 && signal[2] == 4 && signal[3] == 1 && signal[4] == 5 && signal[5] == 9 && signal[6] == 2);
  fjage_msg_destroy(msg);
  double t0 = current_time();
  msg = fjage_receive(gw, NULL, NULL, 1000);
  test_assert("receive (timeout 1)", msg == NULL && current_time()-t0 > 0.9);
#ifdef _WIN32
  printf("receive (interrupt 2): SKIPPED\n");
#else
  pthread_t tid;
  pthread_create(&tid, NULL, intr_thread, gw);
  t0 = current_time();
  msg = fjage_receive(gw, NULL, NULL, 1000);
  test_assert("receive (interrupt 2)", msg == NULL && current_time()-t0 < 0.9);
#endif
  t0 = current_time();
  msg = fjage_receive(gw, NULL, NULL, 1000);
  test_assert("receive (timeout 2)", msg == NULL && current_time()-t0 > 0.9);
  msg = fjage_msg_create("org.arl.fjage.test.TestMessage", FJAGE_INFORM);
  fjage_msg_set_recipient(msg, myaid);
  fjage_send(gw, msg);
  msg = fjage_receive(gw, "badclass", NULL, 1000);
  test_assert("receive (-clazz)", msg == NULL);
  msg = fjage_receive(gw, "org.arl.fjage.test.TestMessage", NULL, 1000);
  test_assert("receive (+clazz)", msg != NULL);
  fjage_msg_destroy(msg);
  msg = fjage_msg_create("org.arl.fjage.test.TestMessage", FJAGE_INFORM);
  fjage_msg_set_recipient(msg, topic);
  fjage_send(gw, msg);
  msg = fjage_receive(gw, NULL, NULL, 1000);
  test_assert("receive (-topic)", msg == NULL);
  fjage_subscribe(gw, topic);
  msg = fjage_msg_create("org.arl.fjage.test.TestMessage", FJAGE_INFORM);
  fjage_msg_set_recipient(msg, topic);
  fjage_send(gw, msg);
  msg = fjage_receive(gw, NULL, NULL, 1000);
  test_assert("receive (+topic)", msg != NULL);
  fjage_msg_destroy(msg);
  msg = fjage_msg_create("org.arl.fjage.shell.ShellExecReq", FJAGE_REQUEST);
  fjage_msg_set_recipient(msg, aid);
  fjage_msg_add_string(msg, "cmd", "ps");
  msg = fjage_request(gw, msg, 1000);
  test_assert("request", msg != NULL && fjage_msg_get_performative(msg) == FJAGE_AGREE);
  fjage_msg_destroy(msg);
  fjage_aid_destroy(aid);
  fjage_aid_destroy(topic);
  // FIXME: param tests are not perfect, since we don't have default agents with many params, but better than nothing...
  aid = fjage_agent_for_service(gw, "org.arl.fjage.shell.Services.SHELL");
  const char* lang = fjage_param_get_string(gw, aid, "org.arl.fjage.shell.ShellParam.language", -1);
  test_assert("get param (+string)", lang != NULL && !strcmp(lang, "Groovy"));
  test_assert("get param (+int)", fjage_param_get_int(gw, aid, "BLOCKING", -1, 0) == -1);
  test_assert("get param (+long)", fjage_param_get_long(gw, aid, "BLOCKING", -1, 0) == -1);
  test_assert("get param (+float)", fjage_param_get_float(gw, aid, "BLOCKING", -1, 0) == -1.0);
  test_assert("get param (-string)", fjage_param_get_string(gw, aid, "dummy", -1) == NULL);
  test_assert("get param (-int)", fjage_param_get_int(gw, aid, "dummy", -1, 0) == 0);
  test_assert("get param (-long)", fjage_param_get_long(gw, aid, "dummy", -1, 0) == 0);
  test_assert("get param (-float)", fjage_param_get_float(gw, aid, "dummy", -1, 0) == 0.0);
  test_assert("set param (+string)", fjage_param_set_string(gw, aid, "dummy", "dummy", -1) == 0);
  test_assert("set param (+int)", fjage_param_set_int(gw, aid, "dummy", 0, -1) == 0);
  test_assert("set param (+long)", fjage_param_set_long(gw, aid, "dummy", 0, -1) == 0);
  test_assert("set param (+float)", fjage_param_set_float(gw, aid, "dummy", 0.0, -1) == 0);
  fjage_aid_destroy(aid);
  aid = fjage_aid_topic("mytopic");
  test_assert("set param (-string)", fjage_param_set_string(gw, aid, "dummy", "dummy", -1) < 0);
  fjage_aid_destroy(aid);
  test_assert("close", fjage_close(gw) == 0);
  test_summary();
  return failed;
}
