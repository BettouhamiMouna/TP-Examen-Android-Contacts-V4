<div align="center">

# 📇 Contacts V4

**Application Android de gestion de contacts**
Persistance SQLite • Préférences utilisateur • Fragments • Google Maps

![Kotlin](https://img.shields.io/badge/Kotlin-1.9-7F52FF?logo=kotlin&logoColor=white)
![Android](https://img.shields.io/badge/Android-minSdk%2024-3DDC84?logo=android&logoColor=white)
![SQLite](https://img.shields.io/badge/SQLite-SQLiteOpenHelper-003B57?logo=sqlite&logoColor=white)
![Google Maps](https://img.shields.io/badge/Google%20Maps-SDK-4285F4?logo=googlemaps&logoColor=white)


*TP Android — Développement mobile • Mouna Bettouhami • L3 DSI*

</div>

---

## 📑 Sommaire

1. [Présentation](#-présentation)
2. [Fonctionnalités](#-fonctionnalités)
3. [Installation](#-installation)
4. [Architecture](#-architecture)
5. [Base de données](#-base-de-données)
6. [Préférences utilisateur](#-préférences-utilisateur)
7. [Bonus réalisés](#-bonus-réalisés)
8. [Tests](#-tests)
9. [Compte rendu](#-compte-rendu)

---

## 🎯 Présentation

**Contacts V4** fait évoluer l’application de gestion de contacts du TP V3. Dans la V3, les contacts
étaient gardés en mémoire et perdus à la fermeture de l’application. La V4 :

- enregistre les contacts dans une **base SQLite locale** (`contacts.db`) avec l’API native
  `SQLiteOpenHelper` (sans Room) ; les données restent après la fermeture de l’application et le
  redémarrage du téléphone ;
- ajoute un écran **Paramètres** qui personnalise l’affichage, enregistré dans les `SharedPreferences`.

---

## ✨ Fonctionnalités

| | Fonctionnalité |
|---|---|
| 📋 | Liste des contacts (photo, nom, prénom, téléphone) triée sans tenir compte de la casse |
| 🔍 | Recherche au fil de la saisie sur le nom, le prénom et le téléphone |
| ➕ | Création d’un contact avec le bouton flottant « + » |
| ✏️ | Modification avec validation des champs (obligatoires, format, coordonnées GPS) |
| 🗑️ | Suppression avec boîte de confirmation |
| 📞 | Appeler (`ACTION_DIAL`), envoyer un e-mail |
| 🖼️ | Photo depuis la galerie ou la caméra, copiée dans le stockage interne |
| 🗺️ | Position du contact sur Google Maps avec un marqueur |
| ⚙️ | Taille de police, couleur du texte, bouton Appeler sur chaque ligne, ordre de tri |
| ⭐ | Contacts favoris affichés en tête de liste *(bonus)* |
| ⏱️ | Import de 100 contacts avec mesure de la durée *(bonus)* |

---

## 🚀 Installation

**Prérequis :** Android Studio (JDK 17), un téléphone ou un émulateur avec les services Google Play.

```bash
git clone https://github.com/BettouhamiMouna/TP-Examen-Android-Contacts-V4.git
```

1. Ouvrir le projet dans Android Studio et laisser Gradle synchroniser.
2. Ajouter la clé Google Maps dans `local.properties` (modèle : `local.properties.exemple`) :
   ```properties
   MAPS_API_KEY=votre_clé_google_maps
   ```
   > 🔒 `local.properties` est ignoré par Git : la clé n’est **jamais** publiée sur GitHub.
3. Lancer l’application avec ▶ **Run**.
4. Tests unitaires :
   ```bash
   ./gradlew test
   ```

Le wrapper Gradle est versionné : le projet se construit à l’identique sur n’importe quel poste.

---

## 🏗️ Architecture

Une seule activité héberge des fragments dynamiques (`replace()` + `addToBackStack()`).
Un `ViewModel` partagé fait le lien entre l’interface et la base.

```
Fragments (UI)  ──►  ContactViewModel  ──►  ContactDao  ──►  SQLite (contacts.db)
      ▲              viewModelScope           seul SQL          via ContactDbHelper
      │              + Dispatchers.IO
PreferencesManager  ◄──  SharedPreferences  ◄──  ParametresFragment
```

```
com.example.contacts
├── MainActivity.kt                activité unique, barre d’application, menus
├── data/
│   ├── Contact.kt                 modèle (id = 0 avant enregistrement)
│   ├── ContactContract.kt         table, colonnes, CREATE TABLE, migration, ordres de tri
│   ├── ContactDbHelper.kt         SQLiteOpenHelper singleton : onCreate, onUpgrade
│   ├── ContactDao.kt              seule classe contenant du SQL
│   └── ContactValidator.kt        validation du formulaire
├── ui/
│   ├── ContactViewModel.kt        ViewModel partagé
│   ├── ListeContactsFragment.kt   liste, recherche, « + », bouton Appeler
│   ├── ContactAdapter.kt          RecyclerView (ListAdapter + DiffUtil)
│   ├── DetailContactFragment.kt   création, modification, suppression, photo, actions
│   ├── CarteContactFragment.kt    Google Maps
│   └── ParametresFragment.kt      écran des préférences
└── util/
    ├── PreferencesManager.kt      lecture centralisée des préférences
    ├── PhotoStorage.kt            copie des photos dans filesDir/photos
    └── FragmentExtensions.kt      Toast, ACTION_DIAL, e-mail
```

---

## 🗄️ Base de données

**Table `contacts`** (version 2)

| Colonne | Type | Contrainte |
|---|---|---|
| `_id` | `INTEGER` | `PRIMARY KEY AUTOINCREMENT` |
| `nom` | `TEXT` | `NOT NULL` |
| `prenom` | `TEXT` | `NOT NULL` |
| `telephone` | `TEXT` | `NOT NULL` |
| `email` | `TEXT` | — |
| `photo_uri` | `TEXT` | — |
| `latitude` | `REAL` | `NULL` autorisé |
| `longitude` | `REAL` | `NULL` autorisé |
| `favori` | `INTEGER` | `NOT NULL DEFAULT 0` *(ajoutée en v2)* |

**Choix techniques**

- 📱 Le téléphone est en `TEXT` pour garder le `0` initial, le `+216` et les espaces.
- 🛡️ Toutes les valeurs passent par des arguments `?` : aucune injection SQL possible,
  et un nom comme `O’Neil` ne provoque aucune erreur.
- 🧹 Chaque `Cursor` est fermé avec `use { }`.
- 🧵 Aucun accès à la base sur le thread principal : `viewModelScope` + `withContext(Dispatchers.IO)`.
- 🔍 Recherche faite en SQL (`LIKE ? ESCAPE`) : la base ne renvoie que les lignes utiles.
- 🖼️ Les photos sont copiées dans `filesDir/photos` ; la base garde le chemin de la copie, qui reste
  lisible après un redémarrage.
- 🆔 Un contact est toujours identifié par son `_id`, jamais par sa position dans la liste.

---

## ⚙️ Préférences utilisateur

| Préférence | Clé | Valeurs | Défaut | Effet |
|---|---|---|---|---|
| Taille de police | `taille_police` | 14 / 16 / 20 sp | 16 sp | Nom, prénom, téléphone |
| Couleur du texte | `couleur_preferee` | Noir, Bleu, Vert, Rouge, Violet | Noir | Nom et prénom |
| Appel depuis la liste | `appel_depuis_liste` | Oui / Non | Non | Bouton 📞 sur chaque ligne |
| Ordre de tri *(bonus)* | `ordre_tri` | Favoris d’abord, Nom, Prénom | Favoris d’abord | Clause `ORDER BY` |

Les changements s’appliquent **dès le retour sur la liste**, sans redémarrer l’application
(relecture dans `onResume()`), et sont conservés après la fermeture.

---

## 🏆 Bonus réalisés

### 1. Migration de la base en version 2
`onUpgrade` ajoute la colonne `favori` sans recréer la table :

```sql
ALTER TABLE contacts ADD COLUMN favori INTEGER NOT NULL DEFAULT 0
```

✅ Testé sur un téléphone où la base était en version 1 : **aucun contact perdu**.
Les favoris sont marqués d’une ⭐ et affichés en tête de liste.

### 2. Préférence « Ordre de tri »
`ORDER BY` n’accepte pas d’argument `?`. La valeur de la préférence sert donc uniquement à
**choisir parmi des clauses fixes** : elle n’est jamais concaténée dans le SQL.

### 3. Import de 100 contacts dans une transaction
Menu ⋮ → **Importer 100 contacts (test)**. Résultats mesurés sur OPPO CPH2579 :

| 100 insertions | Durée |
|---|---:|
| Sans transaction | **410 ms** |
| Avec une transaction unique | **49 ms** |

➡️ La transaction est environ **8 fois plus rapide** : une seule écriture sur le disque au lieu
de 100, et l’import est atomique (tout ou rien).

---

## 🧪 Tests

- [x] Premier lancement : 6 contacts de démonstration, pas de doublon aux lancements suivants
- [x] Création, modification, suppression, puis fermeture complète : tout est conservé
- [x] Nom avec apostrophe (`O’Neil`) et recherche : aucune erreur SQL
- [x] Champs obligatoires vides, e-mail invalide, GPS hors limites : message d’erreur, rien en base
- [x] Chaque taille et chaque couleur appliquées dès le retour des Paramètres
- [x] Bouton Appeler affiché / masqué ; il ouvre le composeur sans ouvrir la fiche
- [x] Carte avec marqueur
- [x] Database Inspector : contenu identique à l’affichage
- [x] Bonus : migration v1 → v2, favoris, ordre de tri, import de 100 contacts
- [x] Tests unitaires JVM : `ContactValidatorTest`, `OrdreTriTest`

---



**Database Inspector**

<img src="captures/19_bonus_database_inspector_favori.png" width="100%"/>

Les 22 captures sont dans le dossier [`captures/`](captures/).

---

## 📄 Compte rendu

Le compte rendu complet est dans [`docs/Compte_rendu_TP_Contacts_V4.pdf`](docs/Compte_rendu_TP_Contacts_V4.pdf).
Il contient le schéma de la base, la stratégie de threads, le choix du filtrage, la gestion des photos
et des préférences, les tests, les difficultés rencontrées et les réponses aux questions de compréhension.

---

<div align="center">

**Mouna Bettouhami** — L3 DSI
[github.com/BettouhamiMouna](https://github.com/BettouhamiMouna)

</div>
