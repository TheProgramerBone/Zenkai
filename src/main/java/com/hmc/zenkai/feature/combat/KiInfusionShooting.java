package com.hmc.zenkai.feature.combat;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.content.entity.technique.KiProjectileEntity;
import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.skills.SkillEffects;
import com.hmc.zenkai.feature.skills.SkillToggles;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
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
 * DOS TIEMPOS, Y ESTO ES LO IMPORTANTE:
 *  1. El attachment se pega a la flecha ORIGINAL en el acto. Ahí va el daño, así que la
 *     infusión funciona aunque el intercambio de entidad falle o no llegue a ocurrir.
 *  2. El intercambio por KiArrowEntity se ENCOLA. En 1.21 el orden de vanilla es
 *     addFreshEntity() y DESPUÉS applyOnProjectileSpawned(), que es quien aplica Poder,
 *     Impacto, Llama, Perforación y guarda el arma de origen. Intercambiando dentro del
 *     evento copiábamos una flecha a la que todavía no le habían puesto nada, y los
 *     encantamientos acababan en la entidad que estábamos descartando. server.execute deja
 *     el intercambio para justo después de esa pila de llamadas: sin perder un tick de vuelo
 *     y sin añadir entidades en mitad del spawn de otra.
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

        // Paso 1: el daño va SIEMPRE sobre la entidad que ya está entrando al mundo.
        proj.setData(ZenkaiDataAttachments.KI_SHOT.get(), new KiInfusedShot(bonus, refPower));

        // Paso 2: el intercambio (comportamiento, no daño) espera a que vanilla termine.
        if (mode == KiProjectileRules.Mode.REPLACE) {
            MinecraftServer server = sp.getServer();
            if (server != null) server.execute(() -> swap(proj, e.getLevel()));
        }
    }

    /**
     * Cambia el proyectil por su versión de ki, ya con los encantamientos aplicados por
     * vanilla. Si algo no cuadra se deja la original: lleva el attachment, así que el
     * jugador conserva lo que pagó — solo pierde el "no se recoge / se desvanece".
     */
    private static void swap(Projectile original, Level level) {
        if (original.isRemoved()) return;   // impactó o se descartó antes de llegar aquí

        Projectile replacement = KiProjectileRules.buildReplacement(original);
        if (replacement == null) return;

        // Los datos ANTES de añadirlo: al entrar al nivel vuelve a disparar el evento de
        // arriba y es el isInfused() quien corta la recursión.
        replacement.setData(ZenkaiDataAttachments.KI_SHOT.get(), KiInfusedShot.of(original));
        original.discard();
        level.addFreshEntity(replacement);
    }
}