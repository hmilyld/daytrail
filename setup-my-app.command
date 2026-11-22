#!/usr/bin/env bash
# DayTrail 一键改造向导入口（macOS 双击会用「终端」打开本文件）。
# 参数会原样传给 scripts/setup-my-app.sh，供 AI 或自动化使用。

cd "$(dirname "$0")" || exit 1

bash scripts/setup-my-app.sh "$@"
status=$?

# 只有双击（无参数）时才停住窗口，避免自动化被卡在等待回车。
if [ $# -eq 0 ]; then
    echo
    read -r -p '按回车关闭窗口' _ || true
fi

exit $status
