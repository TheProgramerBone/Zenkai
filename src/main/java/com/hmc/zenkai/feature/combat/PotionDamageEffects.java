package com.hmc.zenkai.feature.combat;

import com.hmc.zenkai.Zenkai;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Debilidad y Fuerza en el daño de Zenkai (2026-11-25, a raíz de «con debilidad el jugador hace daño nulo»).
 *
 * <p><b>El problema.</b> Zenkai calcula el golpe desde STR, pero vanilla decide ANTES si el golpe existe: la Debilidad resta 4 al
 * atributo {@code attack_damage}, un puño (1) o un arma floja se queda en 0 y {@code Player.attack} (y {@code Mob.doHurtTarget})
 * no llega a generar el evento de daño. Además el pipeline no leía la Debilidad ni la Fuerza: solo movían el bonus de arma.</p>
 *
 * <p><b>La solución.</b> Se cancela el recorte vanilla de la poción (un modificador transitorio {@link #COMP_ID} de signo contrario,
 * solo en entidades con stats de Zenkai) y el efecto se aplica de forma explícita sobre el golpe cuerpo a cuerpo con
 * {@link #meleeFactor}: Debilidad −25 % por nivel y Fuerza +25 % por nivel (mínimo ×0,1). Vale para jugadores y mobs. El modificador
 * se reconcilia en cada tick de la entidad (no con eventos de efecto): {@code removeAllEffects} (la leche) no avisa a nadie.</p>
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID)
public final class PotionDamageEffects {
    private PotionDamageEffects() {}

    /** Cuánto resta vanilla al ataque por nivel de Debilidad y cuánto suma por nivel de Fuerza (valores de vanilla 1.21.1). */
    private static final double VANILLA_WEAKNESS_PER_LEVEL = 4.0;
    private static final double VANILLA_STRENGTH_PER_LEVEL = 3.0;

    /** Efecto de cada nivel sobre el golpe de Zenkai. */
    public static final double FACTOR_PER_LEVEL = 0.25;
    /** Suelo del multiplicador: ni con Debilidad muy alta el golpe desaparece. */
    public static final double MIN_FACTOR = 0.1;

    private static final ResourceLocation COMP_ID = ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "potion_attack_comp");

    /** Entidades que han llevado el modificador (para quitarlo sin buscarlo en cada tick de todas). Es transitorio: no se guarda
     *  en el NBT, así que al recargar una entidad desaparece solo; no se limpia al salir del nivel porque un cambio de
     *  dimensión conserva el modificador y se perdería la pista de quitarlo. Crece como mucho con las entidades afectadas. */
    private static final Set<UUID> COMPENSATED = ConcurrentHashMap.newKeySet();

    /** Niveles de un efecto (amplificador + 1), 0 si no lo lleva. */
    private static int levels(LivingEntity e, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect) {
        MobEffectInstance inst = e.getEffect(effect);
        return inst == null ? 0 : inst.getAmplifier() + 1;
    }

    /** Multiplicador del golpe cuerpo a cuerpo por Debilidad y Fuerza de ESTA entidad (1,0 sin efectos). */
    public static double meleeFactor(LivingEntity e) {
        if (e == null) return 1.0;
        int strength = levels(e, MobEffects.DAMAGE_BOOST);
        int weakness = levels(e, MobEffects.WEAKNESS);
        if (strength == 0 && weakness == 0) return 1.0;
        return Math.max(MIN_FACTOR, 1.0 + FACTOR_PER_LEVEL * (strength - weakness));
    }

    @SubscribeEvent
    public static void onTick(EntityTickEvent.Pre e) {
        if (!(e.getEntity() instanceof LivingEntity living) || living.level().isClientSide()) return;
        int weakness = levels(living, MobEffects.WEAKNESS);
        int strength = levels(living, MobEffects.DAMAGE_BOOST);
        if (weakness == 0 && strength == 0) {
            if (COMPENSATED.remove(living.getUUID())) {
                AttributeInstance attr = living.getAttribute(Attributes.ATTACK_DAMAGE);
                if (attr != null) attr.removeModifier(COMP_ID);
            }
            return;
        }
        // Solo entidades que pasan por el pipeline de Zenkai: el resto conserva el comportamiento vanilla de la poción.
        var stats = ZenkaiStats.of(living);
        if (stats == null || !stats.isCombatActive()) return;
        AttributeInstance attr = living.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attr == null) return;
        double comp = VANILLA_WEAKNESS_PER_LEVEL * weakness - VANILLA_STRENGTH_PER_LEVEL * strength;
        AttributeModifier current = attr.getModifier(COMP_ID);
        if (current == null || current.amount() != comp) {
            attr.addOrUpdateTransientModifier(new AttributeModifier(COMP_ID, comp, AttributeModifier.Operation.ADD_VALUE));
        }
        COMPENSATED.add(living.getUUID());
    }
}
