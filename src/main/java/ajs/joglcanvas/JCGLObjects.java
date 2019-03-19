package ajs.joglcanvas;

import static com.jogamp.opengl.GL.*;
import static com.jogamp.opengl.GL4.*;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.Enumeration;
import java.util.Hashtable;

import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL4;

import ajs.joglcanvas.JOGLImageCanvas.PixelType;
import ij.Prefs;

public class JCGLObjects {
	
	private GL4 gl;
	public JCTextures textures=new JCTextures();
	public JCBuffers buffers=new JCBuffers();
	
	
	public JCGLObjects(GL4 gl) {
		setGL(gl);
	}
	
	public void dispose() {
		textures.dispose();
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
	
	
	
	
	
	
	class JCTextures{
		
		public Hashtable<String,int[]> textures=new Hashtable<String, int[]>();
		public Hashtable<String,int[]> pbos=new Hashtable<String, int[]>();
		
		public JCTextures() {}
		
		public void newTexture(String name, int size) {
			if(textures.containsKey(name)) {
				dispose(name);
			}
			int[] textureHandles = new int[size]; 
			gl.glGenTextures(size, textureHandles, 0);
			textures.put(name, textureHandles);
		}
		
		public void newPbo(String name, int size) {
			if(pbos.containsKey(name)) {
				disposePbo(name);
			}
			pbos.put(name, new int[size]);
		}
		
		public void dispose() {
			int size=textures.size();
			if(size!=0) {
				for(Enumeration<int[]> i=textures.elements(); i.hasMoreElements();) {
					int[] ths=i.nextElement();
					gl.glDeleteTextures(ths.length,ths,0);
				}
			}
			size=pbos.size();
			if(size!=0) {
				for(Enumeration<int[]> i=pbos.elements(); i.hasMoreElements();) {
					int[] phs=i.nextElement();
					gl.glDeleteBuffers(phs.length,phs,0);
				}
			}
		}
		
		public void dispose(String name) {
			int[] ths=textures.get(name);
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
			return textures.get(name)[index];
		}

		public int getLength(String name) {
			if(textures.get(name)==null)return 0;
			return textures.get(name).length;
		}
		public int getPboLength(String name) {
			if(pbos.get(name)==null)return 0;
			return pbos.get(name).length;
		}
		
		public boolean containsKey(String name) {
			return textures.containsKey(name);
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
			int[] ths=textures.get(texName);

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
		
		
		
		public JCBuffers() {}

		public ByteBuffer newBuffer(int gltype, String name, long size, Buffer buffer) {
			Hashtable<String,int[]> dict=array;
			if(gltype==GL_UNIFORM_BUFFER)dict=uniform;
			else if(gltype==GL_ELEMENT_ARRAY_BUFFER)dict=element;
			int[] bn=new int[1];
			gl.glCreateBuffers(1, bn, 0);
			dict.put(name, bn);
			boolean write=(buffer==null);
			gl.glBindBuffer(gltype, bn[0]);
			gl.glBufferStorage(gltype, size, buffer,  (buffer==null)?(GL_MAP_WRITE_BIT | GL_MAP_PERSISTENT_BIT | GL_MAP_COHERENT_BIT):0);
			gl.glBindBuffer(gltype,  0);
			
			if(!write)return null;
			return gl.glMapNamedBufferRange(
					bn[0],
	                0,
	                size,
	                GL4.GL_MAP_WRITE_BIT | GL4.GL_MAP_PERSISTENT_BIT | GL4.GL_MAP_COHERENT_BIT | GL_MAP_INVALIDATE_RANGE_BIT | GL4.GL_MAP_INVALIDATE_BUFFER_BIT); // flags
			
		}
		
		public ByteBuffer newArrayBuffer(String name, long size, Buffer buffer) {
			return newBuffer(GL_ARRAY_BUFFER, name, size, buffer);
			
		}
		public void newUniformBuffer(String name, boolean isWritable) {
			
		}
		public void newElementBuffer(String name, boolean isWritable) {
			
		}
		
		
	}

	class JCVao{
		
		public JCVao() {
			
		}
	}
	
}
