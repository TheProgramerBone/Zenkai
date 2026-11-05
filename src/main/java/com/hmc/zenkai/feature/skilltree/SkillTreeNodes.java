package com.hmc.zenkai.feature.skilltree;

import com.hmc.zenkai.event.tick.ArcosianPowerSystem;
import com.hmc.zenkai.event.tick.OverlayMetersSystem;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.forms.FormDef;
import com.hmc.zenkai.feature.forms.FormIds;
import com.hmc.zenkai.feature.forms.FormRegistry;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.skills.DivineForms;
import com.hmc.zenkai.feature.skills.EndgamePaths;
import com.hmc.zenkai.feature.skills.FormDrivenSkills;
import com.hmc.zenkai.feature.skills.SkillDef;
import com.hmc.zenkai.feature.skills.SkillEffects;
import com.hmc.zenkai.feature.skills.SuperForms;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Forma del skill tree de transformaciones de una raza (qué nodos hay y quién cuelga de quién) y
 * la evaluación de cada nodo para un jugador: poseído, comprable, o qué le falta. Es COMÚN a
 * cliente y servidor a propósito: la pantalla pinta el estado con esta misma función que el
 * servidor usa para validar la compra (SkillTreeBuyPacket), así que lo que se ve comprable es
 * exactamente lo que el servidor acepta, sin una segunda copia de las reglas en el cliente.
 *
 * AUTORIDAD. Dos clases de nodo comprable:
 *  - «Siguiente nivel de una skill» ({@link #isTreeSkill}: super_forms, arcosian_power,
 *    ultra_instinct, ultra_ego y potential_unlock): el nivel sigue guardado en la skill y el
 *    nodo se DERIVA de ella (SkillTreeMigration), así que la rueda, FormSystem, comandos y
 *    logros siguen leyendo lo de siempre.
 *  - Nodos PROPIOS (desde F4/F5), sin skill detrás: la pasiva de raza y las formas divinas.
 *    Se guardan como nodos propios en PlayerSkillTree y los escribe SkillTree.grant.
 * God Ki es una skill de nivel 1 que NO se compra con TP (F5): la dan el maestro tras su prueba,
 * el ritual o ser una divinidad con cargo (isDivine).
 *
 * Reglas que añade el árbol sobre las de SkillBuyPacket: el nodo padre tiene que estar poseído,
 * y los requisitos extra del JSON del nodo (SkillTreeData) tienen que cumplirse. Lo demás (TP,
 * MIND libre, nivel 1 solo ante un maestro, alineamiento de Mystic) lo comprueba la misma rutina
 * de siempre, SkillBuyPacket.purchase, o {@link SkillTree#buyOwned} para los nodos propios.
 */
public final class SkillTreeNodes {
    private SkillTreeNodes() {}

    /** Raíz del árbol de la raza (la Base). */
    public static final ResourceLocation ROOT = SkillTreeMigration.node("root");

    public enum Kind {
        /** La base de la raza: siempre poseída, sin compra. */
        ROOT,
        /** Una transformación. */
        FORM,
        /** Nodo con niveles que refleja una skill (Poder Arcosiano, God Ki, UI, UE, Mystic). */
        LEVELED,
        /** Pasiva de raza (F4): nodo propio con niveles, el 1 gratis desde el inicio. */
        PASSIVE
    }

    /**
     * Un nodo. {@code parent} es el nodo del que cuelga EN EL DIBUJO (God Ki se intercala entre
     * la última forma mortal y la primera divina); la regla de compra mira la forma padre real
     * (FormDef.parent), que puede no coincidir.
     */
    public record Node(ResourceLocation id, Kind kind, @Nullable ResourceLocation parent,
                       @Nullable ResourceLocation form, @Nullable String skill) {}

    public enum State {
        /** Poseído (en los nodos con niveles: al nivel máximo). */
        OWNED,
        /** Se puede comprar ya. */
        AVAILABLE,
        NEED_TP,
        NEED_MIND,
        /** El siguiente nivel solo lo enseña un maestro en persona. */
        NEED_MASTER,
        /** Faltan requisitos de la lista unificada (JSON del nodo, F3). */
        NEED_REQS,
        /** Falta el nodo padre (o un nivel previo). */
        LOCKED,
        /** No se compra: se obtiene por condición (Oozaru, Beast, UI/UE por nivel de su doctrina,
         *  God Ki, formas que solo da una vía especial como el ritual). */
        CONDITION
    }

    /**
     * Resultado de evaluar un nodo. {@code skill}/{@code targetLevel}: lo que compraría (skill
     * null con targetLevel > 0 = nodo PROPIO, ver SkillTree.buyOwned). {@code level}/{@code
     * maxLevel}: progreso de un nodo con niveles (formas: 0/1).
     */
    public record Eval(State state, @Nullable String skill, int targetLevel, int cost,
                       int mindDelta, int level, int maxLevel) {
        public boolean purchasable() { return state == State.AVAILABLE; }
        public boolean ownNode() { return skill == null && targetLevel > 0; }
    }

    /** Línea de requisito del tooltip: el texto y si se cumple (verde/rojo, tipo advancement). */
    public record ReqLine(Component text, boolean met) {}

    // ── Skills que pertenecen al árbol ───────────────────────────────────────

    private static final Set<String> TREE_SKILLS = Set.of(
            SuperForms.SKILL, DivineForms.SKILL, ArcosianPowerSystem.SKILL,
            SkillEffects.ULTRA_INSTINCT, SkillEffects.ULTRA_EGO, SkillEffects.POTENTIAL_UNLOCK,
            SkillEffects.NAMEK_GIANT);

    /** ¿Se compra en el árbol? Esas skills salen de SkillsScreen, no se olvidan (sin reembolso)
     *  y el respec general no las toca. El maestro sigue pudiendo enseñar su nivel 1. */
    public static boolean isTreeSkill(String skillId) {
        return skillId != null && TREE_SKILLS.contains(skillId);
    }

    // ── Pasivas de raza ──────────────────────────────────────────────────────

    /** Nodo de la pasiva de esa raza ({@code zenkai:node/race_<raza>}). */
    public static ResourceLocation passiveNode(Race race) {
        return SkillTreeMigration.node("race_" + race.name().toLowerCase(Locale.ROOT));
    }

    /** Nivel de la pasiva de su raza: 1 (gratis) si no la ha mejorado, 0 sin raza. */
    public static int passiveLevel(PlayerStatsAttachment att) {
        if (att == null || !att.isRaceChosen() || att.getRace() == null) return 0;
        return Math.max(1, att.skillTree().level(passiveNode(att.getRace())));
    }

    // ── Construcción del árbol ───────────────────────────────────────────────

    /**
     * Nodos del árbol de esa raza, padres antes que hijos. Las rutas universales cuelgan del
     * nodo que las abre (decisión del usuario, 2026-10-21): Mystic/Beast de la Base de la raza
     * (es un camino propio, ni divino ni detrás de ninguna forma), Ultra Instinct y Ultra Ego de
     * God Ki (§5: exigen God Ki). Como en el resto del árbol, colgar de un nodo es también la
     * regla de compra: sin el padre poseído, la doctrina sale con candado.
     */
    public static List<Node> build(Race race) {
        Map<ResourceLocation, Node> out = new LinkedHashMap<>();
        if (race == null) return List.of();
        out.put(ROOT, new Node(ROOT, Kind.ROOT, null, FormIds.BASE, null));

        ResourceLocation passive = passiveNode(race);
        if (SkillTreeData.get(passive) != null) put(out, new Node(passive, Kind.PASSIVE, ROOT, null, null));

        if (race == Race.ARCOSIAN && SkillDef.get(ArcosianPowerSystem.SKILL) != null) {
            put(out, new Node(SkillTreeMigration.NODE_ARCOSIAN_POWER, Kind.LEVELED, ROOT, null,
                    ArcosianPowerSystem.SKILL));
        }

        // Gigante Namekiano: interruptor comprable (no una forma), colgado de la Base.
        if (race == Race.NAMEKIAN && SkillDef.get(SkillEffects.NAMEK_GIANT) != null) {
            put(out, new Node(SkillTreeMigration.NODE_NAMEK_GIANT, Kind.LEVELED, ROOT, null,
                    SkillEffects.NAMEK_GIANT));
        }

        // Formas de la raza: BFS por el árbol real (FormRegistry.childrenOf), igual que la rueda.
        Deque<ResourceLocation> queue = new ArrayDeque<>();
        for (FormDef d : FormDef.all()) {
            if (d.parent() == null && d.allows(race) && !isUniversal(d)) queue.add(d.id());
        }
        Set<ResourceLocation> seen = new HashSet<>();
        boolean godKiPlaced = false;
        while (!queue.isEmpty()) {
            ResourceLocation id = queue.poll();
            if (!seen.add(id)) continue; // corta ciclos de un datapack roto
            FormDef d = FormDef.get(id);
            if (d == null || !d.allows(race) || isUniversal(d)) continue;

            ResourceLocation drawParent = d.parent() == null ? ROOT : d.parent();
            // SSJ4 se DIBUJA colgando de Súper Oozaru (lo revela su ritual, diseño §3), aunque su
            // padre real, el que exige la compra (ownedParent con FormDef.parent), siga siendo SSJ3.
            if (FormIds.SSJ4.equals(id) && FormDef.get(FormIds.SUPER_OOZARU) != null
                    && FormDef.get(FormIds.SUPER_OOZARU).allows(race)) {
                drawParent = FormIds.SUPER_OOZARU;
            }
            FormDef pd = FormDef.get(d.parent());
            if (d.divineTier() && (pd == null || !pd.divineTier())) {
                // Primera forma divina: cuelga de God Ki, que se intercala tras su padre mortal.
                if (!godKiPlaced) {
                    put(out, new Node(SkillTreeMigration.NODE_GOD_KI, Kind.LEVELED, drawParent, null,
                            DivineForms.SKILL));
                    godKiPlaced = true;
                }
                drawParent = SkillTreeMigration.NODE_GOD_KI;
            }
            if (!com.hmc.zenkai.feature.forms.LegendaryForms.hiddenNode(id)) {
                put(out, new Node(id, Kind.FORM, drawParent, id, null));
            }
            for (ResourceLocation child : FormRegistry.childrenOf(id)) queue.add(child);
        }

        // Raza sin formas divinas (un datapack que se las quite): God Ki sigue existiendo, porque
        // abre las doctrinas, y cuelga del último peldaño de su cadena mortal.
        if (!godKiPlaced && SkillDef.get(DivineForms.SKILL) != null) {
            List<ResourceLocation> chain = SuperForms.chain(race);
            ResourceLocation last = chain.isEmpty() ? ROOT : chain.get(chain.size() - 1);
            put(out, new Node(SkillTreeMigration.NODE_GOD_KI, Kind.LEVELED, last, null, DivineForms.SKILL));
        }

        // Rutas universales: una doctrina (nodo con niveles) de la que cuelgan sus formas.
        ResourceLocation godKi = out.containsKey(SkillTreeMigration.NODE_GOD_KI)
                ? SkillTreeMigration.NODE_GOD_KI : ROOT;
        addDoctrine(out, race, SkillTreeMigration.NODE_POTENTIAL_UNLOCK, SkillEffects.POTENTIAL_UNLOCK,
                FormIds.POTENTIAL_UNLOCK, true, ROOT);
        addDoctrine(out, race, SkillTreeMigration.NODE_ULTRA_INSTINCT, SkillEffects.ULTRA_INSTINCT,
                FormIds.ULTRA_INSTINCT_SIGN, false, godKi);
        addDoctrine(out, race, SkillTreeMigration.NODE_ULTRA_EGO, SkillEffects.ULTRA_EGO,
                FormIds.ULTRA_EGO, false, godKi);
        return new ArrayList<>(out.values());
    }

    /**
     * Doctrina + sus formas. {@code skipForm}: la forma ES la doctrina (Mystic: la skill
     * potential_unlock y la forma potential_unlock son lo mismo), así que no lleva nodo propio
     * y sus hijas (Beast) cuelgan directamente de la doctrina.
     */
    private static void addDoctrine(Map<ResourceLocation, Node> out, Race race, ResourceLocation nodeId,
                                    String skill, ResourceLocation firstForm, boolean skipForm,
                                    ResourceLocation parent) {
        if (SkillDef.get(skill) == null) return;
        put(out, new Node(nodeId, Kind.LEVELED, parent, null, skill));
        FormDef first = FormDef.get(firstForm);
        if (first == null || !first.allows(race)) return;
        Deque<ResourceLocation[]> queue = new ArrayDeque<>(); // {forma, padre en el dibujo}
        if (skipForm) {
            for (ResourceLocation c : FormRegistry.childrenOf(firstForm)) queue.add(new ResourceLocation[]{c, nodeId});
        } else {
            queue.add(new ResourceLocation[]{firstForm, nodeId});
        }
        Set<ResourceLocation> seen = new HashSet<>();
        while (!queue.isEmpty()) {
            ResourceLocation[] e = queue.poll();
            if (!seen.add(e[0])) continue;
            FormDef d = FormDef.get(e[0]);
            if (d == null || !d.allows(race)) continue;
            put(out, new Node(e[0], Kind.FORM, e[1], e[0], null));
            for (ResourceLocation c : FormRegistry.childrenOf(e[0])) queue.add(new ResourceLocation[]{c, e[0]});
        }
    }

    private static void put(Map<ResourceLocation, Node> out, Node n) { out.putIfAbsent(n.id(), n); }

    /** Formas compartidas por las 5 razas (Mystic y las rutas de endgame): van en sus doctrinas. */
    private static boolean isUniversal(FormDef d) {
        return d.endgamePath() || FormIds.POTENTIAL_UNLOCK.equals(d.id());
    }

    public static @Nullable Node find(Race race, ResourceLocation id) {
        for (Node n : build(race)) if (n.id().equals(id)) return n;
        return null;
    }

    // ── Nodos secretos ───────────────────────────────────────────────────────

    /**
     * ¿Es un nodo SECRETO? No se dibuja (ni su arista, ni deja hueco en el reparto) hasta que se
     * revela, y mientras tanto no se puede comprar. Desde F3 lo decide el JSON del nodo
     * ({@code "secret"} + {@code "reveal"}): Súper Oozaru se revela la primera vez que el jugador
     * llega a él (o con el ritual de SSJ4) y SSJ4 con el ritual. SSJ4 es un camino OCULTO que no
     * condiciona el avance: God Ki y lo divino cuelgan de SSJ3 (ssj_god.json).
     */
    public static boolean secret(Node n) {
        SkillTreeData d = SkillTreeData.get(n.id());
        return d != null && d.secret();
    }

    /** ¿Lo puede ver el jugador? Los nodos no secretos siempre; lo POSEÍDO nunca se esconde
     *  (partidas de antes de 2026-10-21 con SSJ4 pagado sin el ritual). */
    public static boolean revealed(Player p, Node n) {
        // Hermanas empatadas (Blue/Rosé y sus Evolution): solo existe la que le corresponde al
        // jugador según isDivine; la otra no se dibuja NUNCA, ni siquiera poseída (si isDivine
        // cambia, la poseída pasa a ser la hermana, porque ownsDepth cuenta el peldaño entero).
        if (n.kind() == Kind.FORM && n.form() != null) {
            FormDef fd = FormDef.get(n.form());
            if (fd != null && fd.divineTier() && !fd.endgamePath()
                    && !n.form().equals(DivineForms.resolveForPlayer(p, n.form()))) return false;
        }
        // Hija de una forma divina que no es la suya (el Rosé no ve lo que cuelga de Blue): sin padre visible no hay nodo.
        if (n.kind() == Kind.FORM && n.form() != null) {
            FormDef fd = FormDef.get(n.form());
            FormDef pd = fd == null || fd.parent() == null ? null : FormDef.get(fd.parent());
            if (pd != null && pd.divineTier() && !pd.endgamePath()
                    && !fd.parent().equals(DivineForms.resolveForPlayer(p, fd.parent()))) return false;
        }
        SkillTreeData d = SkillTreeData.get(n.id());
        if (d == null || !d.secret()) return true;
        if (PlayerStatsAttachment.get(p).skillTree().owns(n.id())) return true;
        if (d.reveal().isEmpty()) return false;
        for (SkillTreeRequirement r : d.reveal()) if (!r.test(p)) return false;
        return true;
    }

    // ── Posesión ─────────────────────────────────────────────────────────────

    /** ¿Posee el jugador este nodo? Las formas por condición cuentan si ya puede usarlas. */
    public static boolean owned(Player p, Node n) {
        if (n.kind() == Kind.ROOT || n.kind() == Kind.PASSIVE) return true; // la pasiva viene con la raza
        PlayerStatsAttachment att = PlayerStatsAttachment.get(p);
        if (att.skillTree().owns(n.id())) return true;
        // Oozaru y Súper Oozaru están fuera de la cadena: SuperForms.unlocked() les da true de
        // oficio (depthOf <= 0). Oozaru viene con la raza (le pasa a cualquier saiyan con cola
        // bajo la luna llena): siempre poseído. Súper Oozaru se domina con SSJ1
        // (SuperForms.readyForSuperOozaru), que es también cuando se revela su nodo.
        if (FormIds.OOZARU.equals(n.form())) return true;
        if (FormIds.SUPER_OOZARU.equals(n.form())) return SuperForms.readyForSuperOozaru(p);
        if (n.kind() == Kind.FORM) {
            FormDef d = FormDef.get(n.form());
            if (d == null) return false;
            // Forma divina: nodo propio; la hermana empatada (Blue/Rosé) comparte peldaño.
            if (d.divineTier() && !d.endgamePath()) return DivineForms.ownsDepth(p, att.getRace(), d.id());
            if (FormRegistry.isAddonBranch(d)) return false;   // rama de addon: solo la posee quien la compró (arriba, skillTree().owns)
            if (SkillTreeMigration.ownedByPurchase(att.skills(), att.getRace(), d) == null) {
                return SuperForms.unlocked(p, n.form());
            }
        }
        return false;
    }

    // ── Evaluación ───────────────────────────────────────────────────────────

    public static Eval evaluate(Player p, Node n) {
        PlayerStatsAttachment att = PlayerStatsAttachment.get(p);
        Race race = att.getRace();
        if (n.kind() == Kind.ROOT) return info(State.OWNED, 1, 1);
        // Un nodo secreto sin revelar no existe para el jugador: tampoco se compra (el servidor
        // valida con esta misma función, así que un cliente trucado no se lo salta).
        if (!revealed(p, n)) return info(State.LOCKED, 0, 1);
        SkillTreeData data = SkillTreeData.get(n.id());

        if (n.kind() == Kind.PASSIVE) {
            int level = passiveLevel(att);
            int max = data == null ? 1 : data.maxLevel();
            if (level >= max) return info(State.OWNED, level, max);
            if (!requirementsMet(p, data)) return info(State.NEED_REQS, level, max);
            return purchaseOwn(att, data, level, level + 1, max);
        }

        if (n.kind() == Kind.LEVELED) {
            SkillDef def = SkillDef.get(n.skill());
            if (def == null) return info(State.LOCKED, 0, 0);
            int level = att.skills().level(def.id());
            // God Ki (F5): nivel 1 y NUNCA se compra con TP. Lo dan la prueba del maestro, el
            // ritual o ser una divinidad con cargo (isDivine).
            if (DivineForms.SKILL.equals(def.id())) {
                return info(level > 0 ? State.OWNED : State.CONDITION, Math.min(level, 1), 1);
            }
            int max = FormDrivenSkills.maxLevel(def, race);
            if (max > 0 && level >= max) return info(State.OWNED, level, max);
            if (n.parent() != null && !ownedParent(p, race, n.parent())) return info(State.LOCKED, level, max);
            if (!requirementsMet(p, data)) return info(State.NEED_REQS, level, max);
            return purchase(p, att, def, level, level + 1, max);
        }

        // FORM
        FormDef d = FormDef.get(n.form());
        if (d == null) return info(State.LOCKED, 0, 1);
        if (owned(p, n)) return info(State.OWNED, 1, 1);

        // Forma divina de la raza: nodo PROPIO comprado con su tp_cost (F5). Pide God Ki y el
        // peldaño anterior (la forma mortal de la que cuelga, o la divina previa).
        if (d.divineTier() && !d.endgamePath()) {
            if (d.parent() != null && !ownedParent(p, race, d.parent())) return info(State.LOCKED, 0, 1);
            if (att.skills().level(DivineForms.SKILL) <= 0) return info(State.LOCKED, 0, 1);
            if (data != null && !data.purchasableWithTp()) return info(State.CONDITION, 0, 1);
            if (!requirementsMet(p, data)) return info(State.NEED_REQS, 0, 1);
            return purchaseForm(att, d.tpCost());
        }

        // Rama de addon: nodo propio e independiente (tp_cost, padre poseído, requisitos del JSON). Ver FormRegistry.isAddonBranch.
        if (FormRegistry.isAddonBranch(d)) {
            if (d.parent() != null && !ownedParent(p, race, d.parent())) return info(State.LOCKED, 0, 1);
            if (data != null && !data.purchasableWithTp()) return info(State.CONDITION, 0, 1);
            if (!requirementsMet(p, data)) return info(State.NEED_REQS, 0, 1);
            return purchaseForm(att, d.tpCost());
        }

        Boolean byPurchase = SkillTreeMigration.ownedByPurchase(att.skills(), race, d);
        if (byPurchase == null || d.endgamePath()) {
            // Por condición, o una forma de doctrina que llega con el nivel de su skill.
            return info(State.CONDITION, 0, 1);
        }
        if (d.parent() != null && !ownedParent(p, race, d.parent())) return info(State.LOCKED, 0, 1);
        // Solo por una vía especial (SSJ4: el ritual se la regala).
        if (data != null && !data.purchasableWithTp()) return info(State.CONDITION, 0, 1);

        int target = SuperForms.depthOf(race, d.id()) + 1;
        SkillDef def = SkillDef.get(SuperForms.SKILL);
        if (def == null || target <= 1) return info(State.LOCKED, 0, 1);
        int level = att.skills().level(SuperForms.SKILL);
        // La cadena es lineal: solo se compra el SIGUIENTE peldaño (una hermana de alineamiento
        // comparte peldaño, así que poseer una ya da la otra y aquí no llega).
        if (level != target - 1) return info(State.LOCKED, 0, 1);
        if (!requirementsMet(p, data)) return info(State.NEED_REQS, 0, 1);
        Eval e = purchase(p, att, def, level, target, FormDrivenSkills.maxLevel(def, race));
        return new Eval(e.state(), e.skill(), e.targetLevel(), e.cost(), e.mindDelta(), 0, 1);
    }

    /** Comprobaciones comunes de compra del nivel {@code target} de una skill. */
    private static Eval purchase(Player p, PlayerStatsAttachment att, SkillDef def, int level,
                                 int target, int max) {
        if (target > max) return info(State.LOCKED, level, max);
        int cost = FormDrivenSkills.tpCostForLevel(def, att.getRace(), target);
        int mindDelta = def.mindReqFor(target) - def.mindReqFor(level);
        State s;
        if (level <= 0 && def.hasMaster()) s = State.NEED_MASTER;
        else if (cost == Integer.MAX_VALUE) s = State.LOCKED;
        else if (att.mindFree() < mindDelta) s = State.NEED_MIND;
        else if (att.getTP() < cost) s = State.NEED_TP;
        else s = State.AVAILABLE;
        return new Eval(s, def.id(), target, cost, mindDelta, level, max);
    }

    /** Siguiente nivel de un nodo propio con curva (pasiva). */
    private static Eval purchaseOwn(PlayerStatsAttachment att, SkillTreeData data, int level, int target, int max) {
        int cost = data.tpCostFor(target);
        if (cost < 0) return info(State.LOCKED, level, max);
        int mindDelta = data.mindReqFor(target) - data.mindReqFor(level);
        State s = att.mindFree() < mindDelta ? State.NEED_MIND
                : att.getTP() < cost ? State.NEED_TP : State.AVAILABLE;
        return new Eval(s, null, target, cost, mindDelta, level, max);
    }

    private static Eval purchaseForm(PlayerStatsAttachment att, int cost) {
        State s = att.getTP() < cost ? State.NEED_TP : State.AVAILABLE;
        return new Eval(s, null, 1, cost, 0, 0, 1);
    }

    private static Eval info(State s, int level, int max) {
        return new Eval(s, null, 0, 0, 0, level, max);
    }

    private static boolean requirementsMet(Player p, @Nullable SkillTreeData data) {
        if (data == null) return true;
        for (SkillTreeRequirement r : data.requirements()) if (!r.test(p)) return false;
        return true;
    }

    /** ¿Cumple el jugador los requisitos extra de ese nodo? (Los usa también el ritual.) */
    public static boolean requirementsMet(Player p, ResourceLocation node) {
        return requirementsMet(p, SkillTreeData.get(node));
    }

    private static boolean ownedParent(Player p, Race race, ResourceLocation parent) {
        if (ROOT.equals(parent) || FormIds.BASE.equals(parent)) return true;
        Node pn = find(race, parent);
        if (pn != null) return owned(p, pn);
        return SuperForms.unlocked(p, parent);
    }

    /**
     * Requisitos visibles del nodo para el tooltip, tipo advancement: el padre, los que ya
     * existían por reglas propias (God Ki, nivel de doctrina, furia de Beast) y los del JSON.
     * El coste en TP y la MIND van en la línea de estado, no aquí.
     */
    public static List<ReqLine> requirementLines(Player p, Node n) {
        List<ReqLine> out = new ArrayList<>();
        if (n.kind() == Kind.ROOT) return out;
        PlayerStatsAttachment att = PlayerStatsAttachment.get(p);
        Race race = att.getRace();
        FormDef d = n.form() == null ? null : FormDef.get(n.form());
        // Padre de COMPRA: el real de la forma (SSJ4 pide SSJ3 aunque se dibuje bajo Súper
        // Oozaru); para los nodos con niveles, el del dibujo.
        ResourceLocation parent = d != null ? d.parent() : n.parent();
        if (parent != null && !ROOT.equals(parent) && !FormIds.BASE.equals(parent)) {
            if (SkillTreeMigration.NODE_GOD_KI.equals(parent)) {
                out.add(new ReqLine(Component.translatable("skill_tree.zenkai.req.god_ki"),
                        att.skills().level(DivineForms.SKILL) > 0));
            } else {
                out.add(new ReqLine(Component.translatable("skill_tree.zenkai.req.node",
                        SkillTreeData.displayName(parent)), ownedParent(p, race, parent)));
            }
        }
        if (d != null && SuperForms.legendaryBlocked(p, d.id())) {
            out.add(new ReqLine(Component.translatable("skill_tree.zenkai.req.legendary_blocked"), false));
        }
        if (d != null && d.divineTier() && !d.endgamePath()) {
            out.add(new ReqLine(Component.translatable("skill_tree.zenkai.req.god_ki"),
                    att.skills().level(DivineForms.SKILL) > 0));
        }
        if (d != null && d.endgamePath()) {
            int lvl = doctrineLevelFor(d.id());
            if (lvl > 0) {
                String skill = FormIds.ULTRA_EGO.equals(d.id()) ? SkillEffects.ULTRA_EGO : SkillEffects.ULTRA_INSTINCT;
                out.add(new ReqLine(Component.translatable("skill_tree.zenkai.req.skill",
                        Component.translatable("skill.zenkai." + skill), lvl), att.skills().level(skill) >= lvl));
            }
            if (FormIds.BEAST.equals(d.id())) {
                out.add(new ReqLine(Component.translatable("skill_tree.zenkai.req.flag.beast_fury"),
                        att.hasBeastFury()));
            }
        }
        SkillTreeData data = SkillTreeData.get(n.id());
        if (data != null) {
            for (SkillTreeRequirement r : data.requirements()) out.add(new ReqLine(r.describe(), r.test(p)));
        }
        return out;
    }

    // ── Coste de una ruta («marcar ruta», F3) ────────────────────────────────

    /**
     * TP que falta para llegar a {@code target} desde lo que ya posee: suma el coste de cada
     * nodo de la ruta (padres en el dibujo) que no tenga, contando los niveles de doctrina que
     * pide una forma (UI 9, UE 5) y el siguiente nivel de cualquier otro nodo con niveles. God Ki
     * no suma (no se compra con TP); tampoco las formas que solo da una vía especial.
     */
    public static long routeCost(Player p, Race race, List<Node> route) {
        PlayerStatsAttachment att = PlayerStatsAttachment.get(p);
        long total = 0;
        for (int i = 0; i < route.size(); i++) {
            Node n = route.get(i);
            if (owned(p, n) && n.kind() != Kind.LEVELED) continue;
            SkillTreeData data = SkillTreeData.get(n.id());
            switch (n.kind()) {
                case FORM -> {
                    FormDef d = FormDef.get(n.form());
                    if (d == null || d.endgamePath() || (data != null && !data.purchasableWithTp())) continue;
                    if (SkillTreeMigration.ownedByPurchase(att.skills(), race, d) == null
                            && !(d.divineTier()) && !FormRegistry.isAddonBranch(d)) continue;
                    total += Math.max(0, d.tpCost());
                }
                case PASSIVE -> {
                    int c = data == null ? -1 : data.tpCostFor(passiveLevel(att) + 1);
                    if (c > 0) total += c;
                }
                case LEVELED -> {
                    SkillDef def = SkillDef.get(n.skill());
                    if (def == null || DivineForms.SKILL.equals(def.id())) continue;
                    int have = att.skills().level(def.id());
                    int want = have + 1;
                    if (i + 1 < route.size() && route.get(i + 1).form() != null) {
                        int need = doctrineLevelFor(route.get(i + 1).form());
                        want = Math.max(need, have > 0 ? have : 1);
                    }
                    if (i == route.size() - 1) want = have + 1;
                    for (int l = have + 1; l <= want; l++) {
                        int c = FormDrivenSkills.tpCostForLevel(def, race, l);
                        if (c != Integer.MAX_VALUE) total += c;
                    }
                }
                default -> { }
            }
        }
        return total;
    }

    private static boolean hasDivineForms(Race race) {
        for (FormDef d : FormDef.all()) {
            if (d.divineTier() && d.allows(race) && !d.endgamePath()) return true;
        }
        return false;
    }

    /** Primera forma divina de la raza (la que regala el ritual), o null. */
    public static @Nullable ResourceLocation firstDivineForm(Race race) {
        if (!hasDivineForms(race)) return null;
        for (FormDef d : FormDef.all()) {
            if (d.divineTier() && !d.endgamePath() && d.allows(race) && DivineForms.depthOf(race, d.id()) == 1) {
                return d.id();
            }
        }
        return null;
    }

    /**
     * Nivel de doctrina que pide una forma de ruta universal (Sign 5, UI 9, UE 5), o 0. Lo usa
     * la pantalla para el texto de requisito; la regla de verdad sigue en EndgamePaths.
     */
    public static int doctrineLevelFor(ResourceLocation form) {
        if (FormIds.ULTRA_EGO.equals(form)) return OverlayMetersSystem.UE_FORM_LEVEL;
        return EndgamePaths.requiredUiLevel(form);
    }
}
