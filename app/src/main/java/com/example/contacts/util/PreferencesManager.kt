package com.example.contacts.util

import android.content.Context
import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.preference.PreferenceManager
import com.example.contacts.data.ContactContract.OrdreTri

/** Valeurs déjà converties, transmises telles quelles à l'adapter et au détail. */
data class ParametresAffichage(
    val tailleSp: Float,
    @ColorInt val couleurTexte: Int,
    val appelActif: Boolean
) {
    companion object {
        val DEFAUT = ParametresAffichage(16f, Color.BLACK, false)
    }
}

/**
 * Point unique de lecture des préférences (SharedPreferences par défaut,
 * écrites automatiquement par ParametresFragment).
 */
class PreferencesManager(context: Context) {

    private val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)

    fun tailleSp(): Float =
        prefs.getString(CLE_TAILLE_POLICE, TAILLE_DEFAUT)?.toFloatOrNull() ?: TAILLE_DEFAUT.toFloat()

    @ColorInt
    fun couleurTexte(): Int = try {
        Color.parseColor(prefs.getString(CLE_COULEUR, COULEUR_DEFAUT) ?: COULEUR_DEFAUT)
    } catch (e: IllegalArgumentException) {
        Color.BLACK
    }

    fun appelDepuisListe(): Boolean = prefs.getBoolean(CLE_APPEL_LISTE, false)

    /** Bonus : 4e préférence, convertie en ordre de tri (liste blanche de clauses ORDER BY). */
    fun ordreTri(): OrdreTri = OrdreTri.depuis(prefs.getString(CLE_ORDRE_TRI, null))

    fun lire(): ParametresAffichage = ParametresAffichage(tailleSp(), couleurTexte(), appelDepuisListe())

    companion object {
        const val CLE_TAILLE_POLICE = "taille_police"
        const val CLE_COULEUR = "couleur_preferee"
        const val CLE_APPEL_LISTE = "appel_depuis_liste"
        const val CLE_ORDRE_TRI = "ordre_tri"
        private const val TAILLE_DEFAUT = "16"
        private const val COULEUR_DEFAUT = "#000000"
    }
}
