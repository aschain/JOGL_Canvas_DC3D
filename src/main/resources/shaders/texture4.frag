#version 450 core

precision highp float;
precision highp int;

layout(std140, column_major) uniform;

layout (binding = 3) uniform lutblock{
	vec3 luts[6];
};
in vec3 texCoord;
// Outgoing final color.
out vec4 outputColor;

uniform sampler3D mytex;

void main(){
	vec4 texColor=texture(mytex, texCoord);
	outputColor=vec4(0,0,0,0);
	for(int i=0;i<4;i++){
		bool color[3];
		float rgb=luts[i][2];
		if(rgb>0.8){
			float col=max(texColor[i]-luts[i][0],0)/(luts[i][1]-luts[i][0]);
			color[2]=rgb>3.5;
			if(color[2])rgb=rgb-4;
			color[1]=rgb>1.8;
			if(color[1])rgb=rgb-2;
			color[0]=rgb>0.8;
			if(color[0])outputColor.r=clamp(outputColor.r+col,0,1);
			if(color[1])outputColor.g=clamp(outputColor.g+col,0,1);
			if(color[2])outputColor.b=clamp(outputColor.b+col,0,1);
		}
	}
	outputColor.a = max(outputColor.r,max(outputColor.g,outputColor.b));
}
