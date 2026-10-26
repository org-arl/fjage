////////////////////////////////////////////////////////////////////
//
// Offline tests for the JSON encoder and decoder. These include fjage.c
// directly to reach its static functions, and need no master container.
//
// $ cd gateways/c
// $ make test
// $ ./test_codec
//
////////////////////////////////////////////////////////////////////

#include "fjage.c"

#include <float.h>
#include <limits.h>

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

// Decode a message from its JSON object, {"clazz": ..., "data": {...}}.
static fjage_msg_t from_json(const char* s) {
  json_doc_t d;
  return json_parse(&d, s) ? msg_from_doc(&d, 0) : NULL;
}

// Encode a message as it would be sent, and decode it as it would be received.
static fjage_msg_t roundtrip(fjage_msg_t msg) {
  jbuf_t b = {0};
  bool ok = msg_write_json(&b, msg, "gateway");
  fjage_msg_destroy(msg);
  fjage_msg_t rsp = ok ? from_json(b.s) : NULL;
  free(b.s);
  return rsp;
}

static bool unescapes_to(const char* escaped, const char* expected) {
  char s[64];
  strcpy(s, escaped);
  int n = json_unescape(s, strlen(s));
  return n == (int)strlen(expected) && !strcmp(s, expected);
}

static void test_strings(void) {
  const char* text = "quote\" backslash\\ slash/ nl\n cr\r tab\t bs\b ff\f ctl\x01 lambda\xce\xbb";
  fjage_msg_t msg = fjage_msg_create("Te\"st", FJAGE_INFORM);
  fjage_msg_set_recipient(msg, "agent\"1");
  fjage_msg_add_string(msg, "text", text);
  fjage_msg_add_string(msg, "key\"quote", "v");
  fjage_msg_add_string(msg, "nothing", NULL);
  msg = roundtrip(msg);
  test_assert("roundtrip (string)", msg != NULL);
  const char* s = fjage_msg_get_string(msg, "text");
  test_assert("string escapes", s != NULL && !strcmp(s, text));
  s = fjage_msg_get_string(msg, "key\"quote");
  test_assert("key escapes", s != NULL && !strcmp(s, "v"));
  test_assert("null string", fjage_msg_get_string(msg, "nothing") == NULL);
  test_assert("clazz escapes", !strcmp(fjage_msg_get_clazz(msg), "Te\"st"));
  test_assert("recipient escapes", !strcmp(fjage_msg_get_recipient(msg), "agent\"1"));
  fjage_msg_destroy(msg);
}

static void test_unescape(void) {
  test_assert("unescape (simple)", unescapes_to("a\\\"b\\\\c\\/d\\n\\t\\r\\b\\f", "a\"b\\c/d\n\t\r\b\f"));
  test_assert("unescape (\\u, 1-3 bytes)", unescapes_to("\\u0041\\u00e9\\u03bb\\u20AC", "A\xc3\xa9\xce\xbb\xe2\x82\xac"));
  test_assert("unescape (surrogate pair)", unescapes_to("\\ud83d\\ude00", "\xf0\x9f\x98\x80"));
  test_assert("unescape (lone surrogate)", unescapes_to("x\\ud83dy", "x\xef\xbf\xbdy"));
  test_assert("unescape (short \\u)", unescapes_to("\\u12", "u12"));
}

static void test_numbers(void) {
  fjage_msg_t msg = fjage_msg_create("Test", FJAGE_INFORM);
  fjage_msg_add_int(msg, "imin", INT_MIN);
  fjage_msg_add_int(msg, "imax", INT_MAX);
  fjage_msg_add_long(msg, "lmin", LONG_MIN);
  fjage_msg_add_long(msg, "lmax", LONG_MAX);
  fjage_msg_add_float(msg, "fmax", FLT_MAX);
  fjage_msg_add_float(msg, "fmin", -FLT_MAX);
  fjage_msg_add_float(msg, "ftiny", FLT_MIN);
  fjage_msg_add_float(msg, "fsmall", 1e-7f);
  fjage_msg_add_float(msg, "f27", 2.7f);
  fjage_msg_add_float(msg, "fint", 16777216.0f);
  fjage_msg_add_float(msg, "fzero", -0.0f);
  fjage_msg_add_float(msg, "finf", INFINITY);
  fjage_msg_add_float(msg, "fninf", -INFINITY);
  fjage_msg_add_float(msg, "fnan", NAN);
  fjage_msg_add_bool(msg, "bf", false);
  msg = roundtrip(msg);
  test_assert("roundtrip (numbers)", msg != NULL);
  test_assert("int limits", fjage_msg_get_int(msg, "imin", 0) == INT_MIN && fjage_msg_get_int(msg, "imax", 0) == INT_MAX);
  test_assert("long limits", fjage_msg_get_long(msg, "lmin", 0) == LONG_MIN && fjage_msg_get_long(msg, "lmax", 0) == LONG_MAX);
  test_assert("float limits", fjage_msg_get_float(msg, "fmax", 0) == FLT_MAX && fjage_msg_get_float(msg, "fmin", 0) == -FLT_MAX && fjage_msg_get_float(msg, "ftiny", 0) == FLT_MIN);
  test_assert("float exact", fjage_msg_get_float(msg, "fsmall", 0) == 1e-7f && fjage_msg_get_float(msg, "f27", 0) == 2.7f);
  test_assert("float shortest", !strcmp(fjage_msg_get_string(msg, "f27"), "2.7") && !strcmp(fjage_msg_get_string(msg, "fsmall"), "1.0e-07") &&
    !strcmp(fjage_msg_get_string(msg, "fint"), "16777216.0") && !strcmp(fjage_msg_get_string(msg, "fzero"), "-0.0"));
  test_assert("float infinities", fjage_msg_get_float(msg, "finf", 0) == INFINITY && fjage_msg_get_float(msg, "fninf", 0) == -INFINITY);
  test_assert("float NaN as null", fjage_msg_get_string(msg, "fnan") == NULL && fjage_msg_get_float(msg, "fnan", 5) == 5);
  test_assert("bool false", !fjage_msg_get_bool(msg, "bf", true));
  fjage_msg_destroy(msg);
}

// Add a value after padding of every length up to a few buffer doublings, so that
// the value straddles each boundary. Overflows abort under AddressSanitizer.
static void test_buffer_boundaries(void) {
  static char pad[4200];
  bool ok = true;
  for (int n = 0; n < (int)sizeof(pad) && ok; n++) {
    memset(pad, 'x', n);
    pad[n] = 0;
    fjage_msg_t msg = fjage_msg_create("Test", FJAGE_INFORM);
    fjage_msg_add_string(msg, "pad", pad);
    fjage_msg_add_int(msg, "i", INT_MIN);
    fjage_msg_add_bool(msg, "b", false);
    fjage_msg_add_long(msg, "l", LONG_MIN);
    fjage_msg_add_float(msg, "f", FLT_MAX);
    msg = roundtrip(msg);
    const char* s = fjage_msg_get_string(msg, "pad");
    ok = s != NULL && strlen(s) == (size_t)n && fjage_msg_get_int(msg, "i", 0) == INT_MIN &&
      !fjage_msg_get_bool(msg, "b", true) && fjage_msg_get_long(msg, "l", 0) == LONG_MIN &&
      fjage_msg_get_float(msg, "f", 0) == FLT_MAX;
    fjage_msg_destroy(msg);
  }
  test_assert("buffer boundaries", ok);
}

static void test_arrays(void) {
  int32_t values[3] = { 10, 11, 12 };
  int lengths[2] = { 2, 3 };
  uint8_t bytes[3] = { 1, 2, 3 };
  fjage_msg_t msg = fjage_msg_create("Test", FJAGE_INFORM);
  fjage_msg_add_int_array_2d(msg, "i2", values, 3, lengths, 2);
  fjage_msg_add_byte_array(msg, "b", bytes, 3);
  msg = roundtrip(msg);
  int32_t v[5] = { 0 };
  int l[2] = { 0 };
  test_assert("int_array_2d stops at vallen", fjage_msg_get_int_array_2d(msg, "i2", v, 5, l, 2) == 3 && l[0] == 2 && l[1] == 1 && v[2] == 12);
  uint8_t b[3] = { 0 };
  test_assert("byte_array", fjage_msg_get_byte_array(msg, "b", b, 3) == 3 && b[0] == 1 && b[2] == 3);
  fjage_msg_destroy(msg);
}

static void test_lookup(void) {
  fjage_msg_t msg = from_json("{\"clazz\":\"X\",\"data\":{\"arr\":[1,2,3],\"o\":{\"x\":\"inner\",\"k\":{\"z\":[1]}},\"x\":\"outer\"}}");
  const char* s = fjage_msg_get_string(msg, "x");
  test_assert("lookup after arrays and nested objects", s != NULL && !strcmp(s, "outer"));
  int32_t arr[3] = { 0 };
  test_assert("lookup array", fjage_msg_get_int_array(msg, "arr", arr, 3) == 3 && arr[2] == 3);
  fjage_msg_destroy(msg);
  msg = from_json("{\"clazz\":\"X\",\"data\":{\"arr\":[\"sender\",\"evil\"],\"sender\":\"good\",\"perf\":\"AGREE\"}}");
  test_assert("header ignores array elements", !strcmp(fjage_msg_get_sender(msg), "good") && fjage_msg_get_performative(msg) == FJAGE_AGREE);
  test_assert("header hidden from getters", fjage_msg_get_string(msg, "sender") == NULL);
  fjage_msg_destroy(msg);
  msg = from_json("{\"clazz\":\"X\",\"data\":{\"arr\":[\"sender\",\"evil\"]}}");
  test_assert("no sender from array", fjage_msg_get_sender(msg) == NULL);
  fjage_msg_destroy(msg);
  msg = from_json("{\"clazz\":\"X\",\"data\":{\"a\\u0062\":\"v\"}}");
  s = fjage_msg_get_string(msg, "ab");
  test_assert("lookup escaped key", s != NULL && !strcmp(s, "v"));
  fjage_msg_destroy(msg);
  test_assert("reject non-message", from_json("{\"clazz\":\"X\"}") == NULL && from_json("[1]") == NULL && from_json("{\"data\":") == NULL);
}

#ifndef _WIN32

// Feed a line to json_process, returning what the gateway wrote back.
static bool process(_fjage_gw_t* gw, const char* line, const char* id, char* out, size_t outlen) {
  bool rv = json_process(gw, line, id);
  ssize_t n = read(gw->intfd[0], out, outlen-1);
  out[n > 0 ? n : 0] = 0;
  return rv;
}

static void test_gateway_lines(void) {
  // the gateway writes to one end of a pipe, which the test reads from the other
  _fjage_gw_t* gw = calloc(1, sizeof(_fjage_gw_t));
  if (gw == NULL || pipe(gw->intfd) < 0) return;
  fcntl(gw->intfd[0], F_SETFL, O_NONBLOCK);
  gw->sockfd = gw->intfd[1];
  gw->aid = "gateway";
  char out[1024];
  process(gw, "{\"action\":\"send\"}", NULL, out, sizeof(out));
  process(gw, "{\"action\":\"send\",\"message\":\"x\"}", NULL, out, sizeof(out));
  test_assert("send without message", !process(gw, "{\"action\":\"send\",\"message\":{\"clazz\":\"X\"}}", NULL, out, sizeof(out)));
  test_assert("send to someone else", !process(gw, "{\"action\":\"send\",\"message\":{\"clazz\":\"X\",\"data\":{\"recipient\":\"other\"}}}", NULL, out, sizeof(out)));
  test_assert("send to us", process(gw, "{\"action\":\"send\",\"message\":{\"clazz\":\"X\",\"data\":{\"recipient\":\"gateway\",\"text\":\"a\\u003cb\"}}}", NULL, out, sizeof(out)));
  fjage_msg_t msg = mqueue_get(gw, NULL, NULL);
  const char* s = fjage_msg_get_string(msg, "text");
  test_assert("send to us (decoded)", s != NULL && !strcmp(s, "a<b"));
  test_assert("send read-only message", fjage_send(gw, msg) == -1);
  test_assert("response without inResponseTo", process(gw, "{\"id\":\"q1\"}", "q1", out, sizeof(out)));
  test_assert("response without agentID", process(gw, "{\"id\":\"q1\",\"inResponseTo\":\"agentForService\"}", "q1", out, sizeof(out)) && gw->aid_count == 0);
  test_assert("agentsForService", process(gw, "{\"id\":\"q2\",\"inResponseTo\":\"agentsForService\",\"agentIDs\":[\"a\\\"1\",7,\"b\"]}", "q2", out, sizeof(out)) &&
    gw->aid_count == 2 && !strcmp(gw->aids[0], "a\"1") && !strcmp(gw->aids[1], "b"));
  for (int i = 0; i < gw->aid_count; i++) fjage_aid_destroy(gw->aids[i]);
  free(gw->aids);
  char line[600];
  char id[400];
  memset(id, 'x', sizeof(id)-1);
  id[sizeof(id)-1] = 0;
  id[0] = '\\';
  id[1] = '"';
  snprintf(line, sizeof(line), "{\"action\":\"agents\",\"id\":\"%s\"}", id);
  process(gw, line, NULL, out, sizeof(out));
  json_doc_t d;
  const char* id1 = json_parse(&d, out) ? json_str(&d, json_find(&d, 0, "id")) : NULL;
  test_assert("unknown action reply", id1 != NULL && id1[0] == '"' && strlen(id1) == sizeof(id)-2);
  json_free(&d);
  close(gw->intfd[0]);
  close(gw->intfd[1]);
  free(gw);
}

#endif

int main(void) {
  printf("\n");
  test_strings();
  test_unescape();
  test_numbers();
  test_buffer_boundaries();
  test_arrays();
  test_lookup();
#ifndef _WIN32
  test_gateway_lines();
#endif
  printf("\n*** %d test(s) PASSED, %d test(s) FAILED ***\n\n", passed, failed);
  return failed;
}
