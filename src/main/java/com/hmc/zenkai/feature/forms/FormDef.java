package com.hmc.zenkai.feature.forms;

import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.ZenkaiAttributes;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.util.BalanceUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Datos de una transformación. Viven en datapack: data/&lt;ns&gt;/zenkai_forms/&lt;id&gt;.json
 * y se sincronizan al cliente con FormSyncPacket (ver FormManager).
 *
 * La MAESTRÍA interpola entre dos extremos declarados por la propia forma:
 *   stat_percent_untrained -> stat_percent_mastered   (sube con la maestría)
 *   ki_drain_untrained     -> ki_drain_mastered       (baja con la maestría)
 * Por eso NO hace falta un tope de maestría en config: el techo es el dato de cada forma.
 * Nombramos los extremos por la POSICIÓN en la maestría y no por su magnitud, porque el
 * drenaje baja: su "mínimo" es el de maestría máxima, y min/max se prestaba a confusión.
 *
 * La cadena de formas apunta HACIA ATRÁS (parent). Así añadir una rama nueva es crear un
 * archivo sin tocar el de la forma anterior, que es la gracia de los datapacks; la cadena
 * hacia delante se reconstruye al cargar (FormRegistry.nextFrom).
 *
 * ki_drain_* es un PORCENTAJE POR SEGUNDO del poder del jugador (STR+WIL), no ki fijo: ver
 * kiDrainPerSecond. Antes era un número fijo de ki por tick sin relación con el jugador, así que con
 * miles de puntos el pool crecía y el coste no: cualquier forma salía gratis en late game.
 * SPI_REQ no es un candado: la forma se puede activar con cualquier SPI, pero el drenaje se
 * multiplica por drainMultiplier(spi) (por defecto solo encarece: 1.0-6.0; el SPI alto no abarata, ya paga con un pool y regen mayores). spi_req = 0 desactiva
 * el multiplicador (queda en 1.0 siempre), para no romper datapacks viejos.
 *
 * Los 4 campos antes de wheelSelectable son del sistema de "forzar" (powerPercent por encima
 * de 100%, ver OverdriveSystem): DESCENDABLE marca formas que el botón "Descender" de la rueda
 * puede tumbar de golpe a base (hoy solo second_form/third_form/final_form del arcosiano;
 * Golden/Black no lo llevan a propósito, se revierten con el tap de siempre). overdriveCeilingBonus
 * y los dos overdriveDrainMult solo tienen efecto MIENTRAS esa forma está puesta (no por tenerla
 * comprada): amplían cuánto se puede forzar y abaratan el coste de hacerlo. Defaults
 * (false/0/1.0/1.0) no afectan a ninguna forma existente que no los declare en su JSON.
 *
 * wheelSelectable (default true): si es false, WheelSelectPacket.selectForm() rechaza esta forma
 * aunque un cliente modificado la pida por id — server-authoritative, igual que el candado de
 * tipo de técnica documentado en CLAUDE.md. Pensado para formas "cadena" (kind divine, parent
 * null) que no viven en la escalera normal de super_forms y se activan por su propio sistema en
 * vez de por la rueda/tecla H (hoy: oozaru/super_oozaru, ver OozaruSystem). Sin este candado, la
 * rueda ya no las enseña (FormRegistry.chainFor no las alcanza), pero nada impedía a un cliente
 * modificado seleccionarlas igualmente por packet.
 *
 * divineTier (default false): marca un peldaño de energía divina (hoy: human_god/namek_god/
 * majin_god, ssj_god/ssj_blue/ssj_rose) — su acceso NO sale de super_forms sino de la skill
 * "god_ki" (ver feature.skills.DivineForms/SuperForms), aunque siga viviendo en la misma
 * cadena parent/hijo que el resto para efectos de la rueda y la maestría. Dos formas con el
 * MISMO parent y ambas divineTier son hermanas EQUIVALENTES a la misma profundidad divina
 * (ssj_blue/ssj_rose): el jugador elige cuál llevar puesta, no hay que comprarlas por separado.
 *
 * auraOuterRgb (default -1 = sin capa exterior): tinte de una SEGUNDA capa de aura que
 * ENVUELVE a la principal (auraRgb), el mismo mecanismo que ya usaba solo Kaioken-sobre-forma
 * (ver AuraColors.resolveLayers) — ahora una forma puede pedirlo por su cuenta sin necesitar
 * Kaioken encima. Pensado para auras de dos tonos (SSJ Rose/Goku Black: magenta por dentro,
 * granate oscuro por fuera). Si la forma está activa CON Kaioken, Kaioken sigue ganando la capa
 * exterior (KAIOKEN_RGB) — este campo solo se usa cuando la forma no lleva Kaioken encima.
 *
 * tailRgb (default -1 = sin override, ver TailResolver): igual que hairRgb, la cola NUNCA sigue
 * el color elegido por el jugador (no hay selector de color de cola) — cada forma tiene SIEMPRE
 * su propio tono fijo, base incluida. Este campo es solo la ESCOTILLA DE ESCAPE explícita para
 * cuando la cola deba tener un tono distinto al pelo de la forma; el caso normal no necesita
 * declararlo — TailResolver cae a hairRgb de la misma forma por defecto (la cola concuerda con
 * el pelo de la transformación sin duplicar el dato en cada JSON), y si tampoco hay hairRgb, al
 * color natural (TailResolver.DEFAULT_TAIL_RGB). Mismo mecanismo de aplicación que hairRgb
 * (DYED_COLOR en el stack virtual, leído por GeoLayerArmorRenderer.getRenderColor), pero la
 * textura de la cola no tiene canal de tinte propio (ColorChannel.NONE) — no hace falta el "sin
 * declarar -> color de canal" que sí tiene hairRgb.
 *
 * eyeRgb (default -1 = sin override, ver FaceOverlayGeoLayer): a diferencia de hairRgb/tailRgb,
 * el color de ojos NO parte de una textura en gris — cada jugador YA elige el suyo propio en
 * PlayerVisualAttachment.eyeColorRgb al crear personaje, y ese es el que se pinta siempre que
 * ninguna forma diga lo contrario. Este campo es la escotilla de escape para cuando una forma
 * necesita imponer SU PROPIO color de ojos por encima de la elección del jugador (ojos plateados
 * de Ultra Instinto, verde-azulado de SSJ...) — pensado sobre todo para las razas sin pelo
 * (Namekiano/Majin/Arcosiano), donde el pelo no puede vender la transformación y el cambio de
 * ojos es la única pista visual de "ascensión" que les queda (ver `divineTier` en
 * human_god/namek_god/majin_god). No sustituye el iris SELECCIONADO (`eyeIndex`, la forma del
 * ojo), solo su tinte — mismo espíritu que hairRgb tiñendo el mismo modelo de pelo base.
 *
 * Rutas de endgame universales (0.3.0, Destroyer/Angels/Mystic Path — ver
 * {@code .claude/docs/jugador-razas-formas.md}): NO son mutuamente
 * excluyentes (decisión explícita del usuario) — son caminos de progresión independientes,
 * cada uno una cadena normal de {@code FormDef} más colgada de {@code parent: null} (mismo caso
 * que {@code oozaru}/{@code potential_unlock}: fuera de la cadena de la raza, la rueda las
 * ofrece aparte, ver {@code WheelMenu.ENDGAME_ROOTS}/{@code EndgamePaths}). Un jugador puede
 * tener varias desbloqueadas a la vez y cambiar libremente entre ellas como con cualquier otra
 * forma.
 *
 * tier (default NONE): rango de diseño God/Evolution/Ultra, ver {@link Tier}.
 * endgamePath (default false): marca los peldaños de las 3 rutas de endgame universales. Sustituye
 * al antiguo {@code divine_tier: true} de esas 5 formas, que las hacía contar como "rama divina
 * propia" de cada raza (empates en DivineForms.resolveTie, maxLevel(ARCOSIAN) != 0). Su acceso lo
 * decide EndgamePaths, nunca god_ki/super_forms.
 *
 * alignmentBranch (default NONE): ver {@link AlignBranch}.
 * masteryReqs (default vacío): maestría MÍNIMA (0-100) que el jugador debe tener en OTRAS formas
 * para desbloquear ESTA (ver SuperForms.unlocked). Es el único vínculo entre ramas hermanas: la
 * herencia por parent no exige haber dominado nada (golden_fifth_form cuelga de fifth_form, pero
 * exige fifth 100% + golden_form 50%).
 *
 * category (0.3.0, NO es un campo del JSON — se deriva de la SUBCARPETA del archivo dentro de
 * {@code data/<ns>/zenkai_forms/<categoría>/<id>.json}, ver {@link FormManager}): agrupa formas
 * para la rueda ({@code WheelMenu.forms()}, una sub-rama por categoría distinta encontrada en el
 * recorrido, en vez de una sola lista "Forms" plana — pensado para que la rueda no se amontone
 * al crecer una raza con muchas transformaciones). Categorías de hoy: {@code saiyan}/
 * {@code human}/{@code namekian}/{@code arcosian}/{@code majin} (la cadena de cada raza) y
 * {@code universal} (Potential Unlock + las 3 rutas de endgame, compartidas por las 5). El id
 * lógico de la forma ({@code zenkai:ssj1}) NO incluye la subcarpeta — es puramente organización
 * de archivo + agrupación visual, nunca parte de la identidad de la forma.
 */
public record FormDef(ResourceLocation id, EnumSet<Race> races, Kind kind,
                      ResourceLocation parent, int tpCost, int holdTicks,
                      double masteryGain,
                      double statPercentUntrained, double statPercentMastered,
                      double kiDrainUntrained, double kiDrainMastered, int spiReq,
                      Map<String, ResourceLocation> hairItems,
                      Map<String, ResourceLocation> bodyItems,
                      String auraType, int auraRgb, int hairRgb, int tailRgb, double scale,
                      boolean descendable, double overdriveCeilingBonus,
                      double overdriveDrainMultUntrained, double overdriveDrainMultMastered,
                      boolean wheelSelectable, boolean divineTier, int auraOuterRgb, int eyeRgb,
                      int browRgb, Tier tier, boolean endgamePath, AlignBranch alignmentBranch,
                      Map<ResourceLocation, Double> masteryReqs, String category,
                      Map<Integer, Integer> layerColors, int furRgb, boolean noBrows) {

    /**
     * Rango de balance de una forma (campo "tier" del JSON, default NONE), con significado fijo
     * desde F8 del skill tree (.claude/archivo/2026-10-22-pendientes-0.6.0-previo/skill-tree-balance-formas.md §3.1): NONE = peldaño mortal de la
     * cadena; GOD = primer escalón divino (exige God Ki); EVOLUTION = segundo escalón divino o
     * evolución mortal paralela; ULTRA = pináculo. SIN lógica de juego (la de EVOLUTION que abría
     * el endgame se quitó en F8): solo lo leen el balance y los efectos del árbol (marcos que
     * laten más cuanto más alto el tier). NO implica {@code divineTier}.
     */
    public enum Tier {
        NONE, GOD, EVOLUTION, ULTRA;

        public static Tier byName(String s) {
            if (s == null) return NONE;
            try { return valueOf(s.toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException e) { return NONE; }
        }
    }

    /**
     * Bifurcación por alineamiento entre hermanas MORTALES (mismo parent, ambas con valor distinto
     * de NONE): el jugador con alineamiento >= 0 usa la GOOD, el de alineamiento negativo la EVIL,
     * sin elegir (mismo espíritu que ssj_blue/ssj_rose por isDivine, pero para formas no
     * divinas: ver SuperForms.resolveSibling). Hoy: majin_harmony / majin_pure_chaos.
     */
    public enum AlignBranch {
        NONE, GOOD, EVIL;

        public static AlignBranch byName(String s) {
            if (s == null) return NONE;
            try { return valueOf(s.toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException e) { return NONE; }
        }
    }

    public enum Kind {
        /** Innata por raza: no la enseña ningún maestro. */
        SUPER,
        /** Requiere maestro (Whis y compañía). */
        DIVINE,
        /** Cuelga de otra forma (parent); es su continuación. */
        EXTENSION;

        public static Kind byName(String s) {
            if (s == null) return SUPER;
            try { return valueOf(s.toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException e) { return SUPER; }
        }
    }

    private static volatile Map<ResourceLocation, FormDef> REGISTRY = Map.of();

    public static void replaceAll(Map<ResourceLocation, FormDef> defs) {
        REGISTRY = Collections.unmodifiableMap(new LinkedHashMap<>(defs));
    }

    /** null si esa forma no está definida en ningún datapack. */
    public static FormDef get(ResourceLocation id) { return id == null ? null : REGISTRY.get(id); }

    public static java.util.Collection<FormDef> all() { return REGISTRY.values(); }

    // ── Interpolación por maestría (0..100) ─────────────────────────────────

    private static double lerp(double a, double b, double t) {
        double c = Math.max(0.0, Math.min(1.0, t));
        return a + (b - a) * c;
    }

    /** ¿Es una forma UNIVERSAL cuyo % se escala por raza (UI, UE, Beast, Señal; F8)? Mystic
     *  se escala aparte, en PotentialUnlock (su % sale del techo del propio jugador). */
    public boolean universalScaled() { return endgamePath; }

    /** Como {@link #statPercent(double)}, con la escala universal de la raza aplicada (F8). */
    public double statPercent(com.hmc.zenkai.feature.Race race, double mastery0to100) {
        double v = statPercent(mastery0to100);
        return universalScaled() ? v * com.hmc.zenkai.feature.RaceStatTable.universalFormScale(race) : v;
    }

    /** % a maestría 100 con la escala universal de la raza (F8). */
    public double statPercentMastered(com.hmc.zenkai.feature.Race race) {
        return statPercent(race, 100.0);
    }

    /** Fracción que SUMA al multiplicador total (1.5 = +150%), según la maestría. */
    public double statPercent(double mastery0to100) {
        return lerp(statPercentUntrained, statPercentMastered, mastery0to100 / 100.0);
    }

    /** Drenaje "de catálogo" en % POR SEGUNDO, según la maestría (baja al dominarla). Es el dato
     *  del datapack (ki_drain_*), sin poder ni SPI: el ki real sale de {@link #kiDrainPerSecond}. */
    public double kiDrainPct(double mastery0to100) {
        return lerp(kiDrainUntrained, kiDrainMastered, mastery0to100 / 100.0);
    }

    /**
     * Multiplicador de drenaje según cuánto SPI tenga el jugador respecto al requisito de la
     * forma: sqrt(spi_req / SPI), acotado a [forms.drain_spi_min, forms.drain_spi_max] (1.0 y
     * 6.0 por defecto, antes 1.5: con ese tope un personaje recién creado aguantaba un SSJ3 casi como uno entrenado; el SPI alto ya no abarata, solo el bajo encarece). spi_req = 0 -> 1.0 siempre (forma sin requisito, o dato de un datapack
     * viejo). El rango es corto a propósito: el SPI ya paga su aguante por otro lado (sube el pool
     * de ki y con él la regen), así que aquí solo mueve el coste un poco; antes llegaba a 0.5x-3x y
     * en late game (SPI de miles contra un requisito de decenas) dejaba cualquier forma gratis.
     */
    public double drainMultiplier(int spi) {
        if (spiReq <= 0) return 1.0;
        double raw = Math.sqrt(spiReq / (double) Math.max(spi, 1));
        return Math.max(ServerConfig.formDrainSpiMin(), Math.min(ServerConfig.formDrainSpiMax(), raw));
    }

    /**
     * Ki drenado por SEGUNDO por esta forma, el número real que se resta del pool en FormSystem:
     * <pre>pct/100 · max( poder · drain_scale · drainMultiplier(SPI) , drain_floor · energyMax )</pre>
     * El primer término copia al Kaioken (el coste lo marca tu poder, STR+WIL); el segundo es un
     * piso proporcional al pool para que un pool enorme no la deje gratis. Sin el factor de
     * "% de poder liberado": lo aplica quien lo conoce (FormSystem/StatsScreen).
     */
    public double kiDrainPerSecond(double mastery0to100, PlayerStatsAttachment att) {
        double pct = kiDrainPct(mastery0to100);
        if (pct <= 0.0) return 0.0;
        double powerTerm = BalanceUtil.powerStat(att) * ServerConfig.formDrainScale()
                * drainMultiplier(att.getAttribute(ZenkaiAttributes.SPIRIT));
        double floorTerm = ServerConfig.formDrainFloor() * att.getEnergyMax();
        return pct / 100.0 * Math.max(powerTerm, floorTerm);
    }

    /** Multiplicador del coste de FORZAR (powerPercent > 100%) mientras esta forma está puesta,
     *  interpolado por maestría igual que statPercent/kiDrainPerTick. 1.0 = sin efecto (default
     *  de una forma que no declara estos campos). */
    public double overdriveDrainMult(double mastery0to100) {
        return lerp(overdriveDrainMultUntrained, overdriveDrainMultMastered, mastery0to100 / 100.0);
    }

    /** ¿Lleva energía divina a efectos visuales/de entrenamiento (aro del aura, bono de Potential
     *  Unlock)? Cuenta los dioses de raza Y los peldaños de endgame, que dejaron de ser
     *  {@code divineTier} para no contaminar la rama divina de cada raza (ver DivineForms). */
    public boolean hasDivineEnergy() { return divineTier || endgamePath; }

    public boolean allows(Race race) { return race != null && races.contains(race); }

    // ── Visuales ────────────────────────────────────────────────────────────

    /**
     * Item de pelo para un peinado ("hair1", "hair2"...). null si esta forma no cambia el
     * pelo para ese peinado, en cuyo caso manda el pelo base del jugador.
     *
     * OJO: son REFERENCIAS a items ya registrados en Java. Un datapack puede elegir entre
     * los que existan, pero no crear uno nuevo: registrar items es cosa del código. Para
     * pelo totalmente libre habría que renderizar modelo+textura sin item de por medio.
     */
    public ResourceLocation hairItem(String hairStyle) {
        return hairStyle == null ? null : hairItems.get(hairStyle.toLowerCase(Locale.ROOT));
    }

    /** ¿Esta forma tiñe el pelo? -1 = no lo toca (el pelo va con su color propio).
     *  Con el pelo en escala de grises, un solo modelo sirve para cualquier forma. */
    public boolean tintsHair() { return hairRgb >= 0; }

    /**
     * Color RGB que esta forma impone a la capa tintable {@code index} del cuerpo (campo
     * "layer_colors" del JSON: {"0": "0xD9A21B", "2": ...}), pisando el que eligió el jugador
     * mientras la lleva puesta. -1 = la forma no toca esa capa. Permite que una forma reuse el
     * cuerpo y las capas de otra y solo cambie de color (Golden/Black Freezer sobre la forma final).
     */
    public int layerColor(int index) {
        Integer c = layerColors.get(index);
        return c == null ? -1 : c & 0xFFFFFF;
    }

    /**
     * ¿Esta forma lleva la capa de PELAJE del Saiyan (campo "fur_rgb", textura gris
     * saiyan_fur*.png sobre torso y brazos, tintada con este color)? -1 = no la lleva. Solo se
     * pinta en el Saiyan con el cuerpo humano; otra raza con este campo no ve nada. SSJ4 la declara
     * en rojo; una forma propia (un "SSJ5" gris) pone su color sin tocar texturas.
     */
    public boolean hasFur() { return furRgb >= 0; }

    /**
     * ¿Esta forma NO lleva cejas (campo "no_brows")? Oculta las capas de ceja del ojo ("eyebrow": true) mientras
     * está activa, como el SSJ3 del anime, que no tiene. El resto del ojo se pinta igual.
     */
    public boolean hidesBrows() { return noBrows; }

    /** ¿Esta forma declara un color de cola propio? -1 = cae al color natural
     *  (TailResolver.DEFAULT_TAIL_RGB) — la cola nunca es "sin tinte" de verdad. */
    public boolean tintsTail() { return tailRgb >= 0; }

    /** ¿Esta forma pide su propia capa exterior de aura (dos tonos)? -1 = solo capa simple. */
    public boolean hasAuraOuter() { return auraOuterRgb >= 0; }

    /** ¿Esta forma impone su propio color de ojos? -1 = manda el elegido por el jugador
     *  (PlayerVisualAttachment.eyeColorRgb), sin tocarlo. */
    public boolean tintsEyes() { return eyeRgb >= 0; }

    /** ¿Esta forma tiñe la capa "brow" (cejas/párpado de los ojos, que por defecto sigue al pelo)?
     *  -1 = siguen al pelo como siempre. Ultra Ego las pone moradas sin teñir el pelo. */
    public boolean tintsBrows() { return browRgb >= 0; }

    /** Item de cuerpo para un slot ("head", "chest", "legs", "feet"). null = no lo cambia. */
    public ResourceLocation bodyItem(String slot) {
        return slot == null ? null : bodyItems.get(slot.toLowerCase(Locale.ROOT));
    }

    // ── StreamCodec manual (11 campos + lista de razas) ─────────────────────

    public static final StreamCodec<FriendlyByteBuf, FormDef> STREAM_CODEC = StreamCodec.of(
            (buf, d) -> {
                buf.writeResourceLocation(d.id());
                buf.writeVarInt(d.races().size());
                for (Race r : d.races()) buf.writeVarInt(r.ordinal());
                buf.writeVarInt(d.kind().ordinal());
                buf.writeBoolean(d.parent() != null);
                if (d.parent() != null) buf.writeResourceLocation(d.parent());
                buf.writeVarInt(d.tpCost());
                buf.writeVarInt(d.holdTicks());
                buf.writeDouble(d.masteryGain());
                buf.writeDouble(d.statPercentUntrained());
                buf.writeDouble(d.statPercentMastered());
                buf.writeDouble(d.kiDrainUntrained());
                buf.writeDouble(d.kiDrainMastered());
                buf.writeVarInt(d.spiReq());
                writeMap(buf, d.hairItems());
                writeMap(buf, d.bodyItems());
                buf.writeUtf(d.auraType());
                buf.writeInt(d.auraRgb());
                buf.writeInt(d.hairRgb());
                buf.writeInt(d.tailRgb());
                buf.writeDouble(d.scale());
                buf.writeBoolean(d.descendable());
                buf.writeDouble(d.overdriveCeilingBonus());
                buf.writeDouble(d.overdriveDrainMultUntrained());
                buf.writeDouble(d.overdriveDrainMultMastered());
                buf.writeBoolean(d.wheelSelectable());
                buf.writeBoolean(d.divineTier());
                buf.writeInt(d.auraOuterRgb());
                buf.writeInt(d.eyeRgb());
                buf.writeInt(d.browRgb());
                buf.writeVarInt(d.tier().ordinal());
                buf.writeBoolean(d.endgamePath());
                buf.writeVarInt(d.alignmentBranch().ordinal());
                buf.writeVarInt(d.masteryReqs().size());
                for (var e : d.masteryReqs().entrySet()) {
                    buf.writeResourceLocation(e.getKey());
                    buf.writeDouble(e.getValue());
                }
                buf.writeUtf(d.category(), 32);
                buf.writeVarInt(d.layerColors().size());
                for (var e : d.layerColors().entrySet()) {
                    buf.writeVarInt(e.getKey());
                    buf.writeInt(e.getValue());
                }
                buf.writeInt(d.furRgb());
                buf.writeBoolean(d.noBrows());
            },
            buf -> {
                ResourceLocation id = buf.readResourceLocation();
                int n = buf.readVarInt();
                List<Race> list = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    int o = buf.readVarInt();
                    if (o >= 0 && o < Race.values().length) list.add(Race.values()[o]);
                }
                EnumSet<Race> races = list.isEmpty() ? EnumSet.noneOf(Race.class) : EnumSet.copyOf(list);
                int k = buf.readVarInt();
                Kind kind = (k >= 0 && k < Kind.values().length) ? Kind.values()[k] : Kind.SUPER;
                ResourceLocation parent = buf.readBoolean() ? buf.readResourceLocation() : null;
                return new FormDef(id, races, kind, parent,
                        buf.readVarInt(), buf.readVarInt(),
                        buf.readDouble(),
                        buf.readDouble(), buf.readDouble(),
                        buf.readDouble(), buf.readDouble(), buf.readVarInt(),
                        readMap(buf), readMap(buf),
                        buf.readUtf(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readDouble(),
                        buf.readBoolean(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                        buf.readBoolean(), buf.readBoolean(), buf.readInt(), buf.readInt(), buf.readInt(),
                        readTier(buf), buf.readBoolean(), readAlign(buf), readReqs(buf),
                        buf.readUtf(32), readLayerColors(buf), buf.readInt(), buf.readBoolean());
            });

    private static Map<Integer, Integer> readLayerColors(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        Map<Integer, Integer> out = new java.util.LinkedHashMap<>();
        for (int i = 0; i < n; i++) out.put(buf.readVarInt(), buf.readInt());
        return out;
    }

    private static Tier readTier(FriendlyByteBuf buf) {
        int o = buf.readVarInt();
        return (o >= 0 && o < Tier.values().length) ? Tier.values()[o] : Tier.NONE;
    }

    private static AlignBranch readAlign(FriendlyByteBuf buf) {
        int o = buf.readVarInt();
        return (o >= 0 && o < AlignBranch.values().length) ? AlignBranch.values()[o] : AlignBranch.NONE;
    }

    private static Map<ResourceLocation, Double> readReqs(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        Map<ResourceLocation, Double> m = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) m.put(buf.readResourceLocation(), buf.readDouble());
        return Collections.unmodifiableMap(m);
    }

    private static void writeMap(FriendlyByteBuf buf, Map<String, ResourceLocation> map) {
        buf.writeVarInt(map.size());
        for (var e : map.entrySet()) {
            buf.writeUtf(e.getKey());
            buf.writeResourceLocation(e.getValue());
        }
    }

    private static Map<String, ResourceLocation> readMap(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        Map<String, ResourceLocation> map = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) map.put(buf.readUtf(), buf.readResourceLocation());
        return Collections.unmodifiableMap(map);
    }
}