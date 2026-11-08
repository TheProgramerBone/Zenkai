package com.hmc.zenkai.feature.player;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.feature.ZenkaiAttributes;
import com.hmc.zenkai.feature.teleport.InstantTransmissionAttachment;
import com.hmc.zenkai.network.OpenRaceSelectionPayload;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.EnumMap;
import java.util.List;
import java.util.Set;

/**
 * Reencarnación (muerte permanente): el alma renuncia a su vida y nace otra vez. NO es un borrado: el cuerpo, el inventario, la
 * XP, la cama, el TP, las skills, las formas (y su maestría), el God Ki y el Legendario se pierden SIEMPRE, y el aspecto se
 * elige de nuevo (otra raza u otra cara). Lo que el alma recuerda, con porcentajes en {@code ServerConfig permadeath.reincarnation_*}:
 *  - un porcentaje de los PUNTOS INVERTIDOS en cada atributo (no del atributo final: se suman a la base de la raza nueva);
 *  - las técnicas de ki y físicas ya aprendidas, con un porcentaje de su maestría;
 *  - el alineamiento, acercado a neutro;
 *  - lo que es del jugador y no del personaje: maestros conocidos, récords y fatiga de Training (la fatiga se conserva para que
 *    morir no sirva de reinicio), biblioteca de peinados importados, party y cuerpos.
 *
 * LISTA EXPLÍCITA de attachments: cada {@code AttachmentType} de {@link ZenkaiDataAttachments} está en {@link #RESET} (se sustituye
 * por uno nuevo) o en {@link #KEPT} (no es del personaje o se conserva a propósito). Un attachment nuevo que no esté en ninguna
 * de las dos hace fallar el gametest {@code CharacterResetGameTests}.
 */
public final class CharacterReset {
    private CharacterReset() {}

    /** Se sustituyen por una instancia nueva (luego se les vuelca lo que el alma recuerda). */
    public static final Set<String> RESET = Set.of(
            "player_stats", "player_visual", "player_form", "instant_transmission");

    /** No se reinician: el cuerpo en brazos se suelta al morir, ki_shot/entity_stats no son del jugador y training es del
     *  jugador (récords y fatiga); rage_chronicle es la HISTORIA de las vidas (no concede nada al personaje nuevo). */
    public static final Set<String> KEPT = Set.of("carried_body", "ki_shot", "entity_stats", "training", "rage_chronicle");

    /** Solo los datos: sin mundo ni red, para poder comprobarlos en un gametest. */
    public static void resetData(ServerPlayer sp) {
        PlayerStatsAttachment old = PlayerStatsAttachment.get(sp);
        PlayerStatsAttachment fresh = new PlayerStatsAttachment();

        // Atributos: un porcentaje de los puntos invertidos, redondeado hacia abajo.
        int attrPct = ServerConfig.PERMADEATH_REINC_ATTRIBUTE_PERCENT.get();
        EnumMap<ZenkaiAttributes, Integer> kept = new EnumMap<>(ZenkaiAttributes.class);
        int[] oldBase = com.hmc.zenkai.feature.RaceStatTable.baseAttributes(old.getRace());
        for (ZenkaiAttributes a : ZenkaiAttributes.values()) {
            // Lo invertido con TP, o lo que haya por encima de la base de la raza si vino de otro sitio (p. ej. un comando de
            // administrador): sin esto, un atributo subido fuera de spendTP no dejaba nada que conservar.
            int above = Math.max(0, old.getAttribute(a) - oldBase[a.ordinal()]);
            int points = Math.max(old.raceStats().investedIn(a), above);
            kept.put(a, (int) ((long) points * attrPct / 100L));
        }
        fresh.raceStats().carryOverInvested(kept);

        // Técnicas aprendidas (las skills se olvidan: la instancia nueva no trae ninguna) y un porcentaje de su maestría.
        CompoundTag techniques = old.techniques().save();
        fresh.techniques().load(techniques);
        fresh.carryOverTechniqueMastery(old, ServerConfig.PERMADEATH_REINC_MASTERY_PERCENT.get() / 100f);

        // Karma acercado a neutro (la división entera trunca hacia cero en los dos signos).
        fresh.setAlignment(old.getAlignment() * ServerConfig.PERMADEATH_REINC_ALIGNMENT_PERCENT.get() / 100);

        // Maestros ya conocidos: conocerlos es historia del jugador; que te enseñen lo decide su admisión.
        for (String m : old.getMetMasters()) fresh.markMasterMet(m);

        fresh.applyRaceBaseAttributes();
        fresh.refillOnRespawn();
        fresh.setReincarnating(true);
        sp.setData(ZenkaiDataAttachments.PLAYER_STATS.get(), fresh);

        // Aspecto nuevo (se elige otra vez), salvo la biblioteca de peinados importados: es del jugador.
        var library = sp.getData(ZenkaiDataAttachments.PLAYER_VISUAL.get()).getCustomHairs();
        PlayerVisualAttachment visual = new PlayerVisualAttachment();
        visual.setCustomHairs(library);
        sp.setData(ZenkaiDataAttachments.PLAYER_VISUAL.get(), visual);

        sp.setData(ZenkaiDataAttachments.PLAYER_FORM.get(), new PlayerFormAttachment());
        sp.setData(ZenkaiDataAttachments.INSTANT_TRANSMISSION.get(), new InstantTransmissionAttachment());

        // Training se conserva entero salvo la foto de TP de una pelea de Sombra a medias, que no tendría sentido con TP 0.
        sp.getData(ZenkaiDataAttachments.TRAINING.get()).setShadowSessionStartTp(0);
    }

    /**
     * Quita de la entidad los modificadores de atributos de vanilla que puso el mod (escala de la forma, velocidad de
     * vuelo o de guardia, peso...). Todos son transitorios y los sistemas de tick los recalculan cada tick según el estado
     * ACTUAL, así que borrarlos aquí no pierde nada de la vida nueva y evita que la forma anterior deje su escala o su
     * velocidad hasta que el sistema correspondiente vuelva a pasar.
     */
    static void clearZenkaiModifiers(ServerPlayer sp) {
        for (var holder : net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.holders().toList()) {
            var inst = sp.getAttribute(holder);
            if (inst == null) continue;
            for (var mod : List.copyOf(inst.getModifiers())) {
                if (Zenkai.MOD_ID.equals(mod.id().getNamespace())) inst.removeModifier(mod.id());
            }
        }
    }

    /**
     * El acto completo: datos nuevos, cuerpo vanilla limpio, spawn del mundo, sincronización y pantalla de raza.
     * La llama Yemma tras la confirmación explícita (irreversible).
     */
    public static void perform(ServerPlayer sp) {
        resetData(sp);

        sp.removeAllEffects();
        clearZenkaiModifiers(sp);
        com.hmc.zenkai.feature.player.death.PlayerBodyManager.retireAllOf(sp.server, sp.getUUID());
        sp.getInventory().clearContent();
        sp.setExperienceLevels(0);
        sp.setExperiencePoints(0);
        sp.getFoodData().setFoodLevel(20);
        sp.setRemainingFireTicks(0);
        sp.fallDistance = 0.0F;
        sp.setInvulnerable(false);
        sp.setHealth(sp.getMaxHealth());
        if (sp.isSpectator()) sp.setGameMode(GameType.SURVIVAL);
        // Sin cama ni ancla de la vida anterior: reaparece en el spawn del mundo.
        sp.setRespawnPosition(Level.OVERWORLD, null, 0.0F, false, false);

        ServerLevel overworld = sp.server.overworld();
        var spawn = overworld.getSharedSpawnPos();
        sp.setDeltaMovement(Vec3.ZERO);
        sp.teleportTo(overworld, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, sp.getYRot(), sp.getXRot());

        PlayerLifeCycle.sync(sp);
        PlayerLifeCycle.syncVisualToTrackersAndSelf(sp);
        PlayerLifeCycle.syncFormToTrackersAndSelf(sp);
        PacketDistributor.sendToPlayer(sp, new OpenRaceSelectionPayload());
    }
}
