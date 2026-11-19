package com.hmc.zenkai.feature.kiweapon;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.aura.AuraColors;
import com.hmc.zenkai.feature.combat.CombatModeServerState;
import com.hmc.zenkai.feature.skills.SkillEffects;
import com.hmc.zenkai.feature.skills.SkillToggles;
import com.hmc.zenkai.registry.ModParticles;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ciclo de vida del arma de ki. NO es un ítem: es un ESTADO derivado, y la distinción entre
 * querida y activa es la clave:
 *  - QUERIDA: el interruptor está puesto y hay modo combate. Es lo que persiste.
 *  - ACTIVA:  además la mano principal está libre, así que el arma existe de verdad.
 * De ahí sale el comportamiento sin código especial: sacas una espada normal y el arma de ki se
 * desvanece (deja de estar activa, pero sigue querida); vuelves a dejar la mano libre y reaparece
 * sola. El interruptor nunca se toca por el camino.
 *
 * Qué cuelga de que el arma esté ACTIVA (cada cosa se recalcula en el tick, nunca en el momento de
 * pulsar, porque la mano puede vaciarse u ocuparse sin avisar):
 *  - daño y coste de ki: {@link #heldWeapon} lo leen CombatZenkaiHooks y KiInfusion;
 *  - guardia y parry: GuardHands la cuenta como arma en la mano principal;
 *  - alcance, velocidad y daño de ataque: modificadores TRANSITORIOS de aquí;
 *  - render: {@link KiWeaponSyncPacket} avisa al resto de clientes y el cliente la dibuja como
 *    una capa del jugador (no hay nada en el inventario que pueda tirarse, guardarse o duplicarse,
 *    que es justo el problema que tenía hacerlo con un ítem).
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class KiWeaponServer {
    private KiWeaponServer() {}

    private static final ResourceLocation REACH_ID =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "ki_weapon_reach");
    private static final ResourceLocation SPEED_ID =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "ki_weapon_speed");
    private static final ResourceLocation DAMAGE_ID =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "ki_weapon_damage");

    /** Última variante aplicada por jugador (modificadores + packet). Ausente = ninguna. */
    private static final Map<UUID, String> APPLIED = new ConcurrentHashMap<>();
    /** Ídem para el escudo de la mano secundaria. */
    private static final Map<UUID, String> APPLIED_OFF = new ConcurrentHashMap<>();

    /** ¿Este jugador quiere esta variante ahora mismo? Interruptor + habilidades + combate. */
    public static boolean wants(Player p, String variant) {
        if (KiWeaponRegistry.isBanned(variant)) return false;   // banned_ki_weapons.json
        if (!SkillToggles.isOn(p, variant)) return false;
        // Fuera del modo combate no hay arma: es un arma, no un accesorio.
        return !(p instanceof ServerPlayer sp) || CombatModeServerState.isActive(sp.getUUID());
    }

    /** La variante de MANO PRINCIPAL que el jugador quiere, o null. Los interruptores son excluyentes. */
    public static String wantedVariant(Player p) {
        for (String w : SkillToggles.KI_MAIN_WEAPONS) {
            if (wants(p, w)) return w;
        }
        return null;
    }

    /** La variante materializada (querida + mano principal libre), o null. Solo servidor. */
    public static String activeVariant(Player p) {
        if (p.isSpectator() || !p.isAlive()) return null;
        // Solo aparece con la mano LIBRE. Con cualquier otra cosa empuñada se queda esperando.
        if (!p.getMainHandItem().isEmpty()) return null;
        return wantedVariant(p);
    }

    /**
     * El escudo de ki, o null. Va en la mano SECUNDARIA: exige que esté vacía. Un escudo vanilla en la mano
     * secundaria lo desplaza, igual que una espada normal desplaza al arma de la principal.
     * Convive con CUALQUIER arma de ki, también las de dos manos (Scythe, Hammer): decisión del usuario
     * 2026-10-15 (antes el escudo se apagaba con ellas).
     * Es la ÚNICA puerta del escudo: con el interruptor apagado (o fuera del modo combate, o baneado) devuelve
     * null, y de aquí cuelgan su protección (GuardHands.hasKiShield / kiShieldReduction vía heldShield) y su
     * dibujo (KiWeaponSyncPacket manda el cambio en el mismo tick), así que apagarlo no deja ningún buff.
     */
    public static String activeOffhand(Player p) {
        if (p.isSpectator() || !p.isAlive()) return null;
        if (!p.getOffhandItem().isEmpty()) return null;
        if (!wants(p, SkillEffects.KI_SHIELD)) return null;
        return SkillEffects.KI_SHIELD;
    }

    /** Los números del escudo de ki activo, o null. */
    public static KiWeaponDef heldShield(Player p) {
        String v = activeOffhand(p);
        return v == null ? null : KiWeaponRegistry.get(v);
    }

    /** Los números del arma que lleva activa, o null. ÚNICO lector: lo usa el pipeline de daño. */
    public static KiWeaponDef heldWeapon(Player p) {
        String v = activeVariant(p);
        return v == null ? null : KiWeaponRegistry.get(v);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;

        String now = activeVariant(sp);
        String nowOff = activeOffhand(sp);
        String before = APPLIED.get(sp.getUUID());
        String beforeOff = APPLIED_OFF.get(sp.getUUID());
        if (java.util.Objects.equals(now, before) && java.util.Objects.equals(nowOff, beforeOff)) return;

        if (now == null) APPLIED.remove(sp.getUUID());
        else APPLIED.put(sp.getUUID(), now);
        if (nowOff == null) APPLIED_OFF.remove(sp.getUUID());
        else APPLIED_OFF.put(sp.getUUID(), nowOff);

        applyModifiers(sp, now);
        // El techo de attack_speed del modo combate se mide contra getValue(): hay que
        // recalcularlo DESPUÉS de tocar el nuestro (ver CombatModeServerState.refreshAttackSpeed).
        CombatModeServerState.refreshAttackSpeed(sp);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(sp,
                new KiWeaponSyncPacket(sp.getId(), now == null ? "" : now, nowOff == null ? "" : nowOff));
    }

    /** Pone o quita alcance/velocidad/daño. Idempotente: quita siempre antes de añadir. */
    private static void applyModifiers(ServerPlayer sp, String variant) {
        KiWeaponDef def = variant == null ? null : KiWeaponRegistry.get(variant);
        apply(sp.getAttribute(Attributes.ENTITY_INTERACTION_RANGE), REACH_ID, def == null ? 0.0 : def.reach());
        apply(sp.getAttribute(Attributes.ATTACK_SPEED), SPEED_ID, def == null ? 0.0 : def.attackSpeed());
        apply(sp.getAttribute(Attributes.ATTACK_DAMAGE), DAMAGE_ID, def == null ? 0.0 : def.attackDamage());
    }

    private static void apply(AttributeInstance attr, ResourceLocation id, double value) {
        if (attr == null) return;
        attr.removeModifier(id);
        if (value == 0.0) return;
        // Transient a propósito: no se persiste, así que un cierre anómalo no deja a nadie con
        // alcance o velocidad de un arma que ya no existe.
        attr.addTransientModifier(new AttributeModifier(id, value, AttributeModifier.Operation.ADD_VALUE));
    }

    /** Un jugador que empieza a seguir a otro tiene que enterarse de su arma. */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking e) {
        if (!(e.getTarget() instanceof ServerPlayer target)) return;
        if (!(e.getEntity() instanceof ServerPlayer tracker)) return;
        String v = APPLIED.get(target.getUUID());
        String off = APPLIED_OFF.get(target.getUUID());
        if (v != null || off != null) {
            PacketDistributor.sendToPlayer(tracker, new KiWeaponSyncPacket(target.getId(),
                    v == null ? "" : v, off == null ? "" : off));
        }
    }

    // Respawn, cambio de dimensión y login reconstruyen la entidad (y su AttributeMap): los
    // modificadores transitorios se pierden pero APPLIED seguiría diciendo "ya aplicado". Se
    // olvida la entrada para que el siguiente tick los reponga y reenvíe el packet.
    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) { forget(e.getEntity()); }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent e) { forget(e.getEntity()); }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) { forget(e.getEntity()); }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) { forget(e.getEntity()); }

    private static void forget(Player p) {
        APPLIED.remove(p.getUUID());
        APPLIED_OFF.remove(p.getUUID());
    }

    /**
     * Golpe cuerpo a cuerpo con el arma de ki: chispas de impacto y empuje extra (la maza, el mazo).
     * Solo en el golpe de vanilla (PLAYER_ATTACK): una técnica física o un proyectil que firme el
     * mismo jugador no debe heredar ni el empuje ni las chispas de un arma que no está usando.
     * Las chispas se resuelven en SERVIDOR con {@link AuraColors#resolve}, igual que el destello
     * de impacto de los golpes físicos y el fogonazo de Ki Infuse, para que cada cliente vea el
     * mismo color. La cantidad sube con el multiplicador de daño del arma, con tope.
     */
    @SubscribeEvent
    public static void onMeleeHit(LivingDamageEvent.Post e) {
        if (!e.getSource().is(DamageTypes.PLAYER_ATTACK)) return;
        if (!(e.getSource().getEntity() instanceof ServerPlayer attacker)) return;
        if (e.getNewDamage() <= 0.0F) return;
        KiWeaponDef w = heldWeapon(attacker);
        if (w == null) return;

        LivingEntity victim = e.getEntity();
        if (attacker.level() instanceof ServerLevel sl) {
            int rgb = AuraColors.resolve(attacker);
            Vec3 at = victim.position().add(0.0, victim.getBbHeight() * 0.6, 0.0);
            float power = (float) Math.min(2.0, Math.max(0.5, w.damageMult()));
            sl.sendParticles(ModParticles.impact(rgb, 0.8F + 0.4F * power),
                    at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
            sl.sendParticles(ModParticles.spark(rgb, 1.0F),
                    at.x, at.y, at.z, 4 + Math.round(4 * power), 0.2, 0.2, 0.2, 0.12);
        }

        if (w.knockback() <= 0.0) return;
        double yaw = Math.toRadians(attacker.getYRot());
        victim.knockback(w.knockback(), Math.sin(yaw), -Math.cos(yaw));
    }
}
