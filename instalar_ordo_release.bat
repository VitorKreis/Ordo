@echo off
setlocal

set "ROOT=%~dp0"
set "ADB=C:\Users\Usuario\AppData\Local\Android\Sdk\platform-tools\adb.exe"
set "APK=%ROOT%app\build\outputs\apk\release\app-release.apk"
set "PACKAGE=com.vitorkrichter.ordo"

cd /d "%ROOT%"

if not exist "%ADB%" (
  echo ADB nao encontrado em:
  echo %ADB%
  echo.
  echo Verifique a instalacao do Android SDK.
  pause
  exit /b 1
)

if not exist "%ROOT%signing.properties" (
  echo Arquivo signing.properties nao encontrado.
  echo Ele e necessario para gerar o APK release assinado.
  pause
  exit /b 1
)

echo Verificando telefone conectado...
"%ADB%" devices
echo.

echo Compilando Ordo release...
call "%ROOT%gradlew.bat" --no-daemon --console=plain :app:assembleRelease
if errorlevel 1 (
  echo.
  echo Falha ao compilar o APK release.
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
  echo Se aparecer INSTALL_FAILED_VERSION_DOWNGRADE, aumente o versionCode antes de instalar.
  pause
  exit /b 1
)

echo.
echo Abrindo Ordo...
"%ADB%" shell monkey -p "%PACKAGE%" -c android.intent.category.LAUNCHER 1

echo.
echo Ordo release instalado/atualizado com sucesso.
pause
