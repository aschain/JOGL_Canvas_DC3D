package ajs.joglcanvas;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;

import java.awt.Frame;
import java.nio.Buffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

import com.jogamp.opengl.util.GLBuffers;

import ajs.joglcanvas.JOGLImageCanvas.PixelType;

import java.nio.ByteBuffer;

public class StackBuffer {
	
	private ImagePlus imp;
	private boolean isFrameStack=false;
	private JOGLImageCanvas dcic;
	public Buffer[] imageFBs;
	public boolean[] updatedFrames;
	public boolean[] updatedSlices;
	protected int updatingBuffers=0;
	private boolean stopupdate=false;
	private PixelType pixelType=PixelType.BYTE;
	private int undersample=1;
	public int sliceSize=0,bufferSize=0;
	
	public StackBuffer(ImagePlus imp, JOGLImageCanvas dcic) {
		this.imp=imp;
		isFrameStack=imp.getNFrames()>1&&imp.getNSlices()==1;
		this.dcic=dcic;
		if(imp.getNFrames()>1 && imp.getNSlices()==1)isFrameStack=true;
		initBuffers();
		if(JCP.backgroundLoadBuffers) {
			updateBuffersBackground(null);
		}
	}
	
	public void setPixelType(PixelType pt, int us) {
		this.pixelType=pt;
		this.undersample=us;
		isFrameStack=imp.getNFrames()>1&&imp.getNSlices()==1;
	}
		
	public boolean initBuffersIfNeeded() {
		boolean result=false;
		int frms=imp.getNFrames(), sls=imp.getNSlices();
		isFrameStack=(frms>1 && sls==1);
		if(isFrameStack) {sls=frms;frms=1;}
		if(imageFBs==null || imageFBs.length!=frms || 
				updatedFrames==null || updatedFrames.length!=frms || 
						updatedSlices==null || updatedSlices.length!=frms*sls)
		{
			initBuffers(); result=true;
		}
		return result;
	}
	
	public void initBuffers() {
		int frms=imp.getNFrames(), sls=imp.getNSlices();
		isFrameStack=(frms>1 && sls==1);
		if(isFrameStack) {sls=frms;frms=1;}
		imageFBs=new Buffer[frms];
		updatedFrames=new boolean[frms];
		updatedSlices=new boolean[frms*sls];
	}
	
	public void resetSlices() {
		updatedSlices=new boolean[imp.getNFrames()*imp.getNSlices()];
	}
	
	private int tex4div(int wh) {
		return wh+((wh%4)>0?(4-wh%4):0);
	}
	
	public void stopUpdate() {
		if(updatingBuffers>0)stopupdate=true;
	}
	
	public void resetBuffers() {
		while(updatingBuffers>0)IJ.wait(50);
		isFrameStack=imp.getNFrames()>1 && imp.getNSlices()==1;
		imageFBs=new Buffer[isFrameStack?1:imp.getNFrames()];
	}
	
	public void update(int fr, int sl) {
		checkBuffers();
		updateImageBufferSlice(fr+1, sl+1);
	}
	
	private void checkBuffers() {
		isFrameStack=imp.getNFrames()>1&&imp.getNSlices()==1;
		int psize=tex4div(imp.getWidth())*tex4div(imp.getHeight())*imp.getNChannels();
		int bsize=imp.getNSlices()*psize;
		for(int i=0;i<imageFBs.length;i++) {
			if(imageFBs[i] instanceof FloatBuffer && pixelType!=PixelType.FLOAT)imageFBs[i]=null;
			if(imageFBs[i] instanceof ShortBuffer && pixelType!=PixelType.SHORT)imageFBs[i]=null;
			if(imageFBs[i] instanceof ByteBuffer && pixelType!=PixelType.BYTE)imageFBs[i]=null;
			if(imageFBs[i] instanceof IntBuffer && !(pixelType==PixelType.INT_RGB10A2 || pixelType==PixelType.INT_RGBA8))imageFBs[i]=null;
			if(imageFBs[i]==null) {
				if(pixelType==PixelType.FLOAT)imageFBs[i]=GLBuffers.newDirectFloatBuffer(bsize);
				else if(pixelType==PixelType.SHORT)imageFBs[i]=GLBuffers.newDirectShortBuffer(bsize);
				else if(pixelType==PixelType.BYTE)imageFBs[i]=GLBuffers.newDirectByteBuffer(bsize);
				else if(pixelType==PixelType.INT_RGB10A2)imageFBs[i]=GLBuffers.newDirectIntBuffer(bsize);
				else if(pixelType==PixelType.INT_RGBA8)imageFBs[i]=GLBuffers.newDirectIntBuffer(bsize);
			}
		}
	}
	
	public boolean updateBuffers(int frame, boolean bgload) {
		checkBuffers();
		//if(imp.getNSlices()==1)return false; //delete if you implement a buffer for a framestack
		if(bgload && updatingBuffers>0)return true;
		if(!bgload && updatedFrames[frame-1])return true;
		int[] skipframe=new int[0];
		if(frame>0) {
			int fr=frame-1;
			if(isFrameStack) {
				fr=0;
				frame=0;
			}
			updatedFrames[fr]=true;
			updateImageBufferStack(frame);
			int sls=imp.getNSlices();
			for(int sl=0;sl<sls;sl++)updatedSlices[fr*sls+sl]=true;
			if(isFrameStack) for(int i=0;i<imp.getNFrames();i++)updatedSlices[i]=true;
			skipframe=new int[] {frame};
		}
		if(bgload && !isFrameStack)return updateBuffersBackground(skipframe);
		else return false;
	}
	
	public boolean updateBuffersBackground(int[] skipframe) {
		checkBuffers();
		if(imp.getNSlices()==1)return false;
		if(updatingBuffers>0)return true;
		for(int i=0;i<updatedFrames.length;i++)updatedFrames[i]=false;
		if(skipframe==null)skipframe=new int[0];
		for(int i=0;i<skipframe.length;i++) {
			if(skipframe[i]>0)updatedFrames[skipframe[i]-1]=true;
		}
		int cores=Math.max(1,(Runtime.getRuntime().availableProcessors()-2));
		//maybe it's not faster because they are all reading from the same memory location?
		cores=1; //no parallel processing
		int frms=imp.getNFrames();
		int cend=Math.min(frms,cores);
		updatingBuffers=cend;
		for(int i=0;i<cend;i++) {
			int jump=Math.max(frms/cores,1);
			final int p=i;
			(new Thread() {
				public void run() {
					if(isFrameStack) {updateBuffers(0,false); updatingBuffers=0; return;}
					int end=(p+1)*jump;
					if(p==(cend-1))end=frms;
					for(int fr=(p*jump);fr<end;fr++) {
						if(!updatedFrames[fr]) {
							updateImageBufferStack(fr+1);
							updatedFrames[fr]=true;
							int sls=imp.getNSlices();
							for(int sl=0;sl<sls;sl++)updatedSlices[fr*sls+sl]=true;
						}
					}
					updatingBuffers--;
					//if(updatingBuffers==0)
						//IJ.showStatus("3d Ready");
				}
			}).start();
		}
		(new Thread() {
			public void run() {
				int timeout=0;
				final int frms=imp.getNFrames();
				Frame win=(Frame)dcic.icc.getParent();
				String title=win.getTitle();
				while(updatingBuffers>0) {
					if(imp==null)break;
					int ubn=0;
					for(boolean a : updatedFrames) if(a)ubn++;
					win.setTitle(title+" (Updating for 3d: "+(int)((double)ubn/frms*100)+"%...)");
					IJ.wait(50);
					timeout++;
					if(timeout>(20*60*5)) {
						IJ.log("3d updating buffers (at "+(int)((double)ubn/frms*100)+"%) timed out for "+imp.getTitle());
						break;
					}
					win.repaint();
				}
				if(win!=null)win.setTitle(title);
				win.repaint();
			}
		}).start();
		return false;
	}
	
	//create a buffer for one frame but whole NSlices, or modify one slice within the stack buffer
	protected void updateImageBufferStack(int frame) {
		int stsl=0,endsl=imp.getNSlices(),stfr=frame-1;
		if(frame==0) {stsl=0; endsl=1;stfr=0;frame=imp.getNFrames();}
		updateImageBuffer(stsl, endsl, stfr, frame);
	}

	
	//update the buffer for the one slice
	protected void updateImageBufferSlice(int slice, int frame) {
		updateImageBuffer(slice-1, slice, frame-1, frame);
	}



	public void updateImageBuffer(int stsl, int endsl, int stfr, int endfr) {
		getImageBuffer(stsl, endsl, stfr, endfr, imageFBs[stfr]);
	}
	
	private Object getImageArray(int stsl, int endsl, int stfr, int endfr) {
		int bits=imp.getBitDepth();
		int iwidth=imp.getWidth(), iheight=imp.getHeight(), sls=imp.getNSlices(), frms=imp.getNFrames();
		iwidth/=undersample; iheight/=undersample;
		int width=tex4div(iwidth), height=tex4div(iheight);
		int chs=imp.getNChannels();
		int COMPS=bits==24?3:chs;
		if(pixelType==PixelType.INT_RGB10A2 || pixelType==PixelType.INT_RGBA8)COMPS=1;
		int size=width*height*COMPS*(endsl-stsl)*(endfr-stfr);
		Object outPixels;
		if(bits==8)outPixels=new byte[size];
		else if(bits==16)outPixels=new short[size];
		else if(bits==24) {outPixels=new int[size/COMPS];}
		else outPixels=new float[size];
		ImageStack imst=imp.getStack();
		for(int fr=stfr;fr<endfr; fr++) {
			for(int csl=stsl;csl<endsl;csl++) {
				int offset=((csl-stsl))*width*height*chs+(fr-stfr)*(endsl-stsl)*width*height*chs;
				for(int i=0;i<chs;i++) {
					ImageProcessor ip=imst.getProcessor(imp.getStackIndex(i+1, csl+1, fr+1));
					Object pixels=convertForUndersample(ip.getPixels(), undersample);
					addPixels(outPixels, width, pixels, iwidth, iheight, offset, i, chs);
				}
				updatedSlices[fr*sls+csl]=true;
			}
		}
		return outPixels;
	}
	
	public Buffer getImageBuffer(int stsl, int endsl, int stfr, int endfr, Buffer buffer) {
		int bits=imp.getBitDepth();
		int iwidth=imp.getWidth(), iheight=imp.getHeight(), sls=imp.getNSlices(), frms=imp.getNFrames();
		iwidth/=undersample; iheight/=undersample;
		int width=tex4div(iwidth), height=tex4div(iheight);
		int chs=imp.getNChannels();
		int COMPS=bits==24?3:chs;
		if(pixelType==PixelType.INT_RGB10A2 || pixelType==PixelType.INT_RGBA8)COMPS=1;
		int size=width*height*COMPS*(endsl-stsl)*(endfr-stfr);
		Object outPixels=getImageArray(stsl,endsl,stfr,endfr);

		int bsize=size;
		int offset=0;
		if(buffer==imageFBs[stfr]) {
			bsize=width*height*COMPS*((endfr-stfr>1)?frms:sls);
			offset=stsl*width*height*COMPS;
			sliceSize=size;
			bufferSize=bsize;
			if(buffer==null || buffer.capacity()!=bsize) {
				if(pixelType==PixelType.BYTE) {
					buffer=GLBuffers.newDirectByteBuffer(bsize);
				}else if(pixelType==PixelType.SHORT) {
					buffer=GLBuffers.newDirectShortBuffer(bsize);
				}else if((pixelType==PixelType.INT_RGB10A2 || pixelType==PixelType.INT_RGBA8)) {
					buffer=GLBuffers.newDirectIntBuffer(bsize);
				}else if(pixelType==PixelType.FLOAT) {
					buffer=GLBuffers.newDirectFloatBuffer(bsize);
				}
			}
		}
		
		return convertPixels(outPixels, buffer, offset, COMPS);
	}
	
	private Buffer convertPixels(Object outPixels, Buffer buffer, int offset, int COMPS) {
		int bits=imp.getBitDepth();
		int size=bits==8?((byte[])outPixels).length:bits==16?((short[])outPixels).length:bits==32?((float[])outPixels).length:((int[])outPixels).length;
		buffer.position(offset);
		if(pixelType==PixelType.BYTE) {
			if(bits==8)((ByteBuffer)buffer).put(((byte[])outPixels));
			else {
				for(int i=0;i<size;i++) {
					if(bits==16)((ByteBuffer)buffer).put((byte)(((int)((((short[])outPixels)[i]&0xffff)/256.0))));
					else if(bits==32)((ByteBuffer)buffer).put((byte)(((int)(((float[])outPixels)[i]*255f))));
					else {
						int rgb=((int[])outPixels)[i];
						((ByteBuffer)buffer).put((byte)((rgb&0xff0000)>>16)).put((byte)((rgb&0xff00)>>8)).put((byte)(rgb&0xff));
						//if(COMPS==4)((ByteBuffer)buffer).put((byte)((rgb&0xff000000)>>24));
					}
				}
			}
		}else if(pixelType==PixelType.SHORT) {
			if(bits==16)((ShortBuffer)buffer).put((short[])outPixels);
			else {
				for(int i=0;i<size;i++) {
					if(bits==8 || bits==24) IJ.error("Don't use short pixel type with 8 bit image");
					if(bits==32)((ShortBuffer)buffer).put((short)(((float[])outPixels)[i]*65535f));
				}
			}
		}else if(pixelType==PixelType.INT_RGB10A2) {
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
			}else IJ.error("Don't use 10bit INT for 8 bit images");
		}else if(pixelType==PixelType.FLOAT) {
			if(bits==32)((FloatBuffer)buffer).put((float[])outPixels);
			else IJ.error("Don't use less than 32 bit image with 32 bit pixels");
		}
		buffer.position(offset);
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

}
