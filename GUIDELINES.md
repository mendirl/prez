# Guide de reprise — Présentation Remark.js (Projet Amber & Valhalla)

Dernière mise à jour: 2025-09-01

Ce dépôt contient une présentation Remark.js autoportée (fichier HTML unique) sur les projets OpenJDK Amber et Valhalla.

Fichier principal:
- prez_java.html (ouvrir dans un navigateur pour afficher les slides)

## Objectif de ce guide
Fournir des consignes rapides pour reprendre la mise à jour de la présentation ou formuler un prompt à un futur assistant, tout en respectant le style et la structure existants.

## Rappels de structure (Remark.js)
- Les slides sont séparés par `---` dans le bloc `<textarea id="source">…`.
- Le style est minimaliste (ratio 16:9, highlightStyle: github).
- Le contenu est en français.
- Chaque slide comporte idéalement une ligne « Intérêt: … » qui explique en une phrase la valeur de la page.
- Les sections listant des JEPs les ordonnent « par version de Java » (ex.: `JEPs (par version): Java 12 — 325; Java 13 — 354; Java 14 — 361`).
- Les exemples de code sont balisés avec des fences Markdown (```java … ```), sans mélanger du HTML à l’intérieur.

## Conventions éditoriales
- Titres de slides en `##` (ou `#` pour la page de titre).
- Phrase « Intérêt: … » juste après le titre ou la ligne JEPs.
- Références en fin de section, avec URL officielles (openjdk.org, JEP index, design notes).
- Utiliser des tirets courts et lisibles; pour les listes, puces `-`.
- Garder les liens vers l’index JEP à jour et éviter d’asserter des JEP hypothétiques sans le signaler.

## Ajouter/modifier des slides
1. Ouvrir `prez_java.html` dans un éditeur.
2. Rechercher `<textarea id="source">` et ajouter une nouvelle slide après un séparateur `---`.
3. Respecter la structure:
   - Titre de slide (`## ...`)
   - Ligne « Intérêt: … »
   - (Optionnel) Ligne « JEPs (par version): … »
   - Contenu (texte concis, code Java si pertinent)
4. Vérifier que les blocs de code sont bien fermés et que chaque slide est séparée par `---`.
5. Sauvegarder puis ouvrir `prez_java.html` dans un navigateur pour vérifier le rendu.

## Lancer/visualiser la présentation
- Double-cliquer sur `prez_java.html` ou faire glisser le fichier dans un navigateur moderne (Edge, Chrome, Firefox, Safari).
- La navigation se fait avec les flèches du clavier, PageUp/PageDown.

## Section Valhalla — cohérence
- Slides ajoutées: aperçu, concepts clés, repères JEPs (évolutif), exemples hypotétiques/preview, positionnement vs Amber, références mises à jour.
- Mentionner que l’état des JEP Valhalla évolue rapidement; renvoyer à l’index JEP.

## Modèle de prompt pour reprise (copier-coller)
Vous pouvez utiliser ce prompt la prochaine fois pour demander des mises à jour cohérentes:

```
Contexte projet: une présentation Remark.js autoportée dans prez_java.html sur OpenJDK Amber (et Valhalla). 
Contraintes:
- Langue: français.
- Chaque slide inclut une ligne « Intérêt: … ».
- Lister les JEPs « par version de Java ».
- Conserver le format Remark.js (--- pour séparer les slides; code Java avec ```java).
- Ne pas casser la structure HTML existante (textarea + scripts remark).
Tâche: [décrire précisément les ajouts/modifs souhaités, ex.: « ajouter 2 slides sur X, avec exemples de code et références officielles »].
Livrable: modifier prez_java.html uniquement, changements minimalistes et cohérents avec le style actuel.
```

## Check-list rapide avant commit
- [ ] Chaque nouvelle slide a son séparateur `---`.
- [ ] La ligne « Intérêt: … » est présente et utile.
- [ ] Les JEPs sont classés par version et, si possible, vérifiés via https://openjdk.org/jeps/0.
- [ ] Les blocs de code sont correctement balisés et lisibles.
- [ ] Les liens de références pointent vers des sources officielles.

## Maintenance
- Quand une JEP change d’état (preview → final), mettre à jour la ligne « (preview) » et la version de Java si nécessaire.
- Valhalla évoluant, privilégier les formulations prudentes et l’index JEP.

Bonnes présentations !
