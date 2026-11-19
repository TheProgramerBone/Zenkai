package com.hmc.zenkai.feature.technique;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.config.ServerConfig;
import com.hmc.zenkai.feature.action.ActionState;
import com.hmc.zenkai.feature.action.ActionStateServer;
import com.hmc.zenkai.feature.alignment.AlignmentTier;
import com.hmc.zenkai.registry.ModParticles;
import com.hmc.zenkai.feature.party.PartyManager;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Genki Dama viva: mientras el jugador CARGA la técnica SPIRIT_BOMB, cada 20 ticks recibe energía
 * de dos fuentes y la suma como bonus de daño, hasta un tope:
 *  - CRIATURAS de alrededor, con retornos decrecientes (raíz cuadrada del número): las NO
 *    HOSTILES (aldeanos, animales, gólems, fauna acuática y ambiental) para un jugador bueno o
 *    neutral, y las HOSTILAS para un jugador MALVADO (AlignmentTier.EVIL, pedido del usuario): la
 *    energía viene de los mobs que le son afines, con la misma estela pero roja en vez de blanca.
 *  - COMPAÑEROS DE PARTY que estén cargando ki (tecla C), a CUALQUIER distancia (misma dimensión;
 *    `spirit_bomb.party_radius` > 0 lo limita): cada uno suma una cantidad fija
 *    por pulso. Al EMPEZAR la carga, los miembros de la party reciben un aviso en el chat pidiendo
 *    su energía. Otros jugadores fuera de la party no cuentan.
 *
 * El bonus es un FACTOR de daño (1 + bonus) que KiFirePacket aplica al soltar, NO un ratio de
 * carga: así no sube el coste de ki (chargeCostFactor recarga por encima del 100 %) ni cuenta como
 * sobrecarga. Se acumula por UUID y se descarta en cuanto el jugador deja de cargar la Genki Dama
 * (disparo, cancelación, cambio de técnica, muerte, desconexión).
 *
 * Es lógica de servidor: el cliente solo ve las partículas y los mensajes.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class SpiritBombGatherer {
    private SpiritBombGatherer() {}

    private static final int PULSE_TICKS = 20;
    /** Tope de criaturas que se examinan por pulso, para que una granja enorme no cueste CPU. */
    private static final int MAX_COUNTED = 64;
    /** Tope de criaturas que dibujan estela por pulso (cada estela son varias partículas). */
    private static final int MAX_STREAKS = 24;

    /** Rojo de la estela de energía hostil: la misma mota que END_ROD, teñida. */
    public static final int EVIL_STREAK_RGB = 0xFF1A1A;

    private static final Map<UUID, Double> BONUS = new ConcurrentHashMap<>();
    /** Quién ya avisó a su party en esta carga (el aviso sale una vez por carga, no por pulso). */
    private static final Set<UUID> ANNOUNCED = ConcurrentHashMap.newKeySet();

    /** Bonus acumulado, en fracción (0.25 = +25 % de daño). 0 si no hay nada recogido. */
    public static double bonus(ServerPlayer sp) {
        return BONUS.getOrDefault(sp.getUUID(), 0.0);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || !(sp.level() instanceof ServerLevel lvl)) return;

        if (!isChargingSpiritBomb(sp)) {
            if (!BONUS.isEmpty()) BONUS.remove(sp.getUUID());
            if (!ANNOUNCED.isEmpty()) ANNOUNCED.remove(sp.getUUID());
            return;
        }
        // Al empezar la carga: pedir energía a la party (una vez por carga).
        if (ANNOUNCED.add(sp.getUUID())) announceToParty(sp);

        if (lvl.getGameTime() % PULSE_TICKS != 0) return;

        Vec3 target = sp.position().add(0, sp.getBbHeight() + 0.6, 0); // sobre la cabeza: la bola
        double gain = 0.0;

        // ── Criaturas: las pacíficas si el jugador no es malvado, las HOSTILAS si lo es ──
        boolean evil = AlignmentTier.of(PlayerStatsAttachment.get(sp).getAlignment()) == AlignmentTier.EVIL;
        double radius = ServerConfig.spiritBombGatherRadius();
        int count = 0;
        int streaks = 0;
        for (LivingEntity le : lvl.getEntitiesOfClass(LivingEntity.class,
                sp.getBoundingBox().inflate(radius), e2 -> counts(e2, evil))) {
            if (le.distanceToSqr(sp) > radius * radius) continue;
            if (++count > MAX_COUNTED) break;
            if (streaks++ < MAX_STREAKS) {
                // Energía oscura y morada para los malvados; blanca y luminosa para el resto.
                streak(lvl, evil ? ModParticles.energyMote(EVIL_STREAK_RGB, 1.0f) : ParticleTypes.END_ROD,
                        le.position().add(0, le.getBbHeight() * 0.5, 0), target, 2, 6);
            }
        }
        if (count > 0) gain += ServerConfig.spiritBombGainPerPulse() * Math.sqrt(count);

        // ── Compañeros de party que cargan ki ──
        int donors = 0;
        PartyManager.Party party = PartyManager.get(lvl.getServer()).partyOf(sp.getUUID());
        if (party != null) {
            double pr = ServerConfig.spiritBombPartyRadius();
            for (UUID id : party.members) {
                if (id.equals(sp.getUUID())) continue;
                ServerPlayer m = lvl.getServer().getPlayerList().getPlayer(id);
                // pr == 0: sin límite de distancia (siempre puede donar), pero misma dimensión.
                if (m == null || !m.isAlive() || m.level() != lvl) continue;
                if (pr > 0.0 && m.distanceToSqr(sp) > pr * pr) continue;
                if (!PlayerStatsAttachment.get(m).isChargingKi()) continue;
                donors++;
                // Estela más densa y larga que la de una criatura: es una persona dando su energía.
                streak(lvl, ParticleTypes.END_ROD, m.position().add(0, m.getBbHeight() * 0.6, 0), target, 4, 40);
                m.displayClientMessage(Component.translatable("message.zenkai.spirit_bomb_send",
                        sp.getGameProfile().getName()), true);
            }
            gain += ServerConfig.spiritBombPartyGainPerPulse() * donors;
        }
        if (gain <= 0.0) return;

        double cap = ServerConfig.spiritBombBonusCap();
        double now = BONUS.getOrDefault(sp.getUUID(), 0.0);
        double next = Math.min(cap, now + gain);
        BONUS.put(sp.getUUID(), next);
        if (next > now) {
            sp.displayClientMessage(Component.translatable("message.zenkai.spirit_bomb_gather",
                    (int) Math.round(next * 100.0)), true);
        }
    }

    /**
     * Mensaje de chat a los compañeros de party (no a quien carga) pidiendo su energía, con la
     * aclaración de cómo enviarla: cargar ki con C. No hace nada si el jugador no tiene party.
     */
    private static void announceToParty(ServerPlayer sp) {
        var server = sp.getServer();
        if (server == null) return;
        PartyManager.Party party = PartyManager.get(server).partyOf(sp.getUUID());
        if (party == null) return;
        Component line = Component.literal("[Party] ").withStyle(ChatFormatting.AQUA)
                .append(Component.translatable("message.zenkai.spirit_bomb_call",
                        sp.getGameProfile().getName()).withStyle(ChatFormatting.WHITE));
        for (UUID id : party.members) {
            if (id.equals(sp.getUUID())) continue;
            ServerPlayer m = server.getPlayerList().getPlayer(id);
            if (m != null) m.sendSystemMessage(line);
        }
    }

    /**
     * Línea de partículas de energía desde {@code from} hacia {@code to}. END_ROD frena rápido
     * (fricción 0.91: una sola con velocidad alta recorre ~3 bloques y se apaga), así que no se
     * dispara UNA hacia el objetivo sino {@code points} repartidas por el camino, cada una con la
     * velocidad justa para deslizarse un tramo hacia él. Con count 0 los tres «offset» del
     * packet son la velocidad.
     */
    public static void streak(ServerLevel lvl, net.minecraft.core.particles.ParticleOptions particle,
                               Vec3 from, Vec3 to, int minPoints, int maxPoints) {
        Vec3 d = to.subtract(from);
        double len = d.length();
        if (len < 0.5) return;
        int points = (int) Math.max(minPoints, Math.min(maxPoints, len / 1.5));
        // Un donante lejano (party sin límite) necesita más puntos para que la línea no quede a trozos.
        Vec3 dir = d.scale(1.0 / len);
        double seg = len / points;
        double speed = seg * 0.12; // distancia de un tramo ≈ v / (1 - 0.91)
        for (int i = 0; i < points; i++) {
            Vec3 p = from.add(dir.scale(seg * i));
            lvl.sendParticles(particle, p.x, p.y, p.z, 0, dir.x, dir.y, dir.z, speed);
        }
    }

    private static boolean isChargingSpiritBomb(ServerPlayer sp) {
        ActionState st = ActionStateServer.get(sp);
        int slot = st.chargingSlot();
        if (slot < 0) return false;
        KiTechnique tech = PlayerStatsAttachment.get(sp).techniques().slot(slot);
        return tech != null && tech.type() == KiTechniqueType.SPIRIT_BOMB;
    }

    /** ¿Dona energía? Con un jugador malvado: SOLO los hostiles (sale de los mobs que le son
     *  afines). Con los demás: solo las criaturas pacíficas, nunca hostiles. Nunca jugadores. */
    private static boolean counts(LivingEntity e, boolean evil) {
        if (!e.isAlive()) return false;
        if (evil) return e instanceof Enemy;
        if (e instanceof Enemy) return false;
        return e instanceof AbstractVillager || e instanceof IronGolem || e instanceof Animal
                || e instanceof WaterAnimal || e instanceof AmbientCreature;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        BONUS.remove(e.getEntity().getUUID());
        ANNOUNCED.remove(e.getEntity().getUUID());
    }
}
