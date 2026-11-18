package com.example.contacts.ui

import android.app.Application
import android.database.sqlite.SQLiteException
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.contacts.R
import com.example.contacts.data.Contact
import com.example.contacts.data.ContactDao
import com.example.contacts.data.ContactDbHelper
import com.example.contacts.util.PhotoStorage
import com.example.contacts.util.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** Événements à consommer une seule fois (Toast + navigation), même après une rotation. */
sealed class EvenementContact {
    data class Enregistre(val message: String) : EvenementContact()
    data class Supprime(val message: String) : EvenementContact()
    data class Erreur(val message: String) : EvenementContact()
    /** Bonus : résultat de l'import de test (durées avec / sans transaction). */
    data class Information(val titre: String, val message: String) : EvenementContact()
}

/**
 * ViewModel partagé par les fragments (activityViewModels).
 * Tous les appels au DAO sont lancés dans viewModelScope puis exécutés sur Dispatchers.IO.
 */
class ContactViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = ContactDao(ContactDbHelper.getInstance(application))
    private val preferences = PreferencesManager(application)

    private val _contacts = MutableLiveData<List<Contact>>()
    val contacts: LiveData<List<Contact>> = _contacts

    private val _evenements = Channel<EvenementContact>(Channel.BUFFERED)
    val evenements: Flow<EvenementContact> = _evenements.receiveAsFlow()

    /** Texte du filtre, conservé lors des rotations. */
    var filtre: String = ""
        private set

    private var jobChargement: Job? = null

    fun rechercher(texte: String) {
        filtre = texte
        charger()
    }

    /** Recharge la liste depuis la base (avec le filtre courant). */
    fun charger() {
        val texte = filtre.trim()
        jobChargement?.cancel()          // une saisie rapide annule la recherche précédente
        jobChargement = viewModelScope.launch {
            try {
                val liste = withContext(Dispatchers.IO) {
                    // Ordre de tri relu à chaque chargement : un changement dans Paramètres
                    // est appliqué dès le retour sur la liste (onResume → charger()).
                    val ordre = preferences.ordreTri()
                    if (texte.isEmpty()) dao.getAll(ordre) else dao.search(texte, ordre)
                }
                _contacts.value = liste
            } catch (e: SQLiteException) {
                _evenements.send(EvenementContact.Erreur(texte(R.string.err_lecture)))
            }
        }
    }

    /** Lecture d'une fiche, appelée depuis lifecycleScope des fragments. */
    suspend fun trouverContact(id: Long): Contact? = try {
        withContext(Dispatchers.IO) { dao.getById(id) }
    } catch (e: SQLiteException) {
        null
    }

    fun enregistrer(contact: Contact) {
        viewModelScope.launch {
            try {
                val evenement = withContext(Dispatchers.IO) {
                    if (contact.id == 0L) {
                        val nouvelId = dao.insert(contact)
                        if (nouvelId == -1L) EvenementContact.Erreur(texte(R.string.err_insertion))
                        else EvenementContact.Enregistre(texte(R.string.contact_ajoute))
                    } else {
                        val ancien = dao.getById(contact.id)
                        if (ancien == null) {
                            EvenementContact.Erreur(texte(R.string.err_contact_introuvable))
                        } else if (dao.update(contact) == 0) {
                            EvenementContact.Erreur(texte(R.string.err_modification))
                        } else {
                            // L'ancienne copie de la photo n'est plus référencée : on la supprime.
                            if (ancien.photoUri != contact.photoUri) {
                                PhotoStorage.supprimer(getApplication(), ancien.photoUri)
                            }
                            EvenementContact.Enregistre(texte(R.string.contact_modifie))
                        }
                    }
                }
                if (evenement is EvenementContact.Enregistre) charger()
                _evenements.send(evenement)
            } catch (e: SQLiteException) {
                _evenements.send(EvenementContact.Erreur(texte(R.string.err_base, e.localizedMessage ?: "")))
            }
        }
    }

    fun supprimer(id: Long) {
        viewModelScope.launch {
            try {
                val evenement = withContext(Dispatchers.IO) {
                    val contact = dao.getById(id)
                    if (dao.delete(id) > 0) {
                        PhotoStorage.supprimer(getApplication(), contact?.photoUri)
                        EvenementContact.Supprime(texte(R.string.contact_supprime))
                    } else {
                        EvenementContact.Erreur(texte(R.string.err_contact_introuvable))
                    }
                }
                if (evenement is EvenementContact.Supprime) charger()
                _evenements.send(evenement)
            } catch (e: SQLiteException) {
                _evenements.send(EvenementContact.Erreur(texte(R.string.err_base, e.localizedMessage ?: "")))
            }
        }
    }

    // ---------- Bonus : import de 100 contacts, mesure avec / sans transaction ----------

    /**
     * 1) insère [nombre] contacts SANS transaction et mesure la durée, puis les supprime ;
     * 2) insère [nombre] contacts AVEC une transaction unique et mesure la durée (ils sont conservés).
     */
    fun importerContactsTest(nombre: Int = NOMBRE_IMPORT) {
        viewModelScope.launch {
            try {
                val (sansMs, avecMs, inseres) = withContext(Dispatchers.IO) {
                    val debutSans = System.nanoTime()
                    val idsSans = dao.insertAll(genererContacts(nombre), enTransaction = false)
                    val sans = (System.nanoTime() - debutSans) / 1_000_000
                    dao.deleteAll(idsSans.filter { it != -1L })

                    val debutAvec = System.nanoTime()
                    val idsAvec = dao.insertAll(genererContacts(nombre), enTransaction = true)
                    val avec = (System.nanoTime() - debutAvec) / 1_000_000
                    Triple(sans, avec, idsAvec.count { it != -1L })
                }
                charger()
                _evenements.send(
                    EvenementContact.Information(
                        texte(R.string.import_titre),
                        texte(R.string.import_resultat, inseres, sansMs, avecMs)
                    )
                )
            } catch (e: SQLiteException) {
                _evenements.send(EvenementContact.Erreur(texte(R.string.err_base, e.localizedMessage ?: "")))
            }
        }
    }

    /** Supprime les contacts créés par l'import de test, pour revenir à une liste propre. */
    fun supprimerContactsImportes() {
        viewModelScope.launch {
            try {
                val n = withContext(Dispatchers.IO) { dao.deleteImportes(PRENOM_IMPORT, PREFIXE_IMPORT) }
                charger()
                _evenements.send(
                    EvenementContact.Information(texte(R.string.import_titre), texte(R.string.import_supprimes, n))
                )
            } catch (e: SQLiteException) {
                _evenements.send(EvenementContact.Erreur(texte(R.string.err_base, e.localizedMessage ?: "")))
            }
        }
    }

    private fun genererContacts(nombre: Int): List<Contact> = (1..nombre).map { i ->
        Contact(
            nom = String.format(Locale.ROOT, "%s%03d", PREFIXE_IMPORT, i),
            prenom = PRENOM_IMPORT,
            telephone = String.format(Locale.ROOT, "+216 20 000 %03d", i)
        )
    }

    private fun texte(@StringRes id: Int, vararg args: Any): String =
        getApplication<Application>().getString(id, *args)

    companion object {
        const val NOMBRE_IMPORT = 100
        private const val PRENOM_IMPORT = "Test"
        private const val PREFIXE_IMPORT = "Import "
    }
}
