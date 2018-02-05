#include <stdio.h>
#include "fjage.h"

void msg_from_json(fjage_msg_t msg, const char* s);
void msg_write_json(fjage_msg_t msg, int fd);
void msg_dump(fjage_msg_t msg);

int main() {
  printf("\n");
  fjage_msg_t msg = fjage_msg_create("org.arl.unet.phy.TxFrameReq", FJAGE_REQUEST);
  fjage_aid_t sender = fjage_aid_create("jody");
  fjage_aid_t recipient = fjage_aid_create("billy");
  fjage_msg_set_sender(msg, sender);
  fjage_msg_set_recipient(msg, recipient);
  fjage_msg_add_int(msg, "type", 1);
  fjage_msg_add_int(msg, "from", 42);
  fjage_msg_add_int(msg, "to", 27);
  fjage_msg_add_string(msg, "data", "boo");
  //msg_write_json(msg, 1);
  fjage_msg_destroy(msg);
  fjage_aid_destroy(sender);
  fjage_aid_destroy(recipient);
  printf("\n\n");
  fjage_msg_t msg2 = fjage_msg_create(NULL, FJAGE_NONE);
  //msg_from_json(msg2, "{\"clazz\": \"org.arl.unet.phy.TxFrameReq\", data: { \"msgID\": \"nxZqCIY+DNYVMizHXRYUvRtFY1rWIHZSIziB\", \"sender\": \"jody\", \"recipient\": \"billy\", \"type\": 1, \"from\": 42, \"to\": 27, \"data\": \"boo\"}}");
  printf("id: %s\n", fjage_msg_get_id(msg2));
  printf("clazz: %s\n", fjage_msg_get_clazz(msg2));
  printf("sender: %s\n", fjage_msg_get_sender(msg2));
  printf("recipient: %s\n", fjage_msg_get_recipient(msg2));
  printf("inReplyTo: %s\n", fjage_msg_get_in_reply_to(msg2));
  //msg_dump(msg2);
  fjage_msg_destroy(msg2);
  return 0;
}
