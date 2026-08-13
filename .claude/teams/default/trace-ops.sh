#!/bin/sh
# brewcode-meta: version=5.6.0 content_version=5.6.0 generated_by=brewcode:teams-setup
set -eu

USAGE="Usage: trace-ops.sh <add|read|cursor|migrate> <team_dir> [args...]"

die() { printf '%s\n' "$*" >&2; exit 1; }

escape_json() {
  printf '%s' "$1" | tr '\n\r' '  ' | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g' -e 's/	/\\t/g'
}

truncate_text() {
  _t="$1"
  if [ "${#_t}" -gt 100 ]; then
    _t="$(printf '%.100s' "$_t")"
  fi
  printf '%s' "$_t"
}

cmd_add() {
  [ $# -ge 6 ] || die "Usage: trace-ops.sh add <team_dir> <sid> <agent> <kind> <qualifier> <text>"
  _dir="$1"; _sid="$2"; _agent="$3"; _kind="$4"; _qual="$5"
  shift 5; _text="$*"

  case "$_kind" in
    track)
      case "$_qual" in
        took|refused|completed|failed) : ;;
        *) die "Invalid status: $_qual (expected took|refused|completed|failed)" ;;
      esac ;;
    issue)
      case "$_qual" in
        low|medium|high|critical) : ;;
        *) die "Invalid severity: $_qual (expected low|medium|high|critical)" ;;
      esac ;;
    insight)
      case "$_qual" in
        pattern|architecture|performance|security|convention|debt) : ;;
        *) die "Invalid category: $_qual (expected pattern|architecture|performance|security|convention|debt)" ;;
      esac ;;
    *) die "Invalid kind: $_kind (expected track|issue|insight)" ;;
  esac

  _ts="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
  _text="$(truncate_text "$_text")"
  _text_esc="$(escape_json "$_text")"
  _agent_esc="$(escape_json "$_agent")"
  _sid_esc="$(escape_json "$_sid")"

  case "$_kind" in
    track)   _qfield="\"s\":\"$_qual\"" ;;
    issue)   _qfield="\"sev\":\"$_qual\"" ;;
    insight) _qfield="\"cat\":\"$_qual\"" ;;
  esac

  _line="$(printf '{"ts":"%s","sid":"%s","src":"%s","k":"%s",%s,"txt":"%s"}' \
    "$_ts" "$_sid_esc" "$_agent_esc" "$_kind" "$_qfield" "$_text_esc")"

  printf '%s\n' "$_line" >> "$_dir/trace.jsonl"
  printf '%s\n' "$_line"
}

cmd_read() {
  [ $# -ge 1 ] || die "Usage: trace-ops.sh read <team_dir> [--since <ts>] [--sid <sid>] [--kind <k>]"
  _dir="$1"; shift
  _since=""; _sid=""; _kind=""

  while [ $# -gt 0 ]; do
    case "$1" in
      --since) _since="$2"; shift 2 ;;
      --sid)   _sid="$2"; shift 2 ;;
      --kind)  _kind="$2"; shift 2 ;;
      *) die "Unknown option: $1" ;;
    esac
  done

  _file="$_dir/trace.jsonl"
  [ -f "$_file" ] || return 0

  if command -v jq >/dev/null 2>&1; then
    _filter="."
    [ -n "$_since" ] && _filter="$_filter | select(.ts >= \"$_since\")"
    [ -n "$_sid" ]   && _filter="$_filter | select(.sid == \"$_sid\")"
    [ -n "$_kind" ]  && _filter="$_filter | select(.k == \"$_kind\")"
    jq -c "$_filter" "$_file"
  else
    _result="$(cat "$_file")"
    if [ -n "$_since" ]; then
      _result="$(printf '%s\n' "$_result" | while IFS= read -r _ln; do
        _lts="$(printf '%s' "$_ln" | sed -n 's/.*"ts":"\([^"]*\)".*/\1/p')"
        case "$(printf '%s\n%s' "$_since" "$_lts" | sort | head -1)" in
          "$_since") printf '%s\n' "$_ln" ;;
        esac
      done)"
    fi
    [ -n "$_sid" ] && _result="$(printf '%s\n' "$_result" | grep -F "\"sid\":\"$_sid\"" || true)"
    [ -n "$_kind" ] && _result="$(printf '%s\n' "$_result" | grep -F "\"k\":\"$_kind\"" || true)"
    [ -n "$_result" ] && printf '%s\n' "$_result"
  fi
}

cmd_cursor() {
  [ $# -ge 1 ] || die "Usage: trace-ops.sh cursor <team_dir> [set <ts>]"
  _dir="$1"; shift
  _cfile="$_dir/trace.cursor"

  if [ $# -ge 2 ] && [ "$1" = "set" ]; then
    printf '%s\n' "$2" > "$_cfile"
  elif [ -f "$_cfile" ]; then
    cat "$_cfile"
  fi
}

parse_md_rows() {
  _mdfile="$1"
  [ -f "$_mdfile" ] || return 0
  _skip_header=1
  sed -n '/^|/p' "$_mdfile" | grep -v '^[| -]*$' | while IFS='|' read -r _ _c1 _c2 _c3 _c4 _c5 _; do
    if [ "$_skip_header" -eq 1 ]; then _skip_header=0; continue; fi
    _c1="$(printf '%s' "$_c1" | sed 's/^ *//;s/ *$//')"
    _c2="$(printf '%s' "$_c2" | sed 's/^ *//;s/ *$//')"
    _c3="$(printf '%s' "$_c3" | sed 's/^ *//;s/ *$//')"
    _c4="$(printf '%s' "$_c4" | sed 's/^ *//;s/ *$//')"
    _c5="$(printf '%s' "${_c5:-}" | sed 's/^ *//;s/ *$//')"
    printf '%s\t%s\t%s\t%s\t%s\n' "$_c1" "$_c2" "$_c3" "$_c4" "$_c5"
  done
}

to_lower() {
  printf '%s' "$1" | tr '[:upper:]' '[:lower:]'
}

cmd_migrate() {
  [ $# -ge 1 ] || die "Usage: trace-ops.sh migrate <team_dir>"
  _dir="$1"
  _out="$_dir/trace.jsonl"
  _total_track=0; _total_issue=0; _total_insight=0

  if [ -f "$_dir/tracking.md" ]; then
    parse_md_rows "$_dir/tracking.md" | while IFS="$(printf '\t')" read -r _date _agent _task _status _comment; do
      [ -n "$_task" ] || continue
      _suffix=""; [ -n "$_comment" ] && _suffix=" — $_comment" || true
      _txt="$(truncate_text "$_task$_suffix")"
      _txt_esc="$(escape_json "$_txt")"
      _agent_esc="$(escape_json "$_agent")"
      _s="$(to_lower "$_status")"
      case "$_s" in
        took|refused|completed|failed) : ;;
        *) _s="took" ;;
      esac
      _ts="${_date:-1970-01-01}T00:00:00Z"
      printf '{"ts":"%s","sid":"migrated","src":"%s","k":"track","s":"%s","txt":"%s"}\n' \
        "$_ts" "$_agent_esc" "$_s" "$_txt_esc"
    done >> "$_out"
    _total_track="$(parse_md_rows "$_dir/tracking.md" | grep -c . || true)"
    mv "$_dir/tracking.md" "$_dir/tracking.md.bak"
  fi

  if [ -f "$_dir/issues.md" ]; then
    parse_md_rows "$_dir/issues.md" | while IFS="$(printf '\t')" read -r _date _agent _desc _sev _; do
      [ -n "$_desc" ] || continue
      _txt="$(truncate_text "$_desc")"
      _txt_esc="$(escape_json "$_txt")"
      _agent_esc="$(escape_json "$_agent")"
      _sv="$(to_lower "$_sev")"
      case "$_sv" in
        low|medium|high|critical) : ;;
        *) _sv="medium" ;;
      esac
      _ts="${_date:-1970-01-01}T00:00:00Z"
      printf '{"ts":"%s","sid":"migrated","src":"%s","k":"issue","sev":"%s","txt":"%s"}\n' \
        "$_ts" "$_agent_esc" "$_sv" "$_txt_esc"
    done >> "$_out"
    _total_issue="$(parse_md_rows "$_dir/issues.md" | grep -c . || true)"
    mv "$_dir/issues.md" "$_dir/issues.md.bak"
  fi

  if [ -f "$_dir/insights.md" ]; then
    parse_md_rows "$_dir/insights.md" | while IFS="$(printf '\t')" read -r _date _agent _insight _cat _; do
      [ -n "$_insight" ] || continue
      _txt="$(truncate_text "$_insight")"
      _txt_esc="$(escape_json "$_txt")"
      _agent_esc="$(escape_json "$_agent")"
      _ct="$(to_lower "$_cat")"
      case "$_ct" in
        pattern|architecture|performance|security|convention|debt) : ;;
        *) _ct="pattern" ;;
      esac
      _ts="${_date:-1970-01-01}T00:00:00Z"
      printf '{"ts":"%s","sid":"migrated","src":"%s","k":"insight","cat":"%s","txt":"%s"}\n' \
        "$_ts" "$_agent_esc" "$_ct" "$_txt_esc"
    done >> "$_out"
    _total_insight="$(parse_md_rows "$_dir/insights.md" | grep -c . || true)"
    mv "$_dir/insights.md" "$_dir/insights.md.bak"
  fi

  printf 'Migrated: tracking=%s issues=%s insights=%s\n' \
    "$_total_track" "$_total_issue" "$_total_insight"
}

[ $# -ge 2 ] || die "$USAGE"
CMD="$1"; TEAM_DIR="$2"; shift 2

case "$CMD" in
  add)     cmd_add "$TEAM_DIR" "$@" ;;
  read)    cmd_read "$TEAM_DIR" "$@" ;;
  cursor)  cmd_cursor "$TEAM_DIR" "$@" ;;
  migrate) cmd_migrate "$TEAM_DIR" "$@" ;;
  *)       die "$USAGE" ;;
esac
