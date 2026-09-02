package com.hmc.zenkai.feature.forms;

import com.hmc.zenkai.feature.Race;
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
 * SPI_REQ no es un candado: la forma se puede activar con cualquier SPI, pero el drenaje base
 * (ya interpolado por maestría) se multiplica por drainMultiplier(spi), que sube hasta 3x con
 * SPI muy por debajo del requisito y baja hasta 0.5x muy por encima. Antes el drenaje era un
 * número fijo del datapack sin relación con SPI, así que con la maestría dominada CUALQUIER
 * transformación (incluida la última de la cadena) se sostenía indefinidamente con el SPI base
 * de raza sin invertir un solo punto: el regen (1% del pool/seg) escala con el pool, que escala
 * con SPI, así que un pool minúsculo ya bastaba para superar un drenaje fijo minúsculo. spi_req
 * = 0 desactiva el multiplicador (queda en 1.0 siempre), para no romper datapacks viejos.
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
 */
public record FormDef(ResourceLocation id, EnumSet<Race> races, Kind kind,
                      ResourceLocation parent, int tpCost, int holdTicks,
                      double masteryGain,
                      double statPercentUntrained, double statPercentMastered,
                      double kiDrainUntrained, double kiDrainMastered, int spiReq,
                      Map<String, ResourceLocation> hairItems,
                      Map<String, ResourceLocation> bodyItems,
                      String auraType, int auraRgb, int hairRgb, double scale,
                      boolean descendable, double overdriveCeilingBonus,
                      double overdriveDrainMultUntrained, double overdriveDrainMultMastered,
                      boolean wheelSelectable, boolean divineTier) {

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

    /** Fracción que SUMA al multiplicador total (1.5 = +150%), según la maestría. */
    public double statPercent(double mastery0to100) {
        return lerp(statPercentUntrained, statPercentMastered, mastery0to100 / 100.0);
    }

    /** Ki drenado por tick, según la maestría (baja al dominarla). SIN el multiplicador de
     *  SPI: es el número "de catálogo" que ya usaba la pantalla de maestrías. */
    public double kiDrainPerTick(double mastery0to100) {
        return lerp(kiDrainUntrained, kiDrainMastered, mastery0to100 / 100.0);
    }

    /** Techo/suelo del multiplicador de drenaje por SPI. Fuera de estos límites ni una
     *  inversión enorme abarata más el sostenimiento, ni un SPI ínfimo lo encarece sin límite. */
    private static final double MIN_DRAIN_MULT = 0.5;
    private static final double MAX_DRAIN_MULT = 3.0;

    /**
     * Multiplicador de drenaje según cuánto SPI tenga el jugador respecto al requisito de la
     * forma. spi_req = 0 -> 1.0 siempre (forma sin requisito, o dato de un datapack viejo).
     * SPI en el requisito -> 1.0 (paridad con el drenaje base). Por debajo escala hasta 3x;
     * por encima, hasta 0.5x. La curva es intencionalmente simple (razón inversa) para que se
     * pueda razonar de cabeza: la mitad del requisito ya duplica el drenaje.
     */
    public double drainMultiplier(int spi) {
        if (spiReq <= 0) return 1.0;
        double raw = spiReq / (double) Math.max(spi, 1);
        return Math.max(MIN_DRAIN_MULT, Math.min(MAX_DRAIN_MULT, raw));
    }

    /** Ki drenado por tick YA CON el multiplicador de SPI aplicado: el número real que se
     *  resta del pool en FormSystem. */
    public double effectiveKiDrainPerTick(double mastery0to100, int spi) {
        return kiDrainPerTick(mastery0to100) * drainMultiplier(spi);
    }

    /** Multiplicador del coste de FORZAR (powerPercent > 100%) mientras esta forma está puesta,
     *  interpolado por maestría igual que statPercent/kiDrainPerTick. 1.0 = sin efecto (default
     *  de una forma que no declara estos campos). */
    public double overdriveDrainMult(double mastery0to100) {
        return lerp(overdriveDrainMultUntrained, overdriveDrainMultMastered, mastery0to100 / 100.0);
    }

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
                buf.writeDouble(d.scale());
                buf.writeBoolean(d.descendable());
                buf.writeDouble(d.overdriveCeilingBonus());
                buf.writeDouble(d.overdriveDrainMultUntrained());
                buf.writeDouble(d.overdriveDrainMultMastered());
                buf.writeBoolean(d.wheelSelectable());
                buf.writeBoolean(d.divineTier());
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
                        buf.readUtf(), buf.readInt(), buf.readInt(), buf.readDouble(),
                        buf.readBoolean(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                        buf.readBoolean(), buf.readBoolean());
            });

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