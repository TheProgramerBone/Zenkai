<div align="center">

# Zenkai

**A Dragon Ball–inspired combat overhaul for Minecraft 1.21.1 (NeoForge)**

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
- **Transformations** — Super Saiyan 1–4, Arcosian forms (including the Golden and Black lines),
  Potential Unlock, and the Saiyan-only Oozaru → Super Oozaru moonlight ritual (grows a real
  tail, needs a full moon), each with mastery, ki drain and its own aura.
- **Ki techniques** — chargeable blasts, waves, discs, spirals, barriers, plus physical
  techniques (dash, barrage, kiai, heavy blow).
- **Skills** — Flight, Kaioken, Ki Sense, Ki Control, Ki Fist, Ki Infuse, Ki Block, Meditation.
- **Dragon Balls** — Earth and Namek sets, radar, Shenlong and a wish system.
- **Masters** — Korin, Kami, Kaiosama and more, each offering skills, techniques and one-off
  services (senzu beans, growing/removing your tail, training weights).
- **Party system** — invite, accept, leave, kick, disband and a friendly-fire toggle for
  grouping up with other players.
- **Two dimensions** — Namek and the Otherworld, plus the Hyperbolic Time Chamber.
- **Gear** — Scouter, weighted training equipment (Curios), senzu beans, Kintoun.

### Requirements

| Mod | Version |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | see `gradle.properties` |
| GeckoLib | 4.8.4+ |
| Player Animation Library (zigythebird fork) | 1.1.5+ |
| TerraBlender | 4.1.0.8+ |
| Curios API | 9.5.1+ |

---

## Configuration

Zenkai is heavily configurable — over 150 options across three files.

- `zenkai-common.toml` — combat formulas, costs, training rates, scaling. Server-authoritative.
- `zenkai-server.toml` — per-world toggles.
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
| `zenkai_alignment/` | Alignment thresholds and effects |
| `zenkai_masters/` | Per-master admission gates (power level, alignment) |
| `zenkai_scouter_upgrades/` | Per-tier scouter upgrade costs |
| `zenkai_generator_fuels/` | Fuel values for the energy generator block |

Look at Zenkai's own files in `src/main/resources/data/zenkai/` — the shipped content uses the
exact same format an addon would.

### Current limits

Being honest about what you *cannot* do yet, so you do not waste an evening finding out:

- **Races are a Java enum.** You can retune the five existing races, not add a sixth.
- **Technique types are Java enums.** JSON tunes the existing ones; new ones need Java.
- **There is no public `api` package yet.** Nothing is guaranteed stable across versions.

Forms and skills *are* fully addable from a datapack today. If you need one of the limits above
lifted, open an issue — knowing what people actually want to build is what decides what gets an
API first.

### Building against Zenkai

Zenkai is available from the CurseForge Maven. Add it as a `compileOnly` dependency and declare
it in your `neoforge.mods.toml` as an optional or required dependency.

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