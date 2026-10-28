package com.cglabs.lifemusic.ui.screens.bandas

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.cglabs.lifemusic.LocalListenTogetherManager
import com.cglabs.lifemusic.R
import com.cglabs.lifemusic.bandas.Bandas
import com.cglabs.lifemusic.ui.screens.estudio.TonoEstudio
import com.cglabs.lifemusic.ui.screens.estudio.tonoDeEstudio
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/*
 * La banda por dentro (maqueta, pantalla 6): el acorde, En vivo arriba, quien pide
 * entrar, quienes son y salir. Se refresca cada 8 s mientras esta abierta: sin
 * push, es la forma de ver a quien pide entrar y si la banda esta en vivo.
 */
@Composable
fun BandaScreen(navController: NavController, codigo: String) {
    val contexto = LocalContext.current
    val t = tonoDeEstudio()
    val escena = rememberCoroutineScope()
    val manager = LocalListenTogetherManager.current
    var vista by remember { mutableStateOf<Bandas.Vista?>(null) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    var confirmar by remember { mutableStateOf<String?>(null) }
    var miColor by remember { mutableStateOf("coral") }
    var recargar by remember { mutableStateOf(0) }
    LaunchedEffect(codigo, recargar) {
        miColor = Bandas.yo(contexto).color
        while (true) {
            Bandas.ver(contexto, codigo).onSuccess { vista = it; error = null }.onFailure { error = it }
            delay(8_000)
        }
    }
    val v = vista
    FondoBandas(t) {
        BarraBandas(navController, t) {
            if (v?.estado == "miembro") IconButton(onClick = { navController.navigate("banda/$codigo/invitar") }) {
                Icon(painterResource(R.drawable.share), contentDescription = stringResource(R.string.bandas_invita), tint = t.txt)
            }
        }
        when {
            v == null && error == null -> Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CORAL_BANDAS)
            }
            v == null -> Text(
                if ((error as? Bandas.ErrorDeBanda)?.codigo == "no-existe") stringResource(R.string.bandas_te_quitaron) else textoDeError(error).orEmpty(),
                color = t.sec, fontSize = 15.sp, modifier = Modifier.padding(20.dp),
            )
            v.estado == "pendiente" -> Column(Modifier.padding(20.dp)) {
                TituloBandas(v.nombre, t)
                Spacer(Modifier.height(16.dp))
                Esperando(v, miColor, t) {
                    escena.launch { Bandas.olvidar(contexto, codigo); navController.navigateUp() }
                }
            }
            v.estado == "rechazado" -> Text(stringResource(R.string.bandas_error_rechazado), color = t.sec, fontSize = 15.sp, modifier = Modifier.padding(20.dp))
            else -> ContenidoDeBanda(v, t, manager != null && manager.banda?.codigo == codigo && manager.isInRoom,
                alEnVivo = { navController.navigate("banda/$codigo/envivo") },
                alDecidir = { accion, id ->
                    escena.launch { Bandas.decidir(contexto, codigo, accion, id).onSuccess { vista = it }.onFailure { error = it } }
                },
                alSalir = { confirmar = "salir" },
                alBorrar = { confirmar = "borrar" },
            )
        }
        if (v != null) textoDeError(error)?.let { Text(it, color = t.tarda, fontSize = 13.sp, modifier = Modifier.padding(20.dp)) }
    }

    val accion = confirmar
    if (accion != null && v != null) {
        val avisoDeJefe: (@Composable () -> Unit)? =
            if (accion == "salir" && v.soyJefe && v.miembros.size > 1) ({ Text(stringResource(R.string.bandas_salir_confirma_jefe)) }) else null
        AlertDialog(
            onDismissRequest = { confirmar = null },
            title = {
                Text(if (accion == "borrar") stringResource(R.string.bandas_borrar_confirma, v.nombre) else stringResource(R.string.bandas_salir_confirma, v.nombre))
            },
            text = avisoDeJefe,
            confirmButton = {
                TextButton(onClick = {
                    confirmar = null
                    escena.launch {
                        if (manager != null && manager.banda?.codigo == codigo) Bandas.salirDeEnVivo(manager)
                        val r = if (accion == "borrar") Bandas.borrar(contexto, codigo) else Bandas.salir(contexto, codigo)
                        r.onSuccess { navController.navigateUp() }.onFailure { error = it }
                    }
                }) {
                    Text(stringResource(if (accion == "borrar") R.string.bandas_si_borrar else R.string.bandas_si_salir), color = CORAL_BANDAS)
                }
            },
            dismissButton = { TextButton(onClick = { confirmar = null }) { Text(stringResource(R.string.bandas_cancelar)) } },
        )
    }
}

@Composable
private fun ContenidoDeBanda(
    v: Bandas.Vista,
    t: TonoEstudio,
    yaEstoyEnVivo: Boolean,
    alEnVivo: () -> Unit,
    alDecidir: (String, String) -> Unit,
    alSalir: () -> Unit,
    alBorrar: () -> Unit,
) {
    val jefe = v.miembros.firstOrNull { it.id == v.jefe }
    Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.Bottom) {
        Acorde(coloresDe(v.miembros.map { it.color }), alto = 96.dp, tinta = t.txt)
        Column(Modifier.padding(start = 14.dp, bottom = 4.dp)) {
            Text(v.nombre, color = t.txt, fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp)
            Text(stringResource(R.string.bandas_creo, v.miembros.size, jefe?.nombre.orEmpty()), color = t.sec, fontSize = 13.sp)
        }
    }
    Column(Modifier.padding(horizontal = 16.dp).padding(top = 18.dp)) {
        // En vivo arriba: la tarjeta con lo que suena, o la invitacion a prenderlo.
        val envivo = v.envivo
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(
                    if (envivo != null) Brush.horizontalGradient(listOf(Color(0xFF5B2A86), Color(0xFFC8410F)))
                    else Brush.horizontalGradient(listOf(t.sup, t.sup))
                )
                .clickable(onClick = alEnVivo)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                if (envivo != null) {
                    Text(stringResource(R.string.bandas_en_vivo_ahora).uppercase(), color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text(
                        envivo.cancion?.titulo ?: "…", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp),
                    )
                    Text(stringResource(R.string.bandas_pone_musica, envivo.anfitrion, envivo.escuchando), color = Color.White.copy(alpha = 0.8f), fontSize = 12.5.sp)
                } else {
                    Text(stringResource(R.string.bandas_prender_envivo), color = t.txt, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.bandas_prender_envivo_desc), color = t.sec, fontSize = 13.sp, lineHeight = 18.sp)
                }
            }
            Button(
                onClick = alEnVivo, shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (envivo != null) Color.White.copy(alpha = 0.92f) else CORAL_BANDAS,
                    contentColor = if (envivo != null) Color(0xFF111111) else Color.White,
                ),
            ) {
                Text(
                    stringResource(if (yaEstoyEnVivo) R.string.bandas_volver_envivo else if (envivo != null) R.string.bandas_unirme else R.string.bandas_prender_envivo),
                    fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp,
                )
            }
        }

        // Quien pide entrar: solo lo ve quien la creo, que es quien aprueba.
        v.pedidos.forEach { p ->
            Row(
                Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(t.sup).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CabezaPersona(Bandas.color(p.color), p.inicial, ancho = 34.dp)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(stringResource(R.string.bandas_quiere_entrar, p.nombre), color = t.txt, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.bandas_con_tu_codigo), color = t.sec, fontSize = 12.5.sp)
                }
                TextButton(onClick = { alDecidir("rechazar", p.id) }) { Text(stringResource(R.string.bandas_no), color = t.acentoTxt) }
                Button(
                    onClick = { alDecidir("aprobar", p.id) }, shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CORAL_BANDAS, contentColor = Color.White),
                ) { Text(stringResource(R.string.bandas_aprobar), fontWeight = FontWeight.SemiBold) }
            }
        }

        Rotulo(stringResource(R.string.bandas_la_banda), t)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(t.sup)) {
            v.miembros.forEachIndexed { i, m ->
                FilaDeMiembro(m, t, esJefe = m.id == v.jefe, soyYo = m.id == v.yo, puedoQuitar = v.soyJefe && m.id != v.yo) { alDecidir("quitar", m.id) }
                if (i < v.miembros.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(t.linea))
            }
        }

        Row(
            Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(t.sup).clickable(onClick = alSalir).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.logout), contentDescription = null, tint = t.acentoTxt, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.bandas_salir), color = t.acentoTxt, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
        if (v.soyJefe) {
            TextButton(onClick = alBorrar, modifier = Modifier.padding(top = 6.dp)) {
                Text(stringResource(R.string.bandas_borrar), color = t.terc, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun FilaDeMiembro(m: Bandas.Miembro, t: TonoEstudio, esJefe: Boolean, soyYo: Boolean, puedoQuitar: Boolean, alQuitar: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        CabezaViva(Bandas.color(m.color), m.inicial, m.nombre, 34.dp)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(m.nombre, color = t.txt, fontSize = 15.sp, fontWeight = if (soyYo) FontWeight.SemiBold else FontWeight.Medium)
            val detalle = if (esJefe) stringResource(R.string.bandas_creo_la_banda) else desdeCuando(m.desde)?.let { stringResource(R.string.bandas_desde, it) }
            if (detalle != null) Text(detalle, color = t.sec, fontSize = 12.5.sp)
        }
        if (puedoQuitar) Box {
            IconButton(onClick = { menu = true }) { Icon(painterResource(R.drawable.more_vert), contentDescription = null, tint = t.sec) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.bandas_quitar)) }, onClick = { menu = false; alQuitar() })
            }
        }
    }
}

/** «27 de octubre» a partir de la fecha ISO que guarda el relevo. */
private fun desdeCuando(iso: String?): String? = runCatching {
    OffsetDateTime.parse(iso).toLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG))
}.getOrNull()

