#version 450 core

precision highp float;
precision highp int;

layout(std140, column_major) uniform;
layout(std430, column_major) buffer;

in vec3 texCoord;

// Outgoing final color.
layout (location = 0) out vec4 outputColor;

uniform sampler3D mytex;

void main(){
	outputColor=texture(mytex, texCoord);
}