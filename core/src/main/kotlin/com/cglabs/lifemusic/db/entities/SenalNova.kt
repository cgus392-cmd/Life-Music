package com.cglabs.lifemusic.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 2.0 · Lo que Nova aprende de como escuchas. La tabla `event` solo guarda lo
 * que sono 30 s o mas, asi que lo que saltas se perdia: aqui queda cada cancion
 * que termina de sonar, con cuanto sono de verdad.
 *
 * Vive solo en el telefono: no se sube a ningun lado, respeta «Pausar historial»
 * y se borra con «Borrar historial». Ficha: docs/versiones/2.0/nova.md (4.2).
 *
 * Se guardan los tiempos en bruto, ademas del [tipo], para poder afinar despues
 * que cuenta como salto sin perder lo ya aprendido.
 */
@Entity(
    tableName = "senal_nova",
    indices = [Index("songId"), Index("fecha")],
)
data class SenalNova(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: String,
    /** Cuando termino de sonar (epoch ms). La hora y el dia salen de aqui. */
    val fecha: Long,
    /** [SALTO] o [ESCUCHADA]. */
    val tipo: Int,
    /** Cuanto sono de verdad, sin contar pausas. */
    val escuchadoMs: Long,
    /** Lo que dura la cancion. */
    val duracionMs: Long,
) {
    companion object {
        /** La dejo antes de la mitad. */
        const val SALTO = 0

        /** Sono la mitad o mas. */
        const val ESCUCHADA = 1
    }
}
