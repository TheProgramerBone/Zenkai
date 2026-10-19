package com.hmc.zenkai.client.fly;

import com.hmc.zenkai.Zenkai;
import com.hmc.zenkai.config.ClientConfig;
import com.hmc.zenkai.feature.aura.AuraColors;
import com.hmc.zenkai.feature.player.PlayerStatsAttachment;
import com.hmc.zenkai.registry.ModParticles;
import com.hmc.zenkai.registry.ModSounds;
import com.hmc.zenkai.registry.ZenkaiDataAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Efectos de velocidad del vuelo: polvo del suelo, estampido sónico y onda al aterrizar (las
 * líneas de velocidad viven en SpeedLines). Cada uno tiene su interruptor en ClientConfig (sección flight_fx).
 *
 * SOLO cliente y para CUALQUIER jugador cercano, no solo el local: la velocidad se mide con el
 * desplazamiento de posición entre ticks (el delta de movimiento de un jugador remoto es 0 en
 * este cliente) y "está volando" sale del espejo isFlyEnabled de PlayerStatsAttachment, que sí
 * viaja a los clientes que lo ven. Sin paquetes y sin estado en el servidor.
 *
 * FOV dinámico, temblor de cámara y calor de reentrada NO están aquí a propósito: se pidió
 * dejarlos fuera (ver FovLockHandler y CameraShakeMixin). Las imágenes residuales al volar se
 * probaron y se quitaron (2026-10-18): salían como siluetas grises gigantes que tapaban la cámara.
 *
 * Partículas: NADA de ParticleTypes.CLOUD ni SONIC_BOOM. CLOUD visto de cerca son bloques
 * blancos en forma de X (se vio en un video de pruebas) y SONIC_BOOM es el sprite del Warden.
 * El polvo usa el color del bloque pisado; el estampido, el color del aura.
 *
 * Los umbrales están en bloques/tick y se calibraron con el log A0 del vuelo (nivel medio ≈ 2.0,
 * turbo ≈ 2.7, arranque ≈ 0.5). Los sonidos son eventos propios (subtítulo propio) que apuntan a
 * audio de vanilla como relleno hasta tener el del mod (ver sounds.json).
 */
@EventBusSubscriber(modid = Zenkai.MOD_ID, value = Dist.CLIENT)
public final class FlightFx {
    private FlightFx() {}

    /** Solo se calculan los jugadores a esta distancia del local (al cuadrado). */
    private static final double RANGE_SQ = 64.0 * 64.0;

    // ── Polvo del suelo ──
    private static final double DUST_MIN_SPEED = 1.0;
    /** Altura máxima sobre el suelo para que levante polvo. */
    private static final int DUST_MAX_HEIGHT = 4;

    // ── Estampido sónico (con histéresis: se arma al bajar de RESET, dispara al subir de ARM) ──
    private static final double SONIC_ARM_SPEED = 2.3;
    private static final double SONIC_RESET_SPEED = 1.7;
    private static final int SONIC_RING_PARTICLES = 28;
    /** Distancia delante del jugador a la que nace el anillo, en bloques. */
    private static final double SONIC_AHEAD = 3.0;

    // ── Aterrizaje ──
    private static final double LANDING_MIN_SPEED = 1.2;
    private static final int LANDING_RING_PARTICLES = 40;

    private static final class State {
        double prevSpeed;
        boolean wasFlying;
        boolean sonicReady = true;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            STATES.clear();
            return;
        }
        if (mc.isPaused()) return;

        for (var p : level.players()) {
            if (!(p instanceof AbstractClientPlayer ap) || p.isSpectator()) continue;
            if (p.distanceToSqr(mc.player) > RANGE_SQ) continue;
            tickPlayer(level, ap, STATES.computeIfAbsent(p.getUUID(), k -> new State()));
        }
        STATES.keySet().removeIf(id -> level.getPlayerByUUID(id) == null);
    }

    private static void tickPlayer(ClientLevel level, AbstractClientPlayer p, State s) {
        PlayerStatsAttachment att = p.getData(ZenkaiDataAttachments.PLAYER_STATS.get());
        boolean flying = att.isFlyEnabled() && !p.onGround();
        double speed = new Vec3(p.getX() - p.xo, p.getY() - p.yo, p.getZ() - p.zo).length();

        // Aterrizaje: el tick en que toca suelo tras volar rápido. La velocidad de ESTE tick ya es 0,
        // así que se usa la del anterior.
        if (s.wasFlying && p.onGround() && s.prevSpeed >= LANDING_MIN_SPEED
                && ClientConfig.flightFxLanding()) {
            landingShockwave(level, p, s.prevSpeed);
        }

        if (flying) {
            if (ClientConfig.flightFxDust() && speed >= DUST_MIN_SPEED) groundDust(level, p, speed);

            if (speed < SONIC_RESET_SPEED) {
                s.sonicReady = true;
            } else if (s.sonicReady && speed >= SONIC_ARM_SPEED) {
                s.sonicReady = false;
                if (ClientConfig.flightFxSonicBoom()) sonicBoom(level, p);
            }
        } else {
            s.sonicReady = true;
        }

        s.wasFlying = flying;
        s.prevSpeed = flying ? speed : 0.0;
    }

    /** Primer bloque sólido bajo el jugador, hasta DUST_MAX_HEIGHT; null si no hay. */
    private static BlockPos groundBelow(ClientLevel level, AbstractClientPlayer p) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(p.getBlockX(), p.getBlockY(), p.getBlockZ());
        for (int i = 0; i <= DUST_MAX_HEIGHT; i++) {
            BlockState st = level.getBlockState(pos);
            if (!st.isAir() && !st.getCollisionShape(level, pos).isEmpty()) return pos.immutable();
            pos.move(0, -1, 0);
        }
        return null;
    }

    private static void groundDust(ClientLevel level, AbstractClientPlayer p, double speed) {
        BlockPos ground = groundBelow(level, p);
        if (ground == null) return;
        double top = ground.getY() + 1.0;
        double height = Math.max(0.0, p.getY() - top);
        // Cuanto más cerca del suelo y más rápido, más polvo; a la altura máxima, casi nada.
        double closeness = 1.0 - Mth.clamp(height / DUST_MAX_HEIGHT, 0.0, 1.0);
        RandomSource r = level.random;
        int count = (int) Math.round(closeness * (1.0 + speed * 1.5));
        if (count <= 0) return;
        BlockState state = level.getBlockState(ground);
        BlockParticleOption debris = new BlockParticleOption(ParticleTypes.BLOCK, state);
        Vec3 back = p.getDeltaMovement().multiply(1, 0, 1);
        if (back.lengthSqr() < 1.0e-4) back = p.getLookAngle().multiply(1, 0, 1);
        back = back.normalize().scale(-0.12);
        for (int i = 0; i < count; i++) {
            double ox = (r.nextDouble() - 0.5) * 1.4;
            double oz = (r.nextDouble() - 0.5) * 1.4;
            level.addParticle(debris, p.getX() + ox, top + 0.1, p.getZ() + oz,
                    back.x + ox * 0.15, 0.05 + r.nextDouble() * 0.1, back.z + oz * 0.15);
            if (r.nextInt(3) == 0) {
                level.addParticle(dustOf(level, ground, state), p.getX() + ox, top + 0.15, p.getZ() + oz,
                        back.x, 0.02, back.z);
            }
        }
    }

    /** Polvo fino con el color del bloque (su color de mapa, aclarado un poco para que se lea). */
    private static DustParticleOptions dustOf(ClientLevel level, BlockPos pos, BlockState state) {
        int col = state.getMapColor(level, pos).col;
        float r = Mth.lerp(0.25f, ((col >> 16) & 0xFF) / 255f, 1f);
        float g = Mth.lerp(0.25f, ((col >> 8) & 0xFF) / 255f, 1f);
        float b = Mth.lerp(0.25f, (col & 0xFF) / 255f, 1f);
        return new DustParticleOptions(new Vector3f(r, g, b), 1.4f);
    }

    /** Mezcla rgb con blanco; f=0 deja el color tal cual, f=1 da blanco. */
    private static int lighten(int rgb, float f) {
        int r = (int) Mth.lerp(f, (rgb >> 16) & 0xFF, 255);
        int g = (int) Mth.lerp(f, (rgb >> 8) & 0xFF, 255);
        int b = (int) Mth.lerp(f, rgb & 0xFF, 255);
        return (r << 16) | (g << 8) | b;
    }

    private static void sonicBoom(ClientLevel level, AbstractClientPlayer p) {
        Vec3 dir = p.getDeltaMovement();
        if (dir.lengthSqr() < 1.0e-4) dir = p.getLookAngle();
        dir = dir.normalize();
        int rgb = AuraColors.resolve(p);
        // El anillo nace DELANTE del jugador, no sobre él: en primera persona la cámara queda
        // dentro del cuerpo y, con partículas alrededor, la pantalla se llenaba de bloques blancos.
        Vec3 ahead = p.position().add(0, p.getBbHeight() * 0.5, 0).add(dir.scale(SONIC_AHEAD));
        // Destello central con el color del aura (partícula propia, no el sprite del Warden).
        level.addParticle(ModParticles.impact(rgb, 2.2f), ahead.x, ahead.y, ahead.z, 0, 0, 0);
        // Dos anillos de motas perpendiculares al rumbo, abriéndose hacia fuera: el exterior con
        // el color del aura y el interior más claro.
        Vec3 up = Math.abs(dir.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 a = dir.cross(up).normalize();
        Vec3 b = dir.cross(a).normalize();
        for (int i = 0; i < SONIC_RING_PARTICLES; i++) {
            double ang = i * (Math.PI * 2.0 / SONIC_RING_PARTICLES);
            Vec3 out = a.scale(Math.cos(ang)).add(b.scale(Math.sin(ang)));
            level.addParticle(ModParticles.energyMote(rgb, 1.1f),
                    ahead.x + out.x * 0.8, ahead.y + out.y * 0.8, ahead.z + out.z * 0.8,
                    out.x * 0.25, out.y * 0.25, out.z * 0.25);
            if (i % 2 == 0) {
                level.addParticle(ModParticles.energyMote(lighten(rgb, 0.6f), 0.8f),
                        ahead.x + out.x * 0.4, ahead.y + out.y * 0.4, ahead.z + out.z * 0.4,
                        out.x * 0.14, out.y * 0.14, out.z * 0.14);
            }
        }
        level.playLocalSound(p.getX(), p.getY(), p.getZ(), ModSounds.FLIGHT_SONIC_BOOM.get(),
                SoundSource.PLAYERS, 0.5f, 1.4f, false);
    }

    private static void landingShockwave(ClientLevel level, AbstractClientPlayer p, double speed) {
        BlockPos below = p.blockPosition().below();
        BlockState state = level.getBlockState(below);
        BlockPos at = below;
        if (state.isAir()) {
            at = p.blockPosition();
            state = level.getBlockState(at);
        }
        if (state.isAir()) return;
        BlockParticleOption debris = new BlockParticleOption(ParticleTypes.BLOCK, state);
        DustParticleOptions dust = dustOf(level, at, state);
        double power = Mth.clamp(speed / 2.0, 0.6, 1.6);
        for (int i = 0; i < LANDING_RING_PARTICLES; i++) {
            double ang = i * (Math.PI * 2.0 / LANDING_RING_PARTICLES);
            double dx = Math.cos(ang), dz = Math.sin(ang);
            level.addParticle(debris, p.getX() + dx * 0.4, p.getY() + 0.1, p.getZ() + dz * 0.4,
                    dx * 0.45 * power, 0.12, dz * 0.45 * power);
            if (i % 2 == 0) {
                level.addParticle(dust, p.getX() + dx * 0.4, p.getY() + 0.15, p.getZ() + dz * 0.4,
                        dx * 0.3 * power, 0.03, dz * 0.3 * power);
            }
        }
        level.playLocalSound(p.getX(), p.getY(), p.getZ(), ModSounds.FLIGHT_LANDING.get(),
                SoundSource.PLAYERS, 0.3f * (float) power, 1.5f, false);
    }
}
