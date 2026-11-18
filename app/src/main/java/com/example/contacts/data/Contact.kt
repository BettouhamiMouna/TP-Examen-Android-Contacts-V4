package com.example.contacts.data

/**
 * Modèle d'un contact.
 * id vaut 0 tant que le contact n'est pas enregistré, puis prend la valeur _id attribuée par SQLite.
 * favori : ajouté en version 2 de la base (bonus), stocké en INTEGER 0/1.
 */
data class Contact(
    val id: Long = 0L,
    val nom: String,
    val prenom: String,
    val telephone: String,
    val email: String? = null,
    val photoUri: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val favori: Boolean = false
) {
    val aUnePosition: Boolean get() = latitude != null && longitude != null
}
