package com.hmc.zenkai.feature.race.hair.strand;

import net.minecraft.network.chat.Component;

/**
 * Código de pelo ilegible o import imposible. Lleva una clave de idioma
 * ({@code zenkai.hair_code.error.*}) para enseñárselo al jugador traducido.
 */
public class HairCodeException extends Exception {
    private final transient Component component;

    public HairCodeException(String key, Object... args) {
        super(key);
        this.component = Component.translatable("zenkai.hair_code.error." + key, args);
    }

    public HairCodeException withCause(Throwable cause) {
        initCause(cause);
        return this;
    }

    public Component component() {
        return component;
    }
}
