uniform mat4 uMVPMatrix;
attribute vec4 vPosition;
attribute vec2 aTexCoord;
uniform float uOffset;
varying vec2 vTexCoord;

void main() {
    // 保持纹理坐标的原始比例
    vTexCoord = vec2(aTexCoord.x - uOffset, aTexCoord.y);
    
    // 应用MVP矩阵变换
    gl_Position = uMVPMatrix * vPosition;
} 