package com.hmc.zenkai.feature.combat.entity;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.combat.ZenkaiStats;
import com.hmc.zenkai.registry.ModGameRules;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * La vida vanilla de una entidad es una PROYECCIÓN del pool body. Dos vías, y cada una cubre
 * lo que la otra no puede:
 *  - LivingHealEvent: cualquier curación que pase por heal() (el +1/s del Wither, Regeneración,
 *    dar de comer a un lobo). Es EXACTA y se cancela para no contarla dos veces.
 *  - reconcile() en el tick: red para quien escribe setHealth() a pelo, que no dispara ningún
 *    evento (los cristales del End, comandos, otros mods).
 *
 * El reconcile es DELIBERADAMENTE conservador: umbral relativo y sin forzar deltas mínimos.
 * Antes hacía justo lo contrario y regalaba body por ruido de coma flotante, que contra un
 * jefe con pool de miles y golpes de pocos puntos lo dejaba inmortal.
 *
 * Con la gamerule zenkai_enableRaceBoosts en false NADA de esto corre: el daño va a la vida
 * vanilla (CombatZenkaiHooks sale antes) y el pool se queda congelado. Si el espejo siguiera
 * activo, onDamagePost devolvía la vida al pool lleno después de cada golpe y los mobs con stats
 * quedaban inmortales — el "la gamerule apaga el mod" que se reportó el 2026-10-08.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class EntityHealthSync {
    private EntityHealthSync() {}

    @SubscribeEvent
    public static void onHeal(LivingHealEvent e) {
        // Los jugadores los lleva CombatZenkaiHooks.onHeal, que cancela: su curación entra por
        // el pool. Aquí es al revés — la de las entidades es legítima y hay que traducirla.
        if (e.getEntity() instanceof Player) return;
        if (!zenkaiLayerOn(e.getEntity())) return;

        EntityStats st = ZenkaiStats.entityStats(e.getEntity());
        if (st == null || st.getBodyMax() <= 0) return;

        float maxHp = e.getEntity().getMaxHealth();
        if (maxHp <= 0f) return;

        int gain = (int) Math.max(1, Math.round(e.getAmount() * (st.getBodyMax() / maxHp)));
        st.addBody(gain);
        st.mirrorToVanilla(e.getEntity());
        e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onTick(EntityTickEvent.Post e) {
        if (!(e.getEntity() instanceof LivingEntity le) || le instanceof Player) return;
        if (le.level().isClientSide()) return;
        if (!zenkaiLayerOn(le)) return;

        EntityStats st = ZenkaiStats.entityStats(le);
        if (st == null) return;

        st.reconcile(le);
    }

    /**
     * Cierre del golpe. Vanilla acaba de restar el daño equivalente que le pasó
     * CombatZenkaiHooks; aquí se vuelve a proyectar el pool sobre la vida para que las dos
     * cifras queden EXACTAS (el redondeo de vanilla, la absorción o cualquier otro listener
     * pueden haber aplicado un pelo más o menos) y para resembrar el valor de referencia del
     * reconcile, que si no leería esa diferencia como una escritura ajena y la volvería a
     * cobrar del body.
     * NO se toca a un muerto: con body 0 la proyección lo devolvería a 0.01 de vida y lo
     * resucitaría en el mismo tick en el que acaba de morir.
     */
    @SubscribeEvent
    public static void onDamagePost(LivingDamageEvent.Post e) {
        LivingEntity le = e.getEntity();
        if (le instanceof Player) return;
        if (le.level().isClientSide()) return;
        if (le.isDeadOrDying()) return;
        if (!zenkaiLayerOn(le)) return;

        EntityStats st = ZenkaiStats.entityStats(le);
        if (st == null || st.getBody() <= 0) return;

        st.mirrorToVanilla(le);
    }

    /** Capa Zenkai activa en el servidor de esta entidad (gamerule zenkai_enableRaceBoosts). */
    private static boolean zenkaiLayerOn(LivingEntity le) {
        return le.getServer() != null && ModGameRules.enableRaceBoosts(le.getServer());
    }
}
