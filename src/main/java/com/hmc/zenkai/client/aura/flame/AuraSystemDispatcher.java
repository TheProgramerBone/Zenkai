package com.hmc.zenkai.client.aura.flame;

import com.hmc.zenkai.config.ClientConfig;
import org.jetbrains.annotations.Nullable;

/**
 * Punto de despacho ÚNICO entre los dos sistemas de aura: el CLÁSICO ({@code client.aura}, faldones
 * legacy) y el NUEVO compuesto por familias ({@code client.aura.compose}). Ver
 * .claude/docs/tecnicas-y-vfx.md, "Aura COMPUESTA por familias".
 * <p>
 * En cualquier frame el aura la dibuja un sistema o el otro, nunca los dos. Los archivos legacy
 * solo LEEN {@link #useFlame()} / {@link #useComposed()} en guardas de una línea; la decisión vive
 * entera aquí.
 * <p>
 * Orden de resolución: el override de sesión de {@code /zenkaiaura system} si lo hay; si no, la
 * config del jugador ({@code ClientConfig.auraEngine()}: Nueva = AUTO, Clásica = FORCE_LEGACY).
 * La "llama sola" de antes del sistema compuesto (modo FORCE_NEW) se BORRÓ el 2026-09-30.
 */
public final class AuraSystemDispatcher {

    private AuraSystemDispatcher() {}

    /** Override de sesión puesto por {@code /zenkaiaura system} (AuraComposeCommand); solo en memoria. null = seguir
     *  la config. Sirve para comparar los dos sistemas en caliente sin tocar el archivo. */
    private static @Nullable AuraSystemMode sessionOverride = null;

    public static @Nullable AuraSystemMode sessionOverride() { return sessionOverride; }

    public static void setSessionOverride(@Nullable AuraSystemMode mode) { sessionOverride = mode; }

    /** El modo que manda ahora mismo: el override de sesión si lo hay; si no, la config del
     *  jugador, que solo tiene dos opciones (NEW = compuesta, LEGACY = clásica). */
    public static AuraSystemMode effectiveMode() {
        if (sessionOverride != null) return sessionOverride;
        return ClientConfig.auraEngine() == AuraEngine.LEGACY ? AuraSystemMode.FORCE_LEGACY : AuraSystemMode.AUTO;
    }

    /**
     * ¿Dibuja el aura NUEVA (el sistema compuesto, {@code client.aura.compose})? Es también la
     * pregunta que hacen las guardas del legacy: con el aura nueva, el legacy no dibuja faldón, aro,
     * wisps, anillo de suelo, chispas, ascuas ni estela. AUTO la elige también con shaderpack (sin
     * bloom propio ahí). Si un shader del aura nueva no compiló, se usa la clásica: mejor el aura de
     * siempre que ninguna.
     */
    public static boolean useComposed() {
        if (!AuraFlameRenderType.available() || !com.hmc.zenkai.client.aura.compose.AuraComposeShaders.available()) return false;
        AuraSystemMode m = effectiveMode();
        return m == AuraSystemMode.AUTO || m == AuraSystemMode.COMPOSED;
    }

    /** Alias de {@link #useComposed()} para las guardas del legacy y la estrella del suelo, que
     *  preguntan "¿cuerpo nuevo?" desde antes de que existiera el sistema compuesto. */
    public static boolean useFlame() {
        return useComposed();
    }
}
