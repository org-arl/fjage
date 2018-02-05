/******************************************************************************

Copyright (c) 2018, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include "fjage.h"
#include "jsmn.h"

//// utility functions

static int writes(int fd, const char* s) {
  int n = strlen(s);
  return write(fd, s, n);
}

//// gateway API

fjage_gw_t fjage_tcp_open(const char* hostname, int port);
int fjage_close(fjage_gw_t gw);
fjage_aid_t fjage_get_agent_id(fjage_gw_t gw);
int fjage_subscribe(fjage_gw_t gw, const fjage_aid_t topic);
int fjage_unsubscribe(fjage_gw_t gw, const fjage_aid_t topic);
fjage_aid_t fjage_agent_for_service(fjage_gw_t gw, const char* service);
int fjage_agents_for_service(fjage_gw_t gw, const char* service, fjage_aid_t* agents, int max);
int fjage_send(fjage_gw_t gw, const fjage_msg_t msg);
fjage_msg_t fjage_receive(fjage_gw_t gw, const char* clazz, const fjage_msg_t request, long timeout);
fjage_msg_t fjage_request(fjage_gw_t gw, const fjage_msg_t request, long timeout);

//// agent ID API

fjage_aid_t fjage_aid_create(const char* name) {
  char* aid = malloc(strlen(name)+1);
  if (aid == NULL) return NULL;
  strcpy(aid, name);
  return aid;
}

fjage_aid_t fjage_aid_topic(const char* topic) {
  char* aid = malloc(strlen(topic)+2);
  if (aid == NULL) return NULL;
  aid[0] = '#';
  strcpy(aid+1, topic);
  return aid;
}

void fjage_aid_destroy(fjage_aid_t aid) {
  free(aid);
}

static fjage_aid_t clone_aid(fjage_aid_t aid) {
  if (aid == NULL) return NULL;
  return fjage_aid_create(aid);
}

//// message API

#define UUID_LEN        36
#define CLAZZ_LEN       128
#define DATA_BLK_LEN    4096

typedef struct {
  char id[UUID_LEN+1];
  char clazz[CLAZZ_LEN+1];
  fjage_perf_t perf;
  fjage_aid_t sender;
  fjage_aid_t recipient;
  char in_reply_to[UUID_LEN+1];
  char* data;
  int data_len;
  jsmntok_t* tokens;
  int ntokens;
} _fjage_msg_t;

static char* base64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

static void generate_uuid(char* uuid) {
  for (int i = 0; i < UUID_LEN; i++)
    uuid[i] = base64[rand()%64];
  uuid[UUID_LEN] = 0;
}

static char* msg_append(fjage_msg_t msg, int n) {
  if (msg == NULL) return NULL;
  _fjage_msg_t* m = msg;
  if (m->data_len < 0) return NULL;
  int p = 0;
  if (m->data != NULL) p = strlen(m->data);
  if (p+n > m->data_len) {
    int nn = ((p+n-1)/DATA_BLK_LEN+1)*DATA_BLK_LEN;
    char* s = realloc(m->data, nn);
    if (s == NULL) return NULL;
    m->data = s;
    m->data_len = nn;
  }
  return m->data+p;
}

static void msg_write_json(fjage_msg_t msg, int fd) {
  if (msg == NULL) return;
  _fjage_msg_t* m = msg;
  if (m->data == NULL) return;
  writes(fd, "{\"clazz\": \"");
  writes(fd, m->clazz);
  writes(fd, "\", data: { \"msgID\": \"");
  writes(fd, m->id);
  writes(fd, "\", ");
  switch (m->perf) {
    case FJAGE_REQUEST:         writes(fd, "\"perf\": \"REQUEST\", ");         break;
    case FJAGE_AGREE:           writes(fd, "\"perf\": \"AGREE\", ");           break;
    case FJAGE_REFUSE:          writes(fd, "\"perf\": \"REFUSE\", ");          break;
    case FJAGE_FAILURE:         writes(fd, "\"perf\": \"FAILURE\", ");         break;
    case FJAGE_INFORM:          writes(fd, "\"perf\": \"INFORM\", ");          break;
    case FJAGE_CONFIRM:         writes(fd, "\"perf\": \"CONFIRM\", ");         break;
    case FJAGE_DISCONFIRM:      writes(fd, "\"perf\": \"DISCONFIRM\", ");      break;
    case FJAGE_QUERY_IF:        writes(fd, "\"perf\": \"QUERY_IF\", ");        break;
    case FJAGE_NOT_UNDERSTOOD:  writes(fd, "\"perf\": \"NOT_UNDERSTOOD\", ");  break;
    case FJAGE_CFP:             writes(fd, "\"perf\": \"CFP\", ");             break;
    case FJAGE_PROPOSE:         writes(fd, "\"perf\": \"PROPOSE\", ");         break;
    case FJAGE_CANCEL:          writes(fd, "\"perf\": \"CANCEL\", ");          break;
    case FJAGE_NONE:                                                           break;
  }
  if (m->sender != NULL) {
    writes(fd, "\"sender\": \"");
    writes(fd, m->sender);
    writes(fd, "\", ");
  }
  if (m->recipient != NULL) {
    writes(fd, "\"recipient\": \"");
    writes(fd, m->recipient);
    writes(fd, "\", ");
  }
  if (m->in_reply_to[0]) {
    writes(fd, "\"inReplyTo\": \"");
    writes(fd, m->in_reply_to);
    writes(fd, "\", ");
  }
  write(fd, m->data, strlen(m->data)-2);
  writes(fd, "}}");
}

static void msg_from_json(fjage_msg_t msg, const char* s) {
  if (msg == NULL) return;
  _fjage_msg_t* m = msg;
  if (m->tokens != NULL || m->data != NULL) return;
  jsmn_parser parser;
  jsmn_init(&parser);
  int n = jsmn_parse(&parser, s, strlen(s), NULL, 0);
  if (n < 0) return;
  m->tokens = malloc(n*sizeof(jsmntok_t));
  if (m->tokens == NULL) return;
  m->data = malloc(strlen(s)+1);
  if (m->data == NULL) {
    free(m->tokens);
    m->tokens = NULL;
    return;
  }
  strcpy(m->data, s);
  jsmn_init(&parser);
  m->ntokens = jsmn_parse(&parser, m->data, strlen(m->data), m->tokens, n);
  m->data_len = -1;
  for (int i = 1; i < m->ntokens; i+=2) {
    char* t = m->data + m->tokens[i].start;
    t[m->tokens[i].end-m->tokens[i].start] = 0;
    if (!strcmp(t, "clazz")) {
      t = m->data + m->tokens[i+1].start;
      t[m->tokens[i+1].end-m->tokens[i+1].start] = 0;
      strncpy(m->clazz, t, CLAZZ_LEN);
      m->clazz[CLAZZ_LEN] = 0;
      m->tokens[i].type = 0;
    } else if (!strcmp(t, "msgID")) {
      t = m->data + m->tokens[i+1].start;
      t[m->tokens[i+1].end-m->tokens[i+1].start] = 0;
      strncpy(m->id, t, UUID_LEN);
      m->id[UUID_LEN] = 0;
      m->tokens[i].type = 0;
    } else if (!strcmp(t, "inReplyTo")) {
      t = m->data + m->tokens[i+1].start;
      t[m->tokens[i+1].end-m->tokens[i+1].start] = 0;
      strncpy(m->in_reply_to, t, UUID_LEN);
      m->in_reply_to[UUID_LEN] = 0;
      m->tokens[i].type = 0;
    } else if (!strcmp(t, "sender")) {
      t = m->data + m->tokens[i+1].start;
      t[m->tokens[i+1].end-m->tokens[i+1].start] = 0;
      fjage_msg_set_sender(msg, (fjage_aid_t)t);
      m->tokens[i].type = 0;
    } else if (!strcmp(t, "recipient")) {
      t = m->data + m->tokens[i+1].start;
      t[m->tokens[i+1].end-m->tokens[i+1].start] = 0;
      fjage_msg_set_recipient(msg, (fjage_aid_t)t);
      m->tokens[i].type = 0;
    }
  }
}

// void msg_dump(fjage_msg_t msg) {
//   if (msg == NULL) return;
//   _fjage_msg_t* m = msg;
//   for (int i = 1; i < m->ntokens; i+=2) {
//     if (m->tokens[i].type == 3)
//       printf("%.*s: %.*s\n", m->tokens[i].end-m->tokens[i].start, m->data + m->tokens[i].start, m->tokens[i+1].end-m->tokens[i+1].start, m->data + m->tokens[i+1].start);
//   }
// }

fjage_msg_t fjage_msg_create(const char* clazz, fjage_perf_t perf) {
  _fjage_msg_t* m = malloc(sizeof(_fjage_msg_t));
  if (m == NULL) return NULL;
  generate_uuid(m->id);
  memset(m->clazz, 0, CLAZZ_LEN+1);
  if (clazz != NULL) strncpy(m->clazz, clazz, CLAZZ_LEN);
  m->perf = perf;
  m->sender = NULL;
  m->recipient = NULL;
  m->in_reply_to[0] = 0;
  m->data = NULL;
  m->data_len = 0;
  m->tokens = NULL;
  m->ntokens = 0;
  return m;
}

const char* fjage_msg_get_id(fjage_msg_t msg) {
  if (msg == NULL) return NULL;
  _fjage_msg_t* m = msg;
  return m->id;
}

const char* fjage_msg_get_clazz(fjage_msg_t msg) {
  if (msg == NULL) return NULL;
  _fjage_msg_t* m = msg;
  return m->clazz;
}

fjage_perf_t fjage_msg_get_performative(fjage_msg_t msg) {
  if (msg == NULL) return FJAGE_NONE;
  _fjage_msg_t* m = msg;
  return m->perf;
}

fjage_aid_t fjage_msg_get_recipient(fjage_msg_t msg) {
  if (msg == NULL) return NULL;
  _fjage_msg_t* m = msg;
  return m->recipient;
}

fjage_aid_t fjage_msg_get_sender(fjage_msg_t msg) {
  if (msg == NULL) return NULL;
  _fjage_msg_t* m = msg;
  return m->sender;
}

const char* fjage_msg_get_in_reply_to(fjage_msg_t msg) {
  if (msg == NULL) return NULL;
  _fjage_msg_t* m = msg;
  if (m->in_reply_to[0] == 0) return NULL;
  return m->in_reply_to;
}

void fjage_msg_set_recipient(fjage_msg_t msg, fjage_aid_t aid) {
  if (msg == NULL) return;
  _fjage_msg_t* m = msg;
  free(m->recipient);
  m->recipient = clone_aid(aid);
}

void fjage_msg_set_sender(fjage_msg_t msg, fjage_aid_t aid) {
  if (msg == NULL) return;
  _fjage_msg_t* m = msg;
  free(m->sender);
  m->sender = clone_aid(aid);
}

void fjage_msg_set_in_reply_to(fjage_msg_t msg, const char* id) {
  if (msg == NULL) return;
  _fjage_msg_t* m = msg;
  if (id == NULL) m->in_reply_to[0] = 0;
  else strncpy(m->in_reply_to, id, UUID_LEN);
  m->in_reply_to[UUID_LEN] = 0;
}

const char* fjage_msg_get_string(fjage_msg_t msg, const char* key);
int fjage_msg_get_int(fjage_msg_t msg, const char* key, int defval);
float fjage_msg_get_float(fjage_msg_t msg, const char* key, float defval);
bool fjage_msg_get_bool(fjage_msg_t msg, const char* key, bool defval);
int fjage_msg_get_byte_array(fjage_msg_t msg, const char* key, uint8_t* value, int maxlen);
int fjage_msg_get_float_array(fjage_msg_t msg, const char* key, float* value, int maxlen);

void fjage_msg_add_string(fjage_msg_t msg, const char* key, const char* value) {
  char* s = msg_append(msg, strlen(key)+strlen(value)+9);
  if (s != NULL) sprintf(s, "\"%s\": \"%s\", ", key, value);
}

void fjage_msg_add_int(fjage_msg_t msg, const char* key, int value) {
  char* s = msg_append(msg, strlen(key)+16);
  if (s != NULL) sprintf(s, "\"%s\": %d, ", key, value);
}

void fjage_msg_add_float(fjage_msg_t msg, const char* key, float value) {
  char* s = msg_append(msg, strlen(key)+30);
  if (s != NULL) sprintf(s, "\"%s\": %f, ", key, value);
}

void fjage_msg_add_bool(fjage_msg_t msg, const char* key, bool value) {
  char* s = msg_append(msg, strlen(key)+11);
  if (s != NULL) sprintf(s, "\"%s\": %s, ", key, value?"true":"false");
}

void fjage_msg_add_byte_array(fjage_msg_t msg, const char* key, uint8_t* value, int len) {
  // TODO
}

void fjage_msg_add_float_array(fjage_msg_t msg, const char* key, float* value, int len) {
  // TODO
}

void fjage_msg_destroy(fjage_msg_t msg) {
  if (msg == NULL) return;
  _fjage_msg_t* m = msg;
  free(m->sender);
  free(m->recipient);
  free(m->data);
  free(m->tokens);
  free(msg);
}
