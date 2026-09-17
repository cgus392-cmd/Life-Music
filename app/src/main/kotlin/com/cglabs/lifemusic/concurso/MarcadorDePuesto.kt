package com.cglabs.lifemusic.concurso

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cglabs.lifemusic.R
import com.cglabs.lifemusic.constants.ConcursoIdKey
import com.cglabs.lifemusic.constants.ConcursoParticipantesKey
import com.cglabs.lifemusic.constants.ConcursoPuestoKey
import com.cglabs.lifemusic.utils.rememberPreference

/**
 * Chapita con el puesto en el Reto de la semana, para el mini-reproductor:
 * trofeo y «#3». Solo existe mientras el reto esta visible y el usuario esta
 * inscrito con un puesto ya asignado; fuera de eso no ocupa ni un pixel.
 *
 * El puesto lo escribe [ConcursoRepository.sincronizar] en las preferencias
 * tras cada envio (cada pocos minutos mientras suena algo y al acabar cada
 * cancion), asi que se mueve solo. Cuando sube de puesto da un brinco, que
 * es lo que uno quiere ver cuando esta escuchando para ganar.
 */
@Composable
fun MarcadorDePuesto(
    colorTexto: Color,
    colorFondo: Color,
    modifier: Modifier = Modifier,
) {
    if (!Concurso.visible()) return
    val id by rememberPreference(ConcursoIdKey, "")
    val puesto by rememberPreference(ConcursoPuestoKey, -1)
    val participantes by rememberPreference(ConcursoParticipantesKey, -1)
    if (id.isBlank() || puesto <= 0) return

    val escala = remember { Animatable(1f) }
    var puestoAnterior by remember { mutableIntStateOf(puesto) }
    LaunchedEffect(puesto) {
        if (puesto < puestoAnterior) {
            escala.animateTo(1.4f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
            escala.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        }
        puestoAnterior = puesto
    }

    val descripcion = if (participantes > 0) {
        stringResource(R.string.concurso_puesto, puesto, participantes)
    } else {
        "#$puesto"
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .graphicsLayer { scaleX = escala.value; scaleY = escala.value }
            .background(colorFondo, CircleShape)
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .semantics { contentDescription = descripcion },
    ) {
        Icon(
            painter = painterResource(R.drawable.trophy),
            contentDescription = null,
            tint = colorTexto,
            modifier = Modifier.size(12.dp),
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = "#$puesto",
            color = colorTexto,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}
