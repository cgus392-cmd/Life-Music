package com.cglabs.lifemusic.constants

/**
 * Identidad del repositorio de Life Music, en un solo sitio.
 *
 * Antes cada pantalla llevaba la URL del repositorio de Echo escrita a mano, asi
 * que el fork seguia consultando —y ofreciendo actualizaciones desde— la
 * infraestructura del proyecto original. Centralizarlo evita que vuelva a pasar:
 * si el repositorio cambia de sitio, se cambia aqui y ya.
 *
 * Nota: los enlaces al repositorio del proyecto original que quedan en "Acerca de"
 * NO salen de aqui y no deben moverse. Son la atribucion que exige la GPL-3.0.
 */
object Repo {
    const val OWNER = "cgus392-cmd"
    const val NAME = "Life-Music"
    const val SLUG = "$OWNER/$NAME"

    /** Pagina del repositorio. */
    const val HTML = "https://github.com/$SLUG"

    /** Base de la API de GitHub para este repositorio. */
    const val API = "https://api.github.com/repos/$SLUG"

    /** Contenido crudo de la rama principal. */
    const val RAW_MAIN = "https://raw.githubusercontent.com/$SLUG/refs/heads/main"

    /** Nombre del artefacto publicado en cada release. */
    const val APK_ASSET = "lifemusic.apk"

    // --- URLs derivadas, para no repetir concatenaciones por ahi ---

    const val RELEASES_API = "$API/releases"
    const val LATEST_RELEASE_API = "$API/releases/latest"
    const val COMMITS_API = "$API/commits"
    const val SERVER_JSON = "$RAW_MAIN/app/server.json"

    /** URL de descarga del APK de una release concreta. */
    fun apkUrl(tag: String): String = "$HTML/releases/download/$tag/$APK_ASSET"

    /** Pagina de publicaciones, para "descargar la version nueva". */
    const val RELEASES_HTML = "$HTML/releases"

    /** Politica de privacidad. La nuestra, en el repositorio. */
    const val PRIVACY_POLICY = "$HTML/blob/main/PRIVACY_POLICY.md"

    /** Aviso legal: la seccion del README. */
    const val LEGAL_NOTICE = "$HTML#aviso-legal"

    /**
     * Servidor de Escuchar juntos. NO es nuestro: pertenece al proyecto de
     * origen. Se centraliza aqui para que la dependencia este a la vista y no
     * escondida en dos pantallas, y esta declarada en PRIVACY_POLICY.md. Si
     * algun dia Life Music monta el suyo, se cambia en este sitio.
     */
    const val LISTEN_TOGETHER_SERVER = "https://echomusic-listen-together.onrender.com"

    /** URL del changelog publicado junto a una release. */
    fun changelogUrl(tag: String): String = "$HTML/releases/download/$tag/changelog.json"
}
