package com.hmc.zenkai.feature.skilltree;

import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.forms.FormDef;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Comandos de depuración del skill tree: {@code /zenkai debug tree [verify|nodes|refresh]} (F1) y
 * {@code audience|trial|ritual|reset} (F5).
 * Herramienta de desarrollo, igual que el resto de /zenkai debug: no hay claves de lang a propósito.
 */
public final class SkillTreeDebug {
    private SkillTreeDebug() {}

    /** Compara, para cada forma que se decide por compra, FormAccess.unlocked() (lo que ve el
     *  juego) con el nodo poseído. Si usable pero NO poseído: la migración se dejó algo. */
    public static int verify(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer sp = ctx.getSource().getPlayerOrException();
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        Race race = att.getRace();
        List<String> errors = new ArrayList<>();
        List<String> info = new ArrayList<>();
        int checked = 0;
        for (FormDef d : FormDef.all()) {
            if (!d.allows(race)) continue;
            Boolean byPurchase = SkillTreeMigration.ownedByPurchase(att.skills(), race, d);
            // Las formas divinas son nodos propios desde F5: también se comprueban.
            if (byPurchase == null && !(d.divineTier() && !d.endgamePath())) continue;
            checked++;
            boolean usable = FormAccess.unlocked(sp, d.id());
            boolean owned = att.skillTree().owns(d.id());
            if (usable && !owned) errors.add(d.id().getPath());
            else if (owned && !usable) info.add(d.id().getPath());
        }
        final int n = checked;
        ctx.getSource().sendSuccess(() -> Component.literal("[tree] raza " + race + ": " + n
                + " formas por compra comprobadas, " + att.skillTree().all().size() + " nodos, versión "
                + att.skillTree().version()), false);
        if (!errors.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("[tree] ERROR usables sin nodo: " + errors)
                    .withStyle(ChatFormatting.RED), false);
        }
        if (!info.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("[tree] poseídas pero aún no usables (condición pendiente): "
                    + info).withStyle(ChatFormatting.GRAY), false);
        }
        if (errors.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("[tree] OK: cada forma usable por compra tiene su nodo")
                    .withStyle(ChatFormatting.GREEN), false);
        }
        return errors.isEmpty() ? 1 : 0;
    }

    public static int nodes(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer sp = ctx.getSource().getPlayerOrException();
        var all = PlayerStatsAttachment.get(sp).skillTree().all();
        ctx.getSource().sendSuccess(() -> Component.literal("[tree] nodos: " + all), false);
        return all.size();
    }

    /** Recalcula el espejo desde las skills (útil tras recargar el datapack de formas). */
    public static int refresh(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer sp = ctx.getSource().getPlayerOrException();
        SkillTreeMigration.refresh(PlayerStatsAttachment.get(sp));
        com.hmc.zenkai.feature.player.PlayerLifeCycle.sync(sp);
        return verify(ctx);
    }

    public static int audience(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer sp = ctx.getSource().getPlayerOrException();
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        att.setGodKiAudience(!att.hasGodKiAudience());
        com.hmc.zenkai.feature.player.PlayerLifeCycle.sync(sp);
        ctx.getSource().sendSuccess(() -> Component.literal("[tree] audiencia divina: " + att.hasGodKiAudience()), false);
        return 1;
    }

    public static int trial(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer sp = ctx.getSource().getPlayerOrException();
        boolean ok = GodKi.startTrial(sp);
        ctx.getSource().sendSuccess(() -> Component.literal("[tree] prueba de God Ki: " + (ok ? "en marcha" : "no se pudo (¿ya hay una sombra?)")), false);
        return ok ? 1 : 0;
    }

    /** Rodea al jugador de muñecos que cuentan como participantes: hay que cargar ki (flujo real). */
    public static int ritualDummies(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer sp = ctx.getSource().getPlayerOrException();
        int n = GodKi.debugSpawnRitualDummies(sp);
        boolean cand = GodKi.ritualCandidate(sp);
        ctx.getSource().sendSuccess(() -> Component.literal("[tree] ritual: " + n + " muñecos creados. "
                + (cand ? "Mantén la carga de ki hasta que termine."
                : "Aún NO eres candidato (cadena mortal/requisitos de la primera forma divina, o ya tienes God Ki).")), false);
        return n;
    }

    /** Completa el ritual al instante si el jugador es candidato (cadena mortal y requisitos). */
    public static int ritual(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer sp = ctx.getSource().getPlayerOrException();
        boolean ok = GodKi.debugCompleteRitual(sp);
        ctx.getSource().sendSuccess(() -> Component.literal("[tree] ritual: " + (ok ? "completado"
                : "no es candidato (ya tiene God Ki, le falta la cadena mortal o un requisito)")), false);
        return ok ? 1 : 0;
    }

    public static int reset(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer sp = ctx.getSource().getPlayerOrException();
        SkillTree.resetTree(sp);
        ctx.getSource().sendSuccess(() -> Component.literal("[tree] árbol reiniciado (sin reembolso)"), false);
        return 1;
    }
}
