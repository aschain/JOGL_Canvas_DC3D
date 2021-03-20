#version 300 es

precision highp float;
precision highp int;

out vec4 outputColor;
in vec4 fragColor;

void main(){
	outputColor = fragColor;
}
