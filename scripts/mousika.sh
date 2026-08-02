#!/usr/bin/env bash
#
# Mousika 规则编排服务管理脚本。
#
# 用法：./scripts/mousika.sh <build|start|stop|restart|status|logs|dev|doctor|help>
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd -P)"
MODULE="mousika-web"
JAR="$PROJECT_DIR/$MODULE/target/$MODULE.jar"

PORT="${MOUSIKA_PORT:-8080}"
ADDRESS="${MOUSIKA_SERVER_ADDRESS:-127.0.0.1}"
DB_PATH="${MOUSIKA_DB_PATH:-$PROJECT_DIR/data/mousika.db}"
RUNTIME_BASE="${TMPDIR:-/tmp}"
RUN_DIR="${MOUSIKA_RUN_DIR:-${RUNTIME_BASE%/}/mousika}"
STATE_FILE="$RUN_DIR/instance.state"
DEFAULT_LOG_FILE="$RUN_DIR/mousika.log"
LEGACY_RUN_DIR="$PROJECT_DIR/.run"
START_TIMEOUT="${MOUSIKA_START_TIMEOUT:-60}"
STOP_TIMEOUT="${MOUSIKA_STOP_TIMEOUT:-15}"

CURL_BIN=""
PORT_TOOL=""
MAVEN_BIN=""

STATE_PID=""
STATE_PORT=""
STATE_PROJECT=""
STATE_JAR=""
STATE_DB=""
STATE_LOG=""

if [ -t 1 ] && [ -z "${NO_COLOR:-}" ]; then
    C_GREEN="\033[0;32m"
    C_RED="\033[0;31m"
    C_YELLOW="\033[0;33m"
    C_OFF="\033[0m"
else
    C_GREEN=""
    C_RED=""
    C_YELLOW=""
    C_OFF=""
fi

info() { printf '%b\n' "${C_GREEN}▸${C_OFF} $*"; }
warn() { printf '%b\n' "${C_YELLOW}▸${C_OFF} $*"; }
err() { printf '%b\n' "${C_RED}✗${C_OFF} $*" >&2; }

is_positive_integer() {
    case "${1:-}" in
        ''|*[!0-9]*) return 1 ;;
        *) [ "$1" -gt 0 ] ;;
    esac
}

validate_config() {
    if ! is_positive_integer "$PORT" || [ "$PORT" -gt 65535 ]; then
        err "MOUSIKA_PORT 必须是 1 到 65535 之间的整数，当前值：$PORT"
        return 1
    fi
    if ! is_positive_integer "$START_TIMEOUT" || ! is_positive_integer "$STOP_TIMEOUT"; then
        err "MOUSIKA_START_TIMEOUT 和 MOUSIKA_STOP_TIMEOUT 必须是正整数"
        return 1
    fi
    if [ -z "$ADDRESS" ] || [ -z "$DB_PATH" ] || [ -z "$RUN_DIR" ]; then
        err "监听地址、数据库路径和运行目录不能为空"
        return 1
    fi
}

find_command() {
    local name="$1"
    shift
    local found
    found="$(command -v "$name" 2>/dev/null || true)"
    if [ -n "$found" ]; then
        printf '%s\n' "$found"
        return 0
    fi
    local candidate
    for candidate in "$@"; do
        if [ -x "$candidate" ]; then
            printf '%s\n' "$candidate"
            return 0
        fi
    done
    return 1
}

resolve_maven() {
    [ -n "$MAVEN_BIN" ] && return 0
    MAVEN_BIN="$(find_command mvn /opt/homebrew/bin/mvn /usr/local/bin/mvn || true)"
    if [ -z "$MAVEN_BIN" ]; then
        err "未找到 Maven，请安装 Maven 并确保 mvn 可执行"
        return 1
    fi
}

resolve_runtime_tools() {
    if [ -z "$CURL_BIN" ]; then
        CURL_BIN="$(find_command curl /usr/bin/curl /usr/local/bin/curl || true)"
    fi
    if [ -z "$CURL_BIN" ]; then
        err "未找到 curl，无法执行健康检查"
        return 1
    fi
    if [ -z "$PORT_TOOL" ]; then
        if find_command lsof /usr/sbin/lsof /usr/bin/lsof >/dev/null; then
            PORT_TOOL="$(find_command lsof /usr/sbin/lsof /usr/bin/lsof)"
        elif find_command ss /usr/sbin/ss /usr/bin/ss >/dev/null; then
            PORT_TOOL="$(find_command ss /usr/sbin/ss /usr/bin/ss)"
        else
            err "未找到 lsof 或 ss，无法安全确认端口归属"
            return 1
        fi
    fi
}

resolve_java_home() {
    if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ] \
            && "$JAVA_HOME/bin/java" -version 2>&1 | grep -q 'version "21'; then
        return 0
    fi

    local candidate=""
    local java_home_bin=""
    java_home_bin="$(find_command java_home /usr/libexec/java_home || true)"
    if [ -n "$java_home_bin" ]; then
        candidate="$("$java_home_bin" -v 21 2>/dev/null || true)"
    fi
    if [ -z "$candidate" ] && [ -x /Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home/bin/java ]; then
        candidate="/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home"
    fi
    if [ -z "$candidate" ] || [ ! -x "$candidate/bin/java" ]; then
        err "未找到 JDK 21，请设置 JAVA_HOME 指向 JDK 21"
        return 1
    fi
    export JAVA_HOME="$candidate"
}

process_command() {
    ps -p "$1" -o command= 2>/dev/null || true
}

pid_alive() {
    [ -n "${1:-}" ] && kill -0 "$1" 2>/dev/null
}

listening_pids() {
    local port="$1"
    if [ "$(basename "$PORT_TOOL")" = "lsof" ]; then
        "$PORT_TOOL" -tiTCP:"$port" -sTCP:LISTEN 2>/dev/null || true
    else
        "$PORT_TOOL" -ltnp "sport = :$port" 2>/dev/null \
            | sed -n 's/.*pid=\([0-9][0-9]*\).*/\1/p' \
            | sort -u
    fi
}

listening_ports_for_pid() {
    local pid="$1"
    if [ "$(basename "$PORT_TOOL")" = "lsof" ]; then
        "$PORT_TOOL" -Pan -p "$pid" -iTCP -sTCP:LISTEN -Fn 2>/dev/null \
            | sed -n 's/^n.*:\([0-9][0-9]*\)$/\1/p' \
            | sort -u
    else
        "$PORT_TOOL" -ltnp 2>/dev/null \
            | awk -v pid="$pid" '
                index($0, "pid=" pid ",") {
                    count = split($4, parts, ":");
                    if (count > 0) print parts[count];
                }
            ' \
            | sort -u
    fi
}

pid_owns_port() {
    local pid="$1"
    local port="$2"
    listening_pids "$port" | grep -qx "$pid"
}

process_matches_jar() {
    local pid="$1"
    local jar="$2"
    local command_line
    command_line="$(process_command "$pid")"
    [ -n "$command_line" ] && case "$command_line" in
        *"$jar"*) return 0 ;;
        *) return 1 ;;
    esac
}

reset_state() {
    STATE_PID=""
    STATE_PORT=""
    STATE_PROJECT=""
    STATE_JAR=""
    STATE_DB=""
    STATE_LOG=""
}

load_state() {
    reset_state
    [ -f "$STATE_FILE" ] || return 1

    STATE_PID="$(sed -n '1p' "$STATE_FILE")"
    STATE_PORT="$(sed -n '2p' "$STATE_FILE")"
    STATE_PROJECT="$(sed -n '3p' "$STATE_FILE")"
    STATE_JAR="$(sed -n '4p' "$STATE_FILE")"
    STATE_DB="$(sed -n '5p' "$STATE_FILE")"
    STATE_LOG="$(sed -n '6p' "$STATE_FILE")"

    if ! is_positive_integer "$STATE_PID" || ! is_positive_integer "$STATE_PORT" \
            || [ "$STATE_PORT" -gt 65535 ] \
            || [ -z "$STATE_PROJECT" ] || [ -z "$STATE_JAR" ] || [ -z "$STATE_LOG" ]; then
        err "运行状态文件损坏：$STATE_FILE"
        return 2
    fi
    if [ "$STATE_PROJECT" != "$PROJECT_DIR" ]; then
        err "运行目录已被其他 checkout 使用：$STATE_PROJECT"
        err "请设置 MOUSIKA_RUN_DIR 使用独立目录"
        return 2
    fi
    return 0
}

write_state() {
    local pid="$1"
    local port="$2"
    local jar="$3"
    local db="$4"
    local log_file="$5"
    local temporary

    mkdir -p "$RUN_DIR"
    chmod 700 "$RUN_DIR"
    temporary="$STATE_FILE.tmp.$$"
    {
        printf '%s\n' "$pid"
        printf '%s\n' "$port"
        printf '%s\n' "$PROJECT_DIR"
        printf '%s\n' "$jar"
        printf '%s\n' "$db"
        printf '%s\n' "$log_file"
    } > "$temporary"
    chmod 600 "$temporary"
    mv "$temporary" "$STATE_FILE"
}

clear_state() {
    rm -f "$STATE_FILE"
    reset_state
}

adopt_legacy_state() {
    # 显式指定运行目录表示管理独立实例，不得误接管仓库里的旧实例。
    [ -z "${MOUSIKA_RUN_DIR:-}" ] || return 1
    local legacy_pid_file="$LEGACY_RUN_DIR/mousika.pid"
    local legacy_log_file="$LEGACY_RUN_DIR/mousika.log"
    [ -f "$legacy_pid_file" ] || return 1

    local legacy_pid
    legacy_pid="$(sed -n '1p' "$legacy_pid_file")"
    if ! is_positive_integer "$legacy_pid" || ! pid_alive "$legacy_pid"; then
        return 1
    fi

    local legacy_jar=""
    local old_ui_jar="$PROJECT_DIR/mousika-ui/target/mousika-ui.jar"
    if process_matches_jar "$legacy_pid" "$JAR"; then
        legacy_jar="$JAR"
    elif process_matches_jar "$legacy_pid" "$old_ui_jar"; then
        legacy_jar="$old_ui_jar"
    else
        warn "发现旧 PID 文件，但 PID $legacy_pid 不属于当前仓库，未接管"
        return 1
    fi

    local ports
    ports="$(listening_ports_for_pid "$legacy_pid")"
    local port_count
    port_count="$(printf '%s\n' "$ports" | awk 'NF { count++ } END { print count + 0 }')"
    if [ "$port_count" -ne 1 ]; then
        warn "无法唯一确认旧进程 PID $legacy_pid 的监听端口，未接管"
        return 1
    fi

    local legacy_port
    legacy_port="$(printf '%s\n' "$ports" | awk 'NF { print; exit }')"
    write_state "$legacy_pid" "$legacy_port" "$legacy_jar" "$DB_PATH" "$legacy_log_file"
    warn "已识别旧 .run 实例并迁移运行状态：PID ${legacy_pid}，端口 ${legacy_port}"
    load_state
}

load_or_adopt_state() {
    local result=0
    if load_state; then
        return 0
    else
        result=$?
    fi
    [ "$result" -eq 1 ] || return "$result"
    adopt_legacy_state
}

health_code() {
    local port="$1"
    local code
    code="$("$CURL_BIN" -sS -o /dev/null -w '%{http_code}' \
        "http://127.0.0.1:$port/api/flows?pageSize=1" 2>/dev/null || true)"
    printf '%s\n' "${code:-000}"
}

managed_instance_healthy() {
    pid_alive "$STATE_PID" \
        && process_matches_jar "$STATE_PID" "$STATE_JAR" \
        && pid_owns_port "$STATE_PID" "$STATE_PORT" \
        && [ "$(health_code "$STATE_PORT")" = "200" ]
}

wait_for_start() {
    local pid="$1"
    local port="$2"
    local elapsed=0
    while [ "$elapsed" -lt "$START_TIMEOUT" ]; do
        if ! pid_alive "$pid" || ! process_matches_jar "$pid" "$JAR"; then
            return 2
        fi
        if pid_owns_port "$pid" "$port" && [ "$(health_code "$port")" = "200" ]; then
            return 0
        fi
        sleep 1
        elapsed=$((elapsed + 1))
    done
    return 1
}

assert_port_available() {
    local owners
    owners="$(listening_pids "$PORT")"
    if [ -n "$owners" ]; then
        err "端口 $PORT 已被占用（PID：$(printf '%s' "$owners" | tr '\n' ' ')），未启动"
        return 1
    fi
}

cmd_build() {
    resolve_java_home
    resolve_maven
    info "使用 JDK：$JAVA_HOME"
    info "构建 $MODULE 可执行胖包"
    (cd "$PROJECT_DIR" && JAVA_HOME="$JAVA_HOME" "$MAVEN_BIN" -q -pl "$MODULE" -am -DskipTests package)
    info "构建完成：$JAR"
}

cmd_start() {
    validate_config
    resolve_runtime_tools

    local state_result=0
    if load_or_adopt_state; then
        if managed_instance_healthy; then
            warn "服务已运行：PID ${STATE_PID}，端口 ${STATE_PORT}"
            return 0
        fi
        if pid_alive "$STATE_PID" && process_matches_jar "$STATE_PID" "$STATE_JAR"; then
            err "受管进程 PID $STATE_PID 存活但状态异常，请先检查日志或执行 stop --force"
            return 1
        fi
        warn "清理已失效的运行状态"
        clear_state
    else
        state_result=$?
        [ "$state_result" -eq 1 ] || return "$state_result"
    fi

    resolve_java_home
    [ -f "$JAR" ] || cmd_build
    assert_port_available
    mkdir -p "$RUN_DIR" "$(dirname "$DB_PATH")"
    chmod 700 "$RUN_DIR"

    local java_options=(
        "-Dserver.address=$ADDRESS"
        "-Dserver.port=$PORT"
        "-Dmousika.db.path=$DB_PATH"
    )
    if [ -n "${MOUSIKA_JAVA_OPTS:-}" ]; then
        local extra_java_options=()
        read -r -a extra_java_options <<< "$MOUSIKA_JAVA_OPTS"
        java_options=("${extra_java_options[@]}" "${java_options[@]}")
    fi

    info "启动服务：${ADDRESS}:${PORT}，数据库：${DB_PATH}"
    nohup "$JAVA_HOME/bin/java" "${java_options[@]}" \
        -jar "$JAR" > "$DEFAULT_LOG_FILE" 2>&1 &
    local new_pid=$!
    if ! write_state "$new_pid" "$PORT" "$JAR" "$DB_PATH" "$DEFAULT_LOG_FILE"; then
        kill "$new_pid" 2>/dev/null || true
        err "无法写入运行状态，启动已取消：$STATE_FILE"
        return 1
    fi

    local result=0
    if wait_for_start "$new_pid" "$PORT"; then
        info "启动成功：PID ${new_pid}，端口 ${PORT}"
        printf '  规则流列表： http://127.0.0.1:%s/\n' "$PORT"
        printf '  原子规则库： http://127.0.0.1:%s/rules\n' "$PORT"
        printf '  API 基址：   http://127.0.0.1:%s/api\n' "$PORT"
        printf '  日志：       %s\n' "$DEFAULT_LOG_FILE"
        return 0
    else
        result=$?
    fi

    if [ "$result" -eq 2 ]; then
        clear_state
        err "启动进程已退出，日志末尾："
    else
        warn "进程仍存活，保留状态以便检查日志或执行 stop --force"
        err "启动超时或健康检查失败，日志末尾："
    fi
    tail -n 30 "$DEFAULT_LOG_FILE" 2>/dev/null || true
    return 1
}

cmd_stop() {
    local force="false"
    if [ "${1:-}" = "--force" ]; then
        force="true"
    elif [ -n "${1:-}" ]; then
        err "stop 仅支持可选参数 --force"
        return 1
    fi

    resolve_runtime_tools
    local state_result=0
    if load_or_adopt_state; then
        :
    else
        state_result=$?
        [ "$state_result" -eq 1 ] || return "$state_result"
        warn "没有当前脚本管理的运行实例"
        return 0
    fi
    if ! pid_alive "$STATE_PID"; then
        warn "PID $STATE_PID 已不存在，清理失效状态"
        clear_state
        return 0
    fi
    if ! process_matches_jar "$STATE_PID" "$STATE_JAR"; then
        err "PID $STATE_PID 的命令与记录的 JAR 不匹配，拒绝停止"
        return 1
    fi
    if ! pid_owns_port "$STATE_PID" "$STATE_PORT"; then
        if [ "$force" = "true" ]; then
            warn "PID $STATE_PID 未监听记录端口 ${STATE_PORT}，但 JAR 身份匹配，按 --force 继续停止"
        else
            err "PID $STATE_PID 未监听记录端口 ${STATE_PORT}，拒绝停止；确认后可执行 stop --force"
            return 1
        fi
    fi

    info "停止服务：PID ${STATE_PID}，端口 ${STATE_PORT}"
    kill "$STATE_PID"
    local elapsed=0
    while pid_alive "$STATE_PID" && [ "$elapsed" -lt "$STOP_TIMEOUT" ]; do
        sleep 1
        elapsed=$((elapsed + 1))
    done
    if pid_alive "$STATE_PID"; then
        if [ "$force" = "true" ]; then
            warn "优雅停止超时，按 --force 强制结束 PID $STATE_PID"
            kill -9 "$STATE_PID"
        else
            err "优雅停止超时；如确认需要强制结束，请执行：$0 stop --force"
            return 1
        fi
    fi
    clear_state
    info "服务已停止"
}

cmd_restart() {
    resolve_runtime_tools
    local state_result=0
    if load_or_adopt_state; then
        PORT="$STATE_PORT"
        [ -n "$STATE_DB" ] && DB_PATH="$STATE_DB"
        cmd_stop "${1:-}"
    else
        state_result=$?
        [ "$state_result" -eq 1 ] || return "$state_result"
    fi
    cmd_start
}

cmd_status() {
    validate_config
    resolve_runtime_tools
    local state_result=0
    if load_or_adopt_state; then
        :
    else
        state_result=$?
        [ "$state_result" -eq 1 ] || return "$state_result"
        local owners
        owners="$(listening_pids "$PORT")"
        if [ -n "$owners" ]; then
            warn "没有受管实例；配置端口 $PORT 被其他进程占用（PID：$(printf '%s' "$owners" | tr '\n' ' ')）"
        else
            warn "未运行"
        fi
        return 0
    fi

    if ! pid_alive "$STATE_PID"; then
        warn "实例已退出：PID ${STATE_PID}；运行状态已失效"
        return 1
    fi
    if ! process_matches_jar "$STATE_PID" "$STATE_JAR"; then
        err "PID $STATE_PID 与记录的 JAR 不匹配"
        return 1
    fi
    if ! pid_owns_port "$STATE_PID" "$STATE_PORT"; then
        err "PID $STATE_PID 未监听记录端口 $STATE_PORT"
        return 1
    fi

    local code
    code="$(health_code "$STATE_PORT")"
    info "运行中：PID ${STATE_PID}，端口 ${STATE_PORT}，健康 HTTP ${code}"
    printf '  项目：   %s\n' "$STATE_PROJECT"
    printf '  JAR：    %s\n' "$STATE_JAR"
    printf '  数据库： %s\n' "$STATE_DB"
    printf '  日志：   %s\n' "$STATE_LOG"
    [ "$code" = "200" ]
}

cmd_logs() {
    local follow="false"
    case "${1:-}" in
        '') ;;
        -f|--follow) follow="true" ;;
        *) err "logs 仅支持可选参数 -f/--follow"; return 1 ;;
    esac

    resolve_runtime_tools
    local log_file="$DEFAULT_LOG_FILE"
    local state_result=0
    if load_or_adopt_state; then
        log_file="$STATE_LOG"
    else
        state_result=$?
        [ "$state_result" -eq 1 ] || return "$state_result"
    fi
    if [ ! -f "$log_file" ]; then
        warn "暂无日志：$log_file"
        return 0
    fi
    if [ "$follow" = "true" ]; then
        tail -n 100 -f "$log_file"
    else
        tail -n 100 "$log_file"
    fi
}

cmd_dev() {
    validate_config
    resolve_java_home
    resolve_maven
    resolve_runtime_tools
    assert_port_available
    mkdir -p "$(dirname "$DB_PATH")"

    info "构建并安装 $MODULE 及其依赖"
    (cd "$PROJECT_DIR" && JAVA_HOME="$JAVA_HOME" "$MAVEN_BIN" -q -pl "$MODULE" -am -DskipTests install)
    info "开发模式：${ADDRESS}:${PORT}，Ctrl-C 退出"
    cd "$PROJECT_DIR"
    JAVA_HOME="$JAVA_HOME" exec "$MAVEN_BIN" -pl "$MODULE" \
        org.springframework.boot:spring-boot-maven-plugin:run \
        -Dspring-boot.run.jvmArguments="-Dserver.address=$ADDRESS -Dserver.port=$PORT -Dmousika.db.path=$DB_PATH"
}

cmd_doctor() {
    validate_config
    local failed=0

    printf '项目目录： %s\n' "$PROJECT_DIR"
    printf '运行目录： %s\n' "$RUN_DIR"
    printf '监听地址： %s:%s\n' "$ADDRESS" "$PORT"
    printf '数据库：   %s\n' "$DB_PATH"

    if resolve_java_home; then
        printf 'JDK：      %s\n' "$JAVA_HOME"
    else
        failed=1
    fi
    if resolve_maven; then
        printf 'Maven：    %s\n' "$MAVEN_BIN"
    else
        failed=1
    fi
    if resolve_runtime_tools; then
        printf 'curl：     %s\n' "$CURL_BIN"
        printf '端口工具： %s\n' "$PORT_TOOL"
    else
        failed=1
    fi

    if [ "$failed" -ne 0 ]; then
        err "环境检查未通过"
        return 1
    fi
    info "环境检查通过"
}

usage() {
    cat <<EOF
Mousika 规则编排服务管理

用法：$0 <命令> [参数]

命令：
  build           构建 mousika-web 可执行胖包
  start           后台启动并等待健康检查
  stop [--force]  停止受管实例；--force 允许端口异常或超时后强制结束
  restart [--force]
                  使用已记录的端口和数据库重启
  status          显示受管实例、真实端口和健康状态
  logs [-f]       查看最近 100 行日志；-f 持续跟踪
  dev             前台运行 Spring Boot 开发模式
  doctor          检查 JDK、Maven、curl 和端口工具
  help            显示帮助

环境变量：
  MOUSIKA_PORT           新实例端口，默认 8080
  MOUSIKA_SERVER_ADDRESS 监听地址，默认 127.0.0.1
  MOUSIKA_DB_PATH        SQLite 路径，默认 data/mousika.db
  MOUSIKA_RUN_DIR        状态与日志目录，默认 \${TMPDIR:-/tmp}/mousika
  MOUSIKA_START_TIMEOUT  启动等待秒数，默认 60
  MOUSIKA_STOP_TIMEOUT   停止等待秒数，默认 15
  MOUSIKA_JAVA_OPTS      额外 JVM 参数
EOF
}

command_name="${1:-help}"
shift || true
case "$command_name" in
    build) cmd_build "$@" ;;
    start) cmd_start "$@" ;;
    stop) cmd_stop "$@" ;;
    restart) cmd_restart "$@" ;;
    status) cmd_status "$@" ;;
    logs) cmd_logs "$@" ;;
    dev) cmd_dev "$@" ;;
    doctor) cmd_doctor "$@" ;;
    help|-h|--help) usage ;;
    *) err "未知命令：$command_name"; usage; exit 1 ;;
esac
