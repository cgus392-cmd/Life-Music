package com.cglabs.lifemusic.ui.screens.bandas

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.cglabs.lifemusic.LocalListenTogetherManager
import com.cglabs.lifemusic.LocalPlayerConnection
import com.cglabs.lifemusic.R
import com.cglabs.lifemusic.bandas.Bandas
import com.cglabs.lifemusic.constants.BandasActivaKey
import com.cglabs.lifemusic.listentogether.ConnectionState
import com.cglabs.lifemusic.listentogether.ListenTogetherEvent
import com.cglabs.lifemusic.ui.screens.estudio.TonoEstudio
import com.cglabs.lifemusic.ui.screens.estudio.tonoDeEstudio
import com.cglabs.lifemusic.utils.rememberPreference
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * En vivo (maqueta, pantallas 7 y 8): lo que suena, quienes estan, la cola
 * compartida y las 6 reacciones. Cada telefono pone la misma cancion a la vez con
 * el Escuchar juntos de siempre; aqui solo se ve y se reacciona. Al volver atras
 * En vivo sigue sonando; «Salir de En vivo» lo corta.
 */
@Composable
fun EnVivoScreen(navController: NavController, codigo: String) {
    val contexto = LocalContext.current
    val t = tonoDeEstudio()
    val escena = rememberCoroutineScope()
    val manager = LocalListenTogetherManager.current ?: return
    val conexion = LocalPlayerConnection.current
    var vista by remember { mutableStateOf<Bandas.Vista?>(null) }
    val estado by manager.connectionState.collectAsState()
    val sala by manager.roomState.collectAsState()
    val miId by manager.userId.collectAsState()
    val cancion = conexion?.mediaMetadata?.collectAsState()?.value
    val sonando = conexion?.isPlaying?.collectAsState()?.value == true
    // La ultima reaccion de cada persona, para que flote sobre su cabeza un rato.
    val reacciones = remember { mutableStateMapOf<String, Pair<String, Long>>() }
    // Cuantas veces brinco cada cabeza (reaccion) y cuantas recibio el mando (dos brincos).
    val brincos = remember { mutableStateMapOf<String, Int>() }
    val mandos = remember { mutableStateMapOf<String, Int>() }
    val reaccionar: (String, String) -> Unit = { id, tipo ->
        reacciones[id] = tipo to System.currentTimeMillis()
        brincos[id] = (brincos[id] ?: 0) + 1
    }
    var menuMando by remember { mutableStateOf(false) }
    val teQuitaron = stringResource(R.string.bandas_te_quitaron)

    LaunchedEffect(codigo) {
        val yo = Bandas.yo(contexto)
        if (!(manager.banda?.codigo == codigo && manager.isInRoom)) Bandas.entrarEnVivo(manager, codigo, yo)
        while (true) {
            Bandas.ver(contexto, codigo).onSuccess { vista = it }
            delay(20_000)
        }
    }
    LaunchedEffect(manager) {
        manager.events.collect { e ->
            when (e) {
                is ListenTogetherEvent.ReaccionRecibida -> reaccionar(e.payload.userId, e.payload.tipo)
                is ListenTogetherEvent.HostChanged -> mandos[e.newHostId] = (mandos[e.newHostId] ?: 0) + 1
                is ListenTogetherEvent.Kicked -> {
                    Toast.makeText(contexto, teQuitaron, Toast.LENGTH_LONG).show()
                    Bandas.salirDeEnVivo(manager)
                    navController.navigateUp()
                }
                else -> {}
            }
        }
    }
    // Las reacciones se apagan solas a los 2,5 s.
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            val ahora = System.currentTimeMillis()
            reacciones.entries.removeAll { ahora - it.value.second > 2500 }
        }
    }

    val v = vista
    val colorDe: (String) -> Color = { id -> Bandas.color(v?.miembros?.firstOrNull { it.id == id }?.color) }
    val inicialDe: (String, String) -> String = { id, nombre -> v?.miembros?.firstOrNull { it.id == id }?.inicial ?: nombre.take(1).uppercase() }
    val anfitrion = sala?.users?.firstOrNull { it.isHost }
    val soyAnfitrion = sala != null && sala?.hostId == miId

    FondoBandas(t) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { navController.navigateUp() }) {
                Icon(painterResource(R.drawable.expand_more), contentDescription = stringResource(R.string.nova_volver), tint = t.txt)
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.bandas_envivo_de, v?.nombre.orEmpty()), color = t.acentoTxt, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    when {
                        soyAnfitrion -> stringResource(R.string.bandas_tu_pones)
                        anfitrion != null -> stringResource(R.string.bandas_pone_la_musica, anfitrion.username)
                        else -> stringResource(R.string.bandas_conectando)
                    },
                    color = t.txt, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                )
            }
            if (v != null) Acorde(coloresDe(v.miembros.map { it.color }), alto = 40.dp, tinta = t.txt)
        }

        Column(Modifier.padding(horizontal = 20.dp).padding(top = 12.dp)) {
            if (sala == null || estado != ConnectionState.CONNECTED) {
                Row(Modifier.padding(vertical = 40.dp).fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = CORAL_BANDAS, modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.bandas_conectando), color = t.sec, fontSize = 14.sp)
                }
            }

            // La caratula de lo que suena (cada telefono pone la misma cancion).
            Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(28.dp)).background(t.sup), contentAlignment = Alignment.Center) {
                if (cancion?.thumbnailUrl != null) {
                    AsyncImage(model = cancion.thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
                } else if (v != null) {
                    Acorde(coloresDe(v.miembros.map { it.color }), alto = 120.dp, tinta = t.sec)
                }
            }
            Text(
                cancion?.title ?: if (soyAnfitrion) stringResource(R.string.bandas_elige_cancion)
                else stringResource(R.string.bandas_esperando_musica, anfitrion?.username.orEmpty()),
                color = t.txt, fontSize = if (cancion != null) 22.sp else 16.sp, fontWeight = FontWeight.Bold,
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 16.dp),
            )
            if (cancion != null) {
                Text(cancion.artists.joinToString { it.name }, color = t.sec, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Progreso(t)
            } else if (soyAnfitrion) {
                BotonSuave(stringResource(R.string.bandas_elegir_musica), t, Modifier.padding(top = 12.dp)) {
                    navController.navigate("home") { launchSingleTop = true }
                }
            }

            // Quienes estan: su cabeza con la inicial, y la reaccion que acaban de mandar encima.
            Row(Modifier.padding(top = 18.dp).fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    sala?.users?.forEachIndexed { indice, u ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.height(30.dp), contentAlignment = Alignment.BottomCenter) {
                                val r = reacciones[u.userId]
                                if (r != null) ReaccionQueSube(r.first, r.second, t.txt, tam = 26.dp)
                            }
                            // Tocarla brinca y dice su nombre; reacciona y brinca; recibe el mando y brinca dos veces;
                            // late con la musica mientras suena, cada una con su desfase.
                            CabezaViva(
                                colorDe(u.userId).copy(alpha = if (u.isConnected) 1f else 0.4f), inicialDe(u.userId, u.username), u.username, 34.dp,
                                brincos = brincos[u.userId] ?: 0,
                                doblesBrincos = mandos[u.userId] ?: 0,
                                latiendo = sonando && u.isConnected,
                                desfase = indice,
                            )
                            Text(
                                u.username, color = if (u.isHost) t.txt else t.sec, fontSize = 11.sp, maxLines = 1,
                                fontWeight = if (u.isHost) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.width(48.dp),
                                overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                }
                val otros = sala?.users?.filter { it.userId != miId && it.isConnected }.orEmpty()
                if (soyAnfitrion && otros.isNotEmpty()) Box {
                    BotonSuave(stringResource(R.string.bandas_pasar_mando), t) { menuMando = true }
                    DropdownMenu(expanded = menuMando, onDismissRequest = { menuMando = false }) {
                        otros.forEach { u ->
                            DropdownMenuItem(text = { Text(u.username) }, onClick = { menuMando = false; manager.transferHost(u.userId) })
                        }
                    }
                }
            }

            // La cola compartida, con quien puso cada una.
            val cola = sala?.queue.orEmpty()
            if (cola.isNotEmpty()) {
                Rotulo(stringResource(R.string.bandas_sigue_cola), t)
                cola.take(5).forEach { p ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = p.thumbnail, contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(t.sup),
                        )
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(p.title, color = t.txt, fontSize = 14.5.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                p.suggestedBy?.let { stringResource(R.string.bandas_la_puso, it) } ?: p.artist,
                                color = t.sec, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        val quien = sala?.users?.firstOrNull { it.username == p.suggestedBy }
                        if (quien != null) CabezaPersona(colorDe(quien.userId), inicialDe(quien.userId, quien.username), ancho = 26.dp)
                    }
                }
            }

            // Las 6 reacciones: una por segundo, el relevo descarta las demas.
            Row(
                Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(t.sup).padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Bandas.REACCIONES.forEach { tipo ->
                    val nombre = stringResource(nombreDeReaccion(tipo))
                    Box(
                        Modifier.size(46.dp).clip(RoundedCornerShape(23.dp)).semantics { contentDescription = nombre }.clickable {
                            manager.enviarReaccion(tipo)
                            miId?.let { reaccionar(it, tipo) }
                        },
                        contentAlignment = Alignment.Center,
                    ) { IconoReaccion(tipo, t.txt, tam = 30.dp, modifier = Modifier.padding(2.dp)) }
                }
            }

            TextButton(
                onClick = { escena.launch { Bandas.salirDeEnVivo(manager); navController.navigateUp() } },
                modifier = Modifier.padding(top = 12.dp).align(Alignment.CenterHorizontally),
            ) { Text(stringResource(R.string.bandas_salir_envivo), color = t.acentoTxt, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun Progreso(t: TonoEstudio) {
    val conexion = LocalPlayerConnection.current ?: return
    var posicion by remember { mutableLongStateOf(0L) }
    var duracion by remember { mutableLongStateOf(1L) }
    LaunchedEffect(conexion) {
        while (true) {
            posicion = conexion.player.currentPosition
            duracion = conexion.player.duration.takeIf { it > 0 } ?: 1L
            delay(500)
        }
    }
    val fraccion = (posicion.toFloat() / duracion).coerceIn(0f, 1f)
    Box(Modifier.padding(top = 14.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(t.pista)) {
        Box(Modifier.fillMaxWidth(fraccion).height(4.dp).clip(RoundedCornerShape(2.dp)).background(CORAL_BANDAS))
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(minutos(posicion), color = t.terc, fontSize = 11.sp)
        Spacer(Modifier.weight(1f))
        Text(minutos(duracion), color = t.terc, fontSize = 11.sp)
    }
}

private fun minutos(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

/**
 * Sin avisos push (maqueta, pantalla 9): al abrir la app, Inicio dice que tu banda
 * esta en vivo. Solo con Bandas prendida; mira cada 30 s mientras Inicio esta a la vista.
 */
@Composable
fun TarjetaBandaEnVivo(navController: NavController, modifier: Modifier = Modifier) {
    val (activa, _) = rememberPreference(BandasActivaKey, defaultValue = false)
    if (!activa) return
    val contexto = LocalContext.current
    val t = tonoDeEstudio()
    val manager = LocalListenTogetherManager.current
    var enVivo by remember { mutableStateOf<Bandas.EstadoCorto?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            Bandas.estado(contexto).onSuccess { lista -> enVivo = lista.firstOrNull { it.estado == "miembro" && it.envivo != null } }
            delay(30_000)
        }
    }
    val b = enVivo ?: return
    val envivo = b.envivo ?: return
    val yaEstoy = manager != null && manager.banda?.codigo == b.codigo && manager.isInRoom
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(t.sup)
            .clickable { navController.navigate("banda/${b.codigo}/envivo") }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Acorde(List(b.miembros.coerceIn(1, Bandas.MAX_MIEMBROS)) { Bandas.color(b.color) }, alto = 52.dp, tinta = t.txt)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(stringResource(R.string.bandas_tarjeta_envivo), color = t.acentoTxt, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
            Text(b.nombre, color = t.txt, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            envivo.cancion?.let {
                Text(stringResource(R.string.bandas_tarjeta_pone, envivo.anfitrion, it.titulo), color = t.sec, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Button(
            onClick = { navController.navigate("banda/${b.codigo}/envivo") }, shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CORAL_BANDAS, contentColor = Color.White),
        ) { Text(stringResource(if (yaEstoy) R.string.bandas_volver_envivo else R.string.bandas_unirme), fontWeight = FontWeight.SemiBold) }
    }
}
