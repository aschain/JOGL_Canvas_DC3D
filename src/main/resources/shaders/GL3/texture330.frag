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

uniform highp sampler3D mytex[6];

void main(){
	outputColor=vec4(0,0,0,0);
	for(int i=0;i<6;i++){
		vec3 lut=luts[i];
		int rgb=int(lut.b);
		if(rgb>0){
			bool color[3];
			vec4 texColor=texture(mytex[i], texCoord);
			if(rgb>7){
				outputColor.r=texColor.g;
				outputColor.g=texColor.b;
				outputColor.b=texColor.a;
			}else{
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
