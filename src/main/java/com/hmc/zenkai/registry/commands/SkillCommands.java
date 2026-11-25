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

        // Mismo techo que SkillBuyPacket y giveAll (FormDrivenSkills: las skills exclusivas de una raza).
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
     * OJO: el techo real puede no ser def.maxLevel() (skills exclusivas de una raza, como el Gigante
     * Namekiano): misma regla que SkillBuyPacket, vía FormDrivenSkills.
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

    // ── Desbloqueo de transformaciones (pruebas de admin) ────────────────────

    /** Formas que el comando puede conceder: nodos de forma del árbol de la raza, sin Oozaru/Súper Oozaru (automáticas) ni Místico (es su doctrina). */
    private static java.util.List<com.hmc.zenkai.feature.forms.FormDef> grantableForms(com.hmc.zenkai.feature.Race race) {
        java.util.List<com.hmc.zenkai.feature.forms.FormDef> out = new java.util.ArrayList<>();
        for (var n : com.hmc.zenkai.feature.skilltree.SkillTreeNodes.build(race)) {
            if (n.kind() != com.hmc.zenkai.feature.skilltree.SkillTreeNodes.Kind.FORM || n.form() == null) continue;
            if (com.hmc.zenkai.feature.forms.FormIds.OOZARU.equals(n.form())
                    || com.hmc.zenkai.feature.forms.FormIds.SUPER_OOZARU.equals(n.form())) continue;
            var d = com.hmc.zenkai.feature.forms.FormDef.get(n.form());
            if (d != null) out.add(d);
        }
        return out;
    }

    /** Nombres de formas (ids completos) que se pueden conceder a este jugador: para el autocompletado del comando. */
    public static java.util.List<String> grantableFormIds(ServerPlayer sp) {
        var att = PlayerStatsAttachment.get(sp);
        java.util.List<String> ids = new java.util.ArrayList<>();
        if (att.isRaceChosen() && att.getRace() != null) {
            for (var d : grantableForms(att.getRace())) ids.add(d.id().toString());
        }
        return ids;
    }

    /** Qué le falta a una forma, aparte de poseer su nodo, para poder usarse (texto corto) o "" si nada. */
    private static String extraNeed(PlayerStatsAttachment att, com.hmc.zenkai.feature.forms.FormDef d) {
        if (com.hmc.zenkai.feature.forms.LegendaryForms.ownerOf(d.id()) != null && !att.isLegendary()) return "requiere estado Legendario";
        return "";
    }

    /**
     * Concede UNA forma con todo lo que necesita para poder usarse (sin TP ni MIND): sus antepasados, God Ki si es divina, el ritual
     * si es SSJ4, el nivel de la doctrina si es de una ruta universal y la furia si es Beast. No toca el estado Legendario.
     * Devuelve los ids que concedió de nuevo.
     */
    private static void grantFormWithPrereqs(PlayerStatsAttachment att, com.hmc.zenkai.feature.Race race,
                                             com.hmc.zenkai.feature.forms.FormDef d, java.util.List<String> granted) {
        if (d.parent() != null && !com.hmc.zenkai.feature.forms.FormIds.BASE.equals(d.parent())) {
            var pd = com.hmc.zenkai.feature.forms.FormDef.get(d.parent());
            if (pd != null && pd.allows(race)) grantFormWithPrereqs(att, race, pd, granted);
        }
        if (d.endgamePath()) {
            if (com.hmc.zenkai.feature.forms.FormIds.ULTRA_EGO.equals(d.id())) {
                att.skills().grantAdmin(com.hmc.zenkai.feature.skills.SkillEffects.ULTRA_EGO,
                        com.hmc.zenkai.event.tick.OverlayMetersSystem.UE_FORM_LEVEL);
            } else if (com.hmc.zenkai.feature.forms.FormIds.BEAST.equals(d.id())) {
                att.setBeastFury(true);
                att.skills().grantAdmin(com.hmc.zenkai.feature.skills.SkillEffects.POTENTIAL_UNLOCK, 1);
            } else {
                int lvl = com.hmc.zenkai.feature.skills.EndgamePaths.requiredUiLevel(d.id());
                if (lvl > 0) att.skills().grantAdmin(com.hmc.zenkai.feature.skills.SkillEffects.ULTRA_INSTINCT, lvl);
            }
            att.skills().grantAdmin(com.hmc.zenkai.feature.skills.DivineForms.SKILL, 1);
            granted.add(d.id().toString());
            return;
        }
        if (com.hmc.zenkai.feature.forms.FormIds.POTENTIAL_UNLOCK.equals(d.id())) {
            att.skills().grantAdmin(com.hmc.zenkai.feature.skills.SkillEffects.POTENTIAL_UNLOCK, 1);
            granted.add(d.id().toString());
            return;
        }
        if (d.divineTier()) att.skills().grantAdmin(com.hmc.zenkai.feature.skills.DivineForms.SKILL, 1);
        if (com.hmc.zenkai.feature.forms.FormIds.SSJ4.equals(d.id())) {
            att.setHasSsj4Ritual(true);
            att.setReachedSuperOozaru(true);
        }
        if (att.skillTree().ownedLevel(d.id()) < 1) {
            com.hmc.zenkai.feature.skilltree.SkillTree.grantNode(att, d.id(), true);
            granted.add(d.id().toString());
        }
    }

    /** {@code /zenkai skill unlockforms}: lista las transformaciones de la raza y cuáles se pueden desbloquear con el comando. */
    public static int formsList(CommandContext<CommandSourceStack> ctx, ServerPlayer sp) {
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        if (!att.isRaceChosen() || att.getRace() == null) {
            ctx.getSource().sendFailure(Component.literal("[Zenkai] " + sp.getGameProfile().getName() + " no ha elegido raza."));
            return 0;
        }
        var forms = grantableForms(att.getRace());
        ctx.getSource().sendSuccess(() -> Component.literal("[Zenkai] Transformaciones de " + sp.getGameProfile().getName() + " ("
                + att.getRace().name() + "): ✔ ya disponible · ○ se puede desbloquear · ⚠ se puede, pero falta algo")
                .withStyle(ChatFormatting.GOLD), false);
        int pending = 0;
        for (var d : forms) {
            boolean have = com.hmc.zenkai.feature.skilltree.FormAccess.unlocked(sp, d.id());
            String need = have ? "" : extraNeed(att, d);
            if (!have) pending++;
            ChatFormatting color = have ? ChatFormatting.GREEN : need.isEmpty() ? ChatFormatting.YELLOW : ChatFormatting.RED;
            String mark = have ? "✔ " : need.isEmpty() ? "○ " : "⚠ ";
            String text = mark + d.id() + (need.isEmpty() ? "" : "  (" + need + ")");
            ctx.getSource().sendSuccess(() -> Component.literal("  ").append(Component.literal(text).withStyle(color)), false);
        }
        final int pend = pending;
        ctx.getSource().sendSuccess(() -> Component.literal("[Zenkai] " + pend + " pendiente(s). Una concreta: /zenkai skill unlockforms give "
                + sp.getGameProfile().getName() + " <forma>; todas: /zenkai skill unlockforms all").withStyle(ChatFormatting.GRAY), false);
        return forms.size();
    }

    /** {@code /zenkai skill unlockforms give <jugador> <forma>}: concede una forma concreta con sus requisitos. */
    public static int formsGive(CommandContext<CommandSourceStack> ctx, ServerPlayer sp, ResourceLocation form) {
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        if (!att.isRaceChosen() || att.getRace() == null) {
            ctx.getSource().sendFailure(Component.literal("[Zenkai] " + sp.getGameProfile().getName() + " no ha elegido raza."));
            return 0;
        }
        var d = com.hmc.zenkai.feature.forms.FormDef.get(form);
        if (d == null || !grantableForms(att.getRace()).contains(d)) {
            ctx.getSource().sendFailure(Component.literal("[Zenkai] '" + form + "' no es una transformación concedible de "
                    + att.getRace().name() + ". Mira la lista con /zenkai skill unlockforms"));
            return 0;
        }
        java.util.List<String> granted = new java.util.ArrayList<>();
        grantFormWithPrereqs(att, att.getRace(), d, granted);
        PlayerLifeCycle.sync(sp);
        boolean usable = com.hmc.zenkai.feature.skilltree.FormAccess.unlocked(sp, d.id());
        String need = usable ? "" : extraNeed(att, d);
        ctx.getSource().sendSuccess(() -> Component.literal("[Zenkai] " + sp.getGameProfile().getName() + ": "
                + (granted.isEmpty() ? "ya la tenía (" + d.id() + ")" : "concedidas " + granted)
                + (usable ? " → desbloqueada." : " → concedida, pero todavía no se puede usar" + (need.isEmpty() ? "." : ": " + need + "."))), true);
        return 1;
    }

    /**
     * {@code /zenkai skill unlockforms all}: desbloquea TODAS las transformaciones concedibles de la raza (cada una con sus requisitos).
     * NO toca el estado Legendario ({@code isLegendary}): es un hecho de personaje, no una forma comprable.
     */
    public static int formsUnlockAll(CommandContext<CommandSourceStack> ctx, ServerPlayer sp) {
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        if (!att.isRaceChosen() || att.getRace() == null) {
            ctx.getSource().sendFailure(Component.literal("[Zenkai] " + sp.getGameProfile().getName() + " no ha elegido raza."));
            return 0;
        }
        java.util.List<String> granted = new java.util.ArrayList<>();
        for (var d : grantableForms(att.getRace())) grantFormWithPrereqs(att, att.getRace(), d, granted);
        PlayerLifeCycle.sync(sp);
        long skipped = grantableForms(att.getRace()).stream()
                .filter(d -> !com.hmc.zenkai.feature.skilltree.FormAccess.unlocked(sp, d.id())).count();
        ctx.getSource().sendSuccess(() -> Component.literal("[Zenkai] Transformaciones para " + sp.getGameProfile().getName() + ": "
                + granted.size() + " concedida(s)" + (skipped > 0 ? ", " + skipped + " sin poder usarse aún (estado Legendario): ver /zenkai skill unlockforms" : ".")), true);
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
                    + (def.purchasable() ? "" : " §8[no comprable]");
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
