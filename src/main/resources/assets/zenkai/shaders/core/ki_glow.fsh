#version 150

// ALFA PREMULTIPLICADO con EMISIÓN (KiVfxRenderTypes, blend ONE / ONE_MINUS_SRC_ALPHA).
// rgb sale ya multiplicado por su alfa; el alfa de salida es solo la parte que TAPA lo de detrás.
//   ZenkaiEmit 0 → mezcla normal (tiñe, tapa), como un cristal de color.
//   ZenkaiEmit 1 → aditivo puro (suma luz, no tapa nada).
// Entre medias, la capa ilumina Y tapa a la vez. Es la mezcla estándar de VFX de energía: la
// mezcla normal sola se veía pastel sobre el cielo de día (vídeo 2026-09-24 09-38-28); el aditivo
// solo lavaba a blanco todo lo que se solapaba.
// Hace falta un shader propio porque el de entidad vanilla NO premultiplica: con esta mezcla, los
// bordes transparentes de la textura del halo sumarían su color entero.

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float ZenkaiEmit;

in vec4 vColor;
in vec2 vUv;

out vec4 fragColor;

void main() {
    vec4 c = texture(Sampler0, vUv) * vColor * ColorModulator;
    if (c.a < 0.004) discard;
    fragColor = vec4(c.rgb * c.a, c.a * (1.0 - clamp(ZenkaiEmit, 0.0, 1.0)));
}
