package com.cglabs.lifemusic.comunidad

import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cglabs.lifemusic.R
import com.cglabs.lifemusic.constants.Repo
import kotlinx.coroutines.launch

private const val SITIO = "https://lifemusic.pages.dev"

/**
 * «¿Te gusta Life Music?» (1.3.1): cinco estrellas, un comentario opcional que
 * cambia segun la nota y, al enviar, las gracias. Con 4 o 5 estrellas invita a
 * la estrella de GitHub (para quien tiene cuenta) o a compartir; con 1 a 3, el
 * comentario solo lo lee CG. [onCerrar] recibe true si fue «Ahora no».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaDeCalificacion(
    estrellasIniciales: Int = 0,
    onCerrar: (ahoraNo: Boolean) -> Unit,
) {
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val estadoHoja = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var estrellas by remember { mutableIntStateOf(estrellasIniciales) }
    var comentario by remember { mutableStateOf("") }
    var enviando by remember { mutableStateOf(false) }
    var enviado by remember { mutableStateOf(false) }
    var sinRed by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = { onCerrar(!enviado) }, sheetState = estadoHoja) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!enviado) {
                Text(
                    stringResource(R.string.calificacion_titulo),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.calificacion_sub),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(18.dp))
                Estrellas(estrellas) { estrellas = it; sinRed = false }
                if (estrellas > 0) {
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = comentario,
                        onValueChange = { comentario = it.take(Calificacion.MAX_COMENTARIO) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 5,
                        placeholder = {
                            Text(stringResource(if (estrellas >= 4) R.string.calificacion_comentario_bien else R.string.calificacion_comentario_mejorar))
                        },
                    )
                }
                if (sinRed) {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.calificacion_sin_red), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = { onCerrar(true) }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.calificacion_ahora_no))
                    }
                    Button(
                        onClick = {
                            enviando = true
                            scope.launch {
                                val ok = Calificacion.enviar(context, estrellas, comentario)
                                enviando = false
                                if (ok) enviado = true else sinRed = true
                            }
                        },
                        enabled = estrellas > 0 && !enviando,
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.calificacion_enviar)) }
                }
            } else {
                Icon(
                    painterResource(R.drawable.estrella_llena),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp),
                )
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.calificacion_gracias), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(if (estrellas >= 4) R.string.calificacion_gracias_bien else R.string.calificacion_gracias_mejorar),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(18.dp))
                if (estrellas >= 4) {
                    Button(onClick = { uri.openUri(Repo.HTML) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(painterResource(R.drawable.github), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.calificacion_github))
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            val texto = context.getString(R.string.calificacion_compartir_texto, SITIO)
                            val envio = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, texto)
                            context.startActivity(Intent.createChooser(envio, null))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.calificacion_compartir)) }
                    Spacer(Modifier.height(8.dp))
                }
                TextButton(onClick = { onCerrar(false) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.calificacion_listo))
                }
            }
        }
    }
}

@Composable
private fun Estrellas(valor: Int, alElegir: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (n in 1..5) {
            val llena = n <= valor
            val escala by animateFloatAsState(if (llena) 1.12f else 1f, spring(dampingRatio = 0.45f), label = "estrella")
            val descripcion = stringResource(R.string.calificacion_estrellas_n, n)
            IconButton(
                onClick = { alElegir(n) },
                modifier = Modifier
                    .size(52.dp)
                    .semantics { contentDescription = descripcion },
            ) {
                Icon(
                    painterResource(if (llena) R.drawable.estrella_llena else R.drawable.star),
                    contentDescription = null,
                    tint = if (llena) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(40.dp).scale(escala),
                )
            }
        }
    }
}
