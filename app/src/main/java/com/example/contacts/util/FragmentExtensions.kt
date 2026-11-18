package com.example.contacts.util

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import com.example.contacts.R

fun Fragment.toast(@StringRes message: Int) {
    context?.let { Toast.makeText(it, message, Toast.LENGTH_SHORT).show() }
}

fun Fragment.toast(message: String) {
    context?.let { Toast.makeText(it, message, Toast.LENGTH_SHORT).show() }
}

/** ACTION_DIAL ouvre le composeur prérempli : aucune permission n'est nécessaire. */
fun Fragment.ouvrirComposeur(telephone: String) {
    val numero = telephone.trim()
    if (numero.isEmpty()) {
        toast(R.string.err_numero_vide); return
    }
    val intent = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", numero, null))
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        toast(R.string.err_pas_app_telephone)
    }
}

fun Fragment.envoyerEmail(email: String) {
    val adresse = email.trim()
    if (adresse.isEmpty()) {
        toast(R.string.err_email_vide); return
    }
    val intent = Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", adresse, null))
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        toast(R.string.err_pas_app_email)
    }
}
