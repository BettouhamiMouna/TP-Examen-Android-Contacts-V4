package com.example.contacts.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Crée et fait évoluer la base contacts.db.
 * Singleton construit avec applicationContext : une seule connexion partagée,
 * et aucune référence vers une Activity (pas de fuite mémoire).
 */
class ContactDbHelper private constructor(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    /** Appelée une seule fois, quand le fichier contacts.db n'existe pas encore. */
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(ContactContract.SQL_CREATE_TABLE)
        insererContactsDemo(db)
    }

    /**
     * Appelée quand DATABASE_VERSION est supérieure à la version du fichier existant.
     * Les migrations sont incrémentales (if, pas when/else) : un appareil resté en version 1
     * passe par toutes les étapes, sans jamais perdre les contacts.
     */
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // Version 2 (bonus) : colonne favori, 0 pour tous les contacts existants.
            db.execSQL(ContactContract.SQL_MIGRATION_V2)
        }
        // if (oldVersion < 3) { ... }  ← prochaine migration
    }

    /** onCreate s'exécute déjà dans une transaction ouverte par SQLiteOpenHelper. */
    private fun insererContactsDemo(db: SQLiteDatabase) {
        val demo = listOf(
            Contact(nom = "Ben Salah", prenom = "Ahmed", telephone = "+216 71 234 567",
                email = "ahmed.bensalah@example.tn", latitude = 36.8065, longitude = 10.1815,
                favori = true),                                                                  // Tunis
            Contact(nom = "Trabelsi", prenom = "Sarra", telephone = "98 765 432",
                email = "sarra.trabelsi@example.tn", latitude = 34.7406, longitude = 10.7603),   // Sfax
            Contact(nom = "Gharbi", prenom = "Youssef", telephone = "+216 22 333 444",
                latitude = 35.8256, longitude = 10.6084),                                        // Sousse
            Contact(nom = "Jaziri", prenom = "Karim", telephone = "29 456 789",
                email = "karim.jaziri@example.tn", latitude = 36.8625, longitude = 10.1956),     // Ariana
            Contact(nom = "Mansour", prenom = "Leila", telephone = "50 000 111",
                email = "leila.mansour@example.tn", favori = true),                              // sans GPS
            Contact(nom = "O’Neil", prenom = "Patrick", telephone = "+216 55 111 222",
                email = "patrick.oneil@example.com")                                             // sans GPS
        )
        demo.forEach { db.insert(ContactContract.TABLE, null, it.toContentValues()) }
    }

    companion object {
        const val DATABASE_NAME = "contacts.db"
        /** 1 : schéma de l'énoncé ; 2 : ajout de la colonne favori (bonus). */
        const val DATABASE_VERSION = 2

        @Volatile
        private var instance: ContactDbHelper? = null

        fun getInstance(context: Context): ContactDbHelper =
            instance ?: synchronized(this) {
                instance ?: ContactDbHelper(context.applicationContext).also { instance = it }
            }
    }
}
