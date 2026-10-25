package com.xebolax.rescate.actualizaciones

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Lectura de las dos fuentes de actualizaciones: el version.json y la API de GitHub. */
class ActualizadorTest {
	private val repo = "https://github.com/hxebolax/RescateAccesible/releases"
	private val sha = "a".repeat(64)

	@Test
	fun leeElVersionJsonDelServidor() {
		val o = JSONObject("""{"version":"1.0.1","versionCode":2,"file":"RescateAccesible_v1.0.1.apk","sha256":"${sha.uppercase()}","size":123,"date":"2026-10-25","notas":"- Algo"}""")
		val v = Actualizador.desdeVersionJson(o, "https://servidor/RescateAccesible")!!
		assertEquals("1.0.1", v.version)
		assertEquals(2, v.versionCode)
		assertEquals(sha, v.sha256)
		assertEquals("https://servidor/RescateAccesible/RescateAccesible_v1.0.1.apk", v.url)
		assertEquals(OrigenVersion.SERVIDOR, v.origen)
	}

	@Test
	fun rechazaRutasEnElNombreDelArchivo() {
		val o = JSONObject("""{"version":"9.9","file":"../otra/app.apk"}""")
		assertNull(Actualizador.desdeVersionJson(o, "https://servidor"))
	}

	@Test
	fun leeLaReleaseDeGitHub() {
		val json = JSONObject("""
			{"tag_name":"v1.0.1","draft":false,"prerelease":false,"published_at":"2026-10-25T12:00:00Z","body":"Novedades",
			 "assets":[
				{"name":"version.json","browser_download_url":"$repo/download/v1.0.1/version.json","size":10},
				{"name":"RescateAccesible_v1.0.1.apk","browser_download_url":"$repo/download/v1.0.1/RescateAccesible_v1.0.1.apk","size":29000000,"digest":"sha256:$sha"}
			 ]}
		""".trimIndent())
		val v = Actualizador.desdeApiGitHub(json)!!
		assertEquals("1.0.1", v.version)
		assertEquals("RescateAccesible_v1.0.1.apk", v.archivo)
		assertEquals(29000000L, v.tamano)
		assertEquals(sha, v.sha256)
		assertEquals("$repo/download/v1.0.1/RescateAccesible_v1.0.1.apk", v.url)
		assertEquals(OrigenVersion.GITHUB, v.origen)
		assertEquals("2026-10-25", v.fecha)
	}

	@Test
	fun ignoraBorradoresYApksDeOtrosSitios() {
		val borrador = JSONObject("""{"tag_name":"v2.0","draft":true,"assets":[]}""")
		assertNull(Actualizador.desdeApiGitHub(borrador))
		val ajeno = JSONObject("""{"tag_name":"v2.0","assets":[{"name":"RescateAccesible_v2.0.apk","browser_download_url":"https://malo.example/RescateAccesible_v2.0.apk","size":1}]}""")
		assertNull(Actualizador.desdeApiGitHub(ajeno))
	}

	@Test
	fun direccionDeGitHubPorVersion() {
		assertEquals("$repo/download/v1.0.1", Actualizador.baseGitHub("1.0.1"))
	}
}
