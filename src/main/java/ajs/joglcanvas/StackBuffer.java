package ajs.joglcanvas;

import ij.ImagePlus;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

public class StackBuffer {
	
	private ImagePlus imp;
	private boolean[] updatedSlices;
	
	public StackBuffer(ImagePlus imp) {
		this.imp=imp;
		resetSlices();
	}
	
	public void resetSlices() {
		updatedSlices=new boolean[imp.getNFrames()*imp.getNSlices()];
	}
	
	public boolean isSliceUpdated(int sl, int fr) {
		return updatedSlices[fr*imp.getNSlices()+sl];
	}
	
	public void updateSlice(int sl, int fr) {
		updatedSlices[fr*imp.getNSlices()+sl]=true;
	}
	
	/**
	 * channel slice and frame are 1-indexed
	 * 0 for channel means make a multichannel banded array
	 * @return
	 */
	public Buffer getSliceBuffer(int channel, int slice, int frame) {
		Object outPixels=imp.getStack().getProcessor(imp.getStackIndex(channel, slice, frame)).getPixels();
		return wrapPixels(outPixels);
	}
	
	private Buffer wrapPixels(Object outPixels) {
		int bits=(outPixels instanceof byte[])?8:(outPixels instanceof short[])?16:(outPixels instanceof float[])?32:24;
		if(bits==8) return ByteBuffer.wrap((byte[])outPixels);
		if(bits==16) return ShortBuffer.wrap((short[])outPixels);
		if(bits==24) return IntBuffer.wrap((int[])outPixels);
		//else bits==32
		return FloatBuffer.wrap((float[])outPixels);
	}
	
	public Buffer convertForUndersample(Object pixels, int width, int height, int undersample){
		if(undersample==1)return wrapPixels(pixels);
		Object tpixels;
		boolean dobyte=pixels instanceof byte[];
		boolean doshort=pixels instanceof short[];
		boolean doint=pixels instanceof int[];
		int uwidth=width/undersample, uheight=height/undersample;
		if(dobyte)tpixels=new byte[uwidth*uheight];
		else if(doshort)tpixels= new short[uwidth*uheight];
		else if(doint)tpixels= new int[uwidth*uheight];
		else tpixels=new float[uwidth*uheight];
		for(int y=0;y<uheight;y++) {
			for(int x=0;x<uwidth;x++) {
				if(dobyte)((byte[])tpixels)[y*uwidth+x]=((byte[])pixels)[y*undersample*width+x*undersample];
				else if(doshort)((short[])tpixels)[y*uwidth+x]=((short[])pixels)[y*undersample*width+x*undersample];
				else if(doint)((int[])tpixels)[y*uwidth+x]=((int[])pixels)[y*undersample*width+x*undersample];
				else ((float[])tpixels)[y*uwidth+x]=((float[])pixels)[y*undersample*width+x*undersample];
			}
		}
		return wrapPixels(tpixels);
		
	}

}
