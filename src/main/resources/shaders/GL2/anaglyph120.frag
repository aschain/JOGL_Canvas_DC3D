#version 120

varying vec3 texCoord;

uniform sampler2D mytex;
uniform mat3 ana;
uniform float dubois;

void main(){
	vec4 texColor=texture2D(mytex, texCoord.rg);
	if(dubois<0.8){
		texColor.r=max(max(texColor.r,texColor.g),texColor.b);
		texColor.g=0.0;
		texColor.b=0.0;
	}
	gl_FragColor=vec4(texColor.rgb*ana,texColor.a);
}
