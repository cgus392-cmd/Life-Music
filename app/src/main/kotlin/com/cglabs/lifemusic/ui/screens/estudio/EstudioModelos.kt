package com.cglabs.lifemusic.ui.screens.estudio

import android.content.Context
import android.os.StatFs
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.cglabs.lifemusic.LocalPlayerAwareWindowInsets
import com.cglabs.lifemusic.R
import com.cglabs.lifemusic.constants.KaraokePropioKey
import com.cglabs.lifemusic.constants.NovaVozHDKey
import com.cglabs.lifemusic.karaoke.KaraokeModelos
import com.cglabs.lifemusic.nova.MotorVoz
import com.cglabs.lifemusic.nova.NovaVocesHD
import com.cglabs.lifemusic.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

/**
 * 2.0 · Estudio › Modelos (tanda B3b, pantallas 4 y 9 de la maqueta del 27-10):
 * todo lo que Life Music bajo al telefono, el espacio que ocupa, bajar lo que
 * falta del karaoke y borrar con confirmacion, diciendo que funcion se apaga.
 * Los modelos se siguen bajando de donde siempre (sherpa-onnx en GitHub).
 */
@Composable
fun EstudioModelosScreen(navController: NavController) {
    val contexto = LocalContext.current
    val t = tonoDeEstudio()
    val escena = rememberCoroutineScope()
    var vuelta by remember { mutableIntStateOf(0) }
    val bajados = remember(vuelta) { ModelosDelTelefono.bajados(contexto) }
    val (vozHD, alVozHD) = rememberPreference(NovaVozHDKey, defaultValue = "")
    val (_, alKaraoke) = rememberPreference(KaraokePropioKey, defaultValue = false)
    var aBorrar by remember { mutableStateOf<ModeloBajado?>(null) }
    var verCreditos by remember { mutableStateOf(false) }
    var bajando by remember { mutableStateOf<Map<String, Float>>(emptyMap()) }

    val total = bajados.sumOf { it.bytes }
    val disco = remember(vuelta) { StatFs(contexto.filesDir.path) }
    val capacidad = disco.blockCountLong * disco.blockSizeLong
    val libre = disco.availableBytes
    val abajo = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()

    Column(
        Modifier.fillMaxSize().background(t.fondo).statusBarsPadding()
            .verticalScroll(rememberScrollState()).padding(bottom = abajo + 24.dp),
    ) {
        IconButton(onClick = { navController.navigateUp() }, modifier = Modifier.padding(start = 8.dp, top = 4.dp)) {
            Icon(painterResource(R.drawable.arrow_back), contentDescription = stringResource(R.string.nova_volver), tint = t.txt)
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(stringResource(R.string.estudio_modelos), color = t.txt, fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1.2).sp, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
            Text(stringResource(R.string.estudio_modelos_bajada), color = t.sec, fontSize = 14.5.sp, lineHeight = 20.sp, modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 6.dp))

            // El espacio: lo de los modelos, lo demas ocupado y lo libre.
            Column(Modifier.padding(top = 18.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(t.sup).padding(18.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(megas(total), color = t.txt, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
                    Text(" " + stringResource(R.string.estudio_ocupan), color = t.sec, fontSize = 15.sp, modifier = Modifier.padding(bottom = 4.dp))
                }
                val usado = (capacidad - libre).coerceAtLeast(total)
                Row(Modifier.padding(top = 14.dp).fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(t.pista)) {
                    val fModelos = (total.toFloat() / capacidad).coerceIn(0.01f, 1f)
                    val fOtros = ((usado - total).toFloat() / capacidad).coerceIn(0f, 1f - fModelos)
                    Box(Modifier.weight(fModelos).fillMaxSize().background(t.txt))
                    Spacer(Modifier.width(2.dp))
                    if (fOtros > 0f) Box(Modifier.weight(fOtros).fillMaxSize().background(t.sec))
                    val fLibre = 1f - fModelos - fOtros
                    if (fLibre > 0f) Spacer(Modifier.weight(fLibre))
                }
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text(stringResource(R.string.estudio_quedan, gigas(libre)), color = t.sec, fontSize = 12.5.sp)
                    Text(stringResource(R.string.estudio_de, gigas(capacidad)), color = t.sec, fontSize = 12.5.sp)
                }
            }

            if (bajados.isNotEmpty()) {
                Seccion(stringResource(R.string.estudio_bajados), t)
                Grupo(t) {
                    bajados.forEachIndexed { i, m ->
                        FilaDeModelo(m.nombre, stringResource(m.para) + " · " + stringResource(R.string.estudio_bajado_el, fecha(m.fecha)), megas(m.bytes), t) {
                            Text(m.proveedor, color = t.sec, fontSize = 12.5.sp)
                            BotonTono(stringResource(R.string.estudio_borrar), R.drawable.delete, t) { aBorrar = m }
                        }
                        if (i < bajados.lastIndex) Linea(t)
                    }
                }
            }

            // Lo que falta del karaoke se baja aqui; las voces de Nova se eligen en Personalizar.
            val faltan = ModelosDelTelefono.porBajar(contexto, bajados)
            if (faltan.isNotEmpty()) {
                Seccion(stringResource(R.string.estudio_por_bajar), t)
                Grupo(t) {
                    faltan.forEachIndexed { i, f ->
                        val avance = bajando[f.id]
                        FilaDeModelo(f.nombre, stringResource(f.para), "${f.megas} MB", t, avance = avance) {
                            if (avance != null) {
                                Text(stringResource(R.string.estudio_bajando, (avance * 100).roundToInt()), color = t.sec, fontSize = 12.5.sp)
                            } else {
                                Text(f.proveedor, color = t.sec, fontSize = 12.5.sp)
                                BotonTono(stringResource(R.string.estudio_bajar), R.drawable.estudio_descarga, t) {
                                    bajando = bajando + (f.id to 0f)
                                    escena.launch {
                                        withContext(Dispatchers.IO) { f.bajar(contexto) { p -> bajando = bajando + (f.id to p) } }
                                        bajando = bajando - f.id
                                        vuelta++
                                    }
                                }
                            }
                        }
                        if (i < faltan.lastIndex) Linea(t)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Grupo(t) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { verCreditos = true }.padding(horizontal = 18.dp, vertical = 15.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.estudio_licencias), color = t.txt, fontSize = 15.sp)
                        Text(stringResource(R.string.estudio_licencias_que), color = t.sec, fontSize = 12.5.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                    Icon(painterResource(R.drawable.navigate_next), contentDescription = null, tint = t.terc, modifier = Modifier.size(18.dp))
                }
            }
        }
    }

    aBorrar?.let { m ->
        AlertDialog(
            onDismissRequest = { aBorrar = null },
            title = { Text(stringResource(R.string.estudio_borrar_titulo, m.nombre)) },
            text = { Text(stringResource(R.string.estudio_libera, megas(m.bytes)) + " " + stringResource(m.alBorrar)) },
            confirmButton = {
                TextButton(onClick = {
                    m.borrar(contexto)
                    if (m.id == ModelosDelTelefono.RECONOCEDOR) alKaraoke(false)
                    if (m.voz != null && vozHD.startsWith(m.voz + ":")) alVozHD("")
                    aBorrar = null
                    vuelta++
                }) { Text(stringResource(R.string.estudio_borrar), color = Color(0xFFB3261E)) }
            },
            dismissButton = { TextButton(onClick = { aBorrar = null }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
    if (verCreditos) {
        AlertDialog(
            onDismissRequest = { verCreditos = false },
            title = { Text(stringResource(R.string.estudio_licencias)) },
            text = { Text(ModelosDelTelefono.creditos(bajados)) },
            confirmButton = { TextButton(onClick = { verCreditos = false }) { Text(stringResource(android.R.string.ok)) } },
        )
    }
}

/** Un modelo que esta en el telefono. [voz] es el id de la voz de Nova, si lo es. */
internal class ModeloBajado(
    val id: String, val nombre: String, val para: Int, val alBorrar: Int, val proveedor: String,
    val bytes: Long, val fecha: Long, val archivo: File, val voz: String? = null, val credito: String,
) {
    fun borrar(contexto: Context) {
        val clave = archivo.name
        if (voz != null) com.cglabs.lifemusic.nova.NovaDescargas.borrar(contexto, clave) else archivo.deleteRecursively()
    }
}

/** Uno que falta y se puede bajar desde aqui. */
internal class ModeloPorBajar(val id: String, val nombre: String, val para: Int, val proveedor: String, val megas: Int, val bajar: (Context, (Float) -> Unit) -> Unit)

internal object ModelosDelTelefono {
    const val RECONOCEDOR = "reconocedor"
    const val SEPARADOR = "separador"

    fun bajados(contexto: Context): List<ModeloBajado> {
        val lista = mutableListOf<ModeloBajado>()
        KaraokeModelos.reconocedor(contexto)?.first?.parentFile?.let { dir ->
            lista += ModeloBajado(
                RECONOCEDOR, contexto.getString(R.string.estudio_reconocedor), R.string.estudio_karaoke, R.string.estudio_al_borrar_reconocedor,
                "NVIDIA NeMo", tamano(dir), dir.lastModified(), dir, credito = "NVIDIA NeMo FastConformer CTC (español) · CC-BY-4.0",
            )
        }
        KaraokeModelos.separador(contexto)?.let { f ->
            lista += ModeloBajado(
                SEPARADOR, contexto.getString(R.string.estudio_separador), R.string.estudio_karaoke, R.string.estudio_al_borrar_separador,
                "UVR", f.length(), f.lastModified(), f, credito = "UVR MDX-Net KARA (Ultimate Vocal Remover)",
            )
        }
        File(contexto.filesDir, "nova-voces").listFiles()?.filter { it.isDirectory && !it.name.endsWith(".abriendo") }?.forEach { dir ->
            val voz = NovaVocesHD.CATALOGO.firstOrNull { dir.name in it.paquetes.values } ?: return@forEach
            lista += ModeloBajado(
                "voz:${dir.name}", contexto.getString(R.string.estudio_voz_de_nova, voz.nombre), R.string.estudio_voces, R.string.estudio_al_borrar_voz,
                if (voz.motor == MotorVoz.KOKORO) "Kokoro" else "Piper", tamano(dir), dir.lastModified(), dir, voz.id,
                credito = "${voz.nombre}: ${voz.credito} · ${voz.licencia}",
            )
        }
        return lista.sortedByDescending { it.bytes }
    }

    fun porBajar(contexto: Context, bajados: List<ModeloBajado>): List<ModeloPorBajar> = buildList {
        if (bajados.none { it.id == RECONOCEDOR }) add(
            ModeloPorBajar(RECONOCEDOR, contexto.getString(R.string.estudio_reconocedor), R.string.estudio_karaoke, "NVIDIA NeMo", KaraokeModelos.MEGAS_RECONOCEDOR) { c, avance ->
                KaraokeModelos.asegurarReconocedor(c, avance)
            },
        )
        if (bajados.none { it.id == SEPARADOR }) add(
            ModeloPorBajar(SEPARADOR, contexto.getString(R.string.estudio_separador), R.string.estudio_karaoke, "UVR", KaraokeModelos.MEGAS_SEPARADOR) { c, avance ->
                KaraokeModelos.asegurarSeparador(c, avance)
            },
        )
    }

    fun creditos(bajados: List<ModeloBajado>): String =
        (bajados.map { it.credito } + "sherpa-onnx (k2-fsa) · Apache 2.0").joinToString("\n\n")

    private fun tamano(f: File): Long = if (f.isFile) f.length() else f.walkTopDown().filter { it.isFile }.sumOf { it.length() }
}

private fun megas(bytes: Long) = "${(bytes / 1_048_576.0).roundToInt()} MB"

private fun gigas(bytes: Long) = "${(bytes / 1_073_741_824.0).roundToInt()} GB"

private fun fecha(ms: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(ms))

@Composable
private fun Seccion(texto: String, t: TonoEstudio) {
    Text(texto, color = t.sec, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp, top = 22.dp, bottom = 10.dp))
}

@Composable
private fun Grupo(t: TonoEstudio, contenido: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(t.sup)) { contenido() }
}

@Composable
private fun Linea(t: TonoEstudio) = Box(Modifier.fillMaxWidth().height(1.dp).background(t.linea))

/** Una fila de modelo; con [avance], el trazo baja con la cabeza coral como marca. */
@Composable
private fun FilaDeModelo(
    nombre: String, para: String, tamano: String, t: TonoEstudio,
    avance: Float? = null, pie: @Composable () -> Unit,
) {
    Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
        Row {
            Column(Modifier.weight(1f)) {
                Text(nombre, color = t.txt, fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold)
                Text(para, color = t.sec, fontSize = 12.5.sp, modifier = Modifier.padding(top = 2.dp))
            }
            Text(tamano, color = t.sec, fontSize = 13.sp)
        }
        if (avance != null) {
            Medidor(lleno = avance, marca = null, tono = t, grande = true, modifier = Modifier.padding(top = 8.dp).fillMaxWidth().height(24.dp))
        }
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) { pie() }
    }
}

@Composable
private fun BotonTono(texto: String, icono: Int, t: TonoEstudio, alTocar: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clip(RoundedCornerShape(18.dp)).background(t.sup2).clickable(onClick = alTocar)
            .padding(horizontal = 16.dp).height(36.dp),
    ) {
        Icon(painterResource(icono), contentDescription = null, tint = t.txt, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(texto, color = t.txt, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}
