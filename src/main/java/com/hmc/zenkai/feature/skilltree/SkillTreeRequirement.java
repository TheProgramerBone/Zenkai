package com.hmc.zenkai.feature.skilltree;
import com.hmc.zenkai.feature.forms.FormDef;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hmc.zenkai.feature.ZenkaiAttributes;
import com.hmc.zenkai.feature.player.PlayerFormAttachment;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import com.hmc.zenkai.util.ZenkaiNumbers;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Un requisito de la lista UNIFICADA de un nodo del árbol (diseño §2, «Requisitos»): PL, stat,
 * maestría, alineamiento, bandera/condición, nivel de skill, otro nodo, o «cualquiera de». Se
 * leen del JSON del nodo (SkillTreeData) y se evalúan con la MISMA función en cliente y
 * servidor, como el resto de SkillTreeNodes: lo que miran viaja ya al cliente en el sync
 * de stats y de formas.
 *
 * Formato JSON (un objeto por requisito):
 * <pre>
 *   {"type": "pl", "min": 50000}
 *   {"type": "stat", "stat": "spirit", "min": 120}
 *   {"type": "mastery", "form": "zenkai:ssj3", "min": 50}
 *   {"type": "alignment", "min": 20}            (también "max")
 *   {"type": "flag", "flag": "ssj4_ritual"}     (ver {@link #flag})
 *   {"type": "skill", "skill": "kaioken", "level": 3}
 *   {"type": "node", "node": "zenkai:node/god_ki", "level": 1}
 *   {"type": "any_of", "of": [ ...requisitos... ]}
 * </pre>
 */
public sealed interface SkillTreeRequirement {

    boolean test(Player p);

    /** Texto de la línea del tooltip (sin el ✔/✘: lo pone la pantalla según test()). */
    Component describe();

    record Pl(long min) implements SkillTreeRequirement {
        public boolean test(Player p) { return PlayerStatsAttachment.get(p).getReleasablePowerLevel() >= min; }
        public Component describe() {
            return Component.translatable("skill_tree.zenkai.req.pl", ZenkaiNumbers.format(min));
        }
    }

    record Stat(ZenkaiAttributes stat, int min) implements SkillTreeRequirement {
        public boolean test(Player p) { return PlayerStatsAttachment.get(p).getAttribute(stat) >= min; }
        public Component describe() {
            return Component.translatable("skill_tree.zenkai.req.stat",
                    Component.translatable("attr.zenkai." + stat.name().toLowerCase(Locale.ROOT) + ".short"), min);
        }
    }

    record Mastery(ResourceLocation form, double min) implements SkillTreeRequirement {
        public boolean test(Player p) {
            PlayerFormAttachment f = p.getData(ZenkaiDataAttachments.PLAYER_FORM.get());
            return f.getFormMastery(form) >= min;
        }
        public Component describe() {
            return Component.translatable("skill_tree.zenkai.req.mastery",
                    Component.translatable(FormDef.nameKey(form)), Math.round(min));
        }
    }

    record Alignment(int min, int max) implements SkillTreeRequirement {
        public boolean test(Player p) {
            int a = PlayerStatsAttachment.get(p).getAlignment();
            return a >= min && a <= max;
        }
        public Component describe() {
            if (max >= 100) return Component.translatable("skill_tree.zenkai.req.alignment_min", min);
            if (min <= -100) return Component.translatable("skill_tree.zenkai.req.alignment_max", max);
            return Component.translatable("skill_tree.zenkai.req.alignment_range", min, max);
        }
    }

    record Flag(String flag) implements SkillTreeRequirement {
        public boolean test(Player p) { return SkillTreeRequirement.flag(p, flag); }
        public Component describe() { return Component.translatable("skill_tree.zenkai.req.flag." + flag); }
    }

    record Skill(String skill, int level) implements SkillTreeRequirement {
        public boolean test(Player p) { return PlayerStatsAttachment.get(p).skills().level(skill) >= level; }
        public Component describe() {
            return Component.translatable("skill_tree.zenkai.req.skill",
                    Component.translatable("skill.zenkai." + skill), level);
        }
    }

    record Node(ResourceLocation node, int level) implements SkillTreeRequirement {
        public boolean test(Player p) { return PlayerStatsAttachment.get(p).skillTree().level(node) >= level; }
        public Component describe() {
            return Component.translatable("skill_tree.zenkai.req.node", SkillTreeData.displayName(node));
        }
    }

    record AnyOf(List<SkillTreeRequirement> of) implements SkillTreeRequirement {
        public boolean test(Player p) {
            for (SkillTreeRequirement r : of) if (r.test(p)) return true;
            return of.isEmpty();
        }
        public Component describe() {
            var out = Component.empty();
            for (int i = 0; i < of.size(); i++) {
                if (i > 0) out.append(Component.translatable("skill_tree.zenkai.req.or"));
                out.append(of.get(i).describe());
            }
            return out;
        }
    }

    /**
     * Banderas/condiciones con nombre. Son las que ya existen en el jugador; una bandera
     * desconocida da false (un datapack con una errata no regala nada).
     */
    static boolean flag(Player p, String flag) {
        PlayerStatsAttachment att = PlayerStatsAttachment.get(p);
        return switch (flag) {
            case "ssj4_ritual" -> att.hasSsj4Ritual();
            case "reached_super_oozaru" -> att.hasReachedSuperOozaru();
            case "beast_fury" -> att.hasBeastFury();
            case "divine" -> att.isDivine();
            case "legendary" -> att.isLegendary();
            case "immortal" -> att.isImmortal();
            case "god_ki_audience" -> att.hasGodKiAudience();
            case "tail" -> att.hasTail();
            case "namek_orange_hinted" -> att.flags().isNamekOrangeHinted();
            default -> false;
        };
    }

    // ── Lectura ──────────────────────────────────────────────────────────────

    static List<SkillTreeRequirement> parseList(JsonObject o, String key) {
        List<SkillTreeRequirement> out = new ArrayList<>();
        if (!o.has(key)) return out;
        for (JsonElement el : GsonHelper.getAsJsonArray(o, key)) out.add(parse(el.getAsJsonObject()));
        return out;
    }

    static SkillTreeRequirement parse(JsonObject o) {
        String type = GsonHelper.getAsString(o, "type").toLowerCase(Locale.ROOT);
        return switch (type) {
            case "pl" -> new Pl(GsonHelper.getAsLong(o, "min"));
            case "stat" -> new Stat(ZenkaiAttributes.fromString(GsonHelper.getAsString(o, "stat")),
                    GsonHelper.getAsInt(o, "min"));
            case "mastery" -> new Mastery(ResourceLocation.parse(GsonHelper.getAsString(o, "form")),
                    GsonHelper.getAsDouble(o, "min"));
            case "alignment" -> new Alignment(GsonHelper.getAsInt(o, "min", -100), GsonHelper.getAsInt(o, "max", 100));
            case "flag" -> new Flag(GsonHelper.getAsString(o, "flag").toLowerCase(Locale.ROOT));
            case "skill" -> new Skill(GsonHelper.getAsString(o, "skill"), GsonHelper.getAsInt(o, "level", 1));
            case "node" -> new Node(ResourceLocation.parse(GsonHelper.getAsString(o, "node")),
                    GsonHelper.getAsInt(o, "level", 1));
            case "any_of" -> new AnyOf(parseList(o, "of"));
            default -> throw new IllegalArgumentException("Tipo de requisito desconocido: " + type);
        };
    }
}
