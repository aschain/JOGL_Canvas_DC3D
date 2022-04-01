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
			vec4 texColor;
			if(i==0)texColor=texture3D(mytex[0], texCoord);
			else if(i==1)texColor=texture3D(mytex[1], texCoord);
			else if(i==2)texColor=texture3D(mytex[2], texCoord);
			else if(i==3)texColor=texture3D(mytex[3], texCoord);
			else if(i==4)texColor=texture3D(mytex[4], texCoord);
			else if(i==5)texColor=texture3D(mytex[5], texCoord);
			if(rgb>7){
				outputColor.rgb=texColor.gba;
				outputColor.a=max(outputColor.r,max(outputColor.g,outputColor.b));
				gl_FragColor=outputColor;
				return;
			}else{
				float col=(texColor.r-lut.r)/(lut.g-lut.r);
				col=max(col,0.0);
				if(lut.a>0.04)col=exp(lut.a*log(col));
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
