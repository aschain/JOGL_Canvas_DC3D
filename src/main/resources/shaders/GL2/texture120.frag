#version 120

varying vec3 texCoord;

uniform vec4 luts[6];

uniform sampler3D mytex[6];

void main(){
	vec4 outputColor=vec4(0,0,0,0);
	for(int i=0;i<6;i++){
		vec4 lut=luts[i];
		int rgb=int(lut.b);
		if(rgb>0){
			bool color[3];
			vec4 texColor=texture3D(mytex[i], texCoord);
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
