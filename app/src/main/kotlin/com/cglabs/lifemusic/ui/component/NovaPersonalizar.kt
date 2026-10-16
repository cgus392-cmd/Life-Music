package com.cglabs.lifemusic.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.cglabs.lifemusic.LocalPlayerConnection
import com.cglabs.lifemusic.R
import com.cglabs.lifemusic.constants.AccountNameKey
import com.cglabs.lifemusic.constants.NovaCadaKey
import com.cglabs.lifemusic.constants.NovaComoTeDiceKey
import com.cglabs.lifemusic.constants.NovaNombreKey
import com.cglabs.lifemusic.constants.NovaRegionKey
import com.cglabs.lifemusic.constants.NovaSoloSubtitulosKey
import com.cglabs.lifemusic.nova.NovaFrases
import com.cglabs.lifemusic.nova.NovaSesion
import com.cglabs.lifemusic.utils.rememberPreference
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 2.0 · Personalizar a Nova (maqueta aprobada, pantalla 6, adaptada a Material
 * You). Vive dentro de la cabina, como su segunda pagina: el nombre, como te
 * dice, el acento de la voz con su muestra, cada cuanto habla y «Solo
 * subtitulos». Lo opcional que todavia no existe (la voz HD, la IA propia) no
 * se muestra: nada que la persona toque y no haga nada.
 */
@Composable
fun NovaPersonalizar(alVolver: () -> Unit) {
    val conexion = LocalPlayerConnection.current
    val esquema = MaterialTheme.colorScheme
    val alAire by NovaSesion.alAire.collectAsState()
    val colores = rememberColoresAurora()

    val (nombre, alCambiarNombre) = rememberPreference(NovaNombreKey, defaultValue = "")
    val (comoTeDice, alCambiarComoTeDice) = rememberPreference(NovaComoTeDiceKey, defaultValue = "")
    val cuenta by rememberPreference(AccountNameKey, defaultValue = "")
    val (region, alCambiarRegion) = rememberPreference(NovaRegionKey, defaultValue = "latam")
    val (cada, alCambiarCada) = rememberPreference(NovaCadaKey, defaultValue = 1)
    val (estiloGuardado, alCambiarEstilo) = rememberPreference(com.cglabs.lifemusic.constants.NovaEstiloKey, defaultValue = "calida")
    val estilo = NovaFrases.estilo(estiloGuardado)
    val (soloSubtitulos, alCambiarSoloSubtitulos) = rememberPreference(NovaSoloSubtitulosKey, defaultValue = false)
    val (vozHD, alCambiarVozHD) = rememberPreference(com.cglabs.lifemusic.constants.NovaVozHDKey, defaultValue = "")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .padding(bottom = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = alVolver) {
                Icon(painterResource(R.drawable.arrow_back), contentDescription = stringResource(R.string.nova_volver))
            }
            Text(stringResource(R.string.nova_tu_dj), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(10.dp))
            EtiquetaBeta()
        }

        // Su nombre, con la estrella al lado: asi se presenta.
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val (estadoNova, energia) = rememberEstadoNova()
            NovaMirada(
                estado = if (estadoNova == EstadoNova.APAGADA) EstadoNova.ESCUCHA else estadoNova,
                energia = energia,
                colores = colores,
                modifier = Modifier.size(68.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Rotulo(stringResource(R.string.nova_su_nombre))
                OutlinedTextField(
                    value = nombre,
                    onValueChange = { alCambiarNombre(it.take(20)) },
                    placeholder = { Text("Nova") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    supportingText = { Text(stringResource(R.string.nova_su_nombre_ayuda)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // Como te dice: de fabrica, el primer nombre de la cuenta.
        Spacer(Modifier.height(8.dp))
        Rotulo(stringResource(R.string.nova_como_te_dice))
        OutlinedTextField(
            value = comoTeDice,
            onValueChange = { alCambiarComoTeDice(it.take(24)) },
            placeholder = { Text(NovaFrases.primerNombre(cuenta) ?: stringResource(R.string.nova_como_te_dice_vacio)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            supportingText = { Text(stringResource(R.string.nova_como_te_dice_ayuda)) },
            modifier = Modifier.fillMaxWidth(),
        )

        // Su voz: el acento de la voz del telefono, con muestra al tocar.
        Spacer(Modifier.height(16.dp))
        Rotulo(stringResource(R.string.nova_su_voz))
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(
                "latam" to R.string.nova_voz_latam,
                "espana" to R.string.nova_voz_espana,
            ).forEach { (clave, rotulo) ->
                // La voz del telefono esta en uso solo si no hay una voz HD elegida.
                val elegida = region == clave && vozHD.isEmpty()
                OutlinedCard(
                    onClick = {
                        alCambiarRegion(clave)
                        alCambiarVozHD("")
                        conexion?.service?.probarVozNova(clave, vozHD = "")
                    },
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(if (elegida) 2.dp else 1.dp, if (elegida) esquema.primary else esquema.outlineVariant),
                    modifier = Modifier.weight(1f),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(14.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(rotulo), style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(R.string.nova_voz_del_telefono),
                                style = MaterialTheme.typography.bodySmall,
                                color = esquema.onSurfaceVariant,
                            )
                        }
                        if (elegida) {
                            Icon(
                                painterResource(R.drawable.check),
                                contentDescription = null,
                                tint = esquema.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
        Text(
            stringResource(R.string.nova_voz_ayuda),
            style = MaterialTheme.typography.bodySmall,
            color = esquema.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, start = 4.dp),
        )

        // Las voces HD: se bajan una vez y funcionan sin internet.
        SeccionVocesHD(vozElegida = vozHD, alElegir = alCambiarVozHD)

        // Su estilo: como lo dice (CG: «que el DJ se apropie de la musica»). Tocar uno lo deja
        // elegido y Nova lo dice en ese estilo.
        Spacer(Modifier.height(18.dp))
        Rotulo(stringResource(R.string.nova_su_estilo))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        ) {
            listOf(
                NovaFrases.Estilo.CALIDA to R.string.nova_estilo_calida,
                NovaFrases.Estilo.ENERGICA to R.string.nova_estilo_energica,
                NovaFrases.Estilo.BREVE to R.string.nova_estilo_breve,
                NovaFrases.Estilo.CURIOSA to R.string.nova_estilo_curiosa,
            ).forEach { (e, rotulo) ->
                FilterChip(
                    selected = e == estilo,
                    onClick = {
                        alCambiarEstilo(e.name.lowercase())
                        conexion?.service?.probarVozNova(estilo = e.name.lowercase())
                    },
                    label = { Text(stringResource(rotulo)) },
                )
            }
        }
        Text(
            stringResource(
                when (estilo) {
                    NovaFrases.Estilo.CALIDA -> R.string.nova_estilo_calida_desc
                    NovaFrases.Estilo.ENERGICA -> R.string.nova_estilo_energica_desc
                    NovaFrases.Estilo.BREVE -> R.string.nova_estilo_breve_desc
                    NovaFrases.Estilo.CURIOSA -> R.string.nova_estilo_curiosa_desc
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = esquema.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, start = 4.dp),
        )

        // Cuanto habla: de 1 (cada cancion, de fabrica) a 8.
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Rotulo(stringResource(R.string.nova_cuanto_habla), Modifier.weight(1f))
            Text(
                if (cada <= 1) stringResource(R.string.nova_cada_una) else stringResource(R.string.nova_cada_n, cada.coerceIn(1, 8)),
                style = MaterialTheme.typography.labelLarge,
                color = esquema.primary,
            )
        }
        Slider(
            value = cada.coerceIn(1, 8).toFloat(),
            onValueChange = { alCambiarCada(it.roundToInt().coerceIn(1, 8)) },
            valueRange = 1f..8f,
            steps = 6,
        )
        Row {
            Text(stringResource(R.string.nova_mucho), style = MaterialTheme.typography.labelSmall, color = esquema.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.nova_poco), style = MaterialTheme.typography.labelSmall, color = esquema.onSurfaceVariant)
        }

        // Solo subtitulos: para el bus, el trabajo o cuando alguien duerme.
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.nova_solo_subtitulos), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.nova_solo_subtitulos_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = esquema.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = soloSubtitulos, onCheckedChange = alCambiarSoloSubtitulos)
        }

        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.nova_cabina_privacidad),
            style = MaterialTheme.typography.bodySmall,
            color = esquema.onSurfaceVariant.copy(alpha = 0.8f),
        )
    }
}

/** Los rotulos de la maqueta: mayusculas pequeñas y espaciadas. */
@Composable
private fun Rotulo(texto: String, modifier: Modifier = Modifier) {
    Text(
        text = texto.uppercase(Locale.getDefault()),
        style = MaterialTheme.typography.labelSmall,
        letterSpacing = 0.14.em,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(bottom = 4.dp),
    )
}
