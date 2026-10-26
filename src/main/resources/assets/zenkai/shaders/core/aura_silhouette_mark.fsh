#version 150

// Marca en la máscara del contorno de silueta los píxeles que pintó el jugador: los que su depth se
// ACERCÓ a la cámara entre antes y después de dibujarlo. Capta cualquier cosa que dibuje (cuerpo, pelo y
// cuerpo racial GeckoLib, que escriben en la fuente global y no en la del renderer; ítems) y la oclusión
// sale sola: si un bloque lo tapa, ese depth no cambia.

uniform sampler2D Sampler0; // depth del principal, después
uniform sampler2D Sampler1; // depth de antes (R32F)
uniform float Id;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    float now = texture(Sampler0, texCoord).r;
    float before = texture(Sampler1, texCoord).r;
    if (now >= before - 1.0e-7) discard;
    fragColor = vec4(Id, 0.0, 0.0, 1.0);
}
