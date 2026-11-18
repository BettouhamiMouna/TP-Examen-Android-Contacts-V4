package com.example.contacts

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.preference.PreferenceManager
import com.example.contacts.ui.ContactViewModel
import com.example.contacts.ui.ListeContactsFragment
import com.example.contacts.ui.ParametresFragment

/** Activité unique : héberge les fragments et la barre d'application (accès aux Paramètres). */
class MainActivity : AppCompatActivity() {

    /** Même instance que activityViewModels() dans les fragments. */
    private val viewModel: ContactViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Écrit les valeurs par défaut de preferences.xml au premier lancement uniquement.
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false)

        // Après une rotation, le FragmentManager restaure lui-même les fragments :
        // on n'ajoute la liste qu'au tout premier lancement (pas de doublon).
        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                setReorderingAllowed(true)
                add(R.id.fragment_container, ListeContactsFragment(), ListeContactsFragment.TAG)
            }
        }

        supportFragmentManager.addOnBackStackChangedListener {
            majBoutonRetour()
            invalidateOptionsMenu()
        }
        majBoutonRetour()

        addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_main, menu)
            }

            override fun onPrepareMenu(menu: Menu) {
                menu.findItem(R.id.action_parametres)?.isVisible = fragmentCourant() !is ParametresFragment
                // Bonus : actions d'import visibles uniquement sur la liste
                val surListe = fragmentCourant() is ListeContactsFragment
                menu.findItem(R.id.action_importer)?.isVisible = surListe
                menu.findItem(R.id.action_supprimer_importes)?.isVisible = surListe
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean = when (menuItem.itemId) {
                R.id.action_parametres -> {
                    if (fragmentCourant() !is ParametresFragment) {
                        naviguerVers(ParametresFragment(), ParametresFragment.TAG)
                    }
                    true
                }
                R.id.action_importer -> {
                    viewModel.importerContactsTest()
                    true
                }
                R.id.action_supprimer_importes -> {
                    viewModel.supprimerContactsImportes()
                    true
                }
                else -> false
            }
        })
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    fun naviguerVers(fragment: Fragment, tag: String) {
        if (supportFragmentManager.isStateSaved) return
        supportFragmentManager.commit {
            setReorderingAllowed(true)
            replace(R.id.fragment_container, fragment, tag)
            addToBackStack(tag)
        }
    }

    private fun fragmentCourant(): Fragment? =
        supportFragmentManager.findFragmentById(R.id.fragment_container)

    private fun majBoutonRetour() {
        supportActionBar?.setDisplayHomeAsUpEnabled(supportFragmentManager.backStackEntryCount > 0)
    }
}

fun Fragment.naviguerVers(fragment: Fragment, tag: String) {
    (activity as? MainActivity)?.naviguerVers(fragment, tag)
}
