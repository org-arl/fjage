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

#include "jsmn.h"

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

//// input, using jsmn tokens

/// Read 4 hex digits.
///
/// @param s              Digits, in either case
/// @return               Value, or -1 if any char is not a hex digit

static int hex4(const char* s) {
  int v = 0;
  for (int i = 0; i < 4; i++) {
    char c = s[i];
    int d = (c >= '0' && c <= '9') ? c-'0' : (c >= 'a' && c <= 'f') ? c-'a'+10 : (c >= 'A' && c <= 'F') ? c-'A'+10 : -1;
    if (d < 0) return -1;
    v = v*16+d;
  }
  return v;
}

/// Encode a code point as UTF-8.
///
/// @param out            Space for at least 4 chars
/// @param cp             Code point, up to 0x10FFFF
/// @return               Number of chars written

static int utf8_encode(char* out, int cp) {
  if (cp < 0x80) {
    out[0] = cp;
    return 1;
  }
  if (cp < 0x800) {
    out[0] = 0xc0 | (cp >> 6);
    out[1] = 0x80 | (cp & 0x3f);
    return 2;
  }
  if (cp < 0x10000) {
    out[0] = 0xe0 | (cp >> 12);
    out[1] = 0x80 | ((cp >> 6) & 0x3f);
    out[2] = 0x80 | (cp & 0x3f);
    return 3;
  }
  out[0] = 0xf0 | (cp >> 18);
  out[1] = 0x80 | ((cp >> 12) & 0x3f);
  out[2] = 0x80 | ((cp >> 6) & 0x3f);
  out[3] = 0x80 | (cp & 0x3f);
  return 4;
}

/// Decode the escapes in JSON string text in place, and NUL-terminate the result.
/// \uXXXX is decoded to UTF-8, with surrogate pairs combined and lone surrogates
/// replaced by U+FFFD.
///
/// @param s              String text, without the quotes. s[len] must be writable
/// @param len            Length of the text
/// @return               Length of the decoded string

static int json_unescape(char* s, int len) {
  char* out = s;
  for (int i = 0; i < len; i++) {
    if (s[i] != '\\' || i+1 >= len) {
      *out++ = s[i];
      continue;
    }
    char c = s[++i];
    int cp;
    switch (c) {
      case 'b': *out++ = '\b'; break;
      case 'f': *out++ = '\f'; break;
      case 'n': *out++ = '\n'; break;
      case 'r': *out++ = '\r'; break;
      case 't': *out++ = '\t'; break;
      case 'u':
        if (i+4 >= len || (cp = hex4(s+i+1)) < 0) {
          *out++ = c;
          break;
        }
        i += 4;
        if (cp >= 0xd800 && cp <= 0xdbff && i+6 < len && s[i+1] == '\\' && s[i+2] == 'u') {
          int lo = hex4(s+i+3);
          if (lo >= 0xdc00 && lo <= 0xdfff) {
            cp = 0x10000 + ((cp-0xd800) << 10) + (lo-0xdc00);
            i += 6;
          }
        }
        if (cp >= 0xd800 && cp <= 0xdfff) cp = 0xfffd;
        out += utf8_encode(out, cp);
        break;
      default: *out++ = c;  // \" \\ \/
    }
  }
  *out = 0;
  return out-s;
}

/// Parsed JSON text. Tokens are indexed from 0, the outermost value, and an
/// index of -1 stands for a missing value in all the functions below.
typedef struct {
  char* js;               ///< Copy of the text, with strings decoded in place.
  jsmntok_t* tok;         ///< Tokens.
  int n;                  ///< Number of tokens.
} json_doc_t;

/// Free a parsed document and leave it empty. An empty document can be freed again.
///
/// @param d              Document

static void json_free(json_doc_t* d) {
  free(d->js);
  free(d->tok);
  *d = (json_doc_t){0};
}

/// Parse a copy of JSON text. All strings are decoded and all primitives
/// NUL-terminated in the copy, so the text of any token can be used directly.
///
/// @param d              Document to fill; free it with json_free()
/// @param s              JSON text
/// @return               true on success, or false, leaving d empty, if s is not
///                       JSON or memory runs out

static bool json_parse(json_doc_t* d, const char* s) {
  *d = (json_doc_t){0};
  size_t len = strlen(s);
  jsmn_parser parser;
  jsmn_init(&parser);
  int n = jsmn_parse(&parser, s, len, NULL, 0);
  if (n <= 0) return false;
  d->js = malloc(len+1);
  d->tok = malloc(n*sizeof(jsmntok_t));
  if (d->js == NULL || d->tok == NULL) {
    json_free(d);
    return false;
  }
  memcpy(d->js, s, len+1);
  jsmn_init(&parser);
  d->n = jsmn_parse(&parser, d->js, len, d->tok, n);
  if (d->n <= 0) {
    json_free(d);
    return false;
  }
  for (int i = 0; i < d->n; i++) {
    jsmntok_t* t = d->tok+i;
    if (t->type == JSMN_STRING) t->end = t->start + json_unescape(d->js+t->start, t->end-t->start);
    else if (t->type == JSMN_PRIMITIVE) d->js[t->end] = 0;
  }
  return true;
}

/// Get the type of a value.
///
/// @param d              Document
/// @param i              Token index
/// @return               Type, or JSMN_UNDEFINED if i is out of range

static jsmntype_t json_type(const json_doc_t* d, int i) {
  return (i >= 0 && i < d->n) ? d->tok[i].type : JSMN_UNDEFINED;
}

/// Get the number of elements of an array, or of keys of an object.
///
/// @param d              Document
/// @param i              Token index
/// @return               Size, or 0 if i is out of range

static int json_size(const json_doc_t* d, int i) {
  return (i >= 0 && i < d->n) ? d->tok[i].size : 0;
}

/// Get the decoded text of a string. The text is owned by the document.
///
/// @param d              Document
/// @param i              Token index
/// @return               Text, or NULL if the value is not a string

static const char* json_str(const json_doc_t* d, int i) {
  return json_type(d, i) == JSMN_STRING ? d->js+d->tok[i].start : NULL;
}

/// Get the text of a string, number or boolean. The text is owned by the document.
///
/// @param d              Document
/// @param i              Token index
/// @return               Text, or NULL for null, arrays, objects and missing values

static const char* json_text(const json_doc_t* d, int i) {
  jsmntype_t t = json_type(d, i);
  if (t == JSMN_STRING) return d->js+d->tok[i].start;
  if (t == JSMN_PRIMITIVE && strcmp(d->js+d->tok[i].start, "null")) return d->js+d->tok[i].start;
  return NULL;
}

/// Skip past a value and everything inside it. With end = json_next(d, i),
/// `for (j = i+1; j < end; j = json_next(d, j))` visits the elements of array i.
///
/// @param d              Document
/// @param i              Token index
/// @return               Index of the first token after the value

static int json_next(const json_doc_t* d, int i) {
  if (i < 0 || i >= d->n) return d->n;
  int end = d->tok[i].end;
  int j = i+1;
  while (j < d->n && d->tok[j].start < end) j++;
  return j;
}

/// Find a key in an object. Only direct members of the object are searched.
///
/// @param d              Document
/// @param obj            Token index of the object
/// @param key            Key
/// @return               Token index of the value, or -1 if absent

static int json_find(const json_doc_t* d, int obj, const char* key) {
  if (json_type(d, obj) != JSMN_OBJECT || key == NULL) return -1;
  int end = json_next(d, obj);
  for (int k = obj+1; k+1 < end; k = json_next(d, k+1))
    if (d->tok[k].type == JSMN_STRING && !strcmp(d->js+d->tok[k].start, key)) return k+1;
  return -1;
}

#endif
