#version 330 core

precision highp float;
precision highp int;

layout(std140, column_major) uniform;

uniform lutblock{
	highp vec3 luts[6];
};
in vec3 texCoord;
// Outgoing final color.
out vec4 outputColor;

uniform highp sampler3D mytex0;
uniform highp sampler3D mytex1;
uniform highp sampler3D mytex2;
uniform highp sampler3D mytex3;
uniform highp sampler3D mytex4;
uniform highp sampler3D mytex5;

void main(){
	outputColor=vec4(0,0,0,0);
	for(int i=0;i<6;i++){
		bool color[3];
		vec3 lut=luts[i];
		if(lut.b>0.8){
			vec4 texColor;
			if(i==0) texColor=texture(mytex0, texCoord);
			else if(i==1)texColor=texture(mytex1, texCoord);
			else if(i==2)texColor=texture(mytex2, texCoord);
			else if(i==3)texColor=texture(mytex3, texCoord);
			else if(i==4)texColor=texture(mytex4, texCoord);
			else if(i==5)texColor=texture(mytex5, texCoord);
			if(lut.b>7.8){
				outputColor.r=texColor.g;
				outputColor.g=texColor.b;
				outputColor.b=texColor.a;
			}else{
				int rgb=int(lut.b);
				float col=max((texColor.r-lut.r),0.0)/(lut.g-lut.r);
				color[2]=(rgb>3);
				if(color[2])rgb=rgb-4;
				color[1]=(rgb>1);
				if(color[1])rgb=rgb-2;
				color[0]=(rgb>0);
				if(color[0])outputColor.r=clamp(outputColor.r+col,0.0,1.0);
				if(color[1])outputColor.g=clamp(outputColor.g+col,0.0,1.0);
				if(color[2])outputColor.b=clamp(outputColor.b+col,0.0,1.0);
			}
		}
	}
	outputColor.a = max(outputColor.r,max(outputColor.g,outputColor.b));
}
