#include <stdio.h>
#include <stdlib.h>
#include <fcntl.h>
#include <unistd.h>
#include <string.h>
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

  // message construction

  fjage_msg_t msg = fjage_msg_create("org.arl.unet.phy.TxFrameReq", FJAGE_REQUEST);
  fjage_aid_t sender = fjage_aid_create("myagent");
  fjage_aid_t recipient = fjage_aid_create("phy");
  fjage_msg_set_sender(msg, sender);
  fjage_msg_set_recipient(msg, recipient);
  fjage_msg_add_int(msg, "type", 1);
  fjage_msg_add_int(msg, "from", 42);
  fjage_msg_add_int(msg, "to", 27);
  fjage_msg_add_float(msg, "metric", 45.0);
  fjage_msg_add_string(msg, "data", "boo");

  int fd = open("test_fjage.json", O_CREAT|O_WRONLY|O_TRUNC);
  if (fd <= 0) return error("unable to write file");
  fjage_msg_write_json(msg, fd);
  off_t fsize = lseek(fd, 0, SEEK_END);
  close(fd);

  fjage_msg_destroy(msg);
  fjage_aid_destroy(sender);
  fjage_aid_destroy(recipient);

  // message parsing

  char* buf = calloc(1, fsize+1);
  if (buf == NULL) return error("out of memeory");
  fd = open("test_fjage.json", O_RDONLY);
  if (fd <= 0) return error("unable to read file");
  read(fd, buf, fsize);
  close(fd);
  unlink("test_fjage.json");
  msg = fjage_msg_from_json(buf);
  free(buf);

  test_assert("id",         !strcmp(fjage_msg_get_id(msg), "nxZqCIY+DNYVMizHXRYUvRtFY1rWIHZSIziB"));
  test_assert("clazz",      !strcmp(fjage_msg_get_clazz(msg), "org.arl.unet.phy.TxFrameReq"));
  test_assert("sender",     !strcmp(fjage_msg_get_sender(msg), "myagent"));
  test_assert("recipient",  !strcmp(fjage_msg_get_recipient(msg), "phy"));
  test_assert("inReplyTo",  fjage_msg_get_in_reply_to(msg) == NULL);
  test_assert("type",       fjage_msg_get_int(msg, "type", -1) == 1);
  test_assert("from",       fjage_msg_get_int(msg, "from", -1) == 42);
  test_assert("to",         fjage_msg_get_int(msg, "to", -1) == 27);
  test_assert("metric",     fjage_msg_get_float(msg, "metric", -1) == 45.0);
  test_assert("data",       !strcmp(fjage_msg_get_string(msg, "data"), "boo"));

  fjage_msg_destroy(msg);

  fjage_gw_t gw = fjage_tcp_open("localhost", 5081);
  if (gw == NULL) return error("unable to connect to host");
  fjage_agent_for_service(gw, "org.arl.fjage.shell.Services.SHELL");
  fjage_close(gw);

  test_summary();
  return failed;
}
