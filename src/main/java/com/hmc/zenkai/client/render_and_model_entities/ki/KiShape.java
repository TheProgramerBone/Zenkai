package com.hmc.zenkai.client.render_and_model_entities.ki;

/**
 * Forma geométrica con la que se dibuja una técnica. DECISIÓN DE RENDER, por eso vive en
 * cliente: el servidor no dibuja nada.
 * Antes cada tipo era el mismo billboard, así que un láser, un disco y una bola se veían
 * idénticos. La silueta es lo primero que hace que una técnica se reconozca, y por eso cada
 * forma tiene que significar UNA cosa: la hélice es la firma del spiral, y meterla también
 * alrededor de la onda le robaba identidad a las dos.
 *
 * QUÉ FORMA USA CADA TÉCNICA NO SE DECIDE AQUÍ: eso es {@link KiVisual}, que es la autoridad
 * única de presentación. Este enum es solo el catálogo de geometrías que
 * {@link KiMeshFactory} sabe construir.
 */
public enum KiShape {
    SPHERE,
    /** Tubo a lo largo del eje de vuelo, con estrechamiento hacia la cola. */
    CYLINDER,
    /** Dos cintas enroscadas alrededor del eje de vuelo, sin núcleo. */
    HELIX,
    /** Disco plano que vuela DE CANTO y gira sobre su propio eje. */
    DISK
}
