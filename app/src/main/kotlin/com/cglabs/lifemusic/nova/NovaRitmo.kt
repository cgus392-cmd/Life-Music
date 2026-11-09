package com.cglabs.lifemusic.nova

/**
 * 2.0 · A que ritmo habla Nova (plan por bloques del 27-10, bloque A). CG la
 * probo y «habla muy rapido, a veces no se le entiende»: las dos voces iban a
 * velocidad 1,0, que con musica por debajo atropella el español.
 *
 * [vozHD] es la velocidad de Piper y Kokoro (sherpa-onnx), [telefono] la de la
 * voz del telefono (TextToSpeech.setSpeechRate) y [msPorPalabra] lo que dura
 * cada palabra en pantalla cuando Nova va en «solo subtitulos». Lo normal ya
 * es mas pausado que antes; «Agil» deja la velocidad de siempre.
 */
enum class RitmoNova(val clave: String, val vozHD: Float, val telefono: Float, val msPorPalabra: Long) {
    PAUSADO("pausado", 0.8f, 0.82f, 520L),
    NORMAL("normal", 0.88f, 0.9f, 450L),
    AGIL("agil", 1.0f, 1.0f, 380L);

    companion object {
        fun de(valor: String?): RitmoNova = entries.firstOrNull { it.clave == valor } ?: NORMAL
    }
}

/**
 * 2.1.0 · Como entra la musica cuando Nova habla al empezar una cancion (pedido de CG, 09-11):
 * [ENCIMA], como en la radio: la cancion arranca bajita, Nova habla y termina cuando entra la
 * voz; [ANTES]: la cancion espera quieta en su arranque a que Nova termine.
 */
enum class EntradaNova(val clave: String) {
    ENCIMA("encima"),
    ANTES("antes");

    companion object {
        fun de(valor: String?): EntradaNova = entries.firstOrNull { it.clave == valor } ?: ENCIMA
    }
}
