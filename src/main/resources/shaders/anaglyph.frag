#version 330 core

precision highp float;
precision highp int;

layout(std140, column_major) uniform;

in vec3 texCoord;
// Outgoing final color.
out vec4 outputColor;

uniform sampler3D mytex;
uniform mat3 ana;

void main(){
	vec4 texColor=texture(mytex, texCoord);
	outputColor=vec4(texColor.rgb*ana,texColor.a);
}
