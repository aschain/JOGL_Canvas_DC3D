#version 330 core

precision highp float;
precision highp int;

layout(std140, column_major) uniform;


//position in model space
layout (location = 0) in vec3 aPos;
//texture position
layout (location = 1) in vec4 aColor;
//matrices
uniform Transform0{
	mat4 proj;
	mat4 view;
};
uniform Transform1{
	mat4 model;
};
out vec4 fragColor;

void main(){
	gl_Position = proj * (view * (model * vec4(aPos.x, aPos.y, aPos.z, 1.0)));
	fragColor = aColor;
}
