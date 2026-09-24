package com.hmc.zenkai.client.render_and_model_entities.kivfx;

/**
 * Geometría de la CÁSCARA de una técnica. Decisión de render, por eso vive en cliente: el
 * servidor no dibuja nada.
 * QUÉ FORMA USA CADA TÉCNICA NO SE DECIDE AQUÍ: eso es {@link KiVfxProfile}, la autoridad única
 * de presentación. Este enum es solo el catálogo de siluetas que {@link KiVfxGeometry} sabe
 * construir — un láser, un disco y una bola tienen que significar formas DISTINTAS, o toda
 * técnica se lee igual.
 */
public enum KiVfxShape {
    SPHERE,
    /** Tubo a lo largo del eje de vuelo (+Z local), con estrechamiento hacia la cola. */
    BEAM,
    /** Dos cintas enroscadas alrededor del eje de vuelo, sin núcleo propio en la cáscara. */
    HELIX,
    /** Disco-lente plano que vuela DE CANTO y gira sobre su propio eje. */
    DISK
}
