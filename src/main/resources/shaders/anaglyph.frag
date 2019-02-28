#version 450 core

precision highp float;
precision highp int;

layout(std140, column_major) uniform;
layout(std430, column_major) buffer;

// Incoming texture coordinate 3d.
in vec3 texCoord;
in mat4 luts;

// Outgoing final color.
layout (location = 0) out vec4 outputColor;

uniform sampler3D mytex;
//uniform vec3 lrc;
uniform int stereoi;
uniform float aR;
uniform float aG;
uniform float aB;

void main(){

	//Source of below: bino, a 3d video player:  https://github.com/eile/bino/blob/master/src/video_output_render.fs.glsl
	// Source of this matrix: http://www.site.uottawa.ca/~edubois/anaglyph/LeastSquaresHowToPhotoshop.pdf
	mat3 m0 = mat3(
			 0.437, -0.062, -0.048,
			 0.449, -0.062, -0.050,
			 0.164, -0.024, -0.017);
	mat3 m1 = mat3(
			-0.011,  0.377, -0.026,
			-0.032,  0.761, -0.093,
			-0.007,  0.009,  1.234);
	mat3 m=mat3(
			aR,aR,aR,
			aG,aG,aG,
			aB,aB,aB);

	vec4 texColor=texture(mytex, texCoord);
	vec3 anaColor=vec3(0,0,0);
	bool dubois=(aR==0 && aG==0 && aB==0);
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
	if(dubois){
		if(stereoi==0)m=m0;
		else m=m1;
	}
	outputColor = vec4(anaColor*m,1f);
}
