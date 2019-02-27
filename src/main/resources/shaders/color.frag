#version 450 core

precision highp float;
precision highp int;

layout (location = 0) out vec4 outputColor;

in vec4 fragColor;

void main(){
	outputColor = fragColor;
}
