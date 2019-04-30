package ajs.joglcanvas;

import static com.jogamp.opengl.GL3.*;
import static com.jogamp.opengl.GL4.*;

import java.io.File;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.Enumeration;
import java.util.Hashtable;

import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL3;
import com.jogamp.opengl.GL4;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.math.FloatUtil;
import com.jogamp.opengl.util.GLBuffers;
import com.jogamp.opengl.util.glsl.ShaderCode;
import com.jogamp.opengl.util.glsl.ShaderProgram;

import ajs.joglcanvas.JOGLImageCanvas.PixelType;
import ij.Prefs;

public class JCGLObjects {
	
	enum GLVer{GL3, GL4};
	private GLVer glver=GLVer.GL3;
	private GL gl;
	public JCTextures textures=new JCTextures();
	public JCBuffers buffers=new JCBuffers();
	public JCVaos vaos=new JCVaos();
	public JCPrograms programs=new JCPrograms();
	
	public JCGLObjects() {}
	
	public JCGLObjects(GLAutoDrawable drawable) {
		setGL(drawable);
	}

	public JCGLObjects(GL gl) {
		setGL(gl.getGL());
	}
	
	public void dispose() {
		textures.dispose();
		buffers.dispose();
		vaos.dispose();
		programs.dispose();
	}
	
	public void setGL(GLAutoDrawable drawable) {
		this.gl=drawable.getGL();
		setGLVer();
	}
	
	public void setGL(GL gl) {
		this.gl=gl;
		setGLVer();
	}
	
	public void setGLVer() {
		String version=gl.glGetString(GL_VERSION);
		float v=Float.parseFloat(version.substring(0, 3));
		glver=GLVer.GL3;
		if(v>=3.0f)glver=GLVer.GL3;
		if(v>=4.5f)glver=GLVer.GL4;
	}
	
	public void newTexture(String name) {
		textures.newTexture(name, 1);
	}
	
	public void newTexture(String name, int size) {
		textures.newTexture(name, size);
	}
	
	public ByteBuffer newBuffer(int gltype, String name, long size, Buffer buffer) {
		return buffers.newBuffer(gltype,  name, size, buffer);
	}
	
	public void newBuffer(int gltype, String name) {
		buffers.newBuffer(gltype, name);
	}
	
	public void bindUniformBuffer(String name, int binding) {
		buffers.bindBuffer(GL_UNIFORM_BUFFER, name, binding);
	}
	
	public void unBindBuffer(int gltype, int binding) {
		buffers.unBindBuffer(gltype, binding);
	}
	
	public void unBindBuffer(int gltype) {
		buffers.unBindBuffer(gltype, 0);
	}
	
	public void newVao(String name, int size1, int gltype1, int size2, int gltype2) {
		vaos.newVao(name, size1, gltype1, size2, gltype2);
	}
	
	public void newProgram(String name, String root, String vertex, String fragment) {
		programs.newProgram(name, root, vertex, fragment);
	}
	
	public void drawTexVao(String name, int glElementBufferType, int count, int chs) {
		drawTexVao(name, 0, glElementBufferType, count, chs);
	}
	
	public void drawTexVao(String name, int index, Buffer vertexBuffer) {
		vertexBuffer.rewind();
		int[] sizes=vaos.vsizes.get(name);
		Buffer eb=getElementBufferFromVBO(vertexBuffer, (sizes[4]+sizes[5])/getSizeofType(vertexBuffer));
		eb.rewind();
		drawTexVaoWithEBOVBO(name, index, eb, vertexBuffer);
	}
	
	public void drawTexVao(String name, int index, Buffer vb, String pname) {
		programs.useProgram(pname);
		drawTexVao(name, index, vb);
		programs.stopProgram();
	}
	
	/**
	 * Could make this more complicated
	 * @param vertexBuffer
	 * @param vertexSize
	 * @return
	 */
	private Buffer getElementBufferFromVBO(Buffer vertexBuffer, int vertexSize) {
		ShortBuffer eb=GLBuffers.newDirectShortBuffer(vertexBuffer.capacity()/vertexSize);
		for(int i=0;i<vertexBuffer.capacity()/vertexSize;i++)eb.put((short)i);
		return eb;
	}
	
	public void drawTexVaoWithEBOVBO(String name, int index, Buffer elementBuffer, Buffer vertexBuffer) {
		bindEBOVBO(name, elementBuffer, vertexBuffer);
		drawTexVao(name, index, getGLType(elementBuffer), elementBuffer.capacity(), 0);
		unBindEBOVBO(name);
	}
	
	private void bindEBOVBO(String name, Buffer elementBuffer, Buffer vertexBuffer) {
		elementBuffer.rewind();  vertexBuffer.rewind();
		if(buffers.element.containsKey(name))gl.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, buffers.element.get(name)[0]);
		if(buffers.array.containsKey(name))gl.glBindBuffer(GL_ARRAY_BUFFER, buffers.array.get(name)[0]);
		gl.glBufferData(GL_ELEMENT_ARRAY_BUFFER, elementBuffer.capacity()*getSizeofType(elementBuffer), elementBuffer, GL_DYNAMIC_DRAW);
		gl.glBufferData(GL_ARRAY_BUFFER, vertexBuffer.capacity()*getSizeofType(vertexBuffer), vertexBuffer, GL_DYNAMIC_DRAW);
	}
	
	private void unBindEBOVBO(String name) {
		if(buffers.element.containsKey(name))gl.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
		if(buffers.array.containsKey(name))gl.glBindBuffer(GL_ARRAY_BUFFER, 0);
	}

	public void drawTexVao(String name, int texIndex, int glElementBufferType, int count, int chs) {
		GL3 gl3=gl.getGL3();
		int gltype=GL_TEXTURE_3D;
		gl3.glEnable(gltype);
		gl3.glBindTexture(gltype, textures.get(name, texIndex));
		
		for(int i=0;i<chs;i++) {
			gl3.glActiveTexture(GL_TEXTURE0+i);
			gl3.glBindTexture(gltype, textures.get(name, texIndex+i));
			int[] pr=new int[1];gl3.glGetIntegerv(GL_CURRENT_PROGRAM, pr,0);
			gl3.glUniform1i(gl3.glGetUniformLocation(pr[0], "mytex["+i+"]"),i);
		}

		gl3.glBindVertexArray(vaos.get(name));
		
		if(glver==GLVer.GL3){
			//GL3 gl3=gl.getGL3();
			if(buffers.element.containsKey(name))gl3.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, buffers.element.get(name)[0]);
			if(buffers.array.containsKey(name))gl3.glBindBuffer(GL_ARRAY_BUFFER, buffers.array.get(name)[0]);
			int[] sizes=vaos.vsizes.get(name);
			gl3.glVertexAttribPointer(0, sizes[0], sizes[1], false, sizes[4]+sizes[5], 0);
			gl3.glEnableVertexAttribArray(0);
			gl3.glVertexAttribPointer(1, sizes[2], sizes[3], false, sizes[4]+sizes[5], sizes[4]);
			gl3.glEnableVertexAttribArray(1);
		}
		
        gl3.glDrawElements(GL_TRIANGLES, count, glElementBufferType, 0);
		gl3.glBindVertexArray(0);
		gl3.glBindTexture(gltype, 0);
		gl3.glDisable(gltype);
	}
	
	public void drawVao(int glDraw, String vname, Buffer vb, String pname) {
		programs.useProgram(pname);
		drawVao(glDraw, vname, vb);
		programs.stopProgram();
	}
	
	public void drawVao(int glDraw, String name, Buffer vertexBuffer) {
		GL3 gl3=gl.getGL3();
		int[] sizes=vaos.vsizes.get(name);
		Buffer elementBuffer=getElementBufferFromVBO(vertexBuffer, (sizes[4]+sizes[5])/getSizeofType(vertexBuffer));
		bindEBOVBO(name, elementBuffer, vertexBuffer);
		gl3.glBindVertexArray(vaos.get(name));
		gl3.glDrawElements(glDraw, elementBuffer.capacity(), getGLType(elementBuffer), 0);
		gl3.glBindVertexArray(0);
		unBindEBOVBO(name);
	}
	
	
	
	
	
	class JCTextures{
		
		public Hashtable<String,int[]> handles=new Hashtable<String, int[]>();
		public Hashtable<String,int[]> pbos=new Hashtable<String, int[]>();
		
		public JCTextures() {}
		
		public void newTexture(String name, int size) {
			if(handles.containsKey(name)) {
				dispose(name);
			}
			int[] textureHandles = new int[size]; 
			gl.glGenTextures(size, textureHandles, 0);
			handles.put(name, textureHandles);
		}
		
		public void newPbo(String name, int size) {
			if(pbos.containsKey(name)) {
				disposePbo(name);
			}
			pbos.put(name, new int[size]);
		}
		
		public void dispose() {
			for(Enumeration<int[]> i=handles.elements(); i.hasMoreElements();) {
				int[] ths=i.nextElement();
				gl.glDeleteTextures(ths.length,ths,0);
			}
			for(Enumeration<int[]> i=pbos.elements(); i.hasMoreElements();) {
				int[] phs=i.nextElement();
				gl.glDeleteBuffers(phs.length,phs,0);
			}
		}
		
		public void dispose(String name) {
			int[] ths=handles.get(name);
			gl.glDeleteTextures(ths.length,ths,0);
		}
		
		public void disposePbo(String name) {
			int[] phs=pbos.get(name);
			gl.glDeleteBuffers(phs.length,phs,0);
		}
		
		public int get(String name) {
			return get(name, 0);
		}
		
		public int get(String name, int index) {
			return handles.get(name)[index];
		}

		public int getLength(String name) {
			if(handles.get(name)==null)return 0;
			return handles.get(name).length;
		}
		public int getPboLength(String name) {
			if(pbos.get(name)==null)return 0;
			return pbos.get(name).length;
		}
		
		public boolean containsKey(String name) {
			return handles.containsKey(name);
		}
		
		public boolean containsPboKey(String name) {
			return pbos.containsKey(name);
		}
		
		public void createRgbaTexture(String name, Buffer buffer, int width, int height, int depth, int COMPS) {
			createRgbaTexture(name, 0, buffer, width, height, depth, COMPS);
		}
		
		public void createRgbaTexture(String name, int index, Buffer buffer, int width, int height, int depth, int COMPS) {
			createRgbaTexture(get(name,index),buffer, width, height, depth, COMPS);
		}

		private void createRgbaTexture(int glTextureHandle, Buffer buffer, int width, int height, int depth, int COMPS) { 
			GL3 gl3=gl.getGL3();
			
			JOGLImageCanvas.PixelTypeInfo pinfo=JOGLImageCanvas.getPixelTypeInfo(buffer, COMPS);

			gl3.glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
			gl3.glEnable(GL_TEXTURE_3D);
			gl3.glBindTexture(GL_TEXTURE_3D, glTextureHandle); 
			gl3.glTexImage3D(GL_TEXTURE_3D, 0, pinfo.glInternalFormat, width, height, depth, 0, pinfo.glFormat, pinfo.glPixelSize, buffer); 
			//gl.glTexImage3D(GL_TEXTURE_2D, mipmapLevel, internalFormat, width, height, depth, numBorderPixels, pixelFormat, pixelType, buffer); 
			
			int magtype=GL_LINEAR;
			if(!Prefs.interpolateScaledImages)magtype=GL_NEAREST;
			
			gl3.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MAG_FILTER, magtype);
			gl3.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MIN_FILTER, magtype);//GL_NEAREST_MIPMAP_LINEAR
			gl3.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_BORDER);
			gl3.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_BORDER);
			gl3.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_BORDER);
			gl3.glTexParameterfv(GL_TEXTURE_3D, GL_TEXTURE_BORDER_COLOR, new float[] {0f,0f,0f,0f},0);
			gl3.glGenerateMipmap(GL_TEXTURE_3D);
			gl3.glBindTexture(GL_TEXTURE_3D, 0); 
			gl3.glDisable(GL_TEXTURE_3D);
		} 
		
		public void loadTexFromPBO(String sameName, int pn, int width, int height, int depth, int offsetSlice, PixelType type, int COMPS, boolean endian) {
			loadTexFromPBO(sameName, pn, sameName, 0, width, height, depth, offsetSlice, type, COMPS, endian);
		}
		
		public void loadTexFromPBO(String pboName, int pn, String texName, int tn, int width, int height, int depth, int offsetSlice, PixelType type, int COMPS, boolean endian) {

			GL3 gl3=gl.getGL3();
			
			int[] phs=pbos.get(pboName);
			int[] ths=handles.get(texName);
			
			JOGLImageCanvas.PixelTypeInfo pinfo=JOGLImageCanvas.getPixelTypeInfo(type, COMPS);
			
			gl3.glEnable(GL_TEXTURE_3D);
			gl3.glActiveTexture(GL_TEXTURE0);
			gl3.glBindBuffer(GL_PIXEL_UNPACK_BUFFER, phs[pn]);
			gl3.glBindTexture(GL_TEXTURE_3D, ths[tn]); 
			gl3.glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
			if(endian)gl3.glPixelStorei(GL_UNPACK_SWAP_BYTES, GL_TRUE);
			gl3.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_BASE_LEVEL, 0);
			gl3.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MAX_LEVEL, 0);
			gl3.glTexImage3D(GL_TEXTURE_3D, 0, pinfo.glInternalFormat, width, height, depth, 0, pinfo.glFormat, pinfo.glPixelSize, offsetSlice*pinfo.components*width*height*pinfo.sizeBytes);
			int magtype=GL_LINEAR;
			if(!Prefs.interpolateScaledImages)magtype=GL_NEAREST;
			gl3.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MAG_FILTER, magtype); 
			gl3.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MIN_FILTER, magtype);//GL_NEAREST_MIPMAP_LINEAR 
			gl3.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_BORDER);
			gl3.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_BORDER);
			gl3.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_BORDER);
			gl3.glTexParameterfv(GL_TEXTURE_3D, GL_TEXTURE_BORDER_COLOR, new float[] {0f,0f,0f,0f},0);
			//gl3.glGenerateMipmap(GL_TEXTURE_3D);
			if(endian)gl3.glPixelStorei(GL_UNPACK_SWAP_BYTES, GL_FALSE);
			gl3.glDisable(GL_TEXTURE_3D);
			gl3.glBindTexture(GL_TEXTURE_3D, 0); 
			gl3.glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
		}
		
		public void updateRgbaPBO(String name, int index, Buffer buffer) {
			updateSubRgbaPBO(name, index, buffer, 0, 0, buffer.limit(), buffer.limit());
		}
		
		/**
		 * 
		 * @param name
		 * @param index
		 * @param buffer
		 * @param bufferOffset
		 * @param PBOoffset
		 * @param length
		 * @param bsize
		 */
		public void updateSubRgbaPBO(String name, int index, Buffer buffer, int bufferOffset, int PBOoffset, int length, int bsize) {
			int[] phs=pbos.get(name);
			int size=getSizeofType(buffer);
			
			boolean isNew=false;
			if(phs[index]==0) {
				gl.glGenBuffers(1, phs, index);
				isNew=true;
			}
			gl.glBindBuffer(GL_PIXEL_UNPACK_BUFFER, phs[index]); 
			if(!isNew){
				int[] pbosize=new int[1];
				gl.glGetBufferParameteriv(GL_PIXEL_UNPACK_BUFFER, GL_BUFFER_SIZE, pbosize, 0);
				//IJ.log("pbosize: "+pbosize[0]+" des size:"+bsize*size);
				if(pbosize[0]!=bsize*size) {
					isNew=true;
				}
			}
			if(isNew)gl.glBufferData(GL_PIXEL_UNPACK_BUFFER, bsize*size, null, GL_DYNAMIC_DRAW);
			buffer.position(bufferOffset);
			gl.glBufferSubData(GL_PIXEL_UNPACK_BUFFER, (long)PBOoffset*size, (long)length*size, buffer);
			gl.glBindBuffer(GL_PIXEL_UNPACK_BUFFER,0);
		}
		
	}

	class JCBuffers{

		public Hashtable<String,int[]> array=new Hashtable<String, int[]>();
		public Hashtable<String,int[]> uniform=new Hashtable<String, int[]>();
		public Hashtable<String,int[]> element=new Hashtable<String, int[]>();
		public Hashtable<String,Buffer> abuffers=new Hashtable<String,Buffer>();
		public Hashtable<String,Buffer> ubuffers=new Hashtable<String,Buffer>();
		public Hashtable<String,Buffer> ebuffers=new Hashtable<String,Buffer>();
		
		
		
		public JCBuffers() {}

		public ByteBuffer newBuffer(int gltype, String name, long size, Buffer buffer) {
			return newBuffer(gltype, name, size, buffer, true);
		}
		
		public ByteBuffer newBuffer(int gltype, String name) {
			return newBuffer(gltype, name, 0, null, false);
		}
		
		public ByteBuffer newBuffer(int gltype, String name, long size, Buffer buffer, boolean define) {
			GL3 gl3=gl.getGL3();
			GL4 gl4=gl.getGL4();
			Hashtable<String,int[]> dict=array;
			Hashtable<String,Buffer> bdict=abuffers;
			if(gltype==GL_UNIFORM_BUFFER) {dict=uniform; bdict=ubuffers;}
			else if(gltype==GL_ELEMENT_ARRAY_BUFFER) {dict=element;bdict=ebuffers;}
			int[] bn=new int[1];
			if(glver==GLVer.GL4) gl4.glCreateBuffers(1, bn, 0);
			else gl3.glGenBuffers(1, bn, 0);
			dict.put(name, bn);
			if(define) {
				boolean write=(buffer==null);
				gl3.glBindBuffer(gltype, bn[0]);
				if(glver==GLVer.GL4){
					gl4.glBufferStorage(gltype, size, buffer,  (buffer==null)?(GL_MAP_WRITE_BIT | GL_MAP_PERSISTENT_BIT | GL_MAP_COHERENT_BIT):0);
					gl4.glBindBuffer(gltype,  0);
				}else{
					gl3.glBufferData(gltype, size, buffer, (buffer==null)?GL_DYNAMIC_DRAW:GL_STATIC_DRAW);
				}
				
				if(!write)return null;
				ByteBuffer outbuffer;
				if(glver==GLVer.GL4){
					outbuffer= gl4.glMapNamedBufferRange(
							bn[0],
							0,
							size,
							GL_MAP_WRITE_BIT | GL_MAP_PERSISTENT_BIT | GL_MAP_COHERENT_BIT | GL_MAP_INVALIDATE_RANGE_BIT | GL_MAP_INVALIDATE_BUFFER_BIT); // flags
				}else{
					outbuffer= gl3.glMapBufferRange(
							gltype,
			                0,
			                size,
			                GL_MAP_WRITE_BIT | GL_MAP_INVALIDATE_RANGE_BIT | GL_MAP_INVALIDATE_BUFFER_BIT); // flags
					bdict.put(name, outbuffer);
					gl3.glBindBuffer(gltype,  0);
				}
				bdict.put(name, outbuffer);
				return outbuffer;
			}else return null;
		}
		
		public int get(int gltype, String name) {
			Hashtable<String, int[]> dict=array;
			if(gltype==GL_UNIFORM_BUFFER) dict=uniform;
			else if(gltype==GL_ELEMENT_ARRAY_BUFFER)dict=element;
			int[] hs= dict.get(name);
			if(hs==null)return 0;
			return hs[0];
		}
		
		public Buffer getDirectBuffer(int gltype, String name) {
			Hashtable<String, Buffer> bdict=abuffers;
			if(gltype==GL_UNIFORM_BUFFER) bdict=ubuffers;
			else if(gltype==GL_ELEMENT_ARRAY_BUFFER)bdict=ebuffers;
			return bdict.get(name);
		}
		
		public void loadIdentity(String name) {
			loadIdentity(name, 0);
		}
		
		public void loadIdentity(String name, int offset) {
			ByteBuffer matrix=(ByteBuffer)ubuffers.get(name);
			float[] view = FloatUtil.makeIdentity(new float[16]);
	        for (int i = 0; i < 16; i++) {
	            matrix.putFloat(offset + i * 4, view[i]);
	        }
	        matrix.rewind();
		}
		
		public void loadMatrix(String name, float[] matrix) {
			ByteBuffer buffer=(ByteBuffer)ubuffers.get(name);
			buffer.rewind(); buffer.asFloatBuffer().rewind();
			buffer.asFloatBuffer().put(matrix);
			buffer.rewind(); buffer.asFloatBuffer().rewind();
			
		}
		
		public void bindBuffer(int gltype, String name, int binding) {
			GL3 gl3=gl.getGL3();
			if(gltype==GL_UNIFORM_BUFFER) {
				gl3.glBindBufferBase(gltype, binding, uniform.get(name)[0]);
				return;
			}

			Hashtable<String, int[]> dict=array;
			if(gltype==GL_ELEMENT_ARRAY_BUFFER)dict=element;
			gl3.glBindBuffer(gltype, dict.get(name)[0]);
		}
		
		public void unBindBuffer(int gltype, int binding) {
			GL3 gl3=gl.getGL3();
			if(gltype==GL_UNIFORM_BUFFER) {
				gl3.glBindBufferBase(gltype, binding, 0);
				return;
			}
			gl3.glBindBuffer(gltype, 0);
		}
		
		public void dispose() {
			GL3 gl3=gl.getGL3();
			for(int i=0;i<3;i++) {
				Hashtable<String, int[]> dict=array;
				Hashtable<String, Buffer> bdict=abuffers;
				int gltype=GL_ARRAY_BUFFER;
				if(i==1) {dict=uniform; bdict=ubuffers; gltype=GL_UNIFORM_BUFFER;}
				else if(i==2) {dict=element; bdict=ebuffers; gltype=GL_ELEMENT_ARRAY_BUFFER;}
				for(Enumeration<String> j=dict.keys(); j.hasMoreElements();) {
					String name=j.nextElement();
					int[] phs=dict.get(name);
					if(bdict.get(name)!=null){
						if(glver==GLVer.GL4){gl.getGL4().glUnmapNamedBuffer(phs[0]);}
						else{
							gl3.glBindBuffer(gltype, phs[0]);
							gl3.glUnmapBuffer(gltype);
						}
					}
					gl3.glDeleteBuffers(phs.length,phs,0);
				}
			}
		}
	}

	class JCVaos{
		
		public Hashtable<String, int[]> handles =new Hashtable<String, int[]>();
		public Hashtable<String, int[]> vsizes =new Hashtable<String, int[]>();
		
		public JCVaos() {}
		
		public void newVao(String name, int size1, int gltype1, int size2, int gltype2) {
		
			GL3 gl3=gl.getGL3();
			GL4 gl4=gl.getGL4();
			
			int[] vhs=new int[1];
			if(glver==GLVer.GL4){
				gl4.glCreateVertexArrays(vhs.length, vhs, 0);
				int vao=vhs[0];
				gl4.glVertexArrayAttribBinding(vao, 0, 0);//modelcoords
				gl4.glVertexArrayAttribBinding(vao, 1, 0);//texcoords
				int sizeoftype1=getSizeofType(gltype1)*size1;
				int sizeoftype2=getSizeofType(gltype2)*size2;
				gl4.glVertexArrayAttribFormat(vao, 0, size1, gltype1, false, 0);//modelcoords
		        gl4.glVertexArrayAttribFormat(vao, 1, size2, gltype2, false, sizeoftype1);//texcoords
		        gl4.glEnableVertexArrayAttrib(vao, 0);
		        gl4.glEnableVertexArrayAttrib(vao, 1);
				if(buffers.element.get(name)!=null) {
			        gl4.glVertexArrayElementBuffer(vao, buffers.element.get(name)[0]);
				}
				if(buffers.array.get(name)!=null) {
					gl4.glVertexArrayVertexBuffer(vao, 0, buffers.array.get(name)[0], 0, sizeoftype1+sizeoftype2);
				}
			}
			else gl3.glGenVertexArrays(vhs.length, vhs, 0);
			handles.put(name, vhs);
			int sizeoftype1=getSizeofType(gltype1)*size1;
			int sizeoftype2=getSizeofType(gltype2)*size2;
			vsizes.put(name, new int[] {size1,gltype1,size2,gltype2,sizeoftype1,sizeoftype2});
		}
		
		public int get(String name) {
			int[] hs=handles.get(name);
			if(hs==null)return 0;
			return hs[0];
		}
		
		public void dispose() {
			for(Enumeration<int[]> j=handles.elements(); j.hasMoreElements();) {
				int[] vhs=j.nextElement();
				gl.getGL3().glDeleteVertexArrays(vhs.length,vhs,0);
			}
		}
	}
	
	class JCPrograms{

		public Hashtable<String, Program> programs =new Hashtable<String, Program>();

        public JCPrograms() {}
        
        public void newProgram(String name, String root, String vertex, String fragment) {
        	programs.put(name, new Program(root, vertex, fragment));
        }
        
        public void addProgram(String name, int program, Hashtable<String, Integer> locs) {
        	programs.put(name, new Program(program, locs));
        }
        
        public void addLocation(String programName, String var) {
        	programs.get(programName).addLocation(var);
        }
        
        public int getLocation(String programName, String var) {
        	return programs.get(programName).locations.get(var);
        }
        
        public int getProgram(String pname) {
        	return programs.get(pname).name;
        }
        
        public void useProgram(String name) {
        	gl.getGL3().glUseProgram(programs.get(name).name);
        }
        
        public void stopProgram() {
        	gl.getGL3().glUseProgram(0);
        }
        
        public void dispose() {
        	for(Enumeration<Program> j=programs.elements(); j.hasMoreElements();) {
				j.nextElement().dispose();
			}
        }
        
        class Program{
        	
        	int name=0;
    		public Hashtable<String, Integer> locations =new Hashtable<String, Integer>();
        	
        	public Program(String root, String vertex, String fragment) {
        	GL3 gl3=gl.getGL3();
        	//new File(JCGLObjects.class.getClassLoader().getResource(root+"/"+fragment+".frag").toURI()).exists();
        	root=root+"/"+(glver==GLVer.GL4?"GL4":"GL3");
            ShaderCode vertShader = ShaderCode.create(gl3, GL_VERTEX_SHADER, this.getClass(), root, null, vertex,
                    "vert", null, true);
            ShaderCode fragShader = ShaderCode.create(gl3, GL_FRAGMENT_SHADER, this.getClass(), root, null, fragment,
                    "frag", null, true);

            ShaderProgram shaderProgram = new ShaderProgram();

            shaderProgram.add(vertShader);
            shaderProgram.add(fragShader);

            shaderProgram.init(gl3);
            name=shaderProgram.program();

            shaderProgram.link(gl3, System.err);
            
            if(glver==GLVer.GL3) {
        		gl3.glUniformBlockBinding(name, gl3.glGetUniformBlockIndex(name, "Transform0"), 1);
        		gl3.glUniformBlockBinding(name, gl3.glGetUniformBlockIndex(name, "Transform1"), 2);
        		if(fragment.equals("texture"))gl3.glUniformBlockBinding(name, gl3.glGetUniformBlockIndex(name, "lutblock"), 3);
            }
            
        	}
        	
        	public Program(int pname, Hashtable<String, Integer> locs) {
        		name=pname;
        		locations=locs;
        	}
        	
        	public void addLocation(String var) {
        		locations.put(var, gl.getGL3().glGetUniformLocation(name, var));
        	}
        	
        	public void dispose() {
        		gl.getGL3().glDeleteProgram(name);
        	}
        	
        }
		
		
	}
	
	private int getSizeofType(int gltype) {
		switch(gltype) {
		case GL_UNSIGNED_BYTE : return Buffers.SIZEOF_BYTE;
		case GL_UNSIGNED_SHORT : return Buffers.SIZEOF_SHORT;
		case GL_UNSIGNED_INT : return Buffers.SIZEOF_INT;
		case GL_FLOAT : return Buffers.SIZEOF_FLOAT;
		case GL_DOUBLE : return Buffers.SIZEOF_DOUBLE;
		}
		return 0;
	}
	
	private int getGLType(Buffer buffer) {
		if(buffer instanceof java.nio.ByteBuffer) return GL_UNSIGNED_BYTE;
		if(buffer instanceof java.nio.ShortBuffer) return GL_UNSIGNED_SHORT;
		if(buffer instanceof java.nio.IntBuffer) return GL_UNSIGNED_INT;
		if(buffer instanceof java.nio.FloatBuffer) return GL_FLOAT;
		if(buffer instanceof java.nio.DoubleBuffer) return GL_DOUBLE;
		return 0;
	}
	
	private int getSizeofType(Buffer buffer) {
		return getSizeofType(getGLType(buffer));
	}
}
