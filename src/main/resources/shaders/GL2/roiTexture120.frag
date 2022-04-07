#version 120

varying vec3 texCoord;
uniform sampler2D mytex[1];

void main(){
	gl_FragColor=texture2D(mytex[0], texCoord.rg);
}
