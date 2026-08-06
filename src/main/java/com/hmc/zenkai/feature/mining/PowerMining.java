package com.hmc.zenkai.feature.mining;

import com.hmc.zenkai.feature.combat.KiFist;
import com.hmc.zenkai.feature.combat.KiInfusion;
import com.hmc.zenkai.feature.combat.ZenkaiCombatStats;
import com.hmc.zenkai.feature.combat.ZenkaiStats;
import com.hmc.zenkai.registry.ModBlocks;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.DeferredBlock;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Minado por PODER, no por tier de herramienta.
 * Un bloque registrado aquí no lo rompe un pico por ser de tal material: lo rompe quien
 * reúne suficiente poder de golpe. Las tres vías suman en la misma cuenta:
 *   1. STR — el melee efectivo del jugador (ya incluye forma, % de Ki Control y pesas).
 *      Es la vía principal y la única que escala sin techo con el entrenamiento.
 *   2. Herramienta — un pico de diamante o mejor aporta un bono PLANO. Es la vía del
 *      jugador que aún no ha entrenado: le abre la mena, no el bloque macizo.
 *   3. Ki — Ki Fist con las manos vacías, o Ki Infuse empuñando el pico. Solo se COBRA
 *      cuando el bono de ki es lo que cierra la diferencia; si ya llegabas sin él, es gratis.
 * ESTE ES EL ÚNICO SITIO donde se decide qué suma, cuánto pide cada bloque y cuándo se
 * cobra el ki. La velocidad de picado, el corte del servidor y el tooltip leen de aquí; si
 * mañana entra otro material duro (Kachi Katchin, Metal de Kaishin, lo que sea), es una
 * línea en bootstrap() y nada más se toca.
 * El requisito va por BLOQUE, no por tag: un tag no puede llevar un número asociado y
 * queremos que la mena, el bloque macizo y las variantes talladas pidan cosas distintas.
 */
public final class PowerMining {
    private PowerMining() {}

    /** Bloque -> poder mínimo para romperlo. IdentityHashMap: los Block son singletons. */
    private static final Map<Block, Double> REQUIRED = new IdentityHashMap<>();

    /** Velocidad de tier a partir de la cual la herramienta aporta algo. 8.0F = diamante. */
    private static final float MIN_TOOL_SPEED = 8.0F;
    /** Cuánto poder vale cada punto de velocidad de tier. Diamante = 800, netherita = 900. */
    private static final double TOOL_POWER_PER_SPEED = 100.0;

    /** Ticks que tarda el bloque cuando vas JUSTO al mínimo (5 s). */
    private static final double BASE_TICKS = 100.0;
    /** Suelo: por mucho poder que sobre, un bloque nunca baja de 1 s. */
    private static final double MIN_TICKS = 20.0;
    /** Techo del ratio poder/requisito que se traduce en velocidad. */
    private static final double MAX_RATIO = 8.0;

    // ── Tabla de requisitos ──────────────────────────────────────────────────

    /**
     * Se llama desde el commonSetup de Zenkai (enqueueWork), que corre DESPUÉS del registro
     * de bloques: aquí ya se puede hacer .get() sin riesgo.
     */
    public static void bootstrap() {
        REQUIRED.clear();

        // Menas: la puerta de entrada. 400 lo alcanza un jugador con algo de STR invertido,
        // y un pico de diamante (800) las abre de saque aunque no haya entrenado nada.
        require(ModBlocks.KATCHIN_ORE, 400.0);
        require(ModBlocks.DEEPSLATE_KATCHIN_ORE, 600.0);

        // Bloque macizo: 900. Por encima del pico de diamante a propósito — el metal en
        // bruto solo lo parte la netherita o tus propios puños. Es el "romper Katchin" del
        // anime, no un paso de fabricación.
        require(ModBlocks.KATCHIN_BLOCK, 900.0);

        // Set de construcción: 500. Lo has fabricado tú y vas a querer remodelar el dojo;
        // castigar la reforma con el mismo muro que la extracción solo da tedio. Sigue
        // siendo inmune al ki, que es para lo que se pone.
        require(ModBlocks.CUT_KATCHIN, 500.0);
        require(ModBlocks.CUT_KATCHIN_STAIRS, 500.0);
        require(ModBlocks.CUT_KATCHIN_SLAB, 500.0);
        require(ModBlocks.CUT_KATCHIN_WALL, 500.0);
        require(ModBlocks.KATCHIN_PILLAR, 500.0);
    }

    public static void require(DeferredBlock<?> block, double power) {
        REQUIRED.put(block.get(), power);
    }

    public static boolean isPowerMined(BlockState state) {
        return REQUIRED.containsKey(state.getBlock());
    }

    /** Poder que pide el bloque, o 0 si no está en la tabla. */
    public static double required(BlockState state) {
        Double v = REQUIRED.get(state.getBlock());
        return v == null ? 0.0 : v;
    }

    // ── Cálculo del poder del jugador ────────────────────────────────────────

    /**
     * Desglose en dos partes porque el cobro las necesita separadas: si la base ya llega,
     * el ki no se toca.
     *
     * @param base  STR efectivo + bono plano de herramienta
     * @param ki    bono de Ki Fist / Ki Infuse, sin cobrar todavía
     */
    public record Power(double base, double ki) {
        public double total() { return base + ki; }
    }

    public static Power powerOf(Player player) {
        ZenkaiCombatStats st = ZenkaiStats.of(player);
        if (st == null) return new Power(0.0, 0.0);

        double base = st.computeMeleeFinal();
        double ki = 0.0;

        ItemStack main = player.getMainHandItem();
        if (main.isEmpty()) {
            // Manos vacías: Ki Fist. Es la vía del luchador puro.
            ki = KiFist.rawBonus(player, st);
        } else {
            // Con herramienta: el tier suma plano solo desde diamante, y Ki Infuse aporta
            // su bono porque un pico cuenta como arma (attackDamage > 1).
            if (main.getItem() instanceof DiggerItem digger) {
                float speed = digger.getTier().getSpeed();                    // ⚠ API
                if (speed >= MIN_TOOL_SPEED) base += speed * TOOL_POWER_PER_SPEED;
            }
            ki = KiInfusion.rawMeleeBonus(player, st);
        }
        return new Power(base, ki);
    }

    // ── Consultas que usan los eventos ───────────────────────────────────────

    /**
     * Velocidad de picado. Devuelve 0 si no llega: el que llama cancela el evento y el
     * bloque se comporta como irrompible (sin grietas, sin sonido de progreso).
     */
    public static float breakSpeed(Player player, BlockState state, float hardness) {
        double req = required(state);
        if (req <= 0.0) return -1.0F;

        double total = powerOf(player).total();
        if (total < req) return 0.0F;

        double ratio = Mth.clamp(total / req, 1.0, MAX_RATIO);
        double ticks = Mth.clamp(BASE_TICKS / ratio, MIN_TICKS, BASE_TICKS);
        // Fórmula inversa de vainilla: ticks = hardness * 30 / speed.
        return (float) (hardness * 30.0 / ticks);
    }

    /**
     * Autoridad del servidor sobre la rotura. COBRA el ki aquí y solo aquí, y solo si el
     * bono de ki es lo que cierra la diferencia: si tu STR ya bastaba, romper es gratis.
     *
     * Caída silenciosa igual que en KiFist/KiInfusion: sin ki suficiente no cobra nada y
     * devuelve false, en vez de cobrar a medias y dejar el bloque puesto.
     */
    public static boolean tryBreak(Player player, BlockState state) {
        double req = required(state);
        if (req <= 0.0) return true;

        ZenkaiCombatStats st = ZenkaiStats.of(player);
        if (st == null) return false;

        Power p = powerOf(player);
        if (p.base() >= req) return true;
        if (p.total() < req) return false;

        // El ki cierra el hueco. Se cobra por el bono COMPLETO, no por el trozo que faltaba:
        // envolver el puño cuesta lo que cuesta, no lo justo para este bloque.
        int cost = KiInfusion.kiCost(st, p.ki());
        if (st.getEnergy() < cost) return false;
        st.consumeEnergy(cost);
        return true;
    }
}