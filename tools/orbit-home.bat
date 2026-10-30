@echo off
setlocal EnableDelayedExpansion
rem Orbit as default launcher on Mi Box S (Android 9)
rem   orbit-home.bat install 192.168.1.9 orbit-launcher.apk
rem   orbit-home.bat update  192.168.1.9 orbit-launcher.apk
rem   orbit-home.bat rollback 192.168.1.9

set "MODE=%~1"
set "IP=%~2"
set "APK=%~3"
set "ORBIT=dev.orbit.launcher"

if "%MODE%"=="" goto usage
if "%IP%"=="" goto usage
where adb >nul 2>&1
if errorlevel 1 (
  echo [X] adb not found. Run first:  $env:Path += ";C:\platform-tools"
  exit /b 1
)

set "T=%IP%:5555"
echo %IP% | find ":" >nul && set "T=%IP%"

echo [..] Connecting to %T%  - accept "Allow USB debugging" on the TV
adb connect %T% >nul
adb -s %T% wait-for-device

if /i "%MODE%"=="rollback" goto rollback
if not exist "%APK%" (
  echo [X] APK not found: %APK%
  exit /b 1
)

echo [..] Installing Orbit
adb -s %T% install -r "%APK%"
if errorlevel 1 (
  echo [X] Install failed
  exit /b 1
)
if /i "%MODE%"=="update" goto home

echo [..] Test launch
adb -s %T% shell am start -W -n %ORBIT%/.MainActivity
timeout /t 2 >nul
adb -s %T% shell pidof %ORBIT% >nul 2>&1
if errorlevel 1 (
  echo [X] Orbit did not start - stock launcher left untouched
  exit /b 1
)

echo [..] Disabling stock launcher
adb -s %T% shell pm disable-user --user 0 com.google.android.tvlauncher
adb -s %T% shell pm disable-user --user 0 com.google.android.tvrecommendations

:home
adb -s %T% shell input keyevent KEYCODE_HOME
echo [OK] Done. Press Home on the remote - Orbit should open.
echo      Rollback:  %~nx0 rollback %IP%
exit /b 0

:rollback
adb -s %T% shell pm enable com.google.android.tvlauncher
adb -s %T% shell pm enable com.google.android.tvrecommendations
adb -s %T% shell input keyevent KEYCODE_HOME
echo [OK] Stock launcher restored
exit /b 0

:usage
echo Usage:
echo   %~nx0 install  ^<IP^> ^<apk^>
echo   %~nx0 update   ^<IP^> ^<apk^>
echo   %~nx0 rollback ^<IP^>
exit /b 1
