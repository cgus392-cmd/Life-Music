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
import com.cglabs.lifemusic.constants.GreetingEnabledKey
import com.cglabs.lifemusic.utils.rememberPreference

/** Si el fondo ambiente se mueve o se queda quieto. Solo existe en el carro. */
val CarroFondoEnMovimientoKey = booleanPreferencesKey("carroFondoEnMovimiento")

/**
 * Ajustes del carro. Pocos y grandes, cada fila entera es el interruptor: en
 * marcha nadie apunta a un switch de 32 dp. Crecera con las tandas (modo
 * noche, ecualizador, perfil segun la RAM).
 */
@Composable
fun Ajustes(modifier: Modifier = Modifier) {
    var saludo by rememberPreference(GreetingEnabledKey, defaultValue = true)
    var fondoEnMovimiento by rememberPreference(CarroFondoEnMovimientoKey, defaultValue = true)

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
            FilaInterruptor(
                icono = R.drawable.gradient,
                titulo = stringResource(R.string.carro_ajuste_fondo),
                texto = stringResource(R.string.carro_ajuste_fondo_texto),
                valor = fondoEnMovimiento,
                alCambiar = { fondoEnMovimiento = it },
            )
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

@Composable
private fun FilaInterruptor(icono: Int, titulo: String, texto: String, valor: Boolean, alCambiar: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 88.dp)
            .clickable { alCambiar(!valor) }
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Icon(painterResource(icono), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        Column(Modifier.weight(1f)) {
            Text(titulo, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(texto, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = valor, onCheckedChange = alCambiar)
    }
}
