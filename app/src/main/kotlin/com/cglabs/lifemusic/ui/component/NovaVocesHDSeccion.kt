package com.cglabs.lifemusic.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cglabs.lifemusic.LocalPlayerConnection
import com.cglabs.lifemusic.R
import com.cglabs.lifemusic.constants.NovaCalidadKey
import com.cglabs.lifemusic.nova.CalidadVoz
import com.cglabs.lifemusic.nova.NovaCapacidad
import com.cglabs.lifemusic.nova.NovaDescargas
import com.cglabs.lifemusic.nova.NovaVocesHD
import com.cglabs.lifemusic.nova.VozHD
import com.cglabs.lifemusic.utils.rememberPreference
import java.util.Locale

/**
 * 2.0 · Las voces HD en Personalizar, pensadas para cualquier telefono (pedido
 * de CG, 15-10: «no todos tienen la capacidad de un S25»):
 * - antes de bajar, cada voz dice como le iria a ESTE telefono, y la que no
 *   puede mover ni se deja bajar;
 * - al bajarla, se prueba de verdad; la que no da la talla no se puede elegir;
 * - Nova recomienda la mejor voz que este telefono mueve bien.
 * Con datos moviles se pregunta antes, porque pesan.
 */
@Composable
internal fun SeccionVocesHD(vozElegida: String, alElegir: (String) -> Unit) {
    val contexto = LocalContext.current
    val conexion = LocalPlayerConnection.current
    val esquema = MaterialTheme.colorScheme
    val (calidadGuardada, alCambiarCalidad) = rememberPreference(NovaCalidadKey, defaultValue = "liviana")
    val calidad = CalidadVoz.entries.firstOrNull { it.name.equals(calidadGuardada, ignoreCase = true) } ?: CalidadVoz.LIVIANA
    val estados by NovaDescargas.estados.collectAsState()
    var refresco by remember { mutableIntStateOf(0) }
    var preguntarDatos by remember { mutableStateOf<VozHD?>(null) }
    val telefono = remember { NovaCapacidad.telefono(contexto) }

    // Las que ya estaban bajadas y nunca se probaron, se prueban ahora (sin bajar nada).
    LaunchedEffect(Unit) {
        android.util.Log.i("LifeMusicNova", "telefono: ${NovaCapacidad.describir(contexto)}")
        NovaDescargas.probarPendientes(contexto)
    }

    // La recomendada: la mas natural que este telefono mueve sin problema.
    val recomendada = NovaVocesHD.CATALOGO.firstOrNull {
        NovaCapacidad.prevision(telefono, it.exigencia) == NovaCapacidad.Prevision.DISPONIBLE
    }

    fun usar(voz: VozHD) {
        val valor = NovaVocesHD.escribir(voz, calidad)
        alElegir(valor)
        conexion?.service?.probarVozNova(vozHD = valor)
    }

    Text(
        text = stringResource(R.string.nova_voces_hd).uppercase(Locale.getDefault()),
        style = MaterialTheme.typography.labelSmall,
        color = esquema.onSurfaceVariant,
        modifier = Modifier.padding(top = 18.dp, bottom = 2.dp),
    )
    Text(
        stringResource(R.string.nova_voces_hd_ayuda),
        style = MaterialTheme.typography.bodySmall,
        color = esquema.onSurfaceVariant,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
        CalidadVoz.entries.forEach { c ->
            FilterChip(
                selected = c == calidad,
                onClick = { alCambiarCalidad(c.name.lowercase()) },
                label = { Text(stringResource(c.nombre)) },
            )
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        NovaVocesHD.CATALOGO.forEach { voz ->
            val clave = voz.clave(calidad)
            val estado = estados[clave]
            val instalada = remember(clave, estado, refresco) { NovaDescargas.instalada(contexto, clave) }
            val veredicto = remember(clave, estado, refresco) { NovaCapacidad.veredictoDe(contexto, clave) }
            val prevision = NovaCapacidad.prevision(telefono, voz.exigencia)
            val enUso = vozElegida == NovaVocesHD.escribir(voz, calidad)
            val noApta = veredicto == NovaCapacidad.Veredicto.NO_APTA
            val noDisponible = !instalada && prevision == NovaCapacidad.Prevision.NO_DISPONIBLE

            // Que dice la tarjeta sobre como le va a este telefono.
            val (aviso, colorAviso) = when {
                estado is NovaDescargas.Estado.Probando ->
                    stringResource(R.string.nova_probando_telefono) to esquema.onSurfaceVariant
                instalada && veredicto == NovaCapacidad.Veredicto.FLUIDA ->
                    stringResource(R.string.nova_veredicto_fluida) to esquema.primary
                instalada && veredicto == NovaCapacidad.Veredicto.CON_ESPERA ->
                    stringResource(R.string.nova_veredicto_espera) to esquema.tertiary
                noApta -> stringResource(R.string.nova_veredicto_no_apta) to esquema.error
                noDisponible -> stringResource(R.string.nova_prevision_no) to esquema.error
                prevision == NovaCapacidad.Prevision.PUEDE_TARDAR ->
                    stringResource(R.string.nova_prevision_tarda) to esquema.tertiary
                else -> stringResource(
                    when (voz.exigencia) {
                        NovaCapacidad.Exigencia.LIGERA -> R.string.nova_voz_rapida
                        NovaCapacidad.Exigencia.MEDIA -> R.string.nova_voz_lenta
                        NovaCapacidad.Exigencia.ALTA -> R.string.nova_voz_premium
                    },
                ) to esquema.primary
            }

            OutlinedCard(
                onClick = { if (instalada && !enUso && !noApta) usar(voz) },
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(if (enUso) 2.dp else 1.dp, if (enUso) esquema.primary else esquema.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (noDisponible) 0.55f else 1f),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp)) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${voz.nombre} · ${stringResource(voz.detalle)}", style = MaterialTheme.typography.titleSmall)
                            if (voz == recomendada && !enUso) {
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    stringResource(R.string.nova_recomendada),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = esquema.onPrimaryContainer,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(esquema.primaryContainer)
                                        .padding(horizontal = 6.dp, vertical = 1.dp),
                                )
                            }
                        }
                        Text(
                            stringResource(R.string.nova_voz_peso, String.format(Locale.getDefault(), "%.0f", voz.megas(calidad)), voz.licencia),
                            style = MaterialTheme.typography.bodySmall,
                            color = esquema.onSurfaceVariant,
                        )
                        Text(aviso, style = MaterialTheme.typography.labelSmall, color = colorAviso)
                        // Una prueba en mal momento no puede condenar a una voz: se puede repetir.
                        if (instalada && veredicto != null && veredicto != NovaCapacidad.Veredicto.FLUIDA &&
                            estado !is NovaDescargas.Estado.Probando
                        ) {
                            Text(
                                stringResource(R.string.nova_probar_otra_vez),
                                style = MaterialTheme.typography.labelMedium,
                                color = esquema.primary,
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { NovaDescargas.probarOtraVez(contexto, voz, calidad) }
                                    .padding(vertical = 4.dp, horizontal = 2.dp),
                            )
                        }
                    }
                    when {
                        estado is NovaDescargas.Estado.Bajando -> {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(40.dp)) {
                                CircularProgressIndicator(progress = { estado.progreso }, modifier = Modifier.size(34.dp), strokeWidth = 3.dp)
                                Text("${(estado.progreso * 100).toInt()}", style = MaterialTheme.typography.labelSmall)
                            }
                            IconButton(onClick = { NovaDescargas.cancelar(clave) }) {
                                Icon(painterResource(R.drawable.close), contentDescription = stringResource(R.string.nova_cancelar))
                            }
                        }
                        estado is NovaDescargas.Estado.Abriendo || estado is NovaDescargas.Estado.Probando -> {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(if (estado is NovaDescargas.Estado.Abriendo) R.string.nova_abriendo else R.string.nova_probando),
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        instalada && enUso -> {
                            Icon(painterResource(R.drawable.check), contentDescription = null, tint = esquema.primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.nova_en_uso), style = MaterialTheme.typography.labelMedium, color = esquema.primary)
                            BotonBorrar { NovaDescargas.borrar(contexto, clave); alElegir(""); refresco++ }
                        }
                        instalada -> {
                            if (!noApta) TextButton(onClick = { usar(voz) }) { Text(stringResource(R.string.nova_usar)) }
                            BotonBorrar { NovaDescargas.borrar(contexto, clave); refresco++ }
                        }
                        noDisponible -> Unit
                        else -> {
                            val fallo = estado as? NovaDescargas.Estado.Fallo
                            FilledTonalButton(onClick = {
                                if (NovaDescargas.conDatosMoviles(contexto)) preguntarDatos = voz
                                else NovaDescargas.bajar(contexto, voz, calidad)
                            }) {
                                Icon(painterResource(R.drawable.download), contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(if (fallo != null) R.string.nova_reintentar else R.string.nova_bajar))
                            }
                        }
                    }
                }
            }
        }
    }

    // Creditos: lo piden las licencias de las voces, y es justo.
    Spacer(Modifier.height(10.dp))
    Text(
        text = stringResource(R.string.nova_voces_creditos) + " " +
            NovaVocesHD.CATALOGO.distinctBy { it.credito }.joinToString(" · ") { "${it.nombre}: ${it.credito}, ${it.licencia}" } + ".",
        style = MaterialTheme.typography.bodySmall,
        color = esquema.onSurfaceVariant.copy(alpha = 0.8f),
    )

    preguntarDatos?.let { voz ->
        AlertDialog(
            onDismissRequest = { preguntarDatos = null },
            title = { Text(stringResource(R.string.nova_datos_titulo)) },
            text = {
                Text(stringResource(R.string.nova_datos_texto, voz.nombre, String.format(Locale.getDefault(), "%.0f", voz.megas(calidad))))
            },
            confirmButton = {
                TextButton(onClick = {
                    NovaDescargas.bajar(contexto, voz, calidad)
                    preguntarDatos = null
                }) { Text(stringResource(R.string.nova_datos_bajar)) }
            },
            dismissButton = {
                TextButton(onClick = { preguntarDatos = null }) { Text(stringResource(R.string.nova_datos_esperar)) }
            },
        )
    }
}

@Composable
private fun BotonBorrar(alTocar: () -> Unit) {
    IconButton(onClick = alTocar) {
        Icon(painterResource(R.drawable.delete), contentDescription = stringResource(R.string.nova_borrar_voz), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
