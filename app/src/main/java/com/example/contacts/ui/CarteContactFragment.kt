package com.example.contacts.ui

import android.os.Bundle
import android.view.View
import androidx.annotation.StringRes
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.example.contacts.R
import com.example.contacts.databinding.FragmentCarteContactBinding
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.coroutines.launch

/** Affiche la position du contact (lue en base à partir de son _id) avec un marqueur. */
class CarteContactFragment : Fragment(R.layout.fragment_carte_contact) {

    private var _binding: FragmentCarteContactBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ContactViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentCarteContactBinding.bind(view)
        val id = requireArguments().getLong(ARG_ID, 0L)

        viewLifecycleOwner.lifecycleScope.launch {
            val contact = viewModel.trouverContact(id)
            if (contact == null) {
                afficherMessage(R.string.err_contact_introuvable)
                return@launch
            }
            binding.textTitreCarte.text = "${contact.prenom} ${contact.nom}"

            val lat = contact.latitude
            val lng = contact.longitude
            when {
                lat == null || lng == null -> afficherMessage(R.string.carte_pas_de_position)
                lat !in -90.0..90.0 || lng !in -180.0..180.0 -> afficherMessage(R.string.carte_position_invalide)
                else -> {
                    val carte = childFragmentManager.findFragmentById(R.id.map_container) as? SupportMapFragment
                    carte?.getMapAsync { map ->
                        val position = LatLng(lat, lng)
                        map.addMarker(MarkerOptions().position(position).title("${contact.prenom} ${contact.nom}"))
                        map.moveCamera(CameraUpdateFactory.newLatLngZoom(position, 14f))
                        map.uiSettings.isZoomControlsEnabled = true
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        requireActivity().setTitle(R.string.titre_carte)
    }

    private fun afficherMessage(@StringRes message: Int) {
        _binding?.let {
            it.mapContainer.isVisible = false
            it.textMessageCarte.isVisible = true
            it.textMessageCarte.setText(message)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "carte"
        private const val ARG_ID = "contact_id"

        fun newInstance(contactId: Long) = CarteContactFragment().apply {
            arguments = bundleOf(ARG_ID to contactId)
        }
    }
}
