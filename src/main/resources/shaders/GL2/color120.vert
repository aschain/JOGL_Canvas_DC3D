#version 120

attribute vec3 aPos;
attribute vec4 aColor;

uniform mat4 proj[2];
uniform mat4 model;

varying vec4 fragColor;

void main(){
	gl_Position = proj[0] * (proj[1] * (model * vec4(aPos.x, aPos.y, aPos.z, 1.0)));
	fragColor = aColor;
}
