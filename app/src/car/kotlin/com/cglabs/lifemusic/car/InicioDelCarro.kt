package com.cglabs.lifemusic.car

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri

/**
 * Lo que el carro necesita en cuanto arranca el proceso, abra o no la pantalla:
 * por ejemplo, escuchar los botones del volante con la app detras de Waze. Es un
 * proveedor vacio declarado solo en el manifiesto del carro, porque Android crea
 * los proveedores al arrancar el proceso, antes que nada; asi el codigo comun
 * (App) no tiene que saber que existe el carro.
 */
class InicioDelCarro : ContentProvider() {
    override fun onCreate(): Boolean {
        val app = context?.applicationContext ?: return true
        TeclasDelVolante.escuchar(app)
        return true
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
