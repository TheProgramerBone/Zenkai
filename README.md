<div align="center">

# Zenkai

**A Dragon Ball–inspired combat and progression overhaul for Minecraft 1.21.1 (NeoForge)**

🇬🇧 English · 🇪🇸 [Español](#español)

Races, stats, transformations, ki techniques and flight — built to sit *inside* Minecraft,
not to replace it.

### 💬 [Join the Zenkai Discord](https://discord.com/invite/mKD2RCqqsP)
Full changelogs, suggestions, bug reports, previews and addon talk.

</div>

---

## What Zenkai is

Zenkai adds a full progression layer on top of Minecraft: pick a race, train it, learn skills
and ki techniques, transform, and fight with a stat system that scales from your first punch
to the end of the power curve.

The design rule: **nothing turns on until you opt in.** Until you choose a race, Zenkai does not
touch your game. Once you have one, melee stays vanilla until you switch Combat Mode on.

### Features

- **5 races** — Human, Saiyan, Namekian, Arcosian, Majin — each with three specialisations
  (Warrior / Martial Artist / Spiritualist) and their own stat curves.
- **Stats** — Training Points earned by fighting, spent across six attributes, with diminishing
  returns per session.
- **Transformation skill tree** — a form chain per race (Super Saiyan 1–4, God, Blue, Rosé;
  Arcosian, Namekian, Human and Majin god/super forms; Mystic, Ultra Instinct, Ultra Ego, the
  Legendary line, the Oozaru moonlight ritual…). Each form has mastery, ki drain and its own aura.
- **Ki techniques** — chargeable blasts, waves, discs, spirals and barriers, plus physical
  techniques (dash, barrage, kiai, heavy blow), beam clashes, parry and blocking.
- **Ki weapons** — sword, axe, hammer, mace, scythe and shield, plus Ki Infuse on projectiles.
- **Skills** — Flight, Kaioken, Instant Transmission, Ki Sense, Ki Control, Ki Block, Meditation,
  God Ki and more, taught by masters (Korin, Kami, Kaiosama…).
- **Lock-on** — fix a target with an anchored camera and marker.
- **Auras** — a composed aura system with a look per form.
- **Training** — a hub with Shadow Training, Meditation and Target Practice, weighted gear and
  real ambient gravity (Kaiosama's planet, the Hyperbolic Time Chamber).
- **Dragon Balls** — Earth and Namek sets, radar, Shenlong and six wishes.
- **World** — Namek, the Otherworld and the Hyperbolic Time Chamber; a flyable Space Pod.
- **Gear** — Scouter (bench, upgrades, tint), energy generator, senzu beans, Kintoun.
- **Party system** — invite, leave, kick, disband and a friendly-fire toggle.

### Requirements

| Mod | Version |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.233+ |
| GeckoLib | 4.8.4+ |
| Player Animation Library (zigythebird fork) | 1.1.5+ |
| TerraBlender | 4.1.0.8+ |
| Curios API | 9.5.1+ |
| Create + Ponder | optional — in-game 3D tutorials |

---

## Configuration

140+ options across three files:

- `zenkai-server.toml` — almost everything: combat formulas, TP costs, training, mastery, flight,
  ambient gravity. Per world (in the save's `serverconfig/`) and synced to every client.
- `zenkai-client.toml` — HUD and visual preferences.
- `zenkai-common.toml` — per installation, nearly empty (dev options only, no balance).

The gamerule `zenkaiEnableRaceBoosts` disables the whole combat layer at runtime without
uninstalling anything. To keep the progression but not the combat overhaul (or the reverse),
start with the `combat.*` section.

---

## For addon developers

**You do not need permission to build an addon.** Depending on Zenkai and extending it through
the documented surfaces is explicitly allowed by the licence.

Most of Zenkai is data-driven and reloadable with `/reload`, so an addon can be a pure datapack
(forms, skills, masters, techniques, entities, race stats, ki weapons, face parts, hairstyles,
skill-tree nodes, scouter upgrades, generator fuels and more), scanned across **every loaded
namespace**. There is also a small event bus (`com.hmc.zenkai.api.event`: race chosen, form
changed, technique fired, ki consumed, master purchase).

The full contract, folder list and examples are in [`ADDONS.md`](ADDONS.md). Zenkai's own files
in `src/main/resources/data/zenkai/` use exactly the format an addon would.

**Current limits:** races and technique types are Java enums (you can retune them, not add new
ones), and everything outside the datapack folders and events is internal and may change. If a
limit blocks what you want to build, say so on Discord or in an issue.

To build against Zenkai, add it as a `compileOnly` dependency and declare it in your
`neoforge.mods.toml`.

---

## Licence

**The code and the art are not under the same terms.**

| Part | Licence | File |
|---|---|---|
| Source code (`src/main/java`) | Mozilla Public License 2.0 | `LICENSE.txt` |
| Assets — textures, models, `.geo.json`, animations, sounds, structures | All Rights Reserved | `LICENSE_ASSETS.txt` |
| Datapack JSON (`src/main/resources/data`) | MIT | `LICENSE_ASSETS.txt` |
| DragonMineZ hairstyles (`assets/zenkai_dmz`, `data/zenkai_dmz`) | GNU GPL v3.0 | `LICENSE.txt` + `NOTICE.txt` in those folders |

- ✅ You may write, publish and sell addons that depend on Zenkai.
- ✅ You may copy the datapack JSON format freely.
- ✅ You may modify the code, as long as modified source files stay open under MPL-2.0.
- ❌ You may not reuse Zenkai's textures, models, animations or sounds in another project.
- ❌ You may not redistribute the jar outside CurseForge and Modrinth, or publish ports without
  permission.

If this file conflicts with the licence on the CurseForge page, **the CurseForge licence applies.**

---

## Credits and disclaimers

**Not affiliated with Toei Animation, Bird Studio, Shueisha or any Dragon Ball rights holder.**
Dragon Ball and all associated names and characters belong to their owners. Zenkai is a
non-commercial fan project.

Zenkai is an independent implementation, openly influenced by *Dragon Block C* and the wider
Dragon Ball modding tradition. **No code or assets from any other mod were copied, decompiled or
derived from.** If you believe any part infringes your work, open an issue.

## Contributing

Bug reports and balance feedback are welcome through issues or the
[Discord](https://discord.com/invite/mKD2RCqqsP). Java contributions are accepted under MPL-2.0; asset
contributions need a separate conversation.

---

<a id="español"></a>

<div align="center">

# Zenkai (Español)

**Una revisión de combate y progresión inspirada en Dragon Ball para Minecraft 1.21.1 (NeoForge)**

Razas, stats, transformaciones, técnicas de ki y vuelo — hechos para encajar *dentro* de
Minecraft, no para reemplazarlo.

### 💬 [Únete al Discord de Zenkai](https://discord.com/invite/mKD2RCqqsP)
Changelogs completos, sugerencias, reportes de bugs, avances y charla sobre addons.

</div>

## Qué es Zenkai

Zenkai añade una capa completa de progresión sobre Minecraft: elige una raza, entrénala,
aprende habilidades y técnicas de ki, transfórmate y pelea con un sistema de stats que escala
desde tu primer puñetazo hasta el final de la curva de poder.

La regla de diseño: **nada se activa hasta que tú lo decides.** Hasta que eliges una raza,
Zenkai no toca tu partida. Con una raza, el cuerpo a cuerpo sigue siendo el de vanilla hasta que
activas el Modo Combate.

### Características

- **5 razas** — Humano, Saiyan, Namekiano, Arcosiano, Majin — cada una con tres
  especializaciones (Guerrero / Artista Marcial / Espiritualista) y sus propias curvas de stats.
- **Stats** — Puntos de Entrenamiento (TP) ganados peleando y repartidos en seis atributos, con
  rendimientos decrecientes por sesión.
- **Skill tree de transformaciones** — una cadena de formas por raza (Super Saiyan 1–4, God,
  Blue, Rosé; formas god y super de Arcosiano, Namekiano, Humano y Majin; Místico, Ultra
  Instinto, Ultra Ego, la línea Legendaria, el ritual lunar del Oozaru…). Cada forma tiene
  maestría, consumo de ki y su propia aura.
- **Técnicas de ki** — ráfagas cargables, ondas, discos, espirales y barreras, además de técnicas
  físicas (embestida, ráfaga de golpes, kiai, golpe pesado), choque de haces, parry y bloqueo.
- **Armas de ki** — espada, hacha, martillo, maza, guadaña y escudo, además de Ki Infuse en
  proyectiles.
- **Habilidades** — Vuelo, Kaioken, Transmisión Instantánea, Ki Sense, Ki Control, Ki Block,
  Meditación, God Ki y más, enseñadas por maestros (Korin, Kami, Kaiosama…).
- **Fijar objetivo** — cámara anclada y marcador sobre el enemigo.
- **Auras** — un sistema de auras compuesto, con un aspecto por forma.
- **Entrenamiento** — un hub con Shadow Training, Meditación y Práctica de Puntería, equipo con
  peso y gravedad ambiental real (planeta de Kaiosama, Cámara del Tiempo).
- **Dragon Balls** — sets de la Tierra y Namek, radar, Shenlong y seis deseos.
- **Mundo** — Namek, el Otro Mundo y la Cámara del Tiempo; una nave espacial pilotable.
- **Equipo** — Scouter (banco, mejoras, tinte), generador de energía, semillas del ermitaño,
  Kintoun.
- **Party** — invitar, salir, expulsar, disolver y alternar el fuego amigo.

### Requisitos

| Mod | Versión |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.233+ |
| GeckoLib | 4.8.4+ |
| Player Animation Library (fork de zigythebird) | 1.1.5+ |
| TerraBlender | 4.1.0.8+ |
| Curios API | 9.5.1+ |
| Create + Ponder | opcional — tutoriales 3D dentro del juego |

---

## Configuración

Más de 140 opciones en tres archivos:

- `zenkai-server.toml` — casi todo: fórmulas de combate, costes de TP, entrenamiento, maestría,
  vuelo, gravedad ambiental. Es por mundo (en `serverconfig/` del guardado) y se sincroniza a
  cada cliente.
- `zenkai-client.toml` — preferencias de HUD y visuales.
- `zenkai-common.toml` — por instalación y casi vacío (solo opciones de desarrollo, sin balance).

La gamerule `zenkaiEnableRaceBoosts` desactiva la capa de combate entera en ejecución sin
desinstalar nada. Para quedarte con la progresión sin la revisión del combate (o al revés),
empieza por la sección `combat.*`.

---

## Para desarrolladores de addons

**No necesitas permiso para construir un addon.** Depender de Zenkai y extenderlo a través de las
superficies documentadas está explícitamente permitido por la licencia.

Casi todo Zenkai es data-driven y recargable con `/reload`, así que un addon puede ser un
datapack puro (formas, habilidades, maestros, técnicas, entidades, stats de raza, armas de ki,
rasgos de cara, peinados, nodos del skill tree, mejoras del scouter, combustibles del generador
y más), escaneado en **cada namespace cargado**. Hay además un bus de eventos pequeño
(`com.hmc.zenkai.api.event`: raza elegida, forma cambiada, técnica lanzada, ki consumido, compra
a un maestro).

El contrato completo, la lista de carpetas y ejemplos están en [`ADDONS.md`](ADDONS.md). Los
archivos del propio Zenkai en `src/main/resources/data/zenkai/` usan exactamente el formato que
usaría un addon.

**Límites actuales:** las razas y los tipos de técnica son enums de Java (se pueden reajustar,
no añadir nuevos), y todo lo que esté fuera de las carpetas de datapack y los eventos es interno
y puede cambiar. Si un límite te bloquea, cuéntalo en Discord o en un issue.

Para compilar contra Zenkai, añádelo como dependencia `compileOnly` y decláralo en tu
`neoforge.mods.toml`.

---

## Licencia

**El código y el arte no tienen los mismos términos.**

| Parte | Licencia | Archivo |
|---|---|---|
| Código fuente (`src/main/java`) | Mozilla Public License 2.0 | `LICENSE.txt` |
| Assets — texturas, modelos, `.geo.json`, animaciones, sonidos, estructuras | Todos los derechos reservados | `LICENSE_ASSETS.txt` |
| JSON de datapack (`src/main/resources/data`) | MIT | `LICENSE_ASSETS.txt` |
| Peinados de DragonMineZ (`assets/zenkai_dmz`, `data/zenkai_dmz`) | GNU GPL v3.0 | `LICENSE.txt` + `NOTICE.txt` en esas carpetas |

- ✅ Puedes escribir, publicar y vender addons que dependan de Zenkai.
- ✅ Puedes copiar libremente el formato JSON de los datapacks.
- ✅ Puedes modificar el código, siempre que los archivos fuente modificados sigan abiertos bajo
  MPL-2.0.
- ❌ No puedes reutilizar las texturas, modelos, animaciones o sonidos de Zenkai en otro proyecto.
- ❌ No puedes redistribuir el jar fuera de CurseForge y Modrinth, ni publicar ports sin permiso.

Si hay conflicto entre este archivo y la licencia de la página de CurseForge, **se aplica la de
CurseForge.**

---

## Créditos y descargos

**Sin afiliación con Toei Animation, Bird Studio, Shueisha ni ningún titular de derechos de
Dragon Ball.** Dragon Ball y todos los nombres y personajes asociados son propiedad de sus
dueños. Zenkai es un proyecto de fans sin ánimo de lucro.

Zenkai es una implementación independiente, abiertamente influida por *Dragon Block C* y por la
tradición de mods de Dragon Ball. **No se copió, descompiló ni derivó código ni assets de ningún
otro mod.** Si crees que alguna parte infringe tu trabajo, abre un issue.

## Contribuir

Los reportes de bugs y el feedback de balance son bienvenidos por issues o en el
[Discord](https://discord.com/invite/mKD2RCqqsP). Las contribuciones al código Java se aceptan bajo MPL-2.0; las de
assets requieren una conversación aparte.
