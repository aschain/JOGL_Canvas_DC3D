#version 120

varying vec3 texCoord;

uniform vec3 luts[6];

uniform sampler3D mytex0;
uniform sampler3D mytex1;
uniform sampler3D mytex2;
uniform sampler3D mytex3;
uniform sampler3D mytex4;
uniform sampler3D mytex5;

void main(){
	vec4 outputColor=vec4(0,0,0,0);
	for(int i=0;i<6;i++){
		bool color[3];
		vec3 lut=luts[i];
		int rgb=int(lut.b);
		if(rgb>0){
			vec4 texColor;
			if(i==0) texColor=texture3D(mytex0, texCoord);
			else if(i==1)texColor=texture3D(mytex1, texCoord);
			else if(i==2)texColor=texture3D(mytex2, texCoord);
			else if(i==3)texColor=texture3D(mytex3, texCoord);
			else if(i==4)texColor=texture3D(mytex4, texCoord);
			else if(i==5)texColor=texture3D(mytex5, texCoord);
			if(rgb>7){
				outputColor.rgb=texColor.gba;
				outputColor.a=max(outputColor.r,max(outputColor.g,outputColor.b));
				gl_FragColor=outputColor;
				return;
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
	gl_FragColor=outputColor;
}
