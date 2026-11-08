package com.cglabs.lifemusic.appcore.changelog

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.cglabs.lifemusic.BuildConfig
import com.cglabs.lifemusic.R
import com.cglabs.lifemusic.appcore.updater.FichaDeVersion
import com.cglabs.lifemusic.appcore.updater.NovedadesDeVersion
import com.cglabs.lifemusic.appcore.updater.NovedadesEnLinea
import com.cglabs.lifemusic.ui.screens.Boletines
import com.cglabs.lifemusic.ui.screens.CuerpoDeBoletin

/**
 * Historial de versiones (1.3.1, actualizador renovado): una pastilla por
 * version, como el changelog de Vivi que le gusto a CG, y debajo lo que trajo
 * esa version, con su imagen, su titulo y sus novedades.
 *
 * 2.1.0: las versiones salen de las fichas en linea (NovedadesEnLinea, con copia en
 * el telefono) y, las viejas sin ficha, de los boletines que viajan en la app. Reemplaza al changelog heredado de
 * Echo, que dependia de un changelog.json por publicacion que Life Music nunca
 * publico (mostraba error en todas) y que ninguna pantalla abria.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChangelogScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val contexto = androidx.compose.ui.platform.LocalContext.current
    val locales = remember { Boletines.todos.associateBy { it.versionName } }
    var enLinea by remember { androidx.compose.runtime.mutableStateOf<List<String>>(emptyList()) }
    androidx.compose.runtime.LaunchedEffect(Unit) { enLinea = NovedadesEnLinea.indice(contexto) }
    // De la mas nueva a la mas vieja: las de la ficha en linea y las viejas que solo estan en la app.
    val versiones = remember(enLinea) { (enLinea + locales.keys).distinct().sortedWith(NovedadesEnLinea.porVersion.reversed()) }
    var elegida by rememberSaveable { androidx.compose.runtime.mutableStateOf(BuildConfig.VERSION_NAME.substringBefore("-")) }
    val nombre = elegida.takeIf { it in versiones } ?: versiones.firstOrNull()
    val fichas = remember { androidx.compose.runtime.mutableStateMapOf<String, NovedadesDeVersion?>() }
    androidx.compose.runtime.LaunchedEffect(nombre, enLinea) {
        if (nombre != null && nombre in enLinea && nombre !in fichas) fichas[nombre] = NovedadesEnLinea.ficha(contexto, nombre)
    }
    val ficha = nombre?.let { fichas[it] }
    val boletin = nombre?.let { locales[it] }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.historial_versiones)) },
                navigationIcon = {
                    IconButton(onClick = navController::navigateUp) {
                        Icon(painterResource(R.drawable.arrow_back), null)
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { relleno ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(relleno)
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Bottom)),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                versiones.forEach { v ->
                    val esLaInstalada = v == BuildConfig.VERSION_NAME.substringBefore("-")
                    FilterChip(
                        selected = v == nombre,
                        onClick = { elegida = v },
                        label = {
                            Text(
                                if (esLaInstalada) stringResource(R.string.historial_version_actual, v) else v,
                                fontWeight = FontWeight.SemiBold,
                            )
                        },
                        leadingIcon = if (locales[v]?.grande == true || v.endsWith(".0")) {
                            { Icon(painterResource(R.drawable.star), null, modifier = Modifier.padding(start = 2.dp)) }
                        } else null,
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                }
            }
            val cargando = nombre != null && nombre in enLinea && nombre !in fichas
            if (cargando) {
                androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp))
            } else if (ficha == null && boletin == null) {
                Text(
                    stringResource(R.string.historial_vacio),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                ) {
                    Spacer(Modifier.height(12.dp))
                    if (ficha != null && nombre != null) FichaDeVersion(nombre, ficha) else if (boletin != null) CuerpoDeBoletin(boletin, animar = false)
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}
