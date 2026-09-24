#version 150

// Capas planas del VFX de ki (halo, núcleo explícito, estela, rayos): sin luz ni overlay, el
// color del vértice ya viene decidido por el renderer. Ver ki_glow.fsh.

in vec3 Position;
in vec4 Color;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec4 vColor;
out vec2 vUv;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vColor = Color;
    vUv = UV0;
}
