package com.hmc.zenkai.client.aura.compose.component;

/**
 * Configuración INMUTABLE de un componente, ya parseada y validada al cargar el estilo (los errores
 * salen en el log al recargar recursos, no a mitad de partida). Cada jugador crea sus instancias
 * vivas desde aquí.
 */
public interface AuraComponentConfig {

    String type();

    AuraComponent create();
}
