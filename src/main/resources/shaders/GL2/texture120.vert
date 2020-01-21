#version 120

//position in model space
attribute vec3 aPos;
//texture position
attribute vec3 aTex;
//matrices

uniform mat4 proj[2];
uniform mat4 model;

varying vec3 texCoord;

void main(){
	gl_Position = proj[0] * (proj[1] * (model * vec4(aPos.x, aPos.y, aPos.z, 1.0)));
	texCoord = aTex;
}
