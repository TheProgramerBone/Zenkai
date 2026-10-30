package com.hmc.zenkai.client.aura;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.feature.aura.AuraManager;
import com.hmc.zenkai.feature.aura.AuraProfile;
import com.hmc.zenkai.registry.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Efectos de SUELO al cargar ki o transformarse (2026-09-30): nubes de polvo que salen de los pies
 * y trozos del bloque pisado que suben girando. Pedidos tras comparar en vídeo con DragonMine Z,
 * donde son lo que más vende "esto está removiendo el terreno" (en Zenkai no había nada al cargar).
 * La estrella de rayas plana del suelo es otra clase ({@code AuraFlameGroundRenderer}): esa se
 * dibuja cada frame; esto solo spawnea partículas por tick.
 * <p>
 * SOLO CLIENTE, SIN PACKET: cada cliente mira a los jugadores que ya ve con los flags de carga y
 * transformación que el servidor ya sincroniza a los trackers
 * ({@link AuraClientState#isChargingOrTransforming}). Vale para cualquier sistema de aura (llama
 * nueva o legacy): son efectos del mundo, no del cuerpo del aura.
 * <p>
 * Densidad: una base que cualquiera ve (DMZ la muestra a cualquier nivel) más un extra con
 * {@code AuraProfile.ground()} (presencia² × liberación: un endgame a plena potencia remueve mucho
 * más suelo que un novato). Se atenúa con la altura sobre el suelo y se apaga por completo a más de
 * {@link #MAX_HEIGHT} bloques. La opción vanilla de partículas manda: MINIMAL = nada; DECREASED ya
 * la aplica el propio {@code LevelRenderer.addParticle}, igual que la distancia máxima.
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class AuraChargeGroundFx {

    private AuraChargeGroundFx() {}

    /** Bloques bajo los pies en los que todavía se busca suelo (cargar flotando justo encima). */
    public static final int MAX_HEIGHT = 2;
    /** Tinte al que tiende el polvo: sin esto, la hierba (MapColor verde) levantaría polvo verde. */
    private static final int DUST_BASE_RGB = 0xB5A48C;
    private static final float DUST_BASE_MIX = 0.45f;

    /** Suelo encontrado bajo un jugador: bloque, su posición, la Y de su superficie y la altura
     *  del jugador sobre ella. */
    public record Ground(BlockState state, BlockPos pos, double surfaceY, double height) {}

    /**
     * Busca el suelo bajo los pies, hasta {@link #MAX_HEIGHT} bloques. Null si no hay nada sólido
     * y visible (aire, fluidos, bloques invisibles como la barrera).
     */
    public static Ground findGround(ClientLevel level, Player p, double x, double y, double z) {
        for (int dy = 0; dy <= MAX_HEIGHT; dy++) {
            BlockPos pos = BlockPos.containing(x, y - 0.01 - dy, z);
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || !state.getFluidState().isEmpty()
                    || state.getRenderShape() == RenderShape.INVISIBLE) continue;
            VoxelShape shape = state.getCollisionShape(level, pos);
            if (shape.isEmpty()) continue; // hierba alta, flores: no son suelo
            double surface = pos.getY() + shape.max(Direction.Axis.Y);
            double height = y - surface;
            if (height > MAX_HEIGHT) return null;
            return new Ground(state, pos, surface, Math.max(0.0, height));
        }
        return null;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.isPaused()) return;
        if (mc.options.particles().get() == ParticleStatus.MINIMAL) return;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        RandomSource rng = level.random;

        for (Player pl : level.players()) {
            if (!(pl instanceof AbstractClientPlayer p) || p.isSpectator()) continue;
            if (!AuraClientState.isChargingOrTransforming(p)) continue;
            if (p.position().distanceToSqr(cam) > 48 * 48) continue;

            Ground g = findGround(level, p, p.getX(), p.getY(), p.getZ());
            if (g == null) continue;

            AuraProfile profile = AuraManager.profileOf(p, AuraClientState.isTurbo(p) ? 1f : 0f);
            float power = 0.35f + 0.65f * Mth.clamp(profile.ground(), 0f, 1f);
            // Altura: a ras de suelo, entero; a MAX_HEIGHT bloques, casi nada.
            float near = 1f - (float) (g.height() / (MAX_HEIGHT + 0.5));
            float k = power * near;
            if (k <= 0.02f) continue;

            // RESPUESTA DE SUELO POR FAMILIA (aura compuesta, campo "ground" del estilo): una aura
            // divina no ensucia el suelo, una salvaje levanta rocas que se quedan flotando. Con el
            // legacy o la llama sola, null = lo de siempre (polvo + rocas).
            String mode = com.hmc.zenkai.client.aura.compose.AuraComposer.groundModeOf(p.getId());
            if (mode == null) mode = "dust";
            switch (mode) {
                case "none", "clean" -> { }
                case "aggressive" -> {
                    spawnDust(level, rng, p, g, Math.min(1f, k * 1.5f));
                    spawnRocks(level, rng, p, g, Math.min(1f, k * 1.6f), false);
                }
                // Blue (divine_radiance): sin polvo, solo unas pocas rocas que se quedan flotando.
                case "levitate" -> spawnRocks(level, rng, p, g, k * 0.45f, true);
                case "pressure" -> {
                    spawnDust(level, rng, p, g, k);
                    spawnRocks(level, rng, p, g, Math.min(1f, k * 1.4f), true);
                }
                default -> {
                    spawnDust(level, rng, p, g, k);
                    // Como en DMZ, parte de los trozos suben y se QUEDAN flotando alrededor en vez de
                    // caer todos: la mitad levita.
                    spawnRocks(level, rng, p, g, k, rng.nextBoolean());
                }
            }
        }
    }

    /**
     * RÁFAGA de suelo de una transformación (aura compuesta, TransformFx): un anillo de polvo que
     * sale disparado hacia fuera y, si se pide, rocas (que caen, o que levitan en las familias de
     * presión). Una sola vez, en el instante del estallido.
     */
    public static void burst(ClientLevel level, Player p, Ground g, int dust, int rocks, boolean levitate) {
        RandomSource rng = level.random;
        int rgb = dustColor(level, g);
        for (int i = 0; i < dust; i++) {
            double ang = (i + rng.nextDouble() * 0.5) / dust * Math.PI * 2.0;
            double cx = Math.cos(ang), cz = Math.sin(ang);
            double out = 0.35 + rng.nextDouble() * 0.25;
            level.addParticle(ModParticles.auraDust(rgb, 1.2f + rng.nextFloat() * 0.6f),
                    p.getX() + cx * 0.6, g.surfaceY() + 0.1, p.getZ() + cz * 0.6,
                    cx * out, 0.02 + rng.nextDouble() * 0.05, cz * out);
        }
        if (rocks <= 0) return;
        BlockParticleOption opts = new BlockParticleOption(
                (levitate ? ModParticles.AURA_ROCK_FLOAT : ModParticles.AURA_ROCK).get(), g.state());
        for (int i = 0; i < rocks; i++) {
            double ang = rng.nextDouble() * Math.PI * 2.0;
            double r = 0.5 + rng.nextDouble() * 1.8;
            level.addParticle(opts,
                    p.getX() + Math.cos(ang) * r, g.surfaceY() + 0.05, p.getZ() + Math.sin(ang) * r,
                    Math.cos(ang) * 0.08, 0.25 + rng.nextDouble() * 0.35, Math.sin(ang) * 0.08);
        }
    }

    /** Números de partida de DMZ (spawnGroundDust): radio 0.4-1.1, 0.15 hacia fuera, 0.12 arriba. */
    private static void spawnDust(ClientLevel level, RandomSource rng, Player p, Ground g, float k) {
        if (rng.nextFloat() > 0.65f * k + 0.1f) return;
        int rgb = dustColor(level, g);
        // Más nubes, más grandes y más extendidas (2026-09-30, vídeo del usuario contra DMZ: allí
        // el polvo cubre un círculo de varios bloques; aquí quedaba pegado a los pies).
        // ALCANCE: con el rozamiento de AuraDustParticle (0.93) una nube recorre ~14 veces su
        // velocidad inicial y además crece. Con la velocidad de antes llegaba a ~4 bloques — justo
        // donde está la cámara en tercera persona de frente, que quedaba tapada (capturas del
        // 2026-09-30). Radio de nacimiento + recorrido acotados a ~2.6 bloques del jugador.
        int n = 2 + rng.nextInt(2) + Math.round(2f * k);
        for (int i = 0; i < n; i++) {
            double ang = rng.nextDouble() * Math.PI * 2.0;
            double r = 0.5 + rng.nextDouble() * 1.0;
            double cx = Math.cos(ang), cz = Math.sin(ang);
            double out = 0.04 + rng.nextDouble() * 0.04;
            level.addParticle(ModParticles.auraDust(rgb, 0.9f + 0.6f * k),
                    p.getX() + cx * r, g.surfaceY() + 0.1, p.getZ() + cz * r,
                    cx * out, 0.015 + rng.nextDouble() * 0.04, cz * out);
        }
    }

    /** Números de partida de DMZ (spawnFloatingRubble): radio 0.4-1.9, sube 0.1-0.6, ±0.08 de lado. */
    private static void spawnRocks(ClientLevel level, RandomSource rng, Player p, Ground g, float k,
                                   boolean levitate) {
        if (rng.nextFloat() > 0.45f * k) return;
        BlockParticleOption opts = new BlockParticleOption(
                (levitate ? ModParticles.AURA_ROCK_FLOAT : ModParticles.AURA_ROCK).get(), g.state());
        int n = 1 + rng.nextInt(2);
        for (int i = 0; i < n; i++) {
            double ang = rng.nextDouble() * Math.PI * 2.0;
            double r = 0.4 + rng.nextDouble() * 1.3;
            level.addParticle(opts,
                    p.getX() + Math.cos(ang) * r, g.surfaceY() + 0.05, p.getZ() + Math.sin(ang) * r,
                    (rng.nextDouble() - 0.5) * 0.12, 0.25 + rng.nextDouble() * 0.35, (rng.nextDouble() - 0.5) * 0.12);
        }
    }

    /** MapColor del bloque, empujado hacia un beige de polvo: la arena da polvo claro y la piedra
     *  gris, pero la hierba no da polvo verde chillón. */
    private static int dustColor(ClientLevel level, Ground g) {
        MapColor mc = g.state().getMapColor(level, g.pos());
        int c = (mc == null || mc == MapColor.NONE) ? DUST_BASE_RGB : mc.col;
        int r = Math.round(Mth.lerp(DUST_BASE_MIX, (c >> 16) & 0xFF, (DUST_BASE_RGB >> 16) & 0xFF));
        int gg = Math.round(Mth.lerp(DUST_BASE_MIX, (c >> 8) & 0xFF, (DUST_BASE_RGB >> 8) & 0xFF));
        int b = Math.round(Mth.lerp(DUST_BASE_MIX, c & 0xFF, DUST_BASE_RGB & 0xFF));
        return (r << 16) | (gg << 8) | b;
    }
}
