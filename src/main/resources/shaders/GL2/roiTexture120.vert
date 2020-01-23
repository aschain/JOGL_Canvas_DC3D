#version 120

attribute vec3 aPos;
attribute vec3 aTex;

uniform mat4 proj[2];
uniform mat4 model;

varying vec3 texCoord;

void main(){
	gl_Position = proj[0] * (proj[1] * (model * vec4(aPos.x, aPos.y, aPos.z, 1.0)));
	texCoord = aTex;
}
