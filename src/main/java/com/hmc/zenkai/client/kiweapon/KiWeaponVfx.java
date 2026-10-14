package com.hmc.zenkai.client.kiweapon;

import com.hmc.zenkai.client.PlayerHandTracker;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxColors;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxCompositeRenderer;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxFrameQueue;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxGeometry;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxProfile;
import com.hmc.zenkai.client.render_and_model_entities.kivfx.KiVfxRenderTypes;
import com.hmc.zenkai.feature.aura.AuraColors;
import com.hmc.zenkai.feature.kiweapon.KiWeaponState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Arma de ki DIBUJADA CON ENERGÍA, no con un modelo: tubos barridos del mismo pipeline que los
 * haces ({@link KiVfxCompositeRenderer#renderColumn}), así que la hoja tiene las cuatro capas de
 * ki (contorno, tinte, caliente, blanco), el hervor del borde y el bloom de un Kamehameha. La
 * primera versión era un modelo GeckoLib con textura en grises y se leía como un objeto pegado,
 * no como ki (2026-10-13).
 *
 * MARCO DE ÍTEM (el de vanilla en la mano, ver {@link KiWeaponLayer}): +Y es hacia donde apunta
 * la hoja, el origen es el puño, +Z es "arriba" cuando el brazo cuelga y la hoja va al frente, y
 * +X es hacia el costado. Las hojas son tubos APLANADOS en X, así que su ancho se ve de lado.
 *
 * DOS RUTAS:
 *  - Mundo (3ª persona y pasada de cuerpo de PAL): se ENCOLA en {@link KiVfxFrameQueue} con la
 *    matriz del marco de ítem, y se dibuja en AFTER_PARTICLES con bloom.
 *  - Mano de 1ª persona: la pila está en el espacio de la pasada de mano, con otra proyección, así
 *    que diferirla la descolocaría. Se dibuja AL MOMENTO, sin bloom.
 */
public final class KiWeaponVfx {
    private KiWeaponVfx() {}

    /** Dibuja los ejes de la pieza (/zenkaiweapon axes) para calibrar a ojo. */
    public static boolean showAxes = false;

    /** Inclinación (grados) de las armas "de empuñadura" (espada, guadaña) respecto a la perpendicular
     *  al antebrazo, hacia donde apuntan los dedos. 0 = la hoja sale recta del puño. Se ajusta con
     *  /zenkaiweapon tilt: una espada vanilla no se sujeta a 90 grados del antebrazo. */
    public static float tiltDegrees = 20.0F;

    /** Escala extra SOLO en 1ª persona y SOLO para armas de empuñadura: el FOV de la pasada de mano
     *  las agranda. La Blade queda a 1:1 porque su funda tiene que abrazar el brazo. */
    public static float firstPersonScale = 0.8F;

    /** Ticks que tarda el arma en crecer desde el puño al materializarse. */
    private static final float SUMMON_TICKS = 6.0F;

    /** Grosor relativo (en X) de las hojas aplanadas. 1 = tubo redondo. */
    private static final float BLADE_FLAT = 0.28F;

    /** Una pieza del arma: un tubo barrido, con su radio en la punta y su engorde hacia la raíz. */
    private record Part(KiVfxGeometry.TubePath path, float tipRadius, float rootMul, float flat,
                        KiVfxProfile profile) {
        Part(KiVfxGeometry.TubePath path, float tipRadius, float rootMul, float flat) {
            this(path, tipRadius, rootMul, flat, KiVfxProfile.KI_WEAPON);
        }
    }

    /** Cómo se coloca el arma en el marco de ítem. */
    private enum Mode {
        /** Se empuña: la hoja sale del puño y se inclina {@link #tiltDegrees}. */
        HELD,
        /** Se construye con +Y = punta y -Y = codo y se gira para que continúe el brazo hacia abajo (Blade). */
        ALONG_ARM,
        /** Mano secundaria: placa delante del antebrazo, sin inclinación. */
        SHIELD
    }

    /**
     * Las piezas de un arma. {@code trailTip}/{@code trailBase} son los dos bordes de la estela del
     * swing, en coordenadas LOCALES de la pieza (null = sin estela, p. ej. el escudo).
     */
    private record Shape(List<Part> parts, Mode mode, @org.jetbrains.annotations.Nullable Vec3 trailTip,
                         @org.jetbrains.annotations.Nullable Vec3 trailBase) {}

    private static final java.util.Map<String, Shape> SHAPES = new java.util.HashMap<>();

    private static Shape shapeOf(String variant) {
        return SHAPES.computeIfAbsent(variant == null ? "ki_blade" : variant, v -> switch (v) {
            case "ki_scythe" -> scytheShape();
            case "ki_sword" -> swordShape();
            case "ki_axe" -> axeShape();
            case "ki_mace" -> maceShape();
            case "ki_hammer" -> hammerShape();
            case "ki_shield" -> shieldShape();
            default -> bladeShape();
        });
    }

    // ── Piezas básicas ──────────────────────────────────────────────────────
    // Medidas tomadas de las armas de DragonMineZ (geo/weapons/*.geo.json, px/16 = bloques): hoja de
    // espada 3 px de ancho y ~25 de largo, katana 2 px de ancho, guarda de 11 px, hoja de ki de 7 px
    // de ancho que sale 0,6-0,75 bloques por delante del puño y cubre muñeca y antebrazo.

    private static KiVfxGeometry.TubePath line(float y0, float y1) {
        return bend(y0, y1, 0F);
    }

    /** Camino de y0 a y1 (puede ir hacia -Y) que se curva hacia +Z (arriba con el brazo colgando)
     *  una distancia total {@code z1} con forma cuadrática: recto en la raíz, curvo en la punta. */
    private static KiVfxGeometry.TubePath bend(float y0, float y1, float z1) {
        List<Vec3> pts = new ArrayList<>();
        int n = Math.max(3, Math.round(Math.abs(y1 - y0) / 0.1F) + 1);
        for (int i = 0; i < n; i++) {
            float t = i / (float) (n - 1);
            pts.add(new Vec3(0, Mth.lerp(t, y0, y1), z1 * t * t));
        }
        return KiVfxGeometry.tubePath(pts, 0F);
    }

    /** Tubo redondo de y0 a y1 cuyo radio pasa de {@code rRoot} (en y0) a {@code rTip} (en y1). */
    private static Part pipe(float y0, float y1, float rRoot, float rTip) {
        return new Part(line(y0, y1), rTip, rRoot / rTip, 1.0F);
    }

    /** Hoja plana: cuerpo [y0, y1] de radio r (curvado zBody) y punta que se afila hasta yTip. */
    private static List<Part> blade(float y0, float y1, float zBody, float r, float yTip, float zTip,
                                    float flat, KiVfxProfile profile) {
        List<Vec3> tipPts = new ArrayList<>();
        int n = Math.max(3, Math.round((yTip - y1) / 0.1F) + 1);
        for (int i = 0; i < n; i++) {
            float t = i / (float) (n - 1);
            tipPts.add(new Vec3(0, Mth.lerp(t, y1, yTip), zBody + zTip * t * t));
        }
        return List.of(
                new Part(bend(y0, y1, zBody), r, 1.0F, flat, profile),
                new Part(KiVfxGeometry.tubePath(tipPts, 0F), 0.008F, r / 0.008F, flat, profile));
    }

    /** Guarda: travesaño a lo largo de Z (el ANCHO de la hoja, como en una espada de verdad) con las
     *  puntas levantadas {@code up} hacia la punta del arma. */
    private static Part guard(float y, float half, float up, float radius) {
        List<Vec3> pts = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            float t = i / 6F * 2F - 1F;
            pts.add(new Vec3(0, y + up * t * t, half * t));
        }
        return new Part(KiVfxGeometry.tubePath(pts, 0F), radius, 1.0F, 1.0F);
    }

    /** Tubo de radio constante entre dos puntos cualesquiera. */
    private static Part span(Vec3 a, Vec3 b, float radius) {
        return new Part(KiVfxGeometry.tubePath(new ArrayList<>(List.of(a, b)), 0F), radius, 1.0F, 1.0F);
    }

    /** Púa: tubo que se afila de {@code rRoot} en {@code a} a punta en {@code b}. */
    private static Part spike(Vec3 a, Vec3 b, float rRoot) {
        return new Part(KiVfxGeometry.tubePath(new ArrayList<>(List.of(a, b)), 0F), 0.008F, rRoot / 0.008F, 1.0F);
    }

    /** Aro de tubo: {@code normal} 0 = en el plano YZ, 1 = en el XZ, 2 = en el XY. */
    private static Part ring(Vec3 c, float radius, int normal, float tube) {
        List<Vec3> pts = new ArrayList<>();
        int seg = 16;
        for (int i = 0; i <= seg; i++) {
            double ang = Math.PI * 2.0 * i / seg;
            float u = radius * (float) Math.cos(ang), w = radius * (float) Math.sin(ang);
            pts.add(switch (normal) {
                case 0 -> new Vec3(c.x, c.y + u, c.z + w);
                case 1 -> new Vec3(c.x + u, c.y, c.z + w);
                default -> new Vec3(c.x + u, c.y + w, c.z);
            });
        }
        return new Part(KiVfxGeometry.tubePath(pts, 0F), tube, 1.0F, 1.0F);
    }

    private static List<Part> join(List<Part> a, List<Part> b) {
        List<Part> out = new ArrayList<>(a);
        out.addAll(b);
        return out;
    }

    private static List<Part> join(List<Part> a, Part... more) {
        return join(a, List.of(more));
    }

    // ── Ki Blade ────────────────────────────────────────────────────────────
    // Referencia: ki_blade_zamasu.png. Una lente de ki LARGA y DELGADA que nace detrás del codo,
    // recorre el antebrazo y sale ~1,3 bloques más allá del puño con el filo dentado; solo hay
    // placas sueltas en muñeca y antebrazo (no un tubo único). Con el brazo colgando, el puño está
    // en y=0 y el codo en ~-0.40. Coordenadas de "marco de brazo" (alongArm=true).

    private static Part wrist() { return pipe(-0.05F, 0.06F, 0.17F, 0.17F); }
    private static Part forearm() { return pipe(-0.08F, -0.34F, 0.15F, 0.11F); }

    private static Shape bladeShape() {
        final KiVfxProfile J = KiVfxProfile.KI_WEAPON_JAGGED;
        return new Shape(
                join(blade(-0.05F, 0.35F, 0.0F, 0.10F, 1.30F, 0.10F, 0.10F, J),
                        join(List.of(new Part(bend(0.0F, -0.55F, 0.0F), 0.008F, 0.10F / 0.008F, 0.10F, J)),
                                wrist(), forearm())),
                Mode.ALONG_ARM, new Vec3(0, 1.30, 0.10), new Vec3(0, 0.45, 0.02));
    }

    // ── Ki Sword ────────────────────────────────────────────────────────────
    // Referencia: z_sword de DMZ. Empuñadura bajo el puño, guarda ancha con las puntas arriba, hoja
    // de 3 px. Los tamaños van a ~0,75 de los de DMZ (la espada de DMZ mide 2,4 bloques).

    private static Shape swordShape() {
        final KiVfxProfile K = KiVfxProfile.KI_WEAPON;
        return new Shape(
                join(blade(0.26F, 1.15F, 0.0F, 0.075F, 1.45F, 0.0F, 0.18F, K),
                        pipe(-0.28F, 0.26F, 0.03F, 0.03F),
                        pipe(-0.34F, -0.28F, 0.05F, 0.05F),
                        guard(0.26F, 0.27F, 0.07F, 0.028F)),
                Mode.HELD, new Vec3(0, 1.45, 0), new Vec3(0, 0.60, 0));
    }

    // ── Ki Scythe ───────────────────────────────────────────────────────────
    // kiweapon_scythe.geo.json de DMZ: mástil largo (60 px en total), hoja en media luna de ~1 bloque
    // que baja desde la punta, rematada en una púa. Aquí a ~0,6 de esa escala.

    /** Hoja en media luna al final de un mástil: sale hacia -Z (el filo mira ABAJO con el brazo
     *  colgando) y se curva de vuelta hacia el portador. {@code rootR} es su ancho en la base. */
    private static Part crescent(float top, float radius, float sweep, float dirZ, float rootR) {
        List<Vec3> arc = new ArrayList<>();
        int steps = 18;
        for (int i = 0; i <= steps; i++) {
            float a = sweep * i / steps;
            arc.add(new Vec3(0, top - radius * (1F - Mth.cos(a)), dirZ * radius * -Mth.sin(a)));
        }
        return new Part(KiVfxGeometry.tubePath(arc, 0F), 0.008F, rootR / 0.008F, BLADE_FLAT);
    }

    private static Shape scytheShape() {
        return new Shape(
                List.of(pipe(-0.50F, 1.55F, 0.032F, 0.032F),
                        pipe(1.55F, 1.80F, 0.032F, 0.008F),
                        crescent(1.55F, 0.80F, 1.65F, 1F, 0.16F),
                        pipe(1.48F, 1.56F, 0.06F, 0.06F)),
                Mode.HELD, new Vec3(0, 0.69, -0.80), new Vec3(0, 1.55, 0));
    }

    // ── Ki Axe ──────────────────────────────────────────────────────────────
    // Una mano. Mango con pomo, una HOJA grande en media luna hacia +Z (arriba con el brazo colgando:
    // el filo corta hacia delante cuando se descarga el golpe) y un contrapeso corto en el lado
    // contrario, rematada en una púa. Es la que rompe guardias (guard_break del JSON).

    private static Shape axeShape() {
        final float top = 0.95F, yc = top + 0.10F;
        final float flat = 0.30F;
        List<Vec3> big = new ArrayList<>(List.of(new Vec3(0, yc, 0), new Vec3(0, yc - 0.03, 0.22),
                new Vec3(0, yc - 0.13, 0.44)));
        List<Vec3> back = new ArrayList<>(List.of(new Vec3(0, yc, 0), new Vec3(0, yc - 0.02, -0.12),
                new Vec3(0, yc - 0.07, -0.22)));
        return new Shape(List.of(
                pipe(-0.30F, top + 0.12F, 0.032F, 0.032F),
                pipe(-0.37F, -0.30F, 0.052F, 0.052F),
                pipe(0.10F, 0.16F, 0.048F, 0.048F),
                pipe(top + 0.12F, top + 0.42F, 0.042F, 0.008F),
                new Part(KiVfxGeometry.tubePath(big, 0F), 0.010F, 0.16F / 0.010F, flat, KiVfxProfile.KI_WEAPON_JAGGED),
                new Part(KiVfxGeometry.tubePath(back, 0F), 0.010F, 0.075F / 0.010F, flat)),
                Mode.HELD, new Vec3(0, yc - 0.13, 0.44), new Vec3(0, yc, 0));
    }

    // ── Ki Mace ─────────────────────────────────────────────────────────────
    // Una mano. Mango y una cabeza ESFÉRICA hecha de tres aros cruzados con un núcleo macizo y púas
    // en las seis direcciones y en las cuatro diagonales: se lee como una esfera de ki erizada.
    // Empuja (knockback del JSON).

    private static Shape maceShape() {
        final Vec3 c = new Vec3(0, 0.98, 0);
        final float rr = 0.14F;
        List<Part> parts = new ArrayList<>(List.of(
                pipe(-0.28F, 0.82F, 0.034F, 0.034F),
                pipe(-0.34F, -0.28F, 0.05F, 0.05F),
                pipe(0.12F, 0.17F, 0.05F, 0.05F),
                pipe(0.86F, 1.10F, 0.10F, 0.10F),
                ring(c, rr, 0, 0.016F), ring(c, rr, 1, 0.016F), ring(c, rr, 2, 0.016F)));
        // Púas: ±Z, ±X, +Y y las cuatro diagonales del plano XZ.
        double[][] dirs = {{0, 0, 1}, {0, 0, -1}, {1, 0, 0}, {-1, 0, 0}, {0, 1, 0},
                {0.707, 0, 0.707}, {-0.707, 0, 0.707}, {0.707, 0, -0.707}, {-0.707, 0, -0.707}};
        for (double[] d : dirs) {
            Vec3 dir = new Vec3(d[0], d[1], d[2]);
            parts.add(spike(c.add(dir.scale(rr * 0.9)), c.add(dir.scale(rr + 0.13)), 0.028F));
        }
        return new Shape(parts, Mode.HELD, new Vec3(0, 0.98, 0.27), new Vec3(0, 0.98, -0.27));
    }

    // ── Ki Hammer ───────────────────────────────────────────────────────────
    // DOS manos. Mango largo con empuñaduras, cabeza de martillo (cilindro cruzado con bridas en los
    // extremos) y púa en la coronilla. El más lento, el que más pega y empuja.

    private static Shape hammerShape() {
        final float yh = 1.22F;
        return new Shape(List.of(
                pipe(-0.45F, 1.26F, 0.04F, 0.04F),
                pipe(-0.52F, -0.45F, 0.06F, 0.06F),
                ring(new Vec3(0, 0.12F, 0), 0.06F, 1, 0.014F),
                ring(new Vec3(0, 0.34F, 0), 0.06F, 1, 0.014F),
                ring(new Vec3(0, 0.58F, 0), 0.06F, 1, 0.014F),
                span(new Vec3(0, yh, -0.30), new Vec3(0, yh, 0.30), 0.13F),
                span(new Vec3(0, yh, 0.30), new Vec3(0, yh, 0.37), 0.17F),
                span(new Vec3(0, yh, -0.37), new Vec3(0, yh, -0.30), 0.17F),
                ring(new Vec3(0, yh, 0.20), 0.15F, 2, 0.012F),
                ring(new Vec3(0, yh, -0.20), 0.15F, 2, 0.012F),
                spike(new Vec3(0, yh + 0.10, 0), new Vec3(0, yh + 0.40, 0), 0.05F)),
                Mode.HELD, new Vec3(0, yh, 0.37), new Vec3(0, yh, -0.37));
    }

    // ── Ki Shield ───────────────────────────────────────────────────────────
    // MANO SECUNDARIA. Una placa redonda delante del antebrazo (normal +Y, el frente cuando el brazo
    // cuelga): aro exterior, aro interior, borde con relieve, ombligo y ocho radios. El tamaño se
    // dice en radios para poder escalarlo en un solo sitio.

    private static Shape shieldShape() {
        final Vec3 c = new Vec3(0, 0.16, 0.26);
        final float rOut = 0.34F;
        List<Part> parts = new ArrayList<>(List.of(
                ring(c, rOut, 1, 0.022F),
                ring(c.add(0, 0.04, 0), rOut * 0.88F, 1, 0.012F),
                ring(c, rOut * 0.62F, 1, 0.012F),
                ring(c, 0.07F, 1, 0.022F)));
        for (int i = 0; i < 8; i++) {
            double ang = Math.PI * 2.0 * i / 8;
            Vec3 dir = new Vec3(Math.cos(ang), 0, Math.sin(ang));
            parts.add(span(c.add(dir.scale(0.07)), c.add(dir.scale(rOut)), 0.008F));
        }
        return new Shape(parts, Mode.SHIELD, null, null);
    }

    /** 0..1 con aceleración suave a lo largo de la aparición. */
    public static float summonScale(Player player, float partialTick) {
        float age = KiWeaponState.clientAge(player.getId(), player.level().getGameTime(), partialTick);
        float t = Mth.clamp(age / SUMMON_TICKS, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    /**
     * Ruta del MUNDO: encola el arma con la pila YA en el marco de ítem. Solo dentro de la ventana
     * de render del mundo (si no, se estarían guardando coordenadas de pantalla de la GUI).
     * Dibuja lo que corresponda a ESTE brazo: el arma en el principal, el escudo en el otro.
     */
    public static void queue(PoseStack pose, Player player, float partialTick, HumanoidArm arm) {
        String variant = KiWeaponState.variantForArm(player, arm);
        if (variant == null || !PlayerHandTracker.capturing()) return;

        Vector3f o = pose.last().pose().transformPosition(0F, 0F, 0F, new Vector3f());
        int rgb = AuraColors.resolve(player) & 0xFFFFFF;
        float s = Math.max(0.02F, summonScale(player, partialTick));
        KiWeaponCalibration cal = KiWeaponCalibration.of(false, arm);
        KiVfxFrameQueue.submit(pose, new Vec3(o.x(), o.y(), o.z()),
                (ps, buf) -> draw(ps, buf, variant, rgb, s, true, cal));

        // Estela del swing: se muestrea el filo de ESTE frame y se encola el trozo vivo.
        Shape shape = shapeOf(variant);
        if (shape.trailTip() != null && arm == player.getMainArm()) {
            Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            Vec3[] edge = trailEdge(pose, shape, s, true, cal);
            float now = player.level().getGameTime() + partialTick;
            if (player.getAttackAnim(partialTick) > 0.0F) {
                KiWeaponTrail.add(player.getId(), edge[0].add(cam), edge[1].add(cam), now);
            }
            KiWeaponTrail.submitWorld(player.getId(), now, cam, rgb);
        }
    }

    /** Ruta de la MANO en 1ª persona: se dibuja ahora, en el espacio de la pasada de mano. */
    public static void drawNow(PoseStack pose, MultiBufferSource buffers, Player player, float partialTick,
                               HumanoidArm arm) {
        String variant = KiWeaponState.variantForArm(player, arm);
        if (variant == null || !(buffers instanceof MultiBufferSource.BufferSource src)) return;
        int rgb = AuraColors.resolve(player) & 0xFFFFFF;
        float s = Math.max(0.02F, summonScale(player, partialTick));
        KiWeaponCalibration cal = KiWeaponCalibration.of(true, arm);
        draw(pose, src, variant, rgb, s, false, cal);

        Shape shape = shapeOf(variant);
        if (shape.trailTip() != null && arm == player.getMainArm()) {
            Vec3[] edge = trailEdge(pose, shape, s, false, cal);
            float now = player.level().getGameTime() + partialTick;
            if (player.getAttackAnim(partialTick) > 0.0F) KiWeaponTrail.addHand(edge[0], edge[1], now);
            KiWeaponTrail.drawHand(src, now, rgb);
        }
    }

    /** Aplica a {@code ps} la calibración, la inclinación, la escala y el giro propios de la forma. */
    private static void applyFrame(PoseStack ps, Shape shape, float summon, boolean world, KiWeaponCalibration cal) {
        // Calibración manual (/zenkaiweapon): un juego por ruta, ver KiWeaponCalibration.
        ps.translate(cal.x, cal.y, cal.z);
        ps.mulPose(Axis.XP.rotationDegrees(cal.rx));
        ps.mulPose(Axis.YP.rotationDegrees(cal.ry));
        ps.mulPose(Axis.ZP.rotationDegrees(cal.rz));
        if (shape.mode() == Mode.HELD) ps.mulPose(Axis.XP.rotationDegrees(-tiltDegrees));
        // El FOV de la pasada de mano agranda lo que se empuña; la Blade (funda sobre el brazo) queda a 1:1.
        float s = summon * cal.s * (!world && shape.mode() != Mode.ALONG_ARM ? firstPersonScale : 1.0F);
        ps.scale(s, s, s);
        // Las armas "a lo largo del brazo" (la Blade) se construyen con +Y = punta y -Y = codo. En el
        // marco de ítem el brazo va a lo largo de Z (+Z = hombro, -Z = hacia abajo con el brazo
        // colgando), el ancho de la hoja va en X y su grosor en Y: Y local pasa a -Z, Z local a -X
        // y X local a +Y.
        if (shape.mode() == Mode.ALONG_ARM) {
            ps.mulPose(Axis.ZP.rotationDegrees(90.0F));
            ps.mulPose(Axis.XP.rotationDegrees(-90.0F));
        }
    }

    /** Los dos bordes de la estela ({punta, base}) en el espacio de {@code pose} (cámara o mano). */
    private static Vec3[] trailEdge(PoseStack pose, Shape shape, float summon, boolean world, KiWeaponCalibration cal) {
        PoseStack tmp = new PoseStack();
        tmp.last().pose().set(pose.last().pose());
        tmp.last().normal().set(pose.last().normal());
        applyFrame(tmp, shape, summon, world, cal);
        Vector3f t = tmp.last().pose().transformPosition(
                (float) shape.trailTip().x, (float) shape.trailTip().y, (float) shape.trailTip().z, new Vector3f());
        Vector3f b = tmp.last().pose().transformPosition(
                (float) shape.trailBase().x, (float) shape.trailBase().y, (float) shape.trailBase().z, new Vector3f());
        return new Vec3[]{new Vec3(t.x(), t.y(), t.z()), new Vec3(b.x(), b.y(), b.z())};
    }

    private static void draw(PoseStack ps, MultiBufferSource.BufferSource buf, String variant,
                             int rgb, float summon, boolean world, KiWeaponCalibration cal) {
        if (!KiVfxRenderTypes.available()) return;
        float r = ((rgb >> 16) & 0xFF) / 255F, g = ((rgb >> 8) & 0xFF) / 255F, b = (rgb & 0xFF) / 255F;

        ps.pushPose();
        Shape design = shapeOf(variant);
        applyFrame(ps, design, summon, world, cal);

        KiVfxColors.begin(-1);
        if (world) {
            // El shader orienta el hervor a lo largo del eje de la hoja, en ejes de MUNDO: la pila
            // de la ruta de mundo ya es de ejes de mundo, así que basta transformar +Y.
            Vector3f axis = ps.last().pose().transformDirection(0F, 1F, 0F, new Vector3f());
            KiVfxRenderTypes.setAxis(axis.x(), axis.y(), axis.z());
        } else {
            KiVfxRenderTypes.resetAxis();
        }
        for (Part part : design.parts()) {
            if (part.path() == null) continue;
            ps.pushPose();
            ps.scale(part.flat(), 1F, 1F);
            KiVfxCompositeRenderer.renderColumn(buf, part.profile(), ps, part.path(),
                    part.tipRadius(), part.rootMul(), r, g, b, 1F, false, 1F);
            ps.popPose();
        }
        KiVfxRenderTypes.resetAxis();
        if (showAxes) drawAxes(ps, buf);
        ps.popPose();
    }

    /** Ejes del marco de la pieza: X rojo, Y verde (hacia donde apunta la hoja), Z azul. */
    private static void drawAxes(PoseStack ps, MultiBufferSource.BufferSource buf) {
        VertexConsumer vc = buf.getBuffer(RenderType.lines());
        PoseStack.Pose pose = ps.last();
        float len = 0.6F;
        axis(vc, pose, len, 0, 0, 1F, 0.1F, 0.1F);
        axis(vc, pose, 0, len, 0, 0.1F, 1F, 0.1F);
        axis(vc, pose, 0, 0, len, 0.2F, 0.4F, 1F);
        buf.endBatch(RenderType.lines());
    }

    private static void axis(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z,
                             float r, float g, float b) {
        float nx = Math.signum(x), ny = Math.signum(y), nz = Math.signum(z);
        vc.addVertex(pose, 0F, 0F, 0F).setColor(r, g, b, 1F).setNormal(pose, nx, ny, nz);
        vc.addVertex(pose, x, y, z).setColor(r, g, b, 1F).setNormal(pose, nx, ny, nz);
    }
}
