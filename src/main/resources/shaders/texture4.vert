#version 450 core

precision highp float;
precision highp int;

layout(std140, column_major) uniform;
layout(std430, column_major) buffer;

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
layout (binding = 3) uniform Transform2{
	mat4 aluts;
};
//layout (location = 0) out Block
//{
//    vec3 texCoord;
//};
out vec3 texCoord;
out mat4 luts;

void main(){
	gl_Position = proj * (view * (model * vec4(aPos.x, aPos.y, aPos.z, 1.0)));
	texCoord = aTex;
	luts=aluts;
}
