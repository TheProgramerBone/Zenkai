#version 150

// LÁMINAS de llama del aura compuesta (componente "sheets", FlameSheetComponent, 2026-10-26): quads
// verticales orientados a cámara solo en Y alrededor del cuerpo. La forma de la llama NO está en la
// malla: la dibuja el fsh por UV. Contrato de vértice:
//  - UV0.x = semilla de la lámina (parte entera) + posición horizontal 0..1 (parte fraccionaria);
//  - UV0.y = altura 0..1 dentro de la lámina, +2 en el modo "luz" (columna recta de Ultra Instinct);
//  - Color = tono del cuerpo de la llama (alfa = opacidad de la lámina);
//  - Normal = tono de la SOMBRA entre lenguas (no es una normal: tres canales de color 0..1).

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec4 vColor;
out vec2 vUV;
out vec3 vShadow;
out float vDist;

void main() {
    vec4 viewPos = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * viewPos;
    vColor = Color;
    vUV = UV0;
    vShadow = Normal;
    vDist = length(viewPos.xyz);
}
