package ajs.joglcanvas;

import ij.CompositeImage;
import ij.IJ;
import ij.ImageListener;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Menus;
import ij.Prefs;
import ij.gui.ImageCanvas;
import ij.gui.Roi;
import ij.gui.ScrollbarWithLabel;
import ij.gui.StackWindow;
import ij.measure.Calibration;
import ij.process.ImageProcessor;
import ij.process.LUT;

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.CheckboxMenuItem;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.GraphicsConfiguration;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Menu;
import java.awt.MenuItem;
import java.awt.Point;
import java.awt.PopupMenu;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.nio.Buffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.ByteBuffer;

import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GL2ES2;
import com.jogamp.opengl.GL4;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.util.texture.awt.AWTTextureIO;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLException;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.util.awt.AWTGLReadBufferUtil;

public class JOGLImageCanvas extends ImageCanvas implements GLEventListener, ImageListener, KeyListener, ActionListener, ItemListener, WindowListener{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	final protected GLCanvas icc;
	protected boolean disablePopupMenu;
	protected double dpimag=1.0;
	protected boolean myImageUpdated=true;
	protected boolean needImageUpdate=false;
	private int[] lastPosition=new int[3];
	private boolean deletePBOs=false;
	protected boolean isMirror=false;
	private Frame mirror=null;
	private boolean mirrorMagUnlock=false;
	private boolean isFrameStack=false;
	private Rectangle prevSrcRect=null;
	private boolean[] ltr=null;

	protected boolean go3d=true;
	public String renderFunction=JCP.renderFunction;
	public boolean usePBOforSlices=JCP.usePBOforSlices;
	protected int sx,sy;
	protected float dx=0f,dy=0f,dz=0f;
	
	private PopupMenu dcpopup=null;
	private MenuItem mi3d=null;
	protected boolean myHZI=false;

	private int imageTexture;
	private int roiTexture;
	private FloatBuffer vertb=null;
	private int[] imagePBO=new int[] {0};
	private int[] overlayTextures;
	private Buffer[] imageFBs;
	private boolean[] updatedBuffers;
	private boolean[] updatedBuffersSlices;
	private double[] lutminmaxs=null;
	private int updatingBuffers=0;
	private int undersample=JCP.undersample;
	enum StereoType{OFF, CARDBOARD, ANAGLYPH, QUADBUFFER};
	private static String[] stereoTypeStrings=new String[] {"Stereo off", "Google Cardboard-SBS","Anaglyph (red-blue)","OpenGL Quad Buffers"};
	private static final float CB_MAXSIZE=4f;
	private static final float CB_TRANSLATE=0.44f;
	private StereoType stereoType=StereoType.OFF;

	enum PixelType{BYTE, SHORT, FLOAT, INT_RGB10A2};
	private static final String[] pixelTypeStrings=new String[] {"4 bytes (8bpc, 32bit)","4 shorts (16bpc 64bit)","4 floats (32bpc 128bit)","1 int RGB10A2 (10bpc, 32bit)"};
	protected static final int intinternalformat=GL2.GL_RGB10_A2;
	protected static final int intpformat=GL2.GL_UNSIGNED_INT_2_10_10_10_REV;
	private PixelType pixelType=PixelType.BYTE;
	private PixelType pixelType3d=PixelType.BYTE;
	
	private BIScreenGrabber myscreengrabber=null;
	private AWTGLReadBufferUtil ss=null;
	private RoiGLDrawUtility rgldu=null;
	//private long starttime=0;

	public JOGLImageCanvas(ImagePlus imp, boolean mirror) {
		super(imp);
		if(!mirror) {setOverlay(imp.getCanvas().getOverlay());}		
		if(JCP.glCapabilities==null && !JCP.setGLCapabilities()) IJ.showMessage("error in GL Capabilities");
		int[] bits=new int[] {JCP.glCapabilities.getAlphaBits(),JCP.glCapabilities.getRedBits(),JCP.glCapabilities.getGreenBits(),JCP.glCapabilities.getBlueBits()};
		if(imp.getBitDepth()>8 && imp.getBitDepth()!=24) {
			if(bits[0]>8||bits[1]>8||bits[2]>8||bits[3]>8 && JCP.glCapabilities.getGLProfile().isGL4()) {
				pixelType=PixelType.SHORT;
			}
			if(bits[0]<=2 && bits[1]==10 && bits[2]==10 && bits[3]==10) {
				pixelType=PixelType.INT_RGB10A2;
			}
		}
		icc=new GLCanvas(JCP.glCapabilities) /*{
			@Override
			public GraphicsConfiguration getGraphicsConfiguration() {
				GraphicsConfiguration gc=super.getGraphicsConfiguration();
				AffineTransform t=gc.getDefaultTransform();
				IJ.log("icc gc scale: "+t.getScaleX());
				t.setToScale(1.0, 1.0);
				return gc;
			}
		}*/;
		float[] res=new float[] {1.0f,1.0f};
		icc.setSurfaceScale(res);
		res=icc.getRequestedSurfaceScale(res);
		//IJ.log("GetRequestedSurfaceScale:"+res[0]+" "+res[1]);
		icc.addMouseListener(this);
		icc.addMouseMotionListener(this);
		icc.addKeyListener(ij);
		icc.setFocusTraversalKeysEnabled(false);
		icc.setSize(imageWidth, imageHeight);
		icc.setPreferredSize(new Dimension(imageWidth,imageHeight));
		icc.addGLEventListener(this);
		ImagePlus.addImageListener(this);
		createPopupMenu();
		updateLastPosition();
		//if((4.0d*(double)imageWidth * (double)imageHeight*(double)imp.getNSlices())>(double)(Integer.MAX_VALUE-5)) toobig=true;
		initBuffers(imp.getNFrames(),imp.getNSlices());
		if(JCP.backgroundLoadBuffers) {
			updateBuffersBackground(null);
		}
		prevSrcRect=new Rectangle(0, 0, 0, 0);
		if(mirror)setMirror();
		lutminmaxs=new double[imp.getNChannels()*2];
	}
	
	private boolean initBuffers(int frms, int sls) {
		boolean result=false;
		isFrameStack=(frms>1 && sls==1);
		if(isFrameStack) {sls=frms;frms=1;}
		if(imageFBs==null || imageFBs.length!=frms) {imageFBs=new Buffer[frms]; result=true;}
		if(updatedBuffers==null || updatedBuffers.length!=frms) {updatedBuffers=new boolean[frms]; result=true;}
		if(updatedBuffersSlices==null || updatedBuffersSlices.length!=frms*sls) {updatedBuffersSlices=new boolean[frms*sls]; result=true;}
		return result;
	}

	//GLEventListener methods
	@Override
	public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
		GL2 gl2 = drawable.getGL().getGL2();
		gl2.glMatrixMode(GL2.GL_PROJECTION);
		gl2.glLoadIdentity();
		gl2.glOrtho(-1, 1, -(float)srcRect.height/srcRect.width, (float)srcRect.height/srcRect.width, -1, 1);
		gl2.glMatrixMode(GL2.GL_MODELVIEW);
		gl2.glLoadIdentity();
	}

	@Override
	public void init(GLAutoDrawable drawable) {
		GraphicsConfiguration gc=icc.getParent().getGraphicsConfiguration();
		AffineTransform t=gc.getDefaultTransform();
		dpimag=t.getScaleX();
		icc.setSize(dstWidth, dstHeight);
		double dpimag2=drawable.getSurfaceHeight()/dstHeight;
		if(dpimag>1.0)IJ.log("Dpimag: "+dpimag+" "+dpimag2);
		if(IJ.isMacOSX())icc.setLocation(4,47);
		GL2ES2 gl2 = drawable.getGL().getGL2ES2();
		gl2.glClearColor(0f, 0f, 0f, 0f);
		gl2.glDisable(GL2ES2.GL_DEPTH_TEST);

		int numTextures = 2; 
		int[] textureHandles = new int[numTextures]; 
		gl2.glGenTextures(numTextures, textureHandles, 0);

		imageTexture = textureHandles[0];
		roiTexture=textureHandles[1];
		
	}

	@Override
	public void dispose(GLAutoDrawable drawable) {
		GL2ES2 gl=drawable.getGL().getGL2ES2();
		int[] thandles=new int[] {imageTexture, roiTexture};
		gl.glDeleteTextures(thandles.length, thandles, 0);
		if(overlayTextures!=null)gl.glDeleteTextures(overlayTextures.length, overlayTextures, 0);
		if(imagePBO!=null)gl.glDeleteBuffers(imagePBO.length, imagePBO,0);
		imp.unlock();
	}

	@Override
	public void display(GLAutoDrawable drawable) {
		//IJ.log(""+icc);
		//IJ.log("\\Update2:Display took: "+(System.nanoTime()-starttime)/1000000L+"ms");
		//starttime=System.nanoTime();
		if(imp.isLocked())return;
		imp.lock();
		int sl=imp.getZ()-1;
		int fr=imp.getT()-1;
		int sls=imp.getNSlices();
		int frms=imp.getNFrames();
		if(initBuffers(frms,sls)) {
			myImageUpdated=true;
			if(isMirror) {
				addMirrorListeners();
				updateMirror();
			}
		}
		if(isFrameStack) {sl=fr; fr=0; sls=frms; frms=1;}
		float yrat=(float)srcRect.height/srcRect.width;
		
		GL2ES2 gl2es2 = drawable.getGL().getGL2ES2();
		GL2 gl2=gl2es2.getGL2();
		gl2es2.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
		gl2.glColor4f(1f, 1f, 1f,1f);
		
		int srcRectWidthMag = (int)(srcRect.width*magnification+0.5);
		int srcRectHeightMag = (int)(srcRect.height*magnification+0.5);
		//int[] vps=new int[4];
		//gl2.glGetIntegerv(GL2.GL_VIEWPORT, vps, 0);
		//if(dpimag>1.0)IJ.log("bef VPS: "+vps[0]+" "+vps[1]+" "+vps[2]+" "+vps[3]);
		if(dpimag>1.0 && !IJ.isMacOSX())gl2.glViewport(0, 0, (int)(srcRectWidthMag*dpimag+0.5), (int)(srcRectHeightMag*dpimag+0.5));
		if(IJ.isMacOSX())gl2.glViewport(0, 0, dstWidth, dstHeight);
		//gl2.glGetIntegerv(GL2.GL_VIEWPORT, vps, 0);
		//if(dpimag>1.0)IJ.log("aft VPS: "+vps[0]+" "+vps[1]+" "+vps[2]+" "+vps[3]);
		
		//Roi and Overlay
		Roi roi=imp.getRoi();
		ij.gui.Overlay overlay=imp.getCanvas().getOverlay();
		boolean doRoi=false;
		if(!JCP.openglroi && (roi!=null || (!go3d && overlay!=null))) {
			BufferedImage roiImage=new BufferedImage(srcRectWidthMag, srcRectHeightMag, BufferedImage.TYPE_INT_ARGB);
			Graphics g=roiImage.getGraphics();
			if(roi!=null) {roi.draw(g); doRoi=true;}
			if(overlay!=null) {
				for(int i=0;i<overlay.size();i++) {
					Roi oroi=overlay.get(i);
					oroi.setImage(imp);
					int rc=oroi.getCPosition(), rz=oroi.getZPosition(),rt=oroi.getTPosition();
					if((rc==0||rc==imp.getC()) && (rz==0||rz==(sl+1)) && (rt==0||rt==imp.getT())) {oroi.drawOverlay(g); doRoi=true;}
				}
			}
			if(doRoi)createRgbaTexture(gl2es2, roiTexture, AWTTextureIO.newTextureData(gl2es2.getGLProfile(), roiImage, false).getBuffer(), srcRectWidthMag, srcRectHeightMag, 1);
		}
		boolean[] doOv=null;
		if(!JCP.openglroi && overlay!=null && go3d) {
			doOv=new boolean[sls];
			if(overlayTextures==null)overlayTextures=new int[sls];
			else if(overlayTextures.length!=sls) {
				gl2.glDeleteTextures(overlayTextures.length, overlayTextures, 0);
				overlayTextures=new int[sls];
			}
			for(int osl=0;osl<sls;osl++) {
				BufferedImage roiImage=null;
				Graphics g=null;
				for(int i=0;i<overlay.size();i++) {
					Roi oroi=overlay.get(i);
					int rc=oroi.getCPosition(), rz=oroi.getZPosition(),rt=oroi.getTPosition();
					if((rc==0||rc==imp.getC()) && (rz==0||rz==(osl+1)) && (rt==0||rt==imp.getT())) {
						if(g==null) {
							roiImage=new BufferedImage(srcRectWidthMag, srcRectHeightMag, BufferedImage.TYPE_INT_ARGB);
							g=roiImage.getGraphics();
						}
						oroi.setImage(imp);
						oroi.drawOverlay(g);
						doOv[osl]=true;
					}
				}
				if(doOv[osl]) {
					if(overlayTextures[osl]==0)gl2.glGenTextures(1, overlayTextures, osl);
					createRgbaTexture(gl2es2, overlayTextures[osl], AWTTextureIO.newTextureData(gl2es2.getGLProfile(), roiImage, false).getBuffer(), srcRectWidthMag, srcRectHeightMag, 1);
				}
			}
		}
		
		if(imagePBO==null || deletePBOs || imagePBO.length!=frms) {
			if(imagePBO!=null)gl2es2.glDeleteBuffers(imagePBO.length, imagePBO,0);
			imagePBO=new int[frms];
			updatedBuffersSlices=new boolean[sls*frms];
			deletePBOs=false;
		}
		if(go3d&&sls==1)go3d=false;
		
		if(myImageUpdated) {
			boolean loadtex=false;
			if(go3d) {updateBuffers(fr+1,true);}
			else {
				Buffer sliceImage=null;
				if(usePBOforSlices) {
					//IJ.log("sl:"+(sl+1)+" fr:"+(fr+1)+" lps:"+lastPosition[1]+" lpf:"+lastPosition[2]);
					if((lastPosition[0]==imp.getC()||imp.getCompositeMode()!=IJ.COMPOSITE) && lastPosition[1]==(imp.getZ()) && lastPosition[2]==imp.getT()) {
						updatedBuffersSlices=new boolean[sls*frms];
					}
					if(!updatedBuffersSlices[fr*sls+sl]) {
						//IJ.log("Updating for chslfr:"+imp.getC()+" "+(sl+1)+" "+(fr+1));
						sliceImage=getImageBufferSlice(false, imp.getZ(), imp.getT());
						checkBuffers();
						int psize=imageWidth*imageHeight*((pixelType==PixelType.INT_RGB10A2)?1:4);
						int bsize=sls*psize;
						if(imageFBs[fr]==null) {
							if(pixelType==PixelType.FLOAT)imageFBs[fr]=Buffers.newDirectFloatBuffer(bsize);
							else if(pixelType==PixelType.SHORT)imageFBs[fr]=Buffers.newDirectShortBuffer(bsize);
							else if(pixelType==PixelType.BYTE)imageFBs[fr]=Buffers.newDirectByteBuffer(bsize);
							else if(pixelType==PixelType.INT_RGB10A2)imageFBs[fr]=Buffers.newDirectIntBuffer(bsize);
						}
						try {
							updateImageStackBuffer(imageFBs[fr],sliceImage,sl+1);
							int offset=psize*sl;
							imagePBO[fr]=updateSubRgbaPBO(gl2es2, imagePBO[fr],imageFBs[fr],offset, psize, bsize);
							updatedBuffersSlices[fr*sls+sl]=true;
						}catch(Exception e) {
							if(e instanceof GLException) {
								GLException gle=(GLException)e;
								IJ.log(gle.getMessage());
								IJ.log("Out of memory, switching usePBOforSlices off");
								usePBOforSlices=false;
								imageFBs[fr]=null; 
								gl2es2.glDeleteBuffers(imagePBO.length, imagePBO,0);
								imagePBO=new int[frms];
								updatedBuffersSlices=new boolean[sls*frms];
							}
						}
					}else loadtex=true;
				}else {
					sliceImage=getImageBufferSlice(false, imp.getZ(), imp.getT());
				}
				if(loadtex)loadTexFromPBO(gl2es2, imagePBO[fr], imageTexture, imageWidth, imageHeight, 1, sl, pixelType);
				else {
					gl2es2.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
					createRgbaTexture(gl2es2, imageTexture, sliceImage, imageWidth, imageHeight, 1);
				}
			}
			myImageUpdated=false;
			if(needImageUpdate) {showUpdateButton(false);}
			needImageUpdate=false;
		}
		if(go3d) {
			if(updatingBuffers>0 && !updatedBuffers[fr])updateBuffers(fr+1,false);
			for(int ifr=0;ifr<frms;ifr++) {
				if(updatedBuffers[ifr]) {
					imagePBO[ifr]=updateRgbaPBO(gl2es2, imagePBO[ifr], imageFBs[ifr]);
					updatedBuffers[ifr]=false;
					IJ.showStatus("PBO load");
				}
			}
			loadTexFromPBO(gl2es2, imagePBO[fr], imageTexture, imageWidth/undersample, imageHeight/undersample, sls, 0, pixelType3d);
		}
		

		Calibration cal=imp.getCalibration();
		float zmax=0f;
		int zmaxsls=(int)((cal.pixelDepth*(double)sls)/(cal.pixelWidth));
		if(go3d)zmax=(float)(zmaxsls)/(float)srcRect.width;
		
		float 	offx=2f*(float)srcRect.x/srcRect.width, vwidth=2f*(float)imageWidth/srcRect.width,
				offy=2f*(1f-((float)(srcRect.y+srcRect.height)/imageHeight))*(float)imageHeight/srcRect.height,
				vheight=2f*(float)imageHeight/srcRect.height;
		//Quad, 3 space verts, 3 texture verts per each of 4 points of a quad
		float[] initVerts=new float[] {
				-1f-offx, 			(-1f-offy)*yrat, 			-zmax,			0, 1, go3d?1f:0,
				-1f-offx+vwidth, 	(-1f-offy)*yrat, 			zmax,			1, 1, 0,
				-1f-offx+vwidth, 	(-1f-offy+vheight)*yrat, 	zmax,			1, 0, 0,
				-1f-offx, 			(-1f-offy+vheight)*yrat, 	-zmax,			0, 0, go3d?1f:0
		};

		//drawing
		if(stereoType==StereoType.ANAGLYPH) {
			renderFunction="MAX";
		}
		gl2es2.glDrawBuffers(1, new int[] {GL2ES2.GL_BACK}, 0);

		gl2es2.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
		int views=1;
		if(go3d && stereoType.ordinal()>0)views=2;
		for(int stereoi=0;stereoi<views;stereoi++) {
			
			gl2.glMatrixMode(GL2.GL_MODELVIEW);
			gl2.glLoadIdentity();
			gl2es2.glEnable(GL2ES2.GL_TEXTURE_3D);
			gl2es2.glBindTexture(GL2ES2.GL_TEXTURE_3D, imageTexture);
			if(go3d) {
				gl2es2.glEnable(GL2ES2.GL_BLEND);
				if(stereoType==StereoType.QUADBUFFER) {
					if(stereoi==0)gl2es2.glDrawBuffers(1, new int[] {GL2.GL_BACK_LEFT}, 0);
					else gl2es2.glDrawBuffers(1, new int[] {GL2.GL_BACK_RIGHT}, 0);
				}
				gl2.glMatrixMode(GL2.GL_PROJECTION);
				gl2.glLoadIdentity();
				if(stereoType==StereoType.CARDBOARD)gl2.glOrtho(-CB_MAXSIZE, CB_MAXSIZE, -CB_MAXSIZE*yrat, CB_MAXSIZE*yrat, -CB_MAXSIZE, CB_MAXSIZE);
				else gl2.glOrtho(-1, 1, -yrat, yrat, -1, 1);
				gl2.glMatrixMode(GL2.GL_MODELVIEW);
				
				//Rotate
				float dxst=(float)dx;
				if(stereoi>0) {dxst-=(float)JCP.stereoSep; if(dxst<0)dxst+=360f;}
				gl2.glRotatef(dxst, 0f, 1.0f, 0f);
				gl2.glRotatef((float)dy, 1.0f, 0f, 0f);
				gl2.glRotatef((float)dz, 0f, 0f, 1.0f);
				
				float[] matrix=new float[16];
				gl2.glGetFloatv(GL2.GL_MODELVIEW_MATRIX, matrix,0);
				//IJ.log("\\Update0:X x"+Math.round(100.0*matrix[0])/100.0+" y"+Math.round(100.0*matrix[1])/100.0+" z"+Math.round(100.0*matrix[2])/100.0);
				//IJ.log("\\Update1:Y x"+Math.round(100.0*matrix[4])/100.0+" y"+Math.round(100.0*matrix[5])/100.0+" z"+Math.round(100.0*matrix[6])/100.0);
				//IJ.log("\\Update2:Z x"+Math.round(100.0*matrix[8])/100.0+" y"+Math.round(100.0*matrix[9])/100.0+" z"+Math.round(100.0*matrix[10])/100.0);
				
				boolean left,top,reverse;
				float Xza=Math.abs(matrix[2]), Yza=Math.abs(matrix[6]), Zza=Math.abs(matrix[10]);
				float maxZvec=Math.max(Xza, Math.max(Yza, Zza));
				left=(maxZvec==Xza);
				top=(maxZvec==Yza);
				reverse=(Zza==-matrix[10]);
				if(left)reverse=Xza==matrix[2];
				if(top)reverse=Yza==-matrix[6];
				
				if(ltr==null || !(ltr[0]==left && ltr[1]==top /*&& ltr[2]==reverse*/) || !srcRect.equals(prevSrcRect)) {
					if(left) { //left or right
						int lim=imageWidth*3*4*2;
						if(vertb==null ||vertb.limit()!=lim) {vertb=Buffers.newDirectFloatBuffer(lim);}
						for(float p=0;p<imageWidth;p+=1.0f) {
							float xt,xv;
							xt=(p+0.5f)/imageWidth;
							xv=(p*2f-srcRect.width-2f*srcRect.x)/srcRect.width;
							if(!reverse) {xt=1f-xt; xv=-xv;}
							for(int i=0;i<4;i++) {
								vertb.put(xv); vertb.put(initVerts[i*6+1]); vertb.put(initVerts[i*6+2]);
								vertb.put(xt); vertb.put(initVerts[i*6+4]); vertb.put(initVerts[i*6+5]);
							}
						}
					} else if(top) { //top or bottom
						int lim=imageHeight*3*4*2;
						if(vertb==null ||vertb.limit()!=lim) {vertb=Buffers.newDirectFloatBuffer(lim);}
						for(float p=0;p<imageHeight;p+=1.0f) {
							float yt,yv;
							yt=(p+0.5f)/imageHeight;
							yv=(float)(imageHeight-p)/srcRect.height*2f*yrat+initVerts[1];
							if(!reverse) {yt=1f-yt; yv=-yv;}
							for(int i=0;i<4;i++) {
								float zv=initVerts[i*6+2];
								float zt=initVerts[i*6+5];
								if(i==1) {zv=-zmax; zt=1f;}
								else if(i==3) {zv=zmax; zt=0f;}
								vertb.put(initVerts[i*6]); vertb.put(yv); vertb.put(zv);
								vertb.put(initVerts[i*6+3]); vertb.put(yt); vertb.put(zt);
							}
						}
					}else { //front or back
						int lim=zmaxsls*3*4*2;
						if(vertb==null ||vertb.limit()!=lim) {vertb=Buffers.newDirectFloatBuffer(lim);}
						for(float zi=0;zi<zmaxsls;zi+=1.0f) {
							float z;
							if(reverse)z=zi;
							else z=((float)zmaxsls-zi-1.0f);
							for(int i=0;i<4;i++) {
								vertb.put(initVerts[i*6]); vertb.put(initVerts[i*6+1]); vertb.put(((float)zmaxsls-2f*z)/srcRect.width); 
								vertb.put(initVerts[i*6+3]); vertb.put(initVerts[i*6+4]); vertb.put((z+0.5f)/zmaxsls);
							}
						}
					}
					//for(int i=0;i<vertb.limit()/6;i++) {
					//	if(i==4)i=vertb.limit()/6-4;
					//	IJ.log((reverse?"rev ":"")+"Vert"+i+" vx"+vertb.get(i*6)+" vy"+vertb.get(i*6+1)+" vz"+vertb.get(i*6+2)+
					//	" tx"+vertb.get(i*6+3)+" ty"+vertb.get(i*6+4)+" tz"+vertb.get(i*6+5));
					//}
				}
				ltr=new boolean[] {left,top,reverse};
				
				
				if(stereoType==StereoType.CARDBOARD) {
					gl2.glMatrixMode(GL2.GL_PROJECTION);
					if(stereoi==0)gl2.glTranslatef(-CB_MAXSIZE*CB_TRANSLATE, 0f, 0f);
					else gl2.glTranslatef(CB_MAXSIZE*CB_TRANSLATE,0f,0f);
					gl2.glMatrixMode(GL2.GL_MODELVIEW);
				}
				
				//Blend
				if(renderFunction.equals("MAX")) {
					gl2.glBlendEquation(GL2.GL_MAX);
					gl2.glBlendFunc(GL2.GL_SRC_COLOR, GL2.GL_DST_COLOR);
				}else if(renderFunction.equals("ALPHA")) {
					gl2.glBlendEquation(GL2.GL_FUNC_ADD);
					gl2.glBlendFunc(GL2.GL_SRC_ALPHA, GL2.GL_ONE_MINUS_SRC_ALPHA);
				}
				
				if(stereoType==StereoType.ANAGLYPH) {
					if(stereoi==0)gl2.glColor4fv(RoiGLDrawUtility.getFloatColor(JCP.leftAnaglyphColor),0);
					else gl2.glColor4fv(RoiGLDrawUtility.getFloatColor(JCP.rightAnaglyphColor),0);
				}else gl2.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
			}else {
				//gl2.glDisable(GL2.GL_BLEND);
				boolean push=false;
				if(vertb==null || vertb.limit()!=24) {vertb=Buffers.newDirectFloatBuffer(4*3*2); push=true;}
				if(!srcRect.equals(prevSrcRect))push=true;
				if(push) {
					vertb.put(initVerts);
					for(int i=0;i<initVerts.length/6;i++)vertb.put(i*6+5,0.5f);
				}				
			}
			drawTexGL6f(gl2, vertb, GL2.GL_QUADS);
			gl2es2.glDisable(GL2ES2.GL_TEXTURE_3D);

			//brighten
			LUT[] luts=imp.getLuts();
			boolean dobr=false;
			for(int i=0;i<luts.length;i++) {
				if(luts[i].min!=lutminmaxs[i*2] || luts[i].max!=lutminmaxs[i*2+1])dobr=true;
			}
			if(go3d && dobr) {
				gl2.glEnable(GL2.GL_BLEND);
				gl2.glPushMatrix();
				gl2.glLoadIdentity();
				//int bits=imp.getBitDepth();
				//float omax=(bits==32)?(1.0f):(bits==16)?(4095f):(255f);
				FloatBuffer vb=Buffers.newDirectFloatBuffer(new float[] {
						-1,	-yrat,	0f,
						1,	-yrat,	0f,
						1,	yrat,	0f,
						-1,	yrat,	0f,
				});
				gl2.glEnableClientState(GL2.GL_VERTEX_ARRAY);
				gl2.glVertexPointer(3, GL2.GL_FLOAT, 3*Buffers.SIZEOF_FLOAT, vb);
				for(int i=0;i<luts.length;i++) {
					if(luts[i].min!=luts[i].max) {
						float omax=(float)lutminmaxs[i*2+1];
						float omin=(float)lutminmaxs[i*2];
						
						int rgb=luts[i].getRGB(255);
						float[] color=new float[] {((rgb & 0x00ff0000)==0x00ff0000)?1f:0f, ((rgb & 0x0000ff00)==0x0000ff00)?1f:0f, ((rgb & 0x000000ff)==0x000000ff)?1f:0f}; //alpha would be (rgb & 0xff000000)==0xff000000
						float min=0;
						float maxm=(omax-omin)/(float)(luts[i].max-luts[i].min);
						if(luts[i].min>omin) {
							min=((float)luts[i].min-omin)/omax;
							gl2.glColor3f(color[0]*min, color[1]*min, color[2]*min);
							gl2.glBlendEquation(GL2.GL_FUNC_REVERSE_SUBTRACT);
							gl2.glBlendFunc(GL2.GL_ONE, GL2.GL_ONE);
							gl2.glDrawArrays(GL2.GL_QUADS, 0, vb.limit()/3);
						}
						if((float)luts[i].max<omax) {
							gl2.glBlendEquation(GL2.GL_FUNC_ADD);
							gl2.glBlendFunc(GL2.GL_DST_COLOR, GL2.GL_ONE);
							while(maxm>2f) {
								gl2.glColor3f(color[0], color[1], color[2]);
								gl2.glDrawArrays(GL2.GL_QUADS, 0, vb.limit()/3);
								maxm/=2f;
							}
							gl2.glColor3f(color[0]*(maxm-1f), color[1]*(maxm-1f), color[2]*(maxm-1f));
							gl2.glDrawArrays(GL2.GL_QUADS, 0, vb.limit()/3);
						}
						if((float)luts[i].max>omax) {
							gl2.glBlendEquation(GL2.GL_FUNC_ADD);
							gl2.glBlendFunc(GL2.GL_DST_COLOR, GL2.GL_ZERO);
							gl2.glColor3f((color[0]==1f)?maxm:1f, (color[1]==1f)?maxm:1f, (color[2]==1f)?maxm:1f);
							gl2.glDrawArrays(GL2.GL_QUADS, 0, vb.limit()/3);
						}
					}
				}
				gl2.glPopMatrix();
			}
			
			if(roi!=null || overlay!=null) { 
				float z=0f;
				if(go3d) z=((float)sl/(float)(sls)*2f-1f)*zmax;
				gl2.glEnable(GL2.GL_MULTISAMPLE);
				gl2.glEnable(GL2.GL_BLEND);
				gl2.glBlendEquation(GL2.GL_FUNC_ADD);
				gl2.glBlendFunc(GL2.GL_SRC_ALPHA, GL2.GL_ONE_MINUS_SRC_ALPHA);
				if(!JCP.openglroi) {
					if(doRoi)drawGraphics(gl2es2, z, roiTexture);
					if(doOv!=null) {
						for(int osl=0;osl<sls;osl++) {
							if(doOv[osl]) {
								drawGraphics(gl2es2, ((float)osl/(float)(sls)*2f-1f)*zmax, overlayTextures[osl]);
							}
						}
					}
				}else {
					Color anacolor=null;
					if(stereoType==StereoType.ANAGLYPH)anacolor=(stereoi==0)?JCP.leftAnaglyphColor:JCP.rightAnaglyphColor;
					if(overlay!=null) {
						for(int i=0;i<overlay.size();i++) {
							Roi oroi=overlay.get(i);
							int rc=oroi.getCPosition(), rz=oroi.getZPosition(),rt=oroi.getTPosition();
							if(go3d) {
								if(rt==0||rt==fr) {
									drawRoiGL(drawable, oroi, ((float)(rz-1)/(float)(sls)*2f-1f)*zmax, false, anacolor);
								}
							}else {
								if((rc==0||rc==imp.getC()) && (rz==0||(rz)==imp.getZ()) && (rt==0||(rt)==imp.getT()))drawRoiGL(drawable, oroi, z, false, anacolor);
							}
						}
					}
					if(!go3d)gl2.glDisable(GL2.GL_BLEND);
					else gl2.glEnable(GL2.GL_BLEND);
					//gl2.glEnable(GL2.GL_LINE_SMOOTH);
					drawRoiGL(drawable, roi, z, true, anacolor);
				}
			}
			boolean nzi=(!myHZI && (srcRect.width<imageWidth || srcRect.height<imageHeight));
			//NOTE reloading ID matrix don't opengl draw anything after this!
			gl2.glLoadIdentity();
			
			if(nzi) {
				gl2.glDisable(GL2.GL_BLEND);
				gl2.glDisable(GL2.GL_MULTISAMPLE);
				drawMyZoomIndicator(drawable);
			}
			//IJ.log("\\Update0:Display took: "+(System.nanoTime()-starttime)/1000000L+"ms");
			gl2es2.glFlush();
		}
		//IJ.log("\\Update1:Display took: "+(System.nanoTime()-starttime)/1000000L+"ms");
		
		if(imageUpdated) {imageUpdated=false;} //ImageCanvas imageupdated only for single ImagePlus
		updateLastPosition();

		prevSrcRect.x=srcRect.x; prevSrcRect.y=srcRect.y;
		prevSrcRect.width=srcRect.width; prevSrcRect.height=srcRect.height;
		
		if(myscreengrabber!=null) {
			if(myscreengrabber.isReadyForUpdate()) {
				BufferedImage bi=grabScreen(drawable);
				myscreengrabber.screenUpdated(bi);
			}
		}
		imp.unlock();
	}
	
	public BufferedImage grabScreen(GLAutoDrawable drawable) {
		int x=0,y=0,width=drawable.getSurfaceWidth(),height=drawable.getSurfaceHeight();
		boolean alpha=false, awtOrientation=true;
		if(stereoType==StereoType.CARDBOARD) {
			y=(int)((1f-(1f/CB_MAXSIZE))*(float)srcRect.height/(float)srcRect.width/2f*(float)height);
			height/=CB_MAXSIZE;
		}
		if(ss==null) ss=new AWTGLReadBufferUtil(drawable.getGLProfile(), alpha);
		return ss.readPixelsToBufferedImage(drawable.getGL(), x, y, width, height, awtOrientation);
	}
	
	public void setUnderSampling(int us) {
		if(undersample==us)return;
		while(updatingBuffers>0)IJ.wait(50);
		undersample=us;
		resetBuffers();
	}
	
	public void setPixelType(PixelType newtype, boolean for3d) {
		int bits=imp.getBitDepth();
		if(bits==24)bits=8;
		if(newtype==PixelType.FLOAT && bits<32) {IJ.error("Not enough image bits for float display pixel");return;}
		if((newtype==PixelType.SHORT || newtype==PixelType.INT_RGB10A2) && (bits<16)) {IJ.error("Not enough image bits for high bit display pixel");return;}
		
		if(for3d) {
			while(updatingBuffers>0)IJ.wait(50);
			pixelType3d=newtype;
			resetBuffers();
		}
		else {
			pixelType=newtype;
			repaint();
		}
	}
	
	private int getCurrentPixelType(boolean for3d) {
		PixelType pt=for3d?pixelType3d:pixelType;
		for(int i=0;i<PixelType.values().length;i++)if(pt==PixelType.values()[i])return i;
		return -1;
	}
	
	public void resetBuffers() {
		while(updatingBuffers>0)IJ.wait(50);
		isFrameStack=imp.getNFrames()>1 && imp.getNSlices()==1;
		imageFBs=new Buffer[isFrameStack?1:imp.getNFrames()];
		deletePBOs=true;
		myImageUpdated=true;
		repaint();
	}
	
	private void checkBuffers() {
		PixelType pixelType=go3d?this.pixelType3d:this.pixelType;
		for(int i=0;i<imageFBs.length;i++) {
			if(imageFBs[i] instanceof FloatBuffer && pixelType!=PixelType.FLOAT)imageFBs[i]=null;
			if(imageFBs[i] instanceof ShortBuffer && pixelType!=PixelType.SHORT)imageFBs[i]=null;
			if(imageFBs[i] instanceof ByteBuffer && pixelType!=PixelType.BYTE)imageFBs[i]=null;
			if(imageFBs[i] instanceof IntBuffer && pixelType!=PixelType.INT_RGB10A2)imageFBs[i]=null;
		}
	}
	
	public boolean updateBuffers(int frame, boolean bgload) {
		checkBuffers();
		//if(imp.getNSlices()==1)return false; //delete if you implement a buffer for a framestack
		if(bgload && updatingBuffers>0)return true;
		if(!bgload && updatedBuffers[frame-1])return true;
		int[] skipframe=new int[0];
		if(frame>0) {
			int fr=frame-1;
			if(isFrameStack) {
				fr=0;
				frame=0;
			}
			updatedBuffers[fr]=true;
			imageFBs[fr]=getImageBufferStack(go3d, frame, imageFBs[fr]);
			int sls=imp.getNSlices();
			for(int sl=0;sl<sls;sl++)updatedBuffersSlices[fr*sls+sl]=true;
			if(isFrameStack) for(int i=0;i<imp.getNFrames();i++)updatedBuffersSlices[i]=true;
			skipframe=new int[] {frame};
		}
		if(bgload && !isFrameStack)return updateBuffersBackground(skipframe);
		else return false;
	}
	
	public boolean updateBuffersBackground(int[] skipframe) {
		checkBuffers();
		if(imp.getNSlices()==1)return false;
		if(updatingBuffers>0)return true;
		for(int i=0;i<updatedBuffers.length;i++)updatedBuffers[i]=false;
		if(skipframe==null)skipframe=new int[0];
		for(int i=0;i<skipframe.length;i++) {
			if(skipframe[i]>0)updatedBuffers[skipframe[i]-1]=true;
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
						if(!updatedBuffers[fr]) {
							imageFBs[fr]=getImageBufferStack(go3d, fr+1, imageFBs[fr]);
							updatedBuffers[fr]=true;
							int sls=imp.getNSlices();
							for(int sl=0;sl<sls;sl++)updatedBuffersSlices[fr*sls+sl]=true;
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
				Frame win=(Frame)imp.getWindow();
				if(mirror!=null)win=mirror;
				String title=win.getTitle();
				while(updatingBuffers>0) {
					if(imp==null)break;
					int ubn=0;
					for(boolean a : updatedBuffers) if(a)ubn++;
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
	
	private void updateImageStackBuffer(Buffer stackBuffer, Buffer sliceBuffer, int slice) {
		int offset=(slice-1)*imageWidth*imageHeight*((stackBuffer instanceof IntBuffer)?1:4);
		stackBuffer.position(offset);
		if(stackBuffer instanceof ByteBuffer) 
			((ByteBuffer)stackBuffer).put((byte[])sliceBuffer.array());
		else if(stackBuffer instanceof ShortBuffer)
			((ShortBuffer)stackBuffer).put((short[])sliceBuffer.array());
		else if(stackBuffer instanceof FloatBuffer)
			((FloatBuffer)stackBuffer).put((float[])sliceBuffer.array());
		else if(stackBuffer instanceof IntBuffer)
			((IntBuffer)stackBuffer).put((int[])sliceBuffer.array());
		stackBuffer.rewind();
	}
	
	//create a buffer for one frame but whole NSlices, or modify one slice within the stack buffer
	protected Buffer getImageBufferStack(boolean is3d, int frame, Buffer buffer) {
		int stsl=0,endsl=imp.getNSlices(),stfr=frame-1,bsize=endsl;
		if(frame==0) {stsl=0; endsl=1;stfr=0;frame=imp.getNFrames();bsize=frame;}
		return getImageBuffer(is3d, stsl, endsl, stfr, frame, buffer, bsize, 0, false);
	}
	
	//just return a new buffer for the one slice
	protected Buffer getImageBufferSlice(boolean is3d, int slice, int frame) {
		return getImageBuffer(is3d, slice-1, slice, frame-1, frame, null, 1, 0, true);
	}
	
	//If there is a buffer, it should be for one whole frame, otherwise one slice or whole frame
	public Buffer getImageBuffer(boolean is3d, int stsl, int endsl, int stfr, int endfr, Buffer buffer, int bsize, int sliceOffsetInBuffer, boolean notdirect) {
		PixelType type=this.pixelType;
		int bits=imp.getBitDepth();
		int width=imageWidth, height=imageHeight;
		if(is3d) {
			width/=undersample; height/=undersample;
			type=this.pixelType3d;
		}
		int chs=imp.getNChannels();
		int size=width*height*4*(endsl-stsl)*(endfr-stfr);
		bsize*=width*height*4;
		if(bsize==size)sliceOffsetInBuffer=0;
		Object outPixels;
		if(bits==8)outPixels=new byte[size];
		else if(bits==16)outPixels=new short[size];
		else if(bits==24) {size/=4; outPixels=new int[size];}
		else outPixels=new float[size];
		LUT[] luts=imp.getLuts();
		int[] lutrgbs=new int[luts.length];
		for(int i=0;i<luts.length;i++) {
			lutrgbs[i]=luts[i].getRGB(255);
			lutminmaxs[i*2+0]=luts[i].min;
			lutminmaxs[i*2+1]=luts[i].max;
		}
		ImageStack imst=imp.getStack();
		boolean[] color=new boolean[] {true,true,true};
		if(bits==24)color=new boolean[] {true};
		boolean switchcolor=false;
		int endch=imp.getChannel(),stch=endch-1;
		boolean[] active=new boolean[chs];
		for(int i=0;i<chs;i++)active[i]=true;
		if(imp.isComposite()){
			int cmode=imp.getCompositeMode();
			if(cmode==IJ.COMPOSITE){
				stch=0;endch=chs;
				active=((CompositeImage)imp).getActiveChannels();
				switchcolor=true;
			}else if(cmode==IJ.GRAYSCALE)switchcolor=false;
		}
		for(int fr=stfr;fr<endfr; fr++) {
			for(int csl=stsl;csl<endsl;csl++) {
				//int offset=(csl-offsetter)*imageWidth*imageHeight*4+(fr-stfr)*(endsl-stsl)*imageWidth*imageHeight*4;
				int offset=((csl-stsl))*width*height+(fr-stfr)*(endsl-stsl)*width*height;
				//if first time rewriting to floatPixels channel, write over previous:
				boolean[] chclear=new boolean[]{true,true,true};
				for(int i=stch;i<endch;i++) {
					if(active[i]) {
						ImageProcessor ip=imst.getProcessor(imp.getStackIndex(i+1, csl+1, fr+1));
						Object pixels=ip.getPixels();
						
						if(is3d) {
							pixels=convertForUndersample(pixels,imageWidth,imageHeight);
						}
						double min=0,max=0;
						if(bits!=24) {
							min=luts[i].min; max=luts[i].max;
							//lutScaleImage(pixels,luts[i].min,luts[i].max);
							int rgb=lutrgbs[i];
							if(switchcolor)color=new boolean[] {(rgb & 0x00ff0000)==0x00ff0000, (rgb & 0x0000ff00)==0x0000ff00, (rgb & 0x000000ff)==0x000000ff}; //alpha would be (rgb & 0xff000000)==0xff000000
						}
						for(int ci=0;ci<color.length;ci++) {
							if(color[ci]) {
								if(chclear[ci]) {
									addPixels(outPixels, pixels, offset, ci, "COPY",min,max); chclear[ci]=false;
								}else {
									addPixels(outPixels, pixels, offset, ci, "MAX",min,max);
								}
							}
						}
					}
				}
				for(int i=0;i<(width*height);i++){
					if(bits==8) ((byte[])outPixels)[offset*4+3+i*4]=is3d?(byte)Math.max(((byte[])outPixels)[offset*4+i*4]&0xff, Math.max(((byte[])outPixels)[offset*4+1+i*4]&0xff,((byte[])outPixels)[offset*4+2+i*4]&0xff)):(byte)255;
					else if(bits==16)((short[])outPixels)[offset*4+3+i*4]=is3d?(short)Math.max(((short[])outPixels)[offset*4+i*4]&0xffff, Math.max(((short[])outPixels)[offset*4+1+i*4]&0xffff,((short[])outPixels)[offset*4+2+i*4]&0xffff)):(short)65535;
					else if(bits==32)((float[])outPixels)[offset*4+3+i*4]=is3d?Math.max(((float[])outPixels)[offset*4+i*4], Math.max(((float[])outPixels)[offset*4+1+i*4],((float[])outPixels)[offset*4+2+i*4])):1f;
					else {
						if(!is3d)((int[])outPixels)[offset+i]|=0xff000000;
						else {
							int iv=((int[])outPixels)[offset+i];
							((int[])outPixels)[offset+i] |= (((iv&0xff0000)<<8) | ((iv&0xff00)<<16) | ((iv&0xff)<<24));
						}
					}
				}
			}
		}

		if(type==PixelType.BYTE && (buffer==null || buffer.limit()!=bsize)) {
			if(notdirect)buffer=ByteBuffer.allocate(bsize);
			else buffer=Buffers.newDirectByteBuffer(bsize);
		}else if(type==PixelType.SHORT && (buffer==null || buffer.limit()!=bsize)) {
			if(notdirect)buffer=ShortBuffer.allocate(bsize);
			else buffer=Buffers.newDirectShortBuffer(bsize);
		}else if(type==PixelType.INT_RGB10A2 && (buffer==null || buffer.limit()!=bsize)) {
			if(notdirect)buffer=IntBuffer.allocate(bsize/4);
			else buffer=Buffers.newDirectIntBuffer(bsize/4);
		}else if(type==PixelType.FLOAT && (buffer==null || buffer.limit()!=bsize)) {
			if(notdirect)buffer=FloatBuffer.allocate(bsize);
			else buffer=Buffers.newDirectFloatBuffer(bsize);
		}
				
		int offset=sliceOffsetInBuffer*width*height*(type==PixelType.INT_RGB10A2?1:4);
		buffer.position(offset);
		if(type==PixelType.BYTE) {
			if(bits==8)((ByteBuffer)buffer).put(((byte[])outPixels));
			else {
				for(int i=0;i<size;i++) {
					if(bits==16)((ByteBuffer)buffer).put((byte)(((int)((((short[])outPixels)[i]&0xffff)/65535.0*255.0))));
					else if(bits==32)((ByteBuffer)buffer).put((byte)(((int)(((float[])outPixels)[i]*255f))));
					else {
						int rgb=((int[])outPixels)[i];
						((ByteBuffer)buffer).put((byte)((rgb&0xff0000)>>16)).put((byte)((rgb&0xff00)>>8)).put((byte)(rgb&0xff)).put((byte)((rgb&0xff000000)>>24));
					}
				}
			}
		}else if(type==PixelType.SHORT) {
			if(bits==16)((ShortBuffer)buffer).put((short[])outPixels);
			else {
				for(int i=0;i<size;i++) {
					if(bits==8 || bits==24) IJ.error("Don't use short pixel type with 8 bit image");
					if(bits==32)((ShortBuffer)buffer).put((short)(((float[])outPixels)[i]*65535f));
				}
			}
		}else if(type==PixelType.INT_RGB10A2) {
			if(bits==32) {
				float[] floatPixels=((float[])outPixels);
				for(int i=0;i<size;i+=4) {
					int red=(((int)(floatPixels[i]*1023f))&0x3ff);
					int green=(((int)(floatPixels[i+1]*1023f))&0x3ff);
					int blue=(((int)(floatPixels[i+2]*1023f))&0x3ff);
					int alpha=(((int)(floatPixels[i+3]*0x3))&0x3);
					((IntBuffer)buffer).put(alpha<<30 | blue <<20 | green<<10 | red);
				}
			}else if(bits==16) {
				short[] shortPixels=((short[])outPixels);
				for(int i=0;i<size;i+=4) {
					int red=(((int)((shortPixels[i]&0xffff)/65535f*1023f))&0x3ff);
					int green=(((int)((shortPixels[i+1]&0xffff)/65535f*1023f))&0x3ff);
					int blue=(((int)((shortPixels[i+2]&0xffff)/65535f*1023f))&0x3ff);
					int alpha=(((int)((shortPixels[i+3]&0xffff)/65535f*0x3))&0x3);
					((IntBuffer)buffer).put(alpha<<30 | blue <<20 | green<<10 | red);
				}
			}else IJ.error("Don't use 10bit INT for 8 bit images");
		}else if(type==PixelType.FLOAT) {
			if(bits==32)((FloatBuffer)buffer).put((float[])outPixels);
			else IJ.error("Don't use less than 32 bit image with 32 bit pixels");
		}
		buffer.rewind();
		return buffer;
	}


	protected void lutScaleImage(Object pixels, double min, double max) {
		boolean dobyte=pixels instanceof byte[];
		boolean doshort=pixels instanceof short[];
		int size = dobyte?((byte[])pixels).length:(doshort?((short[])pixels).length:((float[])pixels).length);
		double scale = (max-min);
		for (int i=0; i<size; i++) {
			if(dobyte)((byte[])pixels)[i] = (byte)Math.max(255,Math.min(0,(int)(((double)(((byte[])pixels)[i]&0xff)-min)/scale)));
			else if(doshort)((short[])pixels)[i] = (short)Math.max(65535,Math.min(0,(int)(((double)(((short[])pixels)[i]&0xffff)-min)/scale)));
			else ((float[])pixels)[i] = Math.max(1f,Math.min(0f,(float)(((double)((float[])pixels)[i]-min)/scale)));
		}
	}

	protected Object convertForUndersample(Object pixels, int width, int height) {
		if(undersample==1) return pixels;
		int uwidth=width/undersample,uheight=height/undersample;
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
				if(dobyte)((byte[])tpixels)[y*uwidth+x]=((byte[])pixels)[y*undersample*width+x*undersample];
				else if(doshort)((short[])tpixels)[y*uwidth+x]=((short[])pixels)[y*undersample*width+x*undersample];
				else if(doint)((int[])tpixels)[y*uwidth+x]=((int[])pixels)[y*undersample*width+x*undersample];
				else ((float[])tpixels)[y*uwidth+x]=((float[])pixels)[y*undersample*width+x*undersample];
			}
		}
		return tpixels;
	}

	/*
	protected void addPixels(float[] pixels, float[] newpixels, int offset, int bands, int c, String type) {
		for(int i=0;i<newpixels.length;i++) {
			if(type=="ADD") {
				pixels[i*bands+c+offset*bands]+=newpixels[i];
				if(pixels[i*bands+c+offset*bands]>1f)pixels[i*bands+c+offset*bands]=1f;
			}else if(type=="COPY_TRANSPARENT_ZERO") {
				if(newpixels[i]>0)pixels[i*bands+c+offset*bands]=newpixels[i];
			}else if(type=="COPY") {
				pixels[i*bands+c+offset*bands]=newpixels[i];
			}else if(type=="MAX") {
				if(newpixels[i]>pixels[i*bands+c+offset*bands])pixels[i*bands+c+offset*bands]=newpixels[i];
			}
		}
	}
*/
	
	protected void addPixels(Object pixels, Object newpixels, int offset, int c, String type, double min, double max) {
		int bands=4;
		boolean dobyte=(pixels instanceof byte[]);
		boolean doshort=(pixels instanceof short[]);
		boolean doint=(pixels instanceof int[]);
		int length=dobyte?((byte[])newpixels).length:(doshort?((short[])newpixels).length:(doint?((int[])newpixels).length:((float[])newpixels).length));
		double scale = (max-min);
		for(int i=0;i<length;i++) {
			byte bvalue=0;
			short svalue=0;
			float fvalue=0;
			if(dobyte) {bvalue =(byte)(Math.max(0,Math.min(255,(int)(255.0*(((double)((int)((byte[])newpixels)[i]&0xff)-min)/scale)))));
			}else if(doshort) {
				svalue = (short)(Math.max(0,Math.min(65535,(int)(65535.0*((double)(((short[])newpixels)[i]&0xffff)-min)/scale))));
			}else if(!doint) {fvalue = (float)Math.max(0.0,Math.min(1.0,(((double)((float[])newpixels)[i]-min)/scale)));
			}
			if(type=="ADD") {
				if(dobyte) {
					if((bvalue&0xff)>(255-(((byte[])pixels)[i*bands+c+offset*bands]&0xff))) ((byte[])pixels)[i*bands+c+offset*bands]=(byte)255;
					else ((byte[])pixels)[i*bands+c+offset*bands]+=bvalue;
				} else if(doshort) {
					if((svalue&0xffff)>(65535-(((short[])pixels)[i*bands+c+offset*bands]&0xffff))) ((short[])pixels)[i*bands+c+offset*bands]=(short)65535;
					else ((short[])pixels)[i*bands+c+offset*bands]+=svalue;
				} else if(doint) {
					IJ.log("addPixels RGB add pixels not implemented");
				} else {
					if(fvalue>(1f-(((float[])pixels)[i*bands+c+offset*bands]))) ((float[])pixels)[i*bands+c+offset*bands]=1f;
					else ((float[])pixels)[i*bands+c+offset*bands]+=fvalue;
				}
			}else if(type=="COPY_TRANSPARENT_ZERO") {
				if(dobyte) {if(bvalue!=0)((byte[])pixels)[i*bands+c+offset*bands]=bvalue;}
				else if(doshort) {if(svalue!=0)((short[])pixels)[i*bands+c+offset*bands]=svalue;}
				else if(doint){IJ.log("addPixels RGB copy transparent zero not implemented");}
				else {if(fvalue!=0)((float[])pixels)[i*bands+c+offset*bands]=fvalue;}
			}else if(type=="COPY") {
				if(dobyte)((byte[])pixels)[i*bands+c+offset*bands]=bvalue;
				else if(doshort)((short[])pixels)[i*bands+c+offset*bands]=svalue;
				else if(doint)((int[])pixels)[i+c+offset]=((int[])newpixels)[i];
				else ((float[])pixels)[i*bands+c+offset*bands]=fvalue;
			}else if(type=="MAX") {
				if(dobyte) {
					if((((byte[])newpixels)[i]&0xff)>(((byte[])pixels)[i*bands+c+offset*bands]&0xff))((byte[])pixels)[i*bands+c+offset*bands]=bvalue;
				}else if(doshort) {
					if((((short[])newpixels)[i]&0xffff)>(((short[])pixels)[i*bands+c+offset*bands]&0xffff))((short[])pixels)[i*bands+c+offset*bands]=svalue;
				}else if(doint) {
					IJ.log("RGB addPixels MAX not implemented");
				}else {
					if((((float[])newpixels)[i])>(((float[])pixels)[i*bands+c+offset*bands]))((float[])pixels)[i*bands+c+offset*bands]=fvalue;
				}
			}
		}
	}
	
	private void drawGraphics(GL2ES2 gl2es2, float z, int texture) {
		float yrat=(float)srcRect.height/srcRect.width;
		FloatBuffer vb=Buffers.newDirectFloatBuffer(new float[] {
				-1,	-yrat,	z, 	0,1,0,
				1,	-yrat,	z, 	1,1,0,
				1,	yrat,	z, 	1,0,0,
				-1,	yrat,	z,	0,0,0
		});
		drawGraphics(gl2es2, vb, texture);
	}
	
	private void drawGraphics(GL2ES2 gl2es2, FloatBuffer vb, int texture) {

		gl2es2.glEnable(GL2ES2.GL_TEXTURE_3D);
		gl2es2.glBindTexture(GL2ES2.GL_TEXTURE_3D, texture);
		gl2es2.glTexParameteri(GL2ES2.GL_TEXTURE_3D, GL2ES2.GL_TEXTURE_MAG_FILTER, GL2ES2.GL_NEAREST);
		
		GL2 gl2=gl2es2.getGL2();
		drawTexGL6f(gl2, vb, GL2.GL_QUADS);
		if(Prefs.interpolateScaledImages)gl2es2.glTexParameteri(GL2ES2.GL_TEXTURE_3D, GL2ES2.GL_TEXTURE_MAG_FILTER,GL2ES2.GL_LINEAR);
		gl2es2.glBindTexture(GL2ES2.GL_TEXTURE_3D, 0);
		gl2es2.glDisable(GL2ES2.GL_TEXTURE_3D);
	}
	
	private void drawTexGL6f(GL2 gl2, FloatBuffer vb, int toDraw) {
		vb.rewind();
		gl2.glEnableClientState(GL2.GL_VERTEX_ARRAY);
		gl2.glVertexPointer(3, GL2.GL_FLOAT, 6*Buffers.SIZEOF_FLOAT, vb);
		gl2.glEnableClientState(GL2.GL_TEXTURE_COORD_ARRAY);
		vb.position(3);
		gl2.glTexCoordPointer(3, GL2.GL_FLOAT, 6*Buffers.SIZEOF_FLOAT,vb);
		gl2.glDrawArrays(toDraw, 0, vb.limit()/6);
		vb.rewind();
	}

	//Based on jogamp forum user Moa's code, http://forum.jogamp.org/GL-RGBA32F-with-glTexImage2D-td4035766.html
	protected void createRgbaTexture(GL2ES2 gl, int glTextureHandle, Buffer buffer, int width, int height, int depth) { 

		int internalFormat=GL.GL_RGBA32F;
		int pixelType=GL.GL_FLOAT;
		if(buffer instanceof ShortBuffer) {
			internalFormat=GL4.GL_RGBA16;
			pixelType=GL4.GL_UNSIGNED_SHORT;
		}else if(buffer instanceof ByteBuffer) {
			internalFormat=GL.GL_RGBA8;
			pixelType=GL.GL_UNSIGNED_BYTE;
		}else if(buffer instanceof IntBuffer) {
			//if pixelType=PixelType.RGB10_A2_INT
			internalFormat=intinternalformat;
			pixelType=intpformat;
		}
		
		gl.glEnable(GL2ES2.GL_TEXTURE_3D);
		gl.glBindTexture(GL2ES2.GL_TEXTURE_3D, glTextureHandle); 
		//gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
		gl.glTexImage3D(GL2ES2.GL_TEXTURE_3D, 0, internalFormat, width, height, depth, 0, GL.GL_RGBA, pixelType, buffer); 
		//gl.glTexImage3D(GL.GL_TEXTURE_2D, mipmapLevel, internalFormat, width, height, depth, numBorderPixels, pixelFormat, pixelType, buffer); 
		
		int magtype=GL.GL_LINEAR;
		if(!Prefs.interpolateScaledImages)magtype=GL.GL_NEAREST;
		
		gl.glTexParameteri(GL2ES2.GL_TEXTURE_3D, GL.GL_TEXTURE_MAG_FILTER, magtype);
		gl.glTexParameteri(GL2ES2.GL_TEXTURE_3D, GL.GL_TEXTURE_MIN_FILTER, magtype);//GL.GL_NEAREST_MIPMAP_LINEAR
		gl.glTexParameteri(GL2ES2.GL_TEXTURE_3D, GL2ES2.GL_TEXTURE_WRAP_S, GL2ES2.GL_CLAMP_TO_BORDER);
		gl.glTexParameteri(GL2ES2.GL_TEXTURE_3D, GL2ES2.GL_TEXTURE_WRAP_T, GL2ES2.GL_CLAMP_TO_BORDER);
		gl.glTexParameteri(GL2ES2.GL_TEXTURE_3D, GL2ES2.GL_TEXTURE_WRAP_R, GL2ES2.GL_CLAMP_TO_BORDER);
		gl.glTexParameterfv(GL2ES2.GL_TEXTURE_3D, GL2ES2.GL_TEXTURE_BORDER_COLOR, new float[] {0f,0f,0f,0f},0);
		gl.glGenerateMipmap(GL2ES2.GL_TEXTURE_3D);
		gl.glDisable(GL2ES2.GL_TEXTURE_3D);
	} 
	
	private int updateRgbaPBO(GL2ES2 gl, int pboHandle, Buffer buffer) {
		
		return updateSubRgbaPBO(gl, pboHandle, buffer, 0, buffer.limit(), buffer.limit());
	}
	
	protected int updateSubRgbaPBO(GL2ES2 gl, int pboHandle, Buffer buffer, int offset, int length, int bsize) {
		int size=Buffers.SIZEOF_FLOAT;
		if(buffer instanceof ShortBuffer) {
			size=Buffers.SIZEOF_SHORT;
		}else if(buffer instanceof ByteBuffer) {
			size=Buffers.SIZEOF_BYTE;
		}else if(buffer instanceof IntBuffer) {
			size=Buffers.SIZEOF_INT;
		}
		
		boolean isNew=false;
		if(pboHandle==0) {
			pboHandle=genPBO(gl);
			isNew=true;
		}
		gl.glBindBuffer(GL2.GL_PIXEL_UNPACK_BUFFER, pboHandle); 
		if(!isNew){
			int[] pbosize=new int[1];
			gl.glGetBufferParameteriv(GL2.GL_PIXEL_UNPACK_BUFFER, GL2.GL_BUFFER_SIZE, pbosize, 0);
			//IJ.log("pbosize: "+pbosize[0]+" des size:"+bsize*size);
			if(pbosize[0]!=bsize*size) {
				isNew=true;
			}
		}
		if(isNew)gl.glBufferData(GL2.GL_PIXEL_UNPACK_BUFFER, bsize*size, null, GL2.GL_DYNAMIC_DRAW);
		buffer.position(offset);
		gl.glBufferSubData(GL2.GL_PIXEL_UNPACK_BUFFER, (long)offset*size, (long)length*size, buffer);
		gl.glBindBuffer(GL2.GL_PIXEL_UNPACK_BUFFER,0);
		return pboHandle;
	}
	
	protected int genPBO(GL2ES2 gl) {
		int[] pboHandles=new int[1];
		gl.glGenBuffers(1, pboHandles,0);
		return pboHandles[0];
	}
	
	protected void loadTexFromPBO(GL2ES2 gl, int pboHandle, int texHandle, int width, int height, int depth, int offsetSlice, PixelType type) {
		
		int internalFormat=GL.GL_RGBA32F;
		int pixelType=GL.GL_FLOAT;
		int size=Buffers.SIZEOF_FLOAT;
		int components=4;
		
		if(type==PixelType.SHORT) {
			internalFormat=GL4.GL_RGBA16;
			pixelType=GL4.GL_UNSIGNED_SHORT;
			size=Buffers.SIZEOF_SHORT;
		}else if(type==PixelType.BYTE) {
			internalFormat=GL.GL_RGBA8;
			pixelType=GL.GL_UNSIGNED_BYTE;
			size=Buffers.SIZEOF_BYTE;
		}else if(type==PixelType.INT_RGB10A2) {
			internalFormat=intinternalformat;
			pixelType=intpformat;
			size=Buffers.SIZEOF_INT;
			components=1;
		}
		
		gl.glEnable(GL2ES2.GL_TEXTURE_3D);
		gl.glBindBuffer(GL2.GL_PIXEL_UNPACK_BUFFER, pboHandle);
		gl.glBindTexture(GL2ES2.GL_TEXTURE_3D, texHandle); 
		gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
		gl.glTexParameteri(GL2ES2.GL_TEXTURE_3D, GL2.GL_TEXTURE_BASE_LEVEL, 0);
		gl.glTexParameteri(GL2ES2.GL_TEXTURE_3D, GL2.GL_TEXTURE_MAX_LEVEL, 0);
		gl.glTexImage3D(GL2ES2.GL_TEXTURE_3D, 0, internalFormat, width, height, depth, 0, GL.GL_RGBA, pixelType, offsetSlice*components*width*height*size);
		int magtype=GL.GL_LINEAR;
		if(!Prefs.interpolateScaledImages)magtype=GL.GL_NEAREST;
		gl.glTexParameteri(GL2ES2.GL_TEXTURE_3D, GL.GL_TEXTURE_MAG_FILTER, magtype); 
		gl.glTexParameteri(GL2ES2.GL_TEXTURE_3D, GL.GL_TEXTURE_MIN_FILTER, magtype);//GL.GL_NEAREST_MIPMAP_LINEAR 
		gl.glTexParameteri(GL2ES2.GL_TEXTURE_3D, GL2ES2.GL_TEXTURE_WRAP_S, GL2ES2.GL_CLAMP_TO_BORDER);
		gl.glTexParameteri(GL2ES2.GL_TEXTURE_3D, GL2ES2.GL_TEXTURE_WRAP_T, GL2ES2.GL_CLAMP_TO_BORDER);
		gl.glTexParameteri(GL2ES2.GL_TEXTURE_3D, GL2ES2.GL_TEXTURE_WRAP_R, GL2ES2.GL_CLAMP_TO_BORDER);
		gl.glTexParameterfv(GL2ES2.GL_TEXTURE_3D, GL2ES2.GL_TEXTURE_BORDER_COLOR, new float[] {0f,0f,0f,0f},0);
		//gl.glGenerateMipmap(GL2ES2.GL_TEXTURE_3D);
		gl.glDisable(GL2ES2.GL_TEXTURE_3D);
		gl.glBindBuffer(GL2.GL_PIXEL_UNPACK_BUFFER, 0);
		//System.out.println("LPBO 3");
	}

	public void set3d(boolean newboo) {
		myImageUpdated=true;
		go3d=newboo;
		if(!go3d)stereoType=StereoType.OFF;
		//deletePBOs=true;
		if(go3d) {
			//IJ.setTool("hand");
			icc.addKeyListener(this);
			if(isMirror) {
				//icc.removeMouseListener(imp.getCanvas());
				//icc.removeMouseMotionListener(imp.getCanvas());
				//icc.addMouseListener(this);
				//icc.addMouseMotionListener(this);
			}
		}else {
			icc.removeKeyListener(this);
		}
		setStereo(stereoType);
		repaint();
	}
	
	public void toggle3d() {
		set3d(!go3d);
	}
	
	public void setStereo(StereoType stereoTypeChoice) {
		stereoType=stereoTypeChoice;
		if(stereoType.ordinal()>0 && !go3d) {set3d(true); return;}//return because set3d calls setStereo
		if(stereoType==StereoType.ANAGLYPH) {
			imp.setDisplayMode(IJ.GRAYSCALE);
		}else {
			imp.setDisplayMode(IJ.COMPOSITE);
		}
		myImageUpdated=true;
		repaint();
	}
	
	public void setMirror() {
		isMirror=true;
		addMirrorListeners();
		mirror=new Frame("JOGL-DC3D Mirror of "+imp.getTitle());
		//mirror=new ImageWindow("DC3D Mirror of "+imp.getTitle());
		mirror.add(icc);
		mirror.addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				revert();
			}
		});
		mirror.addMouseWheelListener(new MouseAdapter() {
			@Override
			public void mouseWheelMoved(MouseWheelEvent e) {
				imp.getCanvas().setCursor(e.getX(), e.getY(), offScreenX(e.getX()), offScreenY(e.getY()));
				imp.getWindow().mouseWheelMoved(e);
				updateMirror(); repaint();
			}
		});
		mirror.setVisible(true);
		updateMirror();
	}

	@Override
	public void windowOpened(WindowEvent e) {}
	@Override
	public void windowClosing(WindowEvent e) {mirror.dispose();mirror=null;}
	@Override
	public void windowClosed(WindowEvent e) {}
	@Override
	public void windowIconified(WindowEvent e) {}
	@Override
	public void windowDeiconified(WindowEvent e) {}
	@Override
	public void windowActivated(WindowEvent e) {}
	@Override
	public void windowDeactivated(WindowEvent e) {}
	
	private void addMirrorListeners() {
		ImageCanvas originalic=imp.getCanvas();
		removeMirrorListeners();
		originalic.addMouseListener(new MouseListener() {
			public void mouseClicked(MouseEvent e) {}
			@Override
			public void mousePressed(MouseEvent e) { repaint();}
			@Override
			public void mouseReleased(MouseEvent e) { repaint(); }
			@Override
			public void mouseEntered(MouseEvent e) {}
			@Override
			public void mouseExited(MouseEvent e) {}
		});
		originalic.addMouseMotionListener(new MouseMotionListener() {
			@Override
			public void mouseDragged(MouseEvent e) {repaint();}
			@Override
			public void mouseMoved(MouseEvent e) {}
		});
		originalic.addKeyListener(new KeyListener() {
			@Override
			public void keyTyped(KeyEvent e) {}
			@Override
			public void keyPressed(KeyEvent e) {}
			@Override
			public void keyReleased(KeyEvent e) {repaint();}
		});
		imp.getWindow().addWindowListener(this);
	}
	
	private void removeMirrorListeners() {
		ImageCanvas oic=imp.getCanvas();
		for(MouseListener ml:oic.getMouseListeners()) {if(ml.getClass().getName().startsWith("ajs.joglcanvas"))oic.removeMouseListener(ml);}
		for(MouseMotionListener mml:oic.getMouseMotionListeners()) {if(mml.getClass().getName().startsWith("ajs.joglcanvas"))oic.removeMouseMotionListener(mml);}
		for(KeyListener kl:oic.getKeyListeners()) {if(kl.getClass().getName().startsWith("ajs.joglcanvas"))oic.removeKeyListener(kl);}
		for(WindowListener wl:imp.getWindow().getWindowListeners()) {if(wl.getClass().getName().startsWith("ajs.joglcanvas"))imp.getWindow().removeWindowListener(wl);}
	}
	
	public void revert() {
		showUpdateButton(false);
		if(isMirror){
			removeMirrorListeners();
			mirror.dispose();
			mirror=null;
		}else {
			int mode=imp.getDisplayMode();
			imp.getWindow().dispose();
			new StackWindow(imp);
			imp.getCanvas().setMagnification(magnification);
			imp.getCanvas().setSourceRect(srcRect);
			imp.setDisplayMode(mode);
		}
	}
	
	public void setRenderFunction(String function) {
		if(function.equals("MAX") || function.equals("ALPHA"))renderFunction=function;
		repaint();
	}


	//Overridden ImageCanvas Methods
		
	//adapted from drawZoomIndicator() in ImageCanvas
	void drawMyZoomIndicator(GLAutoDrawable drawable) {
		if(myHZI) return;
		GL2 gl2=drawable.getGL().getGL2();
		float x1 = 10;
		float y1 = 10;
		double aspectRatio = (double)imageHeight/imageWidth;
		float w1 = 64;
		if (aspectRatio>1.0)
			w1 = (float)(w1/aspectRatio);
		float h1 = (float)(w1*aspectRatio);
		if (w1<4f) w1 = 4f;
		if (h1<4f) h1 = 4f;
		float w2 = (float)(w1*((double)srcRect.width/imageWidth));
		float h2 = (float)(h1*((double)srcRect.height/imageHeight));
		if (w2<1f) w2 = 1f;
		if (h2<1f) h2 = 1f;
		float x2 = (float)(w1*((double)srcRect.x/imageWidth));
		float y2 = (float)(h1*((double)srcRect.y/imageHeight));
		float w=(float)drawable.getSurfaceWidth();
		float h=(float)drawable.getSurfaceHeight();
		float yrat=(float)srcRect.height/srcRect.width;
		x1=x1/w*2f-1f; y1=((h-y1)/h*2f-1f)*yrat;
		w1=w1/w*2f; h1=h1/h*2f*yrat;
		x2=x2/w*2f; y2=y2/h*2f*yrat;
		w2=w2/w*2f; h2=h2/h*2f*yrat;

		gl2.glLineWidth(1f);
		gl2.glColor3f((float)128/255, (float)128/255, 1f);
		gl2.glBegin(GL2.GL_LINE_LOOP);
		gl2.glVertex3f(x1, y1,0f);
		gl2.glVertex3f(x1+w1, y1,0f);
		gl2.glVertex3f(x1+w1, y1-h1,0f);
		gl2.glVertex3f(x1, y1-h1,0f);
		gl2.glEnd();

		gl2.glBegin(GL2.GL_LINE_LOOP);
		gl2.glVertex3f(x1+x2, y1-y2,0f);
		gl2.glVertex3f(x1+x2+w2, y1-y2,0f);
		gl2.glVertex3f(x1+x2+w2, y1-y2-h2,0f);
		gl2.glVertex3f(x1+x2, y1-y2-h2,0f);
		gl2.glEnd();
		
		gl2.glColor4f(1f, 1f, 1f, 1f);
		
	}
	
	private void drawRoiGL(GLAutoDrawable drawable, Roi roi, float z, boolean drawHandles, Color anacolor) {
		if(roi==null)return;
		if(rgldu==null)rgldu=new RoiGLDrawUtility(imp);
		rgldu.drawRoiGL(drawable, roi, z, drawHandles, anacolor);
	}

	//Create blank image for original other graphics (ROI, overlay) to draw over.
	@Override
	public Image createImage(int width, int height) {
		return new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
	}

	/*Called in super()*/
	@Override
	public void setSize(int width, int height) {
		if(icc!=null) {
			icc.setMinimumSize(new Dimension(10,10));
			icc.setSize(width, height);
			icc.setPreferredSize(new Dimension(width,height));
			//icc.repaint();
			if(isMirror) {icc.getParent().repaint();}
		}
		else super.setSize(width, height);
		dstWidth = width;
		dstHeight = height;
	}

	@Override
	public void setSize(Dimension newsize) {
		setSize(newsize.width,newsize.height);
	}

	@Override
	public void repaint() {
		if(icc!=null) {
			if(isMirror)updateMirror();
			icc.repaint();
		}
		else setPaintPending(false);
		//else super.repaint();
	}

	@Override
	public void repaint(int x,int y,int width,int height) {
		if(icc!=null)icc.repaint(x,y,width,height);
		else super.repaint(x,y,width,height);
	}

	@Override
	public Point getLocation() {
		if(icc!=null)return icc.getLocation();
		else return super.getLocation();
	}

	@Override
	public Rectangle getBounds() {
		if(icc!=null)return icc.getBounds();
		else return super.getBounds();
	}

	@Override
	public void setCursor(Cursor cursor) {
		if(icc!=null)icc.setCursor(cursor);
		else super.setCursor(cursor);
	}

	/* called in super() disable so I don't have to remove them*/
	@Override
	public void addMouseListener(MouseListener object) {
		if(icc!=null)icc.addMouseListener(object);
		//else super.addMouseListener(object);
	}
	@Override
	public void addMouseMotionListener(MouseMotionListener object) {
		if(icc!=null)icc.addMouseMotionListener(object);
		//else super.addMouseMotionListener(object);
	}
	@Override
	public void addKeyListener(KeyListener object) {
		if(icc!=null)icc.addKeyListener(object);
		//else super.addKeyListener(object);
	}
	
	/*just in case*/
	@Override
	public void removeMouseListener(MouseListener object) {
		if(icc!=null)icc.removeMouseListener(object);
		//else super.removeMouseListener(object);
	}
	@Override
	public void removeMouseMotionListener(MouseMotionListener object) {
		if(icc!=null)icc.removeMouseMotionListener(object);
		//else super.removeMouseMotionListener(object);
	}
	@Override
	public void removeKeyListener(KeyListener object) {
		if(icc!=null)icc.removeKeyListener(object);
		//else super.removeKeyListener(object);
	}
	

	@Override
	public void requestFocus() {
		if(icc!=null)icc.requestFocus();
		else super.requestFocus();
	}
	@Override
	public Container getParent() {
		if(icc!=null)return icc.getParent();
		else return super.getParent();
	}
	@Override
	public Graphics getGraphics() {
		if(icc!=null)return icc.getGraphics();
		else return super.getGraphics();
	}
	@Override
	public Dimension getSize() {
		if(icc!=null)return icc.getSize();
		else return super.getSize();
	}
	@Override
	public Dimension getPreferredSize() {
		if(icc!=null)return icc.getPreferredSize();
		else return super.getPreferredSize();
	}

	@Override
	public boolean hideZoomIndicator(boolean hide) {
		boolean hidden=myHZI;
		if (!(srcRect.width<imageWidth||srcRect.height<imageHeight))
			return hidden;
		myHZI=hide;
		repaint();
		return hidden;
	}

	@Override
	public void add(PopupMenu popup) {
		if(icc!=null)icc.add(popup);
		else super.add(popup);
	}
	
	/** Adapted from ImageCanvas, but shows JOGLCanvas popupmenu*/
	@Override
	protected void handlePopupMenu(MouseEvent e) {
		if(icc==null)super.handlePopupMenu(e);
		else {
			if (disablePopupMenu) return;
			if (IJ.debugMode) IJ.log("show popup: " + (e.isPopupTrigger()?"true":"false"));
			int x = e.getX();
			int y = e.getY();
			Roi roi = imp.getRoi();
			if (roi!=null && roi.getState()==Roi.CONSTRUCTING) {
				return;
			}

			if (dcpopup!=null) {
				icc.add(dcpopup);
				if (IJ.isMacOSX()) IJ.wait(10);
				String lbl=mi3d.getLabel();
				int a=0;
				for(int i=0;i<imageFBs.length;i++)if(imageFBs!=null)a++;
				if(a<imageFBs.length)mi3d.setLabel(lbl+" PBOs:"+a+"/"+imageFBs.length);
				if(dpimag>1.0 && !IJ.isMacOSX())dcpopup.show(icc, (int)(x*dpimag), (int)(y*dpimag));
				else dcpopup.show(icc, x, y);
				mi3d.setLabel(lbl);
			}
		}
	}

	/** Disable/enable popup menu. */
	@Override
	public void disablePopupMenu(boolean status) {
		disablePopupMenu = status;
	}
	
	public void createPopupMenu() {
		if(dcpopup==null) {
			dcpopup=new PopupMenu("JOGLCanvas Options");
			PopupMenu popup = Menus.getPopupMenu();
			popup.setLabel("ImageJ");
			
			MenuItem mi;
			
			Menu threeDmenu=new Menu("3d Options");
			if(imp.getNSlices()==1) threeDmenu.setEnabled(false);
			String label="Turn 3d on";
			if(go3d)label="Turn 3d off";
			mi=new MenuItem(label);
			mi.setActionCommand("3d");
			if(imp.getNSlices()==1) mi.setEnabled(false);
			mi.addActionListener(this);
			threeDmenu.add(mi);
			mi3d=mi;
			
			Menu menu=new Menu("Rendering");
			addCMI(menu,"MAX",renderFunction.equals("MAX"));
			addCMI(menu,"ALPHA",renderFunction.equals("ALPHA"));
			threeDmenu.add(menu);
			
			menu=new Menu("Set Undersampling");
			addCMI(menu,"None",undersample==1);
			addCMI(menu,"2",undersample==2);
			addCMI(menu,"4",undersample==4);
			addCMI(menu,"6",undersample==6);
			threeDmenu.add(menu);
			
			menu=new Menu("3D Pixel Type");
			for(int i=0;i<2;i++) addCMI(menu,pixelTypeStrings[i],pixelType3d==PixelType.values()[i]);
			threeDmenu.add(menu);
			
			mi=new MenuItem("Start 3d Background Load");
			mi.setActionCommand("bgload");
			mi.addActionListener(this);
			threeDmenu.add(mi);
			mi=new MenuItem("Update 3d Image");
			mi.setActionCommand("update");
			mi.addActionListener(this);
			threeDmenu.add(mi);
			mi=new MenuItem("Reset 3d view");
			mi.setActionCommand("reset3d");
			mi.addActionListener(this);
			threeDmenu.add(mi);
			
			menu=new Menu("Stereoscopic 3d");
			for(int i=0;i<stereoTypeStrings.length;i++) {
				mi=new MenuItem(stereoTypeStrings[i]);
				mi.addActionListener(this);
				menu.add(mi);
			}
			threeDmenu.add(menu);
			mi=new MenuItem("Save image or movie");
			mi.setActionCommand("Recorder");
			mi.addActionListener(this);
			threeDmenu.add(mi);
			
			dcpopup.add(threeDmenu);
			
			menu=new Menu("Normal Pixel Type");
			for(int i=0;i<pixelTypeStrings.length;i++) addCMI(menu,pixelTypeStrings[i],pixelType==PixelType.values()[i]);
			dcpopup.add(menu);
			
			mi=new MenuItem("Switch use PBO for Slices");
			mi.setActionCommand("usePBOforSlices");
			mi.addActionListener(this);
			dcpopup.add(mi);
			
			
			mi=new MenuItem("Revert to Normal Window");
			mi.setActionCommand("revert");
			mi.addActionListener(this);
			dcpopup.add(mi);
			mi=new MenuItem("JOGL Canvas Preferences");
			mi.setActionCommand("prefs");
			mi.addActionListener(this);
			dcpopup.add(mi);
			dcpopup.add(popup);
		}
	}
	
	private void addCMI(Menu menu, String label, boolean state) {
		CheckboxMenuItem cmi=new CheckboxMenuItem(label);
		cmi.addItemListener(this);
		cmi.setState(state);
		menu.add(cmi);
	}

	public void actionPerformed(ActionEvent e) {
		String cmd=e.getActionCommand();
		if(cmd.equals("3d"))toggle3d();
		else if(cmd.equals("update")) {
			if(go3d) {
				myImageUpdated=true; repaint();
			}else {
				updateBuffers(imp.getT(),true);
			}
		}
		else if(cmd.equals("revert")){revert();}
		else if(cmd.equals("reset3d")){resetAngles();}
		else if(cmd.equals("prefs")){JCP.preferences();}
		else if(cmd.equals("bgload")) {if(go3d) updateBuffers(imp.getT(),true);  else updateBuffersBackground(null);}
		else if(cmd.equals("Recorder")){
			IJ.run("JOGL Canvas Recorder",imp.getTitle());
		}else if(cmd.equals("usePBOforSlices")) {
			if(usePBOforSlices) {
				IJ.log("PBOslices off");
				usePBOforSlices=false;
			}else {
				IJ.log("PBOslices on");
				usePBOforSlices=true;
			}
		}else if(((Menu)((MenuItem)e.getSource()).getParent()).getLabel().equals("Stereoscopic 3d")) {
			int stTypeChoice=0;
			for(int i=0;i<stereoTypeStrings.length;i++) {
				if(cmd.equals(stereoTypeStrings[i]))stTypeChoice=i;
			}
			StereoType stTc=StereoType.values()[stTypeChoice];
			setStereo((stereoType==stTc)?StereoType.OFF:stTc);
		}
		mi3d.setLabel(go3d?"Turn 3d off":"Turn 3d on");
	}
	
	public void itemStateChanged(ItemEvent e) {
		String whichmenu=((Menu)((MenuItem)e.getSource()).getParent()).getLabel();
		String cmd=(String)e.getItem();
		if(whichmenu.equals("Rendering")){
			if(e.getStateChange()==ItemEvent.SELECTED) setRenderFunction(cmd);
			checkRenderPopup(whichmenu, cmd);
		}else if(whichmenu.equals("Set Undersampling")) {
			if(e.getStateChange()==ItemEvent.SELECTED) setUnderSampling(cmd.equals("None")?1:Integer.parseInt(cmd));
			checkRenderPopup(whichmenu, cmd);
		}else if(whichmenu.equals("3D Pixel Type") || whichmenu.equals("Normal Pixel Type")) {
			if(e.getStateChange()==ItemEvent.SELECTED) {
				int temp=0;
				for(int i=0;i<pixelTypeStrings.length;i++) if(cmd.equals(pixelTypeStrings[i])) temp=i;
				setPixelType(PixelType.values()[temp],whichmenu.equals("3D Pixel Type"));
				checkRenderPopup(whichmenu, pixelTypeStrings[getCurrentPixelType(whichmenu.equals("3D Pixel Type"))]);
			}
		}
	}
	
	void checkRenderPopup(String whichmenu, String check) {
		boolean okbreak=false;
		for(int i=0;i<dcpopup.getItemCount();i++) {
			MenuItem topitem=dcpopup.getItem(i);
			if(topitem instanceof Menu && topitem.getLabel().equals("3d Options")) {
				for(int k=0;k<((Menu)topitem).getItemCount();k++) {
					MenuItem item=((Menu)topitem).getItem(k);
					if(item instanceof Menu && item.getLabel().equals(whichmenu)) {
						okbreak=true;
						for(int j=0;j<((Menu)item).getItemCount();j++) {
							CheckboxMenuItem cmi=(CheckboxMenuItem)((Menu)item).getItem(j);
							cmi.setState(cmi.getLabel().equals(check));
						}
						break;
					}
				}
				if(okbreak)break;
			}
			if(topitem instanceof Menu && topitem.getLabel().equals(whichmenu)) {
				for(int j=0;j<((Menu)topitem).getItemCount();j++) {
					CheckboxMenuItem cmi=(CheckboxMenuItem)((Menu)topitem).getItem(j);
					cmi.setState(cmi.getLabel().equals(check));
				}
				break;
			}
		}
	}

	public void imageOpened(ImagePlus imp) {}

	public void imageClosed(ImagePlus imp) {}

	public void imageUpdated(ImagePlus uimp) {
		if(imp.equals(uimp)) {
			if(isMirror) {
				repaint();
			}
			if(!go3d)myImageUpdated=true;
			else {
				if((lastPosition[0]==imp.getC()||imp.getCompositeMode()!=IJ.COMPOSITE) && lastPosition[1]==imp.getSlice() && lastPosition[2]==imp.getFrame()) {
					showUpdateButton(true);
				}
			}
		}
	}
	
	private void updateMirror() {
		if(mirrorMagUnlock)return;
		srcRect=imp.getCanvas().getSrcRect();
		magnification=imp.getCanvas().getMagnification();
		Dimension s=imp.getCanvas().getSize();
		Insets ins=icc.getParent().getInsets();
		setSize(s);
		icc.getParent().setSize(s.width+ins.left+ins.right,s.height+ins.top+ins.bottom);
	}
	
	private void updateLastPosition() {
		lastPosition[0]=imp.getC(); lastPosition[1]=imp.getZ(); lastPosition[2]=imp.getT();
	}
	
	public void setBIScreenGrabber(BIScreenGrabber sg) {
		myscreengrabber=sg;
	}

	@Override
	public void keyPressed(KeyEvent arg0) {}
	@Override
	public void keyReleased(KeyEvent arg0) {}
	@Override
	public void keyTyped(KeyEvent arg0) {
		if(arg0.getKeyChar()=='u') {
			myImageUpdated=true;
			repaint();
		}else {
			if(arg0.getKeyChar()=='='||arg0.getKeyChar()=='-') {
				Point loc = getCursorLoc();
				if (!cursorOverImage()) {
					loc.x = srcRect.x + srcRect.width/2;
					loc.y = srcRect.y + srcRect.height/2;
				}
				int x = screenX(loc.x);
				int y = screenY(loc.y);
				ImageCanvas ic=isMirror?imp.getCanvas():this;
				if(arg0.getKeyChar()=='=')ic.zoomIn(x,y);
				else ic.zoomOut(x,y);
			}
			if(isMirror) {updateMirror(); repaint();}
		}
	}
	
	//from https://stackoverflow.com/questions/2972512/how-to-detect-right-click-event-for-mac-os
	private static boolean isRightClick(MouseEvent e) {
	    return (e.getButton()==MouseEvent.BUTTON3 ||
	            (System.getProperty("os.name").contains("Mac OS X") &&
	                    (e.getModifiers() & MouseEvent.BUTTON1_MASK) != 0 &&
	                    (e.getModifiers() & MouseEvent.CTRL_MASK) != 0));
	}
	
	private boolean shouldKeep(MouseEvent e) {
		return (!isRightClick(e) && isMirror) || (!isRightClick(e) && (go3d && ((IJ.getToolName()=="hand" && !IJ.spaceBarDown()) || IJ.controlKeyDown())));
	}
	
	@Override
	public void mousePressed(MouseEvent e) {
		if(shouldKeep(e)) {
			sx = e.getX();
			sy = e.getY();
			if(IJ.spaceBarDown()) {
				setupScroll(offScreenX(sx),offScreenX(sy));
			}
		}else super.mousePressed(e);
		if(isMirror) {updateMirror(); repaint();}
	}
	
	@Override
	public void mouseDragged(MouseEvent e) {
		if(shouldKeep(e)) {
			if(IJ.spaceBarDown()&&isMirror) {
				scroll(e.getX(),e.getY());
				imp.getCanvas().setSourceRect(srcRect);
			}else {
				if(IJ.altKeyDown() || e.getButton()==MouseEvent.BUTTON2) {
					dz+=(float)(e.getY()-sy)/(float)srcRect.height*90f;
					sy=e.getY();
				}else {
					dx+=(float)(e.getX()-sx)/(float)srcRect.width*90f;
					sx=e.getX();
					dy+=(float)(e.getY()-sy)/(float)srcRect.height*90f;
					sy=e.getY();
				}
				if(dz<0)dz+=360; if(dz>360)dz-=360;
				if(dx<0)dx+=360; if(dx>360)dx-=360;
				if(dy<0)dy+=360; if(dy>360)dy-=360;
			}
			if(isMirror)updateMirror();
			icc.repaint();
		}else super.mouseDragged(e);
	}
	
	@Override
	public void mouseReleased(MouseEvent e) {
		if(shouldKeep(e)) {
			if((IJ.shiftKeyDown())) {
				resetAngles();
			}
		}else super.mouseReleased(e);
	}
	
	public void resetAngles() {
		dx=0f; dy=0f; dz=0f;
		icc.repaint();
	}
	
	@Override
	public void mouseMoved(MouseEvent e) {
		if(!shouldKeep(e) || isMirror)super.mouseMoved(e);
	}
	
	@Override
	public void mouseEntered(MouseEvent e) {
		if(!shouldKeep(e))super.mouseEntered(e);
	}
	
	@Override
	public void mouseExited(MouseEvent e) {
		if(!shouldKeep(e))super.mouseExited(e);
	}
	
	@Override
	public void mouseClicked(MouseEvent e) {
		if(!shouldKeep(e))super.mouseClicked(e);
	}
	
	public float[] getEulerAngles() {
		return new float[] {dx,dy,dz};
	}
	
	public void showUpdateButton(boolean show) {
		if(imp==null || imp.getWindow()==null || !(imp.getWindow() instanceof StackWindow))return;
		if(needImageUpdate && show)return;
		needImageUpdate=show;
		StackWindow stwin=(StackWindow) imp.getWindow();
		ScrollbarWithLabel scr=null;
		Component[] comps=stwin.getComponents();
		for(int i=0;i<comps.length;i++) {
			if(comps[i] instanceof ij.gui.ScrollbarWithLabel) {
				scr=(ScrollbarWithLabel)comps[i];
			}
		}
		if(scr!=null) {
			comps=scr.getComponents();
			for(int i=0;i<comps.length;i++) {
				if(comps[i] instanceof Button) {
					String label=((Button)comps[i]).getLabel();
					if(label.equals("Update")||label.equals("Updating...")) {
						scr.remove(comps[i]);
					}
				}
			}
			
			if(show) {
				Button updateButton= new Button("Update");
				updateButton.addActionListener(new ActionListener() {
					public void actionPerformed(ActionEvent e) {
						updateButton.setLabel("Updating...");
						updateButton.setEnabled(false);
						updateButton.repaint();
						myImageUpdated=true; repaint();
						if(isMirror && mirror==null)showUpdateButton(false);
					}
				});
				updateButton.setFocusable(false);
				scr.add(updateButton,BorderLayout.EAST);
			}
			stwin.pack();
		}
	}

}
