<div align="center">

# Zenkai

**A Dragon Ball–inspired combat overhaul for Minecraft 1.21.1 (NeoForge)**

🇬🇧 English · 🇪🇸 [Español](#español)

Races, stats, transformations, ki techniques and flight — built to sit *inside* Minecraft,
not to replace it.

</div>

---

## What Zenkai is

Zenkai adds a full progression layer on top of Minecraft: pick a race, train it, learn skills
and ki techniques, transform, and fight with a stat system that scales from your first punch
to the end of the power curve.

The design rule the whole mod follows: **nothing turns on until you opt in.**
Until you choose a race, Zenkai does not touch your game — no stats, no pools, no flight, no
altered combat. Once you have a race, melee is still vanilla until you switch Combat Mode on.
You decide when you are playing Minecraft and when you are playing Zenkai.

### Features

- **5 races** — Human, Saiyan, Namekian, Arcosian, Majin — each with three specialisations
  (Warrior / Martial Artist / Spiritualist) and their own stat curves.
- **Stat progression** — Training Points earned by fighting, spent across six attributes.
  Diminishing returns per session so grinding has a shape.
- **Transformations** — a form chain per race: Super Saiyan 1–4, Blue, Rose and God; Arcosian forms
  (including the Golden and Black lines and the Fifth Form); Human, Namekian and Majin god and
  super forms; Potential Unlock, Ultra Instinct, Ultra Ego and more shared forms; and the
  Saiyan-only Oozaru → Super Oozaru moonlight ritual (grows a real tail, needs a full moon).
  Each has mastery, ki drain and its own aura.
- **Ki techniques** — chargeable blasts, waves, discs, spirals, barriers, plus physical
  techniques (dash, barrage, kiai, heavy blow).
- **Skills** — Flight, Run, Kaioken, Instant Transmission, Ki Sense, Ki Control, Ki Fist, Ki Infuse,
  Ki Block, Meditation, God Ki and more.
- **Training minigames** — a Training hub with Shadow Training (spar your own shadow clone),
  Meditation and Target Practice, each with real difficulty scaling and Training Point rewards.
- **Environmental gravity** — Kaiosama's planet and the Hyperbolic Time Chamber apply real ambient
  gravity that scales training and stacks with weighted gear.
- **Dragon Balls** — Earth and Namek sets, radar, Shenlong and a wish system.
- **Masters** — Korin, Kami, Kaiosama and more, each offering skills, techniques and one-off
  services (senzu beans, growing/removing your tail, training weights).
- **Party system** — invite, accept, leave, kick, disband and a friendly-fire toggle for
  grouping up with other players.
- **Two dimensions** — Namek and the Otherworld, plus the Hyperbolic Time Chamber.
- **Space Pod** — a real, flyable ship for Earth↔Namek travel, with its own countdown and landing.
- **Gear** — Scouter (with upgrades), weighted training equipment (Curios), energy generator,
  senzu beans, Kintoun.

### Requirements

| Mod | Version |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | see `gradle.properties` |
| GeckoLib | 4.8.4+ |
| Player Animation Library (zigythebird fork) | 1.1.5+ |
| TerraBlender | 4.1.0.8+ |
| Curios API | 9.5.1+ |
| Create + Ponder | optional — adds in-game 3D tutorials when present |

---

## Configuration

Zenkai is heavily configurable — 140+ options across three files.

- `zenkai-server.toml` — where almost all of it lives: combat formulas, TP costs and economy,
  training rates, mastery, flight, ambient gravity (Kaiosama's planet, the Hyperbolic Time
  Chamber) and more. Per-world (lives in the save under `serverconfig/`) and synced to every
  client automatically, so what you tune is exactly what players see and fight under.
- `zenkai-common.toml` — per-installation only, and deliberately almost empty: a couple of
  dev-only knobs, no gameplay balance. (It used to hold most of the numbers above; they were
  migrated to `zenkai-server.toml` because a per-installation, unsynced file is the wrong place
  for anything a client needs to see correctly — it caused a real desync bug before the move.)
- `zenkai-client.toml` — HUD and visual preferences.

The gamerule `zenkaiEnableRaceBoosts` disables the entire combat layer at runtime without
uninstalling anything.

If you want Zenkai's progression but not its combat overhaul, or vice versa, the config can get
you most of the way there. Start with the `combat.*` section.

---

## For addon developers

**You do not need permission to build an addon.** Depending on Zenkai and extending it through
the documented surfaces below is explicitly allowed under the licence. Go build things.

### Datapack extension points

Most of Zenkai is data-driven and reloadable with `/reload`. An addon can ship as a pure
datapack, no Java required, using these folders under `data/<your_namespace>/`:

| Folder | What it defines |
|---|---|
| `zenkai_forms/` | Transformations — cost, mastery, stat %, ki drain, aura, hair |
| `zenkai_aura_signatures/` | Per-aura-type visual shape tuning for the aura shader |
| `zenkai_skills/` | Skills — levels, TP cost, requirements, per-level value curves |
| `zenkai_race_stats/` | Base attributes and specialisation coefficients per race |
| `zenkai_techniques/ki/`, `zenkai_techniques/physical/` | Technique tuning |
| `zenkai_entities/` | Power level and stats for any entity, vanilla or modded |
| `zenkai_ki_weapons/` | Ki weapon definitions |
| `zenkai_ki_projectiles/` | Which projectiles accept Ki Infuse and how |
| `zenkai_vfx_effects/` | Visual effect definitions for techniques (e.g. physical impacts) |
| `zenkai_archetypes/` | Entity archetypes — attribute shape and multipliers |
| `zenkai_instant_transmission_blocklist/` | Targets Instant Transmission cannot lock onto |
| `zenkai_alignment/` | Alignment thresholds and effects |
| `zenkai_masters/` | Per-master admission gates (power level, alignment) |
| `zenkai_scouter_upgrades/` | Per-tier scouter upgrade costs |
| `zenkai_generator_fuels/` | Fuel values for the energy generator block |

These folders are scanned across **every loaded namespace**, so an addon with its own mod id
just drops its JSON under `data/<your_modid>/` and Zenkai picks it up. The full, supported
contract — including the event bus — is in [`ADDONS.md`](ADDONS.md).

Look at Zenkai's own files in `src/main/resources/data/zenkai/` — the shipped content uses the
exact same format an addon would.

### Current limits

Being honest about what you *cannot* do yet, so you do not waste an evening finding out:

- **Races are a Java enum.** You can retune the five existing races, not add a sixth.
- **Technique types are Java enums.** JSON tunes the existing ones; new ones need Java.
- **The public API is small on purpose.** Since 0.3.0 the supported surface is the datapack
  folders above plus the events in `com.hmc.zenkai.api.event` (race chosen, form changed,
  technique fired, ki consumed, master purchase). Everything else is internal and may change.

Forms, skills and archetypes *are* fully addable from a datapack today. If you need one of the limits above
lifted, open an issue — knowing what people actually want to build is what decides what gets an
API first.

### Building against Zenkai

Add Zenkai as a `compileOnly` dependency and declare it in your `neoforge.mods.toml` as an
optional or required dependency. See [`ADDONS.md`](ADDONS.md) for the details.

---

## Licence

Zenkai uses a split licence. **The code and the art are not under the same terms.**

| Part | Licence | File |
|---|---|---|
| Source code (`src/main/java`) | Mozilla Public License 2.0 | `LICENSE.txt` |
| Assets — textures, models, `.geo.json`, animations, sounds, structures | All Rights Reserved | `LICENSE_ASSETS.txt` |
| Datapack JSON (`src/main/resources/data`) | MIT | `LICENSE_ASSETS.txt` |

In plain terms:

- ✅ You may write, publish and sell addons that depend on Zenkai.
- ✅ You may copy the datapack JSON format freely.
- ✅ You may modify the code, as long as modified source files stay open under MPL-2.0.
- ❌ You may not reuse Zenkai's textures, models, animations or sounds in another project.
- ❌ You may not redistribute the jar outside CurseForge and Modrinth, or publish ports without
  permission.

If there is any conflict between this file and the licence shown on the CurseForge project page,
**the CurseForge licence is the one that applies.**

---

## Credits and disclaimers

**Not affiliated with Toei Animation, Bird Studio, Shueisha or any Dragon Ball rights holder.**
Dragon Ball and all associated names, characters and concepts are the property of their
respective owners. Zenkai is a non-commercial fan project. The licence above covers only this
project's original code and original artwork — it does not and cannot grant any rights over
Dragon Ball itself.

Zenkai is an independent implementation. Its design is openly influenced by *Dragon Block C* and
by the broader Dragon Ball modding tradition — the attribute model and training-point
progression are genre conventions those mods established. **No code or assets from any other mod
were copied, decompiled into this project, or derived from.** Everything here was written and
drawn from scratch.

If you believe any part of this project infringes your work, open an issue and it will be
addressed directly.

---

## Contributing

Bug reports and balance feedback are welcome through issues. For pull requests, note that
contributions to the Java source are accepted under MPL-2.0; asset contributions require a
separate conversation, since assets are not covered by an open licence.

---

<a id="español"></a>

<div align="center">

# Zenkai (Español)

**Una revisión del combate inspirada en Dragon Ball para Minecraft 1.21.1 (NeoForge)**

Razas, stats, transformaciones, técnicas de ki y vuelo — hechos para encajar *dentro* de
Minecraft, no para reemplazarlo.

</div>

## Qué es Zenkai

Zenkai añade una capa completa de progresión sobre Minecraft: elige una raza, entrénala,
aprende habilidades y técnicas de ki, transfórmate y pelea con un sistema de stats que escala
desde tu primer puñetazo hasta el final de la curva de poder.

La regla de diseño de todo el mod: **nada se activa hasta que tú lo decides.** Hasta que eliges
una raza, Zenkai no toca tu partida: sin stats, sin reservas, sin vuelo, sin combate alterado.
Con una raza elegida, el cuerpo a cuerpo sigue siendo el de vanilla hasta que activas el Modo
Combate. Tú decides cuándo juegas a Minecraft y cuándo juegas a Zenkai.

### Características

- **5 razas** — Humano, Saiyan, Namekiano, Arcosiano, Majin — cada una con tres
  especializaciones (Guerrero / Artista Marcial / Espiritualista) y sus propias curvas de stats.
- **Progresión de stats** — Puntos de Entrenamiento (TP) ganados peleando y repartidos en seis
  atributos. Rendimientos decrecientes por sesión para que farmear tenga forma.
- **Transformaciones** — una cadena de formas por raza: Super Saiyan 1–4, Blue, Rose y God;
  formas arcosianas (incluidas las líneas Dorada y Negra y la Quinta Forma); formas god y super
  de Humano, Namekiano y Majin; Potential Unlock, Ultra Instinto, Ultra Ego y más formas
  compartidas; y el ritual lunar exclusivo de Saiyan Oozaru → Super Oozaru (crece una cola real,
  requiere luna llena). Cada una con maestría, consumo de ki y su propia aura.
- **Técnicas de ki** — ráfagas cargables, ondas, discos, espirales, barreras, además de técnicas
  físicas (embestida, ráfaga de golpes, kiai, golpe pesado).
- **Habilidades** — Vuelo, Correr, Kaioken, Transmisión Instantánea, Ki Sense, Ki Control,
  Ki Fist, Ki Infuse, Ki Block, Meditación, God Ki y más.
- **Minijuegos de entrenamiento** — un hub de Training con Shadow Training (combate contra tu
  propio clon de sombra), Meditación y Práctica de Puntería, cada uno con dificultad real y
  recompensas de TP.
- **Gravedad ambiental** — el planeta de Kaiosama y la Cámara del Tiempo aplican gravedad real
  que escala el entrenamiento y se acumula con el equipo con peso.
- **Dragon Balls** — sets de la Tierra y Namek, radar, Shenlong y sistema de deseos.
- **Maestros** — Korin, Kami, Kaiosama y más, cada uno ofrece habilidades, técnicas y servicios
  puntuales (semillas del ermitaño, crecer/quitar la cola, pesas de entrenamiento).
- **Sistema de party** — crear, invitar, aceptar, salir, expulsar, disolver y alternar el fuego
  amigo para agruparte con otros jugadores.
- **Dos dimensiones** — Namek y el Otro Mundo, además de la Cámara del Tiempo.
- **Nave espacial** — una nave real y pilotable para viajar Tierra↔Namek, con cuenta atrás y
  aterrizaje propios.
- **Equipo** — Scouter (con mejoras), equipo de entrenamiento con peso (Curios), generador de
  energía, semillas del ermitaño, Kintoun.

### Requisitos

| Mod | Versión |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | ver `gradle.properties` |
| GeckoLib | 4.8.4+ |
| Player Animation Library (fork de zigythebird) | 1.1.5+ |
| TerraBlender | 4.1.0.8+ |
| Curios API | 9.5.1+ |
| Create + Ponder | opcional — añade tutoriales 3D dentro del juego si está presente |

---

## Configuración

Zenkai es muy configurable — más de 140 opciones repartidas en tres archivos.

- `zenkai-server.toml` — aquí vive casi todo: fórmulas de combate, costes y economía de TP,
  ritmos de entrenamiento, maestría, vuelo, gravedad ambiental (planeta de Kaiosama, Cámara del
  Tiempo) y más. Es por mundo (vive en el guardado, en `serverconfig/`) y se sincroniza solo a
  cada cliente, así que lo que ajustes es exactamente lo que los jugadores ven y con lo que
  pelean.
- `zenkai-common.toml` — solo por instalación y casi vacío a propósito: un par de opciones de
  desarrollo, sin balance de juego. (Antes contenía la mayoría de los números anteriores; se
  migraron a `zenkai-server.toml` porque un archivo por instalación y sin sincronizar es el
  lugar equivocado para cualquier cosa que el cliente deba ver bien — causó un desajuste real
  antes de moverlos.)
- `zenkai-client.toml` — preferencias de HUD y visuales.

La gamerule `zenkaiEnableRaceBoosts` desactiva la capa de combate entera en ejecución sin
desinstalar nada.

Si quieres la progresión de Zenkai pero no su revisión del combate, o al revés, la config te
lleva la mayor parte del camino. Empieza por la sección `combat.*`.

---

## Para desarrolladores de addons

**No necesitas permiso para construir un addon.** Depender de Zenkai y extenderlo a través de
las superficies documentadas abajo está explícitamente permitido por la licencia. Construye cosas.

### Puntos de extensión por datapack

Casi todo Zenkai es data-driven y recargable con `/reload`. Un addon puede distribuirse como un
datapack puro, sin Java, usando estas carpetas bajo `data/<tu_namespace>/`:

| Carpeta | Qué define |
|---|---|
| `zenkai_forms/` | Transformaciones — coste, maestría, % de stats, consumo de ki, aura, pelo |
| `zenkai_aura_signatures/` | Ajuste de forma visual del aura por tipo para el shader |
| `zenkai_skills/` | Habilidades — niveles, coste de TP, requisitos, curvas de valor por nivel |
| `zenkai_race_stats/` | Atributos base y coeficientes de especialización por raza |
| `zenkai_techniques/ki/`, `zenkai_techniques/physical/` | Ajuste de técnicas |
| `zenkai_entities/` | Nivel de poder y stats de cualquier entidad, vanilla o de otro mod |
| `zenkai_ki_weapons/` | Definiciones de armas de ki |
| `zenkai_ki_projectiles/` | Qué proyectiles aceptan Ki Infuse y cómo |
| `zenkai_vfx_effects/` | Definiciones de efectos visuales de técnicas (p. ej. impactos físicos) |
| `zenkai_archetypes/` | Arquetipos de entidad — forma de atributos y multiplicadores |
| `zenkai_instant_transmission_blocklist/` | Objetivos a los que la Transmisión Instantánea no puede fijarse |
| `zenkai_alignment/` | Umbrales y efectos de alineamiento |
| `zenkai_masters/` | Requisitos de admisión por maestro (nivel de poder, alineamiento) |
| `zenkai_scouter_upgrades/` | Costes de mejora del scouter por nivel |
| `zenkai_generator_fuels/` | Valores de combustible del bloque generador de energía |

Estas carpetas se escanean en **cada namespace cargado**, así que un addon con su propio mod id
solo suelta sus JSON bajo `data/<tu_modid>/` y Zenkai los recoge. El contrato completo y
soportado — incluido el bus de eventos — está en [`ADDONS.md`](ADDONS.md).

Mira los archivos del propio Zenkai en `src/main/resources/data/zenkai/` — el contenido que se
distribuye usa exactamente el mismo formato que usaría un addon.

### Límites actuales

Siendo honestos sobre lo que *todavía no* puedes hacer, para que no pierdas una tarde
descubriéndolo:

- **Las razas son un enum de Java.** Puedes reajustar las cinco existentes, no añadir una sexta.
- **Los tipos de técnica son enums de Java.** El JSON ajusta los existentes; los nuevos
  requieren Java.
- **La API pública es pequeña a propósito.** Desde 0.3.0 la superficie soportada son las
  carpetas de datapack de arriba más los eventos de `com.hmc.zenkai.api.event` (raza elegida,
  forma cambiada, técnica lanzada, ki consumido, compra a un maestro). Todo lo demás es interno
  y puede cambiar.

Las formas, habilidades y arquetipos *sí* se pueden añadir por completo desde un datapack hoy.
Si necesitas que se levante alguno de los límites anteriores, abre un issue — saber qué quiere
construir la gente es lo que decide qué recibe una API primero.

### Compilar contra Zenkai

Añade Zenkai como dependencia `compileOnly` y decláralo en tu `neoforge.mods.toml` como
dependencia opcional u obligatoria. Consulta [`ADDONS.md`](ADDONS.md) para los detalles.

---

## Licencia

Zenkai usa una licencia dividida. **El código y el arte no tienen los mismos términos.**

| Parte | Licencia | Archivo |
|---|---|---|
| Código fuente (`src/main/java`) | Mozilla Public License 2.0 | `LICENSE.txt` |
| Assets — texturas, modelos, `.geo.json`, animaciones, sonidos, estructuras | Todos los derechos reservados | `LICENSE_ASSETS.txt` |
| JSON de datapack (`src/main/resources/data`) | MIT | `LICENSE_ASSETS.txt` |

En lenguaje llano:

- ✅ Puedes escribir, publicar y vender addons que dependan de Zenkai.
- ✅ Puedes copiar libremente el formato JSON de los datapacks.
- ✅ Puedes modificar el código, siempre que los archivos fuente modificados sigan abiertos bajo
  MPL-2.0.
- ❌ No puedes reutilizar las texturas, modelos, animaciones o sonidos de Zenkai en otro
  proyecto.
- ❌ No puedes redistribuir el jar fuera de CurseForge y Modrinth, ni publicar ports sin permiso.

Si hay cualquier conflicto entre este archivo y la licencia que aparece en la página del
proyecto en CurseForge, **se aplica la de CurseForge.**

---

## Créditos y descargos

**Sin afiliación con Toei Animation, Bird Studio, Shueisha ni ningún titular de derechos de
Dragon Ball.** Dragon Ball y todos los nombres, personajes y conceptos asociados son propiedad
de sus respectivos dueños. Zenkai es un proyecto de fans sin ánimo de lucro. La licencia de
arriba cubre solo el código y el arte originales de este proyecto — no otorga ni puede otorgar
derechos sobre Dragon Ball en sí.

Zenkai es una implementación independiente. Su diseño está abiertamente influido por *Dragon
Block C* y por la tradición más amplia de mods de Dragon Ball — el modelo de atributos y la
progresión por puntos de entrenamiento son convenciones del género que esos mods establecieron.
**No se copió, descompiló ni derivó código ni assets de ningún otro mod.** Todo lo de aquí está
escrito y dibujado desde cero.

Si crees que alguna parte de este proyecto infringe tu trabajo, abre un issue y se atenderá
directamente.

---

## Contribuir

Los reportes de bugs y el feedback de balance son bienvenidos mediante issues. Para pull
requests, ten en cuenta que las contribuciones al código Java se aceptan bajo MPL-2.0; las
contribuciones de assets requieren una conversación aparte, ya que los assets no están cubiertos
por una licencia abierta.
