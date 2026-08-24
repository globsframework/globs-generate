# Performances : Glob par défaut, Glob généré, code généré

Synthèse des mesures dispersées dans les `CLAUDE.md` du workspace (`globs-generate`,
`globs-bin-serialisation`, `globs-grpc`, `globs-fix`). Rien n'est mesuré ici : ce fichier ne fait que
rassembler et confronter des chiffres déjà relevés, avec la référence du benchmark à relancer pour
les revérifier.

**Comment lire ces chiffres.** Chaque tableau vient d'une seule machine et d'une seule session JMH.
Ce qui a un sens, ce sont les *colonnes entre elles* et les rapports « avant → après » d'un même
tableau ; pas la valeur absolue comparée à une exécution plus ancienne ou à un autre module.

## Les trois choses que l'on compare

Le vocabulaire est ambigu parce que deux générations différentes cohabitent, et **elles ne se valent
pas du tout** :

| | ce que c'est | comment on l'active |
| --- | --- | --- |
| **par défaut** | core : `DefaultGlob32/64/128`, valeurs dans un `Object[]`, parcours par boucle sur `type.getFields()` | rien à faire |
| **classe générée** | une classe `Glob` par `GlobType` émise en ASM, valeurs dans de vrais champs Java | `-Dglobs.builder=…object.GeneratorGlobFactoryService` (ou `…primitive.…`) |
| **code généré** | le *parcours* est émis en ASM et déroulé : accesseurs, visiteurs `accept`, callers de lecture/écriture | `-Dglobs.caller.fromGlob=…`, `-Dglobs.caller.toGlob=…`, ou `FromGlobCallerFactory.callerFor(...)` |

Le fil conducteur de toutes les mesures ci-dessous :

> **Générer la classe Glob seule est neutre ou perdant. C'est le code généré — le caller — qui paie.
> Et le caller n'a même pas besoin de la classe générée pour payer.**

## 1. La classe générée seule : neutre au mieux, perdante en pratique

Sans caller, dans un vrai codec, la génération de la classe Glob **ralentit** :

| module | benchmark | par défaut | classe générée (object) | classe générée (primitive) |
| --- | --- | --- | --- | --- |
| globs-fix | `FixMessagePerf.write` | **2.84 M ops/s** | 2.72 M (−4 %) | 2.64 M (−7 %) |
| globs-fix | `FixMessagePerf.read` | **1.46 M ops/s** | 1.37 M (−6 %) | 1.31 M (−10 %) |
| globs-grpc | `GeneratedGlobPerfTest.write` | 185–197k ops/s | 104k (**moitié moins**) | 141k |
| globs-bin-serialisation | 200k globs × 40 champs, write | **1.95 M globs/s** | 1.15 M globs/s | — |

La raison est toujours la même : **une classe d'accesseur par champ = plus de récepteurs sur le même
site d'appel mégamorphique**. La boucle du codec (`FieldWrite[]`, `DirectFieldReader.read`) voit
désormais toutes les classes générées de tous les types du process au lieu d'une poignée
d'implémentations de core.

Deuxième raison, indépendante du codec et qui touche le code applicatif : les `doGet`/`doSet` générés
sont un `tableswitch` sur tous les champs, donc leur taille croît avec le nombre de champs
(flavour object : `doSet` 214 bytecodes à 10 champs, 384 à 20, 724 à 40 ; `doGet` 288 à 20, 528 à 40).
Au-delà de `FreqInlineSize` (325), un `glob.get(F)` chaud n'est **plus jamais inliné**, là où le
`uncheckGet` de core est un accès tableau toujours inliné. Un type de plus de ~15-20 champs transforme
chaque accès applicatif en appel réel ; un type étroit inline un `tableswitch` entier par accès, ce qui
peut faire sauter le budget de nœuds de C2 (`COMPILE SKIPPED: out of nodes`).

## 2. Le code généré : là où se trouve le gain

### 2.1 Caller de lecture (`FromGlobCallerPerf`, M ops/s, 4 / 20 / 40 champs)

| parcours | 4 | 20 | 40 |
| --- | --- | --- | --- |
| boucle à la main sur accesseurs + fonctions | 23.0 | 4.57 | 2.30 |
| `LoopFromGlobCaller` (le fallback) | 18.9 | 3.97 | 2.00 |
| **caller généré sur un `DefaultGlob`** | **76.7** | **14.3** | **6.09** |
| caller généré sur un Glob généré (object) | 91.1 | 15.8 | 6.77 |

Deux lectures :

- contre le fallback, le caller sur Glob généré vaut **×4.7 / ×4.1 / ×4.9** en object
  (18.5 → 86.8, 3.29 → 13.6, 1.35 → 6.69) et **×4.2 / ×4.4 / ×4.0** en primitive
  (18.1 → 76.0, 3.25 → 14.4, 1.32 → 5.34) ;
- **`forDefaultGlob` prend l'essentiel du gain sans générer aucune classe Glob** : ×3.3 / ×3.1 / ×2.6
  contre la boucle à la main, et à 11-16 % du caller sur Glob généré — sans les classes par type ni
  les dégâts d'inlining du §1. C'est le meilleur rapport du module.

La boucle écrite à la main fait jeu égal avec le fallback (21.9 / 3.50 / 0.96 en object) : passer par
`FromGlobCallerFactory.callerFor` ne coûte donc rien, même pour les types qui retombent sur le fallback.

Le mécanisme n'est pas l'économie de boucle mais le `static final` : constante JIT ⇒ récepteur unique
⇒ inlining, là où l'unique site d'appel d'une boucle voit toutes les fonctions de tous les champs de
tous les types et reste mégamorphique.

### 2.2 Caller d'écriture (`ToGlobCallerPerf`, M ops/s, 4 / 20 / 40 entrées)

| passe | 4 | 20 | 40 |
| --- | --- | --- | --- |
| clés denses, boucle à la main sur tableau | 19.5 | 3.89 | 1.90 |
| clés denses, `LoopToGlobCallerFactory` | 17.4 | 3.30 | 1.58 |
| **clés denses, généré** (`tableswitch`) | **32.0** | **4.40** | **1.62** |
| clés éparses, boucle à la main sur `HashMap` | 15.8 | 3.23 | 1.54 |
| clés éparses, `LoopToGlobCallerFactory` | 17.4 | 3.27 | 1.49 |
| **clés éparses, généré** (`lookupswitch`) | **31.4** | **4.27** | **2.09** |
| toutes les entrées, boucle à la main | 21.0 | 4.23 | 2.14 |
| toutes les entrées, `LoopToGlobCallerFactory` | 20.9 | 4.24 | 2.18 |
| **toutes les entrées, généré** (déroulé) | **34.6** | **7.24** | **2.79** |

Le côté écriture paie **beaucoup moins** que le côté lecture : ×1.6 à 4 entrées, ×1.13 à 20 contre la
boucle, parce que chaque tour fait déjà du vrai travail (parser une valeur, la poser sur le Glob) et
que la boucle d'un parser ne paie qu'un site mégamorphique là où la lecture en paie deux (accesseur +
fonction). **À 40 clés denses le switch généré perd** (1.62 contre 1.90) — méthode trop grosse, budget
d'inlining épuisé, saut indirect dans une table de 40 entrées moins bien prédit que la recherche
dichotomique d'un `lookupswitch`. `ToGlobCallerAll` (déroulé, sans switch) est le seul bras qui
gagne partout : ×1.6 / ×1.7 / ×1.3.

### 2.3 Accesseurs générés

Contre les accesseurs à base de `doGet`/`doSet` qu'ils ont remplacés (`AccessorPerf`, flavour
primitive) : `getNative` **×1.8**, `setNative` **×1.4**, `String` get/set **×1.35**, `isSet` **×1.47**,
`isNull` **×1.37**.

### 2.4 Visiteurs déroulés (`VisitorUnrollPerf`, tous champs positionnés)

Déroulé contre la version en boucle qu'il remplace : **×5.6 à 4 champs et ×10.8 à 20** en object,
**×2.4 / ×3.0 / ×3.3 à 4 / 20 / 40** en primitive (22.3 → 52.3, 4.40 → 13.1, 1.66 → 5.51 M ops/s).

Détail qui a compté : lire le masque par `GETFIELD` + `IAND`/`LAND` au lieu d'appeler `isSetAt(index)`
vaut **12 %** sur un type à 40 champs (6.32 → 7.13 M ops/s).

Mais le déroulage **n'accélère pas la sérialisation telle qu'elle est écrite** : `GlobSerializer`
pilote sa propre boucle sur `type.getFields()` et n'appelle jamais `glob.accept` — les implémentations
font jeu égal dans le bruit. Faire passer la sérialisation par `accept` serait ~1.6-1.9× plus rapide
que le sérialiseur actuel ; sur la version en boucle c'était 1.5-1.7× plus *lent*. Les deux
changements ne paient qu'ensemble.

## 3. Le second niveau : records et lambdas

Le caller ne rend le **premier** site d'appel constant. Le second — la fonction qui appelle son codec,
son délégué — ne se replie que si C2 fait confiance aux champs `final` d'instance de la classe, ce
qu'il fait pour les **records**, les **hidden classes** et donc les **lambdas**, et pas pour une classe
ordinaire (`TrustFinalNonStaticFields` est off par défaut).

Mesuré sur JDK 27-ea, quatre classes collaboratrices :

| la fonction est | second niveau | ns / 4 appels |
| --- | --- | --- |
| classe ordinaire, `private final Delegate d` | `failed to inline: virtual call` | 5.40 |
| **un record** | `inline (hot)` | **0.51** |
| **une lambda** | `inline (hot)` | **0.51** |
| classe ordinaire + `-XX:+TrustFinalNonStaticFields` | `inline (hot)` | 0.53 |
| table de fonctions (premier niveau non constant) | rien ne se propage | 10.90 |

En vrai module, sur les *feuilles* : globs-grpc `+4 %` (224k → 233-235k), globs-bin-serialisation
`+6.7 %` (207.8k → 221.7k). Sur les lecteurs de globs-bin-serialisation, l'expérience porte son propre
témoin : le bras caller gagne `read` +3.8 % (88.7k → 92.1k) et `readNested` +12.3 %
(583.6k → 655.4k), pendant que le bras tableau — mêmes objets, récepteur non constant — ne bouge pas
(75.7k → 76.1k, 500.7k → 506.1k).

**Le cas imbriqué est le contraire, mesuré :** rendre repliable la descente vers un sous-Glob coûte
**−12 %** sur `writeNested` (1.76M → 1.54M ops/s) — C2 inline alors le `call` de l'enfant dans celui du
parent, puis celui du petit-enfant, jusqu'à `NodeCountInliningCutoff`. Le champ non repliable servait
de *barrière* d'inlining. Replier les feuilles, garder une frontière là où pend un sous-arbre.

## 4. Ce que ça donne bout à bout dans les modules consommateurs

### globs-grpc (`GeneratedGlobPerfTest`, quatre types)

| | write, caller off | write, caller on |
| --- | --- | --- |
| DEFAULT (pas de caller) | 185–197k ops/s | inchangé (c'est le témoin) |
| classe générée object | 104k | **229k** |
| classe générée primitive | 141k | **209k** |

Lecture : `read` OBJECT **123.9k → 187.0k ops/s, +51 %**. Le bras tableau (fallback) perd ~5 %
(130.1k → 123.9k) pour la super-interface supplémentaire sur les feuilles.

À retenir : le caller ne fait pas que gagner 120 %, il **rachète d'abord** la pénalité de la classe
générée (104k contre 185-197k pour core), puis dépasse.

Deux stratégies alternatives essayées et abandonnées, à ne pas retenter : le sérialiseur par
`glob.accept` (+92 % object / +35 % primitive contre l'écrivain à accesseurs *avant* le caller, −16 %
sur DefaultGlob, mais 222k/209k/168k contre 229k/209k/184k une fois le caller là) ; et
`ToGlobCallerAll` côté écriture (**235.7k → 213.3k, −9.5 %**).

### globs-bin-serialisation (200k globs, écriture seule, flavour object)

| champs | caller off | caller on | |
| --- | --- | --- | --- |
| 4 | 16.9 | **19.8** M globs/s | +17 % |
| 20 | 2.81 | **4.46** M globs/s | +59 % |
| 40 | 1.15 | **2.20** M globs/s | +91 % |

Référence à ne pas perdre de vue : à 40 champs, core `DefaultGlob` écrit à **1.95 M globs/s**. La
classe générée seule tombe à 1.15 ; il faut le caller pour arriver à 2.20.

Lecture (`GeneratedGlobPerfTest`, OBJECT) : `read` 75.7k → 88.7k (**+17 %**), `readNested`
500.7k → 583.6k (**+17 %**). Le chemin de repli paie un peu la séparation : `read` inchangé,
`readNested` −2.6 % (514.2k → 500.7k). Côté `ToGlobCaller`, l'adoption vaut **+17 %** sur les
deux benchmarks de lecture.

### globs-fix

Pas de caller aujourd'hui, d'où le tableau du §1 où la génération est une petite perte. Le SPI actuel
l'empêche : `FieldWrite.writeAt` retourne un index de buffer et `DirectFieldReader.read` prend une
plage d'octets, là où `FromGlobFunction` / `ToGlobFunction` retournent `void` et ne portent que
des objets. Il faudrait un troisième émetteur dans globs-generate sur une interface de fonction
retournant un `int` (~40 lignes d'ASM de plus que `AsmCallerWriteGenerator`). Gain déjà acquis en
revanche : donner à chaque `FieldReader` le `GlobSetAccessor` du champ au lieu de `data.set(field,
value)` vaut **+21 % sur DEFAULT et +14 à +15 % sur les flavours générées**.

## 5. Points annexes déjà mesurés

- **Largeur de masque `int` vs `long`** : aucune différence de vitesse (`isSet` 1279 contre
  1259 M ops/s, égalité). Le découpage `Glob32`/`Glob64` existe pour l'**empreinte** : flavour
  primitive 40 → 48 octets par glob à 4 champs, 64 → 72 à 9 (toujours +8) ; flavour object 40 → 40 à 4
  champs, 40 → 48 à 5, 104 → 104 à 20. Descendre à un masque `byte`/`short` ne rapporterait rien,
  l'arrondi à 8 octets le mange.
- **Un `default call` sur l'interface** au lieu d'un `call` par classe finale : globs-grpc a mesuré
  **229k → 191k** (object) et **209k → 176k** (primitive). Une seconde dispatch d'interface sur le
  chemin qui existe pour en supprimer une.

## Relancer les mesures

```bash
# globs-generate : aucun binding exec, on lance JMH à la main
mvn -o test-compile dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt
java -cp target/classes:target/test-classes:$(cat /tmp/cp.txt) org.openjdk.jmh.Main FromGlobCallerPerf
#   … ToGlobCallerPerf | AccessorPerf | VisitorUnrollPerf | SerializerPerf

# modules consommateurs : la flavour est un @Param, JMH forke un JVM par flavour
java -cp … org.openjdk.jmh.Main GeneratedGlobPerfTest -p flavour=OBJECT     # bin-serialisation, grpc
java -cp … org.openjdk.jmh.Main FixMessagePerf -p flavour=OBJECT            # globs-fix
# les bras « lecture » ne prennent le caller généré qu'avec la propriété, à passer aux forks :
#   -jvmArgsAppend "-Dglobs.caller.toGlob=org.globsframework.model.generator.AsmCallerWriteGeneratorService"
```

`mvn -o install` dans `globs-generate` est un préalable pour tous les benchmarks des autres modules.
