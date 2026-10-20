package com.hmc.zenkai.client.overlay;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.gui.ZenkaiPalette;
import com.hmc.zenkai.client.party.ClientPartyState;
import com.hmc.zenkai.config.ClientConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import com.hmc.zenkai.feature.party.PartySyncPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.util.List;
import java.util.UUID;

/**
 * Panel de party en el HUD: por cada compañero elegido (hasta 4, ver PartyScreen), un diamante de
 * color, su cara y tres barras finas — vida, stamina y ki — SIN números. Sirve para saber "más o
 * menos cuánto le queda" de un vistazo en pleno combate. Los datos llegan del servidor
 * (PartyVitalsPacket) y funcionan a cualquier distancia.
 *
 * La colocación es de PartyHudLayout y se edita en HudPlacementScreen junto al HUD de técnicas, que
 * es donde se ve si se pisan.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class PartyHudOverlay {
    private PartyHudOverlay() {}

    /** Un color por posición del panel: el diamante identifica al compañero sin leer su nombre. */
    public static final int[] SLOT_COLORS = {0x4FC3F7, 0xFFB74D, 0x81C784, 0xBA68C8};

    // Estilo de las barras del HUD moderno (ModernStatsHud / clash / agarre): contorno oscuro, fondo
    // gris azulado, relleno con luz arriba y sombra abajo. Los tonos salen del atlas hud_modern.png
    // (HP, STM y KI) para que la party y el HUD propio digan lo mismo.
    private static final int FRAME = 0xFF101419;
    private static final int TRACK = 0xFF282832;
    private static final int FADED = 0x22FFFFFF;
    private static final int[] HP_TONES = {0xFFEE9389, 0xFFE24B3B, 0xFF933126};
    private static final int[] STM_TONES = {0xFF8AD1AA, 0xFF3CB371, 0xFF277449};
    private static final int[] KI_TONES = {0xFF8ADDF1, 0xFF3CC6E8, 0xFF278197};

    /** Diamante sobre los compañeros en el mundo: invisible a esta distancia o menos... */
    private static final double WORLD_FADE_START = 16.0;
    /** ...y del todo visible a partir de esta. */
    private static final double WORLD_FADE_FULL = 32.0;

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !ClientConfig.partyHudEnabled()) return;
        List<PartySyncPacket.Member> shown = ClientPartyState.shownMembers();
        if (shown.isEmpty()) return;

        GuiGraphics g = e.getGuiGraphics();
        PartyHudLayout l = PartyHudLayout.current(g.guiWidth(), g.guiHeight(), shown.size());
        for (int i = 0; i < shown.size(); i++) {
            PartySyncPacket.Member m = shown.get(i);
            draw(g, mc, l.rowX(), l.rowY(i), i, m.id(), ClientPartyState.vitalsOf(m.id()));
        }
    }

    /**
     * Una fila. {@code vitals} null = aún no ha llegado el dato (o el compañero está desconectado):
     * se dibuja el marco con las barras vacías y la cara atenuada, no un valor viejo.
     * Público para que la pantalla de colocación pinte la misma fila (con datos de muestra).
     */
    public static void draw(GuiGraphics g, Minecraft mc, int x, int y, int slot, UUID id, int[] vitals) {
        diamond(g, x, y + (PartyHudLayout.FACE - PartyHudLayout.DIAMOND) / 2 + 1,
                0xFF000000 | SLOT_COLORS[slot % SLOT_COLORS.length]);

        int fx = x + PartyHudLayout.DIAMOND + 2;
        g.fill(fx - 1, y - 1, fx + PartyHudLayout.FACE + 1, y + PartyHudLayout.FACE + 1, FRAME);
        PlayerFaceRenderer.draw(g, skinOf(mc, id), fx, y, PartyHudLayout.FACE);
        if (vitals == null) g.fill(fx, y, fx + PartyHudLayout.FACE, y + PartyHudLayout.FACE, 0x99000000);

        int bx = fx + PartyHudLayout.FACE + 3;
        int by = y + 1;
        bar(g, bx, by, vitals == null ? -1 : vitals[0], HP_TONES);
        bar(g, bx, by + PartyHudLayout.BAR_H + PartyHudLayout.BAR_GAP, vitals == null ? -1 : vitals[1], STM_TONES);
        bar(g, bx, by + 2 * (PartyHudLayout.BAR_H + PartyHudLayout.BAR_GAP), vitals == null ? -1 : vitals[2], KI_TONES);
    }

    /** -1 = sin esa reserva: solo el canal vacío y atenuado. */
    private static void bar(GuiGraphics g, int x, int y, int milli, int[] tones) {
        int w = PartyHudLayout.BAR_W, h = PartyHudLayout.BAR_H;
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, FRAME);
        g.fill(x, y, x + w, y + h, TRACK);
        if (milli < 0) {
            g.fill(x, y, x + w, y + h, FADED);
            return;
        }
        int fw = Math.round(w * Math.min(1000, milli) / 1000f);
        if (fw <= 0) return;
        // Luz arriba (1 px), cuerpo, sombra abajo (1 px): el mismo bisel de las barras grandes.
        g.fill(x, y, x + fw, y + 1, tones[0]);
        g.fill(x, y + 1, x + fw, y + h - 1, tones[1]);
        g.fill(x, y + h - 1, x + fw, y + h, tones[2]);
    }

    /**
     * Diamante de cada compañero elegido SOBRE su cabeza, proyectado a pantalla (como los ecos del
     * Ki Sense; no necesita la habilidad). Es para localizar a la party de lejos: a 16 bloques o
     * menos no se dibuja (ya se le ve) y de 16 a 32 sube la opacidad hasta ser del todo visible.
     * Solo hay posición de quien está cargado en el cliente.
     */
    @SubscribeEvent
    public static void onRenderMembersInWorld(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui || mc.screen != null
                || !ClientConfig.partyHudEnabled()) return;
        List<PartySyncPacket.Member> shown = ClientPartyState.shownMembers();
        if (shown.isEmpty()) return;

        GuiGraphics g = e.getGuiGraphics();
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(true);
        for (int i = 0; i < shown.size(); i++) {
            net.minecraft.world.entity.player.Player pl = mc.level.getPlayerByUUID(shown.get(i).id());
            if (pl == null || pl.isRemoved()) continue;
            double d = pl.distanceTo(mc.player);
            if (d <= WORLD_FADE_START) continue;
            float a = (float) Math.min(1.0, (d - WORLD_FADE_START) / (WORLD_FADE_FULL - WORLD_FADE_START));
            net.minecraft.world.phys.Vec3 head = pl.getPosition(pt).add(0, pl.getBbHeight() + 0.6, 0);
            float[] s = SenseKiOverlayRenderer.projectWorld(head, g.guiWidth(), g.guiHeight());
            if (s == null) continue;
            int ai = Math.max(1, Math.min(255, Math.round(a * 255f)));
            int x = Math.round(s[0]) - PartyHudLayout.DIAMOND / 2;
            int y = Math.round(s[1]) - PartyHudLayout.DIAMOND / 2;
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            // Contorno oscuro (un rombo mayor detrás) para que se lea sobre cielo y agua.
            diamond(g, x - 1, y - 1, (ai << 24) | 0x101419, PartyHudLayout.DIAMOND + 2);
            diamond(g, x, y, (ai << 24) | SLOT_COLORS[i % SLOT_COLORS.length], PartyHudLayout.DIAMOND);
            RenderSystem.disableBlend();
        }
    }

    /** Rombo relleno de 7×7 hecho con filas: sin textura, nítido a cualquier GUI Scale. */
    public static void diamond(GuiGraphics g, int x, int y, int argb) {
        diamond(g, x, y, argb, PartyHudLayout.DIAMOND);
    }

    private static void diamond(GuiGraphics g, int x, int y, int argb, int n) {
        int mid = n / 2;
        for (int r = 0; r < n; r++) {
            int half = mid - Math.abs(mid - r);
            g.fill(x + mid - half, y + r, x + mid + half + 1, y + r + 1, argb);
        }
    }

    private static PlayerSkin skinOf(Minecraft mc, UUID id) {
        if (mc.getConnection() != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(id);
            if (info != null) return info.getSkin();
        }
        return DefaultPlayerSkin.get(id);
    }
}
