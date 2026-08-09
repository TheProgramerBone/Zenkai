package com.hmc.zenkai.event.tick;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.forms.FormDef;
import com.hmc.zenkai.feature.forms.FormIds;
import com.hmc.zenkai.feature.forms.FormRegistry;
import com.hmc.zenkai.feature.forms.PotentialUnlock;
import com.hmc.zenkai.feature.mastery.MasteryEffects;
import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.skills.SuperForms;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/** Transformaciones: tick de forma, drenaje de ki, escala y lock de transformación. */
public final class FormSystem {
    private FormSystem() {}

    private static final ResourceLocation FORM_SCALE_MOD_ID =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "form_scale");

    /** @return true si está transformando (corta el tick). */
    public static boolean tick(TickCtx c) {
        Player p = c.p();
        PlayerStatsAttachment att = c.att();
        var form = c.form();
        // Antes del multiplicador: si el techo llega tarde, el tick entero usa el de ayer.
        form.refreshPotentialCeiling(p);

        // Nivel 1 de super_forms: REGALADO. Es el estado base, así que el jugador SIEMPRE tiene
        // la habilidad y solo compra del 2 en adelante. grant() sube el suelo otorgado -> el
        // respec no se lo quita. Aquí ya se filtró "sin raza" (RaceGateSystem) y solo corre en
        // servidor, así que este único sitio cubre elegir raza, partidas viejas y reset completo.
        // maxLevel > 1 -> a las razas sin transformaciones ni les aparece la habilidad.
        if (SuperForms.maxLevel(p) > 1 && att.skills().level(SuperForms.SKILL) <= 0) {
            att.skills().grant(SuperForms.SKILL, 1);
            PlayerLifeCycle.syncIfServer(p);
        }
        // ÚNICO punto donde se escribe el multiplicador de stats (forma + kaioken + majin +
        // zenkai). Va cada tick porque la maestría sube de forma continua, el kaioken puede
        // apagarse solo y el zenkai caduca por tiempo. Con esto el PL y la pantalla de stats
        // se actualizan sin tocar nada más.
        //
        // El zenkai se multiplica AQUÍ y no dentro de formStatFactor a propósito: esa función
        // es sobre maestría, y el zenkai no depende de ninguna — es un estado temporal de la
        // raza. Meterlo allí obligaría a MasteryEffects a conocer RacePassiveSystem.
        att.setStatMultiplier(MasteryEffects.formStatFactor(p)
                * RacePassiveSystem.zenkaiMultiplier(p));

        if (form.serverTick(p, att, c.visual())) {
            PlayerLifeCycle.syncFormIfServer(p);
        }

        // Drena ki si la forma actual tiene coste por tick; sin ki, vuelve a BASE.
        ResourceLocation currentFormId = form.getFormId();
        FormDef activeDef = null;
        if (currentFormId != null && !FormIds.BASE.equals(currentFormId)) {
            FormDef def = FormRegistry.get(currentFormId);

            // Forma inválida para esta raza, o desaparecida del datapack tras un /reload:
            // se vuelve a base. Antes solo se saltaba el drenaje, así que el jugador se
            // quedaba transformado GRATIS.
            if (def == null || !def.allows(att.getRace()) || !SuperForms.unlocked(p, currentFormId) || !PotentialUnlock.canRemain(p, currentFormId)) {
                    form.forceBase();
            } else {
                activeDef = def;
                // El drenaje ya viene interpolado por maestría desde el datapack
                // (ki_drain_untrained -> ki_drain_mastered): no lleva factor extra.
                double drain = form.formKiDrainPerTick();
                if (drain > 0.0) {
                    int before = att.getKiCurrent();
                    att.addKi(-drain);
                    if (before > 0 && att.getKiCurrent() <= 0) {
                        form.forceBase();
                        PlayerLifeCycle.syncFormIfServer(p);
                    }
                }
            }
        }
        applyFormScale(p, att.getRace(), activeDef);

        if (form.isTransforming()) {
            MovementLocks.transform(p, true);
            MovementLocks.clearSpeedMult(p);
            return true;
        }
        MovementLocks.transform(p, false);
        return false;
    }

    /** Escala de la forma (render + hitbox + ojos) vía minecraft:generic.scale.
     *  Sin forma cae a la escala base de la RAZA. Syncable: los clientes la ven sin packet. */
    private static void applyFormScale(Player p, Race race, FormDef def) {
        AttributeInstance scaleAttr = p.getAttribute(Attributes.SCALE); // ⚠
        if (scaleAttr == null) return;
        double base = (race == null) ? 1.0 : race.baseScale();
        double target = (def != null && def.scale() > 0.0) ? def.scale() : base;
        double amount = target - 1.0;
        AttributeModifier current = scaleAttr.getModifier(FORM_SCALE_MOD_ID); // ⚠
        if (amount == 0.0) {
            if (current != null) scaleAttr.removeModifier(FORM_SCALE_MOD_ID);
            return;
        }
        if (current == null || current.amount() != amount) {
            scaleAttr.removeModifier(FORM_SCALE_MOD_ID);
            scaleAttr.addTransientModifier(new AttributeModifier(
                    FORM_SCALE_MOD_ID, amount, AttributeModifier.Operation.ADD_VALUE)); // ⚠
        }
    }
}