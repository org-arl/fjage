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
#include <fcntl.h>
#include <netdb.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include "fjage.h"
#include "jsmn.h"

//// utilities

#define UUID_LEN        36

static char* base64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

static void generate_uuid(char* uuid) {
  for (int i = 0; i < UUID_LEN; i++)
    uuid[i] = base64[rand()%64];
  uuid[UUID_LEN] = 0;
}

static int writes(int fd, const char* s) {
  int n = strlen(s);
  return write(fd, s, n);
}

//// gateway API

#define SUBLIST_LEN       1024

typedef struct {
  int sockfd;
  fjage_aid_t aid;
  char sublist[SUBLIST_LEN];
} _fjage_gw_t;

static void process_responses(fjage_gw_t gw) {
  if (gw == NULL) return;
  _fjage_gw_t* fgw = gw;
  char buf[1024];
  int n;
  while ((n = read(fgw->sockfd, buf, sizeof(buf)-1)) > 0) {
    buf[n] = 0;
    printf("[%s]\n", buf);
  }
}

bool fjage_is_subscribed(fjage_gw_t gw, const fjage_aid_t topic) {
  if (gw == NULL) return false;
  _fjage_gw_t* fgw = gw;
  int i = 0;
  while (fgw->sublist[i]) {
    if (!strcmp(fgw->sublist+i, topic)) return true;
    i += strlen(fgw->sublist+i)+1;
  }
  return false;
}

fjage_gw_t fjage_tcp_open(const char* hostname, int port) {
  _fjage_gw_t* fgw = calloc(1, sizeof(_fjage_gw_t));
  if (fgw == NULL) return NULL;
  fgw->sockfd = socket(AF_INET, SOCK_STREAM, 0);
  if (fgw->sockfd < 0) {
    free(fgw);
    return NULL;
  }
  struct hostent* server = gethostbyname(hostname);
  if (server == NULL) {
    close(fgw->sockfd);
    free(fgw);
    return NULL;
  }
  struct sockaddr_in serv_addr;
  memset(&serv_addr, 0, sizeof(serv_addr));
  serv_addr.sin_family = AF_INET;
  memcpy(server->h_addr, &serv_addr.sin_addr.s_addr, server->h_length);
  serv_addr.sin_port = htons(port);
  if (connect(fgw->sockfd, (struct sockaddr*)&serv_addr, sizeof(serv_addr)) < 0) {
    close(fgw->sockfd);
    free(fgw);
    return NULL;
  }
  fcntl(fgw->sockfd, F_SETFL, O_NONBLOCK);
  char s[64];
  sprintf(s, "CGatewayAgent@%08x", rand());
  fgw->aid = fjage_aid_create(s);
  return fgw;
}

int fjage_close(fjage_gw_t gw) {
  if (gw == NULL) return -1;
  _fjage_gw_t* fgw = gw;
  close(fgw->sockfd);
  fjage_aid_destroy(fgw->aid);
  free(fgw);
  return 0;
}

fjage_aid_t fjage_get_agent_id(fjage_gw_t gw) {
  if (gw == NULL) return NULL;
  _fjage_gw_t* fgw = gw;
  return fgw->aid;
}

int fjage_subscribe(fjage_gw_t gw, const fjage_aid_t topic) {
  if (gw == NULL) return -1;
  _fjage_gw_t* fgw = gw;
  int i = 0;
  while (fgw->sublist[i])
    i += strlen(fgw->sublist+i)+1;
  if (i+strlen(topic)+1 > SUBLIST_LEN) return -1;
  strcpy(fgw->sublist+i, topic);
  return 0;
}

int fjage_unsubscribe(fjage_gw_t gw, const fjage_aid_t topic) {
  if (gw == NULL) return -1;
  _fjage_gw_t* fgw = gw;
  int i = 0;
  while (fgw->sublist[i]) {
    if (!strcmp(fgw->sublist+i, topic)) {
      int n = strlen(fgw->sublist+i);
      memmove(fgw->sublist+i, fgw->sublist+i+n+1, SUBLIST_LEN-(i+n+1));
      memset(fgw->sublist+SUBLIST_LEN-(n+1), 0, n+1);
      return 0;
    }
    i += strlen(fgw->sublist+i)+1;
  }
  return -1;
}

fjage_aid_t fjage_agent_for_service(fjage_gw_t gw, const char* service)  {
  if (gw == NULL) return NULL;
  _fjage_gw_t* fgw = gw;
  char uuid[UUID_LEN+1];
  generate_uuid(uuid);
  writes(fgw->sockfd, "{ \"action\": \"agentForService\", \"id\": \"");
  writes(fgw->sockfd, uuid);
  writes(fgw->sockfd, "\", \"service\": \"");
  writes(fgw->sockfd, service);
  writes(fgw->sockfd, "\" }\n");
  sleep(1);
  process_responses(fgw);
  return NULL;
}

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

static void msg_read_json(fjage_msg_t msg, const char* s) {
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

void fjage_msg_write_json(fjage_msg_t msg, int fd) {
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

fjage_msg_t fjage_msg_from_json(const char* json) {
  fjage_msg_t msg = fjage_msg_create(NULL, FJAGE_NONE);
  if (msg == NULL) return NULL;
  msg_read_json(msg, json);
  return msg;
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

const char* fjage_msg_get_string(fjage_msg_t msg, const char* key) {
  if (msg == NULL) return NULL;
  _fjage_msg_t* m = msg;
  for (int i = 1; i < m->ntokens-1; i += 2) {
    char* t = m->data + m->tokens[i].start;
    if (m->tokens[i].type == JSMN_STRING && (m->tokens[i+1].type == JSMN_STRING || m->tokens[i+1].type == JSMN_PRIMITIVE) && !strcmp(key, t)) {
      t = m->data + m->tokens[i+1].start;
      t[m->tokens[i+1].end-m->tokens[i+1].start] = 0;
      return t;
    }
  }
  return NULL;
}

int fjage_msg_get_int(fjage_msg_t msg, const char* key, int defval) {
  const char* t = fjage_msg_get_string(msg, key);
  if (t == NULL) return defval;
  int x = defval;
  sscanf(t, "%d", &x);
  return x;
}

float fjage_msg_get_float(fjage_msg_t msg, const char* key, float defval) {
  const char* t = fjage_msg_get_string(msg, key);
  if (t == NULL) return defval;
  float x = defval;
  sscanf(t, "%f", &x);
  return x;
}

bool fjage_msg_get_bool(fjage_msg_t msg, const char* key, bool defval) {
  const char* t = fjage_msg_get_string(msg, key);
  if (t == NULL) return defval;
  if (!strcmp(t, "true")) return true;
  else if (!strcmp(t, "false")) return false;
  else return defval;
}

int fjage_msg_get_byte_array(fjage_msg_t msg, const char* key, uint8_t* value, int maxlen) {
  // TODO
  return 0;
}

int fjage_msg_get_float_array(fjage_msg_t msg, const char* key, float* value, int maxlen) {
  // TODO
  return 0;
}

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
