package com.example.contacts.data

import android.content.ContentValues
import android.database.Cursor
import com.example.contacts.data.ContactContract.Colonnes
import com.example.contacts.data.ContactContract.OrdreTri

/**
 * Seule classe qui contient du SQL.
 * - Toutes les valeurs variables passent par des arguments ? (selectionArgs).
 * - Chaque Cursor est fermé avec use { }.
 * - Les méthodes sont bloquantes : elles doivent être appelées hors du thread principal (Dispatchers.IO).
 */
class ContactDao(private val helper: ContactDbHelper) {

    /** @return le _id du nouveau contact, ou -1 en cas d'échec. */
    fun insert(contact: Contact): Long =
        helper.writableDatabase.insert(ContactContract.TABLE, null, contact.toContentValues())

    /** @return le nombre de lignes modifiées (1 si succès, 0 si le contact n'existe plus). */
    fun update(contact: Contact): Int =
        helper.writableDatabase.update(
            ContactContract.TABLE,
            contact.toContentValues(),
            "${Colonnes.ID} = ?",
            arrayOf(contact.id.toString())
        )

    /** @return le nombre de lignes supprimées. */
    fun delete(id: Long): Int =
        helper.writableDatabase.delete(
            ContactContract.TABLE,
            "${Colonnes.ID} = ?",
            arrayOf(id.toString())
        )

    fun getAll(ordre: OrdreTri = OrdreTri.DEFAUT): List<Contact> = requeter(null, null, ordre)

    fun getById(id: Long): Contact? =
        requeter("${Colonnes.ID} = ?", arrayOf(id.toString())).firstOrNull()

    /**
     * Filtre par LIKE sur nom, prénom et téléphone.
     * LIKE est insensible à la casse pour les lettres ASCII dans SQLite.
     * Les caractères % et _ saisis par l'utilisateur sont échappés pour être pris littéralement.
     */
    fun search(texte: String, ordre: OrdreTri = OrdreTri.DEFAUT): List<Contact> {
        val motif = "%" + echapperLike(texte.trim()) + "%"
        val selection = listOf(Colonnes.NOM, Colonnes.PRENOM, Colonnes.TELEPHONE)
            .joinToString(" OR ") { "$it LIKE ? ESCAPE '\\'" }
        return requeter(selection, arrayOf(motif, motif, motif), ordre)
    }

    // ---------- Bonus : insertion en lot, avec ou sans transaction ----------

    /**
     * Insère plusieurs contacts.
     * Sans transaction, chaque insert() est validé (écrit sur disque) séparément.
     * Avec transaction, une seule validation à la fin : beaucoup plus rapide, et tout ou rien.
     * @return les _id attribués (-1 pour une insertion échouée).
     */
    fun insertAll(contacts: List<Contact>, enTransaction: Boolean): List<Long> {
        val db = helper.writableDatabase
        if (!enTransaction) {
            return contacts.map { db.insert(ContactContract.TABLE, null, it.toContentValues()) }
        }
        db.beginTransaction()
        try {
            val ids = contacts.map { db.insert(ContactContract.TABLE, null, it.toContentValues()) }
            db.setTransactionSuccessful()
            return ids
        } finally {
            db.endTransaction()
        }
    }

    /** Supprime les contacts dont l'_id est donné, dans une seule transaction. @return lignes supprimées. */
    fun deleteAll(ids: List<Long>): Int {
        val db = helper.writableDatabase
        var total = 0
        db.beginTransaction()
        try {
            ids.forEach { id ->
                total += db.delete(ContactContract.TABLE, "${Colonnes.ID} = ?", arrayOf(id.toString()))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return total
    }

    /** Supprime les contacts générés par l'import de test (prénom marqueur + nom préfixé). */
    fun deleteImportes(prenomMarqueur: String, prefixeNom: String): Int =
        helper.writableDatabase.delete(
            ContactContract.TABLE,
            "${Colonnes.PRENOM} = ? AND ${Colonnes.NOM} LIKE ? ESCAPE '\\'",
            arrayOf(prenomMarqueur, echapperLike(prefixeNom) + "%")
        )

    private fun requeter(
        selection: String?,
        args: Array<String>?,
        ordre: OrdreTri = OrdreTri.DEFAUT
    ): List<Contact> =
        helper.readableDatabase.query(
            ContactContract.TABLE,
            null,                      // toutes les colonnes
            selection,
            args,
            null,
            null,
            ordre.clause               // clause choisie dans une liste blanche (jamais saisie)
        ).use { cursor ->
            val iId = cursor.getColumnIndexOrThrow(Colonnes.ID)
            val iNom = cursor.getColumnIndexOrThrow(Colonnes.NOM)
            val iPrenom = cursor.getColumnIndexOrThrow(Colonnes.PRENOM)
            val iTel = cursor.getColumnIndexOrThrow(Colonnes.TELEPHONE)
            val iEmail = cursor.getColumnIndexOrThrow(Colonnes.EMAIL)
            val iPhoto = cursor.getColumnIndexOrThrow(Colonnes.PHOTO_URI)
            val iLat = cursor.getColumnIndexOrThrow(Colonnes.LATITUDE)
            val iLng = cursor.getColumnIndexOrThrow(Colonnes.LONGITUDE)
            val iFavori = cursor.getColumnIndexOrThrow(Colonnes.FAVORI)

            val contacts = ArrayList<Contact>(cursor.count)
            while (cursor.moveToNext()) {
                contacts += Contact(
                    id = cursor.getLong(iId),
                    nom = cursor.getString(iNom),
                    prenom = cursor.getString(iPrenom),
                    telephone = cursor.getString(iTel),
                    email = cursor.getStringOrNull(iEmail),
                    photoUri = cursor.getStringOrNull(iPhoto),
                    latitude = cursor.getDoubleOrNull(iLat),
                    longitude = cursor.getDoubleOrNull(iLng),
                    favori = cursor.getInt(iFavori) == 1
                )
            }
            contacts
        }

    private fun echapperLike(texte: String): String =
        texte.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}

private fun Cursor.getStringOrNull(index: Int): String? = if (isNull(index)) null else getString(index)

private fun Cursor.getDoubleOrNull(index: Int): Double? = if (isNull(index)) null else getDouble(index)

/** L'_id n'est jamais mis dans les ContentValues : il est attribué par AUTOINCREMENT. */
internal fun Contact.toContentValues(): ContentValues = ContentValues().apply {
    put(Colonnes.NOM, nom)
    put(Colonnes.PRENOM, prenom)
    put(Colonnes.TELEPHONE, telephone)
    if (email == null) putNull(Colonnes.EMAIL) else put(Colonnes.EMAIL, email)
    if (photoUri == null) putNull(Colonnes.PHOTO_URI) else put(Colonnes.PHOTO_URI, photoUri)
    if (latitude == null) putNull(Colonnes.LATITUDE) else put(Colonnes.LATITUDE, latitude)
    if (longitude == null) putNull(Colonnes.LONGITUDE) else put(Colonnes.LONGITUDE, longitude)
    put(Colonnes.FAVORI, if (favori) 1 else 0)
}
