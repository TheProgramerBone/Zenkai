package com.hmc.zenkai.client;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.event.tick.MovementLocks;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;

/**
 * El FOV ignora la velocidad QUE APORTA EL MOD, pero conserva la de vanilla.
 * Vanilla deforma el FOV con MOVEMENT_SPEED; con los multiplicadores de DEX/forma
 * (SSJ4 corre a varios x) eso da un ojo de pez constante y mareante. Antes lo clavábamos
 * a 1.0, lo que también mataba el efecto de sprint, volar y tensar el arco.
 * Ahora se descuenta SOLO el modificador zenkai:speed_mult: como es ADD_MULTIPLIED_BASE y
 * el sprint de vanilla es ADD_MULTIPLIED_TOTAL, la velocidad "limpia" sale de dividir por
 * (1 + amount). Se corrige el resultado del evento de forma multiplicativa, así que lo que
 * vanilla añade aparte (volar x1.1, arco, catalejo) sobrevive intacto.
 *
 * Corrección 2026-09-10 (pedido explícito del usuario, "que la gravedad no haga zoom"): antes
 * se ignoraban solo los boosts (amount > 0); una PENALIZACIÓN del mod (pesas o gravedad
 * ambiental, GroundMovementSystem/FlightSystem multiplican por WeightSystem.moveFactor DENTRO
 * del mismo modificador zenkai:speed_mult) daba amount < 0 y el guardia lo dejaba pasar tal
 * cual, así que sobrecargarse de gravedad SÍ estrechaba el FOV (efecto "zoom" no deseado) —
 * el mismo mecanismo que ya arregla los boosts, solo que no se aplicaba en la otra dirección.
 * La misma división es simétrica para amount negativo (sale de la identidad
 * withMod = base · (1+amount) despejando base), así que basta con quitar el `<= 0.0`
 * y comprobar solo que HAYA modificador.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class FovLockHandler {
    private FovLockHandler() {}

    @SubscribeEvent
    public static void onComputeFovModifier(ComputeFovModifierEvent e) {
        Player p = e.getPlayer();

        AttributeInstance attr = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr == null) return;

        AttributeModifier ours = attr.getModifier(MovementLocks.MOVE_MOD_ID); // ⚠
        // Antes: "ours.amount() <= 0.0" — solo corregía boosts. Ahora corrige CUALQUIER
        // modificador del mod, boost o penalización (pesas/gravedad incluidas).
        if (ours == null || ours.amount() == 0.0) return; // sin modificador del mod: FOV vanilla tal cual

        float walk = p.getAbilities().getWalkingSpeed();
        if (walk <= 0.0f) return;

        double withMod = attr.getValue();
        double without = withMod / (1.0 + ours.amount());

        // Misma expresión que AbstractClientPlayer.getFieldOfViewModifier usa para la velocidad.
        float actual = (float) ((withMod / walk + 1.0) / 2.0);
        float wanted = (float) ((without / walk + 1.0) / 2.0);
        if (actual <= 0.001f || !Float.isFinite(actual) || !Float.isFinite(wanted)) return;

        e.setNewFovModifier(e.getNewFovModifier() * (wanted / actual)); // ⚠
    }
}