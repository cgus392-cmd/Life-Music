package com.cglabs.lifemusic.ui.screens.mixed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cglabs.lifemusic.LocalPlayerConnection
import com.cglabs.lifemusic.R
import com.cglabs.lifemusic.constants.AutomixEstilo
import com.cglabs.lifemusic.constants.AutomixEstiloKey
import com.cglabs.lifemusic.constants.MixedLargoKey
import com.cglabs.lifemusic.utils.rememberEnumPreference
import com.cglabs.lifemusic.utils.rememberPreference
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Los estilos del Mixed Editor, con los nombres que aprobó CG (07-11). */
private val ESTILOS = listOf(
    AutomixEstilo.AUTOMATICO to R.string.mixed_estilo_auto,
    AutomixEstilo.BLEND to R.string.mixed_estilo_fundido,
    AutomixEstilo.FILTRO to R.string.mixed_estilo_subida,
    AutomixEstilo.CORTE to R.string.mixed_estilo_corte,
    AutomixEstilo.ECO to R.string.mixed_estilo_eco,
)

private fun descripcion(estilo: AutomixEstilo) = when (estilo) {
    AutomixEstilo.BLEND -> R.string.mixed_estilo_fundido_desc
    AutomixEstilo.FILTRO -> R.string.mixed_estilo_subida_desc
    AutomixEstilo.CORTE -> R.string.mixed_estilo_corte_desc
    AutomixEstilo.ECO -> R.string.mixed_estilo_eco_desc
    else -> R.string.mixed_estilo_auto_desc
}

/**
 * 2.1.0 · Mixed Editor, tanda 5: la hoja que se abre al tocar la etiqueta verde «MIX». Se elige el estilo de la
 * transición (Auto, Fundido, Subida, Corte en el golpe, Eco) y su largo en compases. Se guarda para las siguientes y la
 * próxima transición se vuelve a planear con lo elegido.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HojaDeMezcla(onDejarDeMezclar: () -> Unit, onCerrar: () -> Unit) {
    val conexion = LocalPlayerConnection.current ?: return
    val alcance = rememberCoroutineScope()
    val (estilo, cambiarEstilo) = rememberEnumPreference(AutomixEstiloKey, defaultValue = AutomixEstilo.AUTOMATICO)
    val (largo, cambiarLargo) = rememberPreference(MixedLargoKey, defaultValue = 0)
    // DataStore guarda en segundo plano: se espera un momento antes de volver a planear.
    val replanificar: () -> Unit = {
        alcance.launch {
            delay(350)
            conexion.service.replanificarMezcla()
        }
    }

    ModalBottomSheet(onDismissRequest = onCerrar) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.mezclar), contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.mixed_titulo), style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.mixed_modo_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.mixed_estilo), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((valor, nombre) in ESTILOS) {
                    FilterChip(
                        selected = estilo == valor,
                        onClick = {
                            cambiarEstilo(valor)
                            replanificar()
                        },
                        label = { Text(stringResource(nombre)) },
                    )
                }
            }
            Text(
                stringResource(descripcion(estilo)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )

            Spacer(Modifier.height(20.dp))
            val conLargo = estilo != AutomixEstilo.CORTE && estilo != AutomixEstilo.ECO
            Text(stringResource(R.string.mixed_largo), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            val opciones = listOf(0, 4, 8, 16)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                opciones.forEachIndexed { i, valor ->
                    SegmentedButton(
                        selected = largo == valor,
                        onClick = {
                            cambiarLargo(valor)
                            replanificar()
                        },
                        enabled = conLargo,
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = opciones.size),
                        label = { Text(if (valor == 0) stringResource(R.string.mixed_estilo_auto) else valor.toString()) },
                    )
                }
            }
            Text(
                stringResource(if (conLargo) R.string.mixed_largo_desc else R.string.mixed_largo_no_aplica),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )

            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDejarDeMezclar) { Text(stringResource(R.string.mixed_dejar)) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onCerrar) { Text(stringResource(R.string.mixed_listo)) }
            }
        }
    }
}

/** El nombre del estilo que planeó el Automix (en minúsculas, como lo deja en su registro). */
@Composable
fun nombreDeEstiloPlaneado(estilo: String?): String = stringResource(
    when (estilo) {
        "filtro" -> R.string.mixed_estilo_subida
        "corte" -> R.string.mixed_estilo_corte
        "eco" -> R.string.mixed_estilo_eco
        "cierre" -> R.string.mixed_estilo_cierre
        else -> R.string.mixed_estilo_fundido
    },
)

/**
 * El aviso de arriba del reproductor mientras se acerca y dura la transición (lámina 3 de la Mesa de mezcla):
 * «Mixed Editor · Subida de 8 compases». En los fondos oscuros va en blanco translúcido; en el claro, en el color
 * del tema.
 */
@Composable
fun AvisoDeMezcla(visible: Boolean, texto: String, sobreOscuro: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { -it / 2 },
        exit = fadeOut() + slideOutVertically { -it / 2 },
        modifier = modifier,
    ) {
        Surface(
            shape = CircleShape,
            color = if (sobreOscuro) Color.Black.copy(alpha = 0.45f) else MaterialTheme.colorScheme.secondaryContainer,
            contentColor = if (sobreOscuro) Color.White else MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.mezclar), contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(texto, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
