#!/usr/bin/env bash
#
# Mousika 规则编排 - 服务管理脚本
# 用法: ./mousika.sh {start|stop|restart|status|build|logs|help}
#
set -euo pipefail

# ---------- 配置 ----------
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODULE="mousika-web"
JAR="$PROJECT_DIR/$MODULE/target/$MODULE.jar"
PORT="${MOUSIKA_PORT:-8080}"
# 数据库固定到仓库根 data/（与默认 ./data 一致），绝对路径避免按启动 CWD 解析成别处
DB_PATH="${MOUSIKA_DB_PATH:-$PROJECT_DIR/data/mousika.db}"
RUN_DIR="$PROJECT_DIR/.run"
PID_FILE="$RUN_DIR/mousika.pid"
LOG_FILE="$RUN_DIR/mousika.log"
HEALTH_URL="http://127.0.0.1:$PORT/api/flows?pageSize=1"
BASE_URL="http://localhost:$PORT"

# ---------- JDK 21 解析 ----------
resolve_java_home() {
    if [ -n "${JAVA_HOME:-}" ] && "$JAVA_HOME/bin/java" -version 2>&1 | grep -q '"21'; then
        return
    fi
    for cand in \
        "/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home" \
        "$(/usr/libexec/java_home -v 21 2>/dev/null || true)"; do
        if [ -n "$cand" ] && [ -x "$cand/bin/java" ]; then
            export JAVA_HOME="$cand"
            return
        fi
    done
    echo "✗ 未找到 JDK 21，请设置 JAVA_HOME 指向 JDK 21" >&2
    exit 1
}

C_GREEN="\033[0;32m"; C_RED="\033[0;31m"; C_YEL="\033[0;33m"; C_OFF="\033[0m"
info() { echo -e "${C_GREEN}▸${C_OFF} $*"; }
warn() { echo -e "${C_YEL}▸${C_OFF} $*"; }
err()  { echo -e "${C_RED}✗${C_OFF} $*" >&2; }

# ---------- 进程/端口探测 ----------
port_pids() { lsof -ti "tcp:$PORT" -sTCP:LISTEN 2>/dev/null || true; }
pid_alive() { [ -n "${1:-}" ] && kill -0 "$1" 2>/dev/null; }
pid_owns_port() { [ -n "${1:-}" ] && port_pids | grep -qx "$1"; }
health_ok() { [ "$(curl -s -o /dev/null -w '%{http_code}' "$HEALTH_URL" 2>/dev/null || echo 000)" = "200" ]; }

# 本脚本 PID 文件记录、且仍存活的实例 PID（否则输出空）
our_pid() {
    [ -f "$PID_FILE" ] || return 0
    local p; p="$(cat "$PID_FILE" 2>/dev/null || true)"
    pid_alive "$p" && echo "$p" || true
}

# 端口只能由本脚本管理的实例占用；绝不结束来源不明的进程。
assert_port_available() {
    local pids; pids="$(port_pids)"
    [ -z "$pids" ] && return 0
    err "端口 $PORT 已被占用 (PID: $(echo "$pids" | tr '\n' ' '))，未执行启动"
    err "请先确认进程归属；本脚本不会结束未受管进程"
    return 1
}

# 等待“本实例(PID=$1)”既占住端口又通过健康检查（避免被别的进程的 200 骗过）
# 返回：0 成功；2 进程已退出（多为端口冲突/启动异常）；1 超时
wait_healthy_owned() {
    local expect="$1"
    for _ in $(seq 1 60); do
        pid_alive "$expect" || return 2
        if pid_owns_port "$expect" && health_ok; then return 0; fi
        sleep 1
    done
    return 1
}

# ---------- 命令 ----------
cmd_build() {
    resolve_java_home
    info "使用 JDK: $JAVA_HOME"
    info "打包可执行胖包 (mvn -pl $MODULE -am -DskipTests package) ..."
    (cd "$PROJECT_DIR" && mvn -q -pl "$MODULE" -am -DskipTests package)
    info "打包完成: $JAR"
}

cmd_start() {
    # 本脚本实例已正常在跑则幂等返回
    local p; p="$(our_pid)"
    if [ -n "$p" ]; then
        if pid_owns_port "$p" && health_ok; then
            warn "服务已在运行 (PID $p, 端口 $PORT)。如需重启用: $0 restart"
            return 0
        fi
        if pid_owns_port "$p"; then
            err "受管服务 PID $p 已占用端口但健康检查失败，请先执行: $0 stop"
            return 1
        fi
        warn "清理不再占用端口的陈旧 PID 文件 (PID $p)"
        rm -f "$PID_FILE"
    fi
    resolve_java_home
    [ -f "$JAR" ] || { warn "未找到胖包，先执行构建"; cmd_build; }
    mkdir -p "$RUN_DIR" "$(dirname "$DB_PATH")"
    assert_port_available
    info "启动中 (端口 $PORT, DB $DB_PATH) ..."
    nohup "$JAVA_HOME/bin/java" -Dserver.port="$PORT" -Dmousika.db.path="$DB_PATH" \
        -jar "$JAR" > "$LOG_FILE" 2>&1 &
    local newpid=$!; echo "$newpid" > "$PID_FILE"
    if wait_healthy_owned "$newpid"; then
        info "启动成功 (PID $newpid, DB $DB_PATH)"
        echo    "  规则流列表     : $BASE_URL/"
        echo    "  原子规则库     : $BASE_URL/rules"
        echo    "  API 基址       : $BASE_URL/api"
        echo    "  日志           : $LOG_FILE"
    else
        local rc=$?
        rm -f "$PID_FILE" 2>/dev/null || true
        if [ "$rc" = "2" ]; then
            err "启动进程已退出（多为端口冲突或启动异常）。日志末尾："
        else
            err "启动超时/健康检查未通过。日志末尾："
        fi
        tail -n 25 "$LOG_FILE" 2>/dev/null || true
        exit 1
    fi
}

cmd_stop() {
    local p; p="$(our_pid)"
    if [ -z "$p" ]; then
        warn "没有本脚本管理的运行实例"
        [ -n "$(port_pids)" ] && warn "端口 $PORT 由其他进程占用，未作处理"
        rm -f "$PID_FILE" 2>/dev/null || true
        return 0
    fi
    if ! pid_owns_port "$p"; then
        warn "PID 文件中的进程 $p 不再监听端口 $PORT，未结束该进程"
        rm -f "$PID_FILE" 2>/dev/null || true
        return 0
    fi
    info "停止服务 (PID $p) ..."
    kill "$p" 2>/dev/null || true
    for _ in $(seq 1 15); do pid_alive "$p" || break; sleep 1; done
    if pid_alive "$p"; then warn "优雅停止超时，强制结束受管进程"; kill -9 "$p" 2>/dev/null || true; fi
    rm -f "$PID_FILE" 2>/dev/null || true
    info "已停止"
}

cmd_restart() { cmd_stop; cmd_start; }

cmd_dev() {
    resolve_java_home
    info "使用 JDK: $JAVA_HOME"
    mkdir -p "$(dirname "$DB_PATH")"
    assert_port_available
    info "先构建并安装 mousika-web 及其模块依赖 ..."
    (cd "$PROJECT_DIR" && JAVA_HOME="$JAVA_HOME" mvn -q -pl "$MODULE" -am -DskipTests install)
    info "开发模式 (spring-boot:run，前台运行，Ctrl-C 退出)"
    info "改前端后另开终端执行  mvn -pl $MODULE -o resources:resources  即热生效，无需重打包"
    echo    "  入口: $BASE_URL/   DB: $DB_PATH"
    cd "$PROJECT_DIR"
    JAVA_HOME="$JAVA_HOME" exec mvn -pl "$MODULE" org.springframework.boot:spring-boot-maven-plugin:run \
        -Dspring-boot.run.jvmArguments="-Dserver.port=$PORT -Dmousika.db.path=$DB_PATH"
}

cmd_status() {
    local occ; occ="$(port_pids | head -1)"
    if [ -z "$occ" ]; then warn "未运行"; return 0; fi
    local p; p="$(our_pid)"; local ours="否"
    { [ -n "$p" ] && pid_owns_port "$p"; } && ours="是"
    local code; code="$(curl -s -o /dev/null -w '%{http_code}' "$HEALTH_URL" 2>/dev/null || echo 000)"
    info "运行中 (端口 $PORT · PID $occ · 本脚本实例:$ours · 健康 HTTP $code)"
    echo "  入口: $BASE_URL/"
    [ "$ours" = "否" ] && warn "占用端口的不是本脚本启动的实例，可能连错库；建议 $0 restart 接管"
    return 0
}

cmd_logs() {
    [ -f "$LOG_FILE" ] || { warn "暂无日志: $LOG_FILE"; return 0; }
    tail -f "$LOG_FILE"
}

usage() {
    cat <<EOF
Mousika 规则编排 - 服务管理

用法: $0 <命令>

命令:
  start     启动服务(缺胖包会自动构建)，后台运行并等待健康检查通过
  stop      停止服务(优雅停止，超时则强制)
  restart   重启服务
  dev       开发模式前台启动(spring-boot:run)，改前端 resources:resources 即热生效
  status    查看运行状态与健康检查
  build     仅构建可执行胖包
  logs      实时查看日志(tail -f)
  help      显示本帮助

环境变量:
  MOUSIKA_PORT     监听端口(默认 8080)
  MOUSIKA_DB_PATH  SQLite 库路径(默认 data/mousika.db)
EOF
}

case "${1:-help}" in
    start)   cmd_start ;;
    stop)    cmd_stop ;;
    restart) cmd_restart ;;
    dev)     cmd_dev ;;
    status)  cmd_status ;;
    build)   cmd_build ;;
    logs)    cmd_logs ;;
    help|-h|--help) usage ;;
    *) err "未知命令: $1"; usage; exit 1 ;;
esac
