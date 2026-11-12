package com.cglabs.lifemusic.lyrics

import com.cglabs.lifemusic.lyrics.EleccionDeLetra.Candidata
import com.cglabs.lifemusic.lyrics.EleccionDeLetra.Sincronia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** La mejor letra, no la primera: los casos que le pasaron a CG en estos meses. */
class EleccionDeLetraTest {

    private val titulo = "Hecha Pa' Mí (Video Oficial)"
    private val artista = "Grupo Frontera"
    private val duracion = 203

    private val versos = listOf(
        "Mira cómo te ves hecha pa' mí", "No hay otra que me mueva así", "Tus ojos me tienen loco",
        "Y tu boca me pide más", "Hecha pa' mí, hecha pa' mí", "Nadie más me hace sentir",
    )

    private fun porLinea(hasta: Int = 180) = versos.mapIndexed { i, v ->
        val s = 10 + i * (hasta - 10) / (versos.size - 1)
        "[%02d:%02d.00]%s".format(s / 60, s % 60, v)
    }.joinToString("\n")

    private fun palabraPorPalabra() = versos.mapIndexed { i, v ->
        val s = 10 + i * 30
        val palabras = v.split(" ").mapIndexed { j, p -> "<%02d:%02d.%02d> %s".format((s + j) / 60, (s + j) % 60, 0, p) }
        "[%02d:%02d.00] %s".format(s / 60, s % 60, palabras.joinToString(" "))
    }.joinToString("\n")

    private fun palabrasAparte() = versos.mapIndexed { i, v ->
        val s = 10 + i * 30
        "[%02d:%02d.00]%s\n<%s>".format(s / 60, s % 60, v,
            v.split(" ").mapIndexed { j, p -> "$p:${s + j}.0:${s + j + 1}.0" }.joinToString("|"))
    }.joinToString("\n")

    private val sinTiempos = versos.joinToString("\n")

    private val japonesa = listOf("君の目が好き", "あなたのために", "夜が明けるまで", "ずっとそばにいて", "愛してる", "もう一度")
        .mapIndexed { i, v -> "[00:%02d.00]%s".format(10 + i * 5, v) }.joinToString("\n")

    private fun elige(vararg c: Candidata) = EleccionDeLetra.mejor(c.toList(), titulo, artista, duracion)!!.candidata.proveedor

    @Test
    fun reconoce_los_tres_niveles_y_los_dos_formatos_de_palabra() {
        assertEquals(Sincronia.LINEA, EleccionDeLetra.sincronia(porLinea()))
        assertEquals(Sincronia.PALABRA, EleccionDeLetra.sincronia(palabraPorPalabra()))
        assertEquals(Sincronia.PALABRA, EleccionDeLetra.sincronia(palabrasAparte()))
        assertEquals(Sincronia.NINGUNA, EleccionDeLetra.sincronia(sinTiempos))
    }

    @Test
    fun palabra_por_palabra_le_gana_a_la_que_llego_primero_por_linea() {
        assertEquals("Paxsenix", elige(Candidata("LrcLib", porLinea(), 0), Candidata("Paxsenix", palabraPorPalabra(), 3)))
    }

    @Test
    fun otro_alfabeto_pierde_hasta_con_la_que_no_tiene_tiempos() {
        assertEquals("Genius", elige(Candidata("Kugou", japonesa, 0), Candidata("Genius", sinTiempos, 5)))
        val e = EleccionDeLetra.evaluar(Candidata("Kugou", japonesa, 0), titulo, artista, duracion)
        assertFalse(e.escrituraBien)
    }

    @Test
    fun otra_version_con_tiempos_de_mas_queda_debajo_de_la_sin_tiempos() {
        // Termina en 5:20 en una canción de 3:23: es la versión en vivo.
        val enVivo = porLinea(hasta = 320)
        assertFalse(EleccionDeLetra.evaluar(Candidata("SimpMusic", enVivo, 0), titulo, artista, duracion).duracionBien)
        assertEquals("YouTubeMusic", elige(Candidata("SimpMusic", enVivo, 0), Candidata("YouTubeMusic", sinTiempos, 8)))
    }

    @Test
    fun la_que_se_queda_en_la_mitad_tambien_es_otra_version() {
        assertFalse(EleccionDeLetra.evaluar(Candidata("X", porLinea(hasta = 60), 0), titulo, artista, duracion).duracionBien)
        assertTrue(EleccionDeLetra.evaluar(Candidata("X", porLinea(hasta = 190), 0), titulo, artista, duracion).duracionBien)
    }

    @Test
    fun entre_dos_iguales_desempata_el_orden_de_la_persona() {
        assertEquals("YouLyPlus", elige(Candidata("LrcLib", porLinea(), 5), Candidata("YouLyPlus", porLinea(), 0)))
    }

    @Test
    fun un_fragmento_no_es_una_letra() {
        val fragmento = "[00:10.00]Hecha pa' mí\n[00:20.00]Hecha pa' mí\n[00:30.00]Ey"
        assertEquals("Completa", elige(Candidata("Corta", fragmento, 0), Candidata("Completa", porLinea(), 4)))
    }

    @Test
    fun perfecta_solo_si_es_palabra_por_palabra_en_su_idioma_y_de_su_version() {
        assertTrue(EleccionDeLetra.evaluar(Candidata("P", palabraPorPalabra(), 0), titulo, artista, duracion).perfecta)
        assertFalse(EleccionDeLetra.evaluar(Candidata("L", porLinea(), 0), titulo, artista, duracion).perfecta)
    }

    @Test
    fun el_titulo_dentro_de_la_letra_suma() {
        val e = EleccionDeLetra.evaluar(Candidata("L", porLinea(), 0), titulo, artista, duracion)
        assertTrue(e.tituloDentro)
    }
}
