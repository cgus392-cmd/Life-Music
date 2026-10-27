package com.cglabs.lifemusic.ui.screens.estudio

import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.cglabs.lifemusic.LocalPlayerAwareWindowInsets
import com.cglabs.lifemusic.R
import com.cglabs.lifemusic.constants.AmbienteFondoReactivoKey
import com.cglabs.lifemusic.constants.CanvasThumbnailAnimationKey
import com.cglabs.lifemusic.constants.GolpesTvKey
import com.cglabs.lifemusic.constants.KaraokePropioKey
import com.cglabs.lifemusic.constants.LiquidGlassGlobalEnabledKey
import com.cglabs.lifemusic.constants.NovaVozHDKey
import com.cglabs.lifemusic.nova.NovaCapacidad
import com.cglabs.lifemusic.ui.component.isGlassSupported
import com.cglabs.lifemusic.utils.rememberPreference
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 2.0 · Estudio (ficha docs/versiones/2.0/estudio.md; maqueta «la prueba de
 * sonido» aprobada por CG el 27-10, en Brand/maquetas/2026-10-27-estudio): las
 * funciones que usan la potencia del telefono. El telefono se mide y queda como
 * un medidor hecho con el trazo del logo, que llega hasta la cabeza coral; cada
 * funcion trae el mismo medidor en pequeño con la marca de lo que exige.
 *
 * Tanda B3a: la medicion y la lista, con los interruptores sobre las mismas
 * claves de siempre. El centro de modelos, la mudanza y el aviso van despues.
 */
@Composable
fun EstudioScreen(navController: NavController) {
    val contexto = LocalContext.current
    val t = tonoDeEstudio()
    val escena = rememberCoroutineScope()

    // La prueba de sonido: se mide al entrar y con «Volver a medir»; el medidor sube desde cero.
    var telefono by remember { mutableStateOf(NovaCapacidad.telefono(contexto)) }
    val aguante = NovaCapacidad.aguante(telefono)
    val medidor = remember { Animatable(0f) }
    LaunchedEffect(Unit) { medidor.animateTo(aguante, tween(900, easing = FastOutSlowInEasing)) }

    val (karaoke, alKaraoke) = rememberPreference(KaraokePropioKey, defaultValue = false)
    val (vozHD, alVozHD) = rememberPreference(NovaVozHDKey, defaultValue = "")
    val (golpes, alGolpes) = rememberPreference(GolpesTvKey, defaultValue = true)
    val (vidrio, alVidrio) = rememberPreference(LiquidGlassGlobalEnabledKey, defaultValue = false)
    val (canvas, alCanvas) = rememberPreference(CanvasThumbnailAnimationKey, defaultValue = false)
    val (reactivo, alReactivo) = rememberPreference(AmbienteFondoReactivoKey, defaultValue = true)
    val sinVoz = stringResource(R.string.estudio_voces_sin_elegir)

    val funciones = listOfNotNull(
        Funcion(
            R.string.estudio_karaoke, R.string.estudio_karaoke_que,
            listOf(R.drawable.estudio_procesador, R.drawable.estudio_bateria, R.drawable.estudio_descarga), "132 MB",
            exige = 0.88f, detalleTarda = R.string.estudio_karaoke_tarda,
            ligera = R.string.estudio_karaoke_ligero to R.string.estudio_karaoke_ligero_que,
            encendida = karaoke, alCambiar = alKaraoke,
        ),
        Funcion(
            R.string.estudio_voces, R.string.estudio_voces_que,
            listOf(R.drawable.estudio_procesador, R.drawable.estudio_descarga), stringResource(R.string.estudio_voces_peso),
            exige = 0.55f,
            encendida = vozHD.isNotEmpty(),
            // Apagar vuelve a la voz del telefono; prender pide elegir una en Personalizar.
            alCambiar = { if (it) Toast.makeText(contexto, sinVoz, Toast.LENGTH_LONG).show() else alVozHD("") },
        ),
        Funcion(
            R.string.estudio_golpes, R.string.estudio_golpes_que,
            listOf(R.drawable.estudio_procesador), null,
            exige = 0.40f, detalleTarda = R.string.estudio_golpes_tarda,
            encendida = golpes, alCambiar = alGolpes,
        ),
        // Sin Android 12 no hay vidrio de ninguna clase: la funcion no aparece.
        if (isGlassSupported()) Funcion(
            R.string.estudio_vidrio, R.string.estudio_vidrio_que,
            listOf(R.drawable.estudio_grafica), null,
            exige = 0.60f,
            ligera = R.string.estudio_vidrio_ligero to R.string.estudio_vidrio_ligero_que,
            encendida = vidrio, alCambiar = alVidrio,
        ) else null,
        Funcion(
            R.string.estudio_canvas, R.string.estudio_canvas_que,
            listOf(R.drawable.estudio_grafica, R.drawable.estudio_bateria), null,
            exige = 0.50f,
            encendida = canvas, alCambiar = alCanvas,
        ),
        Funcion(
            R.string.estudio_reactivo, R.string.estudio_reactivo_que,
            listOf(R.drawable.estudio_grafica, R.drawable.estudio_bateria), null,
            exige = 0.45f,
            encendida = reactivo, alCambiar = alReactivo,
        ),
    )
        // Lo que no le cabe y no tiene version ligera, no se muestra (decision del 23-10).
        .filter { estadoDe(aguante, it.exige) != Estado.NO_DA || it.ligera != null }

    val estados = funciones.map { estadoDe(aguante, it.exige) }
    val tardan = estados.count { it == Estado.TARDA }
    val veredicto = buildAnnotatedString {
        when {
            Estado.NO_DA in estados -> {
                append(stringResource(R.string.estudio_veredicto_noda) + " ")
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(stringResource(R.string.estudio_veredicto_noda_b)) }
            }
            tardan > 0 -> {
                append(stringResource(R.string.estudio_veredicto_casi) + " ")
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                    append(if (tardan == 1) stringResource(R.string.estudio_veredicto_una) else stringResource(R.string.estudio_veredicto_varias, tardan))
                }
            }
            else -> append(stringResource(R.string.estudio_veredicto_todo))
        }
    }

    // Abajo queda el mini reproductor: el ultimo grupo no se esconde detras.
    val abajo = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()
    Column(
        Modifier
            .fillMaxSize()
            .background(t.fondo)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = abajo + 24.dp),
    ) {
        IconButton(onClick = { navController.navigateUp() }, modifier = Modifier.padding(start = 8.dp, top = 4.dp)) {
            Icon(painterResource(R.drawable.arrow_back), contentDescription = stringResource(R.string.nova_volver), tint = t.txt)
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text(
                stringResource(R.string.estudio_titulo),
                color = t.txt, fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1.2).sp,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            )
            Text(
                stringResource(R.string.estudio_bajada),
                color = t.sec, fontSize = 14.5.sp, lineHeight = 20.sp,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 6.dp),
            )

            // La prueba de sonido.
            Column(
                Modifier
                    .padding(top = 18.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(t.sup)
                    .padding(start = 18.dp, end = 10.dp, top = 18.dp, bottom = 16.dp),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.estudio_prueba), color = t.txt, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            stringResource(R.string.estudio_datos, telefono.nucleos, telefono.memoriaGb.roundToInt()),
                            color = t.sec, fontSize = 13.sp, modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                    TextButton(onClick = {
                        telefono = NovaCapacidad.telefono(contexto)
                        val nuevo = NovaCapacidad.aguante(telefono)
                        escena.launch {
                            medidor.snapTo(0f)
                            medidor.animateTo(nuevo, tween(900, easing = FastOutSlowInEasing))
                        }
                    }) {
                        Text(stringResource(R.string.estudio_volver_a_medir), color = t.acentoTxt, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Medidor(
                    lleno = medidor.value, marca = null, tono = t, grande = true,
                    modifier = Modifier.padding(start = 8.dp, end = 16.dp, top = 14.dp).fillMaxWidth().height(30.dp),
                )
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 6.dp),
                ) {
                    listOf(R.string.estudio_ligera, R.string.estudio_media, R.string.estudio_pesada).forEach {
                        Text(stringResource(it), color = t.terc, fontSize = 12.sp)
                    }
                }
                Text(veredicto, color = t.txt, fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 12.dp, end = 8.dp))
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 6.dp, top = 12.dp, bottom = 0.dp),
            ) {
                Text(
                    stringResource(R.string.estudio_funciones),
                    color = t.sec, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { navController.navigate("estudio/modelos") }) {
                    Text(stringResource(R.string.estudio_centro_de_modelos), color = t.acentoTxt, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(t.sup)) {
                funciones.forEachIndexed { i, f ->
                    FilaDeFuncion(f, estadoDe(aguante, f.exige), aguante, t)
                    if (i < funciones.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(t.linea))
                }
            }
        }
    }
}

private enum class Estado { LISTA, TARDA, NO_DA }

/** Si cabe con holgura, Lista; si queda en el borde, Va a tardar; si se pasa, No da. */
private fun estadoDe(aguante: Float, exige: Float) = when {
    exige <= aguante - 0.04f -> Estado.LISTA
    exige <= aguante + 0.15f -> Estado.TARDA
    else -> Estado.NO_DA
}

private class Funcion(
    val titulo: Int,
    val que: Int,
    val usa: List<Int>,
    val peso: String?,
    /** Lo que exige, en la misma escala que [NovaCapacidad.aguante]. */
    val exige: Float,
    val detalleTarda: Int? = null,
    /** La version ligera (titulo y que hace) que se ofrece cuando no da. Va sobre el mismo ajuste. */
    val ligera: Pair<Int, Int>? = null,
    val encendida: Boolean,
    val alCambiar: (Boolean) -> Unit,
)

@Composable
private fun FilaDeFuncion(f: Funcion, estado: Estado, aguante: Float, t: TonoEstudio) {
    val colorEstado = when (estado) {
        Estado.LISTA -> t.ok
        Estado.TARDA -> t.tarda
        Estado.NO_DA -> t.terc
    }
    Row(Modifier.padding(start = 18.dp, end = 16.dp, top = 15.dp, bottom = 14.dp)) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(f.titulo), color = t.txt, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(f.que), color = t.sec, fontSize = 13.sp, lineHeight = 17.5.sp, modifier = Modifier.padding(top = 2.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 9.dp)) {
                f.usa.forEach {
                    Icon(painterResource(it), contentDescription = null, tint = t.sec, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(10.dp))
                }
                f.peso?.let { Text(it, color = t.sec, fontSize = 12.sp, modifier = Modifier.padding(start = 2.dp)) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 9.dp)) {
                Medidor(lleno = aguante, marca = f.exige, colorMarca = colorEstado, tono = t, grande = false, modifier = Modifier.width(54.dp).height(12.dp))
                Spacer(Modifier.width(9.dp))
                Text(
                    stringResource(
                        when (estado) {
                            Estado.LISTA -> R.string.estudio_lista
                            Estado.TARDA -> R.string.estudio_tarda
                            Estado.NO_DA -> R.string.estudio_noda
                        },
                    ),
                    color = colorEstado, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                )
            }
            if (estado == Estado.TARDA && f.detalleTarda != null) {
                Text(stringResource(f.detalleTarda), color = t.sec, fontSize = 13.sp, modifier = Modifier.padding(start = 63.dp, top = 1.dp))
            }
            if (estado == Estado.NO_DA && f.ligera != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(top = 11.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(t.sup2)
                        .padding(start = 14.dp, end = 4.dp, top = 11.dp, bottom = 11.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.estudio_version_ligera), color = t.acentoTxt, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(f.ligera.first), color = t.txt, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 1.dp))
                        Text(stringResource(f.ligera.second), color = t.sec, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 1.dp))
                    }
                    Interruptor(f.encendida, f.alCambiar, t, Modifier.scale(0.85f))
                }
            }
        }
        if (estado != Estado.NO_DA) {
            Spacer(Modifier.width(12.dp))
            Interruptor(f.encendida, f.alCambiar, t)
        }
    }
}

@Composable
private fun Interruptor(encendido: Boolean, alCambiar: (Boolean) -> Unit, t: TonoEstudio, modifier: Modifier = Modifier) {
    Switch(
        checked = encendido,
        onCheckedChange = alCambiar,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = t.acento,
            checkedBorderColor = t.acento,
            uncheckedThumbColor = t.terc,
            uncheckedTrackColor = t.sup2,
            uncheckedBorderColor = t.terc,
        ),
        modifier = modifier,
    )
}

/**
 * El medidor: la pista, el trazo lleno hasta donde aguanta el telefono y, en el
 * grande, la cabeza de la nota del logo (elipse coral a −22°) en la punta. En el
 * pequeño va ademas la marca de lo que exige la funcion, del color de su estado.
 */
@Composable
internal fun Medidor(
    lleno: Float,
    marca: Float?,
    tono: TonoEstudio,
    grande: Boolean,
    modifier: Modifier = Modifier,
    colorMarca: Color = tono.terc,
) {
    Canvas(modifier) {
        val grosor = (if (grande) 4.dp else 3.dp).toPx()
        val y = size.height / 2f
        val radio = CornerRadius(grosor / 2f)
        drawRoundRect(tono.pista, Offset(0f, y - grosor / 2f), Size(size.width, grosor), radio)
        val hasta = size.width * lleno.coerceIn(0f, 1f)
        drawRoundRect(if (grande) tono.txt else tono.sec, Offset(0f, y - grosor / 2f), Size(hasta, grosor), radio)
        marca?.let {
            val x = size.width * it.coerceIn(0f, 1f)
            val ancho = 2.dp.toPx()
            drawRoundRect(colorMarca, Offset(x - ancho / 2f, 0f), Size(ancho, size.height), CornerRadius(ancho / 2f))
        }
        if (grande) {
            // La cabeza: 22 dp de ancho, como en la maqueta (rx 11,5 · ry 8,5 en una caja de 24 × 18).
            val ancho = 22.dp.toPx() * (23f / 24f)
            val alto = 22.dp.toPx() * 0.75f * (17f / 18f)
            rotate(-22f, pivot = Offset(hasta, y)) {
                drawOval(CORAL, Offset(hasta - ancho / 2f, y - alto / 2f), Size(ancho, alto))
            }
        }
    }
}

internal val CORAL = Color(0xFFFF6B3D)

/** Los colores de la maqueta: oscuro y claro, con el coral de Pulso de acento. */
internal class TonoEstudio(
    val fondo: Color, val sup: Color, val sup2: Color, val txt: Color, val sec: Color, val terc: Color,
    val linea: Color, val acento: Color, val acentoTxt: Color, val ok: Color, val tarda: Color, val pista: Color,
)

@Composable
internal fun tonoDeEstudio(): TonoEstudio {
    val fondoTema = MaterialTheme.colorScheme.background
    return if (fondoTema.luminance() < 0.5f) {
        TonoEstudio(
            // Con negro puro, el fondo sigue siendo negro puro.
            fondo = if (fondoTema.luminance() < 0.002f) Color.Black else Color(0xFF0F0F10),
            sup = Color(0xFF1A1A1C), sup2 = Color(0xFF242427), txt = Color(0xFFF2F2EF), sec = Color(0xFFA1A19D),
            terc = Color(0xFF6E6E6A), linea = Color.White.copy(alpha = 0.08f), acento = CORAL, acentoTxt = Color(0xFFFF8A5C),
            ok = Color(0xFF9AD8B0), tarda = Color(0xFFF2B35E), pista = Color.White.copy(alpha = 0.14f),
        )
    } else {
        TonoEstudio(
            fondo = Color(0xFFF5F5F3), sup = Color.White, sup2 = Color(0xFFEDEDEA), txt = Color(0xFF111111), sec = Color(0xFF5E5E5B),
            terc = Color(0xFF8A8A86), linea = Color(0xFF111111).copy(alpha = 0.08f), acento = CORAL, acentoTxt = Color(0xFFC8410F),
            ok = Color(0xFF2E7D4F), tarda = Color(0xFF9A5B00), pista = Color(0xFF111111).copy(alpha = 0.12f),
        )
    }
}
