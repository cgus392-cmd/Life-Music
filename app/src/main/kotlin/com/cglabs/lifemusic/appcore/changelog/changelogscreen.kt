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
import com.cglabs.lifemusic.ui.screens.Boletines
import com.cglabs.lifemusic.ui.screens.CuerpoDeBoletin

/**
 * Historial de versiones (1.3.1, actualizador renovado): una pastilla por
 * version, como el changelog de Vivi que le gusto a CG, y debajo lo que trajo
 * esa version, con su imagen, su titulo y sus novedades.
 *
 * Sale de los boletines que viajan dentro de la app (Boletines.todos): funciona
 * sin internet y en el idioma del telefono. Reemplaza al changelog heredado de
 * Echo, que dependia de un changelog.json por publicacion que Life Music nunca
 * publico (mostraba error en todas) y que ninguna pantalla abria.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChangelogScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    // De la mas nueva a la mas vieja. Los boletines viajan con la version que los trae,
    // asi que en una build publicada nunca hay uno de una version futura.
    val versiones = remember { Boletines.todos.sortedByDescending { it.versionCode } }
    var elegida by rememberSaveable { mutableIntStateOf(versiones.firstOrNull()?.versionCode ?: 0) }
    val boletin = versiones.firstOrNull { it.versionCode == elegida } ?: versiones.firstOrNull()

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
                    val esLaInstalada = v.versionCode == BuildConfig.VERSION_CODE
                    FilterChip(
                        selected = v.versionCode == boletin?.versionCode,
                        onClick = { elegida = v.versionCode },
                        label = {
                            Text(
                                if (esLaInstalada) stringResource(R.string.historial_version_actual, v.versionName) else v.versionName,
                                fontWeight = FontWeight.SemiBold,
                            )
                        },
                        leadingIcon = if (v.grande) {
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
            if (boletin == null) {
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
                    CuerpoDeBoletin(boletin, animar = false)
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}
