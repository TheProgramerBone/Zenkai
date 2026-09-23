package com.hmc.zenkai.feature.training;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.content.entity.misc.ShadowCloneEntity;
import com.hmc.zenkai.feature.ZenkaiAttributes;
import com.hmc.zenkai.feature.combat.entity.EntityStatDef;
import com.hmc.zenkai.feature.combat.entity.EntityStats;
import com.hmc.zenkai.feature.forms.FormIds;
import com.hmc.zenkai.feature.forms.FormRegistry;
import com.hmc.zenkai.feature.player.PlayerFormAttachment;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.skills.SkillEffects;
import com.hmc.zenkai.feature.skills.SuperForms;
import com.hmc.zenkai.registry.ModEntities;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "Train with your shadow" (TrainingHubScreen -> ShadowTrainingScreen -> StartShadowTrainingPacket):
 * spawnea/rastrea/limpia el clon de un jugador. UNA sombra activa por jugador a la vez.
 *
 * El reward de TP NO se calcula aquí — es CERO código nuevo a propósito:
 *  - Matar la sombra: EntityDeathRewardHandler ya concede TP a CUALQUIER LivingEntity con
 *    EntityStats inicializado (ver su onDeath), y aquí YA dejamos EntityStats inicializado
 *    ANTES de spawnear (ver start()), así que el kill ya rinde con el pipeline normal.
 *  - Golpearla en vivo: CombatZenkaiHooks.applyToVanillaVictim ya llama grantTraining para
 *    CUALQUIER ServerPlayer atacante contra cualquier víctima que no sea otro jugador Zenkai —
 *    la sombra cuenta como "víctima vanilla" para ese pipeline, así que cada golpe ya reparte
 *    TP con pesas/HTC incluidos, sin tocar ese archivo.
 * Esta clase solo decide CUÁNTO PL tiene la sombra (tu PL limpio × dificultad) y quién es dueño
 * de quién, para la limpieza (despawnear al desconectar/morir/respawnear).
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class ShadowTrainingManager {
    private ShadowTrainingManager() {}

    public static final float MIN_FRACTION = 0.05f;
    public static final float MAX_FRACTION = 2.0f;

    /** playerId -> shadowId. Transitorio a propósito, igual que CombatModeServerState: un
     *  crash de servidor a mitad de sesión deja como mucho un mob huérfano en el mundo, no un
     *  estado inconsistente que sobreviva al reinicio. */
    private static final Map<UUID, UUID> ACTIVE = new ConcurrentHashMap<>();

    /**
     * @param simulatedFormId forma cuyo % de stats (a la maestría real del jugador en ella) se
     *                         usa para escalar el PL de la sombra — pedido explícito del usuario:
     *                         poder elegir "voy a entrenar como si llevara puesta X" desde
     *                         ShadowTrainingScreen sin depender de que el jugador se transforme
     *                         de verdad antes de pulsar Start. NO transforma al jugador ni toca
     *                         ningún otro estado suyo, solo el cálculo de PL de la sombra (ver
     *                         simulatedPowerLevel()/PlayerStatsAttachment.
     *                         getPowerLevelWithStatMultiplier). FormIds.BASE simula de verdad
     *                         "sin ninguna forma puesta" (statMultiplier=1.0), no "el PL actual
     *                         del jugador tal cual esté" — un id no desbloqueado/no permitido
     *                         para su raza cae a BASE, server-authoritative, nunca se confía en
     *                         el id que mande el cliente sin validarlo aquí.
     */
    public static void start(ServerPlayer sp, float frac, ResourceLocation simulatedFormId) {
        ServerLevel level = sp.serverLevel();
        // Ya tiene una sombra activa DE VERDAD (no una entrada colgada de una que ya
        // desapareció sin pasar por onDeath, p. ej. un /kill de admin).
        if (ACTIVE.containsKey(sp.getUUID())) {
            if (stillTracked(sp, level)) return;
            ACTIVE.remove(sp.getUUID());
        }
        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        if (!att.isRaceChosen()) return;

        long shadowPl = Math.max(1, Math.round(simulatedPowerLevel(sp, att, simulatedFormId) * frac));

        ShadowCloneEntity shadow = ModEntities.SHADOW_CLONE.get().create(level);
        if (shadow == null) return;

        // +1 bloque en Y: sin este margen, delante de una cuesta/escalón la sombra podía
        // spawnear a media altura dentro de un bloque de tierra en vez de encima. Con +1 cae
        // por gravedad al suelo real de todos modos si el terreno estaba llano, así que no
        // introduce el problema contrario (flotar).
        Vec3 pos = sp.position().add(sp.getLookAngle().normalize().scale(3.0)).add(0, 1.0, 0);
        shadow.moveTo(pos.x, pos.y, pos.z, sp.getYRot() + 180f, 0f);
        shadow.setOwner(sp);
        // El vuelo estilo Vex es solo la CAPACIDAD (shadow_clone.json can_fly:true) — el gate
        // real por-instancia es que el DUEÑO tenga la skill fly desbloqueada, pedido explícito
        // del usuario. Mismo query que ya usa el propio jugador (FlightSystem.tick()).
        shadow.setFlightAllowed(SkillEffects.canFly(sp));

        // Stats en runtime ANTES de addFreshEntity: para cuando EntityJoinLevelEvent dispare,
        // EntitySpawnStatsHandler ve isInitialized()=true y no los pisa (ver su propio onJoin).
        EntityStatDef def = new EntityStatDef(
                BuiltInRegistries.ENTITY_TYPE.getKey(ModEntities.SHADOW_CLONE.get()),
                shadowPl,
                false,
                "balanced",
                0,
                new EnumMap<>(ZenkaiAttributes.class),
                1.0, 1.0,
                List.of(),
                true,
                "auto",
                List.of(),
                false); // canFly aquí es irrelevante: ZenkaiDefaultMob lee el vuelo del def del
                        // DATAPACK vía EntityStatsManager.get(id) (shadow_clone.json), no de este
                        // EntityStatDef local — este solo alimenta stats.applyDef (vida/daño/PL).
        EntityStats stats = shadow.getData(ZenkaiDataAttachments.ENTITY_STATS.get());
        stats.applyDef(def, shadow);
        shadow.setData(ZenkaiDataAttachments.ENTITY_STATS.get(), stats);

        level.addFreshEntity(shadow);
        ACTIVE.put(sp.getUUID(), shadow.getUUID());

        // Snapshot para poder calcular "TP ganado ESTA pelea" al morir el clon — ver
        // TrainingData.shadowSessionStartTp y onShadowDeath() más abajo.
        sp.getData(ZenkaiDataAttachments.TRAINING.get()).setShadowSessionStartTp(att.getTP());
    }

    /** ¿Sigue vivo el mob que rastreamos para este jugador? Si desapareció sin pasar por
     *  onDeath (chunk descargado y la entidad se limpió, /kill de un admin...) no dejamos la
     *  entrada colgada bloqueando una sesión nueva para siempre. */
    private static boolean stillTracked(ServerPlayer sp, ServerLevel level) {
        UUID shadowId = ACTIVE.get(sp.getUUID());
        return shadowId != null && level.getEntity(shadowId) != null;
    }

    /**
     * PL simulado a SU maestría real de la forma pedida (0 si nunca la maestreó, igual que
     * cualquier otra consulta de maestría) — SIEMPRE se calcula así, BASE incluido
     * (FormRegistry.statPercent(BASE,...) ya da 0, así que el factor sale 1.0 solo, sin
     * necesitar un caso especial). Un id no desbloqueado/inventado/de otra raza cae a BASE antes
     * de calcular nada (`unlocked()` ya cubre BASE -> siempre true, y la rama divina vía
     * DivineForms.unlocked).
     *
     * A propósito NO devuelve el PL "real tal cual está el jugador ahora mismo" cuando se pide
     * BASE — un primer intento de esta función trataba BASE como sentinel de "usa mi PL actual",
     * lo que rompía la premisa del selector: si el jugador estaba transformado en SSJ y elegía
     * "Base" en la lista esperando simular una pelea SIN transformar, el PL no bajaba nada. Con
     * el cálculo uniforme, "Base" simula de verdad sus stats sin ninguna forma puesta, sea cual
     * sea su transformación real en ese instante.
     * Package-private (sin `private`): reusado también por ShadowPotentialRequestPacket para el
     * "TP potential: up to X" del selector, sin duplicar esta cuenta una tercera vez.
     */
    static long simulatedPowerLevel(ServerPlayer sp, PlayerStatsAttachment att,
                                     ResourceLocation simulatedFormId) {
        ResourceLocation formId = (simulatedFormId != null
                && (FormIds.BASE.equals(simulatedFormId) || SuperForms.unlocked(sp, simulatedFormId)))
                ? simulatedFormId : FormIds.BASE;
        PlayerFormAttachment formAtt = sp.getData(ZenkaiDataAttachments.PLAYER_FORM.get());
        double mastery = formAtt.getFormMastery(formId);
        double statMult = 1.0 + FormRegistry.statPercent(formId, mastery);
        return att.getPowerLevelWithStatMultiplier(statMult);
    }

    @SubscribeEvent
    public static void onShadowDeath(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ShadowCloneEntity shadow)) return;
        UUID ownerId = shadow.getOwnerId();
        if (ownerId == null) return;
        ACTIVE.remove(ownerId, shadow.getUUID());

        // Resumen de la pelea (TP potencial/récord/obtenido, pedido explícito del usuario — ver
        // Pista G del plan de pulido de Training): Shadow no tiene pantalla propia durante el
        // combate, así que esto es lo único que dispara un aviso al terminar. Si el dueño se
        // desconectó a mitad de la pelea, getPlayer() da null y simplemente no hay a quién
        // avisar — nada que limpiar, despawnFor() ya corrió en onLogout.
        if (!(shadow.level() instanceof ServerLevel level)) return;
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerId);
        if (owner == null) return;

        PlayerStatsAttachment att = PlayerStatsAttachment.get(owner);
        TrainingData td = owner.getData(ZenkaiDataAttachments.TRAINING.get());
        int earned = Math.max(0, att.getTP() - td.getShadowSessionStartTp());
        if (earned > td.getBestShadowTp()) td.setBestShadowTp(earned);

        PacketDistributor.sendToPlayer(owner,
                new ShadowSessionResultPacket(earned, td.getBestShadowTp()));
    }

    /** Morir a mitad de sesión despawnea la sombra en vez de dejarla abandonada peleando
     *  contra nadie. */
    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) despawnFor(sp);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) despawnFor(sp);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) despawnFor(sp);
    }

    private static void despawnFor(ServerPlayer sp) {
        UUID shadowId = ACTIVE.remove(sp.getUUID());
        if (shadowId == null) return;
        Entity e = sp.serverLevel().getEntity(shadowId);
        if (e != null) e.discard();
    }
}
