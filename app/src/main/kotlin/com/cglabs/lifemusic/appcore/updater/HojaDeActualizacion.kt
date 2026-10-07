package com.cglabs.lifemusic.appcore.updater

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.cglabs.lifemusic.R

/**
 * La hoja que sube al abrir la app cuando hay una version nueva (1.3.1). Ocupa
 * el lugar de la ventana sencilla de antes: arriba la imagen de la version (el
 * estilo que eligio CG: acrilico y cromo verde menta), el numero en grande, el
 * resumen y sus 3 a 5 funciones con icono, y el tamano de la descarga.
 *
 * Sin novedades publicadas, cuenta lo que trae la publicacion de GitHub, como
 * antes. «Mas tarde» la aparta 3 dias para esa version (ver [posponerActualizacion]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaDeActualizacion(
    version: String,
    tamanoMb: String,
    novedades: NovedadesDeVersion?,
    changelog: List<ChangelogSection>,
    descripcion: String?,
    onActualizar: () -> Unit,
    onMasTarde: () -> Unit,
) {
    val estado = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onMasTarde, sheetState = estado) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            ) {
                ImagenDeVersion(novedades?.imagen)
                Spacer(Modifier.height(18.dp))
                Text(
                    stringResource(R.string.update_hoja_eyebrow).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    stringResource(R.string.update_hoja_titulo, version.removePrefix("v")),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                val resumen = novedades?.resumen ?: descripcion?.takeIf { changelog.isEmpty() }
                resumen?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(16.dp))
                if (novedades != null && novedades.funciones.isNotEmpty()) {
                    novedades.funciones.forEach { f -> FilaDeFuncion(f) }
                } else {
                    changelog.forEach { seccion ->
                        Text(seccion.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
                        seccion.items.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 6.dp, bottom = 2.dp)) }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            if (tamanoMb.isNotBlank()) {
                Text(
                    stringResource(R.string.update_hoja_tamano, tamanoMb),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Spacer(Modifier.height(8.dp))
            }
            Button(onClick = onActualizar, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(stringResource(R.string.update_actualizar_ahora), fontWeight = FontWeight.SemiBold)
            }
            TextButton(onClick = onMasTarde, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.later))
            }
        }
    }
}

/** La imagen de la version; mientras carga o si no hay, un fondo de la marca. */
@Composable
internal fun ImagenDeVersion(url: String?, local: Int? = null) {
    val forma = RoundedCornerShape(24.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(forma)
            .background(Brush.linearGradient(listOf(Color(0xFF0B0F0D), Color(0xFF12402F), Color(0xFF0B0F0D)))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(R.drawable.music_note),
            contentDescription = null,
            tint = Color(0x5534D399),
            modifier = Modifier.size(56.dp),
        )
        if (local != null || url != null) {
            AsyncImage(
                model = local ?: url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            )
        }
    }
}

@Composable
private fun FilaDeFuncion(f: FuncionNueva) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
        ) {
            Icon(
                painterResource(Novedades.icono(f.icono)),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(Modifier.weight(1f).heightIn(min = 42.dp)) {
            Text(f.titulo, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (f.texto.isNotBlank()) {
                Text(f.texto, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
