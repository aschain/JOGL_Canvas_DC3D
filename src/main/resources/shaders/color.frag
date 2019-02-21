#version 450 core
layout (location = 0, index = 0) out vec4 FragColor;

in vec3 texCoord;

void main(){
	FragColor = vec4(texCoord.x, texCoord.y, texCoord.z, 1.0);
}
