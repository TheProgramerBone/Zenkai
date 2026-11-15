# Construir un addon sobre Zenkai

> 🇬🇧 [English version below](#english)
>
> 💬 ¿Dudas o quieres enseñar tu addon? Únete al [Discord de Zenkai](https://discord.com/invite/mKD2RCqqsP).

Este documento describe el contrato de extensibilidad de Zenkai para Minecraft 1.21.1 (NeoForge
`21.1.233`+) desde la versión **0.3.0** (al día con la 0.6.0). Es un contrato *soportado*: lo que aquí se describe no
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
| `zenkai_forms/<id>.json` | Una transformación nueva colgada de una cadena existente (`parent`). `fur_rgb` (solo Saiyan) activa la capa de pelaje de torso y brazos con ese color: una forma propia tipo SSJ4 gris no necesita texturas nuevas. `no_brows: true` quita las cejas mientras la forma está activa (el SSJ3 las lleva así) | `FormDef`/`FormRegistry` |
| `zenkai_archetypes/<nombre>.json` | Un arquetipo de entidad nuevo (forma de atributos + multiplicadores) | `EntityArchetype`/`EntityArchetypeManager` |
| `zenkai_face_parts/<categoría>/<nombre>.json` | Un rasgo facial nuevo (ojos, cejas, nariz, boca): capas PNG con su fuente de color, razas, variante por raza | `FacePartDef`/`FaceCatalog` |
| `zenkai_hair_styles/<nombre>.json` | Un peinado nuevo (geo de cubos o textura plana), razas, variante por transformación | `HairStyleDef`/`HairCatalog` |
| `zenkai_hair_packs/<pack>.json` | Una CARPETA de la biblioteca de pelo con sus peinados en un solo archivo: nombre, ícono, formato DMZ/DBC por peinado, razas, variantes. Ver §1b | `HairPacks`/`HairFolderDef` |
| `zenkai_skill_tree/<archivo>.json` | Datos de un nodo del skill tree de transformaciones (`"node"`: el id de la forma o `zenkai:node/...`): requisitos extra (`pl`, `stat`, `mastery`, `alignment`, `flag`, `skill`, `node`, `any_of`), pista (`hint`), nodo secreto con `reveal`, vías de adquisición (`acquisition`: `tp`, `ritual`…; cualquier vía distinta de `tp` regala el nodo) y, para nodos con niveles propios como las pasivas de raza, `max_level`/`tp_cost`/`mind_req`/`values`. Ver §1c | `SkillTreeData`/`SkillTreeRequirement` |
| `zenkai_ki_weapons/<id>.json` | Definición de un arma de ki | `KiWeaponDef` |
| `zenkai_ki_projectiles/<id>.json` | Qué proyectiles aceptan Ki Infuse y cómo | ver javadoc del cargador |
| `zenkai_aura_signatures/<tipo>.json` | Forma visual del aura por tipo (shader) | ver javadoc del cargador |
| `zenkai_vfx_effects/<id>.json` | Efectos visuales de técnicas (p. ej. impactos físicos) | ver javadoc del cargador |
| `zenkai_alignment/` | Umbrales y efectos de alineamiento | ver javadoc del cargador |
| `zenkai_instant_transmission_blocklist/` | Objetivos a los que la Transmisión Instantánea no puede fijarse | ver javadoc del cargador |
| `zenkai_scouter_upgrades/`, `zenkai_generator_fuels/` | Costes de mejora del scouter y combustibles del generador | ver javadoc del cargador |
| `zenkai_body_options/` | Opciones de cuerpo y cabeza de la pantalla de Apariencia | ver javadoc del cargador |

En todos los casos: el `id` del archivo tiene que coincidir con un valor **ya existente** del
enum/cadena correspondiente (excepto `zenkai_forms`, `zenkai_archetypes`, `zenkai_masters`,
`zenkai_face_parts` y `zenkai_hair_styles`, que son verdaderamente abiertos — cualquier `id`
nuevo funciona). Las texturas/geo de rasgos y peinados van en `assets/<tu_modid>/`; su nombre
visible, en la clave de lang `face_part.<ns>.<categoría>.<nombre>` o `hair_style.<ns>.<nombre>`.
Formato completo de los campos en el javadoc de `FacePartDef.parse` y `HairStyleDef.parse`. Un archivo cuyo `id` no encaja en
ningún enum se ignora con un aviso en el log, nunca crashea.

### 1b. Packs de peinados (`zenkai_hair_packs`)

Para añadir muchos peinados sin escribir un json por cada uno. Un archivo = una carpeta en la
pestaña Pelo, con su nombre, su ícono y sus peinados. Ejemplo completo y funcional en
[`examples/hair_pack_ejemplo/`](examples/hair_pack_ejemplo/) (copiarlo a `<juego>/zenkai/hair_packs/`).

```json
// data/<tu_modid>/zenkai_hair_packs/<pack>.json  ->  carpeta <tu_modid>:<pack>
{
  "name": "Mi pack",                 // literal; sin él: lang hair_folder.<ns>.<pack>, o el id
  "icon": "<ns>:textures/gui/hair_folders/otro.png",   // opcional (defecto: .../hair_folders/<pack>.png)
  "format": "auto",                  // auto | dmz | dbc: formato por defecto de los códigos
  "races": ["human", "saiyan"],      // por defecto de los peinados (ausente = cualquier raza)
  "sort": 100,                       // orden de la carpeta
  "styles": [
    {"id": "puas", "name": "Púas", "format": "dmz", "code": "DMZF22:...",
     "sort": 10, "races": [...], "exclude_races": [...], "default_for": [...],
     "form_overrides": {"zenkai:ssj1": "puas_ssj"}},   // id sin ":" = de este mismo pack
    {"id": "melena", "name": "Melena", "format": "dbc", "code": "5050...786 dígitos...20"},
    {"id": "cubos", "name": "De cubos", "model": "<tu_modid>:geo/hair/x.geo.json"}   // geo de Blockbench; sin "format"
  ]
}
```
- Un mismo pack puede traer solo DMZ, solo DBC (HairSalon de JRMCore) o ambos. `format` se puede poner por
  pack y por peinado; si NO coincide con el código, ese peinado se descarta con un aviso en el log (`Zenkai-HairCatalog`).
  Con `auto` se detecta solo.
- Id de cada peinado: `<tu_modid>:<pack>/<id>`. Un código DMZ de juego completo (`DMZF…`) trae solo sus variantes
  SSJ/SSJ2/SSJ3/SSJ4; un código DBC suelto genera SSJ1/SSJ2 aproximados (los mechones llevan su reacción al SSJ).
  `form_overrides` solo hace falta para apuntar una forma a OTRO peinado.
- El **ícono** es un asset de cliente (PNG cuadrado, 64×64 recomendado) en `assets/<tu_modid>/textures/gui/hair_folders/`.
  Sin él se usa el ícono genérico. Datos y assets deben ir en el mismo zip/mod o en `hair_packs/` (sirve a los dos lados).
- `/reload` carga los datos y F3+T los assets, sin reiniciar. Los peinados sueltos de `zenkai_hair_styles` con `"folder"`
  siguen funcionando.
- **Licencia:** no redistribuyas códigos de Dragon Block C que no sean tuyos (JinRyuu lo prohíbe) ni de DragonMineZ sin
  respetar su GPLv3. Los del ejemplo son propios.

**Conflictos entre datapacks**: `FormRegistry` avisa por log (`Zenkai-Forms`) si dos datapacks
compiten por la raíz de la misma raza — gana el primero en cargar, igual que cualquier otro
registro de Minecraft, pero ahora queda registrado en vez de fallar en silencio.

### 1c. Nodos del skill tree (`zenkai_skill_tree`)

El árbol se construye solo a partir de `zenkai_forms` (`parent`, `tp_cost`): una forma nueva de un
addon ya tiene nodo sin escribir nada más. El JSON del árbol solo AÑADE cosas a ese nodo:

```json
{
  "node": "mi_addon:ssj_rage",
  "requirements": [
    {"type": "mastery", "form": "zenkai:ssj2", "min": 50},
    {"type": "any_of", "of": [{"type": "flag", "flag": "beast_fury"}, {"type": "pl", "min": 500000}]}
  ],
  "secret": true,
  "reveal": [{"type": "flag", "flag": "ssj4_ritual"}],
  "acquisition": ["tp", "ritual"]
}
```

Banderas disponibles en `flag`: `ssj4_ritual`, `reached_super_oozaru`, `beast_fury`, `divine`,
`legendary`, `immortal`, `god_ki_audience`, `tail` (una desconocida da false). La pista se lee de
`skill_tree.<ns>.<ruta>.hint` (o de la clave que diga `"hint"`). Las formas baneadas en
`banned_forms.json` no tienen nodo. Un archivo roto se ignora con un error en el log.

**Ramas de addon (varios datapacks, mismo padre).** Una forma de TU namespace (no `zenkai`) que cuelga de otra y no tiene
`mastery_reqs` se compra como **nodo propio e independiente**: paga su `tp_cost`, exige tener el padre y los requisitos del
JSON, y NUNCA entra en la cadena lineal de `super_forms`. Dos datapacks de autores distintos pueden colgar formas del mismo
padre (p. ej. «SSJ5» y «SSJ4 Full Power» de SSJ4): son hermanas, se poseen por separado y no se excluyen. Una forma colgada de
OTRA de tu pack (SSJ6 de SSJ5) forma su propia cadena. Las formas con `divine_tier` de un addon igual: su grupo de empate es
su namespace, así que no empatan con la pareja Blue/Rosé del mod (pero cuelgan de Blue: un jugador Rosé no las ve). Nombres:
`form.<tu_namespace>.<id>` (lang) y pista `skill_tree.<tu_namespace>.<id>.hint`; el tooltip del nodo muestra de qué pack viene.
Un padre inexistente o un ciclo se avisan en el log (`Zenkai-Forms`) y la forma no tendrá nodo.

**Icono, pelo y aspecto de una forma (campos del JSON de `zenkai_forms`).**
- `icon_default`: id de una forma cuyo icono se presta (`"zenkai:ssj1"`). El icono se busca en este orden: (1) tu propia textura
  `assets/<tu_ns>/textures/gui/form_icons/<id>.png` (32x32; también la HUD la usa, y un resource pack puede cambiar con ella un
  icono del mod), (2) la celda del atlas si la forma es del mod, (3) `icon_default` (se sigue hasta 2 saltos), (4) el «?» teñido
  con el color del aura.
  **Cómo organizar un icono propio (sin tocar el JSON):** el PNG se llama como la forma y va en tu pack, en
  `assets/<tu_ns>/textures/gui/form_icons/<id>.png` (la forma `mi_pack:ssj7` → `assets/mi_pack/textures/gui/form_icons/ssj7.png`).
  Cuadrado, 32x32 recomendado (vale 16, 64... se escala a 16 px de GUI), fondo transparente y sin halo, como los del mod; un
  archivo no cuadrado se avisa en el log. Con él puesto, `icon_default` ni se mira; sin él, vuelve a `icon_default`. Para
  verlo: F3+T tras copiarlo. Plantilla: `zenkai_builtin_packs/af_saiyan_transformations/.../form_icons/ssj6.png`.
- `hair_style`: o bien un peinado (`"zenkai:classic_ssj"`) o bien el id de OTRA FORMA (`"zenkai:ssj3"` = «el pelo que SSJ3 le da a
  quien lleve este peinado»: sirve con classic y con los peinados DMZ). Solo vale si el peinado base no tiene ya un override para
  tu forma; el color sale de `hair_rgb`. Sin este campo habría que reescribir el JSON del peinado base (`form_overrides`), que dos
  datapacks no pueden tocar a la vez.
- `long_scene` (0.6.1): `{"pose": "serene"}` elige la pose de la secuencia larga de la primera transformación. Valores de Zenkai:
  `kneel`, `roar`, `clutch`, `resolve`, `ssj3_long`, `ssj4`, `ikari`, `serene`, `guard_charge`, `rose`, `pride`, `skyward`, `flex`,
  `cocoon`, `crossed`, `black`, `kata`; con namespace
  (`"mi_pack:mi_clip"`) es el id PAL de un clip tuyo en `assets/<ns>/player_animations/` (unos 6,3 s, estallido a los 4 s). Sin el
  campo: de rodillas si la despertó un duelo y rugido si no. El rescate de un aliado siempre usa `resolve`.
- `transform_face` (0.6.1): cara mientras alguien se transforma HACIA tu forma. Sin el campo, grita (boca abierta); `"calm"` = boca
  cerrada; `"serene"` = boca y ojos cerrados (los usa el Ultra Instinto).
- Ya existentes: `aura_type` (estilo en `assets/<ns>/zenkai_aura_styles/<tipo>.json`, con su bloque `silhouette` = el humo, y firma
  en `data/<ns>/zenkai_aura_signatures/<tipo>.json`), `aura_rgb`/`aura_outer_rgb`, `hair_rgb`, `eye_rgb`, `brow_rgb`, `fur_rgb`,
  `tail_rgb`, `layer_colors`, `scale`, `body_items`; poder y gasto: `stat_percent_*`, `ki_drain_*`, `mastery_gain`, `mastery_reqs`,
  `spi_req`, `hold_ticks`, `overdrive_*`; exclusividad de raza: `races` (sin lista = todas; una forma universal solo para los
  namekianos lleva `"races": ["namekian"]`).

**Packs de ejemplo integrados.** El jar lleva dos datapacks DESACTIVADOS por defecto, copiables como plantilla:
`zenkai_builtin_packs/extra_transformations` (SSJ Blue 2, SSJ Blue 3, SSJ4 Full Power) y
`zenkai_builtin_packs/af_saiyan_transformations` (SSJ5, SSJ6). Al crear un mundo, actívalos en la pantalla de datapacks
(«Zenkai Example: …»); en un mundo existente: `/datapack enable "mod/zenkai:zenkai_builtin_packs/extra_transformations"`.
Para llevar un datapack así en tu propio mod: carpeta con `pack.mcmeta` en tus recursos y
`AddPackFindersEvent#addPackFinders(ResourceLocation, PackType.SERVER_DATA, nombre, PackSource.BUILT_IN, false, Pack.Position.TOP)`
(`alwaysActive=false` = aparece apagado; el pack de assets con lang va aparte, `true`). Ver `BuiltinExamplePacks`.

## 2. Bus de eventos (`com.hmc.zenkai.api.event`)

Nuevo en 0.3.0 (ampliado en la 0.6.0 hasta diecinueve). Eventos NeoForge estándar (`NeoForge.EVENT_BUS`), pensados para que un
addon reaccione a — o vete — algo que pasa dentro de Zenkai sin polling por tick ni Mixin:

| Evento | Se postea en | Cancelable | Qué significa cancelarlo |
|---|---|---|---|
| `RaceChosenEvent` | Tras fijar la raza de un jugador | No | — (informativo) |
| `FormChangedEvent` | Antes de aplicar un cambio de transformación | Sí | El jugador se queda en la forma anterior |
| `TechniqueFiredEvent` | Antes de que una técnica (ki o física) surta efecto | Sí | La técnica entera se aborta (sin coste, sin daño) |
| `KiConsumedEvent` | Antes de cobrar el ki de un disparo | Sí (y mutable: `setAmount`) | El disparo sale gratis, sin abortar la técnica |
| `MasterPurchaseEvent` | Antes de cobrar TP por una compra en persona ante un maestro | Sí | La compra se rechaza |
| `SkillTreeNodePurchaseEvent` | Con un nodo del skill tree ya confirmado como comprable, antes de cobrar | Sí | La compra se rechaza sin coste |
| `TPGainedEvent` | Al ganar TP entrenando (golpes, kills, Meditación, Target Practice), antes de sumarlo | Sí (y mutable: `setAmount`) | No se suma nada. NO salta al gastar TP, devolverlo, comandos ni deseos |
| `WishGrantedEvent` | Cuando Shenlong concede un deseo (efecto ya aplicado) | No | — (informativo; trae el texto del deseo) |
| `PlayerDownedEvent` | Un jugador cae derribado (aún no ha muerto), una vez por caída | No | — (trae testigos de su party y cuándo expira la ventana) |
| `PlayerRescuedEvent` | Otro jugador levanta (revivir o senzu) a uno derribado dentro de su ventana | No | — (trae quién, a quién, método y ticks restantes) |
| `PlayerBodyCreatedEvent` | Nace el cuerpo de un jugador muerto | No | — (indica si la muerte permanente estaba activa) |
| `PlayerBodyInteractEvent` | Alguien abre, carga, entierra o desintegra un cuerpo | No | — (`Kind`: `OPEN`/`CARRY`/`BURY`/`DISINTEGRATE`; si es propio o de un aliado) |
| `PartyMemberDiedEvent` | Muere un jugador y su party lo presencia (al menos un testigo) | No | — |
| `CriticalSurvivalEvent` | Un jugador sobrevive al borde (<20 % de body) y se recupera (≥60 %) | No | — |
| `PlayerParryEvent` | Un jugador con la ventana de parry abierta va a parar un golpe o un proyectil, antes de resolverlo | Sí | No hay parry: el golpe o el proyectil sigue su curso (no salta con los parrys de NPC) |
| `AlignmentChangeEvent` | Antes de cambiar el alineamiento por matar o enterrar un cuerpo (y por `AlignmentManager.change`) | Sí (y mutable: `setDelta`) | No cambia nada. NO salta al fijarlo con comandos o reencarnación |
| `GodKiGrantedEvent` | Un jugador recibe God Ki (prueba, ritual o `GodKi.grantFromAddon`) | No | — (trae la vía: `master_trial`, `ritual`, `event`…) |
| `RageResolvedEvent` | El sistema de Rage Forms termina de evaluar un hecho | No | — (trae detector, intensidad y resultado `AWAKENING`/`PROGRESS`/`RECORDED`) |
| `MajinAbsorbEvent` | La masa de un Majin alcanza a un objetivo absorbible, antes de matarlo (la raza se estrena en la 0.7.0) | Sí | La absorción no ocurre |

Cada evento lleva el `Player`/`ServerPlayer` implicado y los datos mínimos de la acción
(`ResourceLocation` de forma, id de técnica en minúsculas, id de maestro...). Suscribirse es
NeoForge estándar: `@SubscribeEvent` sobre un método que reciba el tipo de evento, registrado en
tu propio mod event bus o vía `NeoForge.EVENT_BUS.addListener(...)`.

### 2b. Deseos propios para Shenlong y Porunga (`com.hmc.zenkai.api.wish`)

Un addon puede añadir deseos a la lista del dragón sin tocar Zenkai: implementa `CustomWish` y regístralo con
`WishRegistry.register(...)` en `FMLCommonSetupEvent` (dentro de `event.enqueueWork`). Aparece al final de la lista,
tras «Revivir mascota». Ejemplo mínimo:

```java
public final class RichesWish implements CustomWish {
    public ResourceLocation id() { return ResourceLocation.fromNamespaceAndPath("mi_addon", "riquezas"); }
    public Component name() { return Component.translatable("mi_addon.wish.riquezas"); }   // clave de TU lang
    public ResourceLocation iconItem() { return ResourceLocation.withDefaultNamespace("emerald"); }
    // Opcional: solo con las esferas de Namek, y solo si el jugador cumple algo.
    public Set<DragonKind> dragons() { return EnumSet.of(DragonKind.PORUNGA); }
    public boolean availableTo(ServerPlayer p, DragonKind d) { return p.experienceLevel >= 5; }
    public Optional<Component> grant(ServerPlayer p, DragonKind d) {
        p.getInventory().add(new ItemStack(Items.EMERALD, 64));
        return Optional.of(Component.translatable("mi_addon.wish.riquezas.desc"));   // texto que se difunde
    }
}
```

- **Shenlong o Porunga:** `dragons()` filtra por `DragonKind.SHENLONG` (esferas de la Tierra) o `DragonKind.PORUNGA`
  (esferas de Namek). Hoy los dos usan el modelo de Shenlong, pero el tipo ya viaja con la entidad: cuando Porunga
  tenga su propia entidad, tu deseo seguirá funcionando sin cambios. Por defecto vale para los dos.
- **La validación es del servidor.** `grant` solo se llama con una sesión de deseo abierta (dragón vivo, con deseos en
  el pool y a menos de 32 bloques), con el deseo registrado, el dragón adecuado y `availableTo` en verdadero. La
  pantalla solo muestra el botón; un cliente modificado no se salta nada.
- **Resultado:** `Optional.of(descripción)` = concedido (Zenkai cierra la sesión, descuenta un deseo del pool, avisa a
  los cercanos y dispara el logro `WISH_GRANTED` con tu id como nombre del deseo). `Optional.empty()` = rechazado: no
  se gasta nada y tú avisas al jugador. Una excepción cuenta como rechazo y queda en el log.
- **Un clic, sin confirmación:** el botón envía la petición al pulsarlo. Si tu deseo es irreversible o caro,
  comprueba tú las condiciones en `availableTo`/`grant`.
- Para reaccionar a CUALQUIER deseo concedido (los de Zenkai incluidos) usa `WishGrantedEvent`.

### 2c. Más puertas de entrada para addons

Todas viven en `com.hmc.zenkai.api.*`, se usan solo en servidor y revalidan por su cuenta.

- **Servicios de maestro** (`MasterServiceRegistry.register(masterId, servicio)`): añade una fila a la pestaña «Servicios» de un maestro
  (`kami`, `korin`, `kaio`, `toribot` o el de un maestro de otro addon). Implementas `MasterService` (`id`, `label`, `available`,
  `tooltip`, `claim`); `claim` revalida siempre. Usa un id con tu namespace: si el id ya existe en ese maestro, se ignora el nuevo.
- **Rage Forms** (`RageApi.trigger(jugador, intensidad, eventId, related)`): dispara el detector `addon`. Qué despierta lo deciden los
  datos, con un JSON en `zenkai_rage_awakenings/` que lleve `"triggers": ["addon"]`. Se aplican las reglas de siempre: sin saltos en la
  cadena, una recompensa por `eventId` y gasta el enfriamiento de rage, así que un addon no puede encadenar despertares. El texto
  `related` sale en la Crónica del jugador.
- **TP de entrenamiento** (`TrainingApi.award(jugador, tpBruto)`): pasa por la misma fatiga, pesas, HTC y bonos que los minijuegos,
  con su propia categoría `addon`, y salta `TPGainedEvent`. La cifra la decide tu addon: ponle un techo por sesión.
- **God Ki** (`GodKi.grantFromAddon(jugador)`): lo concede por la vía `event` si aún no lo tiene (no comprueba cadena mortal ni
  alineamiento: eso lo decides tú) y salta `GodKiGrantedEvent`.
- **Alineamiento** (`AlignmentManager.change(jugador, delta, motivo)`): la ruta que usan matar y enterrar; los cambios de tu addon
  pasan por `AlignmentChangeEvent` como los de Zenkai.

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

> 💬 Questions, or want to show off your addon? Join the [Zenkai Discord](https://discord.com/invite/mKD2RCqqsP).

This document describes Zenkai's extensibility contract for Minecraft 1.21.1 (NeoForge
`21.1.233`+) since version **0.3.0** (up to date with 0.6.0). It is a *supported* contract: what is described here does
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
| `zenkai_forms/<id>.json` | A new transformation hanging off an existing chain (`parent`). `fur_rgb` (Saiyan only) turns on the torso/arm fur layer in that color: a custom grey SSJ4-like form needs no new textures. `no_brows: true` hides the eyebrows while the form is active (SSJ3 uses it). `long_scene: {"pose": "serene"}` (0.6.1) picks the pose of the first-transformation long sequence (`kneel`, `roar`, `clutch`, `resolve`, `ssj3_long`, `ssj4`, `ikari`, `serene`, `guard_charge`, `rose`, `pride`, `skyward`, `flex`, `cocoon`, `crossed`, `black`, `kata`, or a namespaced id of your own PAL clip, ~6.3 s with the burst at 4 s); an ally rescue always uses `resolve`. `transform_face` (0.6.1): face while transforming INTO the form: none = scream, `"calm"` = closed mouth, `"serene"` = closed mouth and eyes (Ultra Instinct uses them) | `FormDef`/`FormRegistry` |
| `zenkai_archetypes/<name>.json` | A new entity archetype (attribute shape + multipliers) | `EntityArchetype`/`EntityArchetypeManager` |
| `zenkai_face_parts/<category>/<name>.json` | A new face feature (eyes, eyebrows, nose, mouth): PNG layers with their color source, races, per-race variant | `FacePartDef`/`FaceCatalog` |
| `zenkai_hair_styles/<name>.json` | A new hairstyle (cube geo or flat texture), races, per-transformation variant | `HairStyleDef`/`HairCatalog` |
| `zenkai_hair_packs/<pack>.json` | A hair-library FOLDER with its hairstyles in a single file: name, icon, per-style DMZ/DBC format, races, variants. See §1b | `HairPacks`/`HairFolderDef` |
| `zenkai_skill_tree/<file>.json` | Data for a transformation skill-tree node (`"node"`: the form id or `zenkai:node/...`): extra requirements (`pl`, `stat`, `mastery`, `alignment`, `flag`, `skill`, `node`, `any_of`), a hint, secret nodes with `reveal`, acquisition routes (`acquisition`: `tp`, `ritual`…; any route other than `tp` grants the node for free) and, for nodes with their own levels such as racial passives, `max_level`/`tp_cost`/`mind_req`/`values` | `SkillTreeData`/`SkillTreeRequirement` |
| `zenkai_ki_weapons/<id>.json` | A ki weapon definition | `KiWeaponDef` |
| `zenkai_ki_projectiles/<id>.json` | Which projectiles accept Ki Infuse and how | see the loader's javadoc |
| `zenkai_aura_signatures/<type>.json` | Per-type visual aura shape (shader) | see the loader's javadoc |
| `zenkai_vfx_effects/<id>.json` | Technique visual effects (e.g. physical impacts) | see the loader's javadoc |
| `zenkai_alignment/` | Alignment thresholds and effects | see the loader's javadoc |
| `zenkai_instant_transmission_blocklist/` | Targets Instant Transmission cannot lock onto | see the loader's javadoc |
| `zenkai_scouter_upgrades/`, `zenkai_generator_fuels/` | Scouter upgrade costs and generator fuel values | see the loader's javadoc |
| `zenkai_body_options/` | Body and head options of the Appearance screen | see the loader's javadoc |

In every case the file's `id` must match an **already existing** value of the corresponding
enum/chain (except `zenkai_forms`, `zenkai_archetypes`, `zenkai_masters`, `zenkai_face_parts`
and `zenkai_hair_styles`, which are truly open — any new `id` works). Face/hair textures and geo
go under `assets/<your_modid>/`; their display name in the lang key
`face_part.<ns>.<category>.<name>` or `hair_style.<ns>.<name>`. Full field format in the javadoc
of `FacePartDef.parse` and `HairStyleDef.parse`. A file whose `id` fits no enum is ignored with a log warning, and
never crashes.

### 1b. Hair packs (`zenkai_hair_packs`)

For adding many hairstyles without one json per style. One file = one folder in the Hair tab, with
its name, its icon and its styles. A full working example lives in
[`examples/hair_pack_ejemplo/`](examples/hair_pack_ejemplo/) (copy it to `<game>/zenkai/hair_packs/`).

```json
// data/<your_modid>/zenkai_hair_packs/<pack>.json  ->  folder <your_modid>:<pack>
{
  "name": "My pack",                 // literal; without it: lang hair_folder.<ns>.<pack>, or the id
  "icon": "<ns>:textures/gui/hair_folders/other.png",  // optional (default: .../hair_folders/<pack>.png)
  "format": "auto",                  // auto | dmz | dbc: default format of the codes
  "races": ["human", "saiyan"],      // default for the styles (absent = any race)
  "sort": 100,                       // folder order
  "styles": [
    {"id": "spikes", "name": "Spikes", "format": "dmz", "code": "DMZF22:...",
     "sort": 10, "races": [...], "exclude_races": [...], "default_for": [...],
     "form_overrides": {"zenkai:ssj1": "spikes_ssj"}},  // an id without ":" belongs to this same pack
    {"id": "mane", "name": "Mane", "format": "dbc", "code": "5050...786 digits...20"},
    {"id": "blocks", "name": "Cubes", "model": "<your_modid>:geo/hair/x.geo.json"}   // Blockbench geo; no "format"
  ]
}
```
- One pack may carry DMZ only, DBC only (JRMCore's HairSalon) or both. `format` can be set per pack and per
  style; if it does NOT match the code, that style is dropped with a log warning (`Zenkai-HairCatalog`). `auto` detects it.
- Each style's id is `<your_modid>:<pack>/<id>`. A full-set DMZ code (`DMZF…`) carries its own SSJ/SSJ2/SSJ3/SSJ4
  variants; a single DBC code yields approximated SSJ1/SSJ2 (its strands carry their SSJ reaction).
  `form_overrides` is only needed to point a form at ANOTHER style.
- The **icon** is a client asset (square PNG, 64×64 recommended) under `assets/<your_modid>/textures/gui/hair_folders/`.
  Without it the generic icon is used. Data and assets must ship in the same zip/mod, or in `hair_packs/` (serves both sides).
- `/reload` loads the data and F3+T the assets, no restart. Loose `zenkai_hair_styles` entries with `"folder"` keep working.
- **Licensing:** do not redistribute Dragon Block C codes that are not yours (JinRyuu forbids it), nor DragonMineZ ones
  without honouring its GPLv3. The example's codes are original.

**Datapack conflicts**: `FormRegistry` logs a warning (`Zenkai-Forms`) if two datapacks compete
for the root of the same race — the first one to load wins, like any other Minecraft registry,
but it is now logged instead of failing silently.

## 2. Event bus (`com.hmc.zenkai.api.event`)

New in 0.3.0 (grown to nineteen in 0.6.0). Standard NeoForge events (`NeoForge.EVENT_BUS`), meant for an addon to react
to — or veto — something happening inside Zenkai without per-tick polling or Mixin:

| Event | Posted | Cancelable | What cancelling it means |
|---|---|---|---|
| `RaceChosenEvent` | After a player's race is set | No | — (informational) |
| `FormChangedEvent` | Before a transformation change is applied | Yes | The player stays in the previous form |
| `TechniqueFiredEvent` | Before a technique (ki or physical) takes effect | Yes | The whole technique is aborted (no cost, no damage) |
| `KiConsumedEvent` | Before the ki cost of a shot is charged | Yes (and mutable: `setAmount`) | The shot is free, without aborting the technique |
| `MasterPurchaseEvent` | Before TP is charged for an in-person purchase from a master | Yes | The purchase is rejected |
| `SkillTreeNodePurchaseEvent` | With a skill tree node already confirmed purchasable, before charging | Yes | The purchase is rejected at no cost |
| `TPGainedEvent` | When TP is earned by training (hits, kills, Meditation, Target Practice), before it is added | Yes (and mutable: `setAmount`) | Nothing is added. Does NOT fire on spending, refunds, commands or wishes |
| `WishGrantedEvent` | When Shenlong grants a wish (effect already applied) | No | — (informational; carries the wish text) |
| `PlayerDownedEvent` | A player falls downed (not dead yet), once per fall | No | — (carries party witnesses and when the window expires) |
| `PlayerRescuedEvent` | Another player lifts a downed one (revive or senzu) inside its window | No | — (carries who, whom, method and ticks left) |
| `PlayerBodyCreatedEvent` | A dead player's body is created | No | — (says whether permanent death was on) |
| `PlayerBodyInteractEvent` | Someone opens, carries, buries or disintegrates a body | No | — (`Kind`: `OPEN`/`CARRY`/`BURY`/`DISINTEGRATE`; own or ally) |
| `PartyMemberDiedEvent` | A player dies and their party witnesses it (at least one witness) | No | — |
| `CriticalSurvivalEvent` | A player survives the brink (<20 % body) and recovers (≥60 %) | No | — |
| `PlayerParryEvent` | A player with the parry window open is about to parry a hit or projectile, before it resolves | Yes | No parry: the hit or projectile goes on (does not fire for NPC parries) |
| `AlignmentChangeEvent` | Before alignment changes from a kill or burying a body (and from `AlignmentManager.change`) | Yes (and mutable: `setDelta`) | Nothing changes. Does NOT fire when set by commands or reincarnation |
| `GodKiGrantedEvent` | A player receives God Ki (trial, ritual or `GodKi.grantFromAddon`) | No | — (carries the route: `master_trial`, `ritual`, `event`…) |
| `RageResolvedEvent` | The Rage Forms system finishes evaluating a fact | No | — (carries detector, intensity and result `AWAKENING`/`PROGRESS`/`RECORDED`) |
| `MajinAbsorbEvent` | A Majin's mass hits an absorbable target, before killing it (the race debuts in 0.7.0) | Yes | The absorption does not happen |

Each event carries the `Player`/`ServerPlayer` involved and the minimal data of the action
(form `ResourceLocation`, lowercase technique id, master id...). Subscribing is standard
NeoForge: `@SubscribeEvent` on a method that takes the event type, registered on your own mod
event bus or via `NeoForge.EVENT_BUS.addListener(...)`.

### 2b. Custom wishes for Shenron and Porunga (`com.hmc.zenkai.api.wish`)

An addon can add wishes to the dragon's list without touching Zenkai: implement `CustomWish` and register it with
`WishRegistry.register(...)` in `FMLCommonSetupEvent` (inside `event.enqueueWork`). It shows up at the end of the list,
after "Revive pet". Minimal example:

```java
public final class RichesWish implements CustomWish {
    public ResourceLocation id() { return ResourceLocation.fromNamespaceAndPath("my_addon", "riches"); }
    public Component name() { return Component.translatable("my_addon.wish.riches"); }   // key from YOUR lang
    public ResourceLocation iconItem() { return ResourceLocation.withDefaultNamespace("emerald"); }
    // Optional: only with the Namek balls, and only if the player meets some condition.
    public Set<DragonKind> dragons() { return EnumSet.of(DragonKind.PORUNGA); }
    public boolean availableTo(ServerPlayer p, DragonKind d) { return p.experienceLevel >= 5; }
    public Optional<Component> grant(ServerPlayer p, DragonKind d) {
        p.getInventory().add(new ItemStack(Items.EMERALD, 64));
        return Optional.of(Component.translatable("my_addon.wish.riches.desc"));   // text broadcast to nearby players
    }
}
```

- **Shenron or Porunga:** `dragons()` filters by `DragonKind.SHENLONG` (Earth balls) or `DragonKind.PORUNGA` (Namek
  balls). Today both use Shenron's model, but the kind already travels with the entity: once Porunga gets its own
  entity your wish keeps working unchanged. Both by default.
- **Everything is validated on the server.** `grant` is only called with an open wish session (live dragon with wishes
  left in the pool, within 32 blocks), the wish registered, the right dragon and `availableTo` true. The screen only
  shows the button; a modified client skips nothing.
- **Result:** `Optional.of(description)` = granted (Zenkai closes the session, takes one wish from the pool, tells
  nearby players and fires the `WISH_GRANTED` advancement trigger with your id as the wish name). `Optional.empty()` =
  refused: nothing is spent and you tell the player. An exception counts as a refusal and is logged.
- **One click, no confirmation:** the button sends the request when pressed. If your wish is irreversible or
  expensive, check conditions yourself in `availableTo`/`grant`.
- To react to ANY granted wish (Zenkai's own included) use `WishGrantedEvent`.

### 2c. More entry points for addons

All live in `com.hmc.zenkai.api.*`, are server-only and re-validate on their own.

- **Master services** (`MasterServiceRegistry.register(masterId, service)`): adds a row to a master's "Services" tab (`kami`, `korin`,
  `kaio`, `toribot` or another addon's master). You implement `MasterService` (`id`, `label`, `available`, `tooltip`, `claim`);
  `claim` always re-validates. Use an id with your namespace: if the id already exists on that master, the new one is ignored.
- **Rage Forms** (`RageApi.trigger(player, intensity, eventId, related)`): fires the `addon` detector. What it awakens is decided by
  data, with a JSON in `zenkai_rage_awakenings/` carrying `"triggers": ["addon"]`. The usual rules apply: no skipping the chain, one
  reward per `eventId`, and it spends the rage cooldown, so an addon cannot chain awakenings. The `related` text shows in the
  player's Chronicle.
- **Training TP** (`TrainingApi.award(player, rawTp)`): goes through the same fatigue, weights, HTC and bonuses as the minigames,
  with its own `addon` category, and fires `TPGainedEvent`. The number is your addon's call: cap it per session.
- **God Ki** (`GodKi.grantFromAddon(player)`): grants it through the `event` route if the player does not have it yet (it checks
  neither the mortal chain nor alignment: that is up to you) and fires `GodKiGrantedEvent`.
- **Alignment** (`AlignmentManager.change(player, delta, reason)`): the route killing and burying use; your addon's changes go through
  `AlignmentChangeEvent` like Zenkai's.

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
