#version 450 core
layout (location = 0, index = 0) out vec4 FragColor;

in Block{
	vec3 texCoord;
};

layout (binding = 0) uniform sampler3D mytex;

void main(){
	FragColor = texture(mytex, texCoord);
}
