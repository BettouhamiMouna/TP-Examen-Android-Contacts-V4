package com.example.contacts.data

import com.example.contacts.R
import com.example.contacts.data.ContactValidator.Champ
import com.example.contacts.data.ContactValidator.Resultat
import com.example.contacts.data.ContactValidator.Saisie
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactValidatorTest {

    private fun saisie(
        nom: String = "O’Neil", prenom: String = "Patrick", tel: String = "+216 55 111 222",
        email: String = "", lat: String = "", lng: String = ""
    ) = Saisie(nom, prenom, tel, email, lat, lng)

    @Test
    fun contactValide_avecApostrophe_sansGps() {
        val r = ContactValidator.valider(0, null, saisie(nom = "L'Ariana"))
        assertTrue(r is Resultat.Valide)
        val c = (r as Resultat.Valide).contact
        assertEquals("L'Ariana", c.nom)
        assertNull(c.latitude); assertNull(c.longitude); assertNull(c.email)
    }

    @Test
    fun champsObligatoiresVides() {
        val r = ContactValidator.valider(0, null, saisie(nom = " ", prenom = "", tel = ""))
        val erreurs = (r as Resultat.Invalide).erreurs
        assertEquals(R.string.err_nom_vide, erreurs[Champ.NOM])
        assertEquals(R.string.err_prenom_vide, erreurs[Champ.PRENOM])
        assertEquals(R.string.err_tel_vide, erreurs[Champ.TELEPHONE])
    }

    @Test
    fun gpsIncomplet() {
        val r = ContactValidator.valider(0, null, saisie(lat = "36.8"))
        assertEquals(R.string.err_gps_incomplet, (r as Resultat.Invalide).erreurs[Champ.LONGITUDE])
    }

    @Test
    fun gpsNonNumerique() {
        val r = ContactValidator.valider(0, null, saisie(lat = "abc", lng = "10"))
        assertEquals(R.string.err_gps_nombre, (r as Resultat.Invalide).erreurs[Champ.LATITUDE])
    }

    @Test
    fun gpsHorsLimites() {
        val r = ContactValidator.valider(0, null, saisie(lat = "95", lng = "200"))
        val erreurs = (r as Resultat.Invalide).erreurs
        assertEquals(R.string.err_latitude_limites, erreurs[Champ.LATITUDE])
        assertEquals(R.string.err_longitude_limites, erreurs[Champ.LONGITUDE])
    }

    @Test
    fun gpsAvecVirgule() {
        val r = ContactValidator.valider(0, null, saisie(lat = "36,8065", lng = "10,1815"))
        assertEquals(36.8065, (r as Resultat.Valide).contact.latitude!!, 1e-9)
    }

    @Test
    fun emailInvalide() {
        val r = ContactValidator.valider(0, null, saisie(email = "pas-un-email"))
        assertEquals(R.string.err_email_format, (r as Resultat.Invalide).erreurs[Champ.EMAIL])
    }

    @Test
    fun favoriTransmisAuContact() {
        val s = Saisie("Gharbi", "Youssef", "22 333 444", "", "", "", favori = true)
        assertTrue((ContactValidator.valider(0, null, s) as Resultat.Valide).contact.favori)
    }
}
