package com.hmc.zenkai.event.tick;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.Race;
import com.hmc.zenkai.feature.ZenkaiAttributes;
import com.hmc.zenkai.feature.forms.FormDef;
import com.hmc.zenkai.feature.forms.FormIds;
import com.hmc.zenkai.feature.forms.FormRegistry;
import com.hmc.zenkai.feature.forms.PotentialUnlock;
import com.hmc.zenkai.feature.mastery.MasteryEffects;
import com.hmc.zenkai.feature.player.PlayerLifeCycle;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.feature.skills.DivineForms;
import com.hmc.zenkai.feature.skills.SkillDef;
import com.hmc.zenkai.feature.skilltree.FormAccess;
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
        if (form.refreshPotentialCeiling(p) && p instanceof net.minecraft.server.level.ServerPlayer lsp) {
            PlayerLifeCycle.syncFormToTrackersAndSelf(lsp);   // el estado Legendario cambió: que lo vean los demás (aura, AuraVariants)
        }
        com.hmc.zenkai.feature.forms.ArcosianContainment.tick(p, att, form);
        // Bono de Voluntad colectiva para la HUD: se re-sincroniza solo cuando cambia el porcentaje mostrado.
        double will = com.hmc.zenkai.feature.forms.HumanWill.bonus(p);
        if (Math.round(will * 100.0) != Math.round(form.getWillBonus() * 100.0)) {
            form.setWillBonus(will);
            PlayerLifeCycle.syncFormIfServer(p);
        }

        // arcosian_power (race_innate: arcosian): el nivel 1 (tanque base de PP) es REGALADO al Arcosiano y
        // sobrevive al respec; los niveles 2-4 solo agrandan el tanque (las formas van todas por el árbol).
        if (att.getRace() == com.hmc.zenkai.feature.Race.ARCOSIAN
                && att.skills().level(ArcosianPowerSystem.SKILL) <= 0) {
            att.skills().grant(ArcosianPowerSystem.SKILL, 1);
            PlayerLifeCycle.syncIfServer(p);
        }

        // Una divinidad con cargo (isDivine) tiene God Ki SIEMPRE (skill tree F5, §5). Aquí y no
        // en el comando que activa isDivine para cubrir cualquier vía futura (Kaioshin, Ángel...).
        if (p instanceof net.minecraft.server.level.ServerPlayer sp
                && com.hmc.zenkai.feature.skilltree.SkillTree.ensureDivineGodKi(sp)) {
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
            if (def == null || !def.allows(att.getRace()) || !FormAccess.usable(p, currentFormId) || !PotentialUnlock.canRemain(p, currentFormId)) {
                // Antes de forzar Base por completo: si lo único que cambió es que dejó de ser la
                // hermana divina que le corresponde (isDivine se volteó mientras la llevaba
                // puesta — ver DivineForms), pasar a la hermana correcta en vez de desvestirlo.
                // pendingSiblingSwap ya comprueba que la hermana SÍ esté desbloqueada, así que
                // si el problema real es otro (p. ej. perdió el nivel de god_ki) devuelve null
                // y se sigue cayendo a Base como siempre.
                ResourceLocation swap = DivineForms.pendingSiblingSwap(p, currentFormId);
                if (swap != null && FormRegistry.get(swap) != null) {
                    form.setFormId(p, swap);
                    currentFormId = swap;
                    def = FormRegistry.get(swap);
                    PlayerLifeCycle.syncFormIfServer(p);
                } else {
                    form.forceBase();
                    // Sin esto, el bloque de abajo seguiría viendo el 'def'/'currentFormId'
                    // VIEJOS (forceBase() no los toca, son variables locales de esta función) y
                    // aplicaría el drenaje de una forma de la que el jugador ya salió.
                    currentFormId = FormIds.BASE;
                    def = null;
                }
            }
            if (currentFormId != null && !FormIds.BASE.equals(currentFormId) && def != null) {
                activeDef = def;
                // El drenaje (FormDef.kiDrainPerSecond) es un % del poder STR+WIL, interpolado
                // por maestría desde el datapack (ki_drain_untrained -> ki_drain_mastered),
                // con un multiplicador corto por SPI y un piso proporcional al pool. Y escalado
                // por el % de poder liberado (hasta 1), como el Kaioken.
                double drain = form.formKiDrainPerTick(att, att.powerFraction());
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
        ResourceLocation holdTarget = form.getHoldTargetForm();
        applyFormScale(p, att.getRace(), activeDef,
                com.hmc.zenkai.feature.skills.SkillToggles.isOn(p, com.hmc.zenkai.feature.skills.SkillEffects.NAMEK_GIANT),
                holdTarget == null ? null : FormRegistry.get(holdTarget), form.formHoldProgress());

        // Banda de forzar (powerPercent > 100%): genérica, drena ki aparte del de la forma.
        // Va AQUÍ y no puede esperar al siguiente tick sin cortar: form.isTransforming() puede
        // devolver true justo debajo y cortar el resto del tick (incluida la carga de ki)
        // durante el hold de Golden/Black, y el drenaje de forzar no debe pausarse esos ~60 ticks.
        OverdriveSystem.tick(c, activeDef);

        if (form.isTransforming()) {
            MovementLocks.transform(p, true);
            MovementLocks.clearSpeedMult(p);
            return true;
        }
        MovementLocks.transform(p, false);
        return false;
    }

    /**
     * Cuánto cambia la escala por tick al crecer / al encoger. Antes el tamaño de la forma se aplicaba de golpe y el Oozaru pasaba de
     * 1 a 3 en un tick, ANTES incluso de que su cuerpo apareciera (la escena larga lo retrasa al estallido): un Saiyan gigante
     * durante 4 s (reportado por el usuario el 2026-11-13). 0,025 lleva de 1 a 3 en 80 ticks, justo la escena hasta el estallido,
     * así que crece mientras se transforma y el mono aparece ya a su tamaño. Encoger va al doble.
     */
    private static final double SCALE_GROW_PER_TICK = 0.025, SCALE_SHRINK_PER_TICK = 0.05;

    /**
     * Fracción de la carga a partir de la cual el cuerpo empieza a encogerse hacia una forma destino MÁS PEQUEÑA. 0,3 es el segundo
     * pulso de la secuencia larga (tick 26 de 80), donde el clip {@code rage_ssj4} empieza a recogerse sobre sí mismo.
     */
    private static final float HOLD_SHRINK_FROM = 0.3f;

    /** Escala de la forma (render + hitbox + ojos) vía minecraft:generic.scale.
     *  Sin forma cae a la escala base de la RAZA. Syncable: los clientes la ven sin packet. Progresiva, ver SCALE_GROW_PER_TICK.
     *  {@code holdTarget}/{@code holdProgress}: forma hacia la que avanza la carga en curso y su avance 0..1 (null/0 sin carga). */
    private static void applyFormScale(Player p, Race race, FormDef def, boolean giant, FormDef holdTarget, float holdProgress) {
        AttributeInstance scaleAttr = p.getAttribute(Attributes.SCALE);
        if (scaleAttr == null) return;
        double base = (race == null) ? 1.0 : race.baseScale();
        double target = (def != null && def.scale() > 0.0) ? def.scale() : base;
        // Encoger DURANTE la carga (pedido del usuario, 2026-11-14, por Súper Oozaru -> SSJ4): antes el tamaño solo bajaba al
        // completarse la carga, así que en el estallido aparecía un SSJ4 de 3,3 bloques que se encogía en 2 s. Ahora el cuerpo
        // actual (el mono) se va haciendo pequeño conforme avanza la carga y llega al tamaño del destino justo cuando cambia la
        // forma. Solo al encoger: crecer sigue ocurriendo después del cambio (el Oozaru, que no tiene carga con tecla, crece
        // durante su escena por SCALE_GROW_PER_TICK). Si la carga se corta, el paso normal lo devuelve a su tamaño.
        boolean holdShrink = false;
        if (holdTarget != null && holdTarget.scale() > 0.0 && holdTarget.scale() < target && holdProgress > HOLD_SHRINK_FROM) {
            double k = (holdProgress - HOLD_SHRINK_FROM) / (1.0 - HOLD_SHRINK_FROM);
            k = k * k * (3.0 - 2.0 * k);   // arranca y acaba suave
            target += (holdTarget.scale() - target) * k;
            holdShrink = true;
        }
        // Gigante Namekiano (interruptor): tamaño de Oozaru, encima de la forma que lleve (nunca menor que ella).
        if (giant && race == Race.NAMEKIAN) target = Math.max(target, com.hmc.zenkai.feature.skills.SkillEffects.NAMEK_GIANT_SCALE);
        double goal = target - 1.0;
        AttributeModifier current = scaleAttr.getModifier(FORM_SCALE_MOD_ID);
        double now = current == null ? 0.0 : current.amount();
        double amount = goal;
        // Al entrar al mundo (modificador transitorio, aún sin aplicar) va directo: nadie debe verte crecer al conectarte.
        // Encogiendo con la carga también va directo: la curva de arriba ya es progresiva.
        if (p.tickCount > 20 && !holdShrink) {
            double step = goal > now ? SCALE_GROW_PER_TICK : SCALE_SHRINK_PER_TICK;
            amount = Math.abs(goal - now) <= step ? goal : now + Math.signum(goal - now) * step;
        }
        if (amount == 0.0) {
            if (current != null) scaleAttr.removeModifier(FORM_SCALE_MOD_ID);
            return;
        }
        if (current == null || current.amount() != amount) {
            scaleAttr.removeModifier(FORM_SCALE_MOD_ID);
            scaleAttr.addTransientModifier(new AttributeModifier(
                    FORM_SCALE_MOD_ID, amount, AttributeModifier.Operation.ADD_VALUE));
        }
    }
}