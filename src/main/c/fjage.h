#ifndef _FJAGE_H_
#define _FJAGE_H_

#include <stdbool.h>
#include <stdint.h>

typedef void* fjage_gw_t;
typedef char* fjage_aid_t;
typedef void* fjage_msg_t;

fjage_gw_t    fjage_tcp_open(const char* hostname, int port);
int           fjage_close(fjage_gw_t gw);
fjage_aid_t   fjage_get_agent_id(fjage_gw_t gw);
int           fjage_subscribe(fjage_gw_t gw, const fjage_aid_t topic);
int           fjage_unsubscribe(fjage_gw_t gw, const fjage_aid_t topic);
fjage_aid_t   fjage_agent_for_service(fjage_gw_t gw, const char* service);
int           fjage_agents_for_service(fjage_gw_t gw, const char* service, fjage_aid_t* agents, int max);
int           fjage_send(fjage_gw_t gw, const fjage_msg_t msg);
fjage_msg_t   fjage_receive(fjage_gw_t gw, const char* clazz, const fjage_msg_t request, long timeout);
fjage_msg_t   fjage_request(fjage_gw_t gw, const fjage_msg_t request, long timeout);

fjage_aid_t   fjage_aid_create(const char* name);
fjage_aid_t   fjage_aid_topic(const char* topic);
void          fjage_aid_destroy(fjage_aid_t aid);

typedef enum {
  FJAGE_NONE = 0,
  FJAGE_REQUEST = 1,
  FJAGE_AGREE = 2,
  FJAGE_REFUSE = 3,
  FJAGE_FAILURE = 4,
  FJAGE_INFORM = 5,
  FJAGE_CONFIRM = 6,
  FJAGE_DISCONFIRM = 7,
  FJAGE_QUERY_IF = 8,
  FJAGE_NOT_UNDERSTOOD = 9,
  FJAGE_CFP = 10,
  FJAGE_PROPOSE = 11,
  FJAGE_CANCEL = 12
} fjage_perf_t;

fjage_msg_t   fjage_msg_create(const char* clazz, fjage_perf_t perf);
const char*   fjage_msg_get_id(fjage_msg_t msg);
const char*   fjage_msg_get_clazz(fjage_msg_t msg);
fjage_perf_t  fjage_msg_get_performative(fjage_msg_t msg);
fjage_aid_t   fjage_msg_get_recipient(fjage_msg_t msg);
fjage_aid_t   fjage_msg_get_sender(fjage_msg_t msg);
const char*   fjage_msg_get_in_reply_to(fjage_msg_t msg);
void          fjage_msg_set_recipient(fjage_msg_t msg, fjage_aid_t aid);
void          fjage_msg_set_sender(fjage_msg_t msg, fjage_aid_t aid);
void          fjage_msg_set_in_reply_to(fjage_msg_t msg, const char* id);
const char*   fjage_msg_get_string(fjage_msg_t msg, const char* key);
int           fjage_msg_get_int(fjage_msg_t msg, const char* key, int defval);
float         fjage_msg_get_float(fjage_msg_t msg, const char* key, float defval);
bool          fjage_msg_get_bool(fjage_msg_t msg, const char* key, bool defval);
int           fjage_msg_get_byte_array(fjage_msg_t msg, const char* key, uint8_t* value, int maxlen);
int           fjage_msg_get_float_array(fjage_msg_t msg, const char* key, float* value, int maxlen);
void          fjage_msg_add_string(fjage_msg_t msg, const char* key, const char* value);
void          fjage_msg_add_int(fjage_msg_t msg, const char* key, int value);
void          fjage_msg_add_float(fjage_msg_t msg, const char* key, float value);
void          fjage_msg_add_bool(fjage_msg_t msg, const char* key, bool value);
void          fjage_msg_add_byte_array(fjage_msg_t msg, const char* key, uint8_t* value, int len);
void          fjage_msg_add_float_array(fjage_msg_t msg, const char* key, float* value, int len);
void          fjage_msg_destroy(fjage_msg_t msg);

#endif
