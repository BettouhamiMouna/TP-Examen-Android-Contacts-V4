# TP Android — Contacts V4 (SQLite • Préférences • Fragments • Google Maps)

Application Kotlin / XML à activité unique. Les contacts sont persistés dans une base SQLite
locale (`contacts.db`, API native `SQLiteOpenHelper`, sans Room) et l’affichage de la liste est
personnalisé par trois préférences utilisateur (`SharedPreferences`).

## Lancer le projet

1. Ouvrir le dossier dans Android Studio (Koala ou plus récent, JDK 17) et laisser Gradle synchroniser.
2. Copier `local.properties.exemple` en `local.properties` et renseigner `MAPS_API_KEY=...`.
   `local.properties` est ignoré par Git : **la clé n’est jamais publiée**.
3. Lancer sur un émulateur avec Google Play Services (nécessaire pour la carte).
4. Tests unitaires : `./gradlew test` (classes `ContactValidatorTest` et `OrdreTriTest`).
   Le wrapper Gradle (`gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`) est versionné :
   le projet se construit à l’identique sur n’importe quel poste (Gradle 8.7).

> Si vous partez de votre projet V3, gardez votre `namespace` / paquet et adaptez la ligne
> `package com.example.contacts` des fichiers.

## Structure

```
com.example.contacts
├── MainActivity.kt              activité unique, barre d’application, menu Paramètres, navigation
├── data/
│   ├── Contact.kt               data class (id = 0 avant insertion, favori en v2)
│   ├── ContactContract.kt       table, colonnes, CREATE TABLE, migration v2, OrdreTri (ORDER BY)
│   ├── ContactDbHelper.kt       SQLiteOpenHelper singleton, onCreate (+ 6 contacts démo), onUpgrade 1→2
│   ├── ContactDao.kt            seule classe avec du SQL : insert/update/delete/getAll/getById/search
│   │                            + insertAll (avec/sans transaction), deleteAll, deleteImportes
│   └── ContactValidator.kt      validation du formulaire (Kotlin pur, testé)
├── ui/
│   ├── ContactViewModel.kt      ViewModel partagé : LiveData de la liste, événements, viewModelScope + IO
│   ├── ListeContactsFragment.kt liste, filtre, FAB « + », bouton Appeler selon préférence
│   ├── ContactAdapter.kt        ListAdapter + DiffUtil, stable ids = _id
│   ├── DetailContactFragment.kt création / modification / suppression, photo, actions
│   ├── CarteContactFragment.kt  Google Maps + marqueur, messages si pas de position
│   └── ParametresFragment.kt    PreferenceFragmentCompat (res/xml/preferences.xml)
└── util/
    ├── PreferencesManager.kt    lecture centralisée et conversion des préférences
    ├── PhotoStorage.kt          copie des photos dans filesDir/photos, miniatures
    └── FragmentExtensions.kt    Toast, ACTION_DIAL, ACTION_SENDTO
```

## Éléments pour le compte rendu

### Schéma de la base et choix des types
Table `contacts` : `_id INTEGER PRIMARY KEY AUTOINCREMENT`, `nom`, `prenom`, `telephone` en
`TEXT NOT NULL`, `email` et `photo_uri` en `TEXT` (facultatifs), `latitude` / `longitude` en
`REAL` acceptant `NULL`. Le téléphone est en `TEXT` car un `INTEGER` perdrait le `0` initial, le
`+216` et les espaces. Une position absente vaut `NULL` dans les deux colonnes : la validation
impose « les deux ou aucune ». `_id` reprend la convention `BaseColumns`.

### Rôle du helper et du DAO
`ContactDbHelper` gère le cycle de vie du fichier : création de la table et insertion des
contacts de démonstration dans `onCreate` (exécuté une seule fois, quand `contacts.db` n’existe
pas), migrations dans `onUpgrade`. C’est un singleton construit avec `applicationContext`.
`ContactDao` est la seule classe qui contient du SQL ; toutes les valeurs variables passent par
des `?` (`selectionArgs`), chaque `Cursor` est fermé par `use { }` et les colonnes sont lues avec
`getColumnIndexOrThrow()`. Le tri `nom COLLATE NOCASE, prenom COLLATE NOCASE` est fait en SQL.

### Stratégie de threads
Le DAO est bloquant. Le `ContactViewModel` lance chaque opération dans `viewModelScope` et
l’exécute dans `withContext(Dispatchers.IO)` ; le résultat revient sur le thread principal
(`LiveData` pour la liste, `Channel` pour les événements « enregistré / supprimé / erreur »).
La lecture d’une fiche (`trouverContact`) est une fonction `suspend` appelée depuis
`viewLifecycleOwner.lifecycleScope`. Un enregistrement en cours survit à une rotation car il
tourne dans le scope du ViewModel, et son événement est mis en tampon jusqu’à ce que le
fragment recréé le collecte.

### Choix du filtrage : SQL
Le filtre est fait en SQL (`LIKE ? ESCAPE '\'` sur nom, prénom et téléphone) :
la base ne renvoie que les lignes utiles et le tri reste fait par SQLite, ce qui passe à l’échelle
si le nombre de contacts augmente ; cela illustre aussi les requêtes paramétrées (une apostrophe
dans le filtre ne provoque aucune erreur). Les caractères `%` et `_` saisis sont échappés.
Chaque nouvelle frappe annule la requête précédente (`Job.cancel()`).
Limite connue : `LIKE` n’ignore la casse que pour les lettres ASCII (`é` ≠ `É`). Un filtrage en
mémoire avec `lowercase()` gérerait mieux les accents mais chargerait toute la table.

### Gestion des photos
La galerie (`GetContent`) fournit une URI temporaire qui peut devenir illisible après un
redémarrage : l’image est donc **copiée** dans `filesDir/photos` (en `Dispatchers.IO`) et c’est
le chemin de la copie qui est stocké dans `photo_uri`. La caméra (`TakePicture`) écrit
directement dans un fichier de `filesDir/photos` exposé par un `FileProvider`. Les copies
inutiles sont supprimées : photo remplacée avant enregistrement, ancienne photo après une
modification, photo d’un contact supprimé, photo choisie puis « Annuler ». Les images sont
décodées réduites (`inSampleSize`) pour éviter une saturation de la mémoire dans la liste.

### Préférences
Stockage : `SharedPreferences` par défaut, écrites automatiquement par `ParametresFragment`
(`PreferenceFragmentCompat` + `res/xml/preferences.xml` + `res/values/arrays.xml`). Les valeurs
par défaut sont initialisées par `PreferenceManager.setDefaultValues()` dans `MainActivity`.
Lecture : `PreferencesManager` convertit une fois pour toutes (taille en `Float` sp, couleur en
`Int` via `Color.parseColor`, booléen) et renvoie un `ParametresAffichage`.
Application immédiate : au retour sur la liste, `onResume()` relit les préférences et les donne à
l’adapter, dont le setter appelle `notifyDataSetChanged()` si elles ont changé. Le détail applique
taille et couleur dans son `onResume()`. Lisibilité : les cinq couleurs sont des teintes foncées
et l’application impose un thème clair (pas de `values-night`), donc elles restent lisibles.

### Rotation et navigation
`MainActivity` n’ajoute la liste que si `savedInstanceState == null` (pas de fragments en
double). Les `EditText` ayant un id restaurent seuls leur texte ; le détail ne recharge la base
que si les données n’ont pas encore été chargées, et sauvegarde dans le `Bundle` la photo en
cours et le fichier caméra en attente. Le filtre est conservé dans le ViewModel.

## Bonus réalisés

### 1. Migration vers la version 2 : colonne `favori`
`DATABASE_VERSION` passe à 2. `onUpgrade` applique, si `oldVersion < 2`,
`ALTER TABLE contacts ADD COLUMN favori INTEGER NOT NULL DEFAULT 0` : la table existante est
modifiée sans être recréée, donc **aucun contact n’est perdu** (ils reçoivent tous `favori = 0`).
Une installation neuve passe par `onCreate`, qui crée directement le schéma v2.
Les migrations sont écrites en `if (oldVersion < N)` successifs pour qu’un appareil resté en
version 1 applique toutes les étapes dans l’ordre. Dans l’interface : case « Favori » dans le
détail, étoile après le nom dans la liste, favoris en tête (ordre par défaut).
*Test :* installer l’ancienne version, ajouter un contact, installer la nouvelle → le contact est
toujours là ; Database Inspector montre la colonne `favori`.

### 2. Quatrième préférence « Ordre de tri »
`ListPreference` `ordre_tri` : favoris d’abord (défaut), nom puis prénom, prénom puis nom.
`PreferencesManager.ordreTri()` convertit la valeur en `ContactContract.OrdreTri`, dont chaque
constante porte une clause `ORDER BY` fixe. `ORDER BY` n’accepte pas d’argument `?` : la valeur
de la préférence sert donc seulement à **choisir dans une liste blanche**, elle n’est jamais
concaténée dans le SQL (une valeur inconnue donne l’ordre par défaut — testé dans `OrdreTriTest`).
Le ViewModel relit l’ordre à chaque `charger()`, appelé dans `onResume()` de la liste :
le changement est appliqué dès le retour des Paramètres.

### 3. Import de 100 contacts et mesure de la transaction
Menu ⋮ de la liste → « Importer 100 contacts (test) ». Le ViewModel (sur `Dispatchers.IO`) :
1. insère 100 contacts **sans transaction** et mesure la durée (`System.nanoTime()`), puis les supprime ;
2. insère 100 contacts dans **une transaction unique**
   (`beginTransaction` / `setTransactionSuccessful` / `endTransaction` dans un `finally`) et mesure.
Les deux durées s’affichent dans une boîte de dialogue. Sans transaction, chaque `insert()` est
une transaction implicite validée et écrite sur le disque une par une ; avec une transaction,
il n’y a qu’une seule validation à la fin, d’où un gain important (souvent 10× ou plus), et
l’import est atomique (tout ou rien). « Supprimer les contacts importés » nettoie la liste
(requête paramétrée `prenom = ? AND nom LIKE ?`).

| Mesure (à compléter sur votre appareil) | Durée |
|---|---|
| 100 insertions sans transaction | … ms |
| 100 insertions avec une transaction | … ms |

## Réponses aux questions de compréhension

**1. onCreate / onUpgrade.** `onCreate` est appelée au premier `getWritableDatabase()` /
`getReadableDatabase()` lorsque le fichier n’existe pas. `onUpgrade` est appelée à l’ouverture si
`DATABASE_VERSION` est supérieure à la version enregistrée dans le fichier. Si l’on modifie
`onCreate` sans changer la version, rien ne se passe sur un appareil où la base existe déjà :
l’ancien schéma est conservé et le code peut planter (colonne inexistante). Il faut désinstaller
l’application / effacer ses données, ou incrémenter la version et écrire la migration.

**2. Arguments `?`.** Les valeurs sont transmises séparément du texte SQL : elles ne sont jamais
interprétées comme du SQL. On évite les erreurs de syntaxe et les injections SQL. Exemple : avec
`"SELECT * FROM contacts WHERE nom = '" + saisie + "'"`, la saisie `O'Neil` produit
`WHERE nom = 'O'Neil'` → erreur de syntaxe ; la saisie `' OR '1'='1` renvoie toute la table.

**3. Valeurs de retour.** `insert()` renvoie le `_id` de la nouvelle ligne ou `-1` en cas d’échec ;
`update()` et `delete()` renvoient le nombre de lignes touchées. Dans l’application : `-1` →
message « L’insertion a échoué » ; `0` pour `update` / `delete` → « Contact introuvable » ou
« La modification n’a pas pu être enregistrée » ; sinon Toast de confirmation, rechargement
de la liste et retour à celle-ci.

**4. Thread principal.** Les accès disque sont lents et imprévisibles ; sur le thread principal
ils bloquent le dessin de l’interface (saccades) et peuvent provoquer un ANR après quelques
secondes. Évité grâce aux coroutines : `viewModelScope` / `lifecycleScope` +
`withContext(Dispatchers.IO)` ; le DAO n’est jamais appelé ailleurs.

**5. Helper unique avec applicationContext.** Une seule instance = une seule connexion partagée,
sans conflits de verrous ni ouvertures multiples coûteuses. `applicationContext` vit aussi
longtemps que le processus : le singleton ne retient pas une Activity détruite (pas de fuite
mémoire après une rotation).

**6. SharedPreferences ou SQLite.** SharedPreferences : peu de valeurs simples clé/valeur
(réglages), lecture directe, pas de requêtes. SQLite : données structurées, nombreuses,
multiples enregistrements, recherche, tri, contraintes, relations. Les contacts sont une
collection d’enregistrements à trier et filtrer → SQLite. Les trois réglages sont des valeurs
isolées → SharedPreferences (intégrées nativement à AndroidX Preference).

**7. Application sans redémarrage.** La liste relit les préférences dans `onResume()` (appelée au
retour depuis Paramètres), les passe à l’adapter qui fait `notifyDataSetChanged()` : chaque ligne
est re-liée avec la nouvelle taille, la nouvelle couleur et la visibilité du bouton Appeler.

**8. ACTION_DIAL / ACTION_CALL.** `ACTION_DIAL` ouvre le composeur avec le numéro prérempli ;
l’utilisateur décide d’appeler : aucune permission requise. `ACTION_CALL` lance l’appel
directement et exige la permission dangereuse `CALL_PHONE`, demandée à l’exécution.

**9. Désinstallation / effacement des données.** Dans les deux cas, le dossier privé de
l’application est supprimé : `databases/contacts.db`, `shared_prefs/…_preferences.xml` et
`files/photos`. Après effacement des données, l’application repart comme au premier lancement :
`onCreate` du helper s’exécute à nouveau (contacts de démo réinsérés) et les préférences
reviennent aux valeurs par défaut. (`allowBackup="false"` : aucune restauration automatique.)

## Tests à réaliser (et captures à joindre)

- [ ] Premier lancement : 6 contacts de démo ; deuxième lancement : pas de doublon.
- [ ] Ajout, modification, suppression puis fermeture complète : persistance de chaque opération.
- [ ] Nom « O’Neil » / « L'Ariana » et filtre contenant `'` : aucune erreur SQL.
- [ ] Enregistrement sans nom / prénom / téléphone : erreurs sous les champs, rien en base.
- [ ] Changer la photo puis redémarrer l’appareil : la photo est toujours affichée.
- [ ] GPS absent, non numérique (`abc`), hors limites (`95`, `200`) : refusé ; carte d’un contact
      sans position : message explicite.
- [ ] Chaque taille et chaque couleur, dans la liste et dans le détail.
- [ ] Appel depuis la liste activé / désactivé ; le bouton ouvre le composeur sans ouvrir le détail.
- [ ] Relance : les trois préférences sont conservées.
- [ ] Rotation sur la liste (filtre), le détail en cours de saisie, les paramètres ; Retour partout.
- [ ] App Inspection → Database Inspector : contenu de `contacts` identique à l’affichage.
- [ ] Bonus : mise à jour depuis la v1 sans perte ; favori coché → en tête de liste avec étoile ;
      chaque ordre de tri ; import de 100 contacts (noter les deux durées) puis suppression.
