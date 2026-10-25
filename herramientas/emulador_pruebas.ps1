# -*- coding: utf-8 -*-
# Rescate Accesible - Pruebas en el emulador (NUNCA en un movil real).
#
#   emulador_pruebas.ps1 -Que voz          pruebas de las voces, el motor propio y las grabaciones
#   emulador_pruebas.ps1 -Que accesibilidad recorrido de todas las pantallas con el Accessibility Test Framework
#   emulador_pruebas.ps1 -Que grabaciones   genera las grabaciones de respaldo incluidas (Pico y eSpeak, es y en)
#                                           y las copia a app\src\main\assets\grabaciones
#   emulador_pruebas.ps1 -Que simulacion    simula todas las averias, las arregla como una persona ciega
#                                           y genera el informe (assets\informe-pruebas.html y docs)
#
# Usa solo el emulador (ANDROID_SERIAL=emulator-...): la simulacion cambia servicios y
# ajustes de accesibilidad, y eso no se hace nunca en el movil de alguien.

param(
	[ValidateSet("voz", "accesibilidad", "grabaciones", "simulacion")]
	[string]$Que = "voz"
)

$ErrorActionPreference = "Stop"
$Raiz = Split-Path -Parent $PSScriptRoot
$Entorno = Join-Path $Raiz "entorno"
$env:JAVA_HOME = Join-Path $Entorno "jdk"
$env:ANDROID_HOME = Join-Path $Entorno "android-sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:GRADLE_USER_HOME = Join-Path $Entorno "gradle-home"
$Adb = Join-Path $env:ANDROID_HOME "platform-tools\adb.exe"
$Gradle = Join-Path $Entorno "gradle\bin\gradle.bat"
$Paquete = "com.xebolax.rescate.pruebas"

$emulador = & $Adb devices | Select-String "^emulator-\S+\s+device" | ForEach-Object { ($_ -split "\s+")[0] } | Select-Object -First 1
if (-not $emulador) {
	Write-Host "[ERROR] No hay ningun emulador abierto. Abrelo con compilar.bat (opcion 5)." -ForegroundColor Red
	exit 1
}
$env:ANDROID_SERIAL = $emulador
Write-Host "[Emulador] Usando $emulador"

$clase = switch ($Que) {
	"voz" { "com.xebolax.rescate.VozInstrumentadaTest" }
	"accesibilidad" { "com.xebolax.rescate.AccesibilidadTest" }
	"grabaciones" { "com.xebolax.rescate.VozInstrumentadaTest#generarGrabacionesIncluidas" }
	"simulacion" { "com.xebolax.rescate.SimulacionAveriasTest" }
}
$argumentos = @("--no-daemon", "-p", $Raiz, "connectedDebugAndroidTest", "-Pandroid.testInstrumentationRunnerArguments.class=$clase")
if ($Que -eq "grabaciones" -or $Que -eq "simulacion") { $argumentos += "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true" }
& $Gradle @argumentos
$codigo = $LASTEXITCODE

if ($Que -eq "grabaciones") {
	$destino = Join-Path $Raiz "app\src\main\assets\grabaciones"
	if (Test-Path $destino) { Remove-Item -Recurse -Force $destino }
	New-Item -ItemType Directory -Force $destino | Out-Null
	& $Adb pull "/sdcard/Android/data/$Paquete/files/grabaciones-generadas/." $destino
	Write-Host "[OK] Grabaciones copiadas a $destino. Vuelve a compilar para incluirlas."
}
if ($Que -eq "simulacion") {
	$json = Join-Path $Raiz "docs\simulacion_informe.json"
	& $Adb pull "/sdcard/Android/data/$Paquete/files/simulacion/informe.json" $json
	if (Test-Path $json) {
		& powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot "informe_simulacion.ps1") -Json $json
		Write-Host "[OK] Informe en la app (Ajustes, Informe de pruebas) y en docs\informe_simulaciones.md. Vuelve a compilar para incluirlo."
	}
}
& $Adb shell am force-stop $Paquete | Out-Null
exit $codigo
