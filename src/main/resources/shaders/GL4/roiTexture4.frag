#version 330 core

precision highp float;
precision highp int;

layout(std140, column_major) uniform;

in vec3 texCoord;
// Outgoing final color.
out vec4 outputColor;

uniform sampler2D mytex[1];

void main(){
	outputColor=texture(mytex[0], texCoord.rg);
}
