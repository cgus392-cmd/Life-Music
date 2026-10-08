package com.cglabs.lifemusic.appcore.updater

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * El actualizador renovado (1.3.1): que lea bien novedades/<version>.json y
 * que cada telefono baje el APK que le corresponde.
 */
class NovedadesTest {

    /** El archivo que se publica de verdad en la web, para que no se rompa sin enterarnos. */
    private fun publicado(version: String): String {
        val candidatos = listOf(File("../web/novedades/$version.json"), File("web/novedades/$version.json"))
        return candidatos.first { it.exists() }.readText()
    }

    @Test
    fun leeLasNovedadesPublicadasDeLa131EnLosDosIdiomas() {
        val es = Novedades.leer(publicado("1.3.1"), "es")!!
        assertEquals("1.3.1", es.version)
        assertEquals("https://lifemusic.pages.dev/novedades/1.3.1.webp", es.imagen)
        assertTrue(es.funciones.size in 3..6) // la regla de la marca es de 3 a 5; la 1.3.1 lleva 6 por excepcion de CG
        assertEquals("Temas PRO del TV", es.funciones.first().titulo)

        val en = Novedades.leer(publicado("1.3.1"), "en")!!
        assertEquals("PRO TV themes", en.funciones.first().titulo)
        assertEquals(es.funciones.size, en.funciones.size)
    }

    @Test
    fun unIdiomaQueNoEstaCaeAlInglesYDespuesAlEspanol() {
        val ambos = """{"version":"9.9.9","es":{"resumen":"hola"},"en":{"resumen":"hello"}}"""
        assertEquals("hello", Novedades.leer(ambos, "fr")!!.resumen)
        val soloEs = """{"version":"9.9.9","es":{"resumen":"hola"}}"""
        assertEquals("hola", Novedades.leer(soloEs, "de")!!.resumen)
    }

    @Test
    fun loQueNoEsUnJsonDeNovedadesDevuelveNull() {
        // Pages contesta la landing (HTML) cuando el archivo no existe.
        assertNull(Novedades.leer("<!doctype html><html></html>", "es"))
        assertNull(Novedades.leer("""{"version":"1.0.0","es":{}}""", "es"))
        assertNull(Novedades.leer("", "es"))
    }

    @Test
    fun leeTamanoYHuellaDeCadaApk() {
        val json = """{"version":"1.3.1","es":{"resumen":"x"},"apk":{"lifemusic-arm64.apk":{"bytes":40851687,"sha256":"ABC123"}}}"""
        val apk = Novedades.leer(json, "es")!!.apks.getValue("lifemusic-arm64.apk")
        assertEquals(40851687L, apk.bytes)
        assertEquals("abc123", apk.sha256)
    }

    private val publicacion = listOf(
        ApkAdjunto("lifemusic.apk", "https://x/lifemusic.apk", 69_818_668),
        ApkAdjunto("lifemusic-arm64.apk", "https://x/lifemusic-arm64.apk", 40_829_899),
        ApkAdjunto("mapping.txt.gz", "https://x/mapping.txt.gz", 1_000),
    )

    @Test
    fun unTelefonoArm64BajaElApkDeArm64() {
        assertEquals("lifemusic-arm64.apk", Novedades.elegirApk(publicacion, listOf("arm64-v8a", "armeabi-v7a", "armeabi"))!!.nombre)
    }

    @Test
    fun unTelefonoDe32BitsNuncaRecibeElDeArm64() {
        assertEquals("lifemusic.apk", Novedades.elegirApk(publicacion, listOf("armeabi-v7a", "armeabi"))!!.nombre)
        assertNull(Novedades.elegirApk(publicacion.filter { it.nombre != "lifemusic.apk" }, listOf("armeabi-v7a")))
    }

    @Test
    fun sinElDeArm64SeQuedaConElUniversalYNuncaConUnoDeDepuracion() {
        val vieja = listOf(
            ApkAdjunto("app-debug.apk", "https://x/app-debug.apk", 1),
            ApkAdjunto("lifemusic.apk", "https://x/lifemusic.apk", 2),
        )
        assertEquals("lifemusic.apk", Novedades.elegirApk(vieja, listOf("arm64-v8a"))!!.nombre)
        assertNull(Novedades.elegirApk(listOf(ApkAdjunto("app-debug.apk", "u", 1)), listOf("arm64-v8a")))
    }

    @Test
    fun leeLosAdjuntosDeLaApiDeGitHub() {
        val assets = JSONArray("""[{"name":"lifemusic.apk","browser_download_url":"https://g/l.apk","size":5},{"name":"sin-url.apk"}]""")
        val adjuntos = Novedades.adjuntos(assets)
        assertEquals(1, adjuntos.size)
        assertEquals(5L, adjuntos.first().bytes)
    }
}
