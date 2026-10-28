package com.cglabs.lifemusic.ui.screens.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cglabs.lifemusic.R

/**
 * 2.0 · El ícono de la app (pedido de CG, 28-10): las seis versiones del logo
 * «la nota es el pulso» de Brand/app-2.0/iconos. Cada una es un activity-alias
 * del manifiesto y solo uno queda prendido. Carbón es la de fábrica y usa el alias
 * de siempre (.MainActivityAlias), asi quien nunca toca esto no ve ningun cambio.
 */
enum class IconoDeLaApp(val clave: String, val alias: String, val nombre: Int, val fondo: Int, val frente: Int) {
    CARBON("carbon", ".MainActivityAlias", R.string.icono_carbon, R.drawable.ic_launcher_carbon_background, R.drawable.ic_launcher_carbon_foreground),
    BLANCO("blanco", ".MainActivityIconoBlanco", R.string.icono_blanco, R.drawable.ic_launcher_blanco_background, R.drawable.ic_launcher_blanco_foreground),
    GRIS("gris", ".MainActivityIconoGris", R.string.icono_gris, R.drawable.ic_launcher_gris_background, R.drawable.ic_launcher_gris_foreground),
    TINTA("tinta", ".MainActivityIconoTinta", R.string.icono_tinta, R.drawable.ic_launcher_tinta_background, R.drawable.ic_launcher_tinta_foreground),
    CORAL("coral", ".MainActivityIconoCoral", R.string.icono_coral, R.drawable.ic_launcher_coral_background, R.drawable.ic_launcher_coral_foreground),
    SOBRE_CORAL("sobrecoral", ".MainActivityIconoSobreCoral", R.string.icono_sobrecoral, R.drawable.ic_launcher_sobrecoral_background, R.drawable.ic_launcher_sobrecoral_foreground);

    companion object {
        fun de(clave: String?) = entries.firstOrNull { it.clave == clave } ?: CARBON

        /** Los alias viejos (estatico y clasico) tambien se apagan: ya muestran Carbón, pero no deben duplicarse. */
        private val VIEJOS = listOf(".MainActivityStatic", ".MainActivityLegacy")

        /** Prende el alias de [icono] y apaga los demas. El launcher tarda unos segundos en enterarse. */
        fun poner(context: Context, icono: IconoDeLaApp) {
            val pm = context.packageManager
            (entries.map { it.alias } + VIEJOS).forEach { alias ->
                pm.setComponentEnabledSetting(
                    ComponentName(context, "com.cglabs.lifemusic$alias"),
                    if (alias == icono.alias) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }
        }
    }
}

/** Las miniaturas del selector: el fondo y el frente del ícono adaptativo, recortados como en el launcher. */
@Composable
fun SelectorDeIcono(actual: IconoDeLaApp, alElegir: (IconoDeLaApp) -> Unit, alCerrar: () -> Unit) {
    val contexto = LocalContext.current
    val aviso = stringResource(R.string.icono_aviso)
    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text(stringResource(R.string.icono_de_la_app)) },
        text = {
            Column {
                IconoDeLaApp.entries.chunked(3).forEach { fila ->
                    Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        fila.forEach { icono ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable {
                                    IconoDeLaApp.poner(contexto, icono)
                                    alElegir(icono)
                                    Toast.makeText(contexto, aviso, Toast.LENGTH_LONG).show()
                                }.padding(6.dp),
                            ) {
                                val forma = RoundedCornerShape(18.dp)
                                Box(
                                    Modifier.size(64.dp).clip(forma).then(
                                        if (icono == actual) Modifier.border(BorderStroke(3.dp, MaterialTheme.colorScheme.primary), forma) else Modifier,
                                    ),
                                ) {
                                    Image(painterResource(icono.fondo), contentDescription = null, modifier = Modifier.fillMaxSize())
                                    Image(painterResource(icono.frente), contentDescription = stringResource(icono.nombre), modifier = Modifier.fillMaxSize())
                                }
                                Text(
                                    stringResource(icono.nombre),
                                    style = MaterialTheme.typography.labelMedium,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                        }
                    }
                }
                Text(
                    stringResource(R.string.icono_nota),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = alCerrar) { Text(stringResource(android.R.string.ok)) } },
    )
}
