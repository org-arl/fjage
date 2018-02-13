#include <stdio.h>
#include <stdlib.h>
#include <fcntl.h>
#include <unistd.h>
#include <string.h>
#include <math.h>
#include "fjage.h"

// function prototypes
fjage_msg_t fjage_msg_from_json(const char* json);
void fjage_msg_write_json(fjage_msg_t msg, int fd);
bool fjage_is_subscribed(fjage_gw_t gw, const fjage_aid_t topic);

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

int main() {
  printf("\n");
  fjage_gw_t gw = fjage_tcp_open("localhost", 5081);
  if (gw == NULL) return error("Could not connect to fjage master container on localhost:5081");
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
  test_assert("close", fjage_close(gw) == 0);
  test_summary();
  return failed;
}
