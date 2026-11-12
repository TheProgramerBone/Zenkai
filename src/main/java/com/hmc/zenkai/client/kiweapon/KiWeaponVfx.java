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
     *  las agranda. */
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
        return SHAPES.computeIfAbsent(variant == null ? "ki_sword" : variant, v -> switch (v) {
            case "ki_scythe" -> scytheShape();
            case "ki_axe" -> axeShape();
            case "ki_mace" -> maceShape();
            case "ki_hammer" -> hammerShape();
            case "ki_shield" -> shieldShape();
            // La Ki Blade se quitó el 2026-10-15 (al usuario no le gustaba el arma): un id desconocido cae en la espada.
            default -> swordShape();
        });
    }

    // ── Piezas básicas ──────────────────────────────────────────────────────
    // Medidas tomadas de las armas de DragonMineZ (geo/weapons/*.geo.json, px/16 = bloques): hoja de
    // espada 3 px de ancho y ~25 de largo, katana 2 px de ancho, guarda de 11 px, hoja de ki de 7 px
    // de ancho que sale 0,6-0,75 bloques por delante del puño y cubre muñeca y antebrazo.

    /** Separación entre anillos de los tubos de las armas (ver KiVfxGeometry.tubePath con ringStep). */
    private static final float WEAPON_RING_STEP = 0.03F;

    private static KiVfxGeometry.TubePath weaponPath(List<Vec3> pts, float trimTip) {
        return KiVfxGeometry.tubePath(pts, trimTip, WEAPON_RING_STEP);
    }

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
        return weaponPath(pts, 0F);
    }

    /** Tubo redondo de y0 a y1 cuyo radio pasa de {@code rRoot} (en y0) a {@code rTip} (en y1). */
    private static Part pipe(float y0, float y1, float rRoot, float rTip) {
        return new Part(line(y0, y1), rTip, rRoot / rTip, 1.0F);
    }

    /** Hoja plana: cuerpo [y0, y1] de radio r (curvado zBody) y punta que se afila hasta yTip.
     *  UN solo tubo con perfil de radio (recto en el cuerpo, afilado en la punta): con dos tubos empalmados la normal y
     *  la UV saltaban en la junta y salía una costura gris de través (espada en 1ª persona, 2026-10-15). */
    private static List<Part> blade(float y0, float y1, float zBody, float r, float yTip, float zTip,
                                    float flat, KiVfxProfile profile) {
        List<Vec3> pts = new ArrayList<>();
        int nb = Math.max(3, Math.round(Math.abs(y1 - y0) / 0.1F) + 1);
        for (int i = 0; i < nb; i++) {
            float t = i / (float) (nb - 1);
            pts.add(new Vec3(0, Mth.lerp(t, y0, y1), zBody * t * t));
        }
        int nt = Math.max(3, Math.round((yTip - y1) / 0.1F) + 1);
        for (int i = 1; i < nt; i++) {
            float t = i / (float) (nt - 1);
            pts.add(new Vec3(0, Mth.lerp(t, y1, yTip), zBody + zTip * t * t));
        }
        final float tipMul = 0.008F / r;
        KiVfxGeometry.TubePath path = weaponPath(pts, 0F);
        if (path == null) return List.of();
        // Distancia de la raíz al final del cuerpo a lo largo del camino (aprox. por la recta: las curvas son suaves).
        final float bodyLen = (float) Math.sqrt((y1 - y0) * (y1 - y0) + zBody * zBody);
        path.withRadiusProfile((sArc, len) -> {
            if (sArc <= bodyLen) return 1F;
            float u = Mth.clamp((sArc - bodyLen) / Math.max(1.0e-4F, len - bodyLen), 0F, 1F);
            return Mth.lerp(u, 1F, tipMul);
        });
        return List.of(new Part(path, r, 1.0F, flat, profile));
    }

    /** Guarda: travesaño a lo largo de Z (el ANCHO de la hoja, como en una espada de verdad) con las
     *  puntas levantadas {@code up} hacia la punta del arma. */
    private static Part guard(float y, float half, float up, float radius) {
        List<Vec3> pts = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            float t = i / 6F * 2F - 1F;
            pts.add(new Vec3(0, y + up * t * t, half * t));
        }
        return new Part(weaponPath(pts, 0F), radius, 1.0F, 1.0F);
    }

    /** Tubo de radio constante entre dos puntos cualesquiera. */
    private static Part span(Vec3 a, Vec3 b, float radius) {
        return new Part(weaponPath(new ArrayList<>(List.of(a, b)), 0F), radius, 1.0F, 1.0F);
    }

    /** Púa: tubo que se afila de {@code rRoot} en {@code a} a punta en {@code b}. */
    private static Part spike(Vec3 a, Vec3 b, float rRoot) {
        return new Part(weaponPath(new ArrayList<>(List.of(a, b)), 0F), 0.008F, rRoot / 0.008F, 1.0F);
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
        return new Part(weaponPath(pts, 0F), tube, 1.0F, 1.0F);
    }

    private static List<Part> join(List<Part> a, List<Part> b) {
        List<Part> out = new ArrayList<>(a);
        out.addAll(b);
        return out;
    }

    private static List<Part> join(List<Part> a, Part... more) {
        return join(a, List.of(more));
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
        return new Part(weaponPath(arc, 0F), 0.008F, rootR / 0.008F, BLADE_FLAT);
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
    // Una mano. Mango con pomo, una HOJA grande en media luna hacia -Z (el filo mira ABAJO con el
    // brazo colgando, como la hoja de la guadaña; antes estaba hacia +Z y salía invertida) y un contrapeso corto en el lado
    // contrario, rematada en una púa. Es la que rompe guardias (guard_break del JSON).

    private static Shape axeShape() {
        final float top = 0.95F, yc = top + 0.10F;
        final float flat = 0.30F;
        List<Vec3> big = new ArrayList<>(List.of(new Vec3(0, yc, 0), new Vec3(0, yc - 0.03, -0.22),
                new Vec3(0, yc - 0.13, -0.44)));
        List<Vec3> back = new ArrayList<>(List.of(new Vec3(0, yc, 0), new Vec3(0, yc - 0.02, 0.12),
                new Vec3(0, yc - 0.07, 0.22)));
        return new Shape(List.of(
                pipe(-0.30F, top + 0.12F, 0.032F, 0.032F),
                pipe(-0.37F, -0.30F, 0.052F, 0.052F),
                pipe(0.10F, 0.16F, 0.048F, 0.048F),
                pipe(top + 0.12F, top + 0.42F, 0.042F, 0.008F),
                new Part(weaponPath(big, 0F), 0.010F, 0.16F / 0.010F, flat, KiVfxProfile.KI_WEAPON_JAGGED),
                new Part(weaponPath(back, 0F), 0.010F, 0.075F / 0.010F, flat)),
                Mode.HELD, new Vec3(0, yc - 0.13, -0.44), new Vec3(0, yc, 0));
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

    /** Centro de la placa respecto del eje del brazo (antes de girarla de lado): hacia delante lo que luego será el lado
     *  exterior, y a media altura del antebrazo (Z + = hacia el hombro). */
    private static final Vec3 SHIELD_CENTER = new Vec3(0, 0.17, 0.30);
    /** Hueco entre la cara exterior del brazo y la placa (el aro tiene grosor). */
    private static final float SHIELD_GAP = 0.11F;

    private static Shape shieldShape() {
        final Vec3 c = SHIELD_CENTER;
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

    /** Cuánto crece la placa del escudo al bloquear (+25 %). */
    private static final float SHIELD_BLOCK_GROW = 0.25F;

    private static final java.util.Map<Integer, Float> SHIELD_BLOCK = new java.util.HashMap<>();
    private static long shieldLastNanos = 0L;

    /** 0 = sin bloquear, 1 = bloqueando, con transición de ~0,15 s. Se avanza una vez por fotograma (el reloj se
     *  compara con la última llamada, que es de otro jugador o del mismo en el fotograma anterior). */
    private static float shieldBlockAmount(Player p, boolean blocking) {
        long now = System.nanoTime();
        float dt = shieldLastNanos == 0L ? 0F : Math.min(0.1F, (now - shieldLastNanos) / 1.0e9F);
        // El reloj solo avanza en la primera llamada de cada fotograma (entre llamadas hay microsegundos).
        if (dt > 0.002F) shieldLastNanos = now; else if (shieldLastNanos == 0L) shieldLastNanos = now;
        float cur = SHIELD_BLOCK.getOrDefault(p.getId(), 0F);
        float step = Math.max(dt, 1F / 60F) / 0.15F;
        cur = blocking ? Math.min(1F, cur + step) : Math.max(0F, cur - step);
        SHIELD_BLOCK.put(p.getId(), cur);
        return cur;
    }

    private static boolean isBlocking(Player p) {
        return p == Minecraft.getInstance().player
                ? com.hmc.zenkai.client.CombatModeClientState.isBlockingLocal()
                : com.hmc.zenkai.client.CombatModeClientState.isBlockingRemote(p.getId());
    }

    /** Opacidad del halo de Ki Infuse sobre el arma. */
    private static final float INFUSE_GLOW_ALPHA = 0.30F;

    /** ¿Brilla el arma de este jugador con su aura por Ki Infuse? Interruptor encendido, algo en la mano principal
     *  (arma de ki o ítem con daño) y la opción de cliente sin apagar. */
    public static boolean infuseGlowActive(Player p) {
        if (!com.hmc.zenkai.config.ClientConfig.kiInfuseGlow()) return false;
        if (!com.hmc.zenkai.feature.skills.SkillToggles.isOn(p, com.hmc.zenkai.feature.skills.SkillEffects.KI_INFUSE)) return false;
        return KiWeaponState.variantForArm(p, p.getMainArm()) != null || heldItemIsWeapon(p);
    }

    /**
     * ¿Lo que lleva en la mano principal cuenta como arma? Se mira el PROPIO ítem y no
     * {@code KiInfusion.hasWeapon} (atributo ATTACK_DAMAGE): en el CLIENTE ese atributo se queda en 1.0 con una espada
     * de diamante en la mano (el servidor sí marca 7.0; medido con FpWeaponShots 2026-10-20), así que el halo nunca se
     * activaba en ningún ítem corriente. Los modificadores del ítem sí están en el stack en ambos lados.
     */
    private static boolean heldItemIsWeapon(Player p) {
        net.minecraft.world.item.ItemStack st = p.getMainHandItem();
        if (st.isEmpty()) return false;
        final double[] dmg = {0.0};
        st.forEachModifier(net.minecraft.world.entity.EquipmentSlot.MAINHAND, (attr, mod) -> {
            if (attr.equals(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)
                    && mod.operation() == net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE) {
                dmg[0] += mod.amount();
            }
        });
        return dmg[0] > 1.0e-6 || com.hmc.zenkai.feature.combat.KiInfusion.hasWeapon(p);
    }

    /** Pulso lento del halo (0.75..1). */
    private static float glowPulse(Player p, float partialTick) {
        return 0.875F + 0.125F * Mth.sin((p.tickCount + partialTick) * 0.25F);
    }

    /** Opacidad del halo sobre un ítem corriente: más alta que la de las armas de ki (su silueta es más fina). */
    private static final float ITEM_GLOW_ALPHA = 0.5F;

    /** Cuánto se aleja el halo de la cámara (bloques): más que el grosor del ítem, para que este lo tape por delante. */
    private static final float GLOW_PUSH_BACK = 0.03F;

    private static boolean inFirstPersonGlow;

    /**
     * Halo sobre el ítem de la mano principal en 1ª PERSONA. Lo llama un mixin al final de
     * {@code ItemInHandRenderer.renderItem}, con la pila ya en el marco exacto del ítem; la pasada de mano usa render
     * types de vanilla, que Iris sí sustituye, así que no necesita el rodeo de {@link #flushPendingHands}. Las armas de
     * ki llevan su propio halo y no pasan por aquí.
     */
    public static void firstPersonItemGlow(net.minecraft.world.entity.LivingEntity entity,
                                           net.minecraft.world.item.ItemStack stack,
                                           net.minecraft.world.item.ItemDisplayContext ctx, boolean left,
                                           PoseStack pose, MultiBufferSource buffer) {
        if (inFirstPersonGlow || !ctx.firstPerson() || stack.isEmpty()) return;
        if (!(entity instanceof Player p) || p != Minecraft.getInstance().player || stack != p.getMainHandItem()) return;
        if (KiWeaponState.variantForArm(p, p.getMainArm()) != null) return;
        boolean infuse = infuseGlowActive(p);
        boolean ue = destructionLayerActive(p);
        if (!infuse && !ue) return;
        float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        int rgb = AuraColors.resolve(p) & 0xFFFFFF;
        final float a = Math.min(1F, ITEM_GLOW_ALPHA * glowPulse(p, partial) * 1.4F);
        inFirstPersonGlow = true;   // collectItem vuelve a pasar por renderItem: sin esto el mixin se llamaría a sí mismo
        try {
            GlowCollector col = collectItem(pose, p, stack, ctx, left);
            if (infuse) {
                col.emit(buffer, 1.14F, rgb, a * 0.3F);
                col.emit(buffer, 1.06F, rgb, a * 0.8F);
            }
            if (ue) col.emit(buffer, infuse ? 1.22F : 1.14F, com.hmc.zenkai.client.fx.FistEnergyFx.UE_TINT, 0.40F);
        } finally {
            inFirstPersonGlow = false;
        }
    }

    /**
     * Halo de Ki Infuse (y capa morada de Ultra Ego) sobre un ÍTEM corriente en la mano principal: la silueta de su
     * clase ({@link #itemParts}) en el color del aura, más una segunda pasada magenta si Ultra Ego está activo.
     */
    public static void queueItemGlow(PoseStack pose, Player player, float partialTick) {
        if (!PlayerHandTracker.capturing()) return;
        net.minecraft.world.item.ItemStack stack = player.getMainHandItem();
        boolean infuse = infuseGlowActive(player);
        boolean ue = destructionLayerActive(player);
        if (!infuse && !ue) return;
        Vector3f o = pose.last().pose().transformPosition(0F, 0F, 0F, new Vector3f());
        int rgb = AuraColors.resolve(player) & 0xFFFFFF;
        float alpha = ITEM_GLOW_ALPHA * glowPulse(player, partialTick);
        // RENDER UNIVERSAL: se vuelve a dibujar el MISMO modelo del ítem (el que sea, también de otros mods) agrandado
        // alrededor de su centro, teñido y translúcido, como un resplandor. Pase "entity translucent emissive": respeta la
        // transparencia de la textura también con shaderpack (el pase "eyes" pintaba el cuadrado entero del sprite).
        final float a = Math.min(1F, alpha * 1.4F);
        KiVfxFrameQueue.submit(pose, new Vec3(o.x(), o.y(), o.z()), (ps, buf) -> {
            GlowCollector col = collectItem(ps, player, stack);
            if (infuse) {
                col.emit(buf, 1.14F, rgb, a * 0.3F);
                col.emit(buf, 1.06F, rgb, a * 0.8F);
            }
            if (ue) col.emit(buf, infuse ? 1.22F : 1.14F,
                    com.hmc.zenkai.client.fx.FistEnergyFx.UE_TINT, 0.40F);
        });
    }

    /** Dibuja el ítem de la mano principal en un recolector (sin pintar nada todavía). */
    private static GlowCollector collectItem(PoseStack ps, Player player, net.minecraft.world.item.ItemStack stack) {
        boolean left = player.getMainArm() == HumanoidArm.LEFT;
        return collectItem(ps, player, stack, left
                ? net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                : net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, left);
    }

    private static GlowCollector collectItem(PoseStack ps, Player player, net.minecraft.world.item.ItemStack stack,
                                             net.minecraft.world.item.ItemDisplayContext ctx, boolean left) {
        GlowCollector col = new GlowCollector();
        // El brillo de encantamiento (glint) se descarta: duplicaría la geometría dentro del recolector.
        Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer().renderItem(
                player, stack, ctx, left, ps,
                t -> t.toString().contains("glint") ? NULL_CONSUMER : col, net.minecraft.client.renderer.LightTexture.FULL_BRIGHT);
        return col;
    }

    /** Guarda los vértices del ítem para volver a emitirlos agrandados alrededor de su centroide. */
    private static final class GlowCollector implements com.mojang.blaze3d.vertex.VertexConsumer {
        private final java.util.List<float[]> verts = new ArrayList<>();   // x y z u v nx ny nz
        private float[] cur;

        @Override public com.mojang.blaze3d.vertex.VertexConsumer addVertex(float x, float y, float z) {
            cur = new float[]{x, y, z, 0, 0, 0, 1, 0};
            verts.add(cur);
            return this;
        }
        @Override public com.mojang.blaze3d.vertex.VertexConsumer setColor(int r, int g, int b, int a) { return this; }
        @Override public com.mojang.blaze3d.vertex.VertexConsumer setUv(float u, float v) {
            if (cur != null) { cur[3] = u; cur[4] = v; }
            return this;
        }
        @Override public com.mojang.blaze3d.vertex.VertexConsumer setUv1(int u, int v) { return this; }
        @Override public com.mojang.blaze3d.vertex.VertexConsumer setUv2(int u, int v) { return this; }
        @Override public com.mojang.blaze3d.vertex.VertexConsumer setNormal(float x, float y, float z) {
            if (cur != null) { cur[5] = x; cur[6] = y; cur[7] = z; }
            return this;
        }

        void emit(MultiBufferSource buf, float scale, int rgb, float alpha) {
            if (verts.isEmpty()) return;
            float cx = 0, cy = 0, cz = 0;
            for (float[] v : verts) { cx += v[0]; cy += v[1]; cz += v[2]; }
            cx /= verts.size(); cy /= verts.size(); cz /= verts.size();
            // Silueta plana del color del aura (atlas blanco); si no se pudo construir, el atlas real teñido.
            net.minecraft.resources.ResourceLocation sil = ItemSilhouetteAtlas.get();
            net.minecraft.client.renderer.RenderType rt = net.minecraft.client.renderer.RenderType.entityTranslucentEmissive(
                    sil != null ? sil : net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS);
            com.mojang.blaze3d.vertex.VertexConsumer vc = buf.getBuffer(rt);
            int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF, a = (int) (Mth.clamp(alpha, 0F, 1F) * 255F);
            // Se empuja hacia atrás (lejos de la cámara) para que el ítem, que ya escribió su profundidad, tape el halo
            // por delante y solo asome el contorno: sin esto, visto de canto (3ª persona) el halo tapaba la hoja entera.
            org.joml.Vector3f look = Minecraft.getInstance().gameRenderer.getMainCamera().getLookVector();
            float px = look.x() * GLOW_PUSH_BACK, py = look.y() * GLOW_PUSH_BACK, pz = look.z() * GLOW_PUSH_BACK;
            for (float[] v : verts) {
                vc.addVertex(cx + (v[0] - cx) * scale + px, cy + (v[1] - cy) * scale + py, cz + (v[2] - cz) * scale + pz)
                        .setColor(r, g, b, a).setUv(v[3], v[4]).setUv1(0, 10).setUv2(240, 240)
                        .setNormal(v[5], v[6], v[7]);
            }
            if (buf instanceof MultiBufferSource.BufferSource bs) bs.endBatch(rt);
        }
    }

    private static final com.mojang.blaze3d.vertex.VertexConsumer NULL_CONSUMER = new com.mojang.blaze3d.vertex.VertexConsumer() {
        @Override public com.mojang.blaze3d.vertex.VertexConsumer addVertex(float x, float y, float z) { return this; }
        @Override public com.mojang.blaze3d.vertex.VertexConsumer setColor(int r, int g, int b, int a) { return this; }
        @Override public com.mojang.blaze3d.vertex.VertexConsumer setUv(float u, float v) { return this; }
        @Override public com.mojang.blaze3d.vertex.VertexConsumer setUv1(int u, int v) { return this; }
        @Override public com.mojang.blaze3d.vertex.VertexConsumer setUv2(int u, int v) { return this; }
        @Override public com.mojang.blaze3d.vertex.VertexConsumer setNormal(float x, float y, float z) { return this; }
    };

    /** ¿Lleva el arma la capa de la destrucción? Ultra Ego activo + algo empuñado en la mano principal (decisión del
     *  usuario 2026-10-14: «imbuido con la energía de la destrucción»). Se apaga junto al halo de Ki Infuse. */
    public static boolean destructionLayerActive(Player p) {
        if (!com.hmc.zenkai.config.ClientConfig.kiInfuseGlow()) return false;
        if (!com.hmc.zenkai.client.fx.FistEnergyFx.ultraEgoActive(p)) return false;
        return KiWeaponState.variantForArm(p, p.getMainArm()) != null || !p.getMainHandItem().isEmpty();
    }

    private static void glowPass(PoseStack ps, MultiBufferSource.BufferSource buf, List<Part> parts, float grow,
                                 int rgb, float alpha, KiVfxProfile profile) {
        float r = ((rgb >> 16) & 0xFF) / 255F, g = ((rgb >> 8) & 0xFF) / 255F, b = (rgb & 0xFF) / 255F;
        for (Part part : parts) {
            if (part.path() == null) continue;
            ps.pushPose();
            ps.scale(part.flat(), 1F, 1F);
            setPartAxis(ps, part);
            KiVfxCompositeRenderer.renderColumn(buf, profile, ps, part.path(), part.tipRadius() * grow,
                    part.rootMul(), r, g, b, alpha, false, 1F);
            ps.popPose();
        }
    }

    /** 0..1 con aceleración suave a lo largo de la aparición. Incluye el ocultado al cargar ki
     *  ({@link #kiChargeVisibility}). */
    public static float summonScale(Player player, float partialTick) {
        float age = KiWeaponState.clientAge(player.getId(), player.level().getGameTime(), partialTick);
        float t = Mth.clamp(age / SUMMON_TICKS, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t) * kiChargeVisibility(player, partialTick);
    }

    /** Ticks que el arma sigue oculta después de soltar una técnica de ki (el gesto del disparo). */
    private static final float KI_HIDE_AFTER = 8.0F;
    /** entityId -> último instante (gameTime + parcial) en que se le vio cargando ki. */
    private static final java.util.Map<Integer, Float> KI_CHARGE_SEEN = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 0..1: con un arma o escudo de ki se pueden lanzar técnicas de ki (decisión del usuario 2026-10-15), y mientras
     * se cargan el arma y el escudo se OCULTAN (0) para que no estorben a las manos. Al soltar siguen ocultos
     * {@link #KI_HIDE_AFTER} ticks y luego vuelven a crecer desde el puño como al invocarlos.
     */
    public static float kiChargeVisibility(Player p, float partialTick) {
        float now = p.level().getGameTime() + partialTick;
        boolean charging = com.hmc.zenkai.client.KiChargeClientState.of(p) != null
                || (p == Minecraft.getInstance().player && com.hmc.zenkai.client.CombatModeClientState.isCharging());
        if (charging) {
            KI_CHARGE_SEEN.put(p.getId(), now);
            return 0.0F;
        }
        Float last = KI_CHARGE_SEEN.get(p.getId());
        if (last == null) return 1.0F;
        float t = (now - last - KI_HIDE_AFTER) / SUMMON_TICKS;
        if (t >= 1.0F || now < last) { KI_CHARGE_SEEN.remove(p.getId()); return 1.0F; }
        if (t <= 0.0F) return 0.0F;
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
        if (kiChargeVisibility(player, partialTick) <= 0.0F) return;   // oculta mientras carga ki

        Vector3f o = pose.last().pose().transformPosition(0F, 0F, 0F, new Vector3f());
        int rgb = AuraColors.resolve(player) & 0xFFFFFF;
        float s = Math.max(0.02F, summonScale(player, partialTick));
        KiWeaponCalibration cal = KiWeaponCalibration.of(false, arm);
        float glow = arm == player.getMainArm() && infuseGlowActive(player)
                ? INFUSE_GLOW_ALPHA * glowPulse(player, partialTick) : 0F;
        boolean ue = arm == player.getMainArm() && destructionLayerActive(player);
        // Escudo de LADO, centrado en el brazo (decisión del usuario 2026-10-15): la placa gira alrededor del EJE del
        // brazo y queda en su cara exterior. Al bloquear se queda en esa misma posición y solo crece un poco (el
        // clip block_shield levanta el brazo, la placa va con él).
        final boolean isShield = shapeOf(variant).mode() == Mode.SHIELD;
        final float blockAmt = isShield ? shieldBlockAmount(player, isBlocking(player)) : 0F;
        final boolean leftArm = arm == HumanoidArm.LEFT;
        // El marco de ítem vanilla ya está en el CENTRO del brazo en X (translate ±1/16) y 2 px por delante de su eje
        // (0.125): se deshace solo esa parte. Se adapta al brazo del MODELO que se ve (geo racial): +X del marco es la
        // derecha del jugador, así que el centro del brazo, cx px a la derecha del pivote, está en -cx; el vanilla
        // ya cuenta con ±1 px. La placa va hacia fuera lo que mide el brazo + un hueco, y a media altura de SU largo
        // (el marco arranca en la punta del brazo vanilla, 10 px bajo el pivote).
        final com.hmc.zenkai.client.fx.ArmGeometry.Arm geo =
                com.hmc.zenkai.client.fx.ArmGeometry.of(player, arm == HumanoidArm.RIGHT);
        final float axisX = -(geo.cx() - (leftArm ? 1F : -1F)) / 16F;
        final float lateral = geo.hx() / 16F * 0.9375F + SHIELD_GAP;
        final float midZ = (10F - geo.tip() / 2F) / 16F;
        final float sideRot = leftArm ? 90F : -90F;
        final float grow = 1F + SHIELD_BLOCK_GROW * blockAmt;
        KiVfxFrameQueue.submit(pose, new Vec3(o.x(), o.y(), o.z()),
                (ps, buf) -> {
                    if (isShield) {
                        ps.translate(axisX, -0.125F, 0F);
                        ps.mulPose(Axis.ZP.rotationDegrees(sideRot));
                        // Ya girado, +Y local es hacia fuera: se mueve la placa a su sitio según el brazo.
                        ps.translate(0F, lateral - (float) SHIELD_CENTER.y, midZ - (float) SHIELD_CENTER.z);
                        // Crece alrededor del centro de la placa, no del eje.
                        ps.translate(SHIELD_CENTER.x, SHIELD_CENTER.y, SHIELD_CENTER.z);
                        ps.scale(grow, grow, grow);
                        ps.translate(-SHIELD_CENTER.x, -SHIELD_CENTER.y, -SHIELD_CENTER.z);
                    }
                    draw(ps, buf, variant, rgb, s, true, cal, glow, ue);
                });

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

    /** Arma de la mano que espera a dibujarse tras el compuesto de Iris (ver drawNow). */
    private record PendingHand(PoseStack viewPose, org.joml.Matrix4f proj, Player player, float partial,
                               HumanoidArm arm, String variant, org.joml.Matrix4f armView) {}
    private static final List<PendingHand> PENDING_HANDS = new java.util.ArrayList<>();

    /**
     * Ruta de la MANO en 1ª persona: se dibuja ahora, en ESPACIO DE VISTA.
     *
     * CAUSA REAL del "aspecto según el cabeceo" (arriba fina, al frente lámina blanca, abajo rayas), 2026-10-15:
     * en 1.21.1 la pasada de mano NO está en espacio de vista. {@code GameRenderer.renderItemInHand} arranca la pila
     * con la INVERSA de la rotación de cámara y pone esa rotación en {@code ModelViewMat}: los vértices salen en ejes de
     * MUNDO y el shader los gira a vista. El arma sigue a la cámara, así que su eje, sus normales y el dominio del
     * hervor (vField, en ejes de mundo) cambiaban cada vez que el jugador movía la mirada. La 3ª vuelta además le
     * aplicaba la inversa de la vista OTRA vez al eje (lo suponía en espacio de vista) y lo dejaba girado.
     * Arreglo: se multiplica la pila por la vista (queda en espacio de vista de verdad) y {@code ModelViewMat} pasa a
     * identidad mientras se dibuja el arma (cada lote del arma se vuelca dentro, con endBatch). Así el arma se ve igual
     * mire donde mire, como cualquier ítem en la mano.
     */
    public static void drawNow(PoseStack pose, MultiBufferSource buffers, Player player, float partialTick,
                               HumanoidArm arm) {
        drawNow(pose, buffers, player, partialTick, arm, null);
    }

    /**
     * {@code armFrame}: pose del brazo en su pivote de hombro (marco vanilla, antes del marco de ítem). Solo se usa
     * bajo shaderpack, para tapar con el brazo el trozo de mango que queda dentro del puño (ver flushPendingHands).
     */
    public static void drawNow(PoseStack pose, MultiBufferSource buffers, Player player, float partialTick,
                               HumanoidArm arm, org.joml.Matrix4f armFrame) {
        String variant = KiWeaponState.variantForArm(player, arm);
        if (variant == null || !(buffers instanceof MultiBufferSource.BufferSource src)) return;
        if (kiChargeVisibility(player, partialTick) <= 0.0F) return;   // oculta mientras carga ki
        org.joml.Matrix4f view = new org.joml.Matrix4f(com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix());
        PoseStack viewPose = new PoseStack();
        viewPose.last().pose().set(view).mul(pose.last().pose());
        viewPose.last().normal().set(new org.joml.Matrix3f(view)).mul(pose.last().normal());
        // SHADERPACK (Iris/Oculus, diagnosticado con FpWeaponShots 2026-10-20): la mano se dibuja ANTES del
        // compuesto del pack, que reescribe el framebuffer principal entero desde sus propias texturas, y un
        // RenderType propio (fuera del pipeline del pack) se pierde ahí: el brazo se ve y el arma no. Además la
        // mano recibe un UnflushableWrapper cuyo endBatch no vuelca. La 3ª persona sí se ve porque se dibuja en
        // AFTER_LEVEL, ya pasado el compuesto. Aquí se guarda la pose en espacio de vista con la proyección de
        // mano y se dibuja en el MISMO fotograma desde flushPendingHands (RenderGuiEvent.Pre), sin retraso.
        if (com.hmc.zenkai.compat.IrisCompat.shaderPackActive()) {
            PENDING_HANDS.add(new PendingHand(viewPose,
                    new org.joml.Matrix4f(com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix()),
                    player, partialTick, arm, variant,
                    armFrame == null ? null : new org.joml.Matrix4f(view).mul(armFrame)));
            return;
        }
        drawHandInView(viewPose, src, player, partialTick, arm, variant, null);
    }

    /** Dibuja las armas guardadas por {@link #drawNow} bajo shaderpack. Llamar al empezar el GUI. */
    public static void flushPendingHands() {
        if (PENDING_HANDS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        MultiBufferSource.BufferSource src = mc.renderBuffers().bufferSource();
        org.joml.Matrix4f guiProj = new org.joml.Matrix4f(com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix());
        com.mojang.blaze3d.vertex.VertexSorting sorting = com.mojang.blaze3d.systems.RenderSystem.getVertexSorting();
        try {
            // Como la pasada de mano de vanilla: el arma va por encima del mundo.
            com.mojang.blaze3d.systems.RenderSystem.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT,
                    net.minecraft.client.Minecraft.ON_OSX);
            for (PendingHand h : PENDING_HANDS) {
                com.mojang.blaze3d.systems.RenderSystem.setProjectionMatrix(h.proj(),
                        com.mojang.blaze3d.vertex.VertexSorting.DISTANCE_TO_ORIGIN);
                drawHandInView(h.viewPose(), src, h.player(), h.partial(), h.arm(), h.variant(), h.armView());
            }
        } finally {
            PENDING_HANDS.clear();
            com.mojang.blaze3d.systems.RenderSystem.setProjectionMatrix(guiProj, sorting);
            // El depth de la mano no debe tapar el GUI que se dibuja después.
            com.mojang.blaze3d.systems.RenderSystem.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT,
                    net.minecraft.client.Minecraft.ON_OSX);
        }
    }

    /**
     * Escribe SOLO en el depth un cajón con la forma del brazo vanilla (4x12x4 px desde el pivote del hombro). Con
     * shaderpack el brazo ya salió en el compuesto y no deja depth: sin esto el mango se vería delante del puño.
     */
    private static void drawArmOccluder(org.joml.Matrix4f m, HumanoidArm arm) {
        float x0 = (arm == HumanoidArm.LEFT ? -1F : -3F) / 16F, x1 = x0 + 4F / 16F;
        float y0 = -2F / 16F, y1 = 10F / 16F, z0 = -2F / 16F, z1 = 2F / 16F;
        com.mojang.blaze3d.systems.RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionShader);
        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
        com.mojang.blaze3d.systems.RenderSystem.depthFunc(org.lwjgl.opengl.GL11.GL_LEQUAL);
        com.mojang.blaze3d.systems.RenderSystem.depthMask(true);
        com.mojang.blaze3d.systems.RenderSystem.disableCull();
        com.mojang.blaze3d.systems.RenderSystem.colorMask(false, false, false, false);
        com.mojang.blaze3d.vertex.BufferBuilder b = com.mojang.blaze3d.vertex.Tesselator.getInstance().begin(
                com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
                com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION);
        float[][] c = {
                {x0, y0, z0}, {x1, y0, z0}, {x1, y1, z0}, {x0, y1, z0},   // -Z
                {x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}};  // +Z
        int[][] faces = {{0, 1, 2, 3}, {4, 5, 6, 7}, {0, 4, 7, 3}, {1, 5, 6, 2}, {0, 1, 5, 4}, {3, 2, 6, 7}};
        for (int[] f : faces) for (int i : f) b.addVertex(m, c[i][0], c[i][1], c[i][2]);
        com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(b.buildOrThrow());
        com.mojang.blaze3d.systems.RenderSystem.colorMask(true, true, true, true);
        com.mojang.blaze3d.systems.RenderSystem.enableCull();
    }

    /** Dibuja con {@code ModelViewMat} en identidad (la pose ya está en espacio de vista; ver el javadoc de drawNow). */
    private static void drawHandInView(PoseStack viewPose, MultiBufferSource.BufferSource src, Player player,
                                       float partialTick, HumanoidArm arm, String variant,
                                       org.joml.Matrix4f armView) {
        org.joml.Matrix4fStack mv = com.mojang.blaze3d.systems.RenderSystem.getModelViewStack();
        mv.pushMatrix().identity();
        com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
        try {
            if (armView != null) drawArmOccluder(armView, arm);
            drawNowInView(viewPose, src, player, partialTick, arm, variant);
        } finally {
            mv.popMatrix();
            com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
        }
    }

    private static void drawNowInView(PoseStack pose, MultiBufferSource.BufferSource src, Player player,
                                      float partialTick, HumanoidArm arm, String variant) {
        int rgb = AuraColors.resolve(player) & 0xFFFFFF;
        float s = Math.max(0.02F, summonScale(player, partialTick));
        KiWeaponCalibration cal = KiWeaponCalibration.of(true, arm);
        float glow = arm == player.getMainArm() && infuseGlowActive(player)
                ? INFUSE_GLOW_ALPHA * glowPulse(player, partialTick) : 0F;
        draw(pose, src, variant, rgb, s, false, cal, glow,
                arm == player.getMainArm() && destructionLayerActive(player));

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
        // El FOV de la pasada de mano agranda lo que se empuña.
        float s = summon * cal.s * (!world ? firstPersonScale : 1.0F);
        ps.scale(s, s, s);
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
                             int rgb, float summon, boolean world, KiWeaponCalibration cal, float glow, boolean ue) {
        if (!KiVfxRenderTypes.available()) return;
        float r = ((rgb >> 16) & 0xFF) / 255F, g = ((rgb >> 8) & 0xFF) / 255F, b = (rgb & 0xFF) / 255F;

        ps.pushPose();
        Shape design = shapeOf(variant);
        applyFrame(ps, design, summon, world, cal);

        KiVfxColors.begin(-1);
        // En 1ª persona (world = false) se usan los perfiles de mano (KiVfxProfile.hand: sin envoltura, hervor casi nulo, caras traseras descartadas), más el halo
        // y las tapas de abajo. La proximidad real a ~0,7 bloques de una hoja de radio ~0,08 ya da 1
        // (KiVfxRenderTypes.proximity), así que se pasa 1.
        final boolean fp = !world;
        final List<Part> parts = design.parts();
        // Mano: no hay bloom (es del mundo), y sin él el arma se lee apagada, casi de cristal (capturas 2026-10-15).
        // Halo plano a la cámara por debajo de cada pieza, ANTES que el arma para que ella quede encima.
        if (fp && handHaloEnabled) handHalo(ps, buf, parts, r, g, b);
        if (fp) handCaps(ps, buf, parts, r, g, b);
        for (Part part : parts) {
            if (part.path() == null) continue;
            ps.pushPose();
            ps.scale(part.flat(), 1F, 1F);
            setPartAxis(ps, part);
            KiVfxCompositeRenderer.renderColumn(buf, fp ? KiVfxProfile.hand(part.profile()) : part.profile(), ps,
                    part.path(), part.tipRadius(), part.rootMul(), r, g, b, 1F, false, 1F);
            ps.popPose();
        }
        // Ki Infuse: segunda pasada, la misma silueta más gruesa y translúcida (el aura del jugador pegada al arma).
        if (glow > 0F) {
            for (Part part : parts) {
                if (part.path() == null) continue;
                ps.pushPose();
                ps.scale(part.flat(), 1F, 1F);
                setPartAxis(ps, part);
                // En la mano, perfil de mano: el del mundo lleva envoltura, y a 1,7x salía como conchas grises
                // concéntricas alrededor de las piezas gruesas (cabeza del martillo, capturas 2026-10-15).
                KiVfxCompositeRenderer.renderColumn(buf, fp ? KiVfxProfile.KI_WEAPON_HAND : KiVfxProfile.KI_WEAPON, ps, part.path(),
                        part.tipRadius() * 1.7F, part.rootMul(), r, g, b, glow, false, 1F);
                ps.popPose();
            }
        }
        // Ultra Ego: tercera pasada magenta, más gruesa: el arma «imbuida con la energía de la destrucción».
        if (ue) {
            KiVfxColors.begin(com.hmc.zenkai.client.fx.FistEnergyFx.UE_HOT);
            glowPass(ps, buf, parts, glow > 0F ? 2.4F : 1.9F, com.hmc.zenkai.client.fx.FistEnergyFx.UE_TINT,
                    0.5F, fp ? KiVfxProfile.KI_WEAPON_JAGGED_HAND : KiVfxProfile.KI_WEAPON_JAGGED);
        }
        KiVfxRenderTypes.resetAxis();
        if (showAxes) drawAxes(ps, buf);
        ps.popPose();
    }

    /**
     * Eje de ESTA pieza para el shader (fresnel axial y hervor), en el MISMO espacio que los vértices (el shader lo gira
     * con ModelViewMat): mundo = ejes de mundo; mano = espacio de vista con ModelViewMat identidad (ver drawNow).
     * Antes era UN eje para el arma entera (el +Y del mango): en las piezas que no van a lo largo del mango (cabeza del
     * martillo, guarda, media luna, cabeza del hacha, púas) el fresnel se calculaba contra un eje ajeno, casi toda la
     * superficie caía en la banda de contorno y la pieza se veía hueca, gris y en capas (capturas 2026-10-15).
     */
    private static void setPartAxis(PoseStack ps, Part part) {
        Vec3 c = part.path().chord();
        Vector3f axis = ps.last().pose().transformDirection((float) c.x, (float) c.y, (float) c.z, new Vector3f());
        KiVfxRenderTypes.setAxis(axis.x(), axis.y(), axis.z());
    }

    /** Radio de extremo a partir del cual una pieza se tapa en la mano (por debajo el hueco no se ve). */
    private static final float CAP_MIN_RADIUS = 0.035F;

    /** Tapas de los extremos abiertos de las piezas gruesas, en la mano (ver KiVfxGeometry.emitEndCap). Antes que las
     *  cáscaras, para que la cáscara translúcida quede por encima. Los aros (camino cerrado) no se tapan. */
    private static void handCaps(PoseStack ps, MultiBufferSource.BufferSource buf, List<Part> parts,
                                 float r, float g, float b) {
        float[] h = KiVfxCompositeRenderer.hot(r, g, b);
        float cr = h[0] + (1F - h[0]) * 0.6F, cg = h[1] + (1F - h[1]) * 0.6F, cb = h[2] + (1F - h[2]) * 0.6F;
        RenderType type = KiVfxRenderTypes.glow(KiVfxRenderTypes.HALO_TEXTURE);
        VertexConsumer vc = buf.getBuffer(type);
        for (Part part : parts) {
            KiVfxGeometry.TubePath tp = part.path();
            if (tp == null || tp.chord().lengthSqr() < 1.0e-6 || tp.chord().equals(tp.tipTangent())) continue;
            // Las hojas aplanadas no se tapan: su hueco no se ve, y la tapa salía como una astilla blanca.
            if (part.flat() < 0.5F) continue;
            ps.pushPose();
            ps.scale(part.flat(), 1F, 1F);
            float rootR = part.tipRadius() * part.rootMul();
            // Un extremo que empalma con otra pieza (el cuerpo de una hoja con su punta) no se tapa: el disco quedaba
            // de través en mitad de la hoja como una muesca (espada, capturas 2026-10-15).
            if (rootR >= CAP_MIN_RADIUS && !joined(parts, part, false))
                KiVfxGeometry.emitEndCap(vc, ps.last(), tp, false, rootR, 0.12F, cr, cg, cb, 0.95F);
            if (part.tipRadius() >= CAP_MIN_RADIUS && !joined(parts, part, true))
                KiVfxGeometry.emitEndCap(vc, ps.last(), tp, true, part.tipRadius(), 0.12F, cr, cg, cb, 0.95F);
            ps.popPose();
        }
        buf.endBatch(type);
    }

    /** ¿Ese extremo de la pieza coincide con un extremo de otra (están empalmadas)? */
    private static boolean joined(List<Part> parts, Part self, boolean tipEnd) {
        Vec3 p = self.path().end(tipEnd);
        for (Part o : parts) {
            if (o == self || o.path() == null) continue;
            if (o.path().end(false).distanceToSqr(p) < 4.0e-4 || o.path().end(true).distanceToSqr(p) < 4.0e-4) return true;
        }
        return false;
    }

    /** Solo para comparar en las capturas de client/dev/FpWeaponShots (con y sin halo). */
    public static boolean handHaloEnabled = true;
    /** Halo de la mano: ancho = HAND_HALO_MUL radios + HAND_HALO_ADD a cada lado. ki_halo solo es visible en su
     *  40 % central, así que lo que se ve es ~0,4 de eso (unas dos veces el radio de la pieza). */
    private static final float HAND_HALO_MUL = 5.0F;
    private static final float HAND_HALO_ADD = 0.06F;
    /** Por disco: en cada punto se solapan unos cinco (ver KiVfxGeometry.emitViewHalo), así que el resplandor
     *  sale a ~0,5, el nivel que se vio bien con la cinta de la primera versión. */
    private static final float HAND_HALO_ALPHA = 0.14F;

    /** Sustituto del bloom en la mano de 1ª persona: discos de halo paralelos a la pantalla (drawNow deja la pila en
     *  espacio de vista) en el color caliente del tinte, a lo largo de cada pieza, en un solo lote. */
    private static void handHalo(PoseStack ps, MultiBufferSource.BufferSource buf, List<Part> parts,
                                 float r, float g, float b) {
        float[] h = KiVfxCompositeRenderer.hot(r, g, b);
        RenderType type = KiVfxRenderTypes.glow(KiVfxRenderTypes.HALO_TEXTURE);
        VertexConsumer vc = buf.getBuffer(type);
        for (Part part : parts) {
            if (part.path() == null) continue;
            ps.pushPose();
            ps.scale(part.flat(), 1F, 1F);
            KiVfxGeometry.emitViewHalo(vc, ps.last(), part.path(), part.tipRadius(), part.rootMul(),
                    HAND_HALO_MUL, HAND_HALO_ADD, h[0], h[1], h[2], HAND_HALO_ALPHA);
            ps.popPose();
        }
        buf.endBatch(type);
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
