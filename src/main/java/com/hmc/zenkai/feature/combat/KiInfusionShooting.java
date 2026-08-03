package com.hmc.zenkai.feature.combat;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.content.entity.technique.KiProjectileEntity;
import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.skills.SkillEffects;
import com.hmc.zenkai.feature.skills.SkillToggles;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

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
    }

}