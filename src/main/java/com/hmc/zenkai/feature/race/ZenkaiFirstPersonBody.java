package com.hmc.zenkai.feature.race;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

import static com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode.isFirstPersonPass;

/**
 * EMBUDO ÚNICO de la pasada corporal de 1ª persona de PAL (THIRD_PERSON_MODEL + showArmor).
 * Contrato congelado:
 *   CABEZA  → HEAD racial ✗ · casco real ✗ · pelo ✗ · halo ✗ · scouter físico ✗
 *   CUERPO  → torso ✓ · brazos ✓ · piernas ✓ · botas ✓
 * POR QUÉ RenderLivingEvent Y NO RenderPlayerEvent
 * ------------------------------------------------
 *   PlayerRenderer.render()
 *     ├─ RenderPlayerEvent.Pre       ← ANTES de setModelProperties → sitio del BYTE
 *     ├─ setModelProperties()        ← setAllVisible(true) + recalcula cada parte del byte
 *     ├─ super.render()
 *     │    └─ RenderLivingEvent.Pre  ← DESPUÉS → sitio de la VISIBILIDAD
 * Dos consecuencias que hay que respetar:
 *   1) El cero de DATA_PLAYER_MODE_CUSTOMISATION (chaqueta/sleeves/pants/hat) SOLO surte
 *      efecto en RenderPlayerEvent.Pre. Eso vive en RaceSkinHideBasePlayerHooks y no se toca.
 *   2) En la pasada FP de PAL solo llega RenderLivingEvent, así que ahí el byte nunca se
 *      pone a cero y el jacket sobrevive. Por eso la supresión del modelo vanilla en FP se
 *      hace AQUÍ por visibilidad: corre después de setModelProperties, nada puede pisarla,
 *      y no depende de que PlayerModelHideMixin llegue a cancelar el renderToBuffer.
 * Cualquier excepción futura (p. ej. una representación especial de transformación) debe ser
 * una capa explícita nueva, NO una condición escondida aquí.
 * ⚠ A verificar al compilar:
 *   · FirstPersonMode.isFirstPersonPass()
 *   · e.getRenderer().getModel() devuelve el PlayerModel (M del LivingEntityRenderer).
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class ZenkaiFirstPersonBody {

    private ZenkaiFirstPersonBody() {}

    /** Slots guardados de la pasada en curso (null = no hay swap activo). Orden: feet..head (0..3). */
    private static ItemStack[] saved = null;
    /** Modelo tocado en la pasada (para restaurar en Post). */
    private static PlayerModel<?> hiddenModel = null;
    /** true = se apagó el modelo entero (skin racial); false = solo cabeza (skin vanilla). */
    private static boolean hiddenAll = false;
    private static boolean savedHeadVisible = true;
    private static boolean savedHatVisible  = true;

    // ── Consultas públicas (las usan mixin, capas y hooks) ────────────────────

    /** ¿Pasada corporal FP para el jugador local? Variante global (sin entidad a mano). */
    public static boolean isBodyPass() {
        return isFirstPersonPass() && Minecraft.getInstance().player != null;
    }

    /** ¿Pasada corporal FP para ESTA entidad? Los remotos nunca entran. */
    public static boolean isBodyPass(Entity e) {
        return isFirstPersonPass() && e == Minecraft.getInstance().player;
    }

    /** Pelo, halo y scouter físico: lo que cuelga de la cabeza queda fuera de la pasada. */
    public static boolean hideHeadAttachments(Entity e) {
        return isBodyPass(e);
    }

    /** El brazo geo de RenderArmEvent debe callarse mientras PAL dibuja el cuerpo FP. */
    public static boolean suppressVanillaArm(Entity e) {
        return isBodyPass(e);
    }

    /** ¿Debe ocultarse la SKIN completa del jugador? (la consulta PlayerModelHideMixin).
     *  Solo con skin racial: sin raza, el cuerpo vanilla SÍ se ve (sin cabeza). */
    public static boolean hideSkinNow() {
        if (!isBodyPass()) return false;
        var mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        var visual = mc.player.getData(ZenkaiDataAttachments.PLAYER_VISUAL.get());
        var stats  = mc.player.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
        return visual.shouldRenderRaceSkin() && stats.isRaceChosen();
    }

    // ── Pasada ────────────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onRenderPre(RenderLivingEvent.Pre<?, ?> e) {
        restoreIfPending(); // seguridad: nunca arrancar una pasada con un swap colgado

        if (!(e.getEntity() instanceof AbstractClientPlayer player)) return;
        if (player != Minecraft.getInstance().player) return;
        if (!isFirstPersonPass()) return;

        // hasRacialBody() ya comprueba (además de shouldRenderRaceSkin/isRaceChosen) que el
        // cuerpo resuelto no esté vacío — sin eso, una raza sin modelo real todavía (ver
        // RaceSkinSlots.backedOrEmpty) ocultaría el modelo vanilla ENTERO más abajo sin nada
        // que lo sustituya, dejando al jugador invisible en primera persona.
        boolean racial = hasRacialBody(player) && !player.isInvisible();

        // 1) Modelo vanilla. Con skin racial se va ENTERO: cuerpo, jacket, sleeves, pants y
        //    hat. Aquí sí surte efecto (corremos después de setModelProperties), a diferencia
        //    del byte, que en esta pasada nunca llega a ponerse a cero.
        //    Con skin vanilla solo se quita la cabeza: el resto es lo que el jugador eligió.
        if (e.getRenderer().getModel() instanceof PlayerModel<?> pm) {
            hiddenModel = pm;
            hiddenAll = racial;
            if (racial) {
                pm.setAllVisible(false);
            } else {
                savedHeadVisible = pm.head.visible;
                savedHatVisible  = pm.hat.visible;
                pm.head.visible = false;
                pm.hat.visible  = false;
            }
        }

        // 2) Slots. Se guardan los CUATRO siempre: HEAD se vacía pase lo que pase, así que
        //    la restauración nunca es opcional.
        var inv = player.getInventory();
        saved = new ItemStack[4];
        for (int i = 0; i < 4; i++) saved[i] = inv.getArmor(i);

        inv.armor.set(3, ItemStack.EMPTY); // HEAD: racial + casco real, fuera

        if (!racial) return;

        // Cuerpo racial en los tres slots que sí se ven. HEAD queda excluido a propósito.
        ItemStack feet  = RaceBodyResolver.resolve(player, EquipmentSlot.FEET);
        ItemStack legs  = RaceBodyResolver.resolve(player, EquipmentSlot.LEGS);
        ItemStack chest = RaceBodyResolver.resolve(player, EquipmentSlot.CHEST);
        if (!feet.isEmpty())  inv.armor.set(0, feet);
        if (!legs.isEmpty())  inv.armor.set(1, legs);
        if (!chest.isEmpty()) inv.armor.set(2, chest);
    }

    @SubscribeEvent
    public static void onRenderPost(RenderLivingEvent.Post<?, ?> e) {
        if (e.getEntity() instanceof AbstractClientPlayer player
                && player == Minecraft.getInstance().player) {
            restoreIfPending();
        }
    }

    private static void restoreIfPending() {
        if (hiddenModel != null) {
            if (hiddenAll) {
                hiddenModel.setAllVisible(true);
            } else {
                hiddenModel.head.visible = savedHeadVisible;
                hiddenModel.hat.visible  = savedHatVisible;
            }
            hiddenModel = null;
            hiddenAll = false;
        }
        if (saved == null) return;
        var mc = Minecraft.getInstance();
        if (mc.player != null) {
            for (int i = 0; i < 4; i++) mc.player.getInventory().armor.set(i, saved[i]);
        }
        saved = null;
    }

    /** ¿Este jugador tiene cuerpo racial que sustituya al modelo vanilla? Es la condición que
     *  decide si los brazos en FP los pone el geo (racial) o el modelo vanilla. */
    public static boolean hasRacialBody(AbstractClientPlayer p) {
        var visual = p.getData(ZenkaiDataAttachments.PLAYER_VISUAL.get());
        var stats  = p.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
        return visual.shouldRenderRaceSkin() && stats.isRaceChosen()
                && !RaceBodyResolver.resolve(p, EquipmentSlot.CHEST).isEmpty();
    }
}