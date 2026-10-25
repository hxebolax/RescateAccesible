# -*- coding: utf-8 -*-
# Rescate Accesible - Genera el informe de pruebas y simulaciones de averías.
#
# Lee el informe.json que deja SimulacionAveriasTest en el emulador
# (Android/data/<paquete>/files/simulacion/informe.json, recogido con adb pull)
# y escribe:
#   - app\src\main\assets\informe-pruebas.html  (se ve en la app: Ajustes > Informe de pruebas)
#   - docs\informe_simulaciones.md              (el mismo informe en el proyecto)
#
# Uso: informe_simulacion.ps1 -Json ruta\informe.json [-Extra ruta\otras_pruebas.json]

param(
	[Parameter(Mandatory = $true)][string]$Json,
	[string]$Extra = ""
)

$ErrorActionPreference = "Stop"
$Raiz = Split-Path -Parent $PSScriptRoot
$Utf8 = New-Object System.Text.UTF8Encoding($false)
$datos = [IO.File]::ReadAllText($Json, $Utf8) | ConvertFrom-Json
$otras = $null
if ($Extra -and (Test-Path $Extra)) { $otras = [IO.File]::ReadAllText($Extra, $Utf8) | ConvertFrom-Json }

function Html($t) { return [System.Net.WebUtility]::HtmlEncode([string]$t) }

$escenarios = @($datos.escenarios)
$superados = @($escenarios | Where-Object { $_.superado }).Count
$total = $escenarios.Count
$comprobaciones = 0
$bien = 0
foreach ($e in $escenarios) { foreach ($c in @($e.comprobaciones)) { $comprobaciones++; if ($c.bien) { $bien++ } } }

$h = New-Object System.Text.StringBuilder
[void]$h.AppendLine('<!DOCTYPE html><html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">')
[void]$h.AppendLine('<title>Informe de pruebas y simulaciones de averías</title>')
[void]$h.AppendLine('<style>body{font-family:sans-serif;font-size:1.05em;line-height:1.5;margin:1em;color:#201a19;background:#fffbff}h1,h2,h3{color:#93000a}.bien{color:#1b5e20;font-weight:bold}.mal{color:#b3261e;font-weight:bold}li{margin:.3em 0}li a{display:inline-block;min-height:48px;line-height:48px}@media(prefers-color-scheme:dark){body{color:#ede0de;background:#201a19}h1,h2,h3{color:#ffb4ab}.bien{color:#a5d6a7}.mal{color:#ffb4ab}}</style></head><body>')
[void]$h.AppendLine('<h1>Informe de pruebas y simulaciones de averías</h1>')
[void]$h.AppendLine("<p>Rescate Accesible $(Html $datos.version_app). Simulación hecha el $(Html $datos.fecha) en un emulador $(Html $datos.dispositivo) con Android $(Html $datos.android) y TalkBack $(Html $datos.talkback).</p>")
[void]$h.AppendLine('<p lang="en">English: this report lists every simulated accessibility failure, what the user did (keys, touches, gestures) and how the app recovered. The details are in Spanish.</p>')
[void]$h.AppendLine('<h2>Resumen</h2><ul>')
[void]$h.AppendLine("<li>Escenarios superados: <strong>$superados de $total</strong>.</li>")
[void]$h.AppendLine("<li>Comprobaciones correctas: <strong>$bien de $comprobaciones</strong>.</li>")
[void]$h.AppendLine('<li>Cada escenario rompe algo de verdad en el emulador, lo arregla manejando la app como lo haría una persona ciega (teclas de volumen, arrastrar el dedo por el menú que habla y levantarlo, confirmar con volumen arriba) y comprueba el resultado leyendo los ajustes y servicios reales del sistema. Nada se da por bueno sin comprobarlo.</li>')
[void]$h.AppendLine('<li>El atajo de accesibilidad (mantener pulsadas las dos teclas de volumen) no se puede pulsar desde una prueba automática: se simula activando el servicio del atajo, que es exactamente lo que hace Android al detectarlo. Las demás teclas, toques y arrastres son pulsaciones reales enviadas al emulador.</li>')
[void]$h.AppendLine('</ul>')
[void]$h.AppendLine('<h2>Índice</h2><ul>')
foreach ($e in $escenarios) {
	$estado = if ($e.superado) { 'superado' } else { 'con fallos' }
	[void]$h.AppendLine("<li><a href=""#$($e.id)"">$(Html $e.id). $(Html $e.titulo) ($estado)</a></li>")
}
[void]$h.AppendLine('</ul>')
foreach ($e in $escenarios) {
	$estado = if ($e.superado) { '<span class="bien">Superado</span>' } else { '<span class="mal">Con fallos</span>' }
	[void]$h.AppendLine("<h2 id=""$($e.id)"">$(Html $e.id). $(Html $e.titulo)</h2>")
	[void]$h.AppendLine("<p>Resultado: $estado ($($e.segundos) segundos).</p>")
	[void]$h.AppendLine('<h3>La avería simulada</h3>')
	[void]$h.AppendLine("<p>$(Html $e.averia)</p>")
	[void]$h.AppendLine('<h3>Lo que hizo el usuario y la app</h3><ol>')
	foreach ($p in @($e.pasos)) { [void]$h.AppendLine("<li>$(Html $p)</li>") }
	[void]$h.AppendLine('</ol><h3>Comprobaciones del estado real</h3><ul>')
	foreach ($c in @($e.comprobaciones)) {
		$marca = if ($c.bien) { '<span class="bien">Bien:</span>' } else { '<span class="mal">Mal:</span>' }
		[void]$h.AppendLine("<li>$marca $(Html $c.texto)</li>")
	}
	[void]$h.AppendLine('</ul>')
}
if ($otras) {
	[void]$h.AppendLine('<h2>Otras pruebas</h2><ul>')
	foreach ($o in @($otras)) { [void]$h.AppendLine("<li>$(Html $o)</li>") }
	[void]$h.AppendLine('</ul>')
}
[void]$h.AppendLine('<h2>Lo que no se puede simular en el emulador</h2><ul>')
[void]$h.AppendLine('<li>Auriculares Bluetooth que se llevan el sonido: el emulador no tiene Bluetooth. La app lo detecta con la lista de salidas de audio y puede mandar la voz al altavoz.</li>')
[void]$h.AppendLine('<li>Pulsar de verdad las dos teclas de volumen a la vez (se simula, ver arriba) y la pregunta de Android la primera vez que se usa el atajo.</li>')
[void]$h.AppendLine('<li>Los ajustes y textos de cada fabricante (Samsung, Xiaomi, OnePlus…): la guía por los Ajustes se ha probado con los Ajustes de Android puro; en otros móviles hay que comprobarla a mano.</li>')
[void]$h.AppendLine('<li>Oír la voz: el emulador se ejecuta sin audio. Se comprueba que la voz produce sonido (muestras sintetizadas, motor que entrega frases) y que TalkBack pide frases al motor de rescate.</li>')
[void]$h.AppendLine('</ul></body></html>')
$destino = Join-Path $Raiz "app\src\main\assets\informe-pruebas.html"
[IO.File]::WriteAllText($destino, $h.ToString(), $Utf8)

# El mismo informe en Markdown para el proyecto
$m = New-Object System.Text.StringBuilder
[void]$m.AppendLine("# Informe de pruebas y simulaciones de averías")
[void]$m.AppendLine("")
[void]$m.AppendLine("Rescate Accesible $($datos.version_app). Simulación del $($datos.fecha) en $($datos.dispositivo), Android $($datos.android), TalkBack $($datos.talkback).")
[void]$m.AppendLine("")
[void]$m.AppendLine("- Escenarios superados: **$superados de $total**.")
[void]$m.AppendLine("- Comprobaciones correctas: **$bien de $comprobaciones**.")
[void]$m.AppendLine("")
foreach ($e in $escenarios) {
	$estado = if ($e.superado) { 'Superado' } else { 'Con fallos' }
	[void]$m.AppendLine("## $($e.id). $($e.titulo)")
	[void]$m.AppendLine("")
	[void]$m.AppendLine("Resultado: **$estado** ($($e.segundos) s).")
	[void]$m.AppendLine("")
	[void]$m.AppendLine("Avería simulada: $($e.averia)")
	[void]$m.AppendLine("")
	[void]$m.AppendLine("Lo que hizo el usuario y la app:")
	[void]$m.AppendLine("")
	$i = 1
	foreach ($p in @($e.pasos)) { [void]$m.AppendLine("$i. $p"); $i++ }
	[void]$m.AppendLine("")
	[void]$m.AppendLine("Comprobaciones:")
	[void]$m.AppendLine("")
	foreach ($c in @($e.comprobaciones)) { $marca = if ($c.bien) { 'Bien' } else { 'Mal' }; [void]$m.AppendLine("- $marca`: $($c.texto)") }
	[void]$m.AppendLine("")
}
[IO.File]::WriteAllText((Join-Path $Raiz "docs\informe_simulaciones.md"), $m.ToString(), $Utf8)
Write-Host "[OK] Informe: $superados de $total escenarios superados; $bien de $comprobaciones comprobaciones."
