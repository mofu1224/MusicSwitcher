@echo off
setlocal
pushd "%~dp0" || exit /b 1
set "INTERACTIVE="
if "%~1"=="" set "INTERACTIVE=1"
set "GRADLE_USER_HOME=%CD%\.local\gradle"
if exist "%CD%\.local\toolchain\jdk\bin\java.exe" set "JAVA_HOME=%CD%\.local\toolchain\jdk"
if exist "%CD%\.local\toolchain\sdk\platforms\android-36\android.jar" (
    set "ANDROID_HOME=%CD%\.local\toolchain\sdk"
    set "ANDROID_SDK_ROOT=%CD%\.local\toolchain\sdk"
)
if not defined ANDROID_HOME if defined ANDROID_SDK_ROOT set "ANDROID_HOME=%ANDROID_SDK_ROOT%"
if not defined ANDROID_HOME if exist "%LOCALAPPDATA%\Android\Sdk\platforms\android-36\android.jar" set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"
if defined INTERACTIVE (
    call "%~dp0gradlew.bat" :app:assembleDebug
) else (
    call "%~dp0gradlew.bat" %*
)
set "RESULT=%ERRORLEVEL%"
popd
if defined INTERACTIVE pause
exit /b %RESULT%
