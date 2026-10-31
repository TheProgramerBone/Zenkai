package com.hmc.zenkai.registry.commands;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.forms.KaiokenTier;
import com.hmc.zenkai.feature.player.PlayerFormAttachment;
import com.hmc.zenkai.feature.skills.FormDrivenSkills;
import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.skills.SkillDef;
import com.hmc.zenkai.feature.technique.PhysicalTechnique;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.hmc.zenkai.registry.*;

/**
 * Comandos de skills, técnicas físicas y maestría (give/revoke/giveall/list, mastery set/list). Cada uno despacha por {@code FormDrivenSkills} y el resto de puntos únicos: NO copies aquí una regla que ya está centralizada (por eso God Ki quedó mal con giveall).
 */
public final class SkillCommands {
    private SkillCommands() {}

    /**
     * Otorga una habilidad SIN TP para pruebas de admin (grantAdmin: sobrevive al respec,
     * pero solo el nivel 1 queda protegido de olvidarse por la X de SkillsScreen — el resto
     * del nivel dado se puede bajar y reembolsa TP con normalidad. Ver
     * PlayerSkills.grantAdmin()).
     *
     * @param requested nivel exacto que se quiere dejar, o -1 para "sube uno".
     *                  Se RECORTA al techo en vez de fallar: si el máximo es 5 y pides 10,
     *                  te quedas en 5. Fallar ahí solo obliga a consultar el max a mano.
     */
    public static int skillGive(CommandContext<CommandSourceStack> ctx, ServerPlayer sp,
                                 String id, int requested) {
        SkillDef def = SkillDef.get(id);
        if (def == null) {
            ctx.getSource().sendFailure(Component.literal("[Zenkai] Unknown skill: " + id));
            return 0;
        }
        var att = sp.getData(ZenkaiDataAttachments.PLAYER_STATS.get());

        // Mismo techo que SkillBuyPacket y giveAll: con levels_from_forms manda la cadena de
        // formas de SU raza, no el max_level del JSON — y FormDrivenSkills es quien sabe si esa
        // cadena es la de super_forms o la de god_ki (u otra futura), no siempre la primera.
        int max = FormDrivenSkills.maxLevel(def, sp);
        if (max <= 0) {
            ctx.getSource().sendFailure(Component.literal("[Zenkai] '" + id
                    + "' no está disponible para la raza de " + sp.getGameProfile().getName()));
            return 0;
        }

        int cur = att.skills().level(id);
        int target = Math.min(requested < 0 ? cur + 1 : requested, max);

        if (target <= cur) {
            ctx.getSource().sendFailure(Component.literal("[Zenkai] "
                    + sp.getGameProfile().getName() + " ya está en lvl " + cur + " de " + id
                    + " (máx " + max + ")"));
            return 0;
        }

        // grantAdmin(), no grant(): protege solo el nivel 1, no "target" entero — si no, un
        // /give de prueba a nivel alto quedaría bloqueado en la UI de olvidar de arriba abajo.
        // Ver el javadoc de PlayerSkills.grantAdmin().
        att.skills().grantAdmin(id, target);
        PlayerLifeCycle.sync(sp);

        final int t = target;
        final boolean capped = requested > max;
        ctx.getSource().sendSuccess(() -> Component.literal(
                "[Zenkai] Skill '" + id + "' lvl " + t + " → " + sp.getGameProfile().getName()
                        + (capped ? " (recortado desde " + requested + ")" : "")), true);
        return 1;
    }

    /** Revoca aunque el id ya no exista en el registro (limpia huérfanas). */
    public static int skillRevoke(CommandContext<CommandSourceStack> ctx, ServerPlayer sp, String id) {
        var att = sp.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
        if (!att.skills().revoke(id)) {
            ctx.getSource().sendFailure(Component.literal("[Zenkai] "
                    + sp.getGameProfile().getName() + " doesn't have: " + id));
            return 0;
        }
        PlayerLifeCycle.sync(sp);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "[Zenkai] Skill '" + id + "' revoked ← " + sp.getGameProfile().getName()), true);
        return 1;
    }

    /**
     * Otorga el conjunto de habilidades del datapack al máximo. Son "granted" (grantAdmin), no
     * compradas: sobreviven al respec y no consumen TP — pero solo el nivel 1 de cada una
     * queda protegido de olvidarse por la X de SkillsScreen, el resto se puede bajar con
     * normalidad. Ver PlayerSkills.grantAdmin().
     * OJO con levels_from_forms: su techo real NO es def.maxLevel(), sino la cadena de
     * formas de SU raza (misma regla que SkillBuyPacket, vía FormDrivenSkills — que además
     * decide CUÁL cadena, super_forms o god_ki). Sin este mínimo, un humano acabaría con
     * niveles de super_forms que no existen para él, o cualquier raza con niveles de god_ki
     * que no existen para ella (ver el bug real: god_ki quedó en 5/2 porque este método
     * comparaba SIEMPRE contra SuperForms.maxLevel, sin importar qué skill fuera).
     */
    public static int skillGiveAll(CommandContext<CommandSourceStack> ctx, ServerPlayer sp) {
        if (SkillDef.all().isEmpty()) {
            ctx.getSource().sendFailure(Component.literal(
                    "[Zenkai] No skills loaded (¿datapack sin cargar? prueba /reload)"));
            return 0;
        }
        var att = sp.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
        int granted = 0, capped = 0;
        for (SkillDef def : SkillDef.all()) {
            int max = FormDrivenSkills.maxLevel(def, sp);
            if (max <= 0) { capped++; continue; }   // raza sin cadena de formas
            if (att.skills().level(def.id()) >= max) continue;
            att.skills().grantAdmin(def.id(), max);   // solo protege el nivel 1, ver javadoc
            granted++;
        }
        PlayerLifeCycle.sync(sp);
        final int g = granted, c = capped;
        ctx.getSource().sendSuccess(() -> Component.literal(
                "[Zenkai] " + g + " skill(s) maxed → " + sp.getGameProfile().getName()
                        + (c > 0 ? " (" + c + " N/A para su raza)" : "")), true);
        return 1;
    }

    /** Lista las habilidades del datapack con el nivel actual del jugador. */
    public static int skillList(CommandContext<CommandSourceStack> ctx, ServerPlayer sp) {
        var all = SkillDef.all();
        if (all.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("[Zenkai] No skills loaded."));
            return 0;
        }
        var att = sp.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
        ctx.getSource().sendSuccess(() -> Component.literal(
                        "[Zenkai] " + all.size() + " skill(s) — " + sp.getGameProfile().getName())
                .withStyle(ChatFormatting.GOLD), false);
        for (SkillDef def : all) {
            int max = FormDrivenSkills.maxLevel(def, sp);
            int lvl = att.skills().level(def.id());
            String extra = (def.hasMaster() ? " §8master:" + String.join(",", def.masters()) : "")
                    + (def.purchasable() ? "" : " §8[no comprable]")
                    + (def.levelsFromForms() ? " §8[formas]" : "");
            ctx.getSource().sendSuccess(() -> Component.literal(
                    String.format("§7- §f%s §7%d/%d §8· §7%d TP%s",
                            def.id(), lvl, max, def.tpCost(), extra)), false);
        }
        return 1;
    }

    /** Desbloquea una física SIN TP (pruebas; maestros después). */
    public static int physGive(CommandContext<CommandSourceStack> ctx, ServerPlayer sp, String name) {
        var t = PhysicalTechnique.byName(
                name.toUpperCase(java.util.Locale.ROOT));
        if (t == null) {
            ctx.getSource().sendFailure(Component.literal("[Zenkai] Unknown: " + name
                    + " (dash_punch|heavy_blow|barrage|kiai|rising_slash|cross_slash|piercing_thrust|spin_slash|sky_cleave|light_blade|dive|grab|evade)"));
            return 0;
        }
        PlayerStatsAttachment.get(sp).techniques().unlock(t);
        PlayerLifeCycle.sync(sp);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "[Zenkai] " + t.name() + " → " + sp.getGameProfile().getName()), true);
        return 1;
    }

    /**
     * Admite "kaioken", cualquier etiqueta de escalón ("x20"), "ssj4" y "zenkai:ssj4".
     * Brigadier resuelve un id sin namespace como minecraft:, así que ese caso se reinterpreta
     * como zenkai: — es lo que quiere decir el jugador el 100% de las veces.
     * Las etiquetas de escalón siguen aceptándose por comodidad, pero cada una apunta a la MISMA
     * clave: la maestría de kaioken ya no se guarda por escalón.
     */
    public static ResourceLocation resolveMasteryId(ResourceLocation raw) {
        String path = raw.getPath();
        if (path.equals("kaioken")) return PlayerFormAttachment.kaiokenMasteryKey();
        for (KaiokenTier t : KaiokenTier.values()) {
            if (t.isOn() && t.label().equals(path)) return PlayerFormAttachment.kaiokenMasteryKey();
        }
        return "minecraft".equals(raw.getNamespace())
                ? ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, path)
                : raw;
    }

    public static int masterySet(CommandContext<CommandSourceStack> ctx, ServerPlayer sp,
                                  ResourceLocation rawId, int value) {
        ResourceLocation id = resolveMasteryId(rawId);
        var form = sp.getData(ZenkaiDataAttachments.PLAYER_FORM.get());
        form.setMastery(id, value);
        // syncForm y NO sync: la maestría vive en PLAYER_FORM. Con sync() el cliente seguiría
        // mostrando el valor viejo en la pantalla de stats.
        PlayerLifeCycle.syncForm(sp);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "[Zenkai] " + id + " mastery = " + value + "% → " + sp.getGameProfile().getName()), true);
        return 1;
    }

    /** Lista lo que el jugador tiene entrenado (formas y escalones mezclados, es un solo mapa). */
    public static int masteryList(CommandContext<CommandSourceStack> ctx, ServerPlayer sp) {
        var form = sp.getData(ZenkaiDataAttachments.PLAYER_FORM.get());
        var view = form.masteryView();
        if (view.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "[Zenkai] " + sp.getGameProfile().getName() + " no tiene maestría en nada."), false);
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
                        "[Zenkai] " + view.size() + " entrada(s) — " + sp.getGameProfile().getName())
                .withStyle(ChatFormatting.GOLD), false);
        view.entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .forEach(en -> ctx.getSource().sendSuccess(() -> Component.literal(
                        String.format("§7- §f%s §7%.1f%%", en.getKey(), en.getValue())), false));
        return 1;
    }
}
