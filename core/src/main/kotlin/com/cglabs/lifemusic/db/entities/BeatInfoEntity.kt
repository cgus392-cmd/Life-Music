package com.cglabs.lifemusic.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "beat_info")
data class BeatInfoEntity(
    @PrimaryKey val songId: String,
    val bpm: Float,
    val firstBeatOffsetMs: Long,
    val confidence: Float,
    val analyzedAt: Long = System.currentTimeMillis(),
    /** Where the incoming track should start: first sustained-energy downbeat past the intro. */
    val mixInPointMs: Long? = null,
    /** Where the outgoing track's body ends (outro begins); transition starts here. */
    val mixOutPointMs: Long? = null,
    /** 0=C, 1=C#, ... 11=B. Null when the track's chroma signal was too weak to call a key. */
    val keyPitchClass: Int? = null,
    val keyIsMinor: Boolean? = null,
    /**
     * Donde la musica deja de oirse de verdad, excluyendo fade y silencio final.
     * La transicion tiene que TERMINAR aqui, no en la duracion del fichero: en
     * generos con cola larga —salsa, vallenato, tropical— la diferencia son
     * 20 o 30 segundos, y sin esto el filtro actuaba sobre nada. -1 = escaneado,
     * no encontrado; null = fila anterior a este campo, se reanaliza una vez.
     */
    val contentEndMs: Long? = null,
)
