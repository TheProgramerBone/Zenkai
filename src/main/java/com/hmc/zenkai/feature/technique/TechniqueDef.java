package com.hmc.zenkai.feature.technique;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Números de una técnica. Viven en datapack:
 *   data/&lt;ns&gt;/zenkai_techniques/ki/&lt;id&gt;.json
 *   data/&lt;ns&gt;/zenkai_techniques/physical/&lt;id&gt;.json
 * y se sincronizan al cliente con TechniqueSyncPacket (ver TechniqueManager).
 * El id es el nombre del enum en minúsculas. Los enums (KiTechniqueType /
 * PhysicalTechnique) siguen siendo la IDENTIDAD: nombre = clave NBT, ordinal = celda
 * del atlas de íconos, y la estela sigue codificada ahí (es visual, no balance).
 * SIN JSON = TÉCNICA DESACTIVADA: no se puede desbloquear, ni guardar, ni disparar,
 * y no aparece en las pantallas. Los getters del enum devuelven valores neutros y
 * los costes devuelven Integer.MAX_VALUE para que nada sea "asequible" por accidente.
 * Campos según kind (los que no aplican quedan a 0):
 *  - KI:       damage_mult, ki_cost_mult, charge_ticks, cooldown_ticks, speed, count,
 *              defensive, default_rgb
 *  - PHYSICAL: damage_mult, stamina_pct, cooldown_ticks, range
 * Comunes: tp_cost (coste de desbloqueo), mind_req (MND mínimo para desbloquear), master
 *          (id de maestro; "" = desbloqueable normal, ver abajo),
 *          anim_ticks (duración del ESTADO VISUAL sincronizado; ver abajo).
 * anim_ticks NO es duración de gameplay. El golpe instantáneo sigue resolviéndose en su
 * tick: el daño, el coste y el cooldown no dependen de esto. Solo dice cuánto se mantiene
 * el ActionState para que los demás jugadores vean la animación completa.
 *
 * TÉCNICA FIRMA (cimiento para "el maestro X enseña la técnica Y", ver conversación de
 * diseño): con {@code master} puesto, el tipo/técnica sigue siendo EXACTAMENTE el mismo
 * código (mismo KiTechniqueType o PhysicalTechnique, mismo KiVisual, mismo proyectil) pero
 * dos cosas cambian:
 *  1) {@link KiTechniqueType#master()}/{@link PhysicalTechnique#master()} != "" bloquea el
 *     desbloqueo genérico por TP (TechniquePacket/PhysicalTechniquePacket.handleUnlock): solo
 *     se puede desbloquear delante de ESE maestro, mismo embudo (MasterManager.check) que ya
 *     usa SkillDef.master() para el nivel 1 de una habilidad con maestro.
 *  2) Para ki, el COLOR de la instancia queda fijo a {@code default_rgb} — TechniquePacket.
 *     handleSave lo fuerza igual que ya fuerza el TIPO en modo edición. El resto (nombre,
 *     tamaño, efecto, sonidos, animSet) sigue siendo elección normal del jugador.
 * Para una técnica de verdad más fuerte que las genéricas del mismo tipo (más daño, más
 * radio de explosión que cualquier size 5...) hace falta además su PROPIO KiTechniqueType/
 * PhysicalTechnique (nuevo valor de enum, con su propio switch en KiVisual/
 * KiTechniqueType#projectileSize etc. — igual que EXPLOSION o BARRIER hoy) apuntando a este
 * mismo id de maestro: el número base de ESE tipo puede partir ya por encima de lo que
 * cualquier size 1..5 del tipo genérico alcanza (mismo patrón que EXPLOSION/BIG_BLAST, ver
 * git history). Ningún tipo nuevo se ha añadido todavía — esto es solo el mecanismo.
 */
public record TechniqueDef(String id, Kind kind, int tpCost, int mindReq, String master,
                           double damageMult, double kiCostMult, double staminaPct,
                           int chargeTicks, int cooldownTicks, double speed,
                           int count, boolean defensive, int defaultRgb, double range, int animTicks) {

    public enum Kind {
        KI, PHYSICAL;

        /** Nombre de la subcarpeta del datapack. */
        public String folder() { return name().toLowerCase(Locale.ROOT); }

        public static Kind byFolder(String s) {
            for (Kind k : values()) if (k.folder().equals(s)) return k;
            return null;
        }
    }

    private static volatile Map<String, TechniqueDef> REGISTRY = Map.of();

    private static String key(Kind kind, String id) { return kind.name() + "/" + id; }

    /** Reemplaza el snapshot completo (reload del server o sync al cliente). */
    public static void replaceAll(Map<String, TechniqueDef> defs) {
        Map<String, TechniqueDef> m = new LinkedHashMap<>();
        for (TechniqueDef d : defs.values()) m.put(key(d.kind(), d.id()), d);
        REGISTRY = Collections.unmodifiableMap(m);
    }

    /** null si la técnica no está definida en ningún datapack (= desactivada). */
    public static TechniqueDef get(Kind kind, String id) { return REGISTRY.get(key(kind, id)); }

    public static java.util.Collection<TechniqueDef> all() { return REGISTRY.values(); }

    // StreamCodec manual: 14 campos, muy por encima de lo que cubre composite.
    public static final StreamCodec<FriendlyByteBuf, TechniqueDef> STREAM_CODEC = StreamCodec.of(
            (buf, d) -> {
                buf.writeUtf(d.id());
                buf.writeVarInt(d.kind().ordinal());
                buf.writeVarInt(d.tpCost());
                buf.writeVarInt(d.mindReq());
                buf.writeUtf(d.master(), 32);
                buf.writeDouble(d.damageMult());
                buf.writeDouble(d.kiCostMult());
                buf.writeDouble(d.staminaPct());
                buf.writeVarInt(d.chargeTicks());
                buf.writeVarInt(d.cooldownTicks());
                buf.writeDouble(d.speed());
                buf.writeVarInt(d.count());
                buf.writeBoolean(d.defensive());
                buf.writeInt(d.defaultRgb());
                buf.writeDouble(d.range());
                buf.writeVarInt(d.animTicks());
            },
            buf -> {
                String id = buf.readUtf();
                int k = buf.readVarInt();
                Kind kind = (k >= 0 && k < Kind.values().length) ? Kind.values()[k] : Kind.KI;
                return new TechniqueDef(id, kind,
                        buf.readVarInt(), buf.readVarInt(), buf.readUtf(32),
                        buf.readDouble(), buf.readDouble(), buf.readDouble(),
                        buf.readVarInt(), buf.readVarInt(),
                        buf.readDouble(), buf.readVarInt(), buf.readBoolean(),
                        buf.readInt(), buf.readDouble(), buf.readVarInt());
            });
}