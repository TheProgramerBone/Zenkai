package com.hmc.zenkai.core.network.feature.race;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.core.network.feature.stats.DataAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

import static com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode.isFirstPersonPass;

/**
 * Durante la pasada de PRIMERA PERSONA de PAL (THIRD_PERSON_MODEL + showArmor), inyecta los
 * items de raza virtuales en los slots de armadura VACÍOS y los restaura al terminar. Así la
 * capa de armadura vanilla/GeckoLib renderiza la skin racial posada por la animación PAL real
 * (bloqueo hoy; cualquier animación futura, gratis). La armadura REAL equipada tiene prioridad
 * (solo se rellenan slots vacíos), igual que se ve en el mundo.
 * Mismo patrón de swap que RaceSkinGeoArmorLayer usa en 3ª persona (mutar + restaurar SIEMPRE).
 * ⚠ A verificar en tu PAL/NeoForge:
 *   - FirstPersonMode.isFirstPersonPass() (package com.zigythebird.playeranim...; es API del
 *     linaje playerAnimator). Si no existe con ese nombre, búscalo en la clase FirstPersonMode.
 *   - Que RenderLivingEvent.Pre/Post se disparen también en la pasada de 1ª persona de PAL.
 *     Si el log DEBUG nunca aparece al bloquear en 1ª persona, avísame: hay hook alternativo.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class FirstPersonRaceArmorSwap {

    private FirstPersonRaceArmorSwap() {}

    /** Slots guardados de la pasada en curso (null = no hay swap activo). Orden: feet..head (0..3). */
    private static ItemStack[] saved = null;
    /** Modelo de skin cuyas partes apagamos en la pasada (para restaurar en Post). */
    private static net.minecraft.client.model.PlayerModel<?> hiddenModel = null;
    private static boolean[] savedVisibility = null;

    @SubscribeEvent
    public static void onRenderPre(RenderLivingEvent.Pre<?, ?> e) {
        restoreIfPending(); // seguridad: nunca arrancar una pasada con un swap colgado

        if (!(e.getEntity() instanceof AbstractClientPlayer player)) return;
        if (player != Minecraft.getInstance().player) return;
        if (!isFirstPersonPass()) return;

        var visual = player.getData(DataAttachments.PLAYER_VISUAL.get());
        var stats  = player.getData(DataAttachments.PLAYER_STATS.get());
        if (!visual.shouldRenderRaceSkin() || !stats.isRaceChosen() || player.isInvisible()) return;

        var inv = player.getInventory();
        ItemStack[] race = {
                RaceBodyResolver.resolve(player, EquipmentSlot.FEET),
                RaceBodyResolver.resolve(player, EquipmentSlot.LEGS),
                RaceBodyResolver.resolve(player, EquipmentSlot.CHEST),
                RaceBodyResolver.resolve(player, EquipmentSlot.HEAD)
        };

        saved = new ItemStack[4];
        boolean any = false;
        for (int i = 0; i < 4; i++) {
            saved[i] = inv.getArmor(i);
            // SIEMPRE la pieza racial (aunque haya armadura real): en 1ª persona lo que se ve
            // es el cuerpo del jugador, y con raza el cuerpo ES el geo. La armadura real sigue
            // visible en 3ª persona como siempre.
            if (!race[i].isEmpty()) {
                inv.armor.set(i, race[i]);
                any = true;
            }
        }
        if (!any) saved = null;
    }

    @SubscribeEvent
    public static void onRenderPost(RenderLivingEvent.Post<?, ?> e) {
        if (e.getEntity() instanceof AbstractClientPlayer player
                && player == Minecraft.getInstance().player) {
            restoreIfPending();
        }
    }

    private static void restoreIfPending() {
        if (saved == null) return;
        var mc = Minecraft.getInstance();
        if (mc.player != null) {
            for (int i = 0; i < 4; i++) {
                mc.player.getInventory().armor.set(i, saved[i]);
            }
        }
        saved = null;
    }

    /** ¿Debe ocultarse la SKIN del jugador en la pasada actual? (la consulta el mixin
     *  PlayerModelHideMixin). true = pasada de 1ª persona de PAL con skin racial activa. */
    public static boolean hideSkinNow() {
        if (!isFirstPersonPass()) return false;
        var mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        var visual = mc.player.getData(DataAttachments.PLAYER_VISUAL.get());
        var stats  = mc.player.getData(DataAttachments.PLAYER_STATS.get());
        return visual.shouldRenderRaceSkin() && stats.isRaceChosen();
    }
}