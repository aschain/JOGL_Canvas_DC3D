package ajs.joglcanvas;

import static com.jogamp.opengl.GL2.*;
import static com.jogamp.opengl.GL2ES3.GL_COLOR;
import static com.jogamp.opengl.GL2ES3.GL_DEPTH;
import static com.jogamp.opengl.GL3.*;
import static com.jogamp.opengl.GL4.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.Enumeration;
import java.util.Hashtable;

import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GL2GL3;
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
	
	//enum GLVer{GL2, GL3, GL4};
	public int glver=2;
	private GL gl=null;
	private GL2GL3 gl23=null;
	private GL2 gl2=null;
	private GL3 gl3=null;
	private GL4 gl4=null;
	public JCTextures textures=new JCTextures();
	public JCBuffers buffers=new JCBuffers();
	public JCVaos vaos=new JCVaos();
	public JCPrograms programs=new JCPrograms();
	
	public JCGLObjects() {}
	
	public JCGLObjects(GLAutoDrawable drawable) {
		setGL(drawable);
	}

	public JCGLObjects(GL gl) {
		setGL(gl);
	}
	
	public void dispose() {
		textures.dispose();
		buffers.dispose();
		vaos.dispose();
		programs.dispose();
	}
	
	public void setGL(GLAutoDrawable drawable) {
		setGL(drawable.getGL());
	}
	
	public void setGL(GL gl) {
		boolean dosv=gl23==null;
		this.gl=gl;
		if(dosv)setGLVer();
		if(glver==2) {gl23=gl.getGL2();gl2=gl.getGL2();}
		if(glver==3) {gl23=gl.getGL3();gl3=gl.getGL3();}
		if(glver==4) {gl23=gl.getGL4();gl4=gl.getGL4();}
	}
	
	public GL2GL3 getGL2GL3() {
		return gl23;
	}
	
	public void setGLVer() {
		String version=gl.glGetString(GL_VERSION);
		//System.out.println("JCGLO gl version "+version);
		float v=Float.parseFloat(version.substring(0, 3));
		int glNameVer=getVersionFromProfileName(JCP.glProfileName);
		glver=2;
		if(v>=3.0f)glver=3;
		if(v>=4.5f)glver=4;
		if(glNameVer>-1 && glNameVer<glver) {
			glver=glNameVer;
			System.out.println("Demoting gl version to "+glver+" because using profile "+JCP.glProfileName);
		}
	}
	
	private int getVersionFromProfileName(String pn) {
		int res=-1;
		if(pn==null)return res;
		for(int i=0;i<pn.length();i++) {
			int c;
			try {
				c=Integer.parseInt(pn.substring(i,i+1));
				if(c>res)res=c;
			}catch(Exception e) {}
		}
		return res;
		/*
		GL4bc GL3bc GL2 GL4 GL3 GLES3 GL4ES3 
		GL2GL3 GLES2 GL2ES2 GLES1 GL2ES1 */
	}

	public void clearColorDepth() {
		if(gl23==null)return;
		if(glver>2) {
			gl23.glClearBufferfv(GL_COLOR, 0, new float[] {0f,0f,0f,0f},0);
	        gl23.glClearBufferfv(GL_DEPTH, 0, new float[] {0f},0);
		}else
			gl.glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
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
		int size=vertexBuffer.capacity()/vertexSize;
		if(size<255) {
			ByteBuffer ebb=GLBuffers.newDirectByteBuffer(size);
			for(int i=0;i<size;i++)ebb.put((byte)i);
			return ebb;
		}else if(size<65535) {
			ShortBuffer ebs=GLBuffers.newDirectShortBuffer(size);
			for(int i=0;i<size;i++)ebs.put((short)i);
			return ebs;
		}else {
			IntBuffer ebi=GLBuffers.newDirectIntBuffer(size);
			for(int i=0;i<size;i++)ebi.put((int)i);
			return ebi;
		}
	}
	
	public void drawTexVaoWithEBOVBO(String name, int index, Buffer elementBuffer, Buffer vertexBuffer) {
		bindEBOVBO(name, elementBuffer, vertexBuffer);
		drawTexVao(name, index, getGLType(elementBuffer), elementBuffer.capacity(), 1);
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
		int gltype=GL_TEXTURE_3D;
		//gl23.glEnable(gltype);
		//gl3.glActiveTexture(GL_TEXTURE0);
		//gl3.glBindTexture(gltype, textures.get(name, texIndex));
		int[] pr=new int[1];gl.glGetIntegerv(GL_CURRENT_PROGRAM, pr,0);
		//gl3.glUniform1i(gl3.glGetUniformLocation(pr[0], "mytex"),0);
		
		for(int i=0;i<chs;i++) {
			gl.glActiveTexture(GL_TEXTURE0+i);
			gl.glBindTexture(gltype, textures.get(name, texIndex+i));
			if(glver==4)
				gl4.glUniform1i(gl23.glGetUniformLocation(pr[0], "mytex["+i+"]"),i);
			else
				gl23.glUniform1i(gl23.glGetUniformLocation(pr[0], "mytex"+i),i);
		}
		gl.glActiveTexture(GL_TEXTURE0);

		if(glver>2)gl23.glBindVertexArray(vaos.get(name));
		
		if(glver<4){
			if(buffers.element.containsKey(name)) {
				gl.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, buffers.element.get(name)[0]);
				if(glver==2 && buffers.ebuffers.containsKey("name")) {
					ByteBuffer buffer=buffers.ebuffers.get(name);
					gl.glBufferData(GL_ELEMENT_ARRAY_BUFFER, buffer.capacity(), buffer, GL_DYNAMIC_DRAW);
				}
			}else System.out.println("AJS MISSING ELEMENT BUFFER "+name);
			if(buffers.array.containsKey(name)) {
				gl.glBindBuffer(GL_ARRAY_BUFFER, buffers.array.get(name)[0]);
				if(glver==2 && buffers.ebuffers.containsKey("name")) {
					ByteBuffer buffer=buffers.abuffers.get(name);
					gl.glBufferData(GL_ARRAY_BUFFER, buffer.capacity(), buffer, GL_DYNAMIC_DRAW);
				}
			}else System.out.println("AJS MISSING ARRAY BUFFER "+name);
			if(glver<4) {
				int[] sizes=vaos.vsizes.get(name);
				gl23.glVertexAttribPointer(0, sizes[0], sizes[1], false, sizes[4]+sizes[5], 0);
				gl23.glEnableVertexAttribArray(0);
				gl23.glVertexAttribPointer(1, sizes[2], sizes[3], false, sizes[4]+sizes[5], sizes[4]);
				gl23.glEnableVertexAttribArray(1);
			}
		}
	
        gl23.glDrawElements(GL_TRIANGLES, count, glElementBufferType, 0);
        if(glver>2)gl23.glBindVertexArray(0);
		gl23.glBindTexture(gltype, 0);
		if(glver==2){
			gl23.glDisableVertexAttribArray(0);
			gl23.glDisableVertexAttribArray(1);
		}
		gl.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
		gl.glBindBuffer(GL_ARRAY_BUFFER, 0);
		//gl23.glDisable(gltype);
	}
	
	public void drawVao(int glDraw, String vname, Buffer vb, String pname) {
		programs.useProgram(pname);
		drawVao(glDraw, vname, vb);
		programs.stopProgram();
	}
	
	public void drawVao(int glDraw, String name, Buffer vertexBuffer) {
		int[] sizes=vaos.vsizes.get(name);
		if(glver>2)gl23.glBindVertexArray(vaos.get(name));
		Buffer elementBuffer=getElementBufferFromVBO(vertexBuffer, (sizes[4]+sizes[5])/getSizeofType(vertexBuffer));
		bindEBOVBO(name, elementBuffer, vertexBuffer);
		if(glver<4) {
			gl23.glVertexAttribPointer(0, sizes[0], sizes[1], false, sizes[4]+sizes[5], 0);
			gl23.glEnableVertexAttribArray(0);
			gl23.glVertexAttribPointer(1, sizes[2], sizes[3], false, sizes[4]+sizes[5], sizes[4]);
			gl23.glEnableVertexAttribArray(1);
		}
		gl23.glDrawElements(glDraw, elementBuffer.capacity(), getGLType(elementBuffer), 0);
		if(glver>2)gl23.glBindVertexArray(0);
		unBindEBOVBO(name);
		if(glver<4) {
			gl23.glDisableVertexAttribArray(0);
			gl23.glDisableVertexAttribArray(1);
		}
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
			for(Enumeration<String> i=handles.keys(); i.hasMoreElements();)handles.remove(i.nextElement());
			for(Enumeration<String> i=pbos.keys(); i.hasMoreElements();)pbos.remove(i.nextElement());
		}
		
		public void dispose(String name) {
			int[] ths=handles.get(name);
			gl.glDeleteTextures(ths.length,ths,0);
			handles.remove(name);
		}
		
		public void disposePbo(String name) {
			int[] phs=pbos.get(name);
			gl.glDeleteBuffers(phs.length,phs,0);
			pbos.remove(name);
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
		
		public void createRgbaTexture(String name, Buffer buffer, int width, int height, int depth, int COMPS, boolean linear) {
			createRgbaTexture(name, 0, buffer, width, height, depth, COMPS, linear);
		}
		
		public void createRgbaTexture(String name, int index, Buffer buffer, int width, int height, int depth, int COMPS, boolean linear) {
			initiate(name, getPixelType(buffer), width, height, depth, COMPS);
			subRgbaTexture(get(name,index),buffer, 0, width, height, depth, COMPS, true, linear);
		}
		
		public void subRgbaTexture(String name, int index, Buffer buffer, int zoffset, int width, int height, int depth, int COMPS, boolean linear) {
			subRgbaTexture(get(name,index),buffer, zoffset, width, height, depth, COMPS, false, linear);
		}
		
		public void initiate(String name, PixelType ptype, int width, int height, int depth, int COMPS) {
			PixelTypeInfo pinfo=new PixelTypeInfo(ptype, COMPS);
			int[] ths=handles.get(name);
			for(int i=0;i<ths.length;i++) {
				gl.glBindTexture(GL_TEXTURE_3D, ths[i]);
				gl23.glTexImage3D(GL_TEXTURE_3D, 0, pinfo.glInternalFormat, width, height, depth, 0, pinfo.glFormat, pinfo.glPixelSize, null);
			}
			
		}

		private void subRgbaTexture(int glTextureHandle, Buffer buffer, int zoffset, int width, int height, int depth, int COMPS, boolean genmipmap, boolean linear) { 
	
			PixelTypeInfo pinfo=new PixelTypeInfo(buffer, COMPS);

			gl.glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
			//gl3.glEnable(GL_TEXTURE_3D);
			gl.glBindTexture(GL_TEXTURE_3D, glTextureHandle);
			if(!genmipmap) {
				gl.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_BASE_LEVEL, 0);
				gl.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MAX_LEVEL, 0);
			}
			gl23.glTexSubImage3D(GL_TEXTURE_3D, 0, 0,0,zoffset, width, height, depth, pinfo.glFormat, pinfo.glPixelSize, buffer);
			int magtype=linear?GL_LINEAR:GL_NEAREST;
			gl23.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MAG_FILTER, magtype);
			gl23.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MIN_FILTER, magtype);//GL_NEAREST_MIPMAP_LINEAR
			gl23.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_BORDER);
			gl23.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_BORDER);
			gl23.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_BORDER);
			gl23.glTexParameterfv(GL_TEXTURE_3D, GL_TEXTURE_BORDER_COLOR, new float[] {0f,0f,0f,0f},0);
			
			if(genmipmap)gl.glGenerateMipmap(GL_TEXTURE_3D);
			gl.glBindTexture(GL_TEXTURE_3D, 0);
			//gl.glDisable(GL_TEXTURE_3D);
		} 
		
		public void loadTexFromPBO(String sameName, int pn, int width, int height, int depth, int offsetSlice, PixelType type, int COMPS, boolean endian, boolean linear) {
			loadTexFromPBO(sameName, pn, sameName, 0, width, height, depth, offsetSlice, type, COMPS, endian, linear);
		}
		
		public void loadTexFromPBO(String pboName, int pn, String texName, int tn, int width, int height, int depth, int offsetSlice, PixelType type, int COMPS, boolean endian, boolean linear) {
			
			int[] phs=pbos.get(pboName);
			int[] ths=handles.get(texName);
			
			PixelTypeInfo pinfo=new PixelTypeInfo(type, COMPS);
			
			//gl3.glEnable(GL_TEXTURE_3D);
			//gl3.glActiveTexture(GL_TEXTURE0);
			gl.glBindBuffer(GL_PIXEL_UNPACK_BUFFER, phs[pn]);
			gl.glBindTexture(GL_TEXTURE_3D, ths[tn]); 
			gl.glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
			if(endian)gl.glPixelStorei(GL_UNPACK_SWAP_BYTES, GL_TRUE);
			gl.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_BASE_LEVEL, 0);
			gl.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MAX_LEVEL, 0);
			gl23.glTexSubImage3D(GL_TEXTURE_3D, 0, 0, 0, 0, width, height, depth, pinfo.glFormat, pinfo.glPixelSize, offsetSlice*pinfo.components*width*height*pinfo.sizeBytes);
			int magtype=linear?GL_LINEAR:GL_NEAREST;
			gl.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MAG_FILTER, magtype);
			gl.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MIN_FILTER, magtype);//GL_NEAREST_MIPMAP_LINEAR
			gl.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_BORDER);
			gl.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_BORDER);
			gl.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_BORDER);
			gl.glTexParameterfv(GL_TEXTURE_3D, GL_TEXTURE_BORDER_COLOR, new float[] {0f,0f,0f,0f},0);
			//gl3.glGenerateMipmap(GL_TEXTURE_3D);
			if(endian)gl.glPixelStorei(GL_UNPACK_SWAP_BYTES, GL_FALSE);
			//gl3.glDisable(GL_TEXTURE_3D);
			gl.glBindTexture(GL_TEXTURE_3D, 0); 
			gl.glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
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
		public Hashtable<String,ByteBuffer> abuffers=new Hashtable<String,ByteBuffer>();
		public Hashtable<String,ByteBuffer> ubuffers=new Hashtable<String,ByteBuffer>();
		public Hashtable<String,ByteBuffer> ebuffers=new Hashtable<String,ByteBuffer>();
		
		
		
		public JCBuffers() {}

		public ByteBuffer newBuffer(int gltype, String name, long size, Buffer buffer) {
			return newBuffer(gltype, name, size, buffer, true);
		}
		
		public ByteBuffer newBuffer(int gltype, String name) {
			return newBuffer(gltype, name, 0, null, false);
		}
		
		public ByteBuffer newBuffer(int gltype, String name, long size, Buffer buffer, boolean define) {
			Hashtable<String,int[]> dict=array;
			Hashtable<String,ByteBuffer> bdict=abuffers;
			if(gltype==GL_UNIFORM_BUFFER) {dict=uniform; bdict=ubuffers;}
			else if(gltype==GL_ELEMENT_ARRAY_BUFFER) {dict=element;bdict=ebuffers;}
			int[] bn=new int[1];
			if(glver==4) {gl4.glCreateBuffers(1, bn, 0);}
			else if(glver==3 || (glver==2 && gltype!=GL_UNIFORM_BUFFER)) gl23.glGenBuffers(1, bn, 0);
			else define=true;
			dict.put(name, bn);
			if(define) {
				boolean write=(buffer==null);
				gl23.glBindBuffer(gltype, bn[0]);
				if(glver==4){
					gl4.glBufferStorage(gltype, size, buffer,  (buffer==null)?(GL_MAP_WRITE_BIT | GL_MAP_PERSISTENT_BIT | GL_MAP_COHERENT_BIT):0);
					gl4.glBindBuffer(gltype,  0);
				}else {
					gl23.glBufferData(gltype, size, buffer, (buffer==null)?GL_DYNAMIC_DRAW:GL_STATIC_DRAW);
				}
				
				if(!write)return null;
				ByteBuffer outbuffer;
				if(glver==4){
					outbuffer= gl4.glMapNamedBufferRange(
							bn[0],
							0,
							size,
							GL_MAP_WRITE_BIT | GL_MAP_PERSISTENT_BIT | GL_MAP_COHERENT_BIT | GL_MAP_INVALIDATE_RANGE_BIT | GL_MAP_INVALIDATE_BUFFER_BIT); // flags
				}else if(glver==3) {
					outbuffer= gl3.glMapBufferRange(
						gltype,
		                0,
		                size,
		                GL_MAP_WRITE_BIT | GL_MAP_INVALIDATE_RANGE_BIT | GL_MAP_INVALIDATE_BUFFER_BIT); // flags
				}else {
					outbuffer=initGL2ByteBuffer((int)size, buffer);
				}
				bdict.put(name, outbuffer);
				gl.glBindBuffer(gltype,  0);
				return outbuffer;
			}else return null;
		}
		
		private ByteBuffer initGL2ByteBuffer(long size, Buffer buffer) {
			ByteBuffer outbuffer=GLBuffers.newDirectByteBuffer((int)size);
			if(buffer!=null) {
				if(buffer instanceof FloatBuffer)outbuffer.asFloatBuffer().put((FloatBuffer)buffer);
				if(buffer instanceof ShortBuffer)outbuffer.asShortBuffer().put((ShortBuffer)buffer);
				if(buffer instanceof IntBuffer)outbuffer.asIntBuffer().put((IntBuffer)buffer);
				if(buffer instanceof ByteBuffer)outbuffer.put((ByteBuffer)buffer);
			}
			return outbuffer;
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
			Hashtable<String, ByteBuffer> bdict=abuffers;
			if(gltype==GL_UNIFORM_BUFFER) bdict=ubuffers;
			else if(gltype==GL_ELEMENT_ARRAY_BUFFER)bdict=ebuffers;
			return bdict.get(name);
		}
		
		public void loadIdentity(String name) {
			loadIdentity(name, 0);
		}
		
		public void loadIdentity(String name, int offset) {
			loadMatrix(name, FloatUtil.makeIdentity(new float[16]), offset);
		}
		
		public void loadMatrix(String name, float[] matrix) {
			loadMatrix(name, matrix, 0);
		}
		
		public void loadMatrix(String name, float[] matrix, int offset) {
			ByteBuffer buffer=(ByteBuffer)ubuffers.get(name);
			buffer.rewind();
			for (int i = 0; i < 16; i++) {
	            buffer.putFloat(offset + i * 4, matrix[i]);
	        }
	        buffer.rewind();
			//buffer.rewind(); buffer.asFloatBuffer().rewind();
			//buffer.asFloatBuffer().put(matrix);
			//buffer.rewind(); buffer.asFloatBuffer().rewind();
			//if(glver==2) {
			//	gl2.glBindBuffer(GL_UNIFORM_BUFFER, uniform.get(name)[0]);
			//	gl2.glBufferData(GL_UNIFORM_BUFFER, buffer.capacity(), buffer, GL_DYNAMIC_DRAW);
			//	gl2.glBindBuffer(GL_UNIFORM_BUFFER, 0);
			//
		}
		
		public void bindBuffer(int gltype, String name, int binding) {
			if(gltype==GL_UNIFORM_BUFFER) {
				if(glver>2)
					gl23.glBindBufferBase(gltype, binding, uniform.get(name)[0]);
				else {
					int[] pr=new int[1];gl.glGetIntegerv(GL_CURRENT_PROGRAM, pr,0);
					//gl3.glUniform1i(gl3.glGetUniformLocation(pr[0], "mytex"),0);
					JCPrograms.Program program=programs.findProgram(pr[0]);
					if(name.contentEquals("global")) {
						int loc=(program==null?gl2.glGetUniformLocation(pr[0], "proj"):program.getLocation("proj"));
						gl2.glUniformMatrix4fv(loc, 2, false, ubuffers.get(name).asFloatBuffer());
					}
					if(name.contentEquals("model") || name.contentEquals("idm") || name.contentEquals("modelr")) {
						int loc=(program==null?gl2.glGetUniformLocation(pr[0], "model"):program.getLocation("model"));
						gl2.glUniformMatrix4fv(loc, 1, false, ubuffers.get(name).asFloatBuffer());
					}
					if(name.contentEquals("lut")) {
						int loc=(program==null?gl2.glGetUniformLocation(pr[0], "luts"):program.getLocation("luts"));
						gl2.glUniform3fv(loc, 6, ubuffers.get(name).asFloatBuffer());
					}
				}
				return;
			}

			Hashtable<String, int[]> dict=array;
			if(gltype==GL_ELEMENT_ARRAY_BUFFER)dict=element;
			gl.glBindBuffer(gltype, dict.get(name)[0]);
		}
		
		public void unBindBuffer(int gltype, int binding) {
			if(gltype==GL_UNIFORM_BUFFER) {
				if(glver==2)return;
				gl23.glBindBufferBase(gltype, binding, 0);
				return;
			}
			gl.glBindBuffer(gltype, 0);
		}
		
		public void dispose() {
			for(int i=0;i<3;i++) {
				Hashtable<String, int[]> dict=array;
				Hashtable<String, ByteBuffer> bdict=abuffers;
				int gltype=GL_ARRAY_BUFFER;
				if(i==1) {dict=uniform; bdict=ubuffers; gltype=GL_UNIFORM_BUFFER;}
				else if(i==2) {dict=element; bdict=ebuffers; gltype=GL_ELEMENT_ARRAY_BUFFER;}
				for(Enumeration<String> j=dict.keys(); j.hasMoreElements();) {
					String name=j.nextElement();
					int[] phs=dict.get(name);
					if(bdict.get(name)!=null){
						if(glver==4){gl4.glUnmapNamedBuffer(phs[0]);}
						else if(glver==3) {
							gl.glBindBuffer(gltype, phs[0]);
							gl.glUnmapBuffer(gltype);
							gl.glBindBuffer(gltype, 0);
						}
						bdict.remove(name);
					}
					gl.glDeleteBuffers(phs.length,phs,0);
					dict.remove(name);
				}
			}
		}
	}

	class JCVaos{
		
		public Hashtable<String, int[]> handles =new Hashtable<String, int[]>();
		public Hashtable<String, int[]> vsizes =new Hashtable<String, int[]>();
		
		public JCVaos() {}
		
		public void newVao(String name, int size1, int gltype1, int size2, int gltype2) {
			
			int[] vhs=new int[1];
			int sizeoftype1=getSizeofType(gltype1)*size1;
			int sizeoftype2=getSizeofType(gltype2)*size2;
			if(glver==4){
				gl4.glCreateVertexArrays(vhs.length, vhs, 0);
				int vao=vhs[0];
				gl4.glVertexArrayAttribBinding(vao, 0, 0);//modelcoords
				gl4.glVertexArrayAttribBinding(vao, 1, 0);//texcoords
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
			else if(glver==3){
				gl3.glGenVertexArrays(vhs.length, vhs, 0);
			}else {
				//GL2
			}
			handles.put(name, vhs);
			vsizes.put(name, new int[] {size1,gltype1,size2,gltype2,sizeoftype1,sizeoftype2});
		}
		
		public int get(String name) {
			int[] hs=handles.get(name);
			if(hs==null)return 0;
			return hs[0];
		}
		
		public void dispose() {
			for(Enumeration<String> j=handles.keys(); j.hasMoreElements();) {
				String name=j.nextElement();
				int[] vhs=handles.get(name);
				if(glver>2)gl23.glDeleteVertexArrays(vhs.length,vhs,0);
				handles.remove(name);
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
        	gl23.glUseProgram(programs.get(name).name);
        }
        
        public void stopProgram() {
        	gl23.glUseProgram(0);
        }
        
        public void dispose() {
        	for(Enumeration<String> j=programs.keys(); j.hasMoreElements();) {
				String name=j.nextElement();
				programs.get(name).dispose();
				programs.remove(name);
			}
        }
        
        public Program findProgram(int handle) {
        	for(Program program : programs.values()) {
        		if(handle==program.name)return program;
        	}
        	return null;
        }
        
        class Program{
        	
        	int name=0;
    		public Hashtable<String, Integer> locations =new Hashtable<String, Integer>();
        	
        	public Program(String root, String vertex, String fragment) {
        		
	        	String add="120";
	        	String radd="/GL2";
	        	if(glver==3) {add="3"; radd="/GL3";}
	        	if(glver==4) {add="4"; radd="/GL4";}
	        	
	            ShaderCode vertShader = ShaderCode.create(gl23, GL_VERTEX_SHADER, this.getClass(), root+radd, null, vertex+add,
	                    "vert", null, true);
	            ShaderCode fragShader = ShaderCode.create(gl23, GL_FRAGMENT_SHADER, this.getClass(), root+radd, null, fragment+add,
	                    "frag", null, true);
	
	            ShaderProgram shaderProgram = new ShaderProgram();
	
	            shaderProgram.add(vertShader);
	            shaderProgram.add(fragShader);
	
	            shaderProgram.init(gl23);
	            PrintStream ps=null;
	            File temp=null;
	            try {
	            	temp=File.createTempFile("ImageJ-JOGLCanvas-shader-err", "txt");
					ps=new PrintStream(temp);
				} catch (IOException e) {
					e.printStackTrace();
				}
	            shaderProgram.link(gl23, ps);
	            if(glver==3 && !shaderProgram.validateProgram(gl23, ps)) {
	            	System.out.println("Going to 330");
	            	add="330";
	            	vertShader = ShaderCode.create(gl23, GL_VERTEX_SHADER, this.getClass(), root+radd, null, vertex+add,
	                        "vert", null, true);
	                fragShader = ShaderCode.create(gl23, GL_FRAGMENT_SHADER, this.getClass(), root+radd, null, fragment+add,
	                        "frag", null, true);
	
	                shaderProgram = new ShaderProgram();
	
	                shaderProgram.add(vertShader);
	                shaderProgram.add(fragShader);
	
	                shaderProgram.init(gl23);
	                shaderProgram.link(gl23, System.err);
	            }
	            if(!shaderProgram.validateProgram(gl23, System.err)) {
	            	System.out.println("Shader "+add+" failed");
					try {
						FileReader fr = new FileReader(temp);
						System.err.println("Shader 300 es error:");
						int i; 
						while ((i=fr.read()) != -1) 
							System.err.print((char) i);
						fr.close();
						System.err.println("");
					} catch (Exception e) {
						e.printStackTrace();
					}
	            }
	            
	            name=shaderProgram.program();
	            
	            if(glver>2) {
	        		gl23.glUniformBlockBinding(name, gl23.glGetUniformBlockIndex(name, "Transform0"), 1);
	        		gl23.glUniformBlockBinding(name, gl23.glGetUniformBlockIndex(name, "Transform1"), 2);
	        		if(fragment.equals("texture"))gl23.glUniformBlockBinding(name, gl23.glGetUniformBlockIndex(name, "lutblock"), 3);
	            }else{
	            	addLocation("proj");
	            	addLocation("model");
	            	if(fragment.equals("texture"))addLocation("luts");
	            }
            
        	}
        	
        	public Program(int pname, Hashtable<String, Integer> locs) {
        		name=pname;
        		locations=locs;
        	}
        	
        	public void addLocation(String var) {
            	locations.put(var, gl23.glGetUniformLocation(name, var));
        	}
        	
        	public int getLocation(String var) {
        		return locations.get(var);
        	}
        	
        	public void dispose() {
            	gl23.glDeleteProgram(name);
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
	
	static class PixelTypeInfo{
		public int glInternalFormat;
		public int glPixelSize;
		public int sizeBytes;
		public int components;
		public int glFormat;
		
		public PixelTypeInfo(PixelType type, int COMPS) {
			glInternalFormat=COMPS==4?GL_RGBA32F:COMPS==3?GL_RGB32F:COMPS==2?GL_RG32F:GL_R32F;
			glPixelSize=GL_FLOAT;
			sizeBytes=Buffers.SIZEOF_FLOAT;
			components=COMPS;
			glFormat=COMPS==4?GL_RGBA:COMPS==3?GL_RGB:COMPS==2?GL_RG:GL_RED;
			
			if(type==PixelType.SHORT) {
				glInternalFormat=COMPS==4?GL_RGBA16:COMPS==3?GL_RGB16:COMPS==2?GL_RG16:GL_R16;
				glPixelSize=GL_UNSIGNED_SHORT;
				sizeBytes=Buffers.SIZEOF_SHORT;
			}else if(type==PixelType.BYTE) {
				glInternalFormat=COMPS==4?GL_RGBA8:COMPS==3?GL_RGB8:COMPS==2?GL_RG8:GL_R8;
				glPixelSize=GL_UNSIGNED_BYTE;
				sizeBytes=Buffers.SIZEOF_BYTE;
			}else if(type==PixelType.INT_RGB10A2) {
				glInternalFormat=GL_RGB10_A2;
				glPixelSize=GL_UNSIGNED_INT_2_10_10_10_REV;
				sizeBytes=Buffers.SIZEOF_INT;
				components=1;
				glFormat=GL_RGBA;
			}else if(type==PixelType.INT_RGBA8) {
				glInternalFormat=GL_RGBA8;
				glPixelSize=GL_UNSIGNED_INT_8_8_8_8;
				sizeBytes=Buffers.SIZEOF_INT;
				components=1;
				glFormat=GL_RGBA;
			}
		}
		
		public PixelTypeInfo(Buffer buffer, int COMPS) {
			this(getPixelType(buffer), COMPS);
		}
	}
	
	protected static PixelType getPixelType(Buffer buffer) {
		PixelType type=PixelType.FLOAT;
		if(buffer instanceof ShortBuffer) {
			type=PixelType.SHORT;
		}else if(buffer instanceof ByteBuffer) {
			type=PixelType.BYTE;
		}else if(buffer instanceof IntBuffer) {
			//type=PixelType.INT_RGB10A2;
			type=PixelType.INT_RGBA8;
		}
		return type;
	}
}
