package com.cglabs.lifemusic.ui.screens.recorrido

import android.graphics.Typeface
import android.provider.Settings
import android.text.Spanned
import android.text.style.StyleSpan
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.navigation.NavController
import com.cglabs.lifemusic.BuildConfig
import com.cglabs.lifemusic.R
import com.cglabs.lifemusic.bandas.Bandas
import com.cglabs.lifemusic.constants.AuroraActivaKey
import com.cglabs.lifemusic.constants.BandasActivaKey
import com.cglabs.lifemusic.constants.EstudioActivoKey
import com.cglabs.lifemusic.constants.IconoAppKey
import com.cglabs.lifemusic.constants.LastOpenedVersionCodeKey
import com.cglabs.lifemusic.ui.component.Aurora
import com.cglabs.lifemusic.ui.component.EstadoNova
import com.cglabs.lifemusic.ui.component.NovaMirada
import com.cglabs.lifemusic.ui.screens.bandas.Acorde
import com.cglabs.lifemusic.ui.screens.bandas.CabezaPersona
import com.cglabs.lifemusic.ui.screens.bandas.IconoReaccion
import com.cglabs.lifemusic.ui.screens.estudio.Medidor
import com.cglabs.lifemusic.ui.screens.estudio.TonoEstudio
import com.cglabs.lifemusic.ui.screens.estudio.tonoDeEstudio
import com.cglabs.lifemusic.ui.screens.settings.IconoDeLaApp
import com.cglabs.lifemusic.utils.dataStore
import com.cglabs.lifemusic.utils.rememberPreference
import kotlinx.coroutines.launch

/*
 * El recorrido de la 2.0 (maqueta «el recorrido es una cancion», aprobada por CG el
 * 28-10, Brand/maquetas/2026-10-28-recorrido-2-0): seis pantallas opacas a pantalla
 * completa, con la linea del logo como indicador y la cabeza coral en la pagina
 * actual. Reemplaza a la ventana de novedades, que era una capa y en algunos
 * telefonos dejaba ver lo de atras. Sale una vez por version, tambien al instalar.
 */

/** La version que tiene recorrido. Las demas siguen con su boletin. */
object Recorrido {
    const val VERSION_CODE = 25

    fun hayPara(versionCode: Int) = versionCode == VERSION_CODE
}

private val CORAL = Color(0xFFFF6B3D)

/** El trazo del logo (viewBox -14 0 116 100) y su cabeza coral. */
private const val TRAZO = "M28 31 Q28 22 21 18 Q13 14 12 8 V80 Q12 92 24 92 Q36 92 36 80 V58 Q36 42 49 42 Q62 42 62 58 V90 V58 Q62 42 75 42 Q88 42 88 58 V90"

private const val PASOS = 6

@Composable
private fun sinMovimiento(): Boolean {
    val contexto = LocalContext.current
    return remember { Settings.Global.getFloat(contexto.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}

@Composable
fun RecorridoScreen(navController: NavController) {
    val contexto = LocalContext.current
    val t = tonoDeEstudio()
    val escena = rememberCoroutineScope()
    val quieto = sinMovimiento()
    val pager = rememberPagerState { PASOS }
    var pasoQuieto by remember { mutableIntStateOf(0) }
    val paso = if (quieto) pasoQuieto else pager.currentPage

    val terminar: () -> Unit = {
        escena.launch {
            contexto.dataStore.edit { it[LastOpenedVersionCodeKey] = BuildConfig.VERSION_CODE }
            if (!navController.popBackStack()) navController.navigate("home")
        }
    }
    val ir: (Int) -> Unit = { destino ->
        if (quieto) pasoQuieto = destino else escena.launch { pager.animateScrollToPage(destino) }
    }
    BackHandler { if (paso > 0) ir(paso - 1) else terminar() }

    // La cabeza del indicador sigue al dedo mientras se desliza.
    val posicion = if (quieto) {
        animateFloatAsState(pasoQuieto.toFloat(), tween(200), label = "avance").value
    } else {
        (pager.currentPage + pager.currentPageOffsetFraction).coerceIn(0f, (PASOS - 1).toFloat())
    }

    Column(Modifier.fillMaxSize().background(t.fondo).statusBarsPadding().navigationBarsPadding()) {
        Avance(posicion / (PASOS - 1), t, Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp).fillMaxWidth().height(20.dp))
        Box(Modifier.weight(1f)) {
            if (quieto) {
                // Con «quitar animaciones», los deslizamientos pasan a fundidos.
                Crossfade(pasoQuieto, animationSpec = tween(180), label = "recorrido") { Pagina(it, t) }
            } else {
                HorizontalPager(pager, modifier = Modifier.fillMaxSize()) { Pagina(it, t) }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 20.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (paso == PASOS - 1) {
                BotonCoral(stringResource(R.string.recorrido_empezar), Modifier.fillMaxWidth(), alTocar = terminar)
            } else {
                TextButton(onClick = terminar) { Text(stringResource(R.string.recorrido_saltar), color = t.sec, fontSize = 16.sp) }
                Spacer(Modifier.weight(1f))
                BotonCoral(stringResource(R.string.recorrido_siguiente)) { ir(paso + 1) }
            }
        }
    }
}

@Composable
private fun BotonCoral(texto: String, modifier: Modifier = Modifier, alTocar: () -> Unit) {
    Button(
        onClick = alTocar, shape = RoundedCornerShape(25.dp), modifier = modifier.height(50.dp),
        colors = ButtonDefaults.buttonColors(containerColor = CORAL, contentColor = Color.White),
    ) { Text(texto, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 10.dp)) }
}

/** El indicador: la linea del logo con seis marcas; lo recorrido va lleno y la cabeza coral en la pagina actual. */
@Composable
private fun Avance(fraccion: Float, t: TonoEstudio, modifier: Modifier) {
    Canvas(modifier) {
        val y = size.height / 2f
        val grosor = 3.dp.toPx()
        drawLine(t.pista, Offset(0f, y), Offset(size.width, y), grosor, StrokeCap.Round)
        drawLine(t.txt, Offset(0f, y), Offset(size.width * fraccion, y), grosor, StrokeCap.Round)
        for (i in 0 until PASOS) {
            val x = size.width * i / (PASOS - 1)
            drawLine(t.terc, Offset(x, y - 6.5.dp.toPx()), Offset(x, y + 6.5.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
        }
        val centro = Offset(size.width * fraccion, y)
        rotate(-22f, centro) {
            drawOval(CORAL, Offset(centro.x - 10.dp.toPx(), centro.y - 7.4.dp.toPx()), Size(20.dp.toPx(), 14.8.dp.toPx()))
        }
    }
}

@Composable
private fun Pagina(i: Int, t: TonoEstudio) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(320.dp).padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
            when (i) {
                0 -> Simbolo(t.txt, 190.dp)
                1 -> Box(Modifier.size(200.dp)) { NovaMirada(EstadoNova.ESCUCHA, 0.4f, Aurora.MARCA, Modifier.fillMaxSize()) }
                2 -> EscenaEstudio(t)
                3 -> EscenaBandas(t)
                4 -> EscenaIconos(t)
                else -> Image(
                    painterResource(R.drawable.novedades_20), contentDescription = null, contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)),
                )
            }
        }
        val (et, ti, p, donde) = when (i) {
            0 -> listOf(R.string.recorrido_pulso_et, R.string.recorrido_pulso_t, R.string.recorrido_pulso_p, 0)
            1 -> listOf(R.string.recorrido_nova_et, R.string.recorrido_nova_t, R.string.recorrido_nova_p, R.string.recorrido_nova_donde)
            2 -> listOf(R.string.recorrido_estudio_et, R.string.recorrido_estudio_t, R.string.recorrido_estudio_p, R.string.recorrido_estudio_donde)
            3 -> listOf(R.string.recorrido_bandas_et, R.string.recorrido_bandas_t, R.string.recorrido_bandas_p, R.string.recorrido_bandas_donde)
            4 -> listOf(R.string.recorrido_icono_et, R.string.recorrido_icono_t, R.string.recorrido_icono_p, R.string.recorrido_icono_donde)
            else -> listOf(R.string.recorrido_listo_et, R.string.recorrido_listo_t, R.string.recorrido_listo_p, 0)
        }
        Column(Modifier.padding(horizontal = 28.dp)) {
            Text(stringResource(et), color = t.acentoTxt, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(ti), color = t.txt, fontSize = 30.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold,
                letterSpacing = (-1).sp, modifier = Modifier.padding(top = 6.dp),
            )
            Text(stringResource(p), color = t.sec, fontSize = 15.5.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 12.dp))
            if (donde != 0) {
                Text(
                    conNegritas(donde, t.txt), color = t.sec, fontSize = 13.5.sp, lineHeight = 19.sp,
                    modifier = Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(t.sup)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
            // Aurora, Estudio y Bandas vienen prendidos; aqui cada quien decide si los apaga.
            when (i) {
                0 -> Interruptor(AuroraActivaKey, R.string.aurora_activa, R.string.recorrido_aurora_nota, t)
                2 -> Interruptor(EstudioActivoKey, R.string.estudio_titulo, R.string.recorrido_interruptor_nota, t)
                3 -> Interruptor(BandasActivaKey, R.string.bandas_titulo, R.string.recorrido_interruptor_nota, t)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Interruptor(clave: Preferences.Key<Boolean>, titulo: Int, nota: Int, t: TonoEstudio) {
    val (prendido, cambiar) = rememberPreference(clave, defaultValue = true)
    Row(
        Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(t.sup)
            .clickable { cambiar(!prendido) }.padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(titulo), color = t.txt, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(nota), color = t.sec, fontSize = 12.5.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Switch(
            checked = prendido,
            onCheckedChange = cambiar,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = CORAL,
                checkedBorderColor = CORAL,
                uncheckedThumbColor = t.terc,
                uncheckedTrackColor = t.sup2,
                uncheckedBorderColor = t.terc,
            ),
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

/** Un texto de recursos con <b>: lo marcado va en negrita y con el color del texto. */
@Composable
private fun conNegritas(id: Int, color: Color): AnnotatedString {
    val texto = LocalContext.current.resources.getText(id)
    return buildAnnotatedString {
        append(texto.toString())
        if (texto is Spanned) {
            texto.getSpans(0, texto.length, StyleSpan::class.java).filter { it.style == Typeface.BOLD }.forEach {
                addStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = color), texto.getSpanStart(it), texto.getSpanEnd(it))
            }
        }
    }
}

/** El simbolo del logo, con su trazo y la cabeza coral. */
@Composable
private fun Simbolo(tinta: Color, ancho: Dp) {
    val trazo = remember { PathParser().parsePathString(TRAZO).toPath() }
    Canvas(Modifier.size(ancho, ancho * (100f / 116f))) {
        val s = size.width / 116f
        translate(left = 14f * s) {
            scale(s, s, pivot = Offset.Zero) {
                drawPath(trazo, tinta, style = Stroke(width = 9f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                rotate(-22f, Offset(2.5f, 85f)) {
                    drawOval(CORAL, Offset(2.5f - 11.5f, 85f - 8.5f), Size(23f, 17f))
                }
            }
        }
    }
}

@Composable
private fun EscenaEstudio(t: TonoEstudio) {
    Column(Modifier.width(300.dp)) {
        // Por donde se entra: tu foto → Estudio.
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(t.sup).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(34.dp, 26.dp).clip(RoundedCornerShape(13.dp)).border(BorderStroke(1.5.dp, CORAL), RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center,
            ) { Text("CG", color = t.txt, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            Text("→", color = t.terc, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 10.dp))
            Box(
                Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x47FFB45E)),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(painterResource(R.drawable.estudio_deslizadores), contentDescription = null, tint = t.txt, modifier = Modifier.size(18.dp))
            }
            Text(stringResource(R.string.estudio_titulo), color = t.txt, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 10.dp))
        }
        Column(Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(t.sup).padding(horizontal = 18.dp, vertical = 16.dp)) {
            Medidor(lleno = 0.72f, marca = null, tono = t, grande = true, modifier = Modifier.padding(horizontal = 6.dp).fillMaxWidth().height(24.dp))
            listOf(
                Triple(R.string.recorrido_estudio_lista, R.string.recorrido_estudio_lista_q, t.ok),
                Triple(R.string.recorrido_estudio_tarda, R.string.recorrido_estudio_tarda_q, t.tarda),
                Triple(R.string.recorrido_estudio_noda, R.string.recorrido_estudio_noda_q, t.terc),
            ).forEachIndexed { k, (nombre, que, color) ->
                Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Medidor(lleno = listOf(0.4f, 0.75f, 1f)[k], marca = 0.6f, tono = t, grande = false, colorMarca = color, modifier = Modifier.width(26.dp).height(12.dp))
                    Text(stringResource(nombre), color = color, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 10.dp))
                    Text(stringResource(que), color = t.sec, fontSize = 13.5.sp, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun EscenaBandas(t: TonoEstudio) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Top) {
            Acorde(listOf(Bandas.color("coral"), Bandas.color("menta"), Bandas.color("cielo")), alto = 150.dp, tinta = t.txt)
            // Quien espera aprobacion flota al lado del acorde.
            CabezaPersona(Bandas.color("lila"), "T", ancho = 32.dp, modifier = Modifier.padding(start = 14.dp, top = 22.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(top = 24.dp)) {
            Bandas.REACCIONES.forEach { IconoReaccion(it, t.txt, tam = 26.dp) }
        }
    }
}

@Composable
private fun EscenaIconos(t: TonoEstudio) {
    val contexto = LocalContext.current
    val (guardado, guardar) = rememberPreference(IconoAppKey, defaultValue = "carbon")
    val actual = IconoDeLaApp.de(guardado)
    val aviso = stringResource(R.string.icono_aviso)
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        IconoDeLaApp.entries.chunked(3).forEach { fila ->
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                fila.forEach { icono ->
                    val suyo = icono == actual
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(84.dp).clip(RoundedCornerShape(20.dp)).clickable {
                            if (!suyo) {
                                IconoDeLaApp.poner(contexto, icono)
                                guardar(icono.clave)
                                Toast.makeText(contexto, aviso, Toast.LENGTH_LONG).show()
                            }
                        },
                    ) {
                        val forma = RoundedCornerShape(20.dp)
                        Box(
                            Modifier
                                .border(BorderStroke(2.5.dp, if (suyo) CORAL else Color.Transparent), RoundedCornerShape(24.dp))
                                .padding(4.dp),
                        ) {
                            Box(Modifier.size(60.dp).clip(forma)) {
                                Image(painterResource(icono.fondo), contentDescription = null, modifier = Modifier.fillMaxSize())
                                Image(painterResource(icono.frente), contentDescription = stringResource(icono.nombre), modifier = Modifier.fillMaxSize())
                            }
                        }
                        Text(
                            stringResource(icono.nombre), color = if (suyo) t.txt else t.sec, fontSize = 12.5.sp,
                            fontWeight = if (suyo) FontWeight.SemiBold else FontWeight.Normal, textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 7.dp),
                        )
                    }
                }
            }
        }
    }
}
