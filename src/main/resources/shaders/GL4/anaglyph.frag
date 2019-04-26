#version 330 core

precision highp float;
precision highp int;

layout(std140, column_major) uniform;

in vec3 texCoord;
// Outgoing final color.
out vec4 outputColor;

uniform sampler3D mytex;
uniform mat3 ana;
uniform float dubois;

void main(){
	vec4 texColor=texture(mytex, texCoord);
	if(dubois<0.8){
		texColor.r=max(max(texColor.r,texColor.g),texColor.b);
		texColor.g=0;
		texColor.b=0;
	}
	outputColor=vec4(texColor.rgb*ana,texColor.a);
}
