package com.cglabs.lifemusic.ui.component

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.cglabs.lifemusic.LocalPlayerConnection
import com.cglabs.lifemusic.R
import com.cglabs.lifemusic.constants.NovaNombreKey
import com.cglabs.lifemusic.utils.rememberPreference
import com.cglabs.lifemusic.nova.NovaBloque
import com.cglabs.lifemusic.nova.NovaOnda
import com.cglabs.lifemusic.nova.NovaSesion
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Donde vive la cabina: se monta una sola vez, arriba de todo (MainActivity), y
 * se despliega cuando alguien toca la estrella de Nova.
 */
@Composable
fun NovaCabinaHost() {
    val abierta by NovaSesion.cabinaAbierta.collectAsState()
    if (abierta) NovaCabina(alCerrar = NovaSesion::cerrarCabina)
}

/**
 * 2.0 · La cabina de Nova (maqueta aprobada, pantalla 2, adaptada a Material
 * You). Todo lo de Nova vive aqui y en ningun otro lado, para que el Inicio y el
 * reproductor sigan limpios (pedido de CG, 15-10): la luz AL AIRE, la estrella
 * dentro de su luz, lo que Nova esta diciendo, el dial de estaciones y el boton
 * de encender. Se despliega desde abajo y se cierra deslizando.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NovaCabina(alCerrar: () -> Unit) {
    val conexion = LocalPlayerConnection.current ?: return
    val alAire by NovaSesion.alAire.collectAsState()
    val estado by NovaSesion.estado.collectAsState()
    val habla by NovaSesion.hablando.collectAsState()
    val cancion by conexion.mediaMetadata.collectAsState()
    val colores = rememberColoresAurora()
    val esquema = MaterialTheme.colorScheme
    val hoja = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Nova, el personaje: su estado y la energia real de su voz.
    val (estadoNova, energia) = rememberEstadoNova()

    // Personalizar vive dentro de la misma cabina, como una segunda pagina.
    var personalizando by remember { mutableStateOf(false) }
    val nombrePropio by rememberPreference(NovaNombreKey, defaultValue = "")
    val nombreDJ = nombrePropio.trim().ifEmpty { "Nova" }

    ModalBottomSheet(
        onDismissRequest = alCerrar,
        sheetState = hoja,
        containerColor = esquema.surfaceContainerLowest,
    ) {
        BackHandler(enabled = personalizando) { personalizando = false }
        AnimatedContent(
            targetState = personalizando,
            transitionSpec = { fadeIn(tween(280)).togetherWith(fadeOut(tween(200))) },
            label = "novaPagina",
        ) { enPersonalizar ->
        if (enPersonalizar) {
            NovaPersonalizar(alVolver = { personalizando = false })
        } else Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 20.dp),
        ) {
            LuzAlAire(encendida = alAire)
            Spacer(Modifier.height(10.dp))
            val cuando = remember {
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEEE h:mm a", Locale.getDefault()))
            }
            Text(
                text = stringResource(R.string.nova_cabina_rotulo, nombreDJ, cuando).uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 0.18.em,
                color = esquema.onSurfaceVariant,
                maxLines = 1,
            )

            // Nova, el personaje de luz (A · La mirada): te mira mientras escuchas.
            NovaMirada(
                estado = estadoNova,
                energia = energia,
                colores = colores,
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .size(240.dp),
            )

            // Lo que dice Nova en grande; si calla, en que va; apagada, la invitacion.
            val principal: String
            val secundario: String?
            val h = habla
            val e = estado
            when {
                h != null -> {
                    principal = h.texto
                    secundario = null
                }
                alAire && e != null -> {
                    principal = stringResource(e.bloque.nombre)
                    secundario = stringResource(R.string.nova_cabina_va, e.posicion, e.total) +
                        cancion?.let { " · ${it.title}" }.orEmpty()
                }
                alAire -> {
                    principal = stringResource(R.string.nova_sintonizando)
                    secundario = null
                }
                else -> {
                    principal = stringResource(R.string.nova_cabina_apagada)
                    secundario = stringResource(R.string.nova_cabina_promesa)
                }
            }
            AnimatedContent(
                targetState = principal,
                transitionSpec = { fadeIn(tween(380)).togetherWith(fadeOut(tween(260))) },
                label = "novaPrincipal",
                modifier = Modifier.heightIn(min = 72.dp),
            ) { texto ->
                Text(
                    text = texto,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Light,
                    textAlign = TextAlign.Center,
                    color = esquema.onSurface,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            AnimatedVisibility(secundario != null, enter = fadeIn(), exit = fadeOut()) {
                Text(
                    text = secundario.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = esquema.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }

            Spacer(Modifier.height(24.dp))
            DialNova(
                actual = if (alAire) e?.bloque else null,
                alElegir = { conexion.service.sintonizar(it) },
            )

            Spacer(Modifier.height(24.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                BotonEncender(
                    alAire = alAire,
                    alTocar = { if (alAire) conexion.service.apagarNova() else conexion.service.encenderNova() },
                )
                AnimatedVisibility(alAire, enter = fadeIn(), exit = fadeOut()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.width(16.dp))
                        FilledTonalButton(onClick = { conexion.service.cambiarDeOnda() }) {
                            Icon(
                                painter = painterResource(R.drawable.radio),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.nova_cambiar_de_onda))
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            TextButton(onClick = { personalizando = true }) {
                Icon(
                    painter = painterResource(R.drawable.tune),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.nova_personalizar))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.nova_cabina_privacidad),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = esquema.onSurfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        }
    }
}

/**
 * El dial de las estaciones: marcas de radio, la aguja en la que suena y los
 * cuatro rotulos debajo. Tocar un rotulo sintoniza esa estacion (y si Nova
 * estaba apagada, la enciende ahi). La aguja llega con un rebote de resorte,
 * como al girar la perilla.
 */
@Composable
private fun DialNova(actual: NovaBloque?, alElegir: (NovaBloque) -> Unit) {
    val estaciones = NovaBloque.DIAL
    val esquema = MaterialTheme.colorScheme
    val destino = actual?.let { estaciones.indexOf(it) } ?: -1
    val posicion by animateFloatAsState(
        if (destino >= 0) destino.toFloat() else 0f,
        spring(dampingRatio = 0.45f, stiffness = 160f),
        label = "aguja",
    )
    val visible by animateFloatAsState(if (destino >= 0) 1f else 0f, tween(400), label = "agujaVisible")

    Column(Modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(34.dp),
        ) {
            val marcas = 41
            val medio = size.height / 2f
            for (i in 0 until marcas) {
                val x = size.width * i / (marcas - 1)
                val alto = if (i % 10 == 0) 16.dp.toPx() else if (i % 5 == 0) 11.dp.toPx() else 7.dp.toPx()
                drawLine(
                    color = esquema.onSurfaceVariant.copy(alpha = 0.35f),
                    start = Offset(x, medio - alto / 2f),
                    end = Offset(x, medio + alto / 2f),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            if (visible > 0f) {
                val columna = size.width / estaciones.size
                val x = columna * (posicion + 0.5f)
                drawLine(
                    color = esquema.primary.copy(alpha = visible),
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 2.5.dp.toPx(),
                )
                drawCircle(color = esquema.primary.copy(alpha = visible), radius = 4.dp.toPx(), center = Offset(x, medio))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            estaciones.forEach { bloque ->
                val suena = bloque == actual
                Text(
                    text = stringResource(bloque.estacion).uppercase(Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    letterSpacing = 0.1.em,
                    fontWeight = if (suena) FontWeight.Bold else FontWeight.Medium,
                    color = if (suena) esquema.primary else esquema.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { alElegir(bloque) }
                        .padding(vertical = 10.dp, horizontal = 2.dp),
                )
            }
        }
    }
}
