#version 300 es

precision highp float;
precision highp int;

layout(std140, column_major) uniform;

in vec3 texCoord;
// Outgoing final color.
out vec4 outputColor;

uniform highp sampler3D mytex0;

void main(){
	outputColor=texture(mytex0, texCoord);
}
