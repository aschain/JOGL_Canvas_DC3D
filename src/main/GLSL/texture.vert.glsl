#version 450 core

//position in model space
layout (location = 0) in vec3 aPos;
//texture position
layout (location = 1) in vec3 aTex;
//matrices
layout (binding = 1) uniform Transform0{
	mat4 proj;
	mat4 view;
};
layout (binding = 2) uniform Transform1{
	mat4 model;
};
layout (location = 0) out Block{
	vec3 texCoord;
};

void main(){
	gl_Position = vec4(aPos,1.0);
	texCoord = aTex;
}
