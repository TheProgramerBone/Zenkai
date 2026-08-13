package com.hmc.zenkai.client.render_and_model_entities.blockentity;

import com.hmc.zenkai.content.blockentity.ScouterBenchBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

import java.util.Optional;

/**
 * Banco de scouter + el scouter que tenga dentro, flotando sobre la bandeja y girando despacio.
 * Se renderiza el ItemStack REAL, no una textura aparte: así el tinte del cristal (el color
 * handler del icono) y el modelo agrietado (el override de "broken") se aplican solos, sin
 * duplicar aquí la lógica que ya vive en ScouterItemColors y en scouter.json.
 * DÓNDE FLOTA. La altura sale del MODELO, no de un número elegido a ojo. El valor anterior
 * (0.95) dejaba el scouter dentro del cuerpo de la máquina, porque el banco mide 25 px de alto
 * y no 16. Dos fuentes, en este orden:
 *   1. Un bone vacío llamado "anchor_scouter", si el geo lo trae. Es la vía buena: se mueve la
 *      bandeja en Blockbench y el scouter la sigue sin tocar Java.
 *   2. Si no existe, la cara superior de la placa central — el cubo [-4,15,-8] de tamaño
 *      [8,3,16], cuyo techo está en y=18 —, más un pelo de aire.
 * El bone "detalles" NO sirve para esto: su pivote es [0,0,0], el mismo que el del modelo
 * entero, así que anclar ahí es anclar al suelo del bloque.
 * ⚠ VERIFICAR en GeckoLib 4.8.4: GeoModel#getBone(String) -> Optional<GeoBone> y
 * GeoBone#getPivotX/Y/Z() en unidades de modelo. Si no compila, se borran las cuatro líneas
 * del bloque `anchor.isPresent()` y queda solo la constante derivada, que es lo que se usa hoy
 * porque ese bone todavía no existe.
 */
public class ScouterBenchRenderer extends GeoBlockRenderer<ScouterBenchBlockEntity> {

    /** Bone opcional que, si existe en el geo, manda sobre la constante. */
    private static final String ANCHOR_BONE = "anchor_scouter";

    /** Techo de la placa central del banco, en píxeles de modelo. */
    private static final float PLATE_TOP_Y = 18f;
    /** Aire entre la placa y el scouter. */
    private static final float HOVER_ABOVE = 2.5f;

    private static final float SPIN_DEG_PER_TICK = 2f;
    private static final float ITEM_SCALE = 0.6f;
    /** Color de vista previa mientras el picker está abierto, en cliente y nada más. Ni un
     *  paquete mientras se arrastra: el stack real solo cambia al confirmar. */
    public static int previewTint = -1;

    public ScouterBenchRenderer(Context context) {
        super(new ScouterBenchModel());
        addRenderLayer(new ScouterBenchGlowLayer(this));
    }

    @Override
    public void render(@NotNull ScouterBenchBlockEntity be, float partialTick, @NotNull PoseStack poseStack,
                       @NotNull MultiBufferSource buffers, int packedLight, int packedOverlay) {
        super.render(be, partialTick, poseStack, buffers, packedLight, packedOverlay);

        ItemStack stack = be.scouter();
        if (stack.isEmpty()) return;

        float spin = be.getLevel() == null
                ? 0f
                : (be.getLevel().getGameTime() + partialTick) * SPIN_DEG_PER_TICK;

        float ax = 0f, ay = PLATE_TOP_Y + HOVER_ABOVE, az = 0f;
        Optional<GeoBone> anchor = getGeoModel().getBone(ANCHOR_BONE);
        if (anchor.isPresent()) {
            GeoBone b = anchor.get();
            ax = b.getPivotX();
            ay = b.getPivotY();
            az = b.getPivotZ();
        }

        poseStack.pushPose();
        poseStack.translate(0.5 + ax / 16.0, ay / 16.0, 0.5 + az / 16.0);
        poseStack.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);
        poseStack.rotateAround(new Quaternionf().rotationY((float) Math.toRadians(spin)), 0, 0, 0);

        // Vista previa del tinte: copia del stack con el color puesto, solo en cliente y solo
        // mientras el picker está abierto. El stack real no se toca hasta confirmar.
        // El campo vive AQUÍ y no en la pantalla: el renderer se dibuja siempre y la pantalla
        // existe a ratos, así que la dependencia tiene que ir en este sentido.
        if (previewTint >= 0) {
            stack = stack.copy();
            stack.set(DataComponents.DYED_COLOR, new DyedItemColor(previewTint, false));
        }

        Minecraft.getInstance().getItemRenderer().renderStatic(
                stack, ItemDisplayContext.GROUND, packedLight, packedOverlay,
                poseStack, buffers, be.getLevel(), 0);

        poseStack.popPose();
    }
}