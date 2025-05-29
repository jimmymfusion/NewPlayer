precision mediump float;
varying vec2 vTexCoord;
uniform sampler2D uTexture1;
uniform sampler2D uTexture2;
uniform float uProgress;
 
void main() {
    vec4 color1 = texture2D(uTexture1, vTexCoord);
    vec4 color2 = texture2D(uTexture2, vTexCoord);
    gl_FragColor = mix(color1, color2, uProgress);
} 