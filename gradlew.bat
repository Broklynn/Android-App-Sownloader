@rem Minimal Windows launcher for this local project.
@rem It uses the Android Studio JBR when JAVA_HOME is not configured and falls back to a
@rem Gradle distribution already present in the user's Gradle cache.
@echo off
setlocal

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

if defined JAVA_HOME (
  set JAVA_EXE=%JAVA_HOME%\bin\java.exe
) else if exist "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" (
  set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
  set JAVA_EXE=C:\Program Files\Android\Android Studio\jbr\bin\java.exe
) else (
  set JAVA_EXE=java.exe
)

if not exist "%JAVA_EXE%" (
  echo ERROR: Java was not found. Set JAVA_HOME to JDK 17 or newer.
  exit /b 1
)

if not defined GRADLE_USER_HOME (
  set GRADLE_USER_HOME=%USERPROFILE%\.gradle
)

if exist "%APP_HOME%gradle\wrapper\gradle-wrapper.jar" (
  "%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% -classpath "%APP_HOME%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
  exit /b %ERRORLEVEL%
)

set GRADLE_BAT=
for /d %%d in ("%GRADLE_USER_HOME%\wrapper\dists\gradle-9.5.0-milestone-1-bin\*") do (
  if exist "%%~fd\gradle-9.5.0-milestone-1\bin\gradle.bat" (
    set GRADLE_BAT=%%~fd\gradle-9.5.0-milestone-1\bin\gradle.bat
    goto foundGradle
  )
)

:foundGradle
if "%GRADLE_BAT%"=="" (
  echo ERROR: Gradle launcher was not found in %%USERPROFILE%%\.gradle\wrapper\dists.
  echo Open the project in Android Studio or install Gradle, then run %APP_BASE_NAME% again.
  exit /b 1
)

call "%GRADLE_BAT%" %*
exit /b %ERRORLEVEL%
