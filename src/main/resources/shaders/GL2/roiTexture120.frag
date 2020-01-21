#version 120

varying vec3 texCoord;
uniform sampler3D mytex0;

void main(){
	gl_FragColor=texture3D(mytex0, texCoord);
}
