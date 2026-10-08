package com.cglabs.lifemusic.comunidad

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cglabs.lifemusic.R

/**
 * La copa de la barra de Inicio (1.3.1), en el lugar del trofeo del reto de
 * septiembre. Lleva al prerregistro; mientras esta abierto, un punto late
 * en la esquina para que se note sin gritar.
 */
@Composable
fun CopaPrerregistro(abierto: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Box {
            Icon(
                painter = painterResource(R.drawable.trophy),
                contentDescription = stringResource(R.string.prerregistro_titulo),
            )
            if (abierto) {
                val latido = rememberInfiniteTransition(label = "copa")
                val escala by latido.animateFloat(
                    initialValue = 0.8f,
                    targetValue = 1.25f,
                    animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
                    label = "punto",
                )
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 3.dp, y = (-2).dp)
                        .size(8.dp)
                        .scale(escala)
                        .alpha(0.95f)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                )
            }
        }
    }
}
