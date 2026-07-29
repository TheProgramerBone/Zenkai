package com.hmc.zenkai.client.render_and_model_entities.item;

import com.hmc.zenkai.content.item.KiWeaponItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.util.Color;

/**
 * Renderer de las armas de ki. Su único trabajo extra es TEÑIR.
 *
 * El color sale de un componente del propio ItemStack (DYED_COLOR), no de mirar quién la
 * empuña. Motivo: un GeoItemRenderer no sabe de quién es el item que está dibujando — se le
 * llama igual para la mano en primera persona, la de otro jugador a treinta bloques, un NPC o
 * el icono del inventario. Poniendo el color EN el stack, el servidor decide una vez y todos
 * los clientes dibujan lo mismo, incluidos los NPC que la usen algún día.
 *
 * El tinte MULTIPLICA sobre la textura, así que la textura vive en grises: el blanco se
 * convierte en el color de ki y el negro se queda negro. Eso también significa que la textura
 * animada y el tinte no se estorban — la animación decide qué frame se muestrea y el tinte
 * pinta el resultado, son dos etapas distintas.
 */
public class KiWeaponRenderer extends GeoItemRenderer<KiWeaponItem> {

    public KiWeaponRenderer() {
        super(new KiWeaponModel());
    }

    @Override
    public Color getRenderColor(KiWeaponItem animatable, float partialTick, int packedLight) {
        ItemStack stack = getCurrentItemStack();
        if (stack == null) return Color.WHITE;

        DyedItemColor dyed = stack.get(DataComponents.DYED_COLOR);
        if (dyed == null) return Color.WHITE;   // sin color asignado todavía: gris tal cual

        // ⚠ VERIFICAR en GeckoLib 4.8.4: Color.ofRGB(int). Si no existe, la alternativa es
        // Color.ofOpaque(rgb) o construirlo por canales.
        return Color.ofOpaque(dyed.rgb());
    }
}