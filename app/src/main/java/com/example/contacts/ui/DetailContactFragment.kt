package com.example.contacts.ui

import android.content.ActivityNotFoundException
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.contacts.R
import com.example.contacts.data.ContactValidator
import com.example.contacts.data.ContactValidator.Champ
import com.example.contacts.databinding.FragmentDetailContactBinding
import com.example.contacts.naviguerVers
import com.example.contacts.util.PhotoStorage
import com.example.contacts.util.PreferencesManager
import com.example.contacts.util.envoyerEmail
import com.example.contacts.util.ouvrirComposeur
import com.example.contacts.util.toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import java.io.File

/**
 * Création (id = 0), consultation, modification et suppression d'un contact.
 * Le contact est identifié par son _id reçu dans le Bundle.
 */
class DetailContactFragment : Fragment(R.layout.fragment_detail_contact) {

    private var _binding: FragmentDetailContactBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ContactViewModel by activityViewModels()

    private val contactId: Long get() = requireArguments().getLong(ARG_ID, 0L)
    private val modeCreation: Boolean get() = contactId == 0L

    // État qui n'est pas porté par les EditText (ceux-ci restaurent seuls leur texte après rotation).
    private var photoCourante: String? = null        // photo affichée dans le formulaire
    private var photoOriginale: String? = null       // photo actuellement enregistrée en base
    private var photoCameraEnAttente: String? = null // fichier cible de la caméra
    private var donneesChargees = false
    private var photoEnregistree = false

    private val choisirImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) {
            toast(R.string.action_annulee)
        } else {
            val appContext = requireContext().applicationContext
            lifecycleScope.launch {
                val chemin = PhotoStorage.copierDepuisUri(appContext, uri)
                if (chemin == null) toast(R.string.err_photo) else remplacerPhoto(chemin)
            }
        }
    }

    private val prendrePhoto = registerForActivityResult(ActivityResultContracts.TakePicture()) { succes ->
        val chemin = photoCameraEnAttente
        photoCameraEnAttente = null
        if (succes && chemin != null && File(chemin).length() > 0) {
            remplacerPhoto(chemin)
        } else {
            context?.let { PhotoStorage.supprimer(it, chemin) }
            toast(R.string.action_annulee)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.let {
            photoCourante = it.getString(KEY_PHOTO_COURANTE)
            photoOriginale = it.getString(KEY_PHOTO_ORIGINALE)
            photoCameraEnAttente = it.getString(KEY_PHOTO_CAMERA)
            donneesChargees = it.getBoolean(KEY_DONNEES_CHARGEES)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentDetailContactBinding.bind(view)

        binding.btnSupprimer.isVisible = !modeCreation
        configurerBoutons()

        if (modeCreation) donneesChargees = true
        // Après une rotation ou un retour de la carte, on garde la saisie en cours.
        if (donneesChargees) afficherPhoto() else chargerContact()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.evenements.collect { gererEvenement(it) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        requireActivity().setTitle(if (modeCreation) R.string.titre_creation else R.string.titre_modification)
        appliquerPreferences()
    }

    private fun configurerBoutons() = with(binding) {
        btnGalerie.setOnClickListener { ouvrirGalerie() }
        btnCamera.setOnClickListener { ouvrirCamera() }
        btnAppeler.setOnClickListener { ouvrirComposeur(editTelephone.text?.toString().orEmpty()) }
        btnEmail.setOnClickListener { envoyerEmail(editEmail.text?.toString().orEmpty()) }
        btnCarte.setOnClickListener {
            if (modeCreation) toast(R.string.err_enregistrer_avant_carte)
            else naviguerVers(CarteContactFragment.newInstance(contactId), CarteContactFragment.TAG)
        }
        btnEnregistrer.setOnClickListener { enregistrer() }
        btnAnnuler.setOnClickListener { parentFragmentManager.popBackStack() }
        btnSupprimer.setOnClickListener { confirmerSuppression() }
    }

    /** Lecture de la fiche depuis la base, hors du thread principal (via le ViewModel). */
    private fun chargerContact() {
        viewLifecycleOwner.lifecycleScope.launch {
            val contact = viewModel.trouverContact(contactId)
            if (contact == null) {
                toast(R.string.err_contact_introuvable)
                parentFragmentManager.popBackStack()
                return@launch
            }
            with(binding) {
                editNom.setText(contact.nom)
                editPrenom.setText(contact.prenom)
                editTelephone.setText(contact.telephone)
                editEmail.setText(contact.email.orEmpty())
                editLatitude.setText(contact.latitude?.toString().orEmpty())
                editLongitude.setText(contact.longitude?.toString().orEmpty())
                checkFavori.isChecked = contact.favori
            }
            photoOriginale = contact.photoUri
            photoCourante = contact.photoUri
            donneesChargees = true
            afficherPhoto()
        }
    }

    private fun enregistrer() {
        effacerErreurs()
        val saisie = with(binding) {
            ContactValidator.Saisie(
                nom = editNom.text?.toString().orEmpty(),
                prenom = editPrenom.text?.toString().orEmpty(),
                telephone = editTelephone.text?.toString().orEmpty(),
                email = editEmail.text?.toString().orEmpty(),
                latitude = editLatitude.text?.toString().orEmpty(),
                longitude = editLongitude.text?.toString().orEmpty(),
                favori = checkFavori.isChecked
            )
        }
        when (val resultat = ContactValidator.valider(contactId, photoCourante, saisie)) {
            is ContactValidator.Resultat.Invalide -> {
                resultat.erreurs.forEach { (champ, message) -> champVers(champ).error = getString(message) }
                toast(R.string.err_formulaire)
            }
            is ContactValidator.Resultat.Valide -> {
                binding.btnEnregistrer.isEnabled = false   // évite un double enregistrement
                viewModel.enregistrer(resultat.contact)
            }
        }
    }

    private fun confirmerSuppression() {
        val prenom = binding.editPrenom.text?.toString().orEmpty()
        val nom = binding.editNom.text?.toString().orEmpty()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.supprimer_titre)
            .setMessage(getString(R.string.supprimer_message, prenom, nom))
            .setNegativeButton(R.string.annuler, null)
            .setPositiveButton(R.string.supprimer) { _, _ -> viewModel.supprimer(contactId) }
            .show()
    }

    private fun gererEvenement(evenement: EvenementContact) {
        when (evenement) {
            is EvenementContact.Enregistre -> {
                photoEnregistree = true
                toast(evenement.message)
                parentFragmentManager.popBackStack()     // retour à la liste actualisée
            }
            is EvenementContact.Supprime -> {
                toast(evenement.message)
                parentFragmentManager.popBackStack()
            }
            is EvenementContact.Erreur -> {
                _binding?.btnEnregistrer?.isEnabled = true
                toast(evenement.message)
            }
            is EvenementContact.Information -> toast(evenement.message)
        }
    }

    private fun ouvrirGalerie() {
        try {
            choisirImage.launch("image/*")
        } catch (e: ActivityNotFoundException) {
            toast(R.string.err_pas_app_galerie)
        }
    }

    private fun ouvrirCamera() {
        val contexte = requireContext()
        val fichier = PhotoStorage.nouveauFichier(contexte)
        val uri = FileProvider.getUriForFile(contexte, "${contexte.packageName}.fileprovider", fichier)
        photoCameraEnAttente = fichier.absolutePath
        try {
            prendrePhoto.launch(uri)
        } catch (e: ActivityNotFoundException) {
            photoCameraEnAttente = null
            fichier.delete()
            toast(R.string.err_pas_app_camera)
        }
    }

    /** Une nouvelle photo remplace la précédente ; une copie temporaire non enregistrée est supprimée. */
    private fun remplacerPhoto(nouveauChemin: String) {
        val precedente = photoCourante
        if (precedente != null && precedente != photoOriginale) {
            context?.let { PhotoStorage.supprimer(it, precedente) }
        }
        photoCourante = nouveauChemin
        afficherPhoto()
    }

    private fun afficherPhoto() {
        _binding?.let { PhotoStorage.afficher(it.imagePhoto, photoCourante, tailleMaxPx = 512) }
    }

    private fun appliquerPreferences() {
        val p = PreferencesManager(requireContext()).lire()
        with(binding) {
            listOf(editNom, editPrenom, editTelephone).forEach {
                it.setTextSize(TypedValue.COMPLEX_UNIT_SP, p.tailleSp)
            }
            editNom.setTextColor(p.couleurTexte)
            editPrenom.setTextColor(p.couleurTexte)
        }
    }

    private fun champVers(champ: Champ): TextInputLayout = when (champ) {
        Champ.NOM -> binding.tilNom
        Champ.PRENOM -> binding.tilPrenom
        Champ.TELEPHONE -> binding.tilTelephone
        Champ.EMAIL -> binding.tilEmail
        Champ.LATITUDE -> binding.tilLatitude
        Champ.LONGITUDE -> binding.tilLongitude
    }

    private fun effacerErreurs() = Champ.values().forEach { champVers(it).error = null }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_PHOTO_COURANTE, photoCourante)
        outState.putString(KEY_PHOTO_ORIGINALE, photoOriginale)
        outState.putString(KEY_PHOTO_CAMERA, photoCameraEnAttente)
        outState.putBoolean(KEY_DONNEES_CHARGEES, donneesChargees)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /** Annulation (fragment retiré sans enregistrement) : on nettoie la photo copiée inutilement. */
    override fun onDestroy() {
        super.onDestroy()
        if (isRemoving && !photoEnregistree) {
            val temporaire = photoCourante
            if (temporaire != null && temporaire != photoOriginale) {
                context?.let { PhotoStorage.supprimer(it, temporaire) }
            }
        }
    }

    companion object {
        const val TAG = "detail"
        private const val ARG_ID = "contact_id"
        private const val KEY_PHOTO_COURANTE = "photo_courante"
        private const val KEY_PHOTO_ORIGINALE = "photo_originale"
        private const val KEY_PHOTO_CAMERA = "photo_camera"
        private const val KEY_DONNEES_CHARGEES = "donnees_chargees"

        fun newInstance(contactId: Long) = DetailContactFragment().apply {
            arguments = bundleOf(ARG_ID to contactId)
        }
    }
}
