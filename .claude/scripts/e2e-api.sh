#!/usr/bin/env bash
# End-to-end REST lifecycle check for allure-server.
# Usage: ./e2e-api.sh [BASE_URL]        (default http://localhost:18080)
#        BASE_URL=... ./e2e-api.sh
# Exits non-zero if any step fails.
set -euo pipefail

BASE_URL="${1:-${BASE_URL:-http://localhost:18080}}"
BASE_URL="${BASE_URL%/}"
CURL_AUTH="${CURL_AUTH:-}"   # e.g. CURL_AUTH="-u admin:admin" when API auth is required

WORK_DIR="$(mktemp -d "${TMPDIR:-/tmp}/allure-e2e.XXXXXX")"
trap 'rm -rf "$WORK_DIR"' EXIT

RUN_ID="$(date +%s)-$$"
REPORT_PATH="e2e/${RUN_ID}"
FAILED=0
STEP=0
BODY="$WORK_DIR/body"

for bin in curl jq unzip zip python3; do
  command -v "$bin" >/dev/null 2>&1 || { echo "FATAL: '$bin' is required"; exit 2; }
done

step() { STEP=$((STEP + 1)); printf '\n=== STEP %s: %s\n' "$STEP" "$1"; }
ok()   { printf 'RESULT: OK   - %s\n' "$1"; }
fail() { FAILED=1; printf 'RESULT: FAIL - %s\n' "$1"; }

# call METHOD URL [curl-args...] -> body in $BODY, status in $HTTP_STATUS; prints request, status, body
call() {
  local method="$1" url="$2"; shift 2
  # shellcheck disable=SC2086
  : > "$BODY"
  # transport failure must not abort the run: it is reported as status 000 by the step itself
  HTTP_STATUS="$(curl -sS -o "$BODY" -w '%{http_code}' -X "$method" $CURL_AUTH "$@" "$url" || true)"
  HTTP_STATUS="${HTTP_STATUS:-000}"
  printf -- '--> %s %s\n<-- HTTP %s\n<-- BODY: ' "$method" "$url" "$HTTP_STATUS"
  if jq -e . "$BODY" >/dev/null 2>&1; then jq -c . "$BODY"; else head -c 600 "$BODY"; echo; fi
}

expect_status() {
  local actual="$1" expected="$2" what="$3"
  [ "$actual" = "$expected" ] && ok "$what (HTTP $actual)" || fail "$what: expected HTTP $expected, got $actual"
}

# Minimal but genuinely valid allure2 results: 2 test results inside 1 container.
make_results_zip() {
  local tag="${1:-1}" dir zip_file
  dir="$WORK_DIR/results-$tag"; zip_file="$WORK_DIR/allure-results-$tag.zip"
  mkdir -p "$dir"
  python3 - "$dir" "$RUN_ID" <<'PY'
import json, sys, time, uuid
out, run_id = sys.argv[1], sys.argv[2]
now = int(time.time() * 1000)
tests = [("e2e passing check", "passed", None), ("e2e failing check", "failed", "expected 1 but was 2")]
children = []
for i, (name, status, msg) in enumerate(tests):
    tid = str(uuid.uuid4())
    children.append(tid)
    body = {
        "uuid": tid,
        "historyId": "e2e-history-%d" % i,          # stable across runs -> real history trend
        "fullName": "ru.iopump.qa.e2e.SmokeTest.%s" % name.replace(" ", "_"),
        "name": name,
        "status": status,
        "stage": "finished",
        "start": now + i * 10,
        "stop": now + i * 10 + 5,
        "labels": [
            {"name": "suite", "value": "E2E API Suite"},
            {"name": "package", "value": "ru.iopump.qa.e2e"},
            {"name": "testClass", "value": "ru.iopump.qa.e2e.SmokeTest"},
            {"name": "framework", "value": "junit5"},
            {"name": "language", "value": "java"},
        ],
        "steps": [{"name": "given service is up", "status": "passed", "stage": "finished",
                   "start": now + i * 10, "stop": now + i * 10 + 2}],
    }
    if msg:
        body["statusDetails"] = {"message": msg, "trace": "ru.iopump.qa.e2e.SmokeTest.check(SmokeTest.java:42)"}
    json.dump(body, open("%s/%s-result.json" % (out, tid), "w"))
cid = str(uuid.uuid4())
json.dump({"uuid": cid, "name": "E2E API Suite run %s" % run_id, "children": children,
           "start": now, "stop": now + 100}, open("%s/%s-container.json" % (out, cid), "w"))
json.dump({"name": "e2e-api.sh", "type": "script", "buildName": "run-%s" % run_id},
          open("%s/executor.json" % out, "w"))
PY
  (cd "$dir" && zip -q -r "$zip_file" .)
  echo "$zip_file"
}

printf 'allure-server E2E API check\nBASE URL: %s\nRUN ID:   %s\nREPORT:   %s\n' \
  "$BASE_URL" "$RUN_ID" "$REPORT_PATH"

# ---------------------------------------------------------------- 1. upload
step "POST /api/result - upload allure-results zip"
ZIP="$(make_results_zip 1)"
echo "Archive entries:"; unzip -l "$ZIP" | sed -n '4,12p'
call POST "$BASE_URL/api/result" -F "allureResults=@$ZIP;type=application/zip;filename=allure-results.zip"
expect_status "$HTTP_STATUS" "201" "upload accepted"
RESULT_UUID="$(jq -r '.uuid // empty' "$BODY")"
[ -n "$RESULT_UUID" ] && ok "result uuid = $RESULT_UUID" || { fail "no uuid in upload response"; RESULT_UUID="none"; }

# ---------------------------------------------------------------- 2. list results
step "GET /api/result - uploaded uuid is listed"
call GET "$BASE_URL/api/result"
expect_status "$HTTP_STATUS" "200" "results listed"
jq -e --arg u "$RESULT_UUID" 'map(select(.uuid == $u)) | length == 1' "$BODY" >/dev/null \
  && ok "uuid $RESULT_UUID present exactly once" || fail "uuid $RESULT_UUID not found in GET /api/result"

# ---------------------------------------------------------------- 3. generate
step "POST /api/report - generate report from that result"
GEN_REQ="$WORK_DIR/generate.json"
jq -n --arg u "$RESULT_UUID" --arg p1 e2e --arg p2 "$RUN_ID" \
  '{reportSpec:{path:[$p1,$p2],executorInfo:{name:"e2e-api.sh",type:"script",buildName:("run-"+$p2)}},results:[$u],deleteResults:false}' \
  > "$GEN_REQ"
call POST "$BASE_URL/api/report" -H 'Content-Type: application/json' --data-binary "@$GEN_REQ"
expect_status "$HTTP_STATUS" "201" "report generated"
REPORT_UUID="$(jq -r '.uuid // empty' "$BODY")"
REPORT_URL_PATH="$(jq -r '.url // empty' "$BODY" | sed -E 's#^https?://[^/]+##')"
[ -n "$REPORT_UUID" ] && ok "report uuid = $REPORT_UUID" || { fail "no uuid in generate response"; REPORT_UUID="none"; }

# ---------------------------------------------------------------- 4. list reports
step "GET /api/report - report is listed"
call GET "$BASE_URL/api/report"
expect_status "$HTTP_STATUS" "200" "reports listed"
jq -e --arg u "$REPORT_UUID" 'map(select(.uuid == $u)) | length == 1' "$BODY" >/dev/null \
  && ok "report $REPORT_UUID present exactly once" || fail "report $REPORT_UUID not found in GET /api/report"

# ---------------------------------------------------------------- 5. serve html
step "GET report HTML - served page is a real Allure report"
REPORT_HTML="${BASE_URL}${REPORT_URL_PATH:-/allure/reports/$REPORT_UUID/}index.html"
call GET "$REPORT_HTML"
expect_status "$HTTP_STATUS" "200" "report index.html served"
if grep -qi 'allure' "$BODY" && grep -qi '<html' "$BODY"; then
  ok "body looks like an Allure report page"
  echo "BODY SLICE:"; head -c 400 "$BODY"; echo
else
  fail "served body is not an Allure report page"
fi
call GET "$BASE_URL/allure/reports/$REPORT_UUID/widgets/summary.json"
expect_status "$HTTP_STATUS" "200" "report summary.json served"
jq -e '.statistic.total == 2 and .statistic.passed == 1 and .statistic.failed == 1' "$BODY" >/dev/null \
  && ok "summary statistic = 2 total / 1 passed / 1 failed" \
  || fail "unexpected summary statistic (expected 2 total, 1 passed, 1 failed)"

# ---------------------------------------------------------------- 6. history
step "History - second run under the same path accumulates trend"
ZIP2="$(make_results_zip 2)"
call POST "$BASE_URL/api/result" -F "allureResults=@$ZIP2;type=application/zip;filename=allure-results.zip"
expect_status "$HTTP_STATUS" "201" "second upload accepted"
RESULT_UUID_2="$(jq -r '.uuid // empty' "$BODY")"
jq -n --arg u "$RESULT_UUID_2" --arg p1 e2e --arg p2 "$RUN_ID" \
  '{reportSpec:{path:[$p1,$p2],executorInfo:{name:"e2e-api.sh",type:"script",buildName:("run2-"+$p2)}},results:[$u],deleteResults:true}' \
  > "$GEN_REQ"
call POST "$BASE_URL/api/report" -H 'Content-Type: application/json' --data-binary "@$GEN_REQ"
expect_status "$HTTP_STATUS" "201" "second report generated"
REPORT_UUID_2="$(jq -r '.uuid // empty' "$BODY")"
call GET "$BASE_URL/allure/reports/$REPORT_UUID_2/widgets/history-trend.json"
expect_status "$HTTP_STATUS" "200" "history-trend.json served"
jq -e 'length >= 2' "$BODY" >/dev/null \
  && ok "history trend has $(jq 'length' "$BODY") runs" \
  || fail "history trend has $(jq 'length' "$BODY" 2>/dev/null || echo '?') runs, expected >= 2"

# ---------------------------------------------------------------- 7. delete one
step "DELETE /api/report/{uuid} - removes exactly that report (new in 3.0.0)"
call DELETE "$BASE_URL/api/report/$REPORT_UUID"
expect_status "$HTTP_STATUS" "204" "report $REPORT_UUID deleted"
call GET "$BASE_URL/api/report"
jq -e --arg u "$REPORT_UUID" 'map(select(.uuid == $u)) | length == 0' "$BODY" >/dev/null \
  && ok "deleted report is gone from the list" || fail "deleted report still listed"
jq -e --arg u "$REPORT_UUID_2" 'map(select(.uuid == $u)) | length == 1' "$BODY" >/dev/null \
  && ok "sibling report $REPORT_UUID_2 untouched" || fail "sibling report $REPORT_UUID_2 was removed too"
call DELETE "$BASE_URL/api/report/$REPORT_UUID"
expect_status "$HTTP_STATUS" "404" "second delete of the same uuid is 404"
call DELETE "$BASE_URL/api/report/not-a-uuid"
expect_status "$HTTP_STATUS" "400" "non-uuid path rejected"

# ---------------------------------------------------------------- 8. age cleanup
step "DELETE /api/report?seconds= - age-based cleanup"
call DELETE "$BASE_URL/api/report?seconds=1"
expect_status "$HTTP_STATUS" "200" "cleanup with old boundary returns 200"
jq -e --arg u "$REPORT_UUID_2" 'map(select(.uuid == $u)) | length == 0' "$BODY" >/dev/null \
  && ok "fresh report kept (not older than 1970-01-01)" || fail "fresh report deleted by an ancient boundary"
FUTURE=$(( $(date +%s) + 3600 ))
call DELETE "$BASE_URL/api/report?seconds=$FUTURE"
expect_status "$HTTP_STATUS" "200" "cleanup with future boundary returns 200"
jq -e --arg u "$REPORT_UUID_2" 'map(select(.uuid == $u)) | length == 1' "$BODY" >/dev/null \
  && ok "report $REPORT_UUID_2 removed by future boundary" || fail "future boundary did not delete the report"
call GET "$BASE_URL/api/report"
jq -e 'length == 0' "$BODY" >/dev/null && ok "report list empty after cleanup" \
  || fail "report list not empty after cleanup: $(jq -c 'map(.uuid)' "$BODY")"

# ---------------------------------------------------------------- 9. history purge
step "DELETE /api/report/history - clears stored history"
call DELETE "$BASE_URL/api/report/history"
expect_status "$HTTP_STATUS" "200" "history cleared"
jq -e 'type == "array"' "$BODY" >/dev/null && ok "response is a report array" || fail "unexpected response shape"

# ---------------------------------------------------------------- cleanup
step "Cleanup - drop results left by this run"
# Only RESULT_UUID survives: the second generation ran with deleteResults=true.
# Deleting an already-consumed result currently answers 500, so it is not re-deleted here.
call DELETE "$BASE_URL/api/result/$RESULT_UUID"
expect_status "$HTTP_STATUS" "200" "result $RESULT_UUID deleted"
call GET "$BASE_URL/api/result"
jq -e --arg u "$RESULT_UUID" 'map(select(.uuid == $u)) | length == 0' "$BODY" >/dev/null \
  && ok "result list no longer contains this run" || fail "result $RESULT_UUID still listed"

printf '\n================================\n'
if [ "$FAILED" -eq 0 ]; then
  echo "OVERALL: PASS"
else
  echo "OVERALL: FAIL"
fi
exit "$FAILED"
