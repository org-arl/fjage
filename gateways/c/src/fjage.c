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
#include "fjson.h"
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

static fjage_msg_t msg_from_doc(json_doc_t* doc, int obj);
static void fjage_msg_set_sender(fjage_msg_t msg, fjage_aid_t aid);
static long get_time_ms(void);
#if defined(__GNUC__) && !defined(_WIN32)
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

static void generate_uuid7(char *uuid){
    // The UUID is 16 bytes (128 bits)
    uint8_t uuid_bytes[16];

    // 1. Get the 48-bit timestamp
    long timestamp_ms = get_time_ms();

    // 2. Get random bytes
    uint8_t random_bytes[10];
    for (int i = 0; i < 10; ++i) {
        random_bytes[i] = rand() % 256;
    }

    // 3. Populate the UUID bytes according to the UUIDv7 spec

    // First 6 bytes are the 48-bit timestamp
    uuid_bytes[0] = (uint8_t)(timestamp_ms >> 40);
    uuid_bytes[1] = (uint8_t)(timestamp_ms >> 32);
    uuid_bytes[2] = (uint8_t)(timestamp_ms >> 24);
    uuid_bytes[3] = (uint8_t)(timestamp_ms >> 16);
    uuid_bytes[4] = (uint8_t)(timestamp_ms >> 8);
    uuid_bytes[5] = (uint8_t)timestamp_ms;

    // Next 2 bytes (6 and 7) for version and rand_a
    // Set version to 7 (0111)
    uuid_bytes[6] = 0x70 | (random_bytes[0] & 0x0F);
    uuid_bytes[7] = random_bytes[1];

    // Next 8 bytes for variant and rand_b
    // Set variant to '10'
    uuid_bytes[8] = 0x80 | (random_bytes[2] & 0x3F);
    memcpy(&uuid_bytes[9], &random_bytes[3], 7);

    // 4. Format the bytes into a standard UUID string
    snprintf(uuid, 37, "%02x%02x%02x%02x-%02x%02x-%02x%02x-%02x%02x-%02x%02x%02x%02x%02x%02x",
             uuid_bytes[0], uuid_bytes[1], uuid_bytes[2], uuid_bytes[3],
             uuid_bytes[4], uuid_bytes[5], uuid_bytes[6], uuid_bytes[7],
             uuid_bytes[8], uuid_bytes[9], uuid_bytes[10], uuid_bytes[11],
             uuid_bytes[12], uuid_bytes[13], uuid_bytes[14], uuid_bytes[15]);
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

/// Write all n bytes to a (possibly non-blocking) fd, retrying short writes.
/// @return 0 on success, -1 on error
static int write_all(int fd, const char* p, size_t n) {
  while (n > 0) {
#ifdef _WIN32
    int rv = send(fd, p, (int)n, 0);
    if (rv == SOCKET_ERROR) {
      if (WSAGetLastError() != WSAEWOULDBLOCK) return -1;
      usleep(10000);
      continue;
    }
#else
    ssize_t rv = write(fd, p, n);
    if (rv < 0) {
      if (errno != EAGAIN && errno != EWOULDBLOCK && errno != EINTR) return -1;
      usleep(10000);
      continue;
    }
#endif
    p += rv;
    n -= rv;
  }
  return 0;
}

/// Write the buffer to fd and release it.
/// @return 0 on success, -1 on error
static int jbuf_flush(jbuf_t* b, int fd) {
  int rv = b->err ? -1 : write_all(fd, b->s, b->len);
  free(b->s);
  b->s = NULL;
  b->len = b->cap = 0;
  b->err = false;
  return rv;
}

static bool msg_write_json(jbuf_t* b, fjage_msg_t msg, fjage_aid_t sender);

static long get_time_ms(void) {
  struct timeval tv;
  gettimeofday(&tv, NULL);
  if (_t0 == 0) _t0 = tv.tv_sec;
  return (long)(tv.tv_sec-_t0)*1000 + (long)(tv.tv_usec)/1000;
}

#ifndef _WIN32
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
#endif

//// gateway API

#define SUBLIST_LEN       1024
#define QUEUE_LEN         1024
#define BUFLEN            65536

#define PARAM_REQ         "org.arl.fjage.param.ParameterReq"
#define DIRECTORY_QUERY_TIMEOUT 6000
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

// Handle the response to a directory query.
static bool json_directory_rsp(_fjage_gw_t* fgw, const json_doc_t* d) {
  const char* rsp = json_str(d, json_find(d, 0, "inResponseTo"));
  if (rsp == NULL || fgw->aid_count != 0) return true;
  if (!strcmp(rsp, "agentForService")) {
    const char* s = json_str(d, json_find(d, 0, "agentID"));
    if (s != NULL) {
      fgw->aids = (fjage_aid_t*)fjage_aid_create(s);
      if (fgw->aids != NULL) fgw->aid_count = 1;
    }
  } else if (!strcmp(rsp, "agentsForService")) {
    int i = json_find(d, 0, "agentIDs");
    int n = json_type(d, i) == JSMN_ARRAY ? json_size(d, i) : 0;
    if (n == 0) return true;
    fgw->aids = malloc(n*sizeof(fjage_aid_t));
    if (fgw->aids == NULL) return true;
    int end = json_next(d, i);
    for (int j = i+1; j < end && fgw->aid_count < n; j = json_next(d, j)) {
      const char* s = json_str(d, j);
      if (s != NULL) fgw->aids[fgw->aid_count++] = fjage_aid_create(s);
    }
    if (fgw->aid_count == 0) {
      free(fgw->aids);
      fgw->aids = NULL;
    }
  }
  return true;
}

// Handle one line from the master container. Returns true if it was the
// response to request id, or a queued message when id is NULL.
static bool json_process(fjage_gw_t gw, const char* line, const char* id) {
  if (gw == NULL) return false;
  _fjage_gw_t* fgw = gw;
  json_doc_t d;
  if (!json_parse(&d, line)) return false;
  bool rv = false;
  const char* action = json_str(&d, json_find(&d, 0, "action"));
  const char* id1 = json_str(&d, json_find(&d, 0, "id"));
  if (action == NULL) {
    if (id != NULL && id1 != NULL && !strcmp(id, id1)) rv = json_directory_rsp(fgw, &d);
  } else if (!strcmp(action, "send")) {
    fjage_msg_t msg = msg_from_doc(&d, json_find(&d, 0, "message"));
    fjage_aid_t rcpt = fjage_msg_get_recipient(msg);
    if (rcpt != NULL && (!strcmp(rcpt, fgw->aid) || fjage_is_subscribed(fgw, rcpt))) {
      mqueue_put(gw, msg);
      if (id == NULL) rv = true;
    } else fjage_msg_destroy(msg);
  } else if (id1 != NULL) {
    jbuf_t b = {0};
    jbuf_puts(&b, "{\"id\": ");
    jbuf_str(&b, id1);
    jbuf_puts(&b, ", \"inResponseTo\": ");
    jbuf_str(&b, action);
    jbuf_puts(&b, ", \"answer\": false}\n");
    jbuf_flush(&b, fgw->sockfd);
  }
  json_free(&d);
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
  jbuf_t b = {0};
  jbuf_puts(&b, "{\"action\": \"wantsMessagesFor\", \"agentIDs\": [");
  jbuf_str(&b, fgw->aid);
  for (int i = 0; fgw->sublist[i]; i += strlen(fgw->sublist+i)+1) {
    jbuf_puts(&b, ", ");
    jbuf_str(&b, fgw->sublist+i);
  }
  jbuf_puts(&b, "]}\n");
  jbuf_flush(&b, fgw->sockfd);
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
  srand(get_time_ms());
  char s[64];
  sprintf(s, "gateway-%08x", rand());
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
  srand(get_time_ms());
  char s[64];
  sprintf(s, "gateway-%08x", rand());
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

/// Send a directory query for a service, generating its request ID into uuid.
/// @return 0 on success, -1 on error
static int directory_query(_fjage_gw_t* fgw, const char* action, char* uuid, const char* service) {
  generate_uuid7(uuid);
  jbuf_t b = {0};
  jbuf_puts(&b, "{\"action\": ");
  jbuf_str(&b, action);
  jbuf_puts(&b, ", \"id\": ");
  jbuf_str(&b, uuid);
  jbuf_puts(&b, ", \"service\": ");
  jbuf_str(&b, service);
  jbuf_puts(&b, "}\n");
  return jbuf_flush(&b, fgw->sockfd);
}

fjage_aid_t fjage_agent_for_service(fjage_gw_t gw, const char* service)  {
  if (gw == NULL) return NULL;
  _fjage_gw_t* fgw = gw;
  char uuid[UUID_LEN+1];
  if (directory_query(fgw, "agentForService", uuid, service) < 0) return NULL;
  fgw->aid_count = 0;
  fgw->aids = NULL;
  flush_interrupts(fgw);
  if (json_reader(gw, uuid, DIRECTORY_QUERY_TIMEOUT) < 0) return NULL;
  if (fgw->aid_count == 0) return NULL;
  return (fjage_aid_t)(fgw->aids);
}

int fjage_agents_for_service(fjage_gw_t gw, const char* service, fjage_aid_t* agents, int max) {
  if (gw == NULL) return -1;
  _fjage_gw_t* fgw = gw;
  char uuid[UUID_LEN+1];
  if (directory_query(fgw, "agentsForService", uuid, service) < 0) return 0;
  fgw->aid_count = 0;
  fgw->aids = NULL;
  flush_interrupts(fgw);
  if (json_reader(gw, uuid, DIRECTORY_QUERY_TIMEOUT) < 0) return 0;
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
  jbuf_t b = {0};
  jbuf_puts(&b, "{\"action\": \"send\", \"relay\": true, \"message\": ");
  bool ok = msg_write_json(&b, msg, fgw->aid);
  jbuf_puts(&b, "}\n");
  fjage_msg_destroy(msg);
  if (!ok) {
    free(b.s);
    return -1;
  }
  return jbuf_flush(&b, fgw->sockfd);
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

// A message is write-only when created locally, and read-only (doc.js != NULL)
// when parsed from JSON.
typedef struct {
  char id[UUID_LEN+1];
  char clazz[CLAZZ_LEN+1];
  fjage_perf_t perf;
  fjage_aid_t sender;
  fjage_aid_t recipient;
  char in_reply_to[UUID_LEN+1];
  jbuf_t body;          // fields added so far, each as `, "key": value`
  json_doc_t doc;       // parsed JSON line holding the message
  int data_tok;         // token of the "data" object in doc
} _fjage_msg_t;

// Performative names, indexed by fjage_perf_t.
static const char* perf_names[] = {
  NULL, "REQUEST", "AGREE", "REFUSE", "FAILURE", "INFORM", "CONFIRM", "DISCONFIRM",
  "QUERY_IF", "NOT_UNDERSTOOD", "CFP", "PROPOSE", "CANCEL"
};

// Start a new field in a write-only message, returning the buffer to write its value to.
static jbuf_t* msg_field(fjage_msg_t msg, const char* key) {
  if (msg == NULL || key == NULL) return NULL;
  _fjage_msg_t* m = msg;
  if (m->doc.js != NULL) return NULL;
  jbuf_puts(&m->body, ", ");
  jbuf_str(&m->body, key);
  jbuf_puts(&m->body, ": ");
  return &m->body;
}

/// Write a write-only message as JSON, with the given sender.
/// @return false if the message is read-only or could not be built in full
static bool msg_write_json(jbuf_t* b, fjage_msg_t msg, fjage_aid_t sender) {
  if (msg == NULL) return false;
  _fjage_msg_t* m = msg;
  if (m->doc.js != NULL || m->body.err) return false;
  jbuf_puts(b, "{\"clazz\": ");
  jbuf_str(b, m->clazz);
  jbuf_puts(b, ", \"data\": {\"msgID\": ");
  jbuf_str(b, m->id);
  if (m->perf > FJAGE_NONE && m->perf <= FJAGE_CANCEL) {
    jbuf_puts(b, ", \"perf\": ");
    jbuf_str(b, perf_names[m->perf]);
  }
  if (m->recipient != NULL) {
    jbuf_puts(b, ", \"recipient\": ");
    jbuf_str(b, m->recipient);
  }
  if (m->in_reply_to[0]) {
    jbuf_puts(b, ", \"inReplyTo\": ");
    jbuf_str(b, m->in_reply_to);
  }
  jbuf_puts(b, ", \"sender\": ");
  jbuf_str(b, sender);
  if (m->body.len > 0) jbuf_raw(b, m->body.s, m->body.len);
  jbuf_puts(b, "}}");
  return !b->err;
}

fjage_msg_t fjage_msg_create(const char* clazz, fjage_perf_t perf) {
  _fjage_msg_t* m = malloc(sizeof(_fjage_msg_t));
  if (m == NULL) return NULL;
  generate_uuid7(m->id);
  memset(m->clazz, 0, CLAZZ_LEN+1);
  if (clazz != NULL) strncpy(m->clazz, clazz, CLAZZ_LEN);
  m->perf = perf;
  m->recipient = NULL;
  m->sender = NULL;
  m->in_reply_to[0] = 0;
  m->body = (jbuf_t){0};
  m->doc = (json_doc_t){0};
  m->data_tok = -1;
  return m;
}

// Make a read-only message from object obj of doc, {"clazz": ..., "data": {...}},
// taking over the doc. Returns NULL if obj is not a message.
static fjage_msg_t msg_from_doc(json_doc_t* doc, int obj) {
  _fjage_msg_t* m = fjage_msg_create(NULL, FJAGE_NONE);
  if (m == NULL) {
    json_free(doc);
    return NULL;
  }
  m->doc = *doc;
  *doc = (json_doc_t){0};
  const json_doc_t* d = &m->doc;
  int data = json_find(d, obj, "data");
  if (json_type(d, data) != JSMN_OBJECT) {
    fjage_msg_destroy(m);
    return NULL;
  }
  m->data_tok = data;
  const char* s = json_str(d, json_find(d, obj, "clazz"));
  if (s != NULL) strncpy(m->clazz, s, CLAZZ_LEN);
  if ((s = json_str(d, json_find(d, data, "msgID"))) != NULL) strncpy(m->id, s, UUID_LEN);
  fjage_msg_set_in_reply_to(m, json_str(d, json_find(d, data, "inReplyTo")));
  fjage_msg_set_sender(m, (fjage_aid_t)json_str(d, json_find(d, data, "sender")));
  fjage_msg_set_recipient(m, (fjage_aid_t)json_str(d, json_find(d, data, "recipient")));
  if ((s = json_str(d, json_find(d, data, "perf"))) != NULL)
    for (int p = FJAGE_REQUEST; p <= FJAGE_CANCEL; p++)
      if (!strcmp(s, perf_names[p])) m->perf = (fjage_perf_t)p;
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

// Parsed JSON of a read-only message, or NULL.
static const json_doc_t* msg_doc(fjage_msg_t msg) {
  _fjage_msg_t* m = msg;
  return (m != NULL && m->doc.js != NULL) ? &m->doc : NULL;
}

// Header fields, which have their own getters.
static const char* header_keys[] = { "msgID", "perf", "sender", "recipient", "inReplyTo", NULL };

// Token of the value of a field in a read-only message, or -1 if absent or a
// header field.
static int msg_find(fjage_msg_t msg, const char* key) {
  const json_doc_t* d = msg_doc(msg);
  if (d == NULL || key == NULL) return -1;
  for (const char** h = header_keys; *h != NULL; h++)
    if (!strcmp(key, *h)) return -1;
  return json_find(d, ((_fjage_msg_t*)msg)->data_tok, key);
}

const char* fjage_msg_get_string(fjage_msg_t msg, const char* key) {
  int i = msg_find(msg, key);
  return i < 0 ? NULL : json_text(msg_doc(msg), i);
}

// Base64 data of an array field encoded as {"clazz": ..., "data": ...}.
static const char* fjage_msg_get_data(fjage_msg_t msg, const char* key) {
  const json_doc_t* d = msg_doc(msg);
  int i = msg_find(msg, key);
  return i < 0 ? NULL : json_str(d, json_find(d, i, "data"));
}

// Read an array field encoded as a JSON list into value, sz bytes per element.
static int fjage_msg_get_array(fjage_msg_t msg, const char* key, const char* format, void* value, int sz, int maxlen) {
  const json_doc_t* d = msg_doc(msg);
  int i = msg_find(msg, key);
  if (i < 0 || json_type(d, i) != JSMN_ARRAY) return -1;
  uint8_t* p = value;
  int n = 0;
  int end = json_next(d, i);
  for (int j = i+1; j < end; j = json_next(d, j), n++) {
    if (p == NULL || n >= maxlen) continue;
    const char* t = json_text(d, j);
    if (t == NULL) t = "";
    if (sz > 1) sscanf(t, format, p+n*sz);
    else {
      int x = 0;
      sscanf(t, "%d", &x);
      p[n] = (uint8_t)(x & 0xff);
    }
  }
  return n;
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

int fjage_msg_get_int_array_2d(fjage_msg_t msg, const char* key, int32_t* value, int maxlen, int* lengths, int maxrows) {
  const json_doc_t* d = msg_doc(msg);
  int i = msg_find(msg, key);
  if (i < 0 || json_type(d, i) != JSMN_ARRAY) return -1;
  int total = 0;
  int row = 0;
  int end = json_next(d, i);
  for (int r = i+1; r < end; r = json_next(d, r), row++) {
    int rowlen = 0;
    if (json_type(d, r) == JSMN_ARRAY) {
      int rend = json_next(d, r);
      for (int e = r+1; e < rend; e = json_next(d, e), rowlen++) {
        const char* t = json_text(d, e);
        if (value != NULL && total+rowlen < maxlen) {
          int x = 0;
          if (t != NULL) sscanf(t, "%d", &x);
          value[total+rowlen] = x;
        }
      }
    } else if (json_type(d, r) == JSMN_OBJECT) {
      const char* s = json_str(d, json_find(d, r, "data"));
      if (s == NULL) return -1;
      size_t buflen;
      void* buf = b64_decode_ex(s, strlen(s), &buflen);
      if (buf == NULL || buflen % sizeof(int32_t) != 0) {
        free(buf);
        return -1;
      }
      rowlen = buflen/sizeof(int32_t);
      if (value != NULL && total < maxlen) {
        int copylen = rowlen;
        if (total+copylen > maxlen) copylen = maxlen-total;
        memcpy(value+total, buf, copylen*sizeof(int32_t));
      }
      free(buf);
    } else return -1;
    if (lengths != NULL && row < maxrows) lengths[row] = rowlen;
    total += rowlen;
  }
  return total;
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
  jbuf_t* b = msg_field(msg, key);
  if (b != NULL) jbuf_str(b, value);
}

void fjage_msg_add_int(fjage_msg_t msg, const char* key, int value) {
  jbuf_t* b = msg_field(msg, key);
  if (b != NULL) jbuf_printf(b, "%d", value);
}

void fjage_msg_add_int_array_2d(fjage_msg_t msg, const char* key, int32_t* values, int vallen, int* lengths, int lenlen) {
  jbuf_t* b = msg_field(msg, key);
  if (b == NULL) return;
  jbuf_puts(b, "[");
  int vndx = 0;
  for (int i = 0; i < lenlen; i++) {
    jbuf_puts(b, i > 0 ? ",[" : "[");
    for (int j = 0; j < lengths[i] && vndx < vallen; j++)
      jbuf_printf(b, j > 0 ? ",%d" : "%d", (int)values[vndx++]);
    jbuf_puts(b, "]");
  }
  jbuf_puts(b, "]");
}

void fjage_msg_add_long(fjage_msg_t msg, const char* key, long value) {
  jbuf_t* b = msg_field(msg, key);
  if (b != NULL) jbuf_printf(b, "%ld", value);
}

void fjage_msg_add_float(fjage_msg_t msg, const char* key, float value) {
  jbuf_t* b = msg_field(msg, key);
  if (b != NULL) jbuf_float(b, value);
}

void fjage_msg_add_bool(fjage_msg_t msg, const char* key, bool value) {
  jbuf_t* b = msg_field(msg, key);
  if (b != NULL) jbuf_puts(b, value ? "true" : "false");
}

// Add an array as {"clazz": clazz, "data": base64}.
static void fjage_msg_add_b64(fjage_msg_t msg, const char* key, const void* data, size_t len, const char* clazz) {
  jbuf_t* b = msg_field(msg, key);
  if (b == NULL) return;
  char* s = b64_encode((const unsigned char*)data, len);
  if (s == NULL) {
    b->err = true;
    return;
  }
  jbuf_puts(b, "{\"clazz\": ");
  jbuf_str(b, clazz);
  jbuf_puts(b, ", \"data\": ");
  jbuf_str(b, s);
  jbuf_puts(b, "}");
  free(s);
}

void fjage_msg_add_byte_array(fjage_msg_t msg, const char* key, uint8_t* value, int len) {
  fjage_msg_add_b64(msg, key, value, len, "[B");
}

void fjage_msg_add_int_array(fjage_msg_t msg, const char* key, int32_t* value, int len) {
  fjage_msg_add_b64(msg, key, value, len*sizeof(int32_t), "[I");
}

void fjage_msg_add_float_array(fjage_msg_t msg, const char* key, float* value, int len) {
  fjage_msg_add_b64(msg, key, value, len*sizeof(float), "[F");
}

void fjage_msg_destroy(fjage_msg_t msg) {
  if (msg == NULL) return;
  _fjage_msg_t* m = msg;
  free(m->sender);
  free(m->recipient);
  free(m->body.s);
  json_free(&m->doc);
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

int fjage_param_get_string(fjage_gw_t gw, fjage_aid_t aid, const char* param, int ndx, char* strval, int len) {
  int rv = -1;
  fjage_msg_t msg = fjage_msg_create(PARAM_REQ, FJAGE_REQUEST);
  fjage_msg_set_recipient(msg, aid);
  fjage_msg_add_int(msg, "index", ndx);
  fjage_msg_add_string(msg, "param", param);
  msg = fjage_request(gw, msg, PARAM_TIMEOUT);
  if (msg != NULL) {
    if (fjage_msg_get_performative(msg) == FJAGE_INFORM) {
      const char* v = fjage_msg_get_string(msg, "value");
      if (v != NULL){
        if (strval != NULL && len > 0) {
          strncpy(strval, v, len);
          rv = strlen(v) > len ? len : strlen(v);
        }
        else rv = strlen(v);
      }
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
