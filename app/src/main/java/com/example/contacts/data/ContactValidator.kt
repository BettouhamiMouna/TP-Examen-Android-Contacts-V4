package com.example.contacts.data

import com.example.contacts.R

/**
 * Validation du formulaire, en Kotlin pur (testable par un test unitaire JVM).
 * Rien n'est écrit en base tant que le résultat n'est pas Valide.
 */
object ContactValidator {

    enum class Champ { NOM, PRENOM, TELEPHONE, EMAIL, LATITUDE, LONGITUDE }

    data class Saisie(
        val nom: String,
        val prenom: String,
        val telephone: String,
        val email: String,
        val latitude: String,
        val longitude: String,
        val favori: Boolean = false
    )

    sealed class Resultat {
        data class Valide(val contact: Contact) : Resultat()
        data class Invalide(val erreurs: Map<Champ, Int>) : Resultat()
    }

    private val REGEX_TELEPHONE = Regex("^\\+?[0-9][0-9 .\\-]{5,19}$")
    private val REGEX_EMAIL = Regex("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$")

    fun valider(id: Long, photoUri: String?, saisie: Saisie): Resultat {
        // Clé = champ en erreur, valeur = id du message (R.string.xxx)
        val erreurs = linkedMapOf<Champ, Int>()

        val nom = saisie.nom.trim()
        val prenom = saisie.prenom.trim()
        val telephone = saisie.telephone.trim()
        val email = saisie.email.trim()

        if (nom.isEmpty()) erreurs[Champ.NOM] = R.string.err_nom_vide
        if (prenom.isEmpty()) erreurs[Champ.PRENOM] = R.string.err_prenom_vide
        when {
            telephone.isEmpty() -> erreurs[Champ.TELEPHONE] = R.string.err_tel_vide
            !REGEX_TELEPHONE.matches(telephone) -> erreurs[Champ.TELEPHONE] = R.string.err_tel_format
        }
        if (email.isNotEmpty() && !REGEX_EMAIL.matches(email)) erreurs[Champ.EMAIL] = R.string.err_email_format

        // Coordonnées : toutes les deux vides (=> NULL) ou toutes les deux valides.
        val latTexte = saisie.latitude.trim().replace(',', '.')
        val lngTexte = saisie.longitude.trim().replace(',', '.')
        var latitude: Double? = null
        var longitude: Double? = null

        if (latTexte.isNotEmpty() || lngTexte.isNotEmpty()) {
            if (latTexte.isEmpty()) erreurs[Champ.LATITUDE] = R.string.err_gps_incomplet
            if (lngTexte.isEmpty()) erreurs[Champ.LONGITUDE] = R.string.err_gps_incomplet

            if (latTexte.isNotEmpty()) {
                val lat = latTexte.toDoubleOrNull()
                if (lat == null || lat.isNaN()) {
                    erreurs[Champ.LATITUDE] = R.string.err_gps_nombre
                } else if (lat !in -90.0..90.0) {
                    erreurs[Champ.LATITUDE] = R.string.err_latitude_limites
                } else {
                    latitude = lat
                }
            }
            if (lngTexte.isNotEmpty()) {
                val lng = lngTexte.toDoubleOrNull()
                if (lng == null || lng.isNaN()) {
                    erreurs[Champ.LONGITUDE] = R.string.err_gps_nombre
                } else if (lng !in -180.0..180.0) {
                    erreurs[Champ.LONGITUDE] = R.string.err_longitude_limites
                } else {
                    longitude = lng
                }
            }
        }

        if (erreurs.isNotEmpty()) return Resultat.Invalide(erreurs)

        return Resultat.Valide(
            Contact(
                id = id,
                nom = nom,
                prenom = prenom,
                telephone = telephone,
                email = email.ifEmpty { null },
                photoUri = photoUri,
                latitude = latitude,
                longitude = longitude,
                favori = saisie.favori
            )
        )
    }
}