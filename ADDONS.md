# Construir un addon sobre Zenkai

> 🇬🇧 [English version below](#english)

Este documento describe el contrato de extensibilidad de Zenkai para Minecraft 1.21.1 (NeoForge
`21.1.233`+) desde la versión **0.3.0**. Es un contrato *soportado*: lo que aquí se describe no
cambia sin un bump de versión mayor. Lo que no aparece aquí (cualquier clase `public` que no sea
uno de los puntos listados) es detalle interno y puede cambiar en cualquier versión, aunque
compile contra ella hoy.

Un addon es un mod NeoForge independiente, con su propio mod id, que depende de Zenkai
(`zenkai`, base package `com.hmc.zenkai`) y le añade contenido **sin fork, sin Mixin, sin tocar
el código fuente de Zenkai**.

## 1. Datapack cross-namespace — el mecanismo principal

Zenkai carga varias carpetas de datapack escaneando **todos los namespaces cargados**, no solo
`zenkai` (`ResourceManager.listResources`, vía `SimplePreparableReloadListener`). Un addon con su
propio mod id puede soltar sus JSON bajo `data/<tu_modid>/<carpeta>/*.json` y Zenkai los recoge
solos, sin que el addon toque ninguna clase de Zenkai:

| Carpeta | Añade | Referencia |
|---|---|---|
| `zenkai_skills/<id>.json` | Una habilidad nueva (o números de una existente) | `SkillDef`/`SkillManager` |
| `zenkai_masters/<id>.json` | Requisitos de admisión de un maestro (PL/alineamiento) | `MasterManager` |
| `zenkai_techniques/ki/<id>.json` | Números y FORMA de un `KiTechniqueType` existente | `TechniqueDef`/`TechniqueManager`, ver §3 |
| `zenkai_techniques/physical/<id>.json` | Números de un `PhysicalTechnique` existente | ídem |
| `zenkai_entities/<entity_type_id>.json` | Moveset/stats de un NPC de combate | `EntityStatsManager` |
| `zenkai_race_stats/<race>.json` | Curvas de stats de una raza | `RaceStatTable` |
| `zenkai_forms/<id>.json` | Una transformación nueva colgada de una cadena existente (`parent`) | `FormDef`/`FormRegistry` |
| `zenkai_archetypes/<nombre>.json` | Un arquetipo de entidad nuevo (forma de atributos + multiplicadores) | `EntityArchetype`/`EntityArchetypeManager` |
| `zenkai_face_parts/<categoría>/<nombre>.json` | Un rasgo facial nuevo (ojos, cejas, nariz, boca): capas PNG con su fuente de color, razas, variante por raza | `FacePartDef`/`FaceCatalog` |
| `zenkai_hair_styles/<nombre>.json` | Un peinado nuevo (geo de cubos o textura plana), razas, variante por transformación | `HairStyleDef`/`HairCatalog` |

En todos los casos: el `id` del archivo tiene que coincidir con un valor **ya existente** del
enum/cadena correspondiente (excepto `zenkai_forms`, `zenkai_archetypes`, `zenkai_masters`,
`zenkai_face_parts` y `zenkai_hair_styles`, que son verdaderamente abiertos — cualquier `id`
nuevo funciona). Las texturas/geo de rasgos y peinados van en `assets/<tu_modid>/`; su nombre
visible, en la clave de lang `face_part.<ns>.<categoría>.<nombre>` o `hair_style.<ns>.<nombre>`.
Formato completo de los campos en el javadoc de `FacePartDef.parse` y `HairStyleDef.parse`. Un archivo cuyo `id` no encaja en
ningún enum se ignora con un aviso en el log, nunca crashea.

**Conflictos entre datapacks**: `FormRegistry` avisa por log (`Zenkai-Forms`) si dos datapacks
compiten por la raíz de la misma raza — gana el primero en cargar, igual que cualquier otro
registro de Minecraft, pero ahora queda registrado en vez de fallar en silencio.

## 2. Bus de eventos (`com.hmc.zenkai.api.event`)

Nuevo en 0.3.0. Cinco eventos NeoForge estándar (`NeoForge.EVENT_BUS`), pensados para que un
addon reaccione a — o vete — algo que pasa dentro de Zenkai sin polling por tick ni Mixin:

| Evento | Se postea en | Cancelable | Qué significa cancelarlo |
|---|---|---|---|
| `RaceChosenEvent` | Tras fijar la raza de un jugador | No | — (informativo) |
| `FormChangedEvent` | Antes de aplicar un cambio de transformación | Sí | El jugador se queda en la forma anterior |
| `TechniqueFiredEvent` | Antes de que una técnica (ki o física) surta efecto | Sí | La técnica entera se aborta (sin coste, sin daño) |
| `KiConsumedEvent` | Antes de cobrar el ki de un disparo | Sí (y mutable: `setAmount`) | El disparo sale gratis, sin abortar la técnica |
| `MasterPurchaseEvent` | Antes de cobrar TP por una compra en persona ante un maestro | Sí | La compra se rechaza |

Cada evento lleva el `Player`/`ServerPlayer` implicado y los datos mínimos de la acción
(`ResourceLocation` de forma, id de técnica en minúsculas, id de maestro...). Suscribirse es
NeoForge estándar: `@SubscribeEvent` sobre un método que reciba el tipo de evento, registrado en
tu propio mod event bus o vía `NeoForge.EVENT_BUS.addListener(...)`.

## 3. Técnicas: números y forma en datapack, identidad en Java (a propósito)

Desde 0.3.0, `TechniqueDef` (el JSON de `zenkai_techniques/ki|physical/`) cubre tanto los
NÚMEROS de siempre (coste, daño, cooldown...) como la FORMA que antes vivía en switches Java de
`KiTechniqueType`: tamaño de proyectil (`projectile_size_base`/`_step`), radio de explosión
(`explosion_radius_mult`), reparto de área (`aoe_factor`/`aoe_edge_falloff`), si el proyectil
viaja (`travels`), si la carga anticipa el tamaño real (`charge_shows_real_size`), qué animación
impone (`anim_override`) y qué efectos admite (`allowed_effects`). Un icono propio
(`icon`, ResourceLocation de un PNG de 20×20) sustituye la celda fija del atlas compartido.

Esto hace **completamente reconfigurable por datapack** cualquiera de los 16 `KiTechniqueType`/4
`PhysicalTechnique` existentes — incluida la vía de "técnica firma" ya soportada (`master` en el
JSON liga una técnica a un maestro concreto, con color/animación/sonido propios, ver el javadoc
de `TechniqueDef`).

**Lo que sigue cerrado, a propósito**: `KiTechniqueType`/`PhysicalTechnique` siguen siendo enums
Java. Un addon puede re-tunear o reskinear (vía técnica firma) cualquiera de los tipos
existentes, pero no puede crear una identidad de técnica genuinamente nueva sin recompilar
Zenkai. Es una decisión de alcance de 0.3.0, no un descuido — abrir el enum a un registro de
verdad es una pieza mayor, evaluada y aplazada.

## 4. Lo que sigue cerrado, y por qué

| Cerrado | Motivo |
|---|---|
| `Race` (enum) | Identidad mecánica de cada raza, hardcodeada a propósito (`RacePassives`) — abrirlo es rediseñar el pipeline visual entero, proyecto propio. |
| `ZenkaiAttributes` (enum) | Diseño de sistema de stats (6 valores fijos), no lista de contenido. |
| `KiTechniqueType`/`PhysicalTechnique` (enum) | Ver §3. |
| `TechniqueDef.Kind`, `FormDef.Kind`, `AlignmentTier`, `MasterManager.Result` | Categorizan CÓMO funciona un sistema ya definido, no contenido en sí. |

## 5. Estándar NeoForge, sin nada especial de Zenkai

Packets propios (`RegisterPayloadHandlersEvent`, `event.registrar(TU_MOD_ID)`), `AttachmentType`
nuevos (`DeferredRegister<AttachmentType<?>>` propio) y config (`ModConfigSpec` propio) son
100% NeoForge estándar — nada de esto pasa por `ModNetworking` ni por ningún registro de Zenkai.

## 6. Daño de ki: tag, no namespace

`RacePassives` decide si un `DamageSource` cuenta como "daño de ki" (mitigación del Majin, que
solo absorbe daño físico) consultando el tag vanilla `zenkai:ki_damage`
(`data/zenkai/tags/damage_type/ki_damage.json`). Un addon con su propio `DamageType` de energía
solo necesita su propio archivo de tag (`data/<tu_modid>/tags/damage_type/ki_damage.json`,
fusión estándar de tags) para que el Majin lo trate como energía — cero Java.

---

<a id="english"></a>

# Building an addon on top of Zenkai (English)

This document describes Zenkai's extensibility contract for Minecraft 1.21.1 (NeoForge
`21.1.233`+) since version **0.3.0**. It is a *supported* contract: what is described here does
not change without a major version bump. Anything not listed here (any `public` class that is not
one of the points below) is an internal detail and may change in any version, even if it compiles
against it today.

An addon is a standalone NeoForge mod with its own mod id that depends on Zenkai (`zenkai`, base
package `com.hmc.zenkai`) and adds content to it **without forking, without Mixin, and without
touching Zenkai's source code**.

## 1. Cross-namespace datapacks — the main mechanism

Zenkai loads several datapack folders by scanning **every loaded namespace**, not just `zenkai`
(`ResourceManager.listResources`, through `SimplePreparableReloadListener`). An addon with its
own mod id can drop its JSON files under `data/<your_modid>/<folder>/*.json` and Zenkai picks
them up by itself, without the addon touching any Zenkai class:

| Folder | Adds | Reference |
|---|---|---|
| `zenkai_skills/<id>.json` | A new skill (or new numbers for an existing one) | `SkillDef`/`SkillManager` |
| `zenkai_masters/<id>.json` | Admission requirements for a master (PL/alignment) | `MasterManager` |
| `zenkai_techniques/ki/<id>.json` | Numbers and SHAPE of an existing `KiTechniqueType` | `TechniqueDef`/`TechniqueManager`, see §3 |
| `zenkai_techniques/physical/<id>.json` | Numbers of an existing `PhysicalTechnique` | same |
| `zenkai_entities/<entity_type_id>.json` | Moveset/stats of a combat NPC | `EntityStatsManager` |
| `zenkai_race_stats/<race>.json` | Stat curves of a race | `RaceStatTable` |
| `zenkai_forms/<id>.json` | A new transformation hanging off an existing chain (`parent`) | `FormDef`/`FormRegistry` |
| `zenkai_archetypes/<name>.json` | A new entity archetype (attribute shape + multipliers) | `EntityArchetype`/`EntityArchetypeManager` |
| `zenkai_face_parts/<category>/<name>.json` | A new face feature (eyes, eyebrows, nose, mouth): PNG layers with their color source, races, per-race variant | `FacePartDef`/`FaceCatalog` |
| `zenkai_hair_styles/<name>.json` | A new hairstyle (cube geo or flat texture), races, per-transformation variant | `HairStyleDef`/`HairCatalog` |

In every case the file's `id` must match an **already existing** value of the corresponding
enum/chain (except `zenkai_forms`, `zenkai_archetypes`, `zenkai_masters`, `zenkai_face_parts`
and `zenkai_hair_styles`, which are truly open — any new `id` works). Face/hair textures and geo
go under `assets/<your_modid>/`; their display name in the lang key
`face_part.<ns>.<category>.<name>` or `hair_style.<ns>.<name>`. Full field format in the javadoc
of `FacePartDef.parse` and `HairStyleDef.parse`. A file whose `id` fits no enum is ignored with a log warning, and
never crashes.

**Datapack conflicts**: `FormRegistry` logs a warning (`Zenkai-Forms`) if two datapacks compete
for the root of the same race — the first one to load wins, like any other Minecraft registry,
but it is now logged instead of failing silently.

## 2. Event bus (`com.hmc.zenkai.api.event`)

New in 0.3.0. Five standard NeoForge events (`NeoForge.EVENT_BUS`), meant for an addon to react
to — or veto — something happening inside Zenkai without per-tick polling or Mixin:

| Event | Posted | Cancelable | What cancelling it means |
|---|---|---|---|
| `RaceChosenEvent` | After a player's race is set | No | — (informational) |
| `FormChangedEvent` | Before a transformation change is applied | Yes | The player stays in the previous form |
| `TechniqueFiredEvent` | Before a technique (ki or physical) takes effect | Yes | The whole technique is aborted (no cost, no damage) |
| `KiConsumedEvent` | Before the ki cost of a shot is charged | Yes (and mutable: `setAmount`) | The shot is free, without aborting the technique |
| `MasterPurchaseEvent` | Before TP is charged for an in-person purchase from a master | Yes | The purchase is rejected |

Each event carries the `Player`/`ServerPlayer` involved and the minimal data of the action
(form `ResourceLocation`, lowercase technique id, master id...). Subscribing is standard
NeoForge: `@SubscribeEvent` on a method that takes the event type, registered on your own mod
event bus or via `NeoForge.EVENT_BUS.addListener(...)`.

## 3. Techniques: numbers and shape in datapacks, identity in Java (on purpose)

Since 0.3.0, `TechniqueDef` (the JSON in `zenkai_techniques/ki|physical/`) covers both the usual
NUMBERS (cost, damage, cooldown...) and the SHAPE that used to live in Java switches inside
`KiTechniqueType`: projectile size (`projectile_size_base`/`_step`), explosion radius
(`explosion_radius_mult`), area distribution (`aoe_factor`/`aoe_edge_falloff`), whether the
projectile travels (`travels`), whether the charge previews the real size
(`charge_shows_real_size`), which animation it forces (`anim_override`) and which effects it
allows (`allowed_effects`). A custom `icon` (a `ResourceLocation` of a 20×20 PNG) replaces the
fixed cell of the shared atlas.

This makes any of the 16 `KiTechniqueType`s / 4 `PhysicalTechnique`s **fully reconfigurable by
datapack** — including the already-supported "signature technique" path (`master` in the JSON
ties a technique to a specific master, with its own color/animation/sound; see the `TechniqueDef`
javadoc).

**What stays closed, on purpose**: `KiTechniqueType`/`PhysicalTechnique` remain Java enums. An
addon can re-tune or reskin (via signature technique) any existing type, but cannot create a
genuinely new technique identity without recompiling Zenkai. This is a 0.3.0 scope decision, not
an oversight — opening the enum into a real registry is a major piece of work, evaluated and
deferred.

## 4. What stays closed, and why

| Closed | Reason |
|---|---|
| `Race` (enum) | Mechanical identity of each race, hardcoded on purpose (`RacePassives`) — opening it means redesigning the entire visual pipeline, a project of its own. |
| `ZenkaiAttributes` (enum) | Stat system design (6 fixed values), not a content list. |
| `KiTechniqueType`/`PhysicalTechnique` (enum) | See §3. |
| `TechniqueDef.Kind`, `FormDef.Kind`, `AlignmentTier`, `MasterManager.Result` | They categorize HOW an already-defined system works, not content itself. |

## 5. Standard NeoForge, nothing Zenkai-specific

Custom packets (`RegisterPayloadHandlersEvent`, `event.registrar(YOUR_MOD_ID)`), new
`AttachmentType`s (your own `DeferredRegister<AttachmentType<?>>`) and config (your own
`ModConfigSpec`) are 100% standard NeoForge — none of it goes through `ModNetworking` or any
Zenkai registry.

## 6. Ki damage: tag, not namespace

`RacePassives` decides whether a `DamageSource` counts as "ki damage" (Majin mitigation, which
only absorbs physical damage) by checking the vanilla tag `zenkai:ki_damage`
(`data/zenkai/tags/damage_type/ki_damage.json`). An addon with its own energy `DamageType` only
needs its own tag file (`data/<your_modid>/tags/damage_type/ki_damage.json`, standard tag
merging) for the Majin to treat it as energy — zero Java.
