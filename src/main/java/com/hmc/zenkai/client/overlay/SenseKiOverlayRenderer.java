package com.hmc.zenkai.client.overlay;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.client.LockOnClientState;
import com.hmc.zenkai.client.gui.AlignmentPalette;
import com.hmc.zenkai.config.ClientConfig;
import com.hmc.zenkai.event.ClientZenkaiHooks;
import com.hmc.zenkai.feature.combat.SenseKiMode;
import com.hmc.zenkai.feature.sense.SenseKiDataPacket;
import com.hmc.zenkai.feature.skills.SkillEffects;
import com.hmc.zenkai.registry.ModTags;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.List;

/**
 * Sentir Ki «Resonancia»: cada presencia percibida es un ECO (núcleo en rombo + ondas que se
 * abren), no una barra de vida. Dirección de arte en .claude/docs/ki-sense-resonancia.md.
 *
 * POR QUÉ EN PANTALLA: la versión en el mundo (quads con RenderType.text/textSeeThrough en
 * AFTER_PARTICLES) salió emborronada en juego —un degradado vertical de ~14 px en una barra de 4—,
 * de modo que el pixel-art perdía contorno y parecía un resplandor. Con el HUD cada píxel del
 * icono cae sobre píxeles enteros de la pantalla y se ve nítido, igual que el resto de la interfaz.
 * El punto de anclaje se proyecta con las matrices REALES del último frame del mundo (cámara,
 * FOV dinámico de sprint/vuelo incluido), guardadas en AFTER_PARTICLES; el resultado es en
 * coordenadas de GUI, así que no depende del GUI Scale.
 *
 * GRAMÁTICA POR NIVEL (el servidor ya filtró los datos; aquí solo se decide la forma):
 *  1 Eco           núcleo genérico que late breve y una onda; la intensidad (tamaño, opacidad,
 *                  halo) separa lo débil de lo extraordinario. Ninguna cifra.
 *  2 Resonancia    el núcleo se estabiliza y dos ondas; rombo ALTO = jugador, ANCHO = criatura;
 *                  corona de cuatro segmentos con la vida; el alineamiento tiñe un poco y acelera
 *                  o calma el latido (matiz, nunca la única pista).
 *  3 Lectura       la corona pasa a arco fino continuo; arcos de ki (izq.) y stamina (der.) de
 *                  quien los tenga; marca de fuerza relativa bajo el núcleo.
 *  4 Interpretación estados por color y movimiento (cargando = latido rápido, suprimiendo = violeta
 *                  e irregular, transformado = ondas más amplias) + iconos; sello de jefe; quien te
 *                  fija late en coral con ondas que se CIERRAN hacia él (presión que se acerca).
 *  5 Dominio       el objetivo fijado en dorado + panel de lectura (TargetReadingOverlay).
 *
 * PRESUPUESTO: SenseKiClientState.select reparte calidad por prioridad (pleno / medio / simple /
 * agrupado) cada pocos ticks; este render solo recorre esa lista.
 *
 * El color indica una PROPIEDAD, no una especie: cian lectura, violeta contenido, coral peligro,
 * dorado dominio. Los sprites son blancos y se tiñen; el contorno y el corazón del núcleo van en una
 * capa aparte sin teñir para que el núcleo conserve bordes nítidos con cualquier color.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class SenseKiOverlayRenderer {
    private SenseKiOverlayRenderer() {}

    // ── Texturas (== tools/gen_ki_sense_textures.py) ─────────────────────────
    private static final ResourceLocation ATLAS =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/ki_sense/echo_atlas.png");
    private static final ResourceLocation HALO =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/ki_sense/aura_halo.png");
    private static final int ATLAS_W = 256, ATLAS_H = 224;
    private static final int CELL = 32, HALF = 16;
    private static final int HALO_TEX = 64;

    private static final int ROW_WAVES = 0;
    private static final int ROW_CORE_FILL = 1;
    private static final int ROW_CORE_INK = 2;
    private static final int ROW_CROWN = 3;
    private static final int ROW_HEALTH_ARC = 4;
    private static final int ROW_KI_ARC = 5;
    private static final int ROW_STAMINA_ARC = 6;

    private static final int WAVE_FRAMES = 8;
    /** Las ondas normales no usan el último fotograma (el más amplio): queda para transformados y abrumadores. */
    private static final int WAVE_FRAMES_CALM = 7;
    private static final int COL_CORE_PLAYER = 3;   // S/M/L de jugador = 3..5; criatura = 0..2
    private static final int COL_CLUSTER = 6;
    private static final int COL_SEAL = 7;
    private static final int COL_STRENGTH = 4;      // fila de la corona: marcas en 4..7
    private static final int HEALTH_STEPS = 8;
    private static final int SIDE_STEPS = 6;

    // Estados: los MISMOS iconos que el HUD del jugador (ClientZenkaiHooks), a su tamaño nativo (20 px)
    // para no emborronar el contorno de 1 px. Solo «suprimiendo» es propio del Ki Sense: el HUD no lo
    // muestra (tools/gen_sense_suppress_icon.py, celda (3,7) de icons.png).
    private static final ClientZenkaiHooks.IconUV U_SUPPRESSING = new ClientZenkaiHooks.IconUV(60, 140);
    private static final int STATUS_ICON = ClientZenkaiHooks.ICON_DRAW;
    /** Jefe sin Ki Sense: el demonio del clash mide 16 px. */
    private static final int ICON = 16;
    private static final int ICON_GAP = 1;
    /** Distancia (px de GUI) del centro del eco a la base de la fila de iconos de estado. */
    private static final int ICON_ROW_OFFSET = 13;
    /** Distancia del centro del eco a la marca de fuerza relativa. */
    private static final int STRENGTH_MARK_OFFSET = 11;

    // Jefe sin Ki Sense: el demonio del clash sobre la cabeza (opción de cliente boss_icon).
    private static final ResourceLocation CLASH_TEX =
            ResourceLocation.fromNamespaceAndPath(Zenkai.MOD_ID, "textures/gui/hud/clash_hud.png");
    private static final int CLASH_W = 256, CLASH_H = 64;
    /** == tools/gen_clash_hud.py, «demonio» (junto a la mano, que está en 184,0). */
    private static final int U_BOSS = 200, V_BOSS = 0;
    private static final double MAX_BOSS_DIST = 64.0;
    private static final double BOSS_HEAD_OFFSET = 0.55;

    // ── Paleta oficial ───────────────────────────────────────────────────────
    public static final int C_INK = 0x151D27;
    public static final int C_CYAN = 0x63C8D6;
    public static final int C_GOLD = 0xD8A64D;
    public static final int C_CORAL = 0xE78C68;
    public static final int C_VIOLET = 0x8B91CF;
    public static final int C_CORE = 0xE9F5F2;
    /** Pista apagada de corona y arcos. */
    private static final int C_TRACK = 0x2F434E;
    private static final float TRACK_ALPHA = 0.85f;

    // ── Animación (en ticks de juego) ────────────────────────────────────────
    private static final float PERIOD_BASE = 36f;
    private static final float PERIOD_ECHO = 48f;      // nivel 1 y ecos simples: más lento y breve
    private static final float PERIOD_CHARGING = 12f;
    private static final float HOSTILE_PACE = 0.75f;
    private static final float FRIENDLY_PACE = 1.25f;
    private static final float COMBAT_PACE = 0.8f;
    private static final int ATTITUDE_ALIGNMENT = 30;
    private static final float ALIGN_TINT_HOSTILE = 0.30f;
    private static final float ALIGN_TINT_FRIENDLY = 0.25f;
    private static final float LOCKED_BRIGHTEN = 0.35f;
    private static final float WAVE_ALPHA = 0.85f;
    /** Ticks por "paso" del parpadeo irregular del ki suprimido. */
    private static final float FLICKER_STEP = 3f;
    /** Segundos de la reacción breve cuando algo empieza a fijarte. */
    private static final float THREAT_BURST_SECONDS = 1.2f;

    /** Color de una firma divina que no se puede leer: lavanda casi blanca. */
    private static final int C_DIVINE_UNKNOWN = 0xD8CCFF;

    // ── Intensidad por fuerza relativa (STRENGTH_FAINT..OVERWHELMING) ────────
    private static final float[] STRENGTH_ALPHA = {0.40f, 0.60f, 0.80f, 0.95f, 1.00f};
    private static final int[] STRENGTH_CORE = {0, 0, 1, 2, 2};
    private static final int[] HALO_SIZE = {0, 0, 0, 22, 32};
    private static final float[] HALO_ALPHA = {0f, 0f, 0f, 0.32f, 0.55f};
    private static final int CHARGING_HALO_SIZE = 26;
    private static final float CHARGING_HALO_ALPHA = 0.40f;

    /** Margen fuera de pantalla hasta el que aún se dibuja (entra poco a poco por el borde). */
    private static final int OFFSCREEN_MARGIN = 24;

    // ── Matrices del último frame del mundo ──────────────────────────────────

    private static final Matrix4f VIEW = new Matrix4f();
    private static final Matrix4f PROJ = new Matrix4f();
    private static Vec3 camPos = Vec3.ZERO;
    private static float partial = 0f;
    private static boolean haveMatrices = false;

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        VIEW.set(e.getModelViewMatrix());
        PROJ.set(e.getProjectionMatrix());
        camPos = e.getCamera().getPosition();
        partial = e.getPartialTick().getGameTimeDeltaPartialTick(true);
        haveMatrices = true;
    }

    private static final Vector4f TMP = new Vector4f();

    /**
     * Proyección para otros indicadores de HUD (el anillo del lock-on): {x, y, píxeles de GUI por
     * bloque a esa distancia}, o null si el punto queda detrás de la cámara o aún no hay matrices.
     * Sin recorte por pantalla: quien llama decide qué hacer con lo que cae fuera.
     */
    static float[] projectWorld(Vec3 world, int guiW, int guiH) {
        if (!haveMatrices) return null;
        TMP.set((float) (world.x - camPos.x), (float) (world.y - camPos.y), (float) (world.z - camPos.z), 1f);
        VIEW.transform(TMP);
        PROJ.transform(TMP);
        if (TMP.w <= 0.05f) return null;
        float ndcX = TMP.x / TMP.w, ndcY = TMP.y / TMP.w;
        float pxPerBlock = PROJ.m11() * 0.5f * guiH / TMP.w;
        return new float[]{(ndcX * 0.5f + 0.5f) * guiW, (1f - (ndcY * 0.5f + 0.5f)) * guiH, pxPerBlock};
    }
    /** Resultado de {@link #project}: x, y en píxeles de GUI. */
    private static float projX, projY;

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (!haveMatrices || mc.level == null || mc.player == null || mc.options.hideGui || mc.screen != null) return;

        GuiGraphics g = e.getGuiGraphics();
        int level = SenseKiClientState.level();
        boolean sense = SenseKiClientState.mode() != SenseKiMode.OFF
                && SkillEffects.senseLevel(mc.player) > 0 && level > 0;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        if (sense) {
            long now = System.nanoTime();
            float time = mc.level.getGameTime() + partial;
            float fade = SenseKiClientState.fadeIn() * ClientConfig.senseOpacity();
            for (SenseKiClientState.Cluster c : SenseKiClientState.clusters()) {
                drawCluster(g, c, time, fade);
            }
            // De menor a mayor prioridad: lo importante queda encima.
            List<SenseKiPresence> sel = SenseKiClientState.selected();
            for (int i = sel.size() - 1; i >= 0; i--) {
                drawPresence(g, mc, sel.get(i), level, time, now, fade);
            }
        }

        // Jefes: el demonio sobre la cabeza no necesita el sentido (opción de cliente). Si el eco
        // ya lleva su sello (nivel 4+), no se duplica.
        if (ClientConfig.bossIcon()) {
            for (Entity ent : mc.level.entitiesForRendering()) {
                if (!(ent instanceof LivingEntity le) || !le.isAlive() || le == mc.player) continue;
                if (!le.getType().is(ModTags.EntityTypes.BOSSES)) continue;
                if (le.distanceToSqr(mc.player) > MAX_BOSS_DIST * MAX_BOSS_DIST) continue;
                if (sense && level >= 4 && SenseKiClientState.isSensed(le.getId())) continue;
                Vec3 p = le.getPosition(partial).add(0, le.getBbHeight() + BOSS_HEAD_OFFSET, 0);
                if (!project(p, g.guiWidth(), g.guiHeight())) continue;
                g.setColor(1f, 1f, 1f, 1f);
                g.blit(CLASH_TEX, Math.round(projX) - ICON / 2, Math.round(projY) - ICON,
                        U_BOSS, V_BOSS, ICON, ICON, CLASH_W, CLASH_H);
            }
        }
        g.setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    // ── Eco individual ───────────────────────────────────────────────────────

    private static void drawPresence(GuiGraphics g, Minecraft mc, SenseKiPresence p, int level,
                                     float time, long now, float fade) {
        Vec3 a = p.anchor(mc, partial, now);
        if (!project(a, g.guiWidth(), g.guiHeight())) return;
        int cx = Math.round(projX), cy = Math.round(projY);

        SenseKiDataPacket.Entry e = p.entry;
        int strength = Mth.clamp(e.strength(), 0, STRENGTH_ALPHA.length - 1);
        float alpha = fade * p.lifeAlpha(now) * STRENGTH_ALPHA[strength];
        if (alpha <= 0.01f) return;
        // Las barras no se atenúan con la fuerza relativa: una presencia débil sigue teniendo vida legible.
        float barAlpha = Math.max(alpha, 0.85f * fade * p.lifeAlpha(now));

        boolean locked = p.id == LockOnClientState.targetId();
        boolean states = level >= 4;
        boolean threat = states && e.has(SenseKiDataPacket.FLAG_TARGETING_YOU);
        boolean suppressing = states && e.has(SenseKiDataPacket.FLAG_SUPPRESSING);
        boolean charging = states && e.has(SenseKiDataPacket.FLAG_CHARGING);
        boolean transformed = states && e.has(SenseKiDataPacket.FLAG_TRANSFORMED);
        int color = colorOf(e, level, locked);
        // Firma divina ilegible (sin God Ki): pálida, temblorosa y sin datos (skill tree F5).
        boolean divineUnknown = e.has(SenseKiDataPacket.FLAG_DIVINE_UNKNOWN);
        if (divineUnknown) {
            color = C_DIVINE_UNKNOWN;
            alpha *= 0.55f + 0.45f * flicker(time, p.id);
        }

        float t = frac(time / period(e, level) + p.phase);
        float flicker = suppressing ? flicker(time, p.id) : 1f;
        SenseKiPresence.Tier tier = p.tier;
        // Nivel 1 y ecos simples: un pulso breve en lugar de una presencia estable.
        boolean brief = level <= 1 || tier == SenseKiPresence.Tier.SIMPLE;
        float coreAlpha = brief ? alpha * (0.35f + 0.65f * Math.max(0f, 1f - t * 2f)) : alpha * flicker;

        // Halo: solo presencias fuertes o cargando, y solo con calidad plena o media.
        if (tier != SenseKiPresence.Tier.SIMPLE) {
            int hs = HALO_SIZE[strength];
            float ha = HALO_ALPHA[strength];
            if (charging && CHARGING_HALO_ALPHA > ha) { hs = Math.max(hs, CHARGING_HALO_SIZE); ha = CHARGING_HALO_ALPHA; }
            if (hs > 0) {
                float breathe = 0.85f + 0.15f * Mth.sin((time / PERIOD_BASE + p.phase) * Mth.TWO_PI);
                tint(g, color, alpha * ha * breathe);
                g.blit(HALO, cx - hs / 2, cy - hs / 2, hs, hs, 0, 0, HALO_TEX, HALO_TEX, HALO_TEX, HALO_TEX);
            }
        }

        // Ondas.
        if (tier != SenseKiPresence.Tier.SIMPLE) {
            int frames = (transformed || strength == SenseKiDataPacket.STRENGTH_OVERWHELMING) ? WAVE_FRAMES : WAVE_FRAMES_CALM;
            if (brief) {
                if (t < 0.5f) wave(g, cx, cy, t * 2f, frames, false, color, alpha * flicker);
            } else {
                int waves = tier == SenseKiPresence.Tier.FULL ? 2 : 1;
                for (int k = 0; k < waves; k++) {
                    wave(g, cx, cy, frac(t + k * 0.5f), frames, threat, color, alpha * flicker);
                }
            }
            if (threat) {
                float s = p.threatSeconds(now);
                if (s >= 0f && s < THREAT_BURST_SECONDS) {
                    cell(g, WAVE_FRAMES - 1, ROW_WAVES, cx, cy, C_CORAL, alpha * (1f - s / THREAT_BURST_SECONDS));
                }
            }
        }

        // Sello de jefe (nivel 4).
        if (states && e.has(SenseKiDataPacket.FLAG_BOSS) && tier != SenseKiPresence.Tier.SIMPLE) {
            float breathe = 0.6f + 0.25f * Mth.sin(time * 0.08f);
            cell(g, COL_SEAL, ROW_CORE_FILL, cx, cy, C_GOLD, alpha * breathe);
        }

        // Núcleo: relleno teñido + tinta sin teñir encima.
        int col = STRENGTH_CORE[strength] + (level >= 2 && e.isPlayer() ? COL_CORE_PLAYER : 0);
        cell(g, col, ROW_CORE_FILL, cx, cy, color, coreAlpha);
        cell(g, col, ROW_CORE_INK, cx, cy, 0xFFFFFF, coreAlpha);

        if (brief || divineUnknown) return;

        // Vida: corona de cuatro segmentos (nivel 2) o arco continuo (nivel 3+).
        if (e.hasHealth()) {
            float h = Mth.clamp(e.healthFraction(), 0f, 1f);
            if (level < 3) {
                int lit = Math.round(h * 4f);
                for (int i = 0; i < 4; i++) {
                    if (i < lit) cell(g, i, ROW_CROWN, cx, cy, color, alpha);
                    else cell(g, i, ROW_CROWN, cx, cy, C_TRACK, alpha * TRACK_ALPHA);
                }
            } else {
                arc(g, ROW_HEALTH_ARC, HEALTH_STEPS, h, cx, cy, h <= 0.25f ? C_CORAL : C_CORE, barAlpha);
            }
        }

        if (tier != SenseKiPresence.Tier.FULL) return;

        // Ki y stamina (nivel 3; el servidor manda -1 si no tiene o no toca).
        if (e.ki() >= 0) arc(g, ROW_KI_ARC, SIDE_STEPS, e.ki() / 1000f, cx, cy, C_CYAN, barAlpha);
        if (e.stamina() >= 0) arc(g, ROW_STAMINA_ARC, SIDE_STEPS, e.stamina() / 1000f, cx, cy, C_GOLD, barAlpha);

        // Fuerza relativa (nivel 3): marca bajo el núcleo.
        if (level >= 3) {
            cell(g, COL_STRENGTH + strengthLabel(strength), ROW_CROWN, cx, cy + STRENGTH_MARK_OFFSET, color, alpha);
        }

        // Iconos de estado (nivel 4), centrados sobre la corona.
        if (states) {
            int flags = e.flags();
            int count = 0;
            if ((flags & SenseKiDataPacket.FLAG_CHARGING) != 0) count++;
            if ((flags & SenseKiDataPacket.FLAG_TRANSFORMING) != 0) count++;
            if ((flags & SenseKiDataPacket.FLAG_TRANSFORMED) != 0) count++;
            if ((flags & SenseKiDataPacket.FLAG_SUPPRESSING) != 0) count++;
            if ((flags & SenseKiDataPacket.FLAG_IN_COMBAT) != 0) count++;
            if (count > 0) {
                int top = cy - ICON_ROW_OFFSET - STATUS_ICON;
                int x = cx - (count * STATUS_ICON + (count - 1) * ICON_GAP) / 2;
                g.setColor(1f, 1f, 1f, alpha);
                x = status(g, x, top, flags, SenseKiDataPacket.FLAG_TRANSFORMING, ClientZenkaiHooks.ICON_TRANSFORMING);
                x = status(g, x, top, flags, SenseKiDataPacket.FLAG_TRANSFORMED, ClientZenkaiHooks.ICON_TRANSFORMED);
                x = status(g, x, top, flags, SenseKiDataPacket.FLAG_CHARGING, ClientZenkaiHooks.ICON_KI_CHARGE);
                x = status(g, x, top, flags, SenseKiDataPacket.FLAG_SUPPRESSING, U_SUPPRESSING);
                status(g, x, top, flags, SenseKiDataPacket.FLAG_IN_COMBAT, ClientZenkaiHooks.ICON_IN_COMBAT);
            }
        }
    }

    /** Zona agregada: tres rombos y una onda lenta, sin datos. */
    private static void drawCluster(GuiGraphics g, SenseKiClientState.Cluster c, float time, float fade) {
        if (!project(new Vec3(c.x(), c.y(), c.z()), g.guiWidth(), g.guiHeight())) return;
        int cx = Math.round(projX), cy = Math.round(projY);
        float alpha = fade * Math.min(1f, 0.45f + 0.1f * c.count());
        float t = frac(time / PERIOD_ECHO + (c.count() * 0.137f));
        int color = c.maxStrength() >= SenseKiDataPacket.STRENGTH_SUPERIOR ? C_CORE : C_CYAN;
        wave(g, cx, cy, t, WAVE_FRAMES_CALM, false, color, alpha);
        cell(g, COL_CLUSTER, ROW_CORE_FILL, cx, cy, color, alpha);
        cell(g, COL_CLUSTER, ROW_CORE_INK, cx, cy, 0xFFFFFF, alpha);
    }

    // ── Piezas ───────────────────────────────────────────────────────────────

    /**
     * Una onda en la fase {@code t} (0..1). Se abre y se apaga; con {@code closing} recorre el camino
     * inverso y se ENCIENDE al cerrarse, que es como se siente una presión que se acerca.
     */
    private static void wave(GuiGraphics g, int cx, int cy, float t, int frames, boolean closing, int color, float alpha) {
        float k = closing ? 1f - t : t;
        int frame = Math.min(frames - 1, (int) (k * frames));
        float a = alpha * WAVE_ALPHA * (1f - frame / (float) frames);
        if (a > 0.01f) cell(g, frame, ROW_WAVES, cx, cy, color, a);
    }

    /** Arco de recurso: pista apagada completa + relleno por pasos. 0 exacto no enciende nada. */
    private static void arc(GuiGraphics g, int row, int steps, float frac, int cx, int cy, int color, float alpha) {
        cell(g, steps - 1, row, cx, cy, C_TRACK, alpha * TRACK_ALPHA);
        float f = Mth.clamp(frac, 0f, 1f);
        if (f <= 0f) return;
        int n = Mth.clamp((int) Math.ceil(f * steps), 1, steps);
        cell(g, n - 1, row, cx, cy, color, alpha);
    }

    private static int status(GuiGraphics g, int x, int y, int flags, int bit, ClientZenkaiHooks.IconUV uv) {
        if ((flags & bit) == 0) return x;
        ClientZenkaiHooks.drawBadge(g, x, y, uv);
        return x + STATUS_ICON + ICON_GAP;
    }

    /** Celda del atlas centrada en (cx, cy), teñida. */
    private static void cell(GuiGraphics g, int col, int row, int cx, int cy, int rgb, float alpha) {
        if (alpha <= 0.01f) return;
        tint(g, rgb, alpha);
        g.blit(ATLAS, cx - HALF, cy - HALF, col * CELL, row * CELL, CELL, CELL, ATLAS_W, ATLAS_H);
    }

    private static void tint(GuiGraphics g, int rgb, float alpha) {
        g.setColor(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f,
                Mth.clamp(alpha, 0f, 1f));
    }

    // ── Reglas de lenguaje visual ────────────────────────────────────────────

    /**
     * Color por PROPIEDAD. Base cian; el alineamiento (nivel 2+) solo lo matiza; suprimir el ki lo
     * vuelve violeta y fijarte, coral; el objetivo fijado se aclara (dorado al nivel 5).
     */
    public static int colorOf(SenseKiDataPacket.Entry e, int level, boolean locked) {
        int c = C_CYAN;
        if (level >= 2 && e.hasAlignment()) {
            int al = e.alignment();
            c = al < 0
                    ? AlignmentPalette.lerpRgb(c, C_CORAL, ALIGN_TINT_HOSTILE * (-al / 100f))
                    : AlignmentPalette.lerpRgb(c, C_CORE, ALIGN_TINT_FRIENDLY * (al / 100f));
        }
        if (level >= 4 && e.has(SenseKiDataPacket.FLAG_SUPPRESSING)) c = C_VIOLET;
        if (level >= 4 && e.has(SenseKiDataPacket.FLAG_TARGETING_YOU)) c = C_CORAL;
        if (locked) c = level >= 5 ? C_GOLD : AlignmentPalette.lerpRgb(c, 0xFFFFFF, LOCKED_BRIGHTEN);
        return c & 0xFFFFFF;
    }

    /** Periodo del latido en ticks: la actitud se expresa en el ritmo, no solo en el color. */
    private static float period(SenseKiDataPacket.Entry e, int level) {
        if (level <= 1) return PERIOD_ECHO;
        if (level >= 4 && e.has(SenseKiDataPacket.FLAG_CHARGING)) return PERIOD_CHARGING;
        float p = PERIOD_BASE;
        if (e.hasAlignment()) {
            if (e.alignment() <= -ATTITUDE_ALIGNMENT) p *= HOSTILE_PACE;
            else if (e.alignment() >= ATTITUDE_ALIGNMENT) p *= FRIENDLY_PACE;
        }
        if (level >= 4 && e.has(SenseKiDataPacket.FLAG_IN_COMBAT)) p *= COMBAT_PACE;
        return p;
    }

    /** Parpadeo irregular pero DETERMINISTA (tiempo de juego + id): nada aleatorio por frame. */
    private static float flicker(float time, int id) {
        int h = Mth.murmurHash3Mixer((int) (time / FLICKER_STEP) * 31 + id);
        return 0.35f + 0.65f * ((h & 0xFF) / 255f);
    }

    /** Índice de la marca de fuerza: inferior (débil o inferior), comparable, superior, abrumadora. */
    public static int strengthLabel(int strength) {
        return switch (strength) {
            case SenseKiDataPacket.STRENGTH_FAINT, SenseKiDataPacket.STRENGTH_INFERIOR -> 0;
            case SenseKiDataPacket.STRENGTH_COMPARABLE -> 1;
            case SenseKiDataPacket.STRENGTH_SUPERIOR -> 2;
            default -> 3;
        };
    }

    private static float frac(float v) {
        return v - Mth.floor(v);
    }

    /**
     * Proyecta un punto del mundo a píxeles de GUI con las matrices del último frame del mundo.
     * @return false si queda detrás de la cámara o fuera de pantalla (más allá del margen).
     */
    private static boolean project(Vec3 world, int guiW, int guiH) {
        TMP.set((float) (world.x - camPos.x), (float) (world.y - camPos.y), (float) (world.z - camPos.z), 1f);
        VIEW.transform(TMP);
        PROJ.transform(TMP);
        if (TMP.w <= 0.05f) return false;
        float ndcX = TMP.x / TMP.w, ndcY = TMP.y / TMP.w;
        projX = (ndcX * 0.5f + 0.5f) * guiW;
        projY = (1f - (ndcY * 0.5f + 0.5f)) * guiH;
        return projX > -OFFSCREEN_MARGIN && projX < guiW + OFFSCREEN_MARGIN
                && projY > -OFFSCREEN_MARGIN && projY < guiH + OFFSCREEN_MARGIN;
    }
}
