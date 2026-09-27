package com.cglabs.lifemusic.car

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.cglabs.lifemusic.R
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * La hora, puesta al dia en cada cambio de minuto (no cada segundo: el reloj
 * no muestra segundos y el radio no tiene por que despertarse para nada).
 *
 * Hace falta porque el carro va a pantalla completa: al esconder la barra de
 * estado se fue con ella la hora, y en un carro la hora se mira.
 */
@Composable
fun rememberAhora(): Long {
    var ahora by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            ahora = System.currentTimeMillis()
            delay(60_000L - ahora % 60_000L + 20L)
        }
    }
    return ahora
}

/** «9:41» o «21:41» segun el radio este en 12 o 24 horas. Sin a. m./p. m.: en el carro sobra. */
@Composable
fun horaCorta(ahora: Long): String {
    val contexto = LocalContext.current
    val patron = if (DateFormat.is24HourFormat(contexto)) "H:mm" else "h:mm"
    return remember(ahora, patron) { SimpleDateFormat(patron, Locale.getDefault()).format(Date(ahora)) }
}

/** «Sáb 27». */
fun fechaCorta(ahora: Long): String = fecha(ahora, "EEEd")

/** «Sábado, 27 de septiembre». */
fun fechaLarga(ahora: Long): String = fecha(ahora, "EEEEdMMMM")

private fun fecha(ahora: Long, esqueleto: String): String {
    val local = Locale.getDefault()
    return SimpleDateFormat(DateFormat.getBestDateTimePattern(local, esqueleto), local)
        .format(Date(ahora))
        .replaceFirstChar { it.titlecase(local) }
}

/** «Buenos días», «Buenas tardes» o «Buenas noches», con las mismas franjas que el saludo del telefono. */
fun cabeceraDeLaHora(contexto: Context, ahora: Long = System.currentTimeMillis()): String {
    val hora = Calendar.getInstance().apply { timeInMillis = ahora }.get(Calendar.HOUR_OF_DAY)
    return contexto.getString(
        when (hora) {
            in 5..11 -> R.string.greeting_morning
            in 12..18 -> R.string.greeting_afternoon
            else -> R.string.greeting_evening
        }
    )
}
