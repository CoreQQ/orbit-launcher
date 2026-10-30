#!/usr/bin/env bash
# Orbit → лаунчер по умолчанию на Mi Box S (Android 9)
#   ./orbit-home.sh install 192.168.1.50 orbit-launcher.apk
#   ./orbit-home.sh rollback 192.168.1.50
set -u

MODE="${1:-}"; IP="${2:-}"; APK="${3:-}"
ORBIT="dev.orbit.launcher"
STOCK=(com.google.android.tvlauncher com.google.android.leanbacklauncher)
EXTRA=(com.google.android.tvrecommendations)   # фоновые рекомендации стока

die() { echo "❌ $*"; exit 1; }
[ -n "$MODE" ] && [ -n "$IP" ] || die "Использование: $0 install <IP> <apk> | rollback <IP>"
command -v adb >/dev/null || die "adb не найден (Android platform-tools)"

case "$IP" in *:*) TARGET="$IP" ;; *) TARGET="$IP:5555" ;; esac
A() { adb -s "$TARGET" "$@"; }

echo "→ Подключение к $TARGET (на ТВ подтверди «Разрешить отладку»)"
adb connect "$TARGET" >/dev/null
for _ in $(seq 1 20); do
  [ "$(adb -s "$TARGET" get-state 2>/dev/null)" = "device" ] && break
  sleep 1
done
[ "$(adb -s "$TARGET" get-state 2>/dev/null)" = "device" ] || die "Нет связи. Включи «Отладка по USB» и проверь IP."

has_pkg() { A shell pm list packages "$1" | tr -d '\r' | grep -qx "package:$1"; }

if [ "$MODE" = "rollback" ]; then
  for p in "${STOCK[@]}" "${EXTRA[@]}"; do
    has_pkg "$p" && A shell pm enable "$p" >/dev/null && echo "✓ включён $p"
  done
  A shell input keyevent KEYCODE_HOME
  echo "✓ Стоковый лаунчер возвращён"
  exit 0
fi

[ "$MODE" = "install" ] || die "Режим: install или rollback"
[ -f "$APK" ] || die "APK не найден: $APK"

echo "→ Установка Orbit"
A install -r "$APK" | tail -1
has_pkg "$ORBIT" || die "Orbit не установился"

echo "→ Проверка запуска"
A shell am start -W -n "$ORBIT/.MainActivity" | tr -d '\r' | grep -E "Status|TotalTime"
sleep 2
A shell pidof "$ORBIT" >/dev/null || die "Orbit не запустился — стоковый лаунчер НЕ трогаю"

echo "→ Отключение стокового лаунчера"
for p in "${STOCK[@]}"; do
  if has_pkg "$p"; then A shell pm disable-user --user 0 "$p" | tr -d '\r'; fi
done
read -r -p "Отключить и фоновые рекомендации стока (экономит RAM)? [Y/n] " ans
if [ "${ans:-Y}" != "n" ]; then
  for p in "${EXTRA[@]}"; do has_pkg "$p" && A shell pm disable-user --user 0 "$p" | tr -d '\r'; done
fi

echo "→ Нажимаю Home"
A shell input keyevent KEYCODE_HOME
sleep 2
TOP=$(A shell dumpsys activity activities | tr -d '\r' | grep -m1 -E "mResumedActivity|ResumedActivity")
echo "  $TOP"
if echo "$TOP" | grep -q "$ORBIT"; then
  echo "✅ Готово: Orbit — домашний экран"
else
  echo "⚠️  Наверху не Orbit. Если на ТВ окно выбора — выбери Orbit → «Всегда»."
  echo "   Откат: $0 rollback $IP"
fi
