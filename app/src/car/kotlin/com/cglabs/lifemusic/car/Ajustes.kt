package com.cglabs.lifemusic.car

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.cglabs.lifemusic.BuildConfig
import com.cglabs.lifemusic.R
import android.app.Activity
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.edit
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.clip
import com.cglabs.lifemusic.constants.AppLanguageKey
import com.cglabs.lifemusic.constants.AudioQuality
import com.cglabs.lifemusic.constants.AudioQualityKey
import com.cglabs.lifemusic.constants.GreetingEnabledKey
import com.cglabs.lifemusic.constants.SYSTEM_DEFAULT
import com.cglabs.lifemusic.utils.dataStore
import com.cglabs.lifemusic.utils.detalle
import com.cglabs.lifemusic.utils.rememberEnumPreference
import com.cglabs.lifemusic.utils.rememberPreference
import com.cglabs.lifemusic.utils.titulo
import kotlinx.coroutines.launch

/** Si el fondo ambiente se mueve o se queda quieto. Solo existe en el carro. */
val CarroFondoEnMovimientoKey = booleanPreferencesKey("carroFondoEnMovimiento")

/**
 * Ajustes del carro. Pocos y grandes, cada fila entera es el interruptor: en
 * marcha nadie apunta a un switch de 32 dp. Crecera con las tandas (modo
 * noche, ecualizador).
 */
@Composable
fun Ajustes(modifier: Modifier = Modifier) {
    var saludo by rememberPreference(GreetingEnabledKey, defaultValue = true)
    var fondoEnMovimiento by rememberPreference(CarroFondoEnMovimientoKey, defaultValue = true)
    val ligero = LocalPerfil.current == Perfil.LIGERO

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TituloDePantalla(stringResource(R.string.carro_ajustes))
        Column(Modifier.fillMaxWidth().cristal(RoundedCornerShape(28.dp))) {
            FilaInterruptor(
                icono = R.drawable.ic_qs_life_logo,
                titulo = stringResource(R.string.carro_ajuste_saludo),
                texto = stringResource(R.string.carro_ajuste_saludo_texto),
                valor = saludo,
                alCambiar = { saludo = it },
            )
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 20.dp))
            // En Ligero el fondo va quieto sin importar el interruptor: se dice y se deja apagado.
            FilaInterruptor(
                icono = R.drawable.gradient,
                titulo = stringResource(R.string.carro_ajuste_fondo),
                texto = stringResource(if (ligero) R.string.carro_ajuste_fondo_ligero else R.string.carro_ajuste_fondo_texto),
                valor = fondoEnMovimiento && !ligero,
                alCambiar = { fondoEnMovimiento = it },
                habilitada = !ligero,
            )
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 20.dp))
            FilaIdioma()
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 20.dp))
            FilaCalidad()
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 20.dp))
            FilaRendimiento()
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 20.dp))
            FilaCanvas()
        }
        Spacer(Modifier.size(16.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().cristal(RoundedCornerShape(28.dp)).padding(20.dp),
        ) {
            Icon(painterResource(R.drawable.info), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(18.dp))
            Column {
                Text(
                    stringResource(R.string.app_name) + " " + BuildConfig.VERSION_NAME,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(stringResource(R.string.carro_acerca_texto), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * Calidad del audio: cuatro pastillas grandes (Automatica, Alta, Normal, Baja) y
 * debajo lo que significa la elegida. Automatica viene por defecto: con el
 * hotspot del telefono (red de datos) pone la normal, que en los parlantes de un
 * carro casi no se distingue y carga bastante mas rapido.
 */
@Composable
private fun FilaCalidad() {
    val (calidad, alCambiar) = rememberEnumPreference(AudioQualityKey, defaultValue = AudioQuality.AUTO)
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Icon(painterResource(R.drawable.graphic_eq), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.audio_quality), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(stringResource(calidad.detalle), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.size(12.dp))
        Pastillas(AudioQuality.entries, calidad, { stringResource(it.titulo) }, alCambiar)
    }
}

/**
 * Rendimiento: lo que se detecto del radio y las tres formas de ir. Automatico
 * (por defecto) decide con la RAM; Ligero y Completo lo fuerzan, por si el
 * radio se defiende mejor (o peor) de lo que dice su memoria.
 */
@Composable
private fun FilaRendimiento() {
    val equipo = LocalEquipo.current
    val (modo, alCambiar) = rememberEnumPreference(CarroRendimientoKey, defaultValue = ModoRendimiento.AUTOMATICO)
    val perfil = perfilDe(modo, equipo)
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Icon(painterResource(R.drawable.speed), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.carro_rendimiento), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(
                        R.string.carro_rendimiento_equipo,
                        equipo.ramTexto,
                        equipo.nucleos,
                        stringResource(if (equipo.bits64) R.string.carro_rendimiento_64 else R.string.carro_rendimiento_32),
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(if (perfil == Perfil.LIGERO) R.string.carro_rendimiento_efecto_ligero else R.string.carro_rendimiento_efecto_completo),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.size(12.dp))
        Pastillas(ModoRendimiento.entries, modo, { stringResource(it.titulo) }, alCambiar)
    }
}

/**
 * Canvas: Automatico (por defecto: red estable, cancion ya cargada y perfil
 * Completo), Siempre o Nunca. Debajo, lo que hace el elegido y, si el radio va
 * en Ligero, que en automatico no se carga.
 */
@Composable
private fun FilaCanvas() {
    val (modo, alCambiar) = rememberEnumPreference(CarroCanvasKey, defaultValue = ModoCanvas.AUTOMATICO)
    val ligero = LocalPerfil.current == Perfil.LIGERO
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Icon(painterResource(R.drawable.slow_motion_video), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.carro_canvas), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(stringResource(modo.detalle), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (ligero && modo == ModoCanvas.AUTOMATICO) {
                    Text(stringResource(R.string.carro_canvas_ligero), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Spacer(Modifier.size(12.dp))
        Pastillas(ModoCanvas.entries, modo, { stringResource(it.titulo) }, alCambiar)
    }
}

/** Una fila de pastillas del mismo ancho: la elegida, llena del color de la caratula. */
@Composable
private fun <T> Pastillas(opciones: List<T>, actual: T, titulo: @Composable (T) -> String, alElegir: (T) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        opciones.forEach { opcion ->
            val activa = opcion == actual
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(if (activa) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.08f))
                    .clickable { alElegir(opcion) },
            ) {
                Text(
                    titulo(opcion),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (activa) FontWeight.Bold else FontWeight.Medium,
                    color = if (activa) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** Los idiomas del carro, en el orden en que se recorren al tocar. */
private val IDIOMAS = listOf(SYSTEM_DEFAULT, "es", "en")

/**
 * Idioma: cada toque pasa al siguiente (como el sistema → Español → English) y
 * la pantalla se rehace en el nuevo. Un toque, sin menus: se cambia en marcha.
 */
@Composable
private fun FilaIdioma() {
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    val actual by rememberPreference(AppLanguageKey, defaultValue = SYSTEM_DEFAULT)
    val nombre = when (actual) {
        "es" -> "Español"
        "en" -> "English"
        else -> stringResource(R.string.carro_idioma_sistema)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 88.dp)
            .clickable {
                val siguiente = IDIOMAS[(IDIOMAS.indexOf(actual).coerceAtLeast(0) + 1) % IDIOMAS.size]
                alcance.launch {
                    contexto.dataStore.edit { it[AppLanguageKey] = siguiente }
                    (contexto as? Activity)?.recreate()
                }
            }
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Icon(painterResource(R.drawable.language), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.carro_ajuste_idioma), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.carro_ajuste_idioma_texto), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(nombre, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun FilaInterruptor(
    icono: Int,
    titulo: String,
    texto: String,
    valor: Boolean,
    alCambiar: (Boolean) -> Unit,
    habilitada: Boolean = true,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 88.dp)
            .clickable(enabled = habilitada) { alCambiar(!valor) }
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Icon(painterResource(icono), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        Column(Modifier.weight(1f)) {
            Text(titulo, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(texto, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = valor, onCheckedChange = alCambiar, enabled = habilitada)
    }
}
