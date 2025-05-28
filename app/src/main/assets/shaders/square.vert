uniform mat4 uMVPMatrix;
attribute vec4 vPosition;
uniform vec4 uColor;
 
void main() {
    gl_Position = uMVPMatrix * vPosition;
} 