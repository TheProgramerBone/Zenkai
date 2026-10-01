package com.hmc.zenkai.client.aura.compose;

import com.hmc.zenkai.client.aura.compose.component.AuraComponentConfig;

import java.util.List;
import java.util.function.Consumer;

/**
 * Una FAMILIA visual de aura: qué se dibuja y cómo, no cuánto. Es la unidad que diferencia
 * estructuralmente dos auras con el mismo poder — flame (SSJ), divine (SSG/SSB), dark_dense (Ultra
 * Ego)... Vive en Java ({@link AuraFamilies}) porque cada una elige un PROGRAMA distinto
 * ({@link AuraBodyKind}) y un moldeado propio; las variantes dentro de una familia son datos
 * (estilos JSON, {@link AuraStyle}).
 *
 * @param id         nombre corto, el que usa el campo {@code family} de los estilos.
 * @param body       programa del cuerpo exterior.
 * @param inner      capa interior por defecto (el estilo puede sustituirla).
 * @param shape      moldeado de la familia sobre los parámetros calibrados de la llama: convierte
 *                   la llama de partida en el cuerpo de la familia (lóbulos, masa...). Nunca toca
 *                   el poder: las dimensiones se multiplican, no se fijan sin escala.
 * @param components componentes por defecto (motes...), si el estilo no da los suyos.
 * @param ground     respuesta de suelo por defecto (AuraChargeGroundFx).
 * @param transform  secuencia de transformación por defecto.
 * @param trail      forma de la estela de vuelo ({@link FlightTrail}).
 */
public record AuraFamily(String id, AuraBodyKind body, AuraInnerSpec inner, Consumer<AuraBodyParams> shape,
                         List<AuraComponentConfig> components, String ground, String transform,
                         AuraTrailSpec trail) {
}
