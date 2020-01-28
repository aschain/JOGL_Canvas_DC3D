package ajs.joglcanvas;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;

import java.nio.Buffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

import com.jogamp.opengl.util.GLBuffers;

import ajs.joglcanvas.JOGLImageCanvas.PixelType;

import java.nio.ByteBuffer;

public class StackBuffer {
	
	private ImagePlus imp;
	private boolean[] updatedSlices;
	private PixelType pixelType=PixelType.BYTE;
	private int undersample=1;
	public boolean isFrameStack=false, okDirect=false;
	public int sliceSize,bufferSize,bufferWidth,bufferHeight;
	
	public StackBuffer(ImagePlus imp) {
		this.imp=imp;
		isFrameStack=imp.getNFrames()>1&&imp.getNSlices()==1;
		if(imp.getNFrames()>1 && imp.getNSlices()==1)isFrameStack=true;
	}
	
	private boolean updateSizes() {
		int frms=imp.getNFrames(), sls=imp.getNSlices();
		int oldbsize=bufferSize;
		isFrameStack=frms>1&&sls==1;
		if(isFrameStack) {sls=frms;frms=1;}
		bufferWidth=tex4div(imp.getWidth()/undersample);
		bufferHeight=tex4div(imp.getHeight()/undersample);
		okDirect=imp.getWidth()==bufferWidth;
		sliceSize=bufferWidth*bufferHeight;
		bufferSize=sliceSize*sls;
		if(bufferSize!=oldbsize) {resetSlices(); return true;}
		return false;
	}
	
	public void setPixelType(PixelType pt, int us) {
		this.pixelType=pt;
		this.undersample=us;
		updateSizes();
	}
	
	public void resetSlices() {
		updatedSlices=new boolean[imp.getNFrames()*imp.getNSlices()];
	}
	
	private int tex4div(int wh) {
		return wh+((wh%4)>0?(4-wh%4):0);
	}
	
	public boolean isSliceUpdated(int sl, int fr) {
		if(isFrameStack)return updatedSlices[fr];
		else return updatedSlices[fr*imp.getNSlices()+sl];
	}
	
	public void updateSlice(int sl, int fr) {
		if(isFrameStack)updatedSlices[fr]=true;
		else updatedSlices[fr*imp.getNSlices()+sl]=true;
	}
		
	/**
	 * 
	 * @param stsl
	 * @param endsl
	 * @param stfr
	 * @param endfr
	 * @param update
	 * @return
	 */
	private Object getImageArray(int stch, int endch, int stsl, int endsl, int stfr, int endfr, boolean update) {
		if(isFrameStack) {stsl=stfr; endsl=endfr; stsl=0; endsl=1;}
		int sls=imp.getNSlices(), chs=endch-stch;
		int size=bufferWidth*bufferHeight*(endsl-stsl)*(endfr-stfr);
		Object outPixels=null;
		if((imp.getWidth()/undersample)==bufferWidth && (imp.getHeight()/undersample)==bufferHeight && ((endsl-stsl)==1 && (endfr-stfr)==1 && chs==1)) {
			return convertForUndersample(imp.getStack().getProcessor(imp.getStackIndex(endch, endsl, endfr)).getPixels(),undersample);
		}
		
		int bits=imp.getBitDepth();
		if(bits==8)outPixels=new byte[size];
		else if(bits==16)outPixels=new short[size];
		else if(bits==24) {outPixels=new int[size];}
		else outPixels=new float[size];
		ImageStack imst=imp.getStack();
		for(int fr=stfr;fr<endfr; fr++) {
			for(int csl=stsl;csl<endsl;csl++) {
				int offset=((csl-stsl))*bufferWidth*bufferHeight*chs+(fr-stfr)*(endsl-stsl)*bufferWidth*bufferHeight*chs;
				for(int i=stch;i<endch;i++) {
					ImageProcessor ip=imst.getProcessor(imp.getStackIndex(i+1, csl+1, fr+1));
					Object pixels=convertForUndersample(ip.getPixels(), undersample);
					addPixels(outPixels, bufferWidth, pixels, imp.getWidth()/undersample, imp.getHeight()/undersample, offset, i-stch, chs);
				}
				if(update)updatedSlices[fr*sls+csl]=true;
			}
		}
		return outPixels;
	}
	
	/**
	 * channel slice and frame are 1-indexed
	 * 0 for channel means make a multichannel banded array
	 * @return
	 */
	public Buffer getSliceBuffer(int channel, int slice, int frame) {
		Object outPixels=null;
		if(okDirect) outPixels=imp.getStack().getProcessor(imp.getStackIndex(channel, slice, frame)).getPixels();
		else outPixels=getImageArray(channel-1, channel, slice-1, slice, frame-1, frame, false);
		return convertPixels(outPixels, channel);
	}
	
	private Buffer convertPixels(Object outPixels, int channel) {
		Buffer buffer=null;
		int bits=(outPixels instanceof byte[])?8:(outPixels instanceof short[])?16:(outPixels instanceof float[])?32:24;
		int size=((bits==8)?((byte[])outPixels).length:(bits==16)?((short[])outPixels).length:(bits==32)?((float[])outPixels).length:((int[])outPixels).length);
		if(pixelType==PixelType.BYTE) {
			if(bits==8) {
				if(okDirect&&JCP.wrappedBuffers)return ByteBuffer.wrap((byte[])outPixels);
				else return GLBuffers.newDirectByteBuffer(size).put((byte[])outPixels);
			}
			else {
				if(bits==16 || bits==32) {
					buffer=GLBuffers.newDirectByteBuffer(size);
					for(int i=0;i<size;i++) {
						if(bits==16)((ByteBuffer)buffer).put((byte)(((int)((((short[])outPixels)[i]&0xffff)/256.0))));
						else /*if(bits==32)*/((ByteBuffer)buffer).put((byte)(((int)(((float[])outPixels)[i]/* *255f*/))));
						//else {
							//IJ.error("Don't convert RGB to bytes");
							//int rgb=((int[])outPixels)[i];
							//requires new imageFBs length to be frms*3
							//((ByteBuffer)buffer[fr*chs+0]).put((byte)((rgb&0xff0000)>>16));
							//((ByteBuffer)buffer[fr*chs+1]).put((byte)((rgb&0xff00)>>8));
							//((ByteBuffer)buffer[fr*chs+2]).put((byte)(rgb&0xff));
						//}
					}
				}else IJ.error("Don't convert RGB to bytes");
				//if(bits==24) {for(int i=0;i<3;i++)imageFBs[fr*chs+i].position(sl*sliceSize);}
			}
		}else if(pixelType==PixelType.INT_RGBA8){
			if(bits==24) {
				if(okDirect&&JCP.wrappedBuffers)return IntBuffer.wrap((int[])outPixels);
				else return GLBuffers.newDirectIntBuffer(size).put((int[])outPixels);
			}
			else {
				IJ.error("INT_RGBA8 only for 24bit images");
			}
		}else if(pixelType==PixelType.SHORT) {
			if(bits==16) {
				if(okDirect&&JCP.wrappedBuffers)return ShortBuffer.wrap((short[])outPixels);
				else return GLBuffers.newDirectShortBuffer(size).put((short[])outPixels);
			}
			else {
				if(bits==8 || bits==24) {
					IJ.error("Don't use short pixel type with 8 bit image");
					return null;
				}
				buffer=GLBuffers.newDirectShortBuffer(size);
				if(bits==32) {
					//LUT[] luts=imp.getLuts();
					for(int i=0;i<size;i++) {
						//double px=(double)(((float[])outPixels)[i]);
						//((ShortBuffer)buffer).put((short)((px-luts[channel-1].min)/(luts[channel-1].max-luts[channel-1].min)*65535.0));
						((ShortBuffer)buffer).put((short)(((float[])outPixels)[i]));
					}
				}
			}
		}else if(pixelType==PixelType.INT_RGB10A2) {
			/*buffer=GLBuffers.newDirectIntBuffer(size);
			int COMPS=1;
			if(bits==32) {
				float[] floatPixels=((float[])outPixels);
				for(int i=0;i<size;i+=COMPS) {
					int red=(((int)(floatPixels[i]*1023f))&0x3ff);
					int green=(COMPS<2)?0:(((int)(floatPixels[i+1]*1023f))&0x3ff);
					int blue=(COMPS<3)?0:(((int)(floatPixels[i+2]*1023f))&0x3ff);
					int alpha=1;
					//if(COMPS==4) alpha=(((int)(floatPixels[i+3]*0x3))&0x3);
					((IntBuffer)buffer).put(alpha<<30 | blue <<20 | green<<10 | red);
				}
			}else if(bits==16) {
				short[] shortPixels=((short[])outPixels);
				for(int i=0;i<size;i+=COMPS) {
					int red=(((int)(shortPixels[i]/64f))&0x3ff);
					int green=(COMPS<2)?0:(((int)(shortPixels[i+1]/64f))&0x3ff);
					int blue=(COMPS<3)?0:(((int)(shortPixels[i+2]/64f))&0x3ff);
					int alpha=1;
					//if(COMPS==4) alpha=(((int)((shortPixels[i+3]&0xffff)/16384f))&0x3);
					((IntBuffer)buffer).put(alpha<<30 | blue <<20 | green<<10 | red);
				}
			}else IJ.error("Don't use 10bit INT for 8 bit images");*/
			IJ.error("Not using RGB10A2 anymore");
		}else if(pixelType==PixelType.FLOAT) {
			if(bits==32) {
				if(okDirect&&JCP.wrappedBuffers)return FloatBuffer.wrap((float[])outPixels);
				else return GLBuffers.newDirectFloatBuffer(size).put((float[])outPixels);
			} else IJ.error("Don't use less than 32 bit image with 32 bit pixels");
			//buffer=GLBuffers.newDirectFloatBuffer(size);
		}
		return buffer;
	}

	protected Object convertForUndersample(Object pixels, int undersample) {
		if(undersample==1) return pixels;
		int imageWidth=imp.getWidth(), imageHeight=imp.getHeight();
		int uwidth=imageWidth/undersample,uheight=imageHeight/undersample;
		Object tpixels;
		boolean dobyte=pixels instanceof byte[];
		boolean doshort=pixels instanceof short[];
		boolean doint=pixels instanceof int[];
		if(dobyte)tpixels=new byte[uwidth*uheight];
		else if(doshort)tpixels= new short[uwidth*uheight];
		else if(doint)tpixels= new int[uwidth*uheight];
		else tpixels=new float[uwidth*uheight];
		for(int y=0;y<uheight;y++) {
			for(int x=0;x<uwidth;x++) {
				if(dobyte)((byte[])tpixels)[y*uwidth+x]=((byte[])pixels)[y*undersample*imageWidth+x*undersample];
				else if(doshort)((short[])tpixels)[y*uwidth+x]=((short[])pixels)[y*undersample*imageWidth+x*undersample];
				else if(doint)((int[])tpixels)[y*uwidth+x]=((int[])pixels)[y*undersample*imageWidth+x*undersample];
				else ((float[])tpixels)[y*uwidth+x]=((float[])pixels)[y*undersample*imageWidth+x*undersample];
			}
		}
		return tpixels;
	}
	
	protected void addPixels(Object pixels, int width, Object newpixels, int nwidth, int nheight, int offset, int c, int bands) {
		boolean dobyte=(pixels instanceof byte[]);
		boolean doshort=(pixels instanceof short[]);
		boolean doint=(pixels instanceof int[]);
		for(int y=0;y<nheight;y++){
			for(int x=0;x<nwidth;x++){
				int pi=y*width*bands+x*bands+offset+c;
				int i=y*nwidth+x;
				if(dobyte)((byte[])pixels)[pi]=((byte[])newpixels)[i];
				else if(doshort)((short[])pixels)[pi]=((short[])newpixels)[i];
				else if(doint)((int[])pixels)[pi]=((int[])newpixels)[i];
				else ((float[])pixels)[pi]=((float[])newpixels)[i];
			}
		}
	}
	/*
	static public MinMax[] getMinMaxArray(LUT[] luts) {
		return getMinMaxArray(luts.length,luts);
	}
	
	static public MinMax[] getMinMaxArray(int size, LUT[] luts) {
		MinMax[] mm=new MinMax[size];
		double min=0,max=0;
		for(int i=0;i<size;i++) {
			if(luts!=null && i<luts.length) {min=luts[i].min;max=luts[i].max;}
			mm[i]=new MinMax(min,max);
		}
		return mm;
	}
	
	public static class MinMax{
		public double min=0,max=0;
		public MinMax(double min, double max) {this.min=min;this.max=max;}
	}
	*/
	
}
