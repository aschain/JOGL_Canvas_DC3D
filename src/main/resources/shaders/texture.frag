#version 450 core

precision highp float;
precision highp int;

layout(std140, column_major) uniform;
layout(std430, column_major) buffer;

layout (location = 0) in Block{
	vec3 texCoord;
};

layout (location = 0) out vec4 outputColor;

uniform sampler3D mytex;

void main(){
	outputColor = texture(mytex, texCoord);
}
