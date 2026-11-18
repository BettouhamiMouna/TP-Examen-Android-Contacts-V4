package com.example.contacts.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.ImageView
import com.example.contacts.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Gestion des photos : chaque photo est COPIÉE dans filesDir/photos.
 * La base ne stocke que le chemin de cette copie, qui reste lisible après un redémarrage,
 * contrairement à une URI temporaire fournie par la galerie.
 */
object PhotoStorage {

    private const val DOSSIER = "photos"

    fun dossier(context: Context): File = File(context.filesDir, DOSSIER).apply { mkdirs() }

    fun nouveauFichier(context: Context): File =
        File(dossier(context), "photo_${System.currentTimeMillis()}.jpg")

    /** Copie l'image désignée par [uri] dans le stockage interne. @return le chemin, ou null si échec. */
    suspend fun copierDepuisUri(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        val destination = nouveauFichier(context)
        try {
            val input = context.contentResolver.openInputStream(uri) ?: return@withContext null
            input.use { entree -> destination.outputStream().use { sortie -> entree.copyTo(sortie) } }
            destination.absolutePath
        } catch (e: IOException) {
            destination.delete(); null
        } catch (e: SecurityException) {
            destination.delete(); null
        }
    }

    /** Supprime un fichier uniquement s'il se trouve dans notre dossier de photos. */
    fun supprimer(context: Context, chemin: String?) {
        if (chemin.isNullOrBlank()) return
        val fichier = File(chemin)
        val dansDossier = fichier.canonicalFile.parentFile == dossier(context).canonicalFile
        if (dansDossier) fichier.delete()
    }

    /** Affiche la photo réduite (évite de charger une image de 12 Mpx en mémoire), sinon l'icône par défaut. */
    fun afficher(vue: ImageView, chemin: String?, tailleMaxPx: Int = 256) {
        val bitmap = chemin?.let { decoderMiniature(it, tailleMaxPx) }
        if (bitmap != null) vue.setImageBitmap(bitmap) else vue.setImageResource(R.drawable.ic_person)
    }

    private fun decoderMiniature(chemin: String, tailleMax: Int): Bitmap? {
        if (!File(chemin).exists()) return null
        val bornes = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(chemin, bornes)
        if (bornes.outWidth <= 0 || bornes.outHeight <= 0) return null
        var echantillon = 1
        while (bornes.outWidth / (echantillon * 2) >= tailleMax &&
            bornes.outHeight / (echantillon * 2) >= tailleMax
        ) echantillon *= 2
        return BitmapFactory.decodeFile(chemin, BitmapFactory.Options().apply { inSampleSize = echantillon })
    }
}
