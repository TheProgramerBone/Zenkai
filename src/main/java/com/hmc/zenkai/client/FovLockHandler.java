package com.hmc.zenkai.client;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.core.network.TickHandlers;
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
 *
 * Ahora se descuenta SOLO el modificador zenkai:speed_mult: como es ADD_MULTIPLIED_BASE y
 * el sprint de vanilla es ADD_MULTIPLIED_TOTAL, la velocidad "limpia" sale de dividir por
 * (1 + amount). Se corrige el resultado del evento de forma multiplicativa, así que lo que
 * vanilla añade aparte (volar x1.1, arco, catalejo) sobrevive intacto.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class FovLockHandler {
    private FovLockHandler() {}

    @SubscribeEvent
    public static void onComputeFovModifier(ComputeFovModifierEvent e) {
        Player p = e.getPlayer();

        AttributeInstance attr = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attr == null) return;

        AttributeModifier ours = attr.getModifier(TickHandlers.MOVE_MOD_ID); // ⚠
        if (ours == null || ours.amount() <= 0.0) return; // sin boost del mod: FOV vanilla tal cual

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