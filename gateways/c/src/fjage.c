/******************************************************************************

Copyright (c) 2018-2020, Mandar Chitre

This file is part of fjage which is released under Simplified BSD License.
See file LICENSE.txt or go to http://www.opensource.org/licenses/BSD-3-Clause
for full license details.

******************************************************************************/

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <fcntl.h>
#include <errno.h>
#include <signal.h>

#ifdef _WIN32
#pragma comment(lib, "ws2_32.lib")
#define WIN32_LEAN_AND_MEAN
#include <Windows.h>
#include <io.h>
#include <winsock2.h>
#include <Ws2tcpip.h>
#else
#include <unistd.h>
#include <netdb.h>
#include <termios.h>
#include <execinfo.h>
#include <sys/time.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#endif

#include "fjage.h"
#include "jsmn.h"
#include "b64.h"

// Interruptable poll_data() is implemented using 3 APIs for platform compatibility.
// The 3 options are listed below, in order of computational efficiency:
//
// 1. Define USE_POLL on UNIX-style OS with support for IPC pipes and poll()
// 2. Define USE_SELECT on use select() instead of poll() to wait for data
// 3. Define USE_IOCTL to use ioctl() to check for data availability

#ifdef _WIN32
#define USE_SELECT
#else
#define USE_POLL
#endif
//#define USE_IOCTL

#if !defined(USE_POLL) && !defined(USE_SELECT) && !defined(USE_IOCTL)
  #error Define one of USE_POLL, USE_SELECT or USE_IOCTL
#endif

#ifdef USE_POLL
#include <sys/poll.h>
  #if defined(USE_SELECT) || defined(USE_IOCTL)
  #error Do not define USE_SELECT or USE_IOCTL when using USE_POLL
  #endif
#endif

#ifdef USE_IOCTL
#include <sys/ioctl.h>
  #ifdef USE_SELECT
  #error Do not define USE_SELECT when using USE_IOCTL
  #endif
#endif

//// prototypes

static fjage_msg_t fjage_msg_from_json(const char* json);
static void fjage_msg_write_json(fjage_gw_t gw, fjage_msg_t msg);
static void fjage_msg_set_sender(fjage_msg_t msg, fjage_aid_t aid);
#ifdef __GNUC__
static void sthandler(int sig) __attribute__ ((unused));
#endif

//// utilities

#define UUID_LEN        36
#define h_addr h_addr_list[0] /* for backward compatibility */

static char* base64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
static time_t _t0 = 0;

static void generate_uuid(char* uuid) {
  for (int i = 0; i < UUID_LEN; i++)
    uuid[i] = base64[rand()%64];
  uuid[UUID_LEN] = 0;
}

static int writes(int fd, const char* s) {
  int n = strlen(s);
#ifdef _WIN32
  return send(fd, s, n, 0);
#else
  return write(fd, s, n);
#endif
}

#ifdef _WIN32
int gettimeofday(struct timeval * tp, struct timezone * tzp)
{
    // Note: some broken versions only have 8 trailing zero's, the correct epoch has 9 trailing zero's
    // This magic number is the number of 100 nanosecond intervals since January 1, 1601 (UTC)
    // until 00:00:00 January 1, 1970
    static const uint64_t EPOCH = ((uint64_t) 116444736000000000ULL);

    SYSTEMTIME  system_time;
    FILETIME    file_time;
    uint64_t    time;

    GetSystemTime( &system_time );
    SystemTimeToFileTime( &system_time, &file_time );
    time =  ((uint64_t)file_time.dwLowDateTime )      ;
    time += ((uint64_t)file_time.dwHighDateTime) << 32;

    tp->tv_sec  = (long) ((time - EPOCH) / 10000000L);
    tp->tv_usec = (long) (system_time.wMilliseconds * 1000);
    return 0;
}

void usleep(__int64 usec)
{
    HANDLE timer;
    LARGE_INTEGER ft;

    ft.QuadPart = -(10*usec); // Convert to 100 nanosecond interval, negative value indicates relative time

    timer = CreateWaitableTimer(NULL, TRUE, NULL);
    SetWaitableTimer(timer, &ft, 0, NULL, NULL, 0);
    WaitForSingleObject(timer, INFINITE);
    CloseHandle(timer);
}
#endif

static long get_time_ms(void) {
  struct timeval tv;
  gettimeofday(&tv, NULL);
  if (_t0 == 0) _t0 = tv.tv_sec;
  return (long)(tv.tv_sec-_t0)*1000 + (long)(tv.tv_usec)/1000;
}

static void sthandler(int sig) {
  void *array[10];
  size_t size;

  // get void*'s for all entries on the stack
  size = backtrace(array, 10);

  // print out all the frames to stderr
  fprintf(stderr, "Error: signal %d:\n", sig);
  backtrace_symbols_fd(array, size, STDERR_FILENO);
  exit(1);
}

//// gateway API

#define SUBLIST_LEN       1024
#define QUEUE_LEN         1024
#define BUFLEN            65536

#define PARAM_REQ         "org.arl.fjage.param.ParameterReq"
#define PARAM_TIMEOUT     1000

// poll_data() API return values
#define DATA_AVAILABLE      0
#define TIMED_OUT          -1
#define INTERRUPTED        -2

#define POLL_DELAY         10000  // us

typedef struct {
  int sockfd;
#ifdef USE_POLL
  int intfd[2];       // self-pipe used to break the poll on sockfd
#else
  int intr;
#endif
  fjage_aid_t aid;
  char sublist[SUBLIST_LEN];
  int buflen;
  char* buf;
  int head;
  int aid_count;
  fjage_aid_t* aids;
  fjage_msg_t mqueue[QUEUE_LEN];
  int mqueue_head;
  int mqueue_tail;
} _fjage_gw_t;

static int flush_interrupts(_fjage_gw_t* fgw) {
  int rv = 0;
#ifdef USE_POLL
  uint8_t dummy;
  while (read(fgw->intfd[0], &dummy, 1) > 0) rv++;
#else
  rv = fgw->intr;
  fgw->intr = 0;
#endif
  return rv;
}

static int interrupt(_fjage_gw_t* fgw) {
#ifdef USE_POLL
  uint8_t dummy = 1;
  if (write(fgw->intfd[1], &dummy, 1) < 0) return -1;
#else
  fgw->intr = 1;
#endif
  return 0;
}

static int poll_data(_fjage_gw_t* fgw, long timeout) {
#ifdef USE_POLL
  struct pollfd fds[2];
  memset(fds, 0, sizeof(fds));
  fds[0].fd = fgw->sockfd;
  fds[0].events = POLLIN;
  fds[1].fd = fgw->intfd[0];
  fds[1].events = POLLIN;
  int rv = poll(fds, 2, timeout);
  if (rv <= 0) return TIMED_OUT;
  if ((fds[1].revents & POLLIN) == 0) return DATA_AVAILABLE;
  flush_interrupts(fgw);
  return INTERRUPTED;
#else
  long t = get_time_ms();
  long t1 = t + timeout;
  while (t <= t1) {
  #ifdef USE_IOCTL
    int rv;
    ioctl(fgw->sockfd, FIONREAD, &rv);
    if (rv < 0) rv = 0;
  #else
    fd_set fds;
    FD_ZERO(&fds);
    FD_SET(fgw->sockfd, &fds);
    struct timeval tval;
    tval.tv_sec = 0;
    tval.tv_usec = POLL_DELAY;
    int rv = select(fgw->sockfd+1, &fds, NULL, NULL, &tval);
  #endif
    if (rv) return DATA_AVAILABLE;
    if (fgw->intr) {
      fgw->intr = 0;
      return INTERRUPTED;
    }
  #ifdef USE_IOCTL
    usleep(POLL_DELAY);
  #endif
    t = get_time_ms();
  }
  return TIMED_OUT;
#endif
}

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
  for (int i = fgw->mqueue_tail; i != fgw->mqueue_head && msg == NULL; i = (i + 1) % QUEUE_LEN) {
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

static bool mqueue_compare_any(const char* clazzt,  const char** clazzes, int clazzlen){
  for (int i=0;i<clazzlen;i++){
    if (strcmp(clazzes[i], clazzt) == 0) return true;
  }
  return false;
}

static fjage_msg_t mqueue_get_any(fjage_gw_t gw, const char** clazzes, int clazzlen) {
  if (gw == NULL) return NULL;
  _fjage_gw_t* fgw = gw;
  fjage_msg_t msg = NULL;
  if (clazzes == NULL || clazzlen < 1) return msg;
  for (int i = fgw->mqueue_tail; i != fgw->mqueue_head && msg == NULL; i = (i + 1) % QUEUE_LEN) {
    if (fgw->mqueue[i] != NULL) {
      const char* clazz1 = fjage_msg_get_clazz(fgw->mqueue[i]);
      if (clazz1 == NULL || ! mqueue_compare_any(clazz1, clazzes, clazzlen)) continue;
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
    if (id != NULL && id1 != NULL && !strcmp(id, id1)) {
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

/// @return -1 if interrupted, 0 otherwise
static int json_reader(fjage_gw_t gw, const char* id, long timeout) {
  if (gw == NULL) return -1;
  _fjage_gw_t* fgw = gw;
  long t0 = get_time_ms();
  int rv = poll_data(fgw, timeout);
  if (rv == DATA_AVAILABLE) {
    int n;
    bool done = false;
#ifdef _WIN32
    while ((n = recv(fgw->sockfd, fgw->buf + fgw->head, fgw->buflen - fgw->head, 0)) > 0) {
#else
    while ((n = read(fgw->sockfd, fgw->buf + fgw->head, fgw->buflen - fgw->head)) > 0) {
#endif
      int bol = 0;
      for (int i = fgw->head; i < fgw->head + n; i++) {
        if (fgw->buf[i] == '\n') {
          fgw->buf[i] = 0;
          done = json_process(gw, fgw->buf + bol, id);
          bol = i+1;
        }
      }
      fgw->head += n;
      if (fgw->head >= fgw->buflen) {
        char* p = realloc(fgw->buf, fgw->buflen+BUFLEN);
        if (p == NULL) fgw->head = 0; // overwrite old data if no memory
        else {
          fgw->buf = p;
          fgw->buflen += BUFLEN;
        }
      }
      if (bol > 0) {
        memmove(fgw->buf, fgw->buf + bol, fgw->head - bol);
        fgw->head -= bol;
      }
      if (done) break;
      long timeout1 = t0+timeout - get_time_ms();
      if (timeout1 <= 0) break;
      rv = poll_data(fgw, timeout1);
      if (rv != DATA_AVAILABLE) break;
    }
  }
  return (rv == INTERRUPTED) ? -1 : 0;
}

static void update_watch(_fjage_gw_t* fgw) {
  int i = 0;
  int n = 0;
  while (fgw->sublist[i]) {
    i += strlen(fgw->sublist+i)+1;
    n++;
  }
  char* s = (char*)malloc(i+1+2*n+strlen(fgw->aid)+3+47);
  if (s == NULL) {
    writes(fgw->sockfd, "{\"action\": \"wantsMessagesFor\", \"agentIDs\": []}\n");
    return;
  }
  sprintf(s, "{\"action\": \"wantsMessagesFor\", \"agentIDs\": [\"%s\"", fgw->aid);
  i = 0;
  while (fgw->sublist[i]) {
    strcat(s, ",\"");
    strcat(s, fgw->sublist+i);
    strcat(s, "\"");
    i += strlen(fgw->sublist+i)+1;
  }
  strcat(s, "]}\n");
  writes(fgw->sockfd, s);
  free(s);
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
#if defined(DEBUG) && ! defined(_WIN32)
  signal(SIGSEGV, sthandler);
#endif
#ifdef _WIN32
  WSADATA wsaData;
  if (WSAStartup(MAKEWORD(2,2), &wsaData) != 0) return NULL;
#endif
  _fjage_gw_t* fgw = calloc(1, sizeof(_fjage_gw_t));
  if (fgw == NULL) return NULL;
  fgw->buf = malloc(BUFLEN);
  if (fgw->buf == NULL) {
    free(fgw);
    return NULL;
  }
  fgw->buflen = BUFLEN;
  fgw->sockfd = socket(AF_INET, SOCK_STREAM, 0);
  if (fgw->sockfd < 0) {
    free(fgw->buf);
    free(fgw);
    return NULL;
  }
  struct hostent* server = gethostbyname(hostname);
  if (server == NULL) {
    close(fgw->sockfd);
    free(fgw->buf);
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
    free(fgw->buf);
    free(fgw);
    return NULL;
  }
#ifdef _WIN32
  u_long NONBLOCK_MODE = 1;
  ioctlsocket(fgw->sockfd, FIONBIO, &NONBLOCK_MODE);
#else
  signal(SIGPIPE, SIG_IGN);
  fcntl(fgw->sockfd, F_SETFL, O_NONBLOCK);
#endif

#ifdef USE_POLL
  if (pipe(fgw->intfd) < 0) {
    close(fgw->sockfd);
    free(fgw->buf);
    free(fgw);
    return NULL;
  }
  fcntl(fgw->intfd[0], F_SETFL, O_NONBLOCK);
#else
  fgw->intr = 0;
#endif
  char s[64];
  sprintf(s, "CGatewayAgent@%08x", rand());
  fgw->aid = fjage_aid_create(s);
  fgw->head = 0;
  update_watch(fgw);
  return fgw;
}

#ifndef _WIN32

fjage_gw_t fjage_rs232_open(const char* devname, int baud, const char* settings) {
  if (settings != NULL && strcmp(settings, "N81")) return NULL;
#if defined(DEBUG) && ! defined(_WIN32)
  signal(SIGSEGV, sthandler);
#endif
  switch (baud) {
    case 50:      baud = B50;     break;
    case 75:      baud = B75;     break;
    case 110:     baud = B110;    break;
    case 134:     baud = B134;    break;
    case 150:     baud = B150;    break;
    case 200:     baud = B200;    break;
    case 300:     baud = B300;    break;
    case 600:     baud = B600;    break;
    case 1200:    baud = B1200;   break;
    case 1800:    baud = B1800;   break;
    case 2400:    baud = B2400;   break;
    case 4800:    baud = B4800;   break;
    case 9600:    baud = B9600;   break;
    case 19200:   baud = B19200;  break;
    case 38400:   baud = B38400;  break;
    case 57600:   baud = B57600;  break;
    case 115200:  baud = B115200; break;
    case 230400:  baud = B230400; break;
    default:      return NULL;
  }
  _fjage_gw_t* fgw = calloc(1, sizeof(_fjage_gw_t));
  if (fgw == NULL) return NULL;
  fgw->buf = malloc(BUFLEN);
  if (fgw->buf == NULL) {
    free(fgw);
    return NULL;
  }
  fgw->buflen = BUFLEN;
  fgw->sockfd = open(devname, O_RDWR|O_NOCTTY|O_NONBLOCK);
  if (fgw->sockfd < 0) {
    free(fgw->buf);
    free(fgw);
    return NULL;
  }
  struct termios options;
  tcgetattr(fgw->sockfd, &options);
  cfmakeraw(&options);
  options.c_cflag = CS8|CREAD|CLOCAL;   // UART Config : N81
  options.c_cc[VMIN] = 1;
  options.c_cc[VTIME] = 1;
  cfsetspeed(&options, baud);
  tcsetattr(fgw->sockfd, TCSANOW, &options);
  tcflush(fgw->sockfd, TCIOFLUSH);
#ifdef USE_POLL
  if (pipe(fgw->intfd) < 0) {
    close(fgw->sockfd);
    free(fgw->buf);
    free(fgw);
    return NULL;
  }
  fcntl(fgw->intfd[0], F_SETFL, O_NONBLOCK);
#else
  fgw->intr = 0;
#endif
  char s[64];
  sprintf(s, "CGatewayAgent@%08x", rand());
  fgw->aid = fjage_aid_create(s);
  fgw->head = 0;
  return fgw;
}

int fjage_rs232_wakeup(const char* devname, int baud, const char* settings) {
  _fjage_gw_t* fgw = fjage_rs232_open(devname, baud, settings);
  if (fgw == NULL) return -1;
  char write_buffer = 'A';
  if (write(fgw->sockfd, &write_buffer, sizeof(char)) < 0) return -1;
  return 0;
}

#endif

int fjage_close(fjage_gw_t gw) {
  if (gw != NULL) {
    _fjage_gw_t* fgw = gw;
#ifdef _WIN32
    closesocket(fgw->sockfd);
#else
    close(fgw->sockfd);
#endif
#ifdef USE_POLL
    close(fgw->intfd[0]);
    close(fgw->intfd[1]);
#endif
    fjage_aid_destroy(fgw->aid);
    free(fgw->buf);
    for (int i = fgw->mqueue_tail; i != fgw->mqueue_head; i = (i + 1) % QUEUE_LEN)
      fjage_msg_destroy(fgw->mqueue[i]);
    free(fgw);
  }
#ifdef _WIN32
  WSACleanup();
#endif
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
  update_watch(fgw);
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
  update_watch((_fjage_gw_t*)gw);
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
      update_watch(gw);
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
  flush_interrupts(fgw);
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
  flush_interrupts(fgw);
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
  _fjage_gw_t* fgw = gw;
  long t0 = get_time_ms();
  long timeout1 = timeout;
  fjage_msg_t msg = mqueue_get(gw, clazz, id);
  flush_interrupts(fgw);
  while (msg == NULL && timeout1 > 0) {
    if (json_reader(gw, NULL, timeout1) < 0) break;
    msg = mqueue_get(gw, clazz, id);
    if (msg == NULL) timeout1 = t0+timeout - get_time_ms();
  }
  return msg;
}

fjage_msg_t fjage_receive_any(fjage_gw_t gw, const char** clazzes, int clazzlen, long timeout) {
    _fjage_gw_t* fgw = gw;
  long t0 = get_time_ms();
  long timeout1 = timeout;
  fjage_msg_t msg = mqueue_get_any(gw, clazzes, clazzlen);
  flush_interrupts(fgw);
  while (msg == NULL && timeout1 > 0) {
    if (json_reader(gw, NULL, timeout1) < 0) break;
    msg = mqueue_get_any(gw, clazzes, clazzlen);
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

int fjage_interrupt(fjage_gw_t gw) {
  if (gw == NULL) return -1;
  _fjage_gw_t* fgw = gw;
  if (interrupt(fgw) < 0) return -1;
  return 0;
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
  for (int i = 1; i < m->ntokens; i += 1+m->tokens[i].size) {
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
#ifdef _WIN32
      int rv = send(fd, p, n, 0);
#else
      int rv = write(fd, p, n);
#endif
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
  int skip = -1;
  for (int i = 1; i < m->ntokens-1; i += 2) {
    char* t = m->data + m->tokens[i].start;
    if (skip <= 0 && m->tokens[i].type == JSMN_STRING && (m->tokens[i+1].type == JSMN_STRING || m->tokens[i+1].type == JSMN_PRIMITIVE) && !strcmp(key, t)) {
      t = m->data + m->tokens[i+1].start;
      t[m->tokens[i+1].end-m->tokens[i+1].start] = 0;
      if (m->tokens[i+1].type == JSMN_PRIMITIVE && !strcmp(t, "null")) t = NULL;
      return t;
    }
    if (skip > 0) skip--;
    if (m->tokens[i+1].type == JSMN_OBJECT) {
      if (skip < 0) skip = 0;
      else skip += m->tokens[i+1].size;
    }
  }
  return NULL;
}

static const char* fjage_msg_get_data(fjage_msg_t msg, const char* key) {
  if (msg == NULL) return NULL;
  _fjage_msg_t* m = msg;
  int skip = -1;
  int acq = 0;
  for (int i = 1; i < m->ntokens-1; i += 2) {
    char* t = m->data + m->tokens[i].start;
    if (!acq && skip == 0 && m->tokens[i].type == JSMN_STRING && m->tokens[i+1].type == JSMN_OBJECT && !strcmp(t, key)) acq = 1;
    else if (acq && m->tokens[i+1].type == JSMN_STRING && !strcmp(t, "data")) {
      t = m->data + m->tokens[i+1].start;
      t[m->tokens[i+1].end-m->tokens[i+1].start] = 0;
      if (m->tokens[i+1].type == JSMN_PRIMITIVE && !strcmp(t, "null")) t = NULL;
      return t;
    }
    if (skip > 0) {
      skip--;
      if (skip == 0 && acq) return NULL;
    }
    if (m->tokens[i+1].type == JSMN_ARRAY) i += m->tokens[i+1].size;
    else if (m->tokens[i+1].type == JSMN_OBJECT) {
      if (skip < 0) skip = 0;
      else skip += m->tokens[i+1].size;
    }
  }
  return NULL;
}

static const int fjage_msg_get_array(fjage_msg_t msg, const char* key, const char* format, void* value, int sz, int maxlen) {
  if (msg == NULL) return -1;
  _fjage_msg_t* m = msg;
  int skip = -1;
  for (int i = 1; i < m->ntokens-1; i += 2) {
    char* t = m->data + m->tokens[i].start;
    if (skip == 0 && m->tokens[i].type == JSMN_STRING && m->tokens[i+1].type == JSMN_ARRAY && !strcmp(t, key)) {
      for (int j = 0; j < m->tokens[i+1].size && j < maxlen; j++) {
        char* tt = m->data + m->tokens[i+2+j].start;
        if (sz > 1) sscanf(tt, format, value);
        else {
          int x;
          sscanf(tt, "%d", &x);
          *((uint8_t*)value) = (uint8_t)(x & 0xff);
        }
#ifdef _WIN32
        ((uint8_t*)value) += sz;
#else
        value += sz;
#endif
      }
      return m->tokens[i+1].size;
    }
    if (skip > 0) skip--;
    if (m->tokens[i+1].type == JSMN_ARRAY) i += m->tokens[i+1].size;
    else if (m->tokens[i+1].type == JSMN_OBJECT) {
      if (skip < 0) skip = 0;
      else skip += m->tokens[i+1].size;
    }
  }
  return -1;
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
  int n = fjage_msg_get_array(msg, key, "%d", value, 1, maxlen);
  if (n >= 0) return n;
  const char* s = fjage_msg_get_string(msg, key);
  if (s == NULL) s = fjage_msg_get_data(msg, key);
  if (s == NULL || strlen(s) == 0) return -1;
  size_t buflen;
  void* buf = b64_decode_ex(s, strlen(s), &buflen);
  if (buf == NULL) return -1;
  if (buflen <= maxlen) memcpy(value, buf, buflen);
  free(buf);
  return buflen;
}

int fjage_msg_get_int_array(fjage_msg_t msg, const char* key, int32_t* value, int maxlen) {
  int n = fjage_msg_get_array(msg, key, "%d", value, sizeof(int), maxlen);
  if (n >= 0) return n;
  const char* s = fjage_msg_get_string(msg, key);
  if (s == NULL) s = fjage_msg_get_data(msg, key);
  if (s == NULL || strlen(s) == 0) return -1;
  size_t buflen;
  void* buf = b64_decode_ex(s, strlen(s), &buflen);
  if (buf == NULL) return -1;
  if (buflen <= maxlen*sizeof(int32_t)) memcpy(value, buf, buflen);
  free(buf);
  return buflen/sizeof(int32_t);
}

int fjage_msg_get_float_array(fjage_msg_t msg, const char* key, float* value, int maxlen) {
  int n = fjage_msg_get_array(msg, key, "%f", value, sizeof(float), maxlen);
  if (n >= 0) return n;
  const char* s = fjage_msg_get_string(msg, key);
  if (s == NULL) s = fjage_msg_get_data(msg, key);
  if (s == NULL || strlen(s) == 0) return -1;
  size_t buflen;
  void* buf = b64_decode_ex(s, strlen(s), &buflen);
  if (buf == NULL) return -1;
  if (buflen <= maxlen*sizeof(float)) memcpy(value, buf, buflen);
  free(buf);
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

static void fjage_msg_add_b64(fjage_msg_t msg, const char* key, const char* value, const char* clazz) {
  char* s = msg_append(msg, strlen(key)+strlen(clazz)+strlen(value)+32);
  if (s != NULL) sprintf(s, "\"%s\": {\"clazz\": \"%s\", \"data\": \"%s\"}, ", key, clazz, value);
}

void fjage_msg_add_byte_array(fjage_msg_t msg, const char* key, uint8_t* value, int len) {
  char* s = b64_encode((unsigned char*)value, len);
  if (s == NULL) return;
  fjage_msg_add_b64(msg, key, s, "[B");
  free(s);
}

void fjage_msg_add_int_array(fjage_msg_t msg, const char* key, int32_t* value, int len) {
  char* s = b64_encode((unsigned char*)value, len*sizeof(int32_t));
  if (s == NULL) return;
  fjage_msg_add_b64(msg, key, s, "[I");
  free(s);
}

void fjage_msg_add_float_array(fjage_msg_t msg, const char* key, float* value, int len) {
  char* s = b64_encode((unsigned char*)value, len*sizeof(float));
  if (s == NULL) return;
  fjage_msg_add_b64(msg, key, s, "[F");
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

int fjage_param_get_int(fjage_gw_t gw, fjage_aid_t aid, const char* param, int ndx, int defval) {
  int v = defval;
  fjage_msg_t msg = fjage_msg_create(PARAM_REQ, FJAGE_REQUEST);
  fjage_msg_set_recipient(msg, aid);
  fjage_msg_add_int(msg, "index", ndx);
  fjage_msg_add_string(msg, "param", param);
  msg = fjage_request(gw, msg, PARAM_TIMEOUT);
  if (msg != NULL) {
    if (fjage_msg_get_performative(msg) == FJAGE_INFORM) v = fjage_msg_get_int(msg, "value", defval);
    fjage_msg_destroy(msg);
  }
  return v;
}

long fjage_param_get_long(fjage_gw_t gw, fjage_aid_t aid, const char* param, int ndx, long defval) {
  long v = defval;
  fjage_msg_t msg = fjage_msg_create(PARAM_REQ, FJAGE_REQUEST);
  fjage_msg_set_recipient(msg, aid);
  fjage_msg_add_int(msg, "index", ndx);
  fjage_msg_add_string(msg, "param", param);
  msg = fjage_request(gw, msg, PARAM_TIMEOUT);
  if (msg != NULL) {
    if (fjage_msg_get_performative(msg) == FJAGE_INFORM) v = fjage_msg_get_long(msg, "value", defval);
    fjage_msg_destroy(msg);
  }
  return v;
}

float fjage_param_get_float(fjage_gw_t gw, fjage_aid_t aid, const char* param, int ndx, float defval) {
  float v = defval;
  fjage_msg_t msg = fjage_msg_create(PARAM_REQ, FJAGE_REQUEST);
  fjage_msg_set_recipient(msg, aid);
  fjage_msg_add_int(msg, "index", ndx);
  fjage_msg_add_string(msg, "param", param);
  msg = fjage_request(gw, msg, PARAM_TIMEOUT);
  if (msg != NULL) {
    if (fjage_msg_get_performative(msg) == FJAGE_INFORM) v = fjage_msg_get_float(msg, "value", defval);
    fjage_msg_destroy(msg);
  }
  return v;
}

bool fjage_param_get_bool(fjage_gw_t gw, fjage_aid_t aid, const char* param, int ndx, bool defval) {
  bool v = defval;
  fjage_msg_t msg = fjage_msg_create(PARAM_REQ, FJAGE_REQUEST);
  fjage_msg_set_recipient(msg, aid);
  fjage_msg_add_int(msg, "index", ndx);
  fjage_msg_add_string(msg, "param", param);
  msg = fjage_request(gw, msg, PARAM_TIMEOUT);
  if (msg != NULL) {
    if (fjage_msg_get_performative(msg) == FJAGE_INFORM) v = fjage_msg_get_bool(msg, "value", defval);
    fjage_msg_destroy(msg);
  }
  return v;
}

int fjage_param_get_string(fjage_gw_t gw, fjage_aid_t aid, const char* param, int ndx, const char* strval, int len) {
  int rv = -1;
  fjage_msg_t msg = fjage_msg_create(PARAM_REQ, FJAGE_REQUEST);
  fjage_msg_set_recipient(msg, aid);
  fjage_msg_add_int(msg, "index", ndx);
  fjage_msg_add_string(msg, "param", param);
  msg = fjage_request(gw, msg, PARAM_TIMEOUT);
  if (msg != NULL) {
    if (fjage_msg_get_performative(msg) == FJAGE_INFORM) {
      const char* v = fjage_msg_get_string(msg, "value");
      if (v != NULL) rv = strlcpy((char*)strval, v, len);
    }
    fjage_msg_destroy(msg);
  }
  return rv;
}

int fjage_param_set_int(fjage_gw_t gw, fjage_aid_t aid, const char* param, int value, int ndx) {
  int rv = -1;
  fjage_msg_t msg = fjage_msg_create(PARAM_REQ, FJAGE_REQUEST);
  fjage_msg_set_recipient(msg, aid);
  fjage_msg_add_int(msg, "index", ndx);
  fjage_msg_add_string(msg, "param", param);
  fjage_msg_add_int(msg, "value", value);
  msg = fjage_request(gw, msg, PARAM_TIMEOUT);
  if (msg != NULL) {
    if (fjage_msg_get_performative(msg) == FJAGE_INFORM) rv = 0;
    fjage_msg_destroy(msg);
  }
  return rv;
}

int fjage_param_set_long(fjage_gw_t gw, fjage_aid_t aid, const char* param, long value, int ndx) {
  int rv = -1;
  fjage_msg_t msg = fjage_msg_create(PARAM_REQ, FJAGE_REQUEST);
  fjage_msg_set_recipient(msg, aid);
  fjage_msg_add_int(msg, "index", ndx);
  fjage_msg_add_string(msg, "param", param);
  fjage_msg_add_long(msg, "value", value);
  msg = fjage_request(gw, msg, PARAM_TIMEOUT);
  if (msg != NULL) {
    if (fjage_msg_get_performative(msg) == FJAGE_INFORM) rv = 0;
    fjage_msg_destroy(msg);
  }
  return rv;
}

int fjage_param_set_float(fjage_gw_t gw, fjage_aid_t aid, const char* param, float value, int ndx) {
  int rv = -1;
  fjage_msg_t msg = fjage_msg_create(PARAM_REQ, FJAGE_REQUEST);
  fjage_msg_set_recipient(msg, aid);
  fjage_msg_add_int(msg, "index", ndx);
  fjage_msg_add_string(msg, "param", param);
  fjage_msg_add_float(msg, "value", value);
  msg = fjage_request(gw, msg, PARAM_TIMEOUT);
  if (msg != NULL) {
    if (fjage_msg_get_performative(msg) == FJAGE_INFORM) rv = 0;
    fjage_msg_destroy(msg);
  }
  return rv;
}

int fjage_param_set_bool(fjage_gw_t gw, fjage_aid_t aid, const char* param, bool value, int ndx) {
  int rv = -1;
  fjage_msg_t msg = fjage_msg_create(PARAM_REQ, FJAGE_REQUEST);
  fjage_msg_set_recipient(msg, aid);
  fjage_msg_add_int(msg, "index", ndx);
  fjage_msg_add_string(msg, "param", param);
  fjage_msg_add_bool(msg, "value", value);
  msg = fjage_request(gw, msg, PARAM_TIMEOUT);
  if (msg != NULL) {
    if (fjage_msg_get_performative(msg) == FJAGE_INFORM) rv = 0;
    fjage_msg_destroy(msg);
  }
  return rv;
}

int fjage_param_set_string(fjage_gw_t gw, fjage_aid_t aid, const char* param, const char* value, int ndx) {
  int rv = -1;
  fjage_msg_t msg = fjage_msg_create(PARAM_REQ, FJAGE_REQUEST);
  fjage_msg_set_recipient(msg, aid);
  fjage_msg_add_int(msg, "index", ndx);
  fjage_msg_add_string(msg, "param", param);
  fjage_msg_add_string(msg, "value", value);
  msg = fjage_request(gw, msg, PARAM_TIMEOUT);
  if (msg != NULL) {
    if (fjage_msg_get_performative(msg) == FJAGE_INFORM) rv = 0;
    fjage_msg_destroy(msg);
  }
  return rv;
}
