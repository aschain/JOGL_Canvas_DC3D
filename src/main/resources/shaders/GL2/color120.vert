#version 120

//position in model space
attribute vec3 aPos;
//texture position
attribute vec4 aColor;
//matrices
uniform mat4 proj[2];
uniform mat4 model;
varying vec4 fragColor;

void main(){
	gl_Position = proj[0] * (proj[1] * (model * vec4(aPos.x, aPos.y, aPos.z, 1.0)));
	fragColor = aColor;
}
