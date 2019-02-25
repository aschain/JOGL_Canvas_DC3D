#version 450 core

precision highp float;
precision highp int;

layout(std140, column_major) uniform;
layout(std430, column_major) buffer;

// Incoming interpolated (between vertices) color.
//layout (location = 0) in Block
//{
//    vec3 texCoord;
//};
in vec3 texCoord;
in mat4 luts;

// Outgoing final color.
layout (location = 0) out vec4 outputColor;

uniform sampler3D mytex;

void main(){
	vec4 texColor=texture(mytex, texCoord);
	outputColor=vec4(0,0,0,0);
	for(int i=0;i<4;i++){
		bool color[3];
		float rgb=luts[i][2];
		color[2]=rgb>3.5;
		if(color[2])rgb=rgb-4;
		color[1]=rgb>1.8;
		if(color[1])rgb=rgb-2;
		color[0]=rgb>0.8;
		texColor[i]=(texColor[i]-luts[i][0])/(luts[i][1]-luts[i][0]);
		if(color[0])outputColor.r=clamp(outputColor.r+texColor[i],0f,1f);
		if(color[1])outputColor.g=clamp(outputColor.g+texColor[i],0f,1f);
		if(color[2])outputColor.b=clamp(outputColor.b+texColor[i],0f,1f);
	}
	outputColor.a = max(outputColor.r,max(outputColor.g,outputColor.b));
}
