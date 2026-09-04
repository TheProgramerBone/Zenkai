#version 150

// Composición final: KiBloomPipeline dibuja este quad de pantalla completa con blending
// ADITIVO (SRC_ALPHA, ONE) ya activado en Java antes de la llamada — este fragment no necesita
// samplear el fondo ni mezclar nada él mismo (a diferencia de composit.fsh de dbrebirth, que sí
// lo hace porque separa "fake"/"real" bloom con tonemap): sumar sobre lo que ya hay en pantalla
// es trabajo del blend state de GL, no del shader. Intensity es el único control de fuerza.

uniform sampler2D Sampler0;
uniform float Intensity;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 c = texture(Sampler0, texCoord);
    fragColor = vec4(c.rgb * Intensity, c.a * Intensity);
}
