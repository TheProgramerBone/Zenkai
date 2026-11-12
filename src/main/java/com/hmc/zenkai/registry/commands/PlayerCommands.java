package com.hmc.zenkai.registry.commands;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.content.effect.MajinEffect;
import com.hmc.zenkai.feature.ZenkaiAttributes;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.Style;
import com.hmc.zenkai.feature.aura.TurboServerState;
import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.player.PlayerVisualAttachment;
import com.hmc.zenkai.feature.skills.SkillEffects;
import com.hmc.zenkai.feature.training.TrainingCategory;
import com.hmc.zenkai.feature.training.TrainingData;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import com.hmc.zenkai.registry.*;

/**
 * Comandos que tocan los DATOS del jugador (TP, atributos, raza, estilo, alineamiento, resets, mascotas, revivir, respec). El árbol /zenkai vive en {@link ModCommands}; aquí solo las implementaciones.
 */
public final class PlayerCommands {
    private PlayerCommands() {}

    public static int addTp(CommandContext<CommandSourceStack> ctx, ServerPlayer sp, int amount) {
        var att = sp.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
        att.addTP(amount);
        PlayerLifeCycle.sync(sp);
        ctx.getSource().sendSuccess(
                () -> Component.literal("[Zenkai] +" + amount + " TP → " + sp.getGameProfile().getName()), true);
        return 1;
    }

    public static int setAttr(CommandContext<CommandSourceStack> ctx,
                               ServerPlayer sp, String attrName, int value) {
        var att = sp.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
        ZenkaiAttributes a = parseAttr(attrName);
        if (a == null) {
            ctx.getSource().sendFailure(Component.literal("[Zenkai] Unknown attribute: " + attrName
                    + ". Valid: strength, constitution, dexterity, willpower, spirit, mind (or STR, CON, DEX, WIL, SPI, MND)"));
            return 0;
        }
        att.setAttribute(a, value);
        PlayerLifeCycle.sync(sp);
        ctx.getSource().sendSuccess(
                () -> Component.literal("[Zenkai] " + a + " = " + value + " → " + sp.getGameProfile().getName()), true);
        return 1;
    }

    /** Ver el comentario de la rama "alignment" más arriba. {@code value} llega SIN acotar:
     *  setAlignment() ya lo satura a [-100, 100], así que un admin puede pedir 9999 o -9999
     *  sin que el comando falle — simplemente queda en el extremo correspondiente. */
    public static int alignmentSet(CommandContext<CommandSourceStack> ctx, ServerPlayer sp, int value) {
        var att = sp.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
        att.setAlignment(value);
        PlayerLifeCycle.sync(sp);
        int applied = att.getAlignment();
        ctx.getSource().sendSuccess(
                () -> Component.literal("[Zenkai] Alignment = " + applied + " → " + sp.getGameProfile().getName()), true);
        return 1;
    }

    public static int setAllAttr(CommandContext<CommandSourceStack> ctx, ServerPlayer sp, int value) {
        var att = sp.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
        int v = Math.max(0, Math.min(value, ServerConfig.globalAttributeCap()));
        for (ZenkaiAttributes a : ZenkaiAttributes.values()) att.setAttribute(a, v);
        PlayerLifeCycle.sync(sp);
        ctx.getSource().sendSuccess(
                () -> Component.literal("[Zenkai] All attributes = " + v + " → " + sp.getGameProfile().getName()), true);
        return 1;
    }

    public static int maxAllAttr(CommandContext<CommandSourceStack> ctx, ServerPlayer sp) {
        return setAllAttr(ctx, sp, ServerConfig.globalAttributeCap());
    }

    public static int setRace(CommandContext<CommandSourceStack> ctx, ServerPlayer sp, String raceName) {
        Race r;
        try { r = Race.valueOf(raceName.toUpperCase()); }
        catch (IllegalArgumentException ex) {
            ctx.getSource().sendFailure(Component.literal("[Zenkai] Unknown race: " + raceName));
            return 0;
        }
        sp.getData(ZenkaiDataAttachments.PLAYER_STATS.get()).setRace(r);
        com.hmc.zenkai.feature.race.FaceBans.reconcile(r, sp.getData(ZenkaiDataAttachments.PLAYER_VISUAL.get()));
        PlayerLifeCycle.sync(sp);
        PlayerLifeCycle.syncVisualToTrackersAndSelf(sp);
        ctx.getSource().sendSuccess(
                () -> Component.literal("[Zenkai] Race = " + r + " → " + sp.getGameProfile().getName()), true);
        return 1;
    }

    public static int setStyle(CommandContext<CommandSourceStack> ctx, ServerPlayer sp, String styleName) {
        Style s;
        try { s = Style.valueOf(styleName.toUpperCase()); }
        catch (IllegalArgumentException ex) {
            ctx.getSource().sendFailure(Component.literal("[Zenkai] Unknown style: " + styleName));
            return 0;
        }
        sp.getData(ZenkaiDataAttachments.PLAYER_STATS.get()).setStyle(s);
        PlayerLifeCycle.sync(sp);
        ctx.getSource().sendSuccess(
                () -> Component.literal("[Zenkai] Style = " + s + " → " + sp.getGameProfile().getName()), true);
        return 1;
    }

    /** Respec: devuelve los TP invertidos sin tocar raza ni estilo. */
    public static int resetStats(CommandContext<CommandSourceStack> ctx, ServerPlayer sp) {
        sp.getData(ZenkaiDataAttachments.PLAYER_STATS.get()).respec();
        // Las formas también se pierden: hay que volver a aprenderlas. Va aquí y no dentro
        // de respec() porque PlayerStatsAttachment no tiene referencia al Player, y la forma
        // vive en otro attachment.
        sp.getData(ZenkaiDataAttachments.PLAYER_FORM.get()).clearProgression();
        PlayerLifeCycle.sync(sp);
        // clearProgression() ya deja formId en BASE en el attachment del SERVIDOR, pero
        // PlayerLifeCycle.sync() solo manda SyncPlayerStatsPacket: sin este sync aparte, el
        // cliente (propio y quien lo esté trackeando) se queda con la forma vieja cacheada y
        // sigue renderizando la transformación anterior encima del respec.
        PlayerLifeCycle.syncForm(sp);
        ctx.getSource().sendSuccess(
                () -> Component.literal("[Zenkai] Stats respec done → " + sp.getGameProfile().getName()), true);
        return 1;
    }

    /**
     * Reset completo: raza, estilo, stats, TP y apariencia visual (pelo, ojos, colores, etc.).
     * El jugador deberá pasar por la creación de personaje de nuevo.
     */
    public static int resetFull(CommandContext<CommandSourceStack> ctx, ServerPlayer sp) {
        // ── Stats ─────────────────────────────────────────────────────────────
        var att = sp.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
        att.skills().clear();
        // Técnicas físicas y de ki aprendidas: submódulo aparte de las habilidades, así que
        // skills().clear() no las toca. Un reset "full" que las dejara vivas no sería full.
        att.techniques().clearAll();
        // Maestría de técnica: la de forma/kaioken ya se limpia abajo vía
        // PLAYER_FORM.clearProgression(), pero esa no toca la de PlayerStatsAttachment.
        att.clearTechniqueMastery();
        att.setRace(Race.HUMAN);
        att.setStyle(Style.MARTIAL_ARTIST);
        att.respec();
        sp.getData(ZenkaiDataAttachments.PLAYER_FORM.get()).clearProgression();
        sp.getData(ZenkaiDataAttachments.PLAYER_FORM.get()).forceBase();
        att.addTP(-att.getTP());
        att.setRaceChosen(false);
        att.setStyleChosen(false);
        att.setFlyEnabled(false);
        att.setImmortal(false);
        att.setLegendary(false);
        att.setDivine(false);
        // "Majin" no tiene flag propio en PlayerStatsAttachment (ese campo se eliminó por
        // vestigial, no representaba nada) — el estado real es
        // PlayerVisualAttachment.isMajinControlled, y ese YA queda limpio más abajo cuando el
        // reset recarga el attachment visual entero con los valores por defecto.
        // Ritual de Oozaru: un reset "full" que dejara esto vivo permitiría re-desbloquear
        // SSJ4 sin repetir el ritual en cuanto se re-comprara el nivel — no sería full. La
        // cola vuelve a su valor por defecto (true) por la misma razón de "estado limpio",
        // aunque el ítem de cola puede haberla puesto en false.
        att.setHasSsj4Ritual(false);
        att.setBeastFury(false); // idem: Beast se vuelve a desbloquear con una nueva furia
        att.setReachedSuperOozaru(false);
        // Skill tree: nodos propios (pasivas, formas divinas), God Ki y audiencia divina. Mismo
        // punto único que usará la reencarnación (SkillTree.resetTree).
        com.hmc.zenkai.feature.skilltree.SkillTree.resetTree(sp);
        att.setHasTail(true);
        // Servicio de Kaiosama: un reset "full" también devuelve el derecho a pedir las
        // pesas de nuevo, igual que borra lo demás — no sería full si dejara este
        // regalo de una sola vez "ya gastado" para una vida completamente nueva.
        att.setReceivedKaioWeights(false);
        att.setAlignment(0);
        // Sin deseos concedidos todavía: el cooldown de Shenlong solo corre desde el primer deseo.
        // (Antes esto guardaba la DURACIÓN del cooldown como si fuera un instante: tras un reset
        // el jugador quedaba bloqueado para invocar hasta que el mundo cumpliera esos días.)
        att.setLastSummonTick(Long.MIN_VALUE);
        // No se queda cargando ki ni con el % de poder subido: vuelve al 50% base (el mismo
        // que da SkillEffects.maxPowerPercent con Ki Control en 0, que es donde acaba de
        // dejarlo skills().clear() de arriba).
        att.setChargingKi(false);
        att.setPowerPercent(50, SkillEffects.maxPowerPercent(sp));
        // El turbo vive en un mapa estático aparte (TurboServerState), no en el attachment:
        // sin esto el jugador seguía drenando ki con el modo turbo encendido tras el reset.
        TurboServerState.set(sp, false);

        AttributeInstance scaleAttr = sp.getAttribute(Attributes.SCALE);
        if (scaleAttr != null) {
            scaleAttr.setBaseValue(1.0D);
            scaleAttr.getModifiers().forEach(scaleAttr::removeModifier);
        }

        PlayerLifeCycle.sync(sp);
        // clearProgression()+forceBase() ya dejan formId en BASE en el attachment del
        // SERVIDOR, pero PlayerLifeCycle.sync() solo manda SyncPlayerStatsPacket: sin este
        // sync aparte, el cliente (propio y quien lo esté trackeando) se queda con la forma
        // vieja cacheada — así es como un reset full seguido de elegir una raza nueva podía
        // dejar, por ejemplo, a un Human recién creado con el aura/escala/pelo de un SSJ4.
        PlayerLifeCycle.syncForm(sp);
        // ── Visual (pelo, ojos, colores, índices) ─────────────────────────────
        // Cargar un attachment limpio con el conjunto de valores por defecto
        var visual = sp.getData(ZenkaiDataAttachments.PLAYER_VISUAL.get());
        visual.load(new PlayerVisualAttachment().save());
        PlayerLifeCycle.syncVisualToTrackersAndSelf(sp);
        // El load() de arriba ya deja majinControlled=false en el attachment, pero NO quita el
        // MobEffectInstance si el jugador tenía Majin activo — sin este removeEffect síncrono,
        // el propio tick del efecto (MajinEffect.applyEffectTick, que corre ANTES que
        // PersistentEffectsSystem dentro del mismo tick de juego) lo detecta todavía presente y
        // vuelve a poner el flag en true, deshaciendo el reset. Ver el javadoc de
        // MajinEffect.setControlled para el porqué exacto de la condición de carrera.
        sp.removeEffect(ModEffects.MAJIN);

        ctx.getSource().sendSuccess(
                () -> Component.literal("[Zenkai] Full reset done → " + sp.getGameProfile().getName()), true);
        return 1;
    }

    /**
     * Reinicia a 0 la fatiga/carry/lastDecay de las TRES categorías de entrenamiento (ver
     * TrainingCategory: COMBAT/MEDITATION/TARGET_PRACTICE, cada una independiente desde
     * 2026-09-09) — pedido explícito del usuario. NO toca récords (bestMeditationTp/
     * bestTargetPracticeTp/bestShadowTp) ni el TP ya ganado: esto solo reinicia el "cansancio"
     * que penaliza la eficiencia de seguir ganando, no el progreso conseguido.
     */
    public static int resetTrainingFatigue(CommandContext<CommandSourceStack> ctx, ServerPlayer sp) {
        TrainingData td = sp.getData(ZenkaiDataAttachments.TRAINING.get());
        long now = sp.level().getGameTime();
        for (TrainingCategory cat : TrainingCategory.values()) {
            td.setFatigue(cat, 0.0);
            td.setCarry(cat, 0.0);
            td.setLastDecayTime(cat, now);
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
                "[Zenkai] Training fatigue reset → " + sp.getGameProfile().getName()), true);
        return 1;
    }

    /**
     * Devuelve el derecho a invocar al dragón: borra el cooldown POR JUGADOR de la invocación. Ese cooldown
     * ({@code summon_cooldown_ticks}) corre desde el último DESEO concedido (WishFinalizer guarda el instante en
     * {@code lastSummonTick}); {@code Long.MIN_VALUE} es "sin deseos todavía", o sea sin cooldown. No toca el
     * gamerule de invocación ni al dragón que ya esté invocado.
     */
    public static int resetSummon(CommandContext<CommandSourceStack> ctx, ServerPlayer sp) {
        sp.getData(ZenkaiDataAttachments.PLAYER_STATS.get()).setLastSummonTick(Long.MIN_VALUE);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "[Zenkai] Dragon summon cooldown reset → " + sp.getGameProfile().getName()), true);
        return 1;
    }

    /** {@code /zenkai reset visit}: quita la espera entre visitas a la Tierra (no toca una visita en curso). */
    public static int resetVisit(CommandContext<CommandSourceStack> ctx, ServerPlayer sp) {
        var flags = sp.getData(ZenkaiDataAttachments.PLAYER_STATS.get()).flags();
        flags.setEarthVisitCooldownTicks(0L);
        PlayerLifeCycle.sync(sp);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "[Zenkai] Earth visit cooldown reset → " + sp.getGameProfile().getName()), true);
        return 1;
    }

    public static int clearPets(CommandContext<CommandSourceStack> ctx, ServerPlayer sp) {
        var att = sp.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
        int n = att.getDeadPets().size();
        att.clearDeadPets();
        PlayerLifeCycle.sync(sp);
        ctx.getSource().sendSuccess(
                () -> Component.literal("[Zenkai] Cleared " + n + " dead pet(s) → " + sp.getGameProfile().getName()), true);
        return 1;
    }

    /** Borra la crónica de Rage Forms y manda la lista vacía al cliente para que la pantalla no muestre datos viejos. */
    public static int clearRageChronicle(CommandContext<CommandSourceStack> ctx, ServerPlayer sp) {
        var chronicle = com.hmc.zenkai.feature.rage.RageChronicle.get(sp);
        int n = chronicle.size();
        chronicle.clear();
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp,
                new com.hmc.zenkai.feature.rage.RageChronicleSyncPacket(chronicle.entries()));
        ctx.getSource().sendSuccess(() -> Component.literal(
                "[Zenkai] Cleared " + n + " rage chronicle entr" + (n == 1 ? "y" : "ies") + " → " + sp.getGameProfile().getName()), true);
        return 1;
    }

    /**
     * Olvida las formas «ya vistas»: la próxima vez que te transformes en cada una vuelve la secuencia larga con su escena. Sirve para
     * probarla en una partida antigua, donde la migración marcó como vista cada forma que ya tenías.
     */
    public static int resetRageScene(CommandContext<CommandSourceStack> ctx, ServerPlayer sp) {
        var flags = com.hmc.zenkai.feature.player.PlayerStatsAttachment.get(sp).flags();
        int n = flags.longCinematicSeen().size();
        flags.clearLongCinematicSeen();
        com.hmc.zenkai.feature.player.PlayerLifeCycle.sync(sp);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "[Zenkai] Forgot " + n + " seen form(s): the next transformation into each plays the long scene → " + sp.getGameProfile().getName()), true);
        return 1;
    }

    public static int revivePlayer(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        boolean ok = com.hmc.zenkai.feature.player.OtherworldManager.revive(target);
        if (!ok) {
            ctx.getSource().sendFailure(Component.literal("[Zenkai] " + target.getGameProfile().getName() + " is not in the otherworld."));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("[Zenkai] Revived " + target.getGameProfile().getName()), true);
        return 1;
    }

    /** Nombre completo o abreviatura de 3 letras (str, con, dex, wil, spi, mnd); null si no existe. */
    @org.jetbrains.annotations.Nullable
    public static ZenkaiAttributes parseAttr(String raw) {
        String s = raw.toUpperCase(java.util.Locale.ROOT);
        for (ZenkaiAttributes a : ZenkaiAttributes.values()) if (a.name().equals(s)) return a;
        return switch (s) {
            case "STR" -> ZenkaiAttributes.STRENGTH;
            case "CON" -> ZenkaiAttributes.CONSTITUTION;
            case "DEX" -> ZenkaiAttributes.DEXTERITY;
            case "WIL" -> ZenkaiAttributes.WILLPOWER;
            case "SPI" -> ZenkaiAttributes.SPIRIT;
            case "MND" -> ZenkaiAttributes.MIND;
            default -> null;
        };
    }

    public static int respec(CommandContext<CommandSourceStack> ctx, ServerPlayer sp) {
        var att = sp.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
        int before = att.getTP();
        att.respec();
        int refunded = att.getTP() - before;
        PlayerLifeCycle.sync(sp);
        ctx.getSource().sendSuccess(() -> Component.translatable(
                "command.zenkai.respec.done", refunded, sp.getName()), true);
        return 1;
    }
}
