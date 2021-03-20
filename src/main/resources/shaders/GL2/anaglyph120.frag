#version 120

varying vec3 texCoord;

uniform sampler3D mytex;
uniform mat3 ana;
uniform float dubois;

void main(){
	vec4 texColor=texture3D(mytex, texCoord);
	if(dubois<0.8){
		texColor.r=max(max(texColor.r,texColor.g),texColor.b);
		texColor.g=0.0;
		texColor.b=0.0;
	}
	gl_FragColor=vec4(texColor.rgb*ana,texColor.a);
}
