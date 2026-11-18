package com.example.contacts.ui

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.example.contacts.R

/**
 * Écran des préférences. Les valeurs sont enregistrées automatiquement dans les
 * SharedPreferences par défaut ; le résumé affiche la valeur choisie (useSimpleSummaryProvider).
 */
class ParametresFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
    }

    override fun onResume() {
        super.onResume()
        requireActivity().setTitle(R.string.parametres)
    }

    companion object {
        const val TAG = "parametres"
    }
}
