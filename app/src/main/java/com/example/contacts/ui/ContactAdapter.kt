package com.example.contacts.ui

import android.util.TypedValue
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.contacts.R
import com.example.contacts.data.Contact
import com.example.contacts.databinding.ItemContactBinding
import com.example.contacts.util.ParametresAffichage
import com.example.contacts.util.PhotoStorage

/**
 * Adapter de la liste. Il reçoit des préférences déjà converties (sp, couleur Int, booléen)
 * et identifie chaque ligne par l'_id du contact (stable ids), jamais par sa position.
 */
class ContactAdapter(
    private val onContactClick: (Contact) -> Unit,
    private val onAppelerClick: (Contact) -> Unit
) : ListAdapter<Contact, ContactAdapter.ContactViewHolder>(DIFF) {

    var parametres: ParametresAffichage = ParametresAffichage.DEFAUT
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()   // toutes les lignes changent d'apparence
            }
        }

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = getItem(position).id

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder =
        ContactViewHolder(ItemContactBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        holder.lier(getItem(position), parametres)
    }

    inner class ContactViewHolder(private val b: ItemContactBinding) : RecyclerView.ViewHolder(b.root) {

        init {
            b.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) onContactClick(getItem(position))
            }
            // Le bouton consomme le clic : la fiche détaillée ne s'ouvre pas.
            b.btnAppeler.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) onAppelerClick(getItem(position))
            }
        }

        fun lier(contact: Contact, p: ParametresAffichage) {
            b.textNom.text = contact.nom
            b.textPrenom.text = contact.prenom
            b.textTelephone.text = contact.telephone

            listOf(b.textNom, b.textPrenom, b.textTelephone).forEach {
                it.setTextSize(TypedValue.COMPLEX_UNIT_SP, p.tailleSp)
            }
            b.textNom.setTextColor(p.couleurTexte)
            b.textPrenom.setTextColor(p.couleurTexte)

            // Bonus : étoile après le nom des favoris
            b.textNom.setCompoundDrawablesRelativeWithIntrinsicBounds(
                0, 0, if (contact.favori) R.drawable.ic_star else 0, 0
            )

            b.btnAppeler.isVisible = p.appelActif      // GONE si désactivé
            b.btnAppeler.contentDescription =
                b.root.context.getString(R.string.appeler_contact, contact.prenom, contact.nom)

            PhotoStorage.afficher(b.imagePhoto, contact.photoUri)
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Contact>() {
            override fun areItemsTheSame(ancien: Contact, nouveau: Contact) = ancien.id == nouveau.id
            override fun areContentsTheSame(ancien: Contact, nouveau: Contact) = ancien == nouveau
        }
    }
}
