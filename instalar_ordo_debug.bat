@echo off
setlocal

set "ROOT=%~dp0"
set "ADB=C:\Users\Usuario\AppData\Local\Android\Sdk\platform-tools\adb.exe"
set "APK=%ROOT%app\build\outputs\apk\debug\app-debug.apk"

cd /d "%ROOT%"

if not exist "%ADB%" (
  echo ADB nao encontrado em:
  echo %ADB%
  echo.
  echo Verifique a instalacao do Android SDK.
  pause
  exit /b 1
)

echo Verificando telefone conectado...
"%ADB%" devices
echo.

echo Compilando Ordo debug...
call "%ROOT%gradlew.bat" --no-daemon --console=plain :app:assembleDebug
if errorlevel 1 (
  echo.
  echo Falha ao compilar o APK debug.
  pause
  exit /b 1
)

if not exist "%APK%" (
  echo.
  echo APK nao encontrado em:
  echo %APK%
  pause
  exit /b 1
)

echo.
echo Instalando/atualizando Ordo no telefone...
"%ADB%" install -r "%APK%"
if errorlevel 1 (
  echo.
  echo Nao foi possivel instalar.
  echo Se o telefone aparecer como unauthorized, desbloqueie o aparelho e aceite a depuracao USB.
  echo Se aparecer INSTALL_FAILED_VERSION_DOWNGRADE, desinstale apenas a versao debug antiga ou aumente o versionCode.
  pause
  exit /b 1
)

echo.
echo Ordo debug instalado/atualizado com sucesso.
pause
