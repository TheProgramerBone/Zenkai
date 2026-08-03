package com.hmc.zenkai.feature.combat;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.content.entity.technique.KiProjectileEntity;
import com.hmc.zenkai.feature.aura.AuraColors;
import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.skills.SkillEffects;
import com.hmc.zenkai.feature.skills.SkillToggles;
import com.hmc.zenkai.registry.ModParticles;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ki Infuse en PROYECTILES. Se cobra AL LANZAR y no al impactar: cobrando al impactar, la
 * jugada óptima sería llenar el aire de flechas a ver cuál acierta y pagar solo por las que
 * entran. Pagando al salir, cada disparo cuesta aciertes o no.
 * Sin ki suficiente el disparo NO SALE (se cancela el spawn). Es distinto del melee, que cae
 * en silencio a golpe normal, y es a propósito: una flecha que sale sin infusionar es
 * indistinguible de una infusionada hasta que impacta, así que el jugador no sabría si acaba
 * de gastar ki o no. Cancelar es ambiguo cero.
 * Un solo hook (EntityJoinLevelEvent) cubre lo que vuela — arco, ballesta, tridente,
 * proyectiles de otros mods — sin un evento por arma. Y el multishot sale gratis: la ballesta
 * genera tres entidades, así que se cobra tres veces sin código especial.
 * UN SOLO TIEMPO. Hubo una fase con intercambio de entidad (la flecha se cambiaba por una
 * KiArrowEntity) y fue un error: en 1.21 Poder, Llama, Impacto y Perforación se resuelven al
 * IMPACTAR, leyendo el ItemStack del arco guardado en el proyectil, y esa arma no sobrevivía a
 * la copia por NBT. Resultado: Poder V no hacía absolutamente nada en flechas infusionadas,
 * mientras que tridente y flecha espectral —que iban por la vía 'scaled', sin intercambio—
 * funcionaban bien desde el primer día. Lo único que el intercambio aportaba de verdad era
 * impedir que la flecha se recogiese, y eso es un setter sobre la original.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class KiInfusionShooting {
    private KiInfusionShooting() {}

    /** Último tick en que cada jugador dibujó fogonazo. La ballesta con Tiro Múltiple genera
     *  TRES proyectiles en el mismo tick y sin esto sonaba y destellaba tres veces en el mismo
     *  punto: un disparo, un fogonazo. */
    private static final Map<UUID, Long> LAST_FX = new ConcurrentHashMap<>();


    @SubscribeEvent
    public static void onProjectileSpawn(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide()) return;
        if (!(e.getEntity() instanceof Projectile proj)) return;

        // Las técnicas de ki ya tienen su propia vía de daño y de coste.
        if (proj instanceof KiProjectileEntity) return;
        // El sustituto vuelve a pasar por este evento: si ya trae datos, está hecho.
        if (KiInfusedShot.of(proj).isInfused()) return;

        if (!(proj.getOwner() instanceof ServerPlayer sp)) return;
        // MISMA COMPUERTA QUE EL MELEE: fuera del modo combate, daño vanilla puro. Sin este
        // guard el arco infusionaba siempre, incluso cazando cerdos con el modo apagado.
        if (!CombatModeServerState.isActive(sp.getUUID())) return;
        if (!SkillToggles.isOn(sp, SkillEffects.KI_INFUSE)) return;

        KiProjectileRules.Mode mode = KiProjectileRules.modeFor(proj.getType());
        if (mode == KiProjectileRules.Mode.NONE) return;

        PlayerStatsAttachment att = PlayerStatsAttachment.get(sp);
        double refPower = att.computeKiPowerFinal();
        double bonus = refPower * SkillEffects.kiInfuseFactor(sp);
        if (bonus <= 0.0) return;

        int cost = KiInfusion.kiCost(att, bonus);
        if (att.getEnergy() < cost) {
            e.setCanceled(true);
            sp.displayClientMessage(Component.translatable("message.zenkai.no_ki_shot"), true);
            return;
        }

        att.consumeEnergy(cost);
        PlayerLifeCycle.sync(sp);

        // El daño va sobre la entidad que ya está entrando al mundo. Sin intercambio: los
        // encantamientos se quedan donde vanilla los puso.
        proj.setData(ZenkaiDataAttachments.KI_SHOT.get(), new KiInfusedShot(bonus, refPower));

        // La flecha infusionada no vuelve al inventario: si se pudiera recoger, saldrían
        // disparos infusionados gratis reciclando los tuyos. Va DESPUÉS de que vanilla haya
        // fijado su propio pickup (BowItem lo pone antes de addFreshEntity, y este evento
        // se dispara dentro).
        // ⚠ API a verificar al compilar: campo público 'pickup' de AbstractArrow en 1.21.1.
        if (proj instanceof AbstractArrow arrow) {
            arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        }

        muzzleFx(sp, proj);
    }

    /** Destello de salida, en unidades de escala de partícula. El ancho real dibujado es
     *  ~2.2x este número en bloques (KiImpactParticle usa quadSize = 1.1 * scale, y el quad va
     *  a ±quadSize), así que 0.35 son ~0.77 bloques: del tamaño de la flecha, no de un
     *  edificio. Toqué SOLO estas dos: el 1.1f de KiImpactParticle lo comparten los impactos
     *  de técnicas físicas y bajarlo ahí encogería también los puñetazos. */
    private static final float MUZZLE_FLASH_MIN = 0.15f;   // toque rápido -> ~0.33 bloques
    private static final float MUZZLE_FLASH_MAX = 0.35f;   // plena carga  -> ~0.77 bloques

    /** Cuánto se adelanta el fogonazo sobre la trayectoria. Es la palanca contra el "me tapa
     *  la pantalla" en primera persona: subirlo lo aleja del near plane sin encogerlo. */
    private static final double MUZZLE_OFFSET = 0.45;

    /**
     * FOGONAZO DE SALIDA. Mismo criterio y mismas fábricas que PhysicalCombatServer.impactFx:
     * destello + chispas tintados con AuraColors.resolve y resueltos en SERVIDOR, para que
     * todos los clientes vean el mismo color y no cada uno el suyo.
     * POR QUÉ EXISTE: Ki Infuse cobra al lanzar y en silencio. Sin señal de salida, el jugador
     * no sabe si acaba de gastar ki hasta que mira la barra — el mismo problema de ambigüedad
     * por el que el disparo sin ki se CANCELA en vez de salir a secas.
     * LA INTENSIDAD SALE DE LA CARGA DEL ARCO y no del daño final, porque en este evento los
     * encantamientos vanilla todavía no se han aplicado (applyOnProjectileSpawned corre
     * después). No es un apaño: la carga es justo lo que hay que comunicar, porque un disparo
     * a media carga paga el ki completo y rinde x0.5 en projectileMultiplier. Ahora se ve.
     * Los proyectiles que no son flecha (tridente, bola de nieve, huevo) no tienen carga
     * variable, así que van siempre al máximo en vez de salir apagados por llevar menos
     * velocidad que una flecha a plena tensión.
     */
    private static void muzzleFx(ServerPlayer sp, Projectile proj) {
        long now = sp.level().getGameTime();
        Long last = LAST_FX.put(sp.getUUID(), now);
        if (last != null && last == now) return;

        double drawF = 1.0;
        if (proj instanceof AbstractArrow) {
            // 3.0 = velocidad de una flecha a plena tensión (misma referencia que
            // combat.projectile_base_damage usa para el daño).
            drawF = Math.max(0.0, Math.min(1.0, proj.getDeltaMovement().length() / 3.0));
        }

        // Adelantado un poco sobre la trayectoria: en el punto de spawn exacto, en primera
        // persona el destello te tapa la pantalla.
        Vec3 dir = proj.getDeltaMovement();
        Vec3 at = dir.lengthSqr() < 1.0e-6
                ? proj.position()
                : proj.position().add(dir.normalize().scale(MUZZLE_OFFSET));

        int rgb = AuraColors.resolve(sp);
        float flash = MUZZLE_FLASH_MIN + (MUZZLE_FLASH_MAX - MUZZLE_FLASH_MIN) * (float) drawF;
        int sparks  = 3 + (int) Math.round(6.0 * drawF);

        var lvl = sp.serverLevel();
        lvl.sendParticles(ModParticles.impact(rgb, flash),
                at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
        // Chorro, no nube: offsets y velocidad a la mitad de lo que usa un impacto de melee,
        // porque aquí la energía sale en una dirección y no revienta contra un cuerpo.
        lvl.sendParticles(ModParticles.spark(rgb, 1.0f),
                at.x, at.y, at.z, sparks, 0.05, 0.05, 0.05, 0.12);

        // ⚠ PROVISIONAL. ModSounds ya registra ki_attack_release_1..4, pero no existen ni los
        // .ogg ni sus entradas en sounds.json, así que suenan a nada. Cuando los tengas,
        // cambia esta línea por uno de ellos y quita el sonido vanilla.
        lvl.playSound(null, at.x, at.y, at.z, SoundEvents.FIRECHARGE_USE,
                SoundSource.PLAYERS, 0.45f, (float) (1.4 + 0.3 * drawF));
    }
}