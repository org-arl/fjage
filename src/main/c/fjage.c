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
#include <errno.h>
#include <netdb.h>
#include <sys/time.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <sys/poll.h>
#include <netinet/in.h>
#include "fjage.h"
#include "jsmn.h"
#include "b64.h"

//// prototypes

static fjage_msg_t fjage_msg_from_json(const char* json);
static void fjage_msg_write_json(fjage_gw_t gw, fjage_msg_t msg);
static void fjage_msg_set_sender(fjage_msg_t msg, fjage_aid_t aid);

//// utilities

#define UUID_LEN        36

static char* base64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
static time_t _t0 = 0;

static void generate_uuid(char* uuid) {
  for (int i = 0; i < UUID_LEN; i++)
    uuid[i] = base64[rand()%64];
  uuid[UUID_LEN] = 0;
}

static int writes(int fd, const char* s) {
  int n = strlen(s);
  return write(fd, s, n);
}

static long get_time_ms(void) {
  struct timeval tv;
  gettimeofday(&tv, NULL);
  if (_t0 == 0) _t0 = tv.tv_sec;
  return (long)(tv.tv_sec-_t0)*1000 + (long)(tv.tv_usec)/1000;
}

//// gateway API

#define SUBLIST_LEN       1024
#define QUEUE_LEN         1024
#define BUFLEN            65536

typedef struct {
  int sockfd;
  int intfd[2];       // self-pipe used to break the poll on sockfd
  fjage_aid_t aid;
  char sublist[SUBLIST_LEN];
  char buf[BUFLEN];
  int head;
  int aid_count;
  fjage_aid_t* aids;
  fjage_msg_t mqueue[QUEUE_LEN];
  int mqueue_head;
  int mqueue_tail;
} _fjage_gw_t;

static void mqueue_put(fjage_gw_t gw, fjage_msg_t msg) {
  if (gw == NULL) return;
  _fjage_gw_t* fgw = gw;
  fgw->mqueue[fgw->mqueue_head] = msg;
  fgw->mqueue_head = (fgw->mqueue_head+1) % QUEUE_LEN;
  if (fgw->mqueue_head == fgw->mqueue_tail) {
    fgw->mqueue_tail = (fgw->mqueue_tail+1) % QUEUE_LEN;
    fjage_msg_destroy(fgw->mqueue[fgw->mqueue_head]);
    fgw->mqueue[fgw->mqueue_head] = NULL;
  }
}

static fjage_msg_t mqueue_get(fjage_gw_t gw, const char* clazz, const char* id) {
  if (gw == NULL) return NULL;
  _fjage_gw_t* fgw = gw;
  fjage_msg_t msg = NULL;
  for (int i = fgw->mqueue_tail; i != fgw->mqueue_head && msg == NULL; i++) {
    if (fgw->mqueue[i] != NULL) {
      if (clazz != NULL) {
        const char* clazz1 = fjage_msg_get_clazz(fgw->mqueue[i]);
        if (clazz1 == NULL || strcmp(clazz, clazz1)) continue;
      }
      if (id != NULL) {
        const char* id1 = fjage_msg_get_in_reply_to(fgw->mqueue[i]);
        if (id1 == NULL || strcmp(id, id1)) continue;
      }
      msg = fgw->mqueue[i];
      fgw->mqueue[i] = NULL;
      if (i == fgw->mqueue_tail) fgw->mqueue_tail = (fgw->mqueue_tail+1) % QUEUE_LEN;
    }
  }
  return msg;
}

static jsmntok_t* json_parse(const char* json) {
  jsmn_parser parser;
  jsmn_init(&parser);
  int n = jsmn_parse(&parser, json, strlen(json), NULL, 0);
  if (n < 0) return NULL;
  jsmntok_t* tokens = malloc(n*sizeof(jsmntok_t));
  if (tokens == NULL) return NULL;
  jsmn_init(&parser);
  jsmn_parse(&parser, json, strlen(json), tokens, n);
  return tokens;
}

static int json_get(char* json, const jsmntok_t* tokens, const char* key) {
  if (tokens == NULL || key == NULL) return -1;
  int n = tokens[0].size;
  int i = 1;
  while (n > 0) {
    char* t = json + tokens[i].start;
    t[tokens[i].end-tokens[i].start] = 0;
    if (!strcmp(t, key)) return i+1;
    int skip = tokens[i].size;
    while (skip > 0)
      skip += tokens[++i].size - 1;
    i++;
    n--;
  }
  return -1;
}

static const char* json_gets(char* json, const jsmntok_t* tokens, const char* key) {
  int i = json_get(json, tokens, key);
  if (i < 0) return NULL;
  char* t = json + tokens[i].start;
  t[tokens[i].end-tokens[i].start] = 0;
  return t;
}

static bool json_process(fjage_gw_t gw, char* json, const char* id) {
  if (gw == NULL) return false;
  _fjage_gw_t* fgw = gw;
  jsmntok_t* tokens = json_parse(json);
  if (tokens == NULL) return false;
  bool rv = false;
  const char* action = json_gets(json, tokens, "action");
  if (action == NULL) {
    const char* id1 = json_gets(json, tokens, "id");
    if (id != NULL && !strcmp(id, id1)) {
      action = json_gets(json, tokens, "inResponseTo");
      if (!strcmp(action, "agentForService")) {
        const char* s = json_gets(json, tokens, "agentID");
        if (s != NULL && fgw->aid_count == 0) {
          fgw->aid_count = 1;
          fgw->aids = (fjage_aid_t*)fjage_aid_create(s);
          rv = true;
        }
      } else if (!strcmp(action, "agentsForService")) {
        int i = json_get(json, tokens, "agentIDs");
        if (i >= 0) {
          int n = tokens[i].size;
          fgw->aids = malloc(n*sizeof(fjage_aid_t));
          if (fgw->aids != NULL) {
            fgw->aid_count = n;
            for (int j = 0; j < n; j++) {
              char* t = json+tokens[i+j+1].start;
              t[tokens[i+j+1].end-tokens[i+j+1].start] = 0;
              fgw->aids[j] = fjage_aid_create(t);
            }
            rv = true;
          }
        }
      }
    }
  } else {
    if (!strcmp(action, "send")) {
      const char* json_msg = json_gets(json, tokens, "message");
      fjage_msg_t msg = fjage_msg_from_json(json_msg);
      if (msg != NULL) {
        fjage_aid_t rcpt = fjage_msg_get_recipient(msg);
        if (rcpt != NULL && (!strcmp(rcpt, fgw->aid) || fjage_is_subscribed(fgw, rcpt))) {
          mqueue_put(gw, msg);
          if (id == NULL) rv = true;
        }
      }
    } else {
      char s[256];
      sprintf(s, "{\"id\": \"%s\", \"inResponseTo\": \"%s\", \"answer\": false}\n", json_gets(json, tokens, "id"), action);
      writes(fgw->sockfd, s);
    }
  }
  free(tokens);
  return rv;
}

static int json_reader(fjage_gw_t gw, const char* id, long timeout) {
  if (gw == NULL) return -1;
  _fjage_gw_t* fgw = gw;
  long t0 = get_time_ms();
  struct pollfd fds[2];
  memset(fds, 0, sizeof(fds));
  fds[0].fd = fgw->sockfd;
  fds[0].events = POLLIN;
  fds[1].fd = fgw->intfd[0];
  fds[1].events = POLLIN;
  int rv = poll(fds, 2, timeout);
  if (rv <= 0) return 0;
  if ((fds[1].revents & POLLIN) == 0) {
    int n;
    bool done = false;
    while ((n = read(fgw->sockfd, fgw->buf + fgw->head, BUFLEN - fgw->head)) > 0) {
      int bol = 0;
      for (int i = fgw->head; i < fgw->head + n; i++) {
        if (fgw->buf[i] == '\n') {
          fgw->buf[i] = 0;
          done = json_process(gw, fgw->buf + bol, id);
          bol = i+1;
        }
      }
      fgw->head += n;
      if (fgw->head >= BUFLEN) fgw->head = 0;
      if (bol > 0) {
        memmove(fgw->buf, fgw->buf + bol, fgw->head - bol);
        fgw->head -= bol;
      }
      if (done) break;
      long timeout1 = t0+timeout - get_time_ms();
      if (timeout1 <= 0) break;
      rv = poll(fds, 2, timeout1);
      if (rv <= 0) break;
      if ((fds[1].revents & POLLIN) != 0) break;
    }
  }
  rv = 0;
  uint8_t dummy;
  while (read(fgw->intfd[0], &dummy, 1) > 0)
    rv = -1;
  return rv;
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
  memcpy(&serv_addr.sin_addr.s_addr, server->h_addr, server->h_length);
  serv_addr.sin_port = htons(port);
  if (connect(fgw->sockfd, (struct sockaddr*)&serv_addr, sizeof(serv_addr)) < 0) {
    close(fgw->sockfd);
    free(fgw);
    return NULL;
  }
  if (pipe(fgw->intfd) < 0) {
    close(fgw->sockfd);
    free(fgw);
    return NULL;
  }
  fcntl(fgw->sockfd, F_SETFL, O_NONBLOCK);
  fcntl(fgw->intfd[0], F_SETFL, O_NONBLOCK);
  char s[64];
  sprintf(s, "CGatewayAgent@%08x", rand());
  fgw->aid = fjage_aid_create(s);
  fgw->head = 0;
  return fgw;
}

int fjage_close(fjage_gw_t gw) {
  if (gw == NULL) return -1;
  _fjage_gw_t* fgw = gw;
  close(fgw->sockfd);
  close(fgw->intfd[0]);
  close(fgw->intfd[1]);
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

int fjage_subscribe_agent(fjage_gw_t gw, const fjage_aid_t aid) {
  if (gw == NULL) return -1;
  if (aid == NULL) return -1;
  fjage_aid_t topic = malloc(strlen(aid)+7);
  if (topic == NULL) return -1;
  sprintf(topic, "#%s__ntf", aid);
  int rv = fjage_subscribe(gw, topic);
  free(topic);
  return rv;
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
  writes(fgw->sockfd, "{\"action\": \"agentForService\", \"id\": \"");
  writes(fgw->sockfd, uuid);
  writes(fgw->sockfd, "\", \"service\": \"");
  writes(fgw->sockfd, service);
  writes(fgw->sockfd, "\"}\n");
  fgw->aid_count = 0;
  fgw->aids = NULL;
  json_reader(gw, uuid, 1000);
  if (fgw->aid_count == 0) return NULL;
  return (fjage_aid_t)(fgw->aids);
}

int fjage_agents_for_service(fjage_gw_t gw, const char* service, fjage_aid_t* agents, int max) {
  if (gw == NULL) return -1;
  _fjage_gw_t* fgw = gw;
  char uuid[UUID_LEN+1];
  generate_uuid(uuid);
  writes(fgw->sockfd, "{\"action\": \"agentsForService\", \"id\": \"");
  writes(fgw->sockfd, uuid);
  writes(fgw->sockfd, "\", \"service\": \"");
  writes(fgw->sockfd, service);
  writes(fgw->sockfd, "\"}\n");
  fgw->aid_count = 0;
  fgw->aids = NULL;
  json_reader(gw, uuid, 1000);
  if (fgw->aid_count == 0) return 0;
  for (int i = 0; i < fgw->aid_count; i++) {
    if (i < max) agents[i] = fgw->aids[i];
    else fjage_aid_destroy(fgw->aids[i]);
  }
  free(fgw->aids);
  return fgw->aid_count;
}

int fjage_send(fjage_gw_t gw, const fjage_msg_t msg) {
  if (gw == NULL) {
    fjage_msg_destroy(msg);
    return -1;
  }
  _fjage_gw_t* fgw = gw;
  writes(fgw->sockfd, "{\"action\": \"send\", \"relay\": true, \"message\": ");
  fjage_msg_write_json(gw, msg);
  writes(fgw->sockfd, "}\n");
  fjage_msg_destroy(msg);
  return 0;
}

fjage_msg_t fjage_receive(fjage_gw_t gw, const char* clazz, const char* id, long timeout) {
  long t0 = get_time_ms();
  long timeout1 = timeout;
  fjage_msg_t msg = NULL;
  while (msg == NULL && timeout1 > 0) {
    if (json_reader(gw, NULL, timeout1) < 0) break;
    msg = mqueue_get(gw, clazz, id);
    if (msg == NULL) timeout1 = t0+timeout - get_time_ms();
  }
  return msg;
}

fjage_msg_t fjage_request(fjage_gw_t gw, const fjage_msg_t request, long timeout) {
  char id[UUID_LEN+1];
  const char* id1 = fjage_msg_get_id(request);
  if (id1 == NULL) {
    fjage_msg_destroy(request);
    return NULL;
  }
  strcpy(id, id1);
  if (fjage_send(gw, request) < 0) return NULL;
  return fjage_receive(gw, NULL, id, timeout);
}

void fjage_interrupt(fjage_gw_t gw) {
  if (gw == NULL) return;
  _fjage_gw_t* fgw = gw;
  uint8_t dummy = 1;
  write(fgw->intfd[1], &dummy, 1);
}

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
    if (!strcmp(t, "clazz") && m->clazz[0] == 0) {
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
    } else if (!strcmp(t, "perf")) {
      t = m->data + m->tokens[i+1].start;
      t[m->tokens[i+1].end-m->tokens[i+1].start] = 0;
      if (!strcmp(t, "REQUEST")) ((_fjage_msg_t*)msg)->perf = FJAGE_REQUEST;
      else if (!strcmp(t, "AGREE")) ((_fjage_msg_t*)msg)->perf = FJAGE_AGREE;
      else if (!strcmp(t, "REFUSE")) ((_fjage_msg_t*)msg)->perf = FJAGE_REFUSE;
      else if (!strcmp(t, "FAILURE")) ((_fjage_msg_t*)msg)->perf = FJAGE_FAILURE;
      else if (!strcmp(t, "INFORM")) ((_fjage_msg_t*)msg)->perf = FJAGE_INFORM;
      else if (!strcmp(t, "CONFIRM")) ((_fjage_msg_t*)msg)->perf = FJAGE_CONFIRM;
      else if (!strcmp(t, "DISCONFIRM")) ((_fjage_msg_t*)msg)->perf = FJAGE_DISCONFIRM;
      else if (!strcmp(t, "QUERY_IF")) ((_fjage_msg_t*)msg)->perf = FJAGE_QUERY_IF;
      else if (!strcmp(t, "NOT_UNDERSTOOD")) ((_fjage_msg_t*)msg)->perf = FJAGE_NOT_UNDERSTOOD;
      else if (!strcmp(t, "CFP")) ((_fjage_msg_t*)msg)->perf = FJAGE_CFP;
      else if (!strcmp(t, "PROPOSE")) ((_fjage_msg_t*)msg)->perf = FJAGE_PROPOSE;
      else if (!strcmp(t, "CANCEL")) ((_fjage_msg_t*)msg)->perf = FJAGE_CANCEL;
      m->tokens[i].type = 0;
    }
  }
}

static void fjage_msg_write_json(fjage_gw_t gw, fjage_msg_t msg) {
  if (msg == NULL || gw == NULL) return;
  _fjage_gw_t* fgw = gw;
  _fjage_msg_t* m = msg;
  int fd = fgw->sockfd;
  writes(fd, "{\"clazz\": \"");
  writes(fd, m->clazz);
  writes(fd, "\", \"data\": { \"msgID\": \"");
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
  writes(fd, "\"sender\": \"");
  writes(fd, fgw->aid);
  writes(fd, "\"");
  if (m->data != NULL) {
    writes(fd, ", ");
    char* p = m->data;
    int n = strlen(m->data)-2;
    while (n > 0) {
      int rv = write(fd, p, n);
      if (rv < 0) {
        if (errno == EAGAIN) usleep(10000);
        else break;
      } else {
        p += rv;
        n -= rv;
      }
    }
  }
  writes(fd, "}}");
}

fjage_msg_t fjage_msg_create(const char* clazz, fjage_perf_t perf) {
  _fjage_msg_t* m = malloc(sizeof(_fjage_msg_t));
  if (m == NULL) return NULL;
  generate_uuid(m->id);
  memset(m->clazz, 0, CLAZZ_LEN+1);
  if (clazz != NULL) strncpy(m->clazz, clazz, CLAZZ_LEN);
  m->perf = perf;
  m->recipient = NULL;
  m->sender = NULL;
  m->in_reply_to[0] = 0;
  m->data = NULL;
  m->data_len = 0;
  m->tokens = NULL;
  m->ntokens = 0;
  return m;
}

static fjage_msg_t fjage_msg_from_json(const char* json) {
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

static void fjage_msg_set_sender(fjage_msg_t msg, fjage_aid_t aid) {
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

static const char* fjage_msg_get_data(fjage_msg_t msg, const char* key) {
  if (msg == NULL) return NULL;
  _fjage_msg_t* m = msg;
  int n = -1;
  for (int i = 1; i < m->ntokens-1; i += 2) {
    char* t = m->data + m->tokens[i].start;
    if (n < 0) {
      if (m->tokens[i].type == JSMN_STRING && m->tokens[i+1].type == JSMN_OBJECT && !strcmp(t, key)) n = m->tokens[i+1].size;
    } else {
      n--;
      if (n < 0) return NULL;
      if (m->tokens[i].type == JSMN_STRING && m->tokens[i+1].type == JSMN_STRING && !strcmp(t, "data")) {
        t = m->data + m->tokens[i+1].start;
        t[m->tokens[i+1].end-m->tokens[i+1].start] = 0;
        return t;
      }
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

long fjage_msg_get_long(fjage_msg_t msg, const char* key, long defval) {
  const char* t = fjage_msg_get_string(msg, key);
  if (t == NULL) return defval;
  long x = defval;
  sscanf(t, "%ld", &x);
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
  const char* s = fjage_msg_get_string(msg, key);
  if (s == NULL) s = fjage_msg_get_data(msg, key);
  if (s == NULL) return -1;
  size_t buflen;
  unsigned char* buf = b64_decode_ex(s, strlen(s), &buflen);
  if (buf == NULL) return -1;
  if (buflen <= maxlen) memcpy(value, buf, buflen);
  return buflen;
}

int fjage_msg_get_float_array(fjage_msg_t msg, const char* key, float* value, int maxlen) {
  const char* s = fjage_msg_get_string(msg, key);
  if (s == NULL) s = fjage_msg_get_data(msg, key);
  if (s == NULL) return -1;
  size_t buflen;
  unsigned char* buf = b64_decode_ex(s, strlen(s), &buflen);
  if (buf == NULL) return -1;
  if (buflen <= maxlen*sizeof(float)) memcpy(value, buf, buflen);
  return buflen/sizeof(float);
}

void fjage_msg_add_string(fjage_msg_t msg, const char* key, const char* value) {
  char* s = msg_append(msg, strlen(key)+strlen(value)+9);
  if (s != NULL) sprintf(s, "\"%s\": \"%s\", ", key, value);
}

void fjage_msg_add_int(fjage_msg_t msg, const char* key, int value) {
  char* s = msg_append(msg, strlen(key)+16);
  if (s != NULL) sprintf(s, "\"%s\": %d, ", key, value);
}

void fjage_msg_add_long(fjage_msg_t msg, const char* key, long value) {
  char* s = msg_append(msg, strlen(key)+16);
  if (s != NULL) sprintf(s, "\"%s\": %ld, ", key, value);
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
  char* s = b64_encode((unsigned char*)value, len);
  if (s == NULL) return;
  fjage_msg_add_string(msg, key, s);
  free(s);
}

void fjage_msg_add_float_array(fjage_msg_t msg, const char* key, float* value, int len) {
  char* s = b64_encode((unsigned char*)value, len*sizeof(float));
  if (s == NULL) return;
  fjage_msg_add_string(msg, key, s);
  free(s);
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
