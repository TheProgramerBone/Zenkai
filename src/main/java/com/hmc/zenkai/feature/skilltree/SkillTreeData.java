package com.hmc.zenkai.feature.skilltree;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.forms.FormDef;
import com.hmc.zenkai.feature.skills.SkillDef;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Lo que el JSON del árbol añade a un nodo (diseño §2, «Datos y compra»): requisitos extra,
 * pista, si es secreto y cuándo se revela, por qué vías se puede conseguir, y —para los nodos sin
 * skill detrás, como las pasivas de raza— su curva por nivel (coste, MIND y valores). Los
 * costes de las FORMAS siguen en {@code FormDef.tp_cost} y la jerarquía en {@code FormDef.parent}:
 * aquí no se repiten.
 *
 * Ruta: {@code data/<ns>/zenkai_skill_tree/<archivo>.json}, un archivo por nodo, con el id del
 * nodo en el campo {@code "node"} (una forma usa su propio id: {@code "zenkai:ssj1"}). Un
 * datapack o addon añade o sobrescribe nodos con el mismo mecanismo (ver ADDONS.md). Un nodo SIN
 * archivo funciona igual que antes de F3: sin requisitos extra, sin pista y visible.
 *
 * Campos (opcionales salvo "node"):
 * <pre>
 *   "requirements": [ ...SkillTreeRequirement... ]   se exigen para comprar/conseguir
 *   "hint": "clave.de.lang"                           por defecto skill_tree.&lt;ns&gt;.&lt;ruta con puntos&gt;.hint
 *   "secret": true, "reveal": [ ...requisitos... ]    no se dibuja hasta cumplir "reveal"
 *   "acquisition": ["tp", "ritual"]                   vías; cualquiera distinta de "tp" regala el nodo
 *   "race": "saiyan"                                  nodos propios de una raza (pasivas)
 *   "max_level": 4, "tp_cost": [..], "mind_req": [..], "values": {"clave": [..]}   nodos con niveles propios
 * </pre>
 *
 * Se carga en el servidor (SkillTreeDataManager) y viaja al cliente como el JSON crudo de cada
 * archivo (SkillTreeDataSyncPacket), que el cliente parsea con esta MISMA clase: una sola
 * lectura del formato, sin un codec aparte que mantener en paralelo.
 */
public record SkillTreeData(ResourceLocation node,
                            List<SkillTreeRequirement> requirements,
                            @Nullable String hint,
                            boolean secret,
                            List<SkillTreeRequirement> reveal,
                            Set<String> acquisition,
                            @Nullable Race race,
                            int maxLevel,
                            List<Integer> tpCost,
                            List<Integer> mindReq,
                            Map<String, List<Double>> values) {

    private static final Logger LOGGER = LoggerFactory.getLogger("Zenkai-SkillTree");

    /** Vía por defecto: comprar con TP. */
    public static final String VIA_TP = "tp";
    public static final String VIA_RITUAL = "ritual";
    public static final String VIA_MASTER = "master";

    private static volatile Map<ResourceLocation, SkillTreeData> ALL = Map.of();
    /** JSON crudo por nodo, tal cual se sincroniza al cliente. */
    private static volatile Map<ResourceLocation, String> RAW = Map.of();

    public static @Nullable SkillTreeData get(@Nullable ResourceLocation node) {
        return node == null ? null : ALL.get(node);
    }

    public static Map<ResourceLocation, SkillTreeData> all() { return ALL; }

    public static Map<ResourceLocation, String> raw() { return RAW; }

    /** Sustituye el registro entero desde el JSON crudo (servidor tras /reload, cliente al sync). */
    public static void replaceAllFromRaw(Map<ResourceLocation, String> raw) {
        Map<ResourceLocation, SkillTreeData> out = new LinkedHashMap<>();
        Map<ResourceLocation, String> kept = new LinkedHashMap<>();
        raw.forEach((file, json) -> {
            try {
                SkillTreeData d = parse(JsonParser.parseString(json).getAsJsonObject());
                out.put(d.node(), d);
                kept.put(file, json);
            } catch (Exception ex) {
                LOGGER.error("[Zenkai] Nodo del árbol inválido en {}: {}", file, ex.toString());
            }
        });
        ALL = Collections.unmodifiableMap(out);
        RAW = Collections.unmodifiableMap(kept);
    }

    // ── Atajos de lectura ────────────────────────────────────────────────────

    /** ¿Se puede conseguir por una vía que no sea TP? Esa vía lo regala (idea del usuario §9). */
    public boolean hasSpecialVia() {
        for (String v : acquisition) if (!VIA_TP.equals(v)) return true;
        return false;
    }

    /** ¿Se compra con TP? Sin "acquisition" en el JSON, sí. */
    public boolean purchasableWithTp() { return acquisition.isEmpty() || acquisition.contains(VIA_TP); }

    /** Coste en TP del nivel {@code level} (1..max) de un nodo con niveles propios, o -1. */
    public int tpCostFor(int level) {
        return level >= 1 && level <= tpCost.size() ? tpCost.get(level - 1) : -1;
    }

    /** MIND acumulada que pide tener el nivel {@code level} (0 si no hay curva). */
    public int mindReqFor(int level) {
        return level >= 1 && level <= mindReq.size() ? mindReq.get(level - 1) : 0;
    }

    /** Valor de la curva {@code key} en ese nivel (1..), o {@code fallback}. Mismo criterio que SkillDef.value. */
    public double value(String key, int level, double fallback) {
        List<Double> l = values.get(key);
        if (l == null || l.isEmpty() || level < 1) return fallback;
        return l.get(Math.min(level, l.size()) - 1);
    }

    /** Clave de lang de la pista: la del JSON o la derivada del id del nodo. */
    public static String hintKey(ResourceLocation node) {
        SkillTreeData d = get(node);
        if (d != null && d.hint() != null) return d.hint();
        // Los nodos sin forma llevan "node." delante: el nodo de doctrina ultra_instinct y la
        // forma ultra_instinct tienen pistas distintas y no pueden compartir clave.
        return "skill_tree." + node.getNamespace() + "." + node.getPath().replace('/', '.') + ".hint";
    }

    /** Nombre visible de un nodo (forma, skill o nodo propio), para requisitos y textos. */
    public static Component displayName(ResourceLocation node) {
        if (FormDef.get(node) != null) return Component.translatable(FormDef.nameKey(node));
        String path = node.getPath().replace("node/", "");
        SkillDef def = SkillDef.get(path);
        if (def != null) return Component.translatable(def.nameKey());
        return Component.translatable("skill_tree." + node.getNamespace() + "." + node.getPath().replace('/', '.') + ".name");
    }

    // ── Parseo ───────────────────────────────────────────────────────────────

    static SkillTreeData parse(JsonObject o) {
        ResourceLocation node = ResourceLocation.parse(GsonHelper.getAsString(o, "node"));
        Set<String> via = new LinkedHashSet<>();
        if (o.has("acquisition")) {
            for (JsonElement el : GsonHelper.getAsJsonArray(o, "acquisition")) via.add(el.getAsString().toLowerCase(Locale.ROOT));
        }
        Race race = null;
        if (o.has("race")) race = Race.valueOf(GsonHelper.getAsString(o, "race").toUpperCase(Locale.ROOT));
        Map<String, List<Double>> values = new LinkedHashMap<>();
        if (o.has("values")) {
            JsonObject v = GsonHelper.getAsJsonObject(o, "values");
            for (String k : v.keySet()) {
                List<Double> l = new ArrayList<>();
                for (JsonElement el : v.getAsJsonArray(k)) l.add(el.getAsDouble());
                values.put(k, List.copyOf(l));
            }
        }
        List<Integer> tp = ints(o, "tp_cost");
        return new SkillTreeData(node,
                List.copyOf(SkillTreeRequirement.parseList(o, "requirements")),
                o.has("hint") ? GsonHelper.getAsString(o, "hint") : null,
                GsonHelper.getAsBoolean(o, "secret", false),
                List.copyOf(SkillTreeRequirement.parseList(o, "reveal")),
                Collections.unmodifiableSet(via),
                race,
                GsonHelper.getAsInt(o, "max_level", Math.max(1, tp.size())),
                tp, ints(o, "mind_req"),
                Collections.unmodifiableMap(values));
    }

    private static List<Integer> ints(JsonObject o, String key) {
        if (!o.has(key)) return List.of();
        List<Integer> out = new ArrayList<>();
        JsonArray arr = GsonHelper.getAsJsonArray(o, key);
        for (JsonElement el : arr) out.add(el.getAsInt());
        return List.copyOf(out);
    }
}
