package com.example.contacts.ui

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.contacts.R
import com.example.contacts.databinding.FragmentListeContactsBinding
import com.example.contacts.naviguerVers
import com.example.contacts.util.PreferencesManager
import com.example.contacts.util.ouvrirComposeur
import com.example.contacts.util.toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class ListeContactsFragment : Fragment(R.layout.fragment_liste_contacts) {

    private var _binding: FragmentListeContactsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ContactViewModel by activityViewModels()
    private lateinit var preferences: PreferencesManager
    private lateinit var adapter: ContactAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentListeContactsBinding.bind(view)
        preferences = PreferencesManager(requireContext())

        adapter = ContactAdapter(
            onContactClick = { contact -> ouvrirDetail(contact.id) },
            onAppelerClick = { contact -> ouvrirComposeur(contact.telephone) }
        )
        binding.recyclerContacts.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerContacts.addItemDecoration(
            DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL)
        )
        binding.recyclerContacts.adapter = adapter

        // Filtrage au fil de la saisie (requête SQL LIKE paramétrée dans le DAO).
        binding.editFiltre.doAfterTextChanged { texte ->
            val nouveau = texte?.toString().orEmpty()
            if (nouveau != viewModel.filtre) viewModel.rechercher(nouveau)
        }

        binding.fabAjouter.setOnClickListener { ouvrirDetail(0L) }

        viewModel.contacts.observe(viewLifecycleOwner) { liste ->
            adapter.submitList(liste)
            binding.textVide.isVisible = liste.isEmpty()
            binding.textVide.setText(
                if (viewModel.filtre.isBlank()) R.string.liste_vide else R.string.aucun_resultat
            )
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.evenements.collect { evenement ->
                    when (evenement) {
                        is EvenementContact.Erreur -> toast(evenement.message)
                        is EvenementContact.Information -> afficherInformation(evenement)
                        else -> Unit   // Enregistre / Supprime sont traités par le détail
                    }
                }
            }
        }
    }

    /** À chaque retour sur la liste : relecture des préférences et rechargement depuis la base. */
    override fun onResume() {
        super.onResume()
        requireActivity().setTitle(R.string.titre_liste)
        requireActivity().invalidateOptionsMenu()   // affiche les actions propres à la liste
        adapter.parametres = preferences.lire()
        viewModel.charger()
    }

    private fun afficherInformation(info: EvenementContact.Information) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(info.titre)
            .setMessage(info.message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun ouvrirDetail(id: Long) {
        naviguerVers(DetailContactFragment.newInstance(id), DetailContactFragment.TAG)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "liste"
    }
}
