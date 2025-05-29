attribute vec4 vPosition;
attribute vec2 aTexCoord;
uniform mat4 uMVPMatrix;
varying vec2 vTexCoord;
 
void main() {
    gl_Position = uMVPMatrix * vPosition;
    vTexCoord = aTexCoord;
} 