#!/usr/bin/env sh
set -eu

APP_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
JAR_PATH=${JAR_PATH:-"$APP_DIR/voice-backend-1.0.0.jar"}
CONFIG_PATH=${CONFIG_PATH:-"$APP_DIR/application.yml"}

if [ ! -f "$JAR_PATH" ]; then
    echo "找不到后端 JAR: $JAR_PATH" >&2
    exit 1
fi

if [ ! -f "$CONFIG_PATH" ]; then
    echo "找不到外部配置: $CONFIG_PATH" >&2
    echo "请将 application.yml 放在 JAR 同目录。" >&2
    exit 1
fi

exec java ${JAVA_OPTS:-} -jar "$JAR_PATH" \
    --spring.config.additional-location="file:$CONFIG_PATH"