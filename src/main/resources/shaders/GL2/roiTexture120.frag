#version 120

varying vec3 texCoord;
uniform sampler3D mytex[1];

void main(){
	gl_FragColor=texture3D(mytex[0], texCoord);
}
