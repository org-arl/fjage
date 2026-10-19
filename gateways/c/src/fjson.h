// JSON helpers for fjage.c. All functions are static, so this header is
// compiled into fjage.c rather than built as a separate source file.

#ifndef _FJSON_H_
#define _FJSON_H_

#include <stdarg.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>

//// output

/// Growable, NUL-terminated output buffer. An allocation failure sets err, and
/// all further writes to the buffer are ignored. Start from a zeroed buffer, and
/// free s when done.
typedef struct {
  char* s;                ///< Text, or NULL before the first write.
  size_t len;             ///< Length of the text.
  size_t cap;             ///< Allocated size of s.
  bool err;               ///< Set if an allocation failed.
} jbuf_t;

/// Make room for n more chars, plus a NUL, in a buffer.
///
/// @param b              Buffer
/// @param n              Number of chars
/// @return               Where to write the chars, or NULL if the buffer is in error

static char* jbuf_reserve(jbuf_t* b, size_t n) {
  if (b->err) return NULL;
  if (b->len+n >= b->cap) {
    size_t cap = b->cap > 0 ? b->cap : 256;
    while (b->len+n >= cap) cap *= 2;
    char* s = realloc(b->s, cap);
    if (s == NULL) {
      b->err = true;
      return NULL;
    }
    b->s = s;
    b->cap = cap;
  }
  return b->s+b->len;
}

/// Append chars to a buffer as they are.
///
/// @param b              Buffer
/// @param s              Chars to append
/// @param n              Number of chars

static void jbuf_raw(jbuf_t* b, const char* s, size_t n) {
  char* p = jbuf_reserve(b, n);
  if (p == NULL) return;
  memcpy(p, s, n);
  b->len += n;
  b->s[b->len] = 0;
}

/// Append a string to a buffer as it is.
///
/// @param b              Buffer
/// @param s              NUL-terminated string

static void jbuf_puts(jbuf_t* b, const char* s) {
  jbuf_raw(b, s, strlen(s));
}

/// Append printf-formatted text to a buffer.
///
/// @param b              Buffer
/// @param fmt            printf format
/// @param ...            Format arguments

static void jbuf_printf(jbuf_t* b, const char* fmt, ...) {
  va_list ap;
  va_start(ap, fmt);
  int n = vsnprintf(NULL, 0, fmt, ap);
  va_end(ap);
  if (n < 0) {
    b->err = true;
    return;
  }
  char* p = jbuf_reserve(b, n);
  if (p == NULL) return;
  va_start(ap, fmt);
  vsnprintf(p, n+1, fmt, ap);
  va_end(ap);
  b->len += n;
}

/// Append a string as a quoted, escaped JSON string. Control chars, quotes and
/// backslashes are escaped, and other bytes are written as they are.
///
/// @param b              Buffer
/// @param s              String, or NULL to write null

static void jbuf_str(jbuf_t* b, const char* s) {
  if (s == NULL) {
    jbuf_puts(b, "null");
    return;
  }
  jbuf_raw(b, "\"", 1);
  const char* run = s;
  for (const char* p = s; ; p++) {
    unsigned char c = *p;
    if (c >= 0x20 && c != '"' && c != '\\') continue;
    jbuf_raw(b, run, p-run);
    if (c == 0) break;
    switch (c) {
      case '"':  jbuf_puts(b, "\\\""); break;
      case '\\': jbuf_puts(b, "\\\\"); break;
      case '\b': jbuf_puts(b, "\\b");  break;
      case '\f': jbuf_puts(b, "\\f");  break;
      case '\n': jbuf_puts(b, "\\n");  break;
      case '\r': jbuf_puts(b, "\\r");  break;
      case '\t': jbuf_puts(b, "\\t");  break;
      default:   jbuf_printf(b, "\\u%04x", c);
    }
    run = p+1;
  }
  jbuf_raw(b, "\"", 1);
}

/// Append a float using the fewest digits that read back as the same value. NaN and
/// infinities are written as null and +/-Infinity, as the Java side does.
///
/// @param b              Buffer
/// @param v              Value

static void jbuf_float(jbuf_t* b, float v) {
  if (isnan(v)) jbuf_puts(b, "null");
  else if (isinf(v)) jbuf_puts(b, v > 0 ? "Infinity" : "-Infinity");
  else {
    char s[32];
    for (int prec = 6; prec <= 9; prec++) {
      snprintf(s, sizeof(s), "%.*g", prec, v);
      if (strtof(s, NULL) == v) break;
    }
    jbuf_puts(b, s);
  }
}

#endif
