# Construir un addon sobre Zenkai

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

En todos los casos: el `id` del archivo tiene que coincidir con un valor **ya existente** del
enum/cadena correspondiente (excepto `zenkai_forms`, `zenkai_archetypes` y `zenkai_masters`, que
son verdaderamente abiertos — cualquier `id` nuevo funciona). Un archivo cuyo `id` no encaja en
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
