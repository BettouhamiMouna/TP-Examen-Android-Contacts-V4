package com.example.contacts.data

import com.example.contacts.data.ContactContract.OrdreTri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Bonus : la valeur de la préférence est convertie en une clause ORDER BY d'une liste blanche. */
class OrdreTriTest {

    @Test
    fun valeursConnues() {
        assertEquals(OrdreTri.FAVORIS_DABORD, OrdreTri.depuis("favoris"))
        assertEquals(OrdreTri.NOM, OrdreTri.depuis("nom"))
        assertEquals(OrdreTri.PRENOM, OrdreTri.depuis("prenom"))
    }

    @Test
    fun valeurInconnueOuAbsente_donneLeDefaut() {
        assertEquals(OrdreTri.DEFAUT, OrdreTri.depuis(null))
        assertEquals(OrdreTri.DEFAUT, OrdreTri.depuis("nom; DROP TABLE contacts"))
    }

    @Test
    fun favorisEnTete_puisNomPrenomSansCasse() {
        val clause = OrdreTri.FAVORIS_DABORD.clause
        assertTrue(clause.startsWith("favori DESC"))
        assertTrue(clause.contains("nom COLLATE NOCASE"))
    }
}
