package com.hmc.zenkai.client.overlay;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.config.ClientConfig;
import com.hmc.zenkai.feature.sense.DamageNumberPacket;
import com.hmc.zenkai.feature.skills.SkillEffects;
import com.hmc.zenkai.util.ZenkaiNumbers;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Números de daño flotantes sobre el enemigo golpeado. Los manda el servidor solo al atacante y
 * solo si tiene Ki Sense (DamageNumberPacket); aquí únicamente se dibujan, y la opción de
 * cliente {@code sense_ki.damage_numbers} los apaga.
 *
 * Billboard con la fuente en modo NORMAL y contorno 8x de vanilla (como Damage Indicators), sin
 * RenderType propio (compatible con Iris/Oculus). El tamaño
 * se compensa con la distancia para que sigan siendo legibles de lejos.
 *
 * Los golpes de un mismo objetivo muy seguidos (combo, haz continuo) se SUMAN en un solo número
 * en vez de apilar uno por tick, y hay un tope de números vivos.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class DamageNumberRenderer {
    private DamageNumberRenderer() {}

    /** Duración de un número, en ticks, y cuánto sube (bloques). */
    private static final float LIFETIME = 34f;
    private static final float RISE = 1.3f;
    /** Ventana en la que un golpe nuevo se suma al número vivo del mismo objetivo. */
    private static final float MERGE_WINDOW = 7f;
    private static final int MAX_ALIVE = 32;

    private static final float REF_DIST = 10f;
    private static final float SCALE_MIN = 0.7f;
    private static final float SCALE_MAX = 4.0f;
    /** Escala base tras el scale de nametag (0.025): el texto vanilla mide 8 px de alto. */
    private static final float BASE_SCALE = 0.038f;

    // Colores saturados y claros: sobre un fondo de aura/haz/partículas el blanco puro se pierde,
    // así que el físico es amarillo cálido (contrasta con el cielo y con el azul del ki).
    private static final int C_PHYSICAL = 0xFFE23A;
    private static final int C_KI = 0x55E0FF;
    private static final int C_BLACK_FLASH = 0xC060FF;
    private static final int C_BLOCKED = 0xB8B8B8;

    private static final int FULL_BRIGHT = 0xF000F0; // LightTexture.FULL_BRIGHT

    private static final class Pop {
        final int victimId;
        int amount;
        int flags;
        final Vec3 origin;
        final float dx, dz;
        float born;

        Pop(int victimId, int amount, int flags, Vec3 origin, float dx, float dz, float born) {
            this.victimId = victimId; this.amount = amount; this.flags = flags;
            this.origin = origin; this.dx = dx; this.dz = dz; this.born = born;
        }
    }

    private static final List<Pop> POPS = new ArrayList<>();

    /** Llamado desde DamageNumberPacket.handle, en el hilo principal. */
    public static void add(int victimId, int amount, int flags) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !ClientConfig.senseDamageNumbers()) return;
        // Defensa extra: sin la habilidad no se dibuja aunque llegara (el servidor ya filtra).
        if (mc.player == null || SkillEffects.senseLevel(mc.player) <= 0) return;
        Entity ent = mc.level.getEntity(victimId);
        if (ent == null) return;

        float now = mc.level.getGameTime();
        for (Pop p : POPS) {
            if (p.victimId == victimId && now - p.born < MERGE_WINDOW) {
                p.amount += amount;
                p.flags |= flags;
                return;
            }
        }
        if (POPS.size() >= MAX_ALIVE) POPS.remove(0);

        // Desvío lateral pseudoaleatorio pero estable por golpe, para que varios no se tapen.
        long h = (victimId * 73856093L) ^ ((long) now * 19349663L);
        float dx = ((h & 0xFF) / 255f - 0.5f) * 0.7f;
        float dz = (((h >> 8) & 0xFF) / 255f - 0.5f) * 0.7f;
        // Cada número vivo del mismo objetivo sube el punto de nacimiento, para que no se pisen.
        int stacked = 0;
        for (Pop p : POPS) if (p.victimId == victimId) stacked++;
        Vec3 origin = ent.position().add(0, ent.getBbHeight() * 0.75 + 0.3 * Math.min(stacked, 4), 0);
        POPS.add(new Pop(victimId, amount, flags, origin, dx, dz, now));
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (POPS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options.hideGui) return;

        Camera cam = e.getCamera();
        Vec3 camPos = cam.getPosition();
        PoseStack pose = e.getPoseStack();
        float now = mc.level.getGameTime() + e.getPartialTick().getGameTimeDeltaPartialTick(true);
        Font font = mc.font;
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

        POPS.removeIf(p -> now - p.born > LIFETIME);
        for (Pop p : POPS) {
            float t = (now - p.born) / LIFETIME;
            float rise = 1f - (1f - t) * (1f - t); // sale rápido y se frena arriba
            double x = p.origin.x + p.dx, y = p.origin.y + RISE * rise, z = p.origin.z + p.dz;
            double dx = x - camPos.x, dy = y - camPos.y, dz = z - camPos.z;
            float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);

            // Pequeño "golpe" de escala al nacer y desvanecido en el último tercio.
            float pop = 1f + 0.6f * Math.max(0f, 1f - t * 5f);
            // Los golpes grandes se ven más grandes (1.0 a 1.6), a la manera de los números de crítico.
            float mag = Mth.clamp(0.9f + 0.1f * (float) Math.log10(Math.max(p.amount, 1)), 1f, 1.6f);
            // Se desvanece ENCOGIENDO, nunca con alfa: el texto con alfa parcial sale translúcido y
            // la fuente descarta los píxeles casi transparentes (el número "se cierra"). Damage
            // Indicators y Dummmmmmy hacen igual y por eso nunca se ve transparente.
            float fade = t < 0.7f ? 1f : Mth.clamp(1f - (t - 0.7f) / 0.3f, 0.05f, 1f);
            int argb = 0xFF000000 | colorFor(p.flags);

            String text = ZenkaiNumbers.format((long) p.amount);
            float k = Mth.clamp(dist / REF_DIST, SCALE_MIN, SCALE_MAX) * pop * mag * fade;
            int rgb = colorFor(p.flags);
            // Contorno casi negro y opaco: lo que de verdad evita que el número se pierda.
            int outline = 0xFF000000 | darken(rgb, 0.15f);

            pose.pushPose();
            pose.translate(dx, dy, dz);
            pose.mulPose(cam.rotation());
            // X POSITIVA, como la nametag vanilla de 1.21 (scale(0.025, -0.025, 0.025)): con
            // cam.rotation() el texto ya mira a cámara. Con -X (convenio de versiones viejas) las
            // letras quedaban de espaldas, el culling las descartaba y no se veía NINGÚN número.
            // (El marcador del lock-on sí usa -X, pero su quad lleva el winding compensado a mano.)
            pose.scale(BASE_SCALE * k, -BASE_SCALE * k, BASE_SCALE * k);
            Matrix4f m = pose.last().pose();
            float w = font.width(text);
            // Contorno y relleno con la función de vanilla (la misma de Damage Indicators): ella
            // separa el relleno del contorno en profundidad. Hacerlo a mano con 8 copias en el
            // mismo plano dejaba el contorno tapando el relleno (mancha negra con puntitos).
            font.drawInBatch8xOutline(Component.literal(text).getVisualOrderText(), -w / 2f, 0f,
                    argb, outline, m, buffers, FULL_BRIGHT);
            buffers.endBatch();
            pose.popPose();
        }
    }

    private static int darken(int rgb, float f) {
        int r = (int) (((rgb >> 16) & 0xFF) * f), g = (int) (((rgb >> 8) & 0xFF) * f), b = (int) ((rgb & 0xFF) * f);
        return (r << 16) | (g << 8) | b;
    }

    private static int colorFor(int flags) {
        if ((flags & DamageNumberPacket.FLAG_BLACK_FLASH) != 0) return C_BLACK_FLASH;
        if ((flags & DamageNumberPacket.FLAG_BLOCKED) != 0) return C_BLOCKED;
        if ((flags & DamageNumberPacket.FLAG_KI) != 0) return C_KI;
        return C_PHYSICAL;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) { POPS.clear(); }
}
