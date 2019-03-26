package ajs.joglcanvas;

import static com.jogamp.opengl.GL.*;
import static com.jogamp.opengl.GL2ES3.GL_UNIFORM_BUFFER;
import static com.jogamp.opengl.GL4.*;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.Enumeration;
import java.util.Hashtable;

import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL4;
import com.jogamp.opengl.math.FloatUtil;
import com.jogamp.opengl.util.GLBuffers;

import ajs.joglcanvas.JOGLImageCanvas.PixelType;
import ij.Prefs;

public class JCGLObjects {
	
	private GL4 gl;
	public JCTextures textures=new JCTextures();
	public JCBuffers buffers=new JCBuffers();
	public JCVaos vaos=new JCVaos();
	
	public JCGLObjects() {}
	public JCGLObjects(GL4 gl) {
		setGL(gl);
	}
	
	public void dispose() {
		textures.dispose();
		buffers.dispose();
		vaos.dispose();
	}
	
	public void setGL(GL4 gl) {
		this.gl=gl;
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
	
	public void drawTexVao(String name, int glElementBufferType, int count) {
		drawTexVao(name, 0, glElementBufferType, count);
	}
	
	public void drawTexVao(String name, int index, Buffer vertexBuffer) {
		vertexBuffer.rewind();
		Buffer eb=getElementBufferFromVBO(vertexBuffer, vaos.vsizes.get(name)/getSizeofType(vertexBuffer));
		eb.rewind();
		drawTexVaoWithEBOVBO(name, index, eb, vertexBuffer);
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
		drawTexVao(name, index, getGLType(elementBuffer), elementBuffer.capacity());
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
	

	public void drawTexVao(String name, int texIndex, int glElementBufferType, int count) {
		gl.glEnable(GL4.GL_TEXTURE_3D);
		gl.glBindTexture(GL4.GL_TEXTURE_3D, textures.get(name, texIndex));
		gl.glBindVertexArray(vaos.get(name));
		
        gl.glDrawElements(GL_TRIANGLES, count, glElementBufferType, 0);
		gl.glBindVertexArray(0);
		gl.glBindTexture(GL4.GL_TEXTURE_3D, 0);
		gl.glDisable(GL4.GL_TEXTURE_3D);
	}
	
	public void drawVao(int glDraw, String name, Buffer vertexBuffer) {
		Buffer elementBuffer=getElementBufferFromVBO(vertexBuffer, vaos.vsizes.get(name)/getSizeofType(vertexBuffer));
		bindEBOVBO(name, elementBuffer, vertexBuffer);
		gl.glBindVertexArray(vaos.get(name));
		gl.glDrawElements(glDraw, elementBuffer.capacity(), getGLType(elementBuffer), 0);
		gl.glBindVertexArray(0);
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

			
			int internalFormat=COMPS==4?GL_RGBA32F:COMPS==3?GL_RGB32F:COMPS==2?GL_RG32F:GL_R32F;
			int pixelType=GL_FLOAT;
			if(buffer instanceof ShortBuffer) {
				internalFormat=COMPS==4?GL_RGBA16:COMPS==3?GL_RGB16:COMPS==2?GL_RG16:GL_R16;
				pixelType=GL_UNSIGNED_SHORT;
			}else if(buffer instanceof ByteBuffer) {
				internalFormat=COMPS==4?GL_RGBA8:COMPS==3?GL_RGB8:COMPS==2?GL_RG8:GL_R8;
				pixelType=GL_UNSIGNED_BYTE;
			}else if(buffer instanceof IntBuffer) {
				//if pixelType=PixelType.RGB10_A2_INT
				internalFormat=GL_RGB10_A2;
				pixelType=GL_UNSIGNED_INT_2_10_10_10_REV;
			}

			gl.glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
			gl.glEnable(GL_TEXTURE_3D);
			gl.glBindTexture(GL_TEXTURE_3D, glTextureHandle); 
			//gl.glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
			gl.glTexImage3D(GL_TEXTURE_3D, 0, internalFormat, width, height, depth, 0, (COMPS==4||pixelType==GL_UNSIGNED_INT_2_10_10_10_REV)?GL_RGBA:COMPS==3?GL_RGB:COMPS==2?GL_RG:GL_LUMINANCE, pixelType, buffer); 
			//gl.glTexImage3D(GL_TEXTURE_2D, mipmapLevel, internalFormat, width, height, depth, numBorderPixels, pixelFormat, pixelType, buffer); 
			
			int magtype=GL_LINEAR;
			if(!Prefs.interpolateScaledImages)magtype=GL_NEAREST;
			
			gl.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MAG_FILTER, magtype);
			gl.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MIN_FILTER, magtype);//GL_NEAREST_MIPMAP_LINEAR
			gl.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_BORDER);
			gl.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_BORDER);
			gl.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_BORDER);
			gl.glTexParameterfv(GL_TEXTURE_3D, GL_TEXTURE_BORDER_COLOR, new float[] {0f,0f,0f,0f},0);
			gl.glGenerateMipmap(GL_TEXTURE_3D);
			gl.glBindTexture(GL_TEXTURE_3D, 0); 
			gl.glDisable(GL_TEXTURE_3D);
		} 
		
		public void loadTexFromPBO(String sameName, int pn, int width, int height, int depth, int offsetSlice, PixelType type, int COMPS) {
			loadTexFromPBO(sameName, pn, sameName, 0, width, height, depth, offsetSlice, type, COMPS);
		}
		
		public void loadTexFromPBO(String pboName, int pn, String texName, int tn, int width, int height, int depth, int offsetSlice, PixelType type, int COMPS) {

			int[] phs=pbos.get(pboName);
			int[] ths=handles.get(texName);

			int internalFormat=COMPS==4?GL_RGBA32F:COMPS==3?GL_RGB32F:COMPS==2?GL_RG32F:GL_R32F;
			int pixelType=GL_FLOAT;
			int size=Buffers.SIZEOF_FLOAT;
			int components=COMPS;
			
			if(type==PixelType.SHORT) {
				internalFormat=COMPS==4?GL_RGBA16:COMPS==3?GL_RGB16:COMPS==2?GL_RG16:GL_R16;
				pixelType=GL_UNSIGNED_SHORT;
				size=Buffers.SIZEOF_SHORT;
			}else if(type==PixelType.BYTE) {
				internalFormat=COMPS==4?GL_RGBA8:COMPS==3?GL_RGB8:COMPS==2?GL_RG8:GL_R8;
				pixelType=GL_UNSIGNED_BYTE;
				size=Buffers.SIZEOF_BYTE;
			}else if(type==PixelType.INT_RGB10A2) {
				internalFormat=GL_RGB10_A2;
				pixelType=GL_UNSIGNED_INT_2_10_10_10_REV;
				size=Buffers.SIZEOF_INT;
				components=1;
			}
			
			gl.glEnable(GL_TEXTURE_3D);
			gl.glActiveTexture(GL_TEXTURE0);
			gl.glBindBuffer(GL_PIXEL_UNPACK_BUFFER, phs[pn]);
			gl.glBindTexture(GL_TEXTURE_3D, ths[tn]); 
			gl.glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
			gl.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_BASE_LEVEL, 0);
			gl.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MAX_LEVEL, 0);
			gl.glTexImage3D(GL_TEXTURE_3D, 0, internalFormat, width, height, depth, 0, (COMPS==4||components==1)?GL_RGBA:COMPS==3?GL_RGB:COMPS==2?GL_RG:GL_LUMINANCE, pixelType, offsetSlice*components*width*height*size);
			int magtype=GL_LINEAR;
			if(!Prefs.interpolateScaledImages)magtype=GL_NEAREST;
			gl.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MAG_FILTER, magtype); 
			gl.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MIN_FILTER, magtype);//GL_NEAREST_MIPMAP_LINEAR 
			gl.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_BORDER);
			gl.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_BORDER);
			gl.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_BORDER);
			gl.glTexParameterfv(GL_TEXTURE_3D, GL_TEXTURE_BORDER_COLOR, new float[] {0f,0f,0f,0f},0);
			//gl.glGenerateMipmap(GL_TEXTURE_3D);
			gl.glDisable(GL_TEXTURE_3D);
			gl.glBindTexture(GL_TEXTURE_3D, 0); 
			gl.glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
			//System.out.println("LPBO 3");
		}
		
		public void updateRgbaPBO(String name, int index, Buffer buffer) {
			updateSubRgbaPBO(name, index, buffer, 0, buffer.limit(), buffer.limit());
		}
		
		public void updateSubRgbaPBO(String name, int index, Buffer buffer, int offset, int length, int bsize) {
			int[] phs=pbos.get(name);
			int size=Buffers.SIZEOF_FLOAT;
			if(buffer instanceof ShortBuffer) {
				size=Buffers.SIZEOF_SHORT;
			}else if(buffer instanceof ByteBuffer) {
				size=Buffers.SIZEOF_BYTE;
			}else if(buffer instanceof IntBuffer) {
				size=Buffers.SIZEOF_INT;
			}
			
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
			buffer.position(offset);
			gl.glBufferSubData(GL_PIXEL_UNPACK_BUFFER, (long)offset*size, (long)length*size, buffer);
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
			Hashtable<String,int[]> dict=array;
			Hashtable<String,Buffer> bdict=abuffers;
			if(gltype==GL_UNIFORM_BUFFER) {dict=uniform; bdict=ubuffers;}
			else if(gltype==GL_ELEMENT_ARRAY_BUFFER) {dict=element;bdict=ebuffers;}
			int[] bn=new int[1];
			gl.glCreateBuffers(1, bn, 0);
			dict.put(name, bn);
			if(define) {
				boolean write=(buffer==null);
				gl.glBindBuffer(gltype, bn[0]);
				gl.glBufferStorage(gltype, size, buffer,  (buffer==null)?(GL_MAP_WRITE_BIT | GL_MAP_PERSISTENT_BIT | GL_MAP_COHERENT_BIT):0);
				gl.glBindBuffer(gltype,  0);
				
				if(!write)return null;
				ByteBuffer outbuffer= gl.glMapNamedBufferRange(
						bn[0],
		                0,
		                size,
		                GL4.GL_MAP_WRITE_BIT | GL4.GL_MAP_PERSISTENT_BIT | GL4.GL_MAP_COHERENT_BIT | GL_MAP_INVALIDATE_RANGE_BIT | GL4.GL_MAP_INVALIDATE_BUFFER_BIT); // flags
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
			
			if(gltype==GL_UNIFORM_BUFFER) {
				gl.glBindBufferBase(gltype, binding, uniform.get(name)[0]);
				return;
			}

			Hashtable<String, int[]> dict=array;
			if(gltype==GL_ELEMENT_ARRAY_BUFFER)dict=element;
			gl.glBindBuffer(gltype, dict.get(name)[0]);
		}
		
		public void unBindBuffer(int gltype, int binding) {
			if(gltype==GL_UNIFORM_BUFFER) {
				gl.glBindBufferBase(gltype, binding, 0);
				return;
			}
			gl.glBindBuffer(gltype, 0);
		}
		
		public void dispose() {
			for(int i=0;i<3;i++) {
				Hashtable<String, int[]> dict=array;
				Hashtable<String, Buffer> bdict=abuffers;
				if(i==1) {dict=uniform; bdict=ubuffers;}
				else if(i==2) {dict=element; bdict=ebuffers;}
				for(Enumeration<String> j=dict.keys(); j.hasMoreElements();) {
					String name=j.nextElement();
					int[] phs=dict.get(name);
					if(bdict.get(name)!=null)gl.glUnmapNamedBuffer(phs[0]);
					gl.glDeleteBuffers(phs.length,phs,0);
				}
			}
		}
	}

	class JCVaos{
		
		public Hashtable<String, int[]> handles =new Hashtable<String, int[]>();
		public Hashtable<String, Integer> vsizes =new Hashtable<String, Integer>();
		
		public JCVaos() {}
		
		public void newVao(String name, int size1, int gltype1, int size2, int gltype2) {
		
			int[] vhs=new int[1];
			gl.glCreateVertexArrays(vhs.length, vhs, 0);
			handles.put(name, vhs);
			int vao=vhs[0];
			gl.glVertexArrayAttribBinding(vao, 0, 0);//modelcoords
			gl.glVertexArrayAttribBinding(vao, 1, 0);//texcoords
			int sizeoftype1=getSizeofType(gltype1)*size1;
			int sizeoftype2=getSizeofType(gltype2)*size2;
			gl.glVertexArrayAttribFormat(vao, 0, size1, gltype1, false, 0);//modelcoords
	        gl.glVertexArrayAttribFormat(vao, 1, size2, gltype2, false, sizeoftype1);//texcoords
	        gl.glEnableVertexArrayAttrib(vao, 0);
	        gl.glEnableVertexArrayAttrib(vao, 1);
			if(buffers.element.get(name)!=null) {
		        gl.glVertexArrayElementBuffer(vao, buffers.element.get(name)[0]);
			}
			if(buffers.array.get(name)!=null) {
				gl.glVertexArrayVertexBuffer(vao, 0, buffers.array.get(name)[0], 0, sizeoftype1+sizeoftype2);
			}
			vsizes.put(name, sizeoftype1+sizeoftype2);
		}
		
		public int get(String name) {
			int[] hs=handles.get(name);
			if(hs==null)return 0;
			return hs[0];
		}
		
		public void dispose() {
			for(Enumeration<int[]> j=handles.elements(); j.hasMoreElements();) {
				int[] vhs=j.nextElement();
				gl.glDeleteVertexArrays(vhs.length,vhs,0);
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
