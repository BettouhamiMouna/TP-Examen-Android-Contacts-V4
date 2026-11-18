package com.example.contacts.data

import android.provider.BaseColumns

/**
 * Nom de la table et des colonnes : aucun nom de colonne n'est écrit en dur ailleurs dans le code.
 */
object ContactContract {

    const val TABLE = "contacts"

    object Colonnes {
        const val ID = BaseColumns._ID          // "_id"
        const val NOM = "nom"
        const val PRENOM = "prenom"
        const val TELEPHONE = "telephone"
        const val EMAIL = "email"
        const val PHOTO_URI = "photo_uri"
        const val LATITUDE = "latitude"
        const val LONGITUDE = "longitude"
        const val FAVORI = "favori"             // ajoutée en version 2
    }

    /** Schéma complet (version 2) : utilisé par onCreate sur une installation neuve. */
    val SQL_CREATE_TABLE = """
        CREATE TABLE $TABLE (
            ${Colonnes.ID} INTEGER PRIMARY KEY AUTOINCREMENT,
            ${Colonnes.NOM} TEXT NOT NULL,
            ${Colonnes.PRENOM} TEXT NOT NULL,
            ${Colonnes.TELEPHONE} TEXT NOT NULL,
            ${Colonnes.EMAIL} TEXT,
            ${Colonnes.PHOTO_URI} TEXT,
            ${Colonnes.LATITUDE} REAL,
            ${Colonnes.LONGITUDE} REAL,
            ${Colonnes.FAVORI} INTEGER NOT NULL DEFAULT 0
        )
    """.trimIndent()

    /** Migration 1 → 2 : les lignes existantes reçoivent favori = 0, aucun contact n'est perdu. */
    val SQL_MIGRATION_V2 =
        "ALTER TABLE $TABLE ADD COLUMN ${Colonnes.FAVORI} INTEGER NOT NULL DEFAULT 0"

    private const val TRI_NOM_PRENOM =
        "${Colonnes.NOM} COLLATE NOCASE ASC, ${Colonnes.PRENOM} COLLATE NOCASE ASC"
    private const val TRI_PRENOM_NOM =
        "${Colonnes.PRENOM} COLLATE NOCASE ASC, ${Colonnes.NOM} COLLATE NOCASE ASC"

    /**
     * Ordres de tri possibles (préférence « ordre_tri »).
     * ORDER BY ne peut pas recevoir d'argument ? : on choisit donc parmi des clauses fixes,
     * jamais à partir d'un texte saisi (liste blanche).
     */
    enum class OrdreTri(val valeurPreference: String, val clause: String) {
        FAVORIS_DABORD("favoris", "${Colonnes.FAVORI} DESC, $TRI_NOM_PRENOM"),
        NOM("nom", TRI_NOM_PRENOM),
        PRENOM("prenom", TRI_PRENOM_NOM);

        companion object {
            val DEFAUT = FAVORIS_DABORD
            fun depuis(valeur: String?): OrdreTri = entries.firstOrNull { it.valeurPreference == valeur } ?: DEFAUT
        }
    }
}
