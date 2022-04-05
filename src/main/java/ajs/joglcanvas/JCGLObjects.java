package ajs.joglcanvas;

import static com.jogamp.opengl.GL2.*;

import java.io.File;
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

public class JCGLObjects {
	
	//enum GLVer{GL2, GL3, GL4};
	public int glver=2;
	private GL gl=null;
	private GL2GL3 gl23=null;
	private GL2 gl2=null;
	private GL3 gl3=null;
	private GL4 gl4=null;
	public Hashtable<String,JCTexture> textures=new Hashtable<String,JCTexture>();
	public Hashtable<String,JCPbo> pbos=new Hashtable<String,JCPbo>();
	public Hashtable<String,JCBuffer> buffers=new Hashtable<String,JCBuffer>();
	public Hashtable<String,JCVao> vaos=new Hashtable<String,JCVao>();
	public Hashtable<String, JCProgram> programs =new Hashtable<String, JCProgram>();
	
	public JCGLObjects() {}
	
	public JCGLObjects(GLAutoDrawable drawable) {
		setGL(drawable);
	}

	public JCGLObjects(GL gl) {
		setGL(gl);
	}
	
	public void dispose() {
		for(JCTexture obj:textures.values())obj.dispose();
		for(JCPbo obj:pbos.values())obj.dispose();
		for(JCBuffer obj:buffers.values())obj.dispose();
		for(JCVao obj:vaos.values())obj.dispose();
		for(JCProgram obj:programs.values())obj.dispose();
	}
	
	public void setGL(GLAutoDrawable drawable) {
		setGL(drawable.getGL());
	}
	
	public void setGL(GL gl) {
		boolean dosv=gl23==null;
		this.gl=gl;
		if(dosv)setGLVer();
		if(glver==2) {gl2=gl.getGL2();gl23=gl2;}
		if(glver==3) {gl23=gl.getGL3();gl3=gl.getGL3();}
		if(glver==4) {gl23=gl.getGL4();gl4=gl.getGL4();}
	}
	
	public GL2GL3 getGL2GL3() {
		return gl23;
	}
	
	public void setGLVer() {
		String version=gl.glGetString(GL_VERSION);
		//System.out.println("JCGLO gl version "+version);
		float v=0f;
		int st=0;
		if(version.startsWith("OpenGL ES"))st=10;
		try{
			v=Float.parseFloat(version.substring(st, st+3));
		}catch(Exception e) {
			System.out.println("Could not parse version: "+version);
		}
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
	
	public void newTexture(String name, boolean is3d) {
		newTexture(name, 1, is3d);
	}
	
	public void newTexture(String name, int size, boolean is3d) {
		JCTexture oldtex=textures.put(name, new JCTexture(size, is3d));
		if(oldtex!=null)oldtex.dispose();
	}
	
	public boolean hasTexture(String name) {
		return textures.containsKey(name);
	}
	
	public int getTextureHandle(String name, int index) {
		return textures.get(name).handles[index];
	}
	
	public JCTexture getTexture(String name) {
		return textures.get(name);
	}
	
	public void disposeTexture(String name) {
		if(textures.get(name)==null)return;
		textures.get(name).dispose();
	}
	
	/*
	 * Texture handling loading
	 */
	
	public void loadTexFromPbo(String sameName, int pn,int width, int height, int depth, int offsetSlice, PixelType type, int COMPS, boolean endian, boolean linear) {
		loadTexFromPbo(sameName, pn, sameName, 0, width, height, depth, offsetSlice, type, COMPS, endian, linear);
	}
	
	//public void loadTexFromPbo(int poop, int texHandle, int pboHandle, int width, int height, int depth, int offsetSlice, PixelType type, int COMPS, boolean endian, boolean linear) {
	public void loadTexFromPbo(String pboName, int pn, String texName, int tn, int width, int height, int depth, int offsetSlice, PixelType type, int COMPS, boolean endian, boolean linear) {
			
		int texHandle=getTextureHandle(texName, tn), pboHandle=getPboHandle(pboName, pn);
		boolean is3d=getTexture(texName).is3d;
		
		PixelTypeInfo pinfo=new PixelTypeInfo(type, COMPS);
		
		int textype=is3d?GL_TEXTURE_3D:GL_TEXTURE_2D;
		
		//gl3.glEnable(GL_TEXTURE_3D);
		//gl3.glActiveTexture(GL_TEXTURE0);
		gl.glBindBuffer(GL_PIXEL_UNPACK_BUFFER, pboHandle);
		gl.glBindTexture(textype, texHandle); 
		gl.glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
		if(endian)gl.glPixelStorei(GL_UNPACK_SWAP_BYTES, GL_TRUE);
		gl.glTexParameteri(textype, GL_TEXTURE_BASE_LEVEL, 0);
		gl.glTexParameteri(textype, GL_TEXTURE_MAX_LEVEL, 0);
		if(is3d)
			gl23.glTexSubImage3D(GL_TEXTURE_3D, 0, 0, 0, 0, width, height, depth, pinfo.glFormat, pinfo.glPixelSize, offsetSlice*pinfo.components*width*height*pinfo.sizeBytes);
		else
			gl23.glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height, pinfo.glFormat, pinfo.glPixelSize, offsetSlice*pinfo.components*width*height*pinfo.sizeBytes);

		int magtype=linear?GL_LINEAR:GL_NEAREST;
		gl.glTexParameteri(textype, GL_TEXTURE_MAG_FILTER, magtype);
		gl.glTexParameteri(textype, GL_TEXTURE_MIN_FILTER, magtype);//GL_NEAREST_MIPMAP_LINEAR
		gl.glTexParameteri(textype, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_BORDER);
		gl.glTexParameteri(textype, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_BORDER);
		gl.glTexParameteri(textype, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_BORDER);
		gl.glTexParameterfv(textype, GL_TEXTURE_BORDER_COLOR, new float[] {0f,0f,0f,0f},0);
		//gl3.glGenerateMipmap(GL_TEXTURE_3D);
		if(endian)gl.glPixelStorei(GL_UNPACK_SWAP_BYTES, GL_FALSE);
		//gl3.glDisable(GL_TEXTURE_3D);
		gl.glBindTexture(textype, 0); 
		gl.glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
	}
	
	public void drawTexVao(String name, int glElementBufferType, int count, int chs) {
		drawTexVao(name, 0, glElementBufferType, count, chs);
	}
	
	public void drawTexVao(String name, int index, Buffer vertexBuffer) {
		vertexBuffer.rewind();
		int[] sizes=vaos.get(name).vsizes;
		Buffer eb=getElementBufferFromVBO(vertexBuffer, (sizes[4]+sizes[5])/getSizeofType(vertexBuffer));
		eb.rewind();
		drawTexVaoWithEBOVBO(name, index, eb, vertexBuffer);
	}
	
	public void drawTexVao(String name, int index, Buffer vb, String pname) {
		useProgram(pname);
		drawTexVao(name, index, vb);
		stopProgram();
	}
	
	/**
	 * 
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
		if(buffers.containsKey(name+GL_ELEMENT_ARRAY_BUFFER))gl.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, getBufferHandle(GL_ELEMENT_ARRAY_BUFFER,name));
		if(buffers.containsKey(name+GL_ARRAY_BUFFER))gl.glBindBuffer(GL_ARRAY_BUFFER, getBufferHandle(GL_ARRAY_BUFFER,name));
		gl.glBufferData(GL_ELEMENT_ARRAY_BUFFER, elementBuffer.capacity()*getSizeofType(elementBuffer), elementBuffer, GL_DYNAMIC_DRAW);
		gl.glBufferData(GL_ARRAY_BUFFER, vertexBuffer.capacity()*getSizeofType(vertexBuffer), vertexBuffer, GL_DYNAMIC_DRAW);
	}
	
	private void unBindEBOVBO(String name) {
		if(buffers.containsKey(name+GL_ELEMENT_ARRAY_BUFFER))gl.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
		if(buffers.containsKey(name+GL_ARRAY_BUFFER))gl.glBindBuffer(GL_ARRAY_BUFFER, 0);
	}

	public void drawTexVao(String name, int texIndex, int glElementBufferType, int count, int chs) {
		int gltype=GL_TEXTURE_3D;
		if(!getTexture(name).is3d)gltype=GL_TEXTURE_2D;
		//gl23.glEnable(gltype);
		//gl3.glActiveTexture(GL_TEXTURE0);
		//gl3.glBindTexture(gltype, textures.get(name, texIndex));
		int[] pr=new int[1];gl.glGetIntegerv(GL_CURRENT_PROGRAM, pr,0);
		//gl3.glUniform1i(gl3.glGetUniformLocation(pr[0], "mytex"),0);

		//if(glver==2)gl2.glEnable(gltype);
		int[] tns=new int[chs];
		for(int i=0;i<chs;i++) {
			gl.glActiveTexture(GL_TEXTURE0+i);
			//if(glver==2)gl2.glEnable(gltype);
			gl.glBindTexture(gltype, getTextureHandle(name, texIndex+i));
			tns[i]=i;
		}
		gl.glActiveTexture(GL_TEXTURE0);
		gl23.glUniform1iv(gl23.glGetUniformLocation(pr[0], "mytex"),chs, tns,0);

		if(glver>2)gl23.glBindVertexArray(vaos.get(name).handle);
		
		if(glver<4){
			bindBuffer(GL_ELEMENT_ARRAY_BUFFER, name);
			bindBuffer(GL_ARRAY_BUFFER, name);
			int[] sizes=vaos.get(name).vsizes;
			gl23.glVertexAttribPointer(0, sizes[0], sizes[1], false, sizes[4]+sizes[5], 0);
			gl23.glEnableVertexAttribArray(0);
			gl23.glVertexAttribPointer(1, sizes[2], sizes[3], false, sizes[4]+sizes[5], sizes[4]);
			gl23.glEnableVertexAttribArray(1);
		}
	
        gl23.glDrawElements(GL_TRIANGLES, count, glElementBufferType, 0);
        if(glver>2)gl23.glBindVertexArray(0);
		gl23.glBindTexture(gltype, 0);
		if(glver==2){
			gl2.glDisable(gltype);
		}
		gl.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
		gl.glBindBuffer(GL_ARRAY_BUFFER, 0);
		//gl23.glDisable(gltype);
	}
	
	public void drawVao(int glDraw, String vname, Buffer vb, String pname) {
		useProgram(pname);
		drawVao(glDraw, vname, vb);
		stopProgram();
	}
	
	public void drawVao(int glDraw, String name, Buffer vertexBuffer) {
		int[] sizes=vaos.get(name).vsizes;
		if(glver>2)gl23.glBindVertexArray(vaos.get(name).handle);
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
	}
	
	class JCTexture{
		
		public int[] handles;
		public boolean is3d;
		
		public JCTexture(int size, boolean is3d) {
			handles=new int[size];
			gl.glGenTextures(size, handles, 0);
			this.is3d=is3d;
		}
		
		public void dispose() {
			gl.glDeleteTextures(handles.length,handles,0);
		}
		
		public int getTexture(int index) {
			return handles[index];
		}

		public int getTextureLength() {
			if(handles==null)return 0;
			return handles.length;
		}
		
		public void createRgbaTexture(Buffer buffer, int width, int height, int depth, int COMPS, boolean linear) {
			createRgbaTexture(0, buffer, width, height, depth, COMPS, linear);
		}
		
		public void createRgbaTexture(int index, Buffer buffer, int width, int height, int depth, int COMPS, boolean linear) {
			initiate(getPixelType(buffer), width, height, depth, COMPS);
			subRgbaTexture(handles[index],buffer, 0, width, height, depth, COMPS, true, linear);
		}
		
		public void subRgbaTexture(int index, Buffer buffer, int zoffset, int width, int height, int depth, int COMPS, boolean linear) {
			subRgbaTexture(handles[index],buffer, zoffset, width, height, depth, COMPS, false, linear);
		}
		
		public void initiate(PixelType ptype, int width, int height, int depth, int COMPS) {
			PixelTypeInfo pinfo=new PixelTypeInfo(ptype, COMPS);
			int[] ths=handles;
			for(int i=0;i<ths.length;i++) {
				if(is3d) {
					gl.glBindTexture(GL_TEXTURE_3D, ths[i]);
					gl23.glTexImage3D(GL_TEXTURE_3D, 0, pinfo.glInternalFormat, width, height, depth, 0, pinfo.glFormat, pinfo.glPixelSize, null);
				}else {
					gl.glBindTexture(GL_TEXTURE_2D, ths[i]);
					gl23.glTexImage2D(GL_TEXTURE_2D, 0, pinfo.glInternalFormat, width, height, 0, pinfo.glFormat, pinfo.glPixelSize, null);
				}
			}
			
		}

		private void subRgbaTexture(int glTextureHandle, Buffer buffer, int zoffset, int width, int height, int depth, int COMPS, boolean genmipmap, boolean linear) { 
	
			PixelTypeInfo pinfo=new PixelTypeInfo(buffer, COMPS);
			
			int textype=is3d?GL_TEXTURE_3D:GL_TEXTURE_2D;

			gl.glBindTexture(textype, glTextureHandle);
			if(!genmipmap) {
				gl.glTexParameteri(textype, GL_TEXTURE_BASE_LEVEL, 0);
				gl.glTexParameteri(textype, GL_TEXTURE_MAX_LEVEL, 0);
			}
			if(is3d)
				gl23.glTexSubImage3D(GL_TEXTURE_3D, 0, 0,0,zoffset, width, height, depth, pinfo.glFormat, pinfo.glPixelSize, buffer);
			else
				gl23.glTexSubImage2D(GL_TEXTURE_2D, 0, 0,0, width, height, pinfo.glFormat, pinfo.glPixelSize, buffer);
				
			int magtype=linear?GL_LINEAR:GL_NEAREST;
			gl23.glTexParameteri(textype, GL_TEXTURE_MAG_FILTER, magtype);
			gl23.glTexParameteri(textype, GL_TEXTURE_MIN_FILTER, magtype);//GL_NEAREST_MIPMAP_LINEAR
			gl23.glTexParameteri(textype, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_BORDER);
			gl23.glTexParameteri(textype, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_BORDER);
			gl23.glTexParameteri(textype, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_BORDER);
			gl23.glTexParameterfv(textype, GL_TEXTURE_BORDER_COLOR, new float[] {0f,0f,0f,0f},0);
			
			if(genmipmap)gl.glGenerateMipmap(textype);
			gl.glBindTexture(textype, 0);
		} 
	}

	/*
	 * PBO functions
	 */
	
	public void newPbo(String name, int size) {
		if(pbos.containsKey(name)) {
			pbos.get(name).newPbo(size);
		}else pbos.put(name,new JCPbo(size));
	}
	
	public boolean hasPbo(String name) {
		return pbos.containsKey(name);
	}
	
	public JCPbo getPbo(String name) {
		return pbos.get(name);
	}
	
	public int getPboHandle(String name, int index) {
		return pbos.get(name).pbos[index];
	}
	
	public int getPboLength(String name) {
		if(!pbos.containsKey(name))return 0;
		if(pbos.get(name).pbos==null)return 0;
		return pbos.get(name).pbos.length;
	}
	
	public void disposePbo(String name) {
		if(pbos.get(name)==null)return;
		pbos.get(name).dispose();
	}
	
	class JCPbo{
		
		public int[] pbos;
		
		public JCPbo(int size) {
			pbos=new int[size];
		}
		
		public boolean hasPbo() {return (pbos!=null && pbos.length>0);}
		
		public void newPbo(int size) {
			if(pbos!=null && pbos.length>0) {
				dispose();
			}
			pbos=new int[size];
		}
		
		public int getPbo(int index) {
			return pbos[index];
		}

		public int getPboLength(String name) {
			if(pbos==null)return 0;
			return pbos.length;
		}
		
		public void dispose() {
			gl.glDeleteBuffers(pbos.length,pbos,0);
		}
		
		public void updateRgbaPBO(int index, Buffer buffer) {
			updateSubRgbaPBO(index, buffer, 0, 0, buffer.limit(), buffer.limit());
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
		public void updateSubRgbaPBO(int index, Buffer buffer, int bufferOffset, int PBOoffset, int length, int bsize) {
			int[] phs=pbos;
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
	
	/*
	 * Buffer functions
	 */
	
	public ByteBuffer newBuffer(int gltype, String name, long size, Buffer buffer) {
		JCBuffer buf=new JCBuffer(gltype,  name, size, buffer);
		JCBuffer oldbuf=buffers.put(name+gltype, buf);
		if(oldbuf!=null)oldbuf.dispose();
		return buf.buffer;
	}
	
	public ByteBuffer newBuffer(int gltype, String name, Buffer buffer) {
		JCBuffer buf=new JCBuffer(gltype,  name, buffer);
		JCBuffer oldbuf=buffers.put(name+gltype, buf);
		if(oldbuf!=null)oldbuf.dispose();
		return buf.buffer;
	}
	
	public void newBuffer(int gltype, String name) {
		buffers.put(name+gltype, new JCBuffer(gltype,  name));
	}
	
	public JCBuffer getBuffer(int gltype, String name) {
		return buffers.get(name+gltype);
	}
	
	public JCBuffer getUniformBuffer(String name) {
		return buffers.get(name+GL_UNIFORM_BUFFER);
	}
	
	public int getBufferHandle(int gltype, String name) {
		JCBuffer buf=buffers.get(name+gltype);
		if(buf==null) return 0;
		return buf.handle;
	}
	
	public ByteBuffer getDirectBuffer(int gltype, String name) {
		JCBuffer buf=buffers.get(name+gltype);
		if(buf==null) return null;
		return buf.buffer;
	}
	
	public void bindUniformBuffer(String name, int binding) {
		bindBuffer(GL_UNIFORM_BUFFER, name, binding);
	}
	
	public void bindBuffer(int gltype, String name) {
		bindBuffer(gltype, name, 0);
	}
	
	public void bindBuffer(int gltype, String name, int binding) {
		JCBuffer buf=buffers.get(name+gltype);
		if(buf!=null)buf.bindBuffer(binding);
		else System.err.println("AJS Could not find buffer "+name+" "+gltype);
	}
	
	public void unBindBuffer(int gltype, int binding) {
		if(gltype==GL_UNIFORM_BUFFER) {
			if(glver==2)return;
			gl23.glBindBufferBase(gltype, binding, 0);
			return;
		}
		gl.glBindBuffer(gltype, 0);
	}
	
	public void unBindBuffer(int gltype) {
		unBindBuffer(gltype, 0);
	}

	class JCBuffer{

		String name;
		String bindName;
		public int handle;
		public ByteBuffer buffer=null;
		public int gltype;
		
		public JCBuffer(int gltype, String name, long size, Buffer buffer) {
			this(gltype, name, size, buffer, true);
		}
		
		public JCBuffer(int gltype, String name, Buffer buffer) {
			this(gltype, name, buffer.capacity()*getSizeofType(buffer), buffer, true);
		}
		
		public JCBuffer(int gltype, String name) {
			this(gltype, name, 0, null, false);
		}
		
		public JCBuffer(int gltype, String name, long size, Buffer buffer, boolean define) {
			this.gltype=gltype;
			this.name=name;
			this.bindName=name;
			int[] bn=new int[1];
			boolean write=(buffer==null);
			if(glver==4) {gl4.glCreateBuffers(1, bn, 0);}
			else if(glver==3 || (glver==2 && gltype!=GL_UNIFORM_BUFFER)) gl23.glGenBuffers(1, bn, 0);
			else {define=true; write=true;}
			handle=bn[0];
			if(define) {
				gl23.glBindBuffer(gltype, handle);
				if(glver==4){
					gl4.glBufferStorage(gltype, size, buffer,  (buffer==null)?(GL_MAP_WRITE_BIT | GL4.GL_MAP_PERSISTENT_BIT | GL4.GL_MAP_COHERENT_BIT):0);
					gl4.glBindBuffer(gltype,  0);
				}else if(glver==3 || (glver==2 && gltype!=GL_UNIFORM_BUFFER)){
					gl23.glBufferData(gltype, size, buffer, (buffer==null)?GL_DYNAMIC_DRAW:GL_STATIC_DRAW);
				}
				
				if(!write)return;
				if(glver==4){
					this.buffer= gl4.glMapNamedBufferRange(
							bn[0],
							0,
							size,
							GL_MAP_WRITE_BIT | GL4.GL_MAP_PERSISTENT_BIT | GL4.GL_MAP_COHERENT_BIT | GL_MAP_INVALIDATE_RANGE_BIT | GL_MAP_INVALIDATE_BUFFER_BIT); // flags
				}else {//if(glver==3) {
				//	outbuffer= gl3.glMapBufferRange(
				//		gltype,
		         //       0,
		        //        size,
		        //        GL_MAP_WRITE_BIT | GL_MAP_INVALIDATE_RANGE_BIT | GL_MAP_INVALIDATE_BUFFER_BIT); // flags
				//}else {
					this.buffer=initGL2ByteBuffer((int)size, buffer);
				}
				gl.glBindBuffer(gltype,  0);
			}
		}
		
		private ByteBuffer initGL2ByteBuffer(long size, Buffer buffer) {
			if(buffer!=null && buffer instanceof ByteBuffer && buffer.isDirect() && buffer.capacity()==size)return (ByteBuffer)buffer;
			ByteBuffer outbuffer=GLBuffers.newDirectByteBuffer((int)size);
			if(buffer!=null) {
				if(buffer instanceof FloatBuffer)outbuffer.asFloatBuffer().put((FloatBuffer)buffer);
				if(buffer instanceof ShortBuffer)outbuffer.asShortBuffer().put((ShortBuffer)buffer);
				if(buffer instanceof IntBuffer)outbuffer.asIntBuffer().put((IntBuffer)buffer);
				if(buffer instanceof ByteBuffer)outbuffer.put((ByteBuffer)buffer);
			}
			return outbuffer;
		}
		
		public void setBindName(String bn) {bindName=bn;}
		
		public void loadIdentity() {
			loadIdentity(0);
		}
		
		public void loadIdentity(int offset) {
			loadMatrix(FloatUtil.makeIdentity(new float[16]), offset);
		}
		
		public void loadMatrix(float[] matrix) {
			loadMatrix(matrix, 0);
		}
		
		public void loadMatrix(float[] matrix, int offset) {
			((Buffer)buffer).rewind();
			for (int i = 0; i < 16; i++) {
	            buffer.putFloat(offset + i * 4, matrix[i]);
	        }
			((Buffer)buffer).rewind();
		}
		
		public void bindBuffer() {
			bindBuffer(0);
		}
		public void bindBuffer(int binding) {
			if(gltype==GL_UNIFORM_BUFFER) {
				if(glver>2) {
					if(glver==3 && buffer!=null) {
						gl.glBindBuffer(gltype, handle);
						gl.glBufferSubData(gltype, 0, buffer.capacity(), buffer);
						gl.glBindBuffer(gltype, 0);
					}
					gl23.glBindBufferBase(gltype, binding, handle);
				}else {
					int[] pr=new int[1];gl.glGetIntegerv(GL_CURRENT_PROGRAM, pr,0);
					//gl3.glUniform1i(gl3.glGetUniformLocation(pr[0], "mytex"),0);
					JCProgram program=findProgram(pr[0]);
					if(bindName.contentEquals("global") || bindName.contentEquals("proj")) {
						int loc=(program==null?gl2.glGetUniformLocation(pr[0], "proj"):program.getLocation("proj"));
						gl2.glUniformMatrix4fv(loc, 2, false, buffer.asFloatBuffer());
					}
					if(bindName.contentEquals("model") || bindName.contentEquals("idm")) {
						int loc=(program==null?gl2.glGetUniformLocation(pr[0], "model"):program.getLocation("model"));
						gl2.glUniformMatrix4fv(loc, 1, false, buffer.asFloatBuffer());
					}
					if(bindName.contentEquals("lut")) {
						int loc=(program==null?gl2.glGetUniformLocation(pr[0], "luts"):program.getLocation("luts"));
						gl2.glUniform4fv(loc, 6, buffer.asFloatBuffer());
					}
				}
				return;
			}else {
				gl.glBindBuffer(gltype, handle);
				if(glver<4 && buffer!=null) {
					((Buffer)buffer).rewind();
					gl.glBufferSubData(gltype, 0L, (long)buffer.limit(), buffer);
				}
			}
		}
		
		public void dispose() {
			gl.glDeleteBuffers(1, new int[] {handle},0);
		}
	}


	/*
	 * VAO functions
	 */
	
	public void newVao(String name, int size1, int gltype1, int size2, int gltype2) {
		JCVao oldvao=vaos.put(name, new JCVao(name, size1, gltype1, size2, gltype2));
		if(oldvao!=null)oldvao.dispose();
	}
	
	class JCVao{
		
		public String name;
		public int handle;
		public int[] vsizes;
		
		public JCVao(String name, int size1, int gltype1, int size2, int gltype2) {
			
			this.name=name;
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
				if(buffers.containsKey(name+GL_ELEMENT_ARRAY_BUFFER)) {
			        gl4.glVertexArrayElementBuffer(vao, getBufferHandle(GL_ELEMENT_ARRAY_BUFFER, name));
				}
				if(buffers.containsKey(name+GL_ARRAY_BUFFER)) {
					gl4.glVertexArrayVertexBuffer(vao, 0, getBufferHandle(GL_ARRAY_BUFFER, name), 0, sizeoftype1+sizeoftype2);
				}
			}
			else if(glver==3){
				gl3.glGenVertexArrays(vhs.length, vhs, 0);
			}else {
				//GL2
			}
			handle=vhs[0];
			vsizes=new int[] {size1,gltype1,size2,gltype2,sizeoftype1,sizeoftype2};
		}
		
		public void dispose() {
			if(glver>2)gl23.glDeleteVertexArrays(1,new int[] {handle},0);
		}
	}
	
	/*
	 * Program Functions
	 */
	
    
    public void newProgram(String name, String root, String vertex, String fragment) {
    	JCProgram oldpro=programs.put(name, new JCProgram(root, vertex, fragment));
    	if(oldpro!=null)oldpro.dispose();
    }
    
    public void addProgram(String name, int program, Hashtable<String, Integer> locs) {
    	programs.put(name, new JCProgram(program, locs));
    }
    
    public void addLocation(String programName, String var) {
    	programs.get(programName).addLocation(var);
    }
    
    public int getLocation(String programName, String var) {
    	return programs.get(programName).locations.get(var);
    }
    
    public int getProgram(String pname) {
    	return programs.get(pname).handle;
    }
    
    public void useProgram(String name) {
    	gl23.glUseProgram(programs.get(name).handle);
    }
    
    public void stopProgram() {
    	gl23.glUseProgram(0);
    }
    
    public void disposePrograms() {
    	for(Enumeration<String> j=programs.keys(); j.hasMoreElements();) {
			String name=j.nextElement();
			programs.get(name).dispose();
			programs.remove(name);
		}
    }
    
    public JCProgram findProgram(int handle) {
    	for(JCProgram program : programs.values()) {
    		if(handle==program.handle)return program;
    	}
    	return null;
    }
	
	class JCProgram{
        	
        	int handle=0;
    		public Hashtable<String, Integer> locations =new Hashtable<String, Integer>();
        	
        	public JCProgram(String root, String vertex, String fragment) {
        		
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
	            	System.out.println("JOGLCanvas Shader: 300es not working, trying 330");
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
	            	System.out.println("JOGLCanvas Shader "+add+" failed");
					try {
						FileReader fr = new FileReader(temp);
						System.err.println("JOGLCanvas Shader 300 es error:");
						int i; 
						while ((i=fr.read()) != -1) 
							System.err.print((char) i);
						fr.close();
						System.err.println("");
					} catch (Exception e) {
						e.printStackTrace();
					}
	            }
	            
	            handle=shaderProgram.program();
	            
	            if(glver>2) {
	        		gl23.glUniformBlockBinding(handle, gl23.glGetUniformBlockIndex(handle, "Transform0"), 1);
	        		gl23.glUniformBlockBinding(handle, gl23.glGetUniformBlockIndex(handle, "Transform1"), 2);
	        		if(fragment.startsWith("texture"))gl23.glUniformBlockBinding(handle, gl23.glGetUniformBlockIndex(handle, "lutblock"), 3);
	            }else{
	            	addLocation("proj");
	            	addLocation("model");
	            	if(fragment.startsWith("texture"))addLocation("luts");
	            }
            
        	}
        	
        	public JCProgram(int pname, Hashtable<String, Integer> locs) {
        		handle=pname;
        		locations=locs;
        	}
        	
        	public void addLocation(String var) {
            	locations.put(var, gl23.glGetUniformLocation(handle, var));
        	}
        	
        	public int getLocation(String var) {
        		return locations.get(var);
        	}
        	
        	public void dispose() {
            	gl23.glDeleteProgram(handle);
        	}
	}
	
	/*
	 * Other utility functions
	 */
	
	/**
	 * Gives the size (in bytes) of the data type
	 * @param gltype
	 * e.g. GL_UNSIGNED_BYTE or GL_FLOAT
	 * @return Size in bytes.  
	 * GL_FLOAT is 4 bytes, for example
	 * 
	 */
	public static int getSizeofType(int gltype) {
		switch(gltype) {
		case GL_UNSIGNED_BYTE : return Buffers.SIZEOF_BYTE;
		case GL_UNSIGNED_SHORT : return Buffers.SIZEOF_SHORT;
		case GL_UNSIGNED_INT : return Buffers.SIZEOF_INT;
		case GL_FLOAT : return Buffers.SIZEOF_FLOAT;
		case GL_DOUBLE : return Buffers.SIZEOF_DOUBLE;
		}
		return 0;
	}
	
	/**
	 * Returns the GL int name of the data type
	 * used in the buffer
	 * @param buffer
	 * Like a ByteBuffer, FloatBuffer
	 * @return e.g. GL_UNSIGNED_BYTE, GL_FLOAT
	 */
	private int getGLType(Buffer buffer) {
		if(buffer instanceof java.nio.ByteBuffer) return GL_UNSIGNED_BYTE;
		if(buffer instanceof java.nio.ShortBuffer) return GL_UNSIGNED_SHORT;
		if(buffer instanceof java.nio.IntBuffer) return GL_UNSIGNED_INT;
		if(buffer instanceof java.nio.FloatBuffer) return GL_FLOAT;
		if(buffer instanceof java.nio.DoubleBuffer) return GL_DOUBLE;
		return 0;
	}
	
	private int getSizeofType(Buffer buffer) {
		return Buffers.sizeOfBufferElem(buffer);
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
