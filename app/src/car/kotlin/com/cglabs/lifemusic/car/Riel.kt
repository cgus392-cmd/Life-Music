package com.cglabs.lifemusic.car

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cglabs.lifemusic.R

/** Las pantallas del carro, en el orden del riel. */
enum class Destino(val icono: Int, val iconoActivo: Int, val nombre: Int) {
    INICIO(R.drawable.home_outlined, R.drawable.home_filled, R.string.carro_inicio),
    BUSCAR(R.drawable.search, R.drawable.search, R.string.carro_buscar),
    BIBLIOTECA(R.drawable.library_music_outlined, R.drawable.library_music_filled, R.string.carro_biblioteca),
    COLA(R.drawable.queue_music, R.drawable.queue_music, R.string.carro_cola),
    AJUSTES(R.drawable.settings, R.drawable.settings, R.string.carro_ajustes),
}

/**
 * El riel de navegacion, flotando en cristal a la izquierda. En un radio de
 * 600 px de alto una barra abajo se come lo que mas falta (altura); a un lado,
 * ademas, queda a mano del conductor en los carros con volante a la izquierda.
 *
 * Arriba la hora (el carro va a pantalla completa y sin ella no hay reloj),
 * en medio las cuatro pantallas y abajo, aparte, Ajustes.
 */
@Composable
fun Riel(actual: Destino, alElegir: (Destino) -> Unit, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxHeight().width(112.dp).cristal(RoundedCornerShape(32.dp)).padding(vertical = 14.dp),
    ) {
        RelojDelRiel()
        Spacer(Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            listOf(Destino.INICIO, Destino.BUSCAR, Destino.BIBLIOTECA, Destino.COLA).forEach { d ->
                ItemDelRiel(d, d == actual) { alElegir(d) }
            }
        }
        Spacer(Modifier.weight(1f))
        ItemDelRiel(Destino.AJUSTES, actual == Destino.AJUSTES) { alElegir(Destino.AJUSTES) }
    }
}

@Composable
private fun RelojDelRiel() {
    val ahora = rememberAhora()
    Text(horaCorta(ahora), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    Text(fechaCorta(ahora), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * Un destino: la pastilla del activo se estira con muelle (Expressive) y se
 * rellena del color de la caratula; el resto queda en contorno.
 */
@Composable
private fun ItemDelRiel(d: Destino, activo: Boolean, alTocar: () -> Unit) {
    val ancho by animateDpAsState(if (activo) 76.dp else 52.dp, spring(dampingRatio = 0.55f, stiffness = 500f), label = "anchoPastilla")
    val fondo by animateColorAsState(if (activo) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, tween(250), label = "fondoPastilla")
    val tinta = if (activo) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = alTocar)
            .padding(vertical = 6.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(width = ancho, height = 40.dp).clip(CircleShape).background(fondo)) {
            Icon(painterResource(if (activo) d.iconoActivo else d.icono), contentDescription = null, tint = tinta, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(d.nombre),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (activo) FontWeight.Bold else FontWeight.Medium,
            color = if (activo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
