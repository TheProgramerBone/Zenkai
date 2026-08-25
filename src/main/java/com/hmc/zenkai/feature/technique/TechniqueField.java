package com.hmc.zenkai.feature.technique;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hmc.zenkai.feature.technique.TechniqueDef.Kind;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.GsonHelper;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;

/**
 * AUTORIDAD ÚNICA de los campos de {@link TechniqueDef}. Lo que se puede saber de un
 * campo vive aquí y solo aquí:
 *   clave JSON · tipo · parseo (texto, JSON y NBT) · clamp · default de fábrica ·
 *   a qué {@link Kind} pertenece · serialización para el volcado.
 * Existe porque el mismo campo lo tocan tres frentes que antes no se hablaban:
 *   Loader (datapack) → Command (edición en caliente) → Dump (volcado).
 * Con los {@code GsonHelper.getAs*} sueltos dentro del Loader, cada frente habría tenido su
 * propia copia del default y del clamp, y bastaba tocar uno para que el juego y el JSON
 * dejaran de decir lo mismo.
 * CAMPOS POR KIND: un campo que no aplica a un kind NO se lee del JSON ni se puede editar, y
 * el record recibe su valor neutro (0 / false). Antes el Loader leía los quince campos para
 * ambos kinds, así que una técnica de ki cargaba {@code range = 3.0} y una física
 * {@code speed = 1.0}: números que nadie consultaba pero que aparecían en pantalla y en el
 * paquete de sincronización. La única lectura cruzada que existía —{@code range()} en las
 * físicas— sigue intacta.
 * CLAMPS: los que ya hacía el Loader ({@code count >= 0}, {@code anim_ticks >= 1}, RGB a 24
 * bits) más un suelo por campo que antes no existía porque el JSON lo escribía yo y nunca
 * ponía negativos. Un comando sí puede: {@code charge_ticks} a 0 divide por cero en el
 * progreso de carga del cliente, y un coste negativo regala TP.
 * MASTER es el único campo STRING: id de maestro, o "" para "desbloqueable normal". Es el
 * cimiento de "técnica firma" (ver su propio comentario más abajo) — no cambia nada del tipo
 * ni de la malla, solo QUIÉN puede concederla.
 */
public enum TechniqueField {

    TP_COST       ("tp_cost",        ValueType.INT,    0,        0, Integer.MAX_VALUE, Kind.KI, Kind.PHYSICAL),
    MIND_REQ      ("mind_req",       ValueType.INT,    0,        0, Integer.MAX_VALUE, Kind.KI, Kind.PHYSICAL),
    /** "" = desbloqueable normal (TP, sin maestro). Con un id, solo SU maestro la enseña — ver
     *  TechniquePacket/PhysicalTechniquePacket.handleUnlock, mismo embudo que SkillDef.master()
     *  ya usa para el nivel 1 de una habilidad con maestro (SkillBuyPacket). Es el cimiento de
     *  "técnica firma": una técnica normal en lo demás (mismo tipo, mismo código), pero
     *  que un jugador no puede autodesbloquearse desde el editor. */
    MASTER        ("master",        ValueType.STRING, "",                              Kind.KI, Kind.PHYSICAL),
    DAMAGE_MULT   ("damage_mult",    ValueType.DOUBLE, 1.0,    0.0, Double.MAX_VALUE,  Kind.KI, Kind.PHYSICAL),
    KI_COST_MULT  ("ki_cost_mult",   ValueType.DOUBLE, 1.0,    0.0, Double.MAX_VALUE,  Kind.KI),
    STAMINA_PCT   ("stamina_pct",    ValueType.DOUBLE, 0.0,    0.0, Double.MAX_VALUE,  Kind.PHYSICAL),
    CHARGE_TICKS  ("charge_ticks",   ValueType.INT,    20,       1, Integer.MAX_VALUE, Kind.KI),
    COOLDOWN_TICKS("cooldown_ticks", ValueType.INT,    20,       0, Integer.MAX_VALUE, Kind.KI, Kind.PHYSICAL),
    SPEED         ("speed",          ValueType.DOUBLE, 1.0,    0.0, Double.MAX_VALUE,  Kind.KI),
    COUNT         ("count",          ValueType.INT,    1,        0, Integer.MAX_VALUE, Kind.KI),
    DEFENSIVE     ("defensive",      ValueType.BOOL,   false,                          Kind.KI),
    DEFAULT_RGB   ("default_rgb",    ValueType.RGB,    0xFFFFFF,                       Kind.KI),
    RANGE         ("range",          ValueType.DOUBLE, 3.0,    0.0, Double.MAX_VALUE,  Kind.PHYSICAL),
    ANIM_TICKS    ("anim_ticks",     ValueType.INT,    12,       1, Integer.MAX_VALUE, Kind.KI, Kind.PHYSICAL);

    /** Tipo de dato del campo. Decide parseo, clamp, formato y cómo viaja a JSON/NBT. */
    public enum ValueType { INT, DOUBLE, BOOL, RGB, STRING }

    private final String key;
    private final ValueType type;
    private final Object factory;
    private final double min;
    private final double max;
    private final EnumSet<Kind> kinds;

    TechniqueField(String key, ValueType type, int factory, int min, int max, Kind... kinds) {
        this(key, type, (Object) factory, min, max, set(kinds));
    }

    TechniqueField(String key, ValueType type, double factory, double min, double max, Kind... kinds) {
        this(key, type, (Object) factory, min, max, set(kinds));
    }

    TechniqueField(String key, ValueType type, boolean factory, Kind... kinds) {
        this(key, type, (Object) factory, 0.0, 0.0, set(kinds));
    }

    TechniqueField(String key, ValueType type, int factory, Kind... kinds) {
        this(key, type, (Object) factory, 0.0, 0.0, set(kinds));
    }

    TechniqueField(String key, ValueType type, String factory, Kind... kinds) {
        this(key, type, (Object) factory, 0.0, 0.0, set(kinds));
    }

    /**
     * Constructor común. Recibe un EnumSet y NO {@code Kind...} a propósito: un constructor
     * varargs solo se considera en la fase de resolución que admite boxing, y ahí este y el de
     * {@code double} eran igual de aplicables sin que ninguno fuese más específico ({@code double}
     * y {@code Object} no se comparan). El tipo distinto del último parámetro lo saca del
     * concurso de sobrecargas sin tocar las firmas públicas.
     */
    private TechniqueField(String key, ValueType type, Object factory, double min, double max,
                           EnumSet<Kind> kinds) {
        this.key = key;
        this.type = type;
        this.factory = factory;
        this.min = min;
        this.max = max;
        this.kinds = kinds;
    }

    private static EnumSet<Kind> set(Kind... kinds) {
        EnumSet<Kind> s = EnumSet.noneOf(Kind.class);
        java.util.Collections.addAll(s, kinds);
        return s;
    }

    // ── Identidad ────────────────────────────────────────────────────────────

    /** Clave en el JSON del datapack y nombre del campo en los comandos: son el MISMO texto. */
    public String key() { return key; }

    public ValueType type() { return type; }

    /** Valor de fábrica: el que usaba el Loader cuando el JSON no declaraba la clave. */
    public Object factoryDefault() { return factory; }

    public boolean applies(Kind kind) { return kinds.contains(kind); }

    // ── Valor ────────────────────────────────────────────────────────────────

    /** Ajusta al rango legal del campo. Se aplica en cualquier entrada: JSON, NBT y comando. */
    public Object clamp(Object raw) {
        return switch (type) {
            case INT -> {
                long v = ((Number) raw).longValue();
                yield (int) Math.max((long) min, Math.min((long) max, v));
            }
            case DOUBLE -> Math.max(min, Math.min(max, ((Number) raw).doubleValue()));
            case BOOL -> (Boolean) raw;
            case RGB -> ((Number) raw).intValue() & 0xFFFFFF;
            // Id de maestro: minúsculas (mismo formato que MasterDef/ZenkaiMasterEntity#masterId)
            // y recortado a 32 -- igual que el resto de ids cortos que viajan por packet en el mod.
            case STRING -> {
                String s = ((String) raw).trim().toLowerCase(Locale.ROOT);
                yield s.length() > 32 ? s.substring(0, 32) : s;
            }
        };
    }

    /**
     * Parsea lo que escribe el jugador en el comando. Lanza {@link IllegalArgumentException}
     * (incluida {@code NumberFormatException}) si el texto no vale: el comando lo convierte en
     * un sendFailure con el formato esperado.
     */
    public Object parse(String raw) {
        String s = raw.trim();
        return switch (type) {
            case INT -> clamp(Integer.parseInt(s));
            case DOUBLE -> clamp(Double.parseDouble(s));
            case BOOL -> {
                if (s.equalsIgnoreCase("true")) yield Boolean.TRUE;
                if (s.equalsIgnoreCase("false")) yield Boolean.FALSE;
                throw new IllegalArgumentException("se esperaba true o false");
            }
            case RGB -> clamp(Integer.decode(s.replace("#", "0x")));
            case STRING -> clamp(s);
        };
    }

    /** null = el JSON no declara la clave (y entonces el valor efectivo es el de fábrica). */
    public Object readJson(JsonObject o) {
        if (!o.has(key)) return null;
        return switch (type) {
            case INT -> clamp(GsonHelper.getAsInt(o, key));
            case DOUBLE -> clamp(GsonHelper.getAsDouble(o, key));
            case BOOL -> GsonHelper.getAsBoolean(o, key);
            case RGB -> {
                // Acepta 12345 o "0x55AAFF" / "#55AAFF" (venía de TechniqueManager.readRgb).
                JsonElement el = o.get(key);
                if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
                    yield Integer.decode(el.getAsString().trim().replace("#", "0x")) & 0xFFFFFF;
                }
                yield el.getAsInt() & 0xFFFFFF;
            }
            case STRING -> clamp(GsonHelper.getAsString(o, key));
        };
    }

    /** El RGB se vuelca como "0xRRGGBB" a propósito: un entero decimal ahí no se lee. */
    public void writeJson(JsonObject o, Object v) {
        switch (type) {
            case INT -> o.addProperty(key, ((Number) v).intValue());
            case DOUBLE -> o.addProperty(key, ((Number) v).doubleValue());
            case BOOL -> o.addProperty(key, (Boolean) v);
            case RGB -> o.addProperty(key, format(v));
            case STRING -> o.addProperty(key, (String) v);
        }
    }

    public void writeNbt(CompoundTag tag, Object v) {
        switch (type) {
            case INT, RGB -> tag.putInt(key, ((Number) v).intValue());
            case DOUBLE -> tag.putDouble(key, ((Number) v).doubleValue());
            case BOOL -> tag.putBoolean(key, (Boolean) v);
            case STRING -> tag.putString(key, (String) v);
        }
    }

    /** null = este override no existe. */
    public Object readNbt(CompoundTag tag) {
        if (!tag.contains(key)) return null;
        return switch (type) {
            case INT, RGB -> clamp(tag.getInt(key));
            case DOUBLE -> clamp(tag.getDouble(key));
            case BOOL -> tag.getBoolean(key);
            case STRING -> clamp(tag.getString(key));
        };
    }

    /** Cómo se enseña en chat. */
    public String format(Object v) {
        return switch (type) {
            case INT -> String.valueOf(((Number) v).intValue());
            case BOOL -> String.valueOf((Boolean) v);
            case RGB -> String.format(Locale.ROOT, "0x%06X", ((Number) v).intValue() & 0xFFFFFF);
            case DOUBLE -> {
                double d = ((Number) v).doubleValue();
                yield (d == Math.rint(d) && Math.abs(d) < 1.0E9)
                        ? String.format(Locale.ROOT, "%.1f", d)
                        : String.valueOf(d);
            }
            case STRING -> (String) v;
        };
    }

    /** Lee este campo de un def ya construido. Exhaustivo sin default: un campo nuevo no
     *  compila hasta que se decide de dónde sale. */
    public Object get(TechniqueDef d) {
        return switch (this) {
            case TP_COST -> d.tpCost();
            case MIND_REQ -> d.mindReq();
            case MASTER -> d.master();
            case DAMAGE_MULT -> d.damageMult();
            case KI_COST_MULT -> d.kiCostMult();
            case STAMINA_PCT -> d.staminaPct();
            case CHARGE_TICKS -> d.chargeTicks();
            case COOLDOWN_TICKS -> d.cooldownTicks();
            case SPEED -> d.speed();
            case COUNT -> d.count();
            case DEFENSIVE -> d.defensive();
            case DEFAULT_RGB -> d.defaultRgb();
            case RANGE -> d.range();
            case ANIM_TICKS -> d.animTicks();
        };
    }

    // ── Estáticos ────────────────────────────────────────────────────────────

    public static TechniqueField byKey(String k) {
        String s = k.trim().toLowerCase(Locale.ROOT);
        for (TechniqueField f : values()) if (f.key.equals(s)) return f;
        return null;
    }

    /** Los campos editables de un kind, en orden de declaración. */
    public static java.util.List<TechniqueField> of(Kind kind) {
        java.util.List<TechniqueField> out = new java.util.ArrayList<>();
        for (TechniqueField f : values()) if (f.applies(kind)) out.add(f);
        return out;
    }

    /** Punto de partida de una técnica que no tiene JSON: de fábrica. */
    public static EnumMap<TechniqueField, Object> factoryValues(Kind kind) {
        EnumMap<TechniqueField, Object> m = new EnumMap<>(TechniqueField.class);
        for (TechniqueField f : values()) if (f.applies(kind)) m.put(f, f.factory);
        return m;
    }

    /** Único sitio donde se construye un {@link TechniqueDef} a partir de valores sueltos.
     *  Los campos ausentes (los que no aplican al kind) quedan en su valor neutro. */
    public static TechniqueDef build(String id, Kind kind, Map<TechniqueField, Object> v) {
        return new TechniqueDef(id, kind,
                i(v, TP_COST), i(v, MIND_REQ), s(v, MASTER),
                d(v, DAMAGE_MULT), d(v, KI_COST_MULT), d(v, STAMINA_PCT),
                i(v, CHARGE_TICKS), i(v, COOLDOWN_TICKS),
                d(v, SPEED), i(v, COUNT), b(v, DEFENSIVE),
                i(v, DEFAULT_RGB), d(v, RANGE), i(v, ANIM_TICKS));
    }

    private static int i(Map<TechniqueField, Object> v, TechniqueField f) {
        Object o = v.get(f);
        return o instanceof Number n ? n.intValue() : 0;
    }

    private static double d(Map<TechniqueField, Object> v, TechniqueField f) {
        Object o = v.get(f);
        return o instanceof Number n ? n.doubleValue() : 0.0;
    }

    private static boolean b(Map<TechniqueField, Object> v, TechniqueField f) {
        Object o = v.get(f);
        return o instanceof Boolean bo && bo;
    }

    private static String s(Map<TechniqueField, Object> v, TechniqueField f) {
        Object o = v.get(f);
        return o instanceof String str ? str : "";
    }
}