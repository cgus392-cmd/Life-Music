package com.cglabs.lifemusic.ui.menu

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cglabs.lifemusic.LocalDatabase
import com.cglabs.lifemusic.R
import com.cglabs.lifemusic.constants.KaraokePropioKey
import com.cglabs.lifemusic.karaoke.KaraokeService
import com.cglabs.lifemusic.ui.component.Material3MenuItemData
import com.cglabs.lifemusic.utils.isLocalMediaId
import com.cglabs.lifemusic.utils.rememberPreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 2.x · Karaoke propio (beta): las opciones del menu de una cancion. Vacio si el
 * interruptor esta apagado o la cancion no esta en el telefono (descargada o
 * local). Lo usan el menu del reproductor, el de la cola y el de la biblioteca.
 */
@Composable
fun opcionesKaraoke(
    mediaId: String,
    titulo: String,
    descargada: Boolean,
    onDismiss: () -> Unit,
): List<Material3MenuItemData> {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val (activo) = rememberPreference(key = KaraokePropioKey, defaultValue = true)
    val hecho = remember(mediaId) { KaraokeService.tieneOriginal(context, mediaId) }
    if (!activo || !(descargada || mediaId.isLocalMediaId())) return emptyList()

    return buildList {
        add(
            Material3MenuItemData(
                title = { Text(text = stringResource(R.string.karaoke_preparar)) },
                description = { Text(text = stringResource(R.string.karaoke_preparar_desc)) },
                icon = { Icon(painter = painterResource(R.drawable.mic), contentDescription = null) },
                onClick = {
                    onDismiss()
                    KaraokeService.preparar(context, mediaId, titulo)
                    Toast.makeText(context, context.getString(R.string.karaoke_en_cola), Toast.LENGTH_LONG).show()
                },
            ),
        )
        if (hecho) {
            add(
                Material3MenuItemData(
                    title = { Text(text = stringResource(R.string.karaoke_volver)) },
                    description = { Text(text = stringResource(R.string.karaoke_volver_desc)) },
                    icon = { Icon(painter = painterResource(R.drawable.lyrics), contentDescription = null) },
                    onClick = {
                        // Fuera del alcance del menu: al cerrarse cancelaria el trabajo.
                        CoroutineScope(Dispatchers.IO).launch { KaraokeService.volverAlOriginal(context, database, mediaId) }
                        onDismiss()
                    },
                ),
            )
        }
    }
}

/**
 * 2.0.1 · La invitacion encima de una letra por linea de una cancion del telefono: un
 * toque y el karaoke propio la pasa a palabra por palabra en segundo plano.
 */
@Composable
fun InvitacionKaraoke(mediaId: String, titulo: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var pedido by remember(mediaId) { mutableStateOf(false) }
    val tinta = MaterialTheme.colorScheme.onSurface
    Row(
        modifier = modifier
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(50))
            .background(tinta.copy(alpha = 0.1f))
            .clickable(enabled = !pedido) {
                pedido = true
                KaraokeService.preparar(context, mediaId, titulo)
                Toast.makeText(context, context.getString(R.string.karaoke_en_cola), Toast.LENGTH_LONG).show()
            }
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painter = painterResource(R.drawable.mic), contentDescription = null, tint = tinta, modifier = Modifier.size(18.dp))
        Text(
            text = stringResource(if (pedido) R.string.karaoke_preparando else R.string.karaoke_invitacion),
            color = tinta, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
