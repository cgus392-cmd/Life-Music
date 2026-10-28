package com.cglabs.lifemusic.ui.screens.bandas

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.cglabs.lifemusic.LocalPlayerAwareWindowInsets
import com.cglabs.lifemusic.R
import com.cglabs.lifemusic.bandas.Bandas
import com.cglabs.lifemusic.ui.screens.estudio.TonoEstudio
import com.cglabs.lifemusic.ui.screens.estudio.tonoDeEstudio
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * Bandas (2.0, beta): la lista, crear, entrar e invitar. Maqueta aprobada por CG
 * el 28-10 (Brand/maquetas/2026-10-28-bandas, pantallas 2 a 5). Va con el tono
 * de Estudio: la misma familia Pulso.
 */

// ---------- Piezas comunes ----------

@Composable
internal fun FondoBandas(t: TonoEstudio, contenido: @Composable ColumnScope.() -> Unit) {
    // Abajo queda el mini reproductor: lo ultimo no se esconde detras.
    val abajo = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()
    Column(
        Modifier
            .fillMaxSize()
            .background(t.fondo)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = abajo + 24.dp),
        content = contenido,
    )
}

@Composable
internal fun BarraBandas(navController: NavController, t: TonoEstudio, cerrar: Boolean = false, accion: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { navController.navigateUp() }) {
            Icon(
                painterResource(if (cerrar) R.drawable.close else R.drawable.arrow_back),
                contentDescription = stringResource(R.string.nova_volver), tint = t.txt,
            )
        }
        Spacer(Modifier.weight(1f))
        accion?.invoke()
    }
}

@Composable
internal fun TituloBandas(texto: String, t: TonoEstudio, bajada: String? = null) {
    Text(
        texto, color = t.txt, fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1.2).sp,
        modifier = Modifier.padding(horizontal = 20.dp).padding(top = 4.dp),
    )
    if (bajada != null) {
        Text(bajada, color = t.sec, fontSize = 14.5.sp, lineHeight = 20.sp, modifier = Modifier.padding(horizontal = 20.dp).padding(top = 6.dp))
    }
}

@Composable
internal fun BotonCoral(texto: String, modifier: Modifier = Modifier, habilitado: Boolean = true, icono: Int? = null, alTocar: () -> Unit) {
    Button(
        onClick = alTocar, enabled = habilitado, shape = RoundedCornerShape(26.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = CORAL_BANDAS, contentColor = Color.White,
            disabledContainerColor = CORAL_BANDAS.copy(alpha = 0.35f), disabledContentColor = Color.White.copy(alpha = 0.7f),
        ),
        modifier = modifier.fillMaxWidth().height(50.dp),
    ) {
        if (icono != null) {
            Icon(painterResource(icono), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(texto, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun BotonSuave(texto: String, t: TonoEstudio, modifier: Modifier = Modifier, icono: Int? = null, alTocar: () -> Unit) {
    Button(
        onClick = alTocar, shape = RoundedCornerShape(26.dp),
        colors = ButtonDefaults.buttonColors(containerColor = t.sup2, contentColor = t.txt),
        modifier = modifier.height(46.dp),
    ) {
        if (icono != null) {
            Icon(painterResource(icono), contentDescription = null, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(texto, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun SelectorDeColor(elegido: String, t: TonoEstudio, alElegir: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        Bandas.COLORES.entries.chunked(4).forEach { fila ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                fila.forEach { (nombre, color) ->
                    val suyo = nombre == elegido
                    Box(
                        Modifier
                            .size(64.dp, 48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .border(2.dp, if (suyo) t.txt else Color.Transparent, RoundedCornerShape(24.dp))
                            .clickable { alElegir(nombre) },
                        contentAlignment = Alignment.Center,
                    ) { CabezaPersona(color, "", ancho = 42.dp) }
                }
            }
        }
    }
}

@Composable
internal fun CampoBandas(valor: String, alCambiar: (String) -> Unit, etiqueta: String, t: TonoEstudio) {
    OutlinedTextField(
        value = valor, onValueChange = alCambiar, singleLine = true,
        label = { Text(etiqueta) },
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = CORAL_BANDAS, focusedLabelColor = CORAL_BANDAS, cursorColor = CORAL_BANDAS,
            unfocusedBorderColor = t.linea, focusedTextColor = t.txt, unfocusedTextColor = t.txt, unfocusedLabelColor = t.sec,
        ),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun Rotulo(texto: String, t: TonoEstudio, modifier: Modifier = Modifier) {
    Text(texto, color = t.sec, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = modifier.padding(top = 20.dp, bottom = 10.dp, start = 4.dp))
}

@Composable
internal fun textoDeError(e: Throwable?): String? {
    if (e == null) return null
    return when ((e as? Bandas.ErrorDeBanda)?.codigo) {
        "red" -> stringResource(R.string.bandas_error_red)
        "no-existe" -> stringResource(R.string.bandas_error_no_existe)
        "llena" -> stringResource(R.string.bandas_error_llena)
        "rechazado" -> stringResource(R.string.bandas_error_rechazado)
        "muchos-pedidos" -> stringResource(R.string.bandas_error_muchos)
        else -> stringResource(R.string.bandas_error_otro)
    }
}

/** El perfil de la persona (nombre y color), solo la primera vez. */
@Composable
private fun PerfilSiFalta(yo: Bandas.Yo?, nombre: String, alNombre: (String) -> Unit, color: String, alColor: (String) -> Unit, t: TonoEstudio) {
    if (yo == null || yo.conPerfil) return
    Rotulo(stringResource(R.string.bandas_tu_nombre), t)
    CampoBandas(nombre, { if (it.length <= 20) alNombre(it) }, stringResource(R.string.bandas_tu_nombre), t)
    Rotulo(stringResource(R.string.bandas_tu_color), t)
    SelectorDeColor(color, t, alColor)
}

// ---------- Bandas: la lista o el vacio ----------

@Composable
fun BandasScreen(navController: NavController) {
    val contexto = LocalContext.current
    val t = tonoDeEstudio()
    var bandas by remember { mutableStateOf<List<Bandas.EstadoCorto>?>(null) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    var miColor by remember { mutableStateOf("coral") }
    LaunchedEffect(Unit) {
        miColor = Bandas.yo(contexto).color
        while (true) {
            Bandas.estado(contexto)
                .onSuccess { bandas = it; error = null }
                .onFailure { error = it; if (bandas == null) bandas = emptyList() }
            delay(10_000)
        }
    }
    FondoBandas(t) {
        BarraBandas(navController, t)
        TituloBandas(stringResource(R.string.bandas_titulo), t)
        val lista = bandas
        when {
            lista == null -> Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CORAL_BANDAS)
            }
            lista.isEmpty() -> Column(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Vacio: tu cabeza abajo y los lugares por llenar.
                Acorde(listOf(Bandas.color(miColor)), alto = 120.dp, libres = 3, tinta = t.txt)
                Text(
                    stringResource(R.string.bandas_vacio_titulo), color = t.txt, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 22.dp),
                )
                Text(
                    stringResource(R.string.bandas_vacio_texto), color = t.sec, fontSize = 14.sp, lineHeight = 20.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp),
                )
                BotonCoral(stringResource(R.string.bandas_crear), Modifier.padding(top = 24.dp)) { navController.navigate("bandas/nueva") }
                BotonSuave(stringResource(R.string.bandas_entrar_codigo), t, Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    navController.navigate("bandas/entrar")
                }
                Text(
                    stringResource(R.string.bandas_sin_cuentas), color = t.terc, fontSize = 12.5.sp, lineHeight = 18.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(top = 18.dp),
                )
            }
            else -> Column(Modifier.padding(horizontal = 16.dp)) {
                Rotulo(stringResource(R.string.bandas_tus_bandas), t)
                lista.forEach { b -> FilaDeBanda(b, t) { navController.navigate("banda/${b.codigo}") } }
                BotonCoral(stringResource(R.string.bandas_crear), Modifier.padding(top = 18.dp)) { navController.navigate("bandas/nueva") }
                BotonSuave(stringResource(R.string.bandas_entrar_codigo), t, Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    navController.navigate("bandas/entrar")
                }
            }
        }
        textoDeError(error)?.let { Text(it, color = t.tarda, fontSize = 13.sp, modifier = Modifier.padding(20.dp)) }
    }
}

@Composable
private fun FilaDeBanda(b: Bandas.EstadoCorto, t: TonoEstudio, alTocar: () -> Unit) {
    Row(
        Modifier
            .padding(bottom = 10.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(t.sup)
            .clickable(onClick = alTocar)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Acorde(List(b.miembros.coerceIn(1, Bandas.MAX_MIEMBROS)) { Bandas.color(b.color) }, alto = 52.dp, tinta = t.txt)
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(b.nombre, color = t.txt, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            val detalle = when {
                b.estado == "pendiente" -> stringResource(R.string.bandas_esperando_corto)
                b.envivo != null -> stringResource(R.string.bandas_pone_musica, b.envivo.anfitrion, b.envivo.escuchando)
                else -> stringResource(R.string.bandas_cuantos, b.miembros)
            }
            Text(
                if (b.envivo != null) stringResource(R.string.bandas_en_vivo_ahora) + " · " + detalle else detalle,
                color = if (b.envivo != null) t.acentoTxt else t.sec, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (b.pedidos > 0) {
            Box(
                Modifier.size(24.dp).clip(RoundedCornerShape(12.dp)).background(CORAL_BANDAS),
                contentAlignment = Alignment.Center,
            ) { Text(b.pedidos.toString(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

// ---------- Nueva banda ----------

@Composable
fun NuevaBandaScreen(navController: NavController) {
    val contexto = LocalContext.current
    val t = tonoDeEstudio()
    val escena = rememberCoroutineScope()
    var yo by remember { mutableStateOf<Bandas.Yo?>(null) }
    var nombre by remember { mutableStateOf("") }
    var color by remember { mutableStateOf("coral") }
    var miNombre by remember { mutableStateOf("") }
    var miColor by remember { mutableStateOf("menta") }
    var trabajando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    LaunchedEffect(Unit) {
        yo = Bandas.yo(contexto).also { miNombre = it.nombre; if (it.conPerfil) miColor = it.color }
    }
    FondoBandas(t) {
        BarraBandas(navController, t, cerrar = true)
        TituloBandas(stringResource(R.string.bandas_nueva_titulo), t)
        Column(Modifier.padding(horizontal = 20.dp).padding(top = 16.dp)) {
            CampoBandas(nombre, { if (it.length <= 30) nombre = it }, stringResource(R.string.bandas_nombre), t)
            Rotulo(stringResource(R.string.bandas_su_color), t)
            SelectorDeColor(color, t) { color = it }
            PerfilSiFalta(yo, miNombre, { miNombre = it }, miColor, { miColor = it }, t)

            // La vista previa: el acorde con tu cabeza y el nombre.
            Row(
                Modifier.padding(top = 22.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(t.sup).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Acorde(listOf(Bandas.color(miColor)), alto = 64.dp, libres = 2, tinta = t.txt)
                Column(Modifier.padding(start = 14.dp)) {
                    Text(nombre.ifBlank { "…" }, color = t.txt, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.bandas_vista_previa), color = t.sec, fontSize = 13.sp, lineHeight = 18.sp)
                }
            }
            textoDeError(error)?.let { Text(it, color = t.tarda, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp)) }
            BotonCoral(
                stringResource(R.string.bandas_crear_la), Modifier.padding(top = 22.dp),
                habilitado = nombre.isNotBlank() && miNombre.isNotBlank() && !trabajando,
            ) {
                escena.launch {
                    trabajando = true
                    if (yo?.conPerfil != true) Bandas.guardarPerfil(contexto, miNombre, miColor)
                    Bandas.crear(contexto, nombre, color)
                        .onSuccess { v -> navController.navigate("banda/${v.codigo}/invitar") { popUpTo("bandas/nueva") { inclusive = true } } }
                        .onFailure { error = it }
                    trabajando = false
                }
            }
        }
    }
}

// ---------- Entrar a una banda ----------

@Composable
fun EntrarBandaScreen(navController: NavController, codigoInicial: String?) {
    val contexto = LocalContext.current
    val t = tonoDeEstudio()
    val escena = rememberCoroutineScope()
    var yo by remember { mutableStateOf<Bandas.Yo?>(null) }
    var codigo by remember { mutableStateOf(codigoInicial?.let { Bandas.limpiarCodigo(it) }.orEmpty()) }
    var miNombre by remember { mutableStateOf("") }
    var miColor by remember { mutableStateOf("menta") }
    var trabajando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    var espera by remember { mutableStateOf<Bandas.Vista?>(null) }
    LaunchedEffect(Unit) {
        yo = Bandas.yo(contexto).also { miNombre = it.nombre; if (it.conPerfil) miColor = it.color }
    }
    // Mientras espera, mira cada 5 s si ya lo aprobaron.
    LaunchedEffect(espera?.codigo) {
        val cod = espera?.codigo ?: return@LaunchedEffect
        while (true) {
            delay(5_000)
            Bandas.ver(contexto, cod)
                .onSuccess { v ->
                    if (v.estado == "miembro") navController.navigate("banda/$cod") { popUpTo("bandas/entrar") { inclusive = true } }
                }
                .onFailure { e ->
                    if ((e as? Bandas.ErrorDeBanda)?.codigo in setOf("rechazado", "no-existe")) { espera = null; error = e }
                }
        }
    }
    val pedir: () -> Unit = {
        val limpio = Bandas.limpiarCodigo(codigo)
        if (limpio != null && miNombre.isNotBlank() && !trabajando) escena.launch {
            trabajando = true
            error = null
            if (yo?.conPerfil != true) Bandas.guardarPerfil(contexto, miNombre, miColor)
            Bandas.pedir(contexto, limpio)
                .onSuccess { v ->
                    if (v.estado == "miembro") navController.navigate("banda/$limpio") { popUpTo("bandas/entrar") { inclusive = true } }
                    else espera = v
                }
                .onFailure { error = it }
            trabajando = false
        }
    }
    FondoBandas(t) {
        BarraBandas(navController, t, cerrar = true)
        TituloBandas(stringResource(R.string.bandas_entrar_titulo), t, stringResource(R.string.bandas_entrar_texto))
        Column(Modifier.padding(horizontal = 20.dp).padding(top = 20.dp)) {
            val esperando = espera
            if (esperando != null) {
                Esperando(esperando, miColor, t) {
                    escena.launch { Bandas.olvidar(contexto, esperando.codigo) }
                    espera = null
                }
                return@Column
            }
            BasicTextField(
                value = codigo,
                onValueChange = { nuevo -> codigo = nuevo.uppercase().filter { it.isLetterOrDigit() }.take(6) },
                singleLine = true,
                textStyle = TextStyle(color = Color.Transparent),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { pedir() }),
                decorationBox = { campo ->
                    Box {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            repeat(6) { i ->
                                val actual = i == codigo.length
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .height(56.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(t.sup)
                                        .border(1.5.dp, if (actual) CORAL_BANDAS else Color.Transparent, RoundedCornerShape(14.dp)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(codigo.getOrNull(i)?.toString().orEmpty(), color = t.txt, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                        Box(Modifier.size(1.dp)) { campo() }
                    }
                },
            )
            PerfilSiFalta(yo, miNombre, { miNombre = it }, miColor, { miColor = it }, t)
            textoDeError(error)?.let { Text(it, color = t.tarda, fontSize = 13.sp, modifier = Modifier.padding(top = 14.dp)) }
            BotonCoral(
                stringResource(R.string.bandas_pedir), Modifier.padding(top = 22.dp),
                habilitado = Bandas.limpiarCodigo(codigo) != null && miNombre.isNotBlank() && !trabajando,
                alTocar = pedir,
            )
        }
    }
}

/** «Esperando a Camila»: tu cabeza flota junto al acorde mientras te aprueban. */
@Composable
internal fun Esperando(v: Bandas.Vista, miColor: String, t: TonoEstudio, alCancelar: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(t.sup).padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CabezaPersona(Bandas.color(miColor), "", ancho = 30.dp)
                Acorde(coloresDe(v.colores), alto = 72.dp, tinta = t.txt)
            }
            Column(Modifier.padding(start = 14.dp)) {
                Text(stringResource(R.string.bandas_esperando_a, v.jefe), color = t.txt, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.bandas_esperando_texto, v.nombre), color = t.sec, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
        TextButton(onClick = alCancelar, modifier = Modifier.padding(top = 10.dp)) {
            Text(stringResource(R.string.bandas_cancelar), color = t.acentoTxt, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ---------- Invita a tu banda ----------

@Composable
fun InvitarBandaScreen(navController: NavController, codigo: String) {
    val contexto = LocalContext.current
    val t = tonoDeEstudio()
    var nombre by remember { mutableStateOf("") }
    LaunchedEffect(codigo) { Bandas.ver(contexto, codigo).onSuccess { nombre = it.nombre } }
    val enlace = Bandas.enlace(codigo)
    val copiado = stringResource(R.string.bandas_copiado)
    val textoCompartir = stringResource(R.string.bandas_compartir_texto, nombre, enlace, codigo)
    FondoBandas(t) {
        BarraBandas(navController, t, cerrar = true)
        TituloBandas(stringResource(R.string.bandas_invita), t, stringResource(R.string.bandas_invita_texto))
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                codigo.take(3) + " " + codigo.drop(3), color = t.txt, fontSize = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = 6.sp,
            )
            Box(Modifier.padding(top = 18.dp).clip(RoundedCornerShape(22.dp)).background(Color.White).padding(10.dp)) {
                CodigoQr(enlace, tam = 200.dp)
            }
            Text(enlace.removePrefix("https://"), color = t.sec, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 18.dp)) {
                Button(
                    onClick = {
                        val envio = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, textoCompartir) }
                        contexto.startActivity(Intent.createChooser(envio, null))
                    },
                    shape = RoundedCornerShape(23.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CORAL_BANDAS, contentColor = Color.White),
                    modifier = Modifier.height(46.dp),
                ) {
                    Icon(painterResource(R.drawable.share), contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.bandas_compartir), fontWeight = FontWeight.SemiBold)
                }
                BotonSuave(stringResource(R.string.bandas_copiar_codigo), t) {
                    val portapapeles = contexto.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    portapapeles.setPrimaryClip(ClipData.newPlainText("Life Music", codigo))
                    Toast.makeText(contexto, copiado, Toast.LENGTH_SHORT).show()
                }
            }
            Text(
                stringResource(R.string.bandas_caben), color = t.terc, fontSize = 12.5.sp, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 18.dp),
            )
            BotonSuave(stringResource(R.string.bandas_la_banda), t, Modifier.fillMaxWidth().padding(top = 22.dp)) {
                navController.navigate("banda/$codigo") { popUpTo("banda/{codigo}/invitar") { inclusive = true } }
            }
        }
    }
}
