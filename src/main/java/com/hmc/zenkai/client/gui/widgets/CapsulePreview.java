package com.hmc.zenkai.client.gui.widgets;

import com.hmc.zenkai.content.block.CapsuleBlock;
import com.hmc.zenkai.feature.capsule.CapsuleZone;
import com.hmc.zenkai.registry.ModTags;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.RenderTypeHelper;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

/**
 * Visor 3D de la pantalla de una cápsula (2026-11-24, petición del usuario): lo que se va a guardar, en isométrica, con
 * giro arrastrando, zoom con la rueda y vuelta a la vista inicial con clic derecho. Mientras nadie lo toque gira solo.
 *
 * DE DÓNDE SALE: del mundo del CLIENTE, que ya tiene cargada la zona (el jugador está al lado). No viaja nada por red. Se
 * relee cada segundo por si alguien construye con la pantalla abierta. Es una VISTA PREVIA, no el resumen: lo que el
 * cliente no puede saber (un cofre con otra cápsula dentro) se dibuja igual, y la cifra buena es la del servidor.
 * Se omite lo que la captura nunca se lleva y el cliente sí puede saber: aire, fluido suelto, otra cápsula, el tag
 * {@code zenkai:capsule_blacklist_blocks} y lo indestructible (misma lista que CapsuleScan.isProhibited, salvo el NBT).
 *
 * CÓMO SE DIBUJA (patrón de InventoryScreen.renderEntityInInventory, que es la forma vanilla de meter 3D en una GUI):
 * pose trasladada al centro del visor y muy hacia delante en z (la GUI usa z mayor = más cerca), escala con y negativa
 * (la proyección de la GUI ya invierte y), giro de cámara y bloques teselados con renderBatched sobre las capas de
 * entidad de RenderTypeHelper (las mismas que usa renderSingleBlock para un bloque en la mano). Al acabar se borra el
 * buffer de profundidad: si no, el texto que la pantalla pinta después en z=0 quedaría «detrás» de los bloques.
 * Luz SIEMPRE plena (View devuelve 15): una casa de noche se vería negra. El AO sí se conserva, da volumen.
 *
 * ILUMINACIÓN (rehecha 2026-11-24, el usuario la vio oscura): las luces de inventario de vanilla vienen de un lado, así
 * que una de las dos caras laterales visibles quedaba al 40 % y, encima, con el sombreado por cara del mundo (0,6) casi
 * negra. Ahora dos luces propias en el espacio de la GUI (de frente y desde arriba, {@link #LIGHT_0}/{@link #LIGHT_1}),
 * que dejan las dos caras laterales visibles por igual (~90 %) y la de arriba al 100 %; la diferencia entre caras la pone un
 * sombreado por cara más suave que el del mundo ({@link View#getShade}).
 *
 * OCULTAR: con capturas enormes (netherita, radio 11) teselar cada frame puede trabar el juego. El ojo de la pantalla lo
 * apaga; si el jugador no ha elegido en esta sesión, arranca apagado por encima de {@link #AUTO_HIDE_BLOCKS} bloques.
 */
public final class CapsulePreview {

    private static final float DEFAULT_YAW = 225f, DEFAULT_PITCH = 30f;
    private static final float MIN_ZOOM = 0.4f, MAX_ZOOM = 6f;
    /** Luces del shader de entidad, en espacio de GUI (x a la derecha, y hacia ABAJO, z hacia el jugador). */
    private static final org.joml.Vector3f LIGHT_0 = new org.joml.Vector3f(0f, -0.4f, 1f).normalize();
    private static final org.joml.Vector3f LIGHT_1 = new org.joml.Vector3f(0f, -1f, 0.3f).normalize();
    /** Por encima de esto, el visor arranca oculto si el jugador no lo ha encendido o apagado a mano en esta sesión. */
    public static final int AUTO_HIDE_BLOCKS = 4096;
    /** Elección del jugador con el ojo, para la sesión entera (null = sin elegir). */
    @Nullable private static Boolean userHidden = null;
    /** Luz plena empaquetada (cielo 15, bloque 15). */
    private static final int FULL_BRIGHT = 0xF000F0;

    private final BlockPos capsule;
    private final BlockPos min;
    private final int side;

    private float yaw = DEFAULT_YAW, pitch = DEFAULT_PITCH, zoom = 1f;
    private boolean autoSpin = true;
    private boolean dragging;
    private int ticks;

    private final List<BlockPos> blocks = new ArrayList<>();
    private final LongOpenHashSet shown = new LongOpenHashSet();
    /**
     * Encuadre: centro y diagonal de la caja de lo que SE VE (relativos a la zona), no de la zona entera. Con la zona,
     * una casa de 7 en una cápsula de 11 salía diminuta (capturas K1 del 2026-11-24). Sin bloques, la zona.
     */
    private float centerX, centerY, centerZ, horizontal, height;

    public CapsulePreview(BlockPos capsule, int radius) {
        this.capsule = capsule;
        this.min = CapsuleZone.captureMin(capsule, radius);
        this.side = CapsuleZone.side(radius);
        refresh();
    }

    // ── Estado ──────────────────────────────────────────────────────────────

    public void tick() {
        if (autoSpin) yaw += 1.2f;
        if (++ticks % 20 == 0) refresh();
    }

    public boolean hidden() { return userHidden != null ? userHidden : blocks.size() > AUTO_HIDE_BLOCKS; }

    public void toggleHidden() { userHidden = !hidden(); }

    public int blockCount() { return blocks.size(); }

    /** ¿Hay algo que enseñar? (bloques; los mobs se buscan en cada frame). */
    public boolean hasBlocks() { return !blocks.isEmpty(); }

    private void refresh() {
        blocks.clear();
        shown.clear();
        centerX = centerY = centerZ = side / 2f;
        horizontal = side * (float) Math.sqrt(2);
        height = side;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        int x0 = side, y0 = side, z0 = side, x1 = -1, y1 = -1, z1 = -1;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int y = 0; y < side; y++) {
            for (int x = 0; x < side; x++) {
                for (int z = 0; z < side; z++) {
                    m.set(min.getX() + x, min.getY() + y, min.getZ() + z);
                    if (m.equals(capsule) || level.isOutsideBuildHeight(m)) continue;
                    BlockState st = level.getBlockState(m);
                    if (st.isAir() || st.getBlock() instanceof LiquidBlock || st.getBlock() instanceof CapsuleBlock) continue;
                    if (st.is(ModTags.Blocks.CAPSULE_BLACKLIST_BLOCKS) || st.getDestroySpeed(level, m) < 0) continue;
                    BlockPos p = m.immutable();
                    blocks.add(p);
                    shown.add(p.asLong());
                    x0 = Math.min(x0, x); y0 = Math.min(y0, y); z0 = Math.min(z0, z);
                    x1 = Math.max(x1, x); y1 = Math.max(y1, y); z1 = Math.max(z1, z);
                }
            }
        }
        if (x1 >= 0) {
            centerX = (x0 + x1 + 1) / 2f;
            centerY = (y0 + y1 + 1) / 2f;
            centerZ = (z0 + z1 + 1) / 2f;
            int dx = x1 - x0 + 1, dy = y1 - y0 + 1, dz = z1 - z0 + 1;
            horizontal = Math.max(1.5f, (float) Math.sqrt(dx * dx + dz * dz));
            height = dy;
        }
    }

    // ── Ratón (coordenadas absolutas; la pantalla decide si el clic cayó en el visor) ──

    public void startDrag() {
        dragging = true;
        autoSpin = false;
    }

    public void stopDrag() { dragging = false; }

    public boolean dragging() { return dragging; }

    public void drag(double dx, double dy) {
        yaw += (float) dx * 0.9f;
        pitch = Mth.clamp(pitch + (float) dy * 0.9f, -10f, 89f);
    }

    public void scroll(double amount) {
        autoSpin = false;
        zoom = Mth.clamp(zoom * (amount > 0 ? 1.15f : 1f / 1.15f), MIN_ZOOM, MAX_ZOOM);
    }

    public void reset() {
        yaw = DEFAULT_YAW;
        pitch = DEFAULT_PITCH;
        zoom = 1f;
        autoSpin = true;
    }

    // ── Dibujo ──────────────────────────────────────────────────────────────

    /** Dibuja dentro del rectángulo absoluto [x0,x1)×[y0,y1), recortado con scissor. */
    public void render(GuiGraphics g, int x0, int y0, int x1, int y1, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || hidden()) return;

        g.flush();
        g.enableScissor(x0, y0, x1, y1);
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate((x0 + x1) / 2f, (y0 + y1) / 2f, 400f);
        // Encuadre con la proyección de la vista inicial: a lo ancho, la diagonal horizontal (lo más que ocupa al girar);
        // a lo alto, la altura inclinada más el fondo visto desde arriba. Fijo con DEFAULT_PITCH para que el tamaño no
        // salte al arrastrar. ×1,1 deja margen. El visor es más ancho que alto: la diagonal 3D contra el lado corto lo
        // dejaba en la mitad (capturas K1 del 2026-11-24).
        double p = Math.toRadians(DEFAULT_PITCH);
        float tall = (float) (height * Math.cos(p) + horizontal * Math.sin(p));
        float scale = Math.min((x1 - x0) / (horizontal * 1.1f), (y1 - y0) / (tall * 1.1f)) * zoom;
        ps.scale(scale, -scale, scale);
        ps.mulPose(Axis.XP.rotationDegrees(pitch));
        ps.mulPose(Axis.YP.rotationDegrees(yaw + (autoSpin ? partialTick * 1.2f : 0f)));
        ps.translate(-centerX, -centerY, -centerZ);

        RenderSystem.setShaderLights(LIGHT_0, LIGHT_1);
        MultiBufferSource.BufferSource buffers = g.bufferSource();
        View view = new View(level);
        try {
            renderBlocks(mc, level, view, ps, buffers, partialTick);
            renderEntities(mc, level, ps, buffers, partialTick);
            buffers.endBatch();
        } finally {
            ps.popPose();
            g.disableScissor();
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            Lighting.setupFor3DItems();
        }
    }

    private void renderBlocks(Minecraft mc, ClientLevel level, View view, PoseStack ps,
                              MultiBufferSource.BufferSource buffers, float partialTick) {
        BlockRenderDispatcher brd = mc.getBlockRenderer();
        RandomSource random = RandomSource.create();
        for (BlockPos p : blocks) {
            BlockState st = level.getBlockState(p);
            if (st.isAir()) continue;   // se rompió desde el último refresco
            ps.pushPose();
            ps.translate(p.getX() - min.getX(), p.getY() - min.getY(), p.getZ() - min.getZ());
            try {
                if (st.getRenderShape() == RenderShape.MODEL) {
                    BakedModel model = brd.getBlockModel(st);
                    ModelData data = model.getModelData(view, p, st, level.getModelData(p));
                    random.setSeed(st.getSeed(p));
                    for (RenderType rt : model.getRenderTypes(st, random, data)) {
                        brd.renderBatched(st, p, view, ps, buffers.getBuffer(RenderTypeHelper.getEntityRenderType(rt, false)),
                                true, random, data, rt);
                    }
                }
                // Cofres, carteles, camas…: sin su renderizador de block entity no se verían. Con luz plena, igual que
                // los bloques, en vez de la del mundo que usaría BlockEntityRenderDispatcher.render.
                if (st.hasBlockEntity()) {
                    BlockEntity be = level.getBlockEntity(p);
                    if (be != null) {
                        BlockEntityRenderer<BlockEntity> r = mc.getBlockEntityRenderDispatcher().getRenderer(be);
                        if (r != null) r.render(be, partialTick, ps, buffers, FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                    }
                }
            } catch (RuntimeException ignored) {
                // Un modelo o renderizador de otro mod que no soporte este contexto se salta: es una vista previa.
            }
            ps.popPose();
        }
    }

    private void renderEntities(Minecraft mc, ClientLevel level, PoseStack ps,
                                MultiBufferSource.BufferSource buffers, float partialTick) {
        AABB box = CapsuleZone.box(min, (side - 1) / 2);
        EntityRenderDispatcher erd = mc.getEntityRenderDispatcher();
        erd.setRenderShadow(false);
        try {
            // Mismo filtro de tipo que CapsuleScan (mobs, colgados y vehículos con los pies dentro), incluidos pasajeros.
            for (Entity e : level.getEntities((Entity) null, box, c -> box.contains(c.position())
                    && !(c instanceof Player) && c.isAlive()
                    && (c instanceof LivingEntity || c instanceof HangingEntity || c instanceof VehicleEntity))) {
                double x = Mth.lerp(partialTick, e.xOld, e.getX()) - min.getX();
                double y = Mth.lerp(partialTick, e.yOld, e.getY()) - min.getY();
                double z = Mth.lerp(partialTick, e.zOld, e.getZ()) - min.getZ();
                try {
                    erd.render(e, x, y, z, Mth.lerp(partialTick, e.yRotO, e.getYRot()), partialTick, ps, buffers, FULL_BRIGHT);
                } catch (RuntimeException ignored) {
                    // Igual que con los bloques: un renderizador ajeno que falle aquí no tumba la pantalla.
                }
            }
        } finally {
            erd.setRenderShadow(true);
        }
    }

    /**
     * El mundo visto por el visor: solo los bloques que se enseñan (el resto, aire, para que el borde de la zona y los
     * huecos de lo omitido se vean cerrados) y luz plena. Color de bioma, sombreado y model data, los del mundo real.
     */
    private final class View implements BlockAndTintGetter {
        private final ClientLevel level;

        View(ClientLevel level) { this.level = level; }

        @Override
        public @NotNull BlockState getBlockState(@NotNull BlockPos pos) {
            return shown.contains(pos.asLong()) ? level.getBlockState(pos) : Blocks.AIR.defaultBlockState();
        }

        @Override
        public @NotNull FluidState getFluidState(@NotNull BlockPos pos) { return getBlockState(pos).getFluidState(); }

        @Override
        public @Nullable BlockEntity getBlockEntity(@NotNull BlockPos pos) {
            return shown.contains(pos.asLong()) ? level.getBlockEntity(pos) : null;
        }

        @Override
        public float getShade(@NotNull Direction direction, boolean shade) {
            // Más suave que el del mundo (1 / 0,5 / 0,8 / 0,6): el shader ya aporta volumen con LIGHT_0/LIGHT_1.
            if (!shade) return 1f;
            return switch (direction) {
                case UP -> 1f;
                case DOWN -> 0.65f;
                case NORTH, SOUTH -> 0.92f;
                case EAST, WEST -> 0.82f;
            };
        }

        @Override
        public @NotNull LevelLightEngine getLightEngine() { return level.getLightEngine(); }

        @Override
        public int getBlockTint(@NotNull BlockPos pos, @NotNull ColorResolver resolver) { return level.getBlockTint(pos, resolver); }

        @Override
        public int getBrightness(@NotNull LightLayer layer, @NotNull BlockPos pos) { return 15; }

        @Override
        public int getRawBrightness(@NotNull BlockPos pos, int amount) { return 15; }

        @Override
        public @NotNull ModelData getModelData(@NotNull BlockPos pos) { return level.getModelData(pos); }

        @Override
        public int getHeight() { return level.getHeight(); }

        @Override
        public int getMinBuildHeight() { return level.getMinBuildHeight(); }
    }
}
