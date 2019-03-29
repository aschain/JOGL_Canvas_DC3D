#version 330 core

precision highp float;
precision highp int;

layout(std140, column_major) uniform;

// Incoming texture coordinate 3d.
in vec3 texCoord;
in mat4 luts;

// Outgoing final color.
layout (location = 0) out vec4 outputColor;

uniform sampler3D mytex;
uniform int stereoi;
uniform mat3 ana[2];

void main(){
	vec4 texColor=texture(mytex, texCoord);
	vec3 anaColor=vec3(0,0,0);
	for(int i=0;i<4;i++){
		float rgb=luts[i][2];
		if(rgb>0.8){
			texColor[i]=(texColor[i]-luts[i][0])/(luts[i][1]-luts[i][0]);
			bool color[3];
			color[2]=rgb>3.5;
			if(color[2])rgb=rgb-4;
			color[1]=rgb>1.8;
			if(color[1])rgb=rgb-2;
			color[0]=rgb>0.8;
			if(color[0])anaColor.r=clamp(anaColor.r+texColor[i],0f,1f);
			if(color[1])anaColor.g=clamp(anaColor.g+texColor[i],0f,1f);
			if(color[2])anaColor.b=clamp(anaColor.b+texColor[i],0f,1f);
		}
	}
    mat3 m;
    if(stereoi==0)m=ana[0];
    else m=ana[1];
	outputColor = vec4(anaColor*m,1f);
}
