package com.cglabs.lifemusic.nova

import android.content.Context
import android.net.ConnectivityManager
import com.cglabs.lifemusic.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 2.0 · Las voces HD de Nova: voces Piper en español, preparadas para sherpa-onnx.
 * No van en el APK: cada persona baja la que elige, una sola vez, directo de las
 * publicaciones de sherpa-onnx en GitHub (sin servidor nuestro, sin costo). Ya
 * bajadas funcionan sin internet. Ficha: docs/versiones/2.0/nova.md (4.5).
 *
 * Licencias revisadas el 2026-10-15 (MODEL_CARD de cada voz en rhasspy/piper-voices).
 * Todas parten de la voz base «lessac», de uso no comercial: valen para Life
 * Music, que es educativa y sin fines de lucro.
 */
enum class CalidadVoz(val sufijo: String, val nombre: Int) {
    LIVIANA("-int8", R.string.nova_calidad_liviana),
    MEDIA("-fp16", R.string.nova_calidad_media),
    COMPLETA("", R.string.nova_calidad_completa),
}

/**
 * Una voz del catalogo. [hablante] elige la voz dentro de un paquete que trae
 * varias (Sharvard: 0 masculina, 1 femenina). [megas] es el peso de la descarga
 * en cada calidad, en el orden de [CalidadVoz].
 */
data class VozHD(
    val id: String,
    val nombre: String,
    val detalle: Int,
    val paquete: String,
    val hablante: Int,
    val megas: List<Float>,
    val licencia: String,
    val credito: String,
    /**
     * Las «medium» preparan una frase de 5 s en menos de 1 s; las «high» (mas
     * naturales) tardan casi lo mismo que dura la frase (medido en el S25, 15-10).
     */
    val rapida: Boolean = true,
) {
    fun clave(calidad: CalidadVoz) = paquete + calidad.sufijo
    fun megas(calidad: CalidadVoz) = megas[calidad.ordinal]
    fun url(calidad: CalidadVoz) =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/${clave(calidad)}.tar.bz2"
}

object NovaVocesHD {
    val CATALOGO = listOf(
        VozHD("daniela", "Daniela", R.string.nova_voz_f_argentina, "vits-piper-es_AR-daniela-high", 0,
            listOf(33.4f, 55.8f, 110.2f), "CC BY-SA 4.0", "OpenSLR 61 · larcanio", rapida = false),
        VozHD("claude", "Claude", R.string.nova_voz_mexico, "vits-piper-es_MX-claude-high", 0,
            listOf(20.2f, 34.2f, 64.1f), "Apache 2.0", "HirCoir", rapida = false),
        VozHD("ald", "Ald", R.string.nova_voz_m_mexico, "vits-piper-es_MX-ald-medium", 0,
            listOf(20.3f, 34.2f, 64.1f), "Unlicense", "rmcpantoja"),
        VozHD("sharvard-f", "Sharvard", R.string.nova_voz_f_espana, "vits-piper-es_ES-sharvard-medium", 1,
            listOf(22.4f, 40.0f, 76.6f), "CC BY 3.0", "Universidad de Edimburgo"),
        VozHD("sharvard-m", "Sharvard", R.string.nova_voz_m_espana, "vits-piper-es_ES-sharvard-medium", 0,
            listOf(22.4f, 40.0f, 76.6f), "CC BY 3.0", "Universidad de Edimburgo"),
        VozHD("davefx", "Davefx", R.string.nova_voz_m_espana, "vits-piper-es_ES-davefx-medium", 0,
            listOf(20.2f, 34.2f, 64.1f), "CC0", "OHF-Voice"),
    )

    fun voz(id: String) = CATALOGO.firstOrNull { it.id == id }

    /** La voz elegida guardada en ajustes: «id:calidad», o vacio para la del telefono. */
    fun leer(valor: String?): Pair<VozHD, CalidadVoz>? {
        val partes = valor?.split(':') ?: return null
        if (partes.size != 2) return null
        val voz = voz(partes[0]) ?: return null
        val calidad = CalidadVoz.entries.firstOrNull { it.name.equals(partes[1], ignoreCase = true) } ?: return null
        return voz to calidad
    }

    fun escribir(voz: VozHD, calidad: CalidadVoz) = "${voz.id}:${calidad.name.lowercase()}"
}

/** Los archivos de una voz ya abierta en el telefono. */
data class ArchivosDeVoz(val modelo: String, val tokens: String, val datos: String)

/**
 * Bajar, abrir y borrar voces. Vive mientras vive la app: si la persona cierra la
 * cabina, la descarga sigue. Una voz bajada a medias nunca queda «lista»: se
 * abre en una carpeta aparte y solo al final pasa a su lugar.
 */
object NovaDescargas {
    sealed interface Estado {
        data class Bajando(val progreso: Float, val megas: Float, val total: Float) : Estado
        data object Abriendo : Estado
        data object Lista : Estado
        data class Fallo(val motivo: String) : Estado
    }

    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val trabajos = ConcurrentHashMap<String, Job>()
    private val _estados = MutableStateFlow<Map<String, Estado>>(emptyMap())
    val estados: StateFlow<Map<String, Estado>> = _estados.asStateFlow()

    private val cliente by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private fun carpeta(contexto: Context, clave: String) = File(contexto.filesDir, "nova-voces/$clave")

    /** Los archivos de la voz, si esta completa en el telefono. */
    fun archivos(contexto: Context, clave: String): ArchivosDeVoz? {
        val raiz = carpeta(contexto, clave)
        if (!raiz.isDirectory) return null
        val todos = raiz.walkTopDown().toList()
        val modelo = todos.firstOrNull { it.isFile && it.name.endsWith(".onnx") } ?: return null
        val tokens = todos.firstOrNull { it.isFile && it.name == "tokens.txt" } ?: return null
        val datos = todos.firstOrNull { it.isDirectory && it.name == "espeak-ng-data" } ?: return null
        return ArchivosDeVoz(modelo.absolutePath, tokens.absolutePath, datos.absolutePath)
    }

    fun instalada(contexto: Context, clave: String) = archivos(contexto, clave) != null

    /** Con datos moviles (red medida) se pregunta antes: las voces pesan. */
    fun conDatosMoviles(contexto: Context): Boolean =
        (contexto.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager)?.isActiveNetworkMetered == true

    fun bajar(contexto: Context, voz: VozHD, calidad: CalidadVoz) {
        val clave = voz.clave(calidad)
        if (trabajos[clave]?.isActive == true) return
        val app = contexto.applicationContext
        trabajos[clave] = alcance.launch {
            val total = voz.megas(calidad)
            val parcial = File(app.cacheDir, "nova-voces/$clave.tar.bz2.part")
            val abriendo = File(app.filesDir, "nova-voces/$clave.abriendo")
            try {
                poner(clave, Estado.Bajando(0f, 0f, total))
                parcial.parentFile?.mkdirs()
                cliente.newCall(Request.Builder().url(voz.url(calidad)).build()).execute().use { respuesta ->
                    if (!respuesta.isSuccessful) error("HTTP ${respuesta.code}")
                    val cuerpo = respuesta.body ?: error("sin contenido")
                    val largo = cuerpo.contentLength().takeIf { it > 0 } ?: (total * 1_048_576).toLong()
                    cuerpo.byteStream().use { entrada ->
                        parcial.outputStream().use { salida ->
                            val buffer = ByteArray(64 * 1024)
                            var llevado = 0L
                            var ultimo = 0L
                            while (isActive) {
                                val n = entrada.read(buffer)
                                if (n < 0) break
                                salida.write(buffer, 0, n)
                                llevado += n
                                if (llevado - ultimo > 256 * 1024) {
                                    ultimo = llevado
                                    poner(clave, Estado.Bajando((llevado.toFloat() / largo).coerceIn(0f, 1f), llevado / 1_048_576f, total))
                                }
                            }
                        }
                    }
                }
                if (!isActive) return@launch

                // Abrir el paquete en una carpeta aparte; solo completa pasa a su lugar.
                poner(clave, Estado.Abriendo)
                abriendo.deleteRecursively()
                abriendo.mkdirs()
                val base = abriendo.canonicalPath + File.separator
                TarArchiveInputStream(BZip2CompressorInputStream(BufferedInputStream(FileInputStream(parcial), 256 * 1024))).use { tar ->
                    var entrada = tar.nextEntry
                    while (entrada != null && isActive) {
                        val destino = File(abriendo, entrada.name)
                        // Nada puede escribirse fuera de la carpeta de la voz.
                        if (!destino.canonicalPath.startsWith(base)) error("ruta rara en el paquete")
                        if (entrada.isDirectory) {
                            destino.mkdirs()
                        } else {
                            destino.parentFile?.mkdirs()
                            destino.outputStream().use { tar.copyTo(it, 64 * 1024) }
                        }
                        entrada = tar.nextEntry
                    }
                }
                if (!isActive) return@launch
                val final = carpeta(app, clave)
                final.deleteRecursively()
                if (!abriendo.renameTo(final)) error("no se pudo guardar")
                if (!instalada(app, clave)) error("al paquete le faltan archivos")
                poner(clave, Estado.Lista)
                android.util.Log.i("LifeMusicNova", "voz $clave lista")
            } catch (e: kotlinx.coroutines.CancellationException) {
                quitar(clave)
                throw e
            } catch (e: Exception) {
                android.util.Log.w("LifeMusicNova", "fallo bajando $clave", e)
                poner(clave, Estado.Fallo(e.message ?: "error"))
            } finally {
                parcial.delete()
                abriendo.deleteRecursively()
                trabajos.remove(clave)
            }
        }
    }

    fun cancelar(clave: String) {
        trabajos[clave]?.cancel()
        quitar(clave)
    }

    /** Borra una voz del telefono (la persona lo pide desde Personalizar). */
    fun borrar(contexto: Context, clave: String) {
        cancelar(clave)
        carpeta(contexto.applicationContext, clave).deleteRecursively()
        quitar(clave)
    }

    private fun poner(clave: String, estado: Estado) = _estados.update { it + (clave to estado) }
    private fun quitar(clave: String) = _estados.update { it - clave }
}
