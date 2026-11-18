package com.hmc.zenkai.client.gui.widgets;

import com.hmc.zenkai.client.gui.ZenkaiTechPalette;
import com.hmc.zenkai.feature.sideconfig.SideConfigPacket;
import com.hmc.zenkai.feature.sideconfig.SideConfigurable;
import com.hmc.zenkai.feature.transport.SideMode;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Cruz de caras al estilo de la configuración de lados de Mekanism (0.7.0): seis casillas con la
 * TEXTURA real de cada cara del bloque y un marco del color del modo. Hoy la usa la pantalla del Motor
 * de Ki (pestañas Energía / Fluido). Hubo una pantalla de caras suelta para la llave y botón en los
 * tanques; se quitaron el 2026-11-17: la llave cambia la cara con un clic directo (WrenchItem).
 *
 * DISPOSICIÓN (la de Mekanism):      [arriba]
 *                            [izq.] [frente] [der.]
 *                            [atrás][abajo]
 * El FRENTE es la cara que mira hacia el jugador al abrir (los tanques no tienen orientación propia,
 * y así «izquierda» es siempre SU izquierda). Izquierda = frente.getClockWise(): con el jugador al sur
 * mirando al norte, su izquierda es el oeste.
 *
 * Colores del marco: Normal acero, Empujar cian, Tirar verde, Nada rojo con la cara oscurecida. Un
 * modo que no hace nada en el estado actual (SideConfigurable.sideModeUseful) va atenuado.
 * Clic izquierdo = siguiente modo, derecho = anterior. Solo PIDE el cambio (SideConfigPacket): lo que
 * se pinta es siempre el estado sincronizado del bloque, nunca una suposición local.
 */
public final class SideConfigWidget {

    public static final int CELL = 20, GAP = 2;
    public static final int W = CELL * 3 + GAP * 2, H = W;

    private final BlockPos pos;
    private final Direction front;
    private SideConfigurable.Channel channel;
    private int x, y;

    public SideConfigWidget(BlockPos pos, Direction front, SideConfigurable.Channel channel) {
        this.pos = pos;
        this.front = front.getAxis().isHorizontal() ? front : Direction.NORTH;
        this.channel = channel;
    }

    public void setPosition(int x, int y) { this.x = x; this.y = y; }
    public void setChannel(SideConfigurable.Channel c) { this.channel = c; }
    public SideConfigurable.Channel channel() { return channel; }

    /** Las seis casillas: columna, fila y la cara del mundo que representa. */
    private record Cell(int col, int row, Direction face, String rel) {}

    private List<Cell> cells() {
        return List.of(
                new Cell(1, 0, Direction.UP, "top"),
                new Cell(0, 1, front.getClockWise(), "left"),
                new Cell(1, 1, front, "front"),
                new Cell(2, 1, front.getCounterClockWise(), "right"),
                new Cell(0, 2, front.getOpposite(), "back"),
                new Cell(1, 2, Direction.DOWN, "bottom"));
    }

    @Nullable
    private SideConfigurable target() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.getBlockEntity(pos) instanceof SideConfigurable c ? c : null;
    }

    private int cellX(Cell c) { return x + c.col() * (CELL + GAP); }
    private int cellY(Cell c) { return y + c.row() * (CELL + GAP); }

    @Nullable
    private Cell cellAt(double mx, double my) {
        for (Cell c : cells()) {
            if (mx >= cellX(c) && mx < cellX(c) + CELL && my >= cellY(c) && my < cellY(c) + CELL) return c;
        }
        return null;
    }

    public void render(GuiGraphics g, int mouseX, int mouseY) {
        SideConfigurable cfg = target();
        Minecraft mc = Minecraft.getInstance();
        if (cfg == null || mc.level == null) return;
        BlockState state = mc.level.getBlockState(pos);
        Cell hover = cellAt(mouseX, mouseY);

        for (Cell c : cells()) {
            int cx = cellX(c), cy = cellY(c);
            boolean locked = cfg.sideLocked(c.face());
            SideMode mode = cfg.sideMode(channel, c.face());
            boolean useful = locked || cfg.sideModeUseful(channel, mode);

            int frame = locked ? ZenkaiTechPalette.SCREEN_EDGE : frameColor(mode);
            if (!useful) frame = ZenkaiTechPalette.withAlpha(frame, 0x70);
            g.fill(cx, cy, cx + CELL, cy + CELL, frame);
            g.fill(cx + 1, cy + 1, cx + CELL - 1, cy + CELL - 1, ZenkaiTechPalette.SCREEN_EDGE);

            TextureAtlasSprite sprite = faceSprite(mc, state, c.face());
            if (sprite != null) {
                RenderSystem.enableBlend();
                g.blit(cx + 2, cy + 2, 0, 16, 16, sprite);
            }
            // Desconectada o bloqueada: la cara se apaga; la cara apuntada se aclara un poco.
            if (locked || mode == SideMode.NONE) {
                g.fill(cx + 2, cy + 2, cx + CELL - 2, cy + CELL - 2, 0xA0101418);
            } else if (!useful) {
                g.fill(cx + 2, cy + 2, cx + CELL - 2, cy + CELL - 2, 0x60101418);
            }
            if (locked) {
                // Aspa de «sin puertos» con el acero oscuro.
                for (int i = 4; i < CELL - 4; i++) {
                    g.fill(cx + i, cy + i, cx + i + 1, cy + i + 1, ZenkaiTechPalette.STEEL_DARK);
                    g.fill(cx + CELL - 1 - i, cy + i, cx + CELL - i, cy + i + 1, ZenkaiTechPalette.STEEL_DARK);
                }
            }
            if (c == hover && !locked) {
                g.fill(cx + 2, cy + 2, cx + CELL - 2, cy + CELL - 2, 0x30FFFFFF);
            }
        }
    }

    public static int frameColor(SideMode m) {
        return switch (m) {
            case NORMAL -> ZenkaiTechPalette.STEEL_HI;
            case PUSH -> ZenkaiTechPalette.CYAN_HI;
            case PULL -> ZenkaiTechPalette.LED_GREEN_MID;
            case NONE -> ZenkaiTechPalette.LED_RED;
        };
    }

    /**
     * Textura de la cara tal como la pinta el modelo del bloque: el primer quad de esa dirección
     * (con el ModelData del block entity, que en un tanque decide la textura conectada). Si el modelo
     * no tiene quads «culled» en esa cara (un cable, un modelo por partes), cae a la partícula.
     */
    @Nullable
    private TextureAtlasSprite faceSprite(Minecraft mc, BlockState state, Direction face) {
        try {
            BakedModel model = mc.getBlockRenderer().getBlockModel(state);
            BlockEntity be = mc.level.getBlockEntity(pos);
            ModelData data = be != null ? be.getModelData() : ModelData.EMPTY;
            List<BakedQuad> quads = model.getQuads(state, face, RandomSource.create(42L), data, null);
            if (!quads.isEmpty()) return quads.get(0).getSprite();
            return model.getParticleIcon(data);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** true si el clic cayó en una casilla (se haya podido cambiar o no). */
    public boolean mouseClicked(double mx, double my, int button) {
        Cell c = cellAt(mx, my);
        if (c == null || (button != 0 && button != 1)) return false;
        SideConfigurable cfg = target();
        if (cfg == null || cfg.sideLocked(c.face())) return true;
        // El clic suena desde el servidor (SideConfigPacket), una vez y también para quien esté al lado.
        PacketDistributor.sendToServer(new SideConfigPacket(pos, channel.ordinal(), c.face().ordinal(), button == 1));
        return true;
    }

    /** Tooltip de la casilla bajo el ratón, o lista vacía. */
    public List<Component> tooltip(int mouseX, int mouseY) {
        List<Component> lines = new ArrayList<>();
        Cell c = cellAt(mouseX, mouseY);
        SideConfigurable cfg = target();
        if (c == null || cfg == null) return lines;
        lines.add(Component.translatable("screen.zenkai.side_config.face." + c.rel())
                .append(Component.literal(" ("))
                .append(Component.translatable("messages.zenkai.transport.side." + c.face().getName()))
                .append(Component.literal(")"))
                .withStyle(ChatFormatting.BOLD));
        if (cfg.sideLocked(c.face())) {
            lines.add(Component.translatable("screen.zenkai.side_config.locked").withStyle(ChatFormatting.GRAY));
            return lines;
        }
        SideMode m = cfg.sideMode(channel, c.face());
        lines.add(Component.translatable(m.langKey()).withStyle(s -> s.withColor(frameColor(m) & 0xFFFFFF)));
        lines.add(Component.translatable("screen.zenkai.side_config.tip." + m.getSerializedName()).withStyle(ChatFormatting.GRAY));
        if (!cfg.sideModeUseful(channel, m)) {
            lines.add(Component.translatable("screen.zenkai.side_config.idle").withStyle(ChatFormatting.RED));
        }
        lines.add(Component.translatable("screen.zenkai.side_config.click").withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }
}
