package ajs.joglcanvas;

import ij.CompositeImage;
import ij.IJ;
import ij.ImageListener;
import ij.ImagePlus;
import ij.Menus;
import ij.Prefs;
import ij.gui.ImageCanvas;
import ij.gui.Roi;
import ij.gui.ScrollbarWithLabel;
import ij.gui.StackWindow;
import ij.measure.Calibration;
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
import java.nio.ShortBuffer;
import java.nio.ByteBuffer;

import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL3;
import static com.jogamp.opengl.GL3.*;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.util.texture.awt.AWTTextureIO;

import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLException;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.util.awt.AWTGLReadBufferUtil;
import com.jogamp.opengl.math.FloatUtil;
import com.jogamp.opengl.util.GLBuffers;

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
	private Rectangle prevSrcRect=null;
	private boolean[] ltr=null;

	protected boolean go3d=false;
	public String renderFunction=JCP.renderFunction;
	public boolean usePBOforSlices=JCP.usePBOforSlices;
	protected int sx,sy;
	protected float dx=0f,dy=0f,dz=0f;
	
	private PopupMenu dcpopup=null;
	private MenuItem mi3d=null;
	protected boolean myHZI=false;
	
	private StackBuffer sb;

	private GL3 gl;
	private JCGLObjects glos;
	private FloatBuffer zoomIndVerts=null;
	private int lim;
	private int undersample=JCP.undersample;
	enum StereoType{OFF, CARDBOARD, ANAGLYPH, QUADBUFFER};
	private static String[] stereoTypeStrings=new String[] {"Stereo off", "Google Cardboard-SBS","Anaglyph (red-cyan)","OpenGL Quad Buffers"};
	private static final float CB_MAXSIZE=4f;
	private static final float CB_TRANSLATE=0.44f;
	private StereoType stereoType=StereoType.OFF;
	private boolean stereoUpdated=true,threeDupdated=true;
	private int[] stereoFramebuffers=new int[2];

	enum PixelType{BYTE, SHORT, FLOAT, INT_RGB10A2, INT_RGBA8};
	private static final String[] pixelTypeStrings=new String[] {"4 bytes (8bpc, 32bit)","4 shorts (16bpc 64bit)","4 floats (32bpc 128bit)","1 int RGB10A2 (10bpc, 32bit)","1 int RGBA8 (8bpc, 32bit)"};
	protected PixelType pixelType3d=PixelType.BYTE;
	private int COMPS=0;
	
	private BIScreenGrabber myscreengrabber=null;
	private AWTGLReadBufferUtil ss=null;
	private RoiGLDrawUtility rgldu=null;
	//private long starttime=0;

	public JOGLImageCanvas(ImagePlus imp, boolean mirror) {
		super(imp);
		COMPS=1;//imp.getBitDepth()==24?3:imp.getNChannels();
		if(!mirror) {setOverlay(imp.getCanvas().getOverlay());}
		updateLastPosition();
		prevSrcRect=new Rectangle(0, 0, 0, 0);
		if(JCP.glCapabilities==null && !JCP.setGLCapabilities()) IJ.showMessage("error in GL Capabilities");
		createPopupMenu();
		sb=new StackBuffer(imp);
		icc=new GLCanvas(JCP.glCapabilities);
		float[] res=new float[] {1.0f,1.0f};
		icc.setSurfaceScale(res);
		//res=icc.getRequestedSurfaceScale(res);
		//IJ.log("GetRequestedSurfaceScale:"+res[0]+" "+res[1]);
		icc.addMouseListener(this);
		icc.addMouseMotionListener(this);
		icc.addKeyListener(ij);
		icc.setFocusTraversalKeysEnabled(false);
		icc.setSize(imageWidth, imageHeight);
		icc.setPreferredSize(new Dimension(imageWidth,imageHeight));
		icc.addGLEventListener(this);
		ImagePlus.addImageListener(this);
		if(mirror)setMirror();
		pixelType3d=getPixelType();
	}
	
	private void setGL(GLAutoDrawable drawable) {
		gl = drawable.getGL().getGL3();
	}

	//GLEventListener methods
	@Override
	public void init(GLAutoDrawable drawable) {
		GraphicsConfiguration gc=icc.getParent().getGraphicsConfiguration();
		AffineTransform t=gc.getDefaultTransform();
		dpimag=t.getScaleX();
		icc.setSize(dstWidth, dstHeight);
		double dpimag2=drawable.getSurfaceHeight()/dstHeight;
		if(dpimag>1.0)IJ.log("Dpimag: "+dpimag+" "+dpimag2);
		if(IJ.isMacOSX())icc.setLocation(4,47);
		setGL(drawable);
		JCP.version=drawable.getGL().glGetString(GL_VERSION);
		gl.glClearColor(0f, 0f, 0f, 0f);
		gl.glDisable(GL_DEPTH_TEST);
		gl.glDisable(GL_MULTISAMPLE);
		
		Calibration cal=imp.getCalibration();
		long zmaxsls=(long)((double)imp.getNSlices()*cal.pixelDepth/cal.pixelWidth);
		long maxsize=Math.max((long)imp.getWidth(), Math.max((long)imp.getHeight(), zmaxsls))*6;
		ShortBuffer elementBuffer=GLBuffers.newDirectShortBuffer((int)maxsize);
		for(int i=0; i<(maxsize/6);i++) {
			elementBuffer.put((short)(i*4+0)).put((short)(i*4+1)).put((short)(i*4+2));
			elementBuffer.put((short)(i*4+2)).put((short)(i*4+3)).put((short)(i*4+0));
		}
		elementBuffer.rewind();
		
		/*
		long vbms=(imageWidth*4L*6L + imageHeight*4L*6L + zmaxsls*4L*6L)*2;
		vertb=GLBuffers.newDirectByteBuffer((int)vbms);
		float yrat=imageHeight/imageWidth;
		float[] initVerts=new float[] {
				-1f, 	-yrat, 	0,		0, 1, 0.5f,
				 1f, 	-yrat, 	0,		1, 1, 0.5f,
				 1f, 	yrat, 	0,		1, 0, 0.5f,
				-1f, 	yrat, 	0,		0, 0, 0.5f
		};
		
		for(float z=zmaxsls-1;z>-0.5f;z-=1.0f) {
			for(int i=0;i<4;i++) {
				vertb.putFloat(initVerts[i*6]); vertb.putFloat(initVerts[i*6+1]); vertb.putFloat(((float)zmaxsls/2-z)/imageWidth); 
				vertb.putFloat(initVerts[i*6+3]); vertb.putFloat(initVerts[i*6+4]); vertb.putFloat((z+0.5f)/zmaxsls); 
			}
		}

		*/
		

		glos=new JCGLObjects(drawable);
		glos.newTexture("image", imp.getNChannels());
		glos.newBuffer(GL_ARRAY_BUFFER, "image", maxsize*4*Buffers.SIZEOF_FLOAT, null);
		glos.newBuffer(GL_ELEMENT_ARRAY_BUFFER, "image", maxsize*Buffers.SIZEOF_SHORT, elementBuffer);
		glos.newVao("image", 3, GL_FLOAT, 3, GL_FLOAT);

		glos.newTexture("roiGraphic");
		glos.newBuffer(GL_ARRAY_BUFFER, "roiGraphic");
		glos.newBuffer(GL_ELEMENT_ARRAY_BUFFER, "roiGraphic");
		glos.newVao("roiGraphic", 3, GL_FLOAT, 3, GL_FLOAT);
		
		glos.newBuffer(GL_UNIFORM_BUFFER, "global", 16*2 * Buffers.SIZEOF_FLOAT, null);
		glos.newBuffer(GL_UNIFORM_BUFFER, "model", 16 * Buffers.SIZEOF_FLOAT, null);
		glos.newBuffer(GL_UNIFORM_BUFFER, "lut", 6*4 * Buffers.SIZEOF_FLOAT, null);
		glos.newBuffer(GL_UNIFORM_BUFFER, "idm", 16 * Buffers.SIZEOF_FLOAT, GLBuffers.newDirectFloatBuffer(FloatUtil.makeIdentity(new float[16])));
		
		glos.buffers.loadIdentity("model");
		//global written during reshape call

		glos.programs.newProgram("image", "shaders", "texture", "texture");
		glos.programs.newProgram("anaglyph", "shaders", "roiTexture", "anaglyph");
		glos.programs.newProgram("roi", "shaders", "roiTexture", "roiTexture");
		glos.programs.addLocation("anaglyph", "ana");
		glos.programs.addLocation("anaglyph", "dubois");
		
		glos.newTexture("anaglyph");
		glos.newBuffer(GL_ARRAY_BUFFER, "anaglyph");
		glos.newBuffer(GL_ELEMENT_ARRAY_BUFFER, "anaglyph");
		glos.newVao("anaglyph", 3, GL_FLOAT, 3, GL_FLOAT);
		gl.glGenFramebuffers(1, stereoFramebuffers, 0);
		gl.glGenRenderbuffers(1, stereoFramebuffers, 1);
		
		rgldu=new RoiGLDrawUtility(imp, drawable);
		
		zoomIndVerts=GLBuffers.newDirectFloatBuffer(4*3+4*4);
		
		//int[] pf=new int[1];
		//for(int i=1;i<5;i++) {
		//	JCGLObjects.PixelTypeInfo pti=JCGLObjects.getPixelTypeInfo(getPixelType(),i);
		//	gl.glGetInternalformativ(GL_TEXTURE_3D, pti.glInternalFormat, GL_TEXTURE_IMAGE_FORMAT, 1, pf, 0);
		//	IJ.log("Best in format for comps:"+i+" Int format:"+pti.glInternalFormat+" my form:"+pti.glFormat+" best:"+pf[0]);
		//}
		
		if(isMirror) {
			addMirrorListeners();
			updateMirror();
		}
	}
	
	@Override
	public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
		setGL(drawable);
		resetGlobalMatricies();
		gl.glViewport(x, y, width, height);
		if(dpimag>1.0 && !IJ.isMacOSX()) {
			//int[] vps=new int[4];
			//gl.glGetIntegerv(GL_VIEWPORT, vps, 0);
			//if(dpimag>1.0)IJ.log("bef VPS: "+vps[0]+" "+vps[1]+" "+vps[2]+" "+vps[3]);
			int srcRectWidthMag = (int)(srcRect.width*magnification+0.5);
			int srcRectHeightMag = (int)(srcRect.height*magnification+0.5);
			gl.glViewport(0, 0, (int)(srcRectWidthMag*dpimag+0.5), (int)(srcRectHeightMag*dpimag+0.5));
			//gl.glGetIntegerv(GL_VIEWPORT, vps, 0);
			//if(dpimag>1.0)IJ.log("aft VPS: "+vps[0]+" "+vps[1]+" "+vps[2]+" "+vps[3]);
		}
	}
	
	private void resetGlobalMatricies() {
		float[] ortho = FloatUtil.makeOrtho(new float[16], 0, false, -1f, 1f, -(float)srcRect.height/srcRect.width, (float)srcRect.height/srcRect.width, -1f, 1f);
		glos.buffers.loadMatrix("global", ortho);
		glos.buffers.loadIdentity("global",16*Buffers.SIZEOF_FLOAT);
	}

	@Override
	public void dispose(GLAutoDrawable drawable) {
		glos.setGL(drawable);
		glos.dispose();
		
		imp.unlock();
	}

	@Override
	public void display(GLAutoDrawable drawable) {
		//IJ.log("\\Update2:Display took: "+(System.nanoTime()-starttime)/1000000L+"ms");
		//starttime=System.nanoTime();
		if(imp.isLocked())return;
		imp.lock();
		int sl=imp.getZ()-1, fr=imp.getT()-1,chs=imp.getNChannels(),sls=imp.getNSlices(),frms=imp.getNFrames();
		if(go3d&&sls==1)go3d=false;
		sb.setPixelType(go3d?pixelType3d:getPixelType(), go3d?undersample:1);
		//if(sb.isFrameStack) {sl=fr; fr=0; sls=frms; frms=1;}
		float yrat=(float)srcRect.height/srcRect.width;
		
		int srcRectWidthMag = (int)(srcRect.width*magnification+0.5);
		int srcRectHeightMag = (int)(srcRect.height*magnification+0.5);
		setGL(drawable);
		glos.setGL(drawable);
		
		if(stereoUpdated) {
			if(stereoType!=StereoType.CARDBOARD) {
				resetGlobalMatricies();
			}
			if(stereoType==StereoType.ANAGLYPH) {
			}
			stereoUpdated=false;
		}
		if(threeDupdated || deletePBOs) {
			if(go3d) {
				for(int i=0;i<chs;i++) glos.textures.initiate("image",pixelType3d, sb.bufferWidth, sb.bufferHeight, sls);
			}else {
				glos.buffers.loadIdentity("model", 0);
				resetGlobalMatricies();
				for(int i=0;i<chs;i++) glos.textures.initiate("image",getPixelType(), sb.bufferWidth, sb.bufferHeight, 1);
			}
			threeDupdated=false;
		}
		
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
			if(doRoi)glos.textures.createRgbaTexture("roiGraphic", AWTTextureIO.newTextureData(gl.getGLProfile(), roiImage, false).getBuffer(), srcRectWidthMag, srcRectHeightMag, 1, 4);
		}
		boolean[] doOv=null;
		if(!JCP.openglroi && overlay!=null && go3d) {
			doOv=new boolean[sls];
			if(!glos.textures.containsKey("overlay") || glos.textures.getLength("overlay")!=sls) {
				glos.newTexture("overlay",sls);
				glos.newBuffer(GL_ARRAY_BUFFER, "overlay");
				glos.newBuffer(GL_ELEMENT_ARRAY_BUFFER, "overlay");
				glos.newVao("overlay", 3, GL_FLOAT, 3, GL_FLOAT);
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
					glos.textures.createRgbaTexture("overlay", osl, AWTTextureIO.newTextureData(gl.getGLProfile(), roiImage, false).getBuffer(), srcRectWidthMag, srcRectHeightMag, 1, 4);
				}
			}
		}
		
		if(!glos.textures.containsPboKey("image") || deletePBOs || glos.textures.getPboLength("image")!=(chs*frms)) {
			glos.textures.newPbo("image", chs*frms);
			sb.resetSlices();
			deletePBOs=false;
		}
				
		if(myImageUpdated) {
			if(go3d) {
				//sb.updateBuffers(fr+1,true);
				if((lastPosition[0]==imp.getC()||imp.getCompositeMode()!=IJ.COMPOSITE)  && lastPosition[1]==(imp.getZ()) && lastPosition[2]==imp.getT()) {
					sb.resetSlices();
				}
				for(int i=0;i<chs;i++){ 
					for(int ifr=0;ifr<frms;ifr++) {
						for(int isl=0;isl<sls;isl++) {
							if(!sb.isSliceUpdated(isl, ifr)) {
								glos.textures.updateSubRgbaPBO("image",ifr*chs+i, sb.getSliceBuffer(i+1, isl+1, ifr+1),0, isl*sb.sliceSize, sb.sliceSize, sb.bufferSize);
								if(i==(chs-1))sb.updateSlice(isl,ifr);
							}
						}
					}
				}
				
			}else {
				int cfr=sb.isFrameStack?0:fr;
				if(usePBOforSlices) {
					//IJ.log("sl:"+(sl+1)+" fr:"+(fr+1)+" lps:"+lastPosition[1]+" lpf:"+lastPosition[2]);
					if((lastPosition[0]==imp.getC()||imp.getCompositeMode()!=IJ.COMPOSITE) && lastPosition[1]==(imp.getZ()) && lastPosition[2]==imp.getT()) {
						sb.resetSlices();
						//I don't think I need to update all slices in all scenarios here
						//not if lut was changed
					}
					if(!sb.isSliceUpdated(sl,fr)) {
						//sb.updateImageBufferSlice(sl+1, fr+1);
						try {
							for(int i=0;i<chs;i++) {
								int ccfr=cfr*chs+i;
								//glos.textures.updateSubRgbaPBO("image",ccfr, sb.imageFBs[ccfr],sb.imageFBs[ccfr].position(), sb.imageFBs[ccfr].position(), sb.sliceSize, sb.bufferSize);
								glos.textures.updateSubRgbaPBO("image",ccfr, sb.getSliceBuffer(i+1, sl+1, fr+1),0, sl*sb.sliceSize, sb.sliceSize, sb.bufferSize);
								//sb.imageFBs[ccfr].rewind();
								sb.updateSlice(sl, fr);
							}
						}catch(Exception e) {
							if(e instanceof GLException) {
								GLException gle=(GLException)e;
								IJ.log(gle.getMessage());
								IJ.log("Out of memory, switching usePBOforSlices off");
								usePBOforSlices=false;
								sb.resetSlices();
								glos.textures.disposePbo("image");
								glos.textures.newPbo("image", frms);
							}
						}
					}
					for(int i=0;i<chs;i++) {
						int ccfr=cfr*chs+i;
						glos.textures.loadTexFromPBO("image",ccfr, "image", i, sb.bufferWidth, sb.bufferHeight, 1, sb.isFrameStack?fr:sl, getPixelType(), COMPS, false);
					}
				}else {
					//still update stackbuffer?
					for(int i=0;i<chs;i++) {
						glos.textures.createRgbaTexture("image", i, sb.getSliceBuffer(i+1, sl+1, fr+1), sb.bufferWidth, sb.bufferHeight, 1, COMPS);
					}
				}
			}
			myImageUpdated=false;
			if(needImageUpdate) {showUpdateButton(false);}
			needImageUpdate=false;
		}
		if(go3d) {
			//if(pboWasUpdated) {
			//	IJ.log("SubRgbaTex");
			//	for(int isl=0;isl<sls;isl++) {
			//		for(int i=0;i<chs;i++) {
			//			glos.textures.subRgbaTexture("image", i, sb.getSliceBuffer(i+1, isl+1, fr+1), sl, tex4div(imageWidth/undersample), tex4div(imageHeight/undersample), 1, 1);
			//		}
			//	}
			//}else {
				for(int i=0;i<chs;i++) {
					int ccfr=fr*chs+i;
					glos.textures.loadTexFromPBO("image", ccfr, "image", i, sb.bufferWidth, sb.bufferHeight, sls, 0, pixelType3d, COMPS, false);
				}
			//}
		}
		

		Calibration cal=imp.getCalibration();
		float zmax=0f;
		int zmaxsls=(int)((cal.pixelDepth*(double)sls)/(cal.pixelWidth));
		if(go3d)zmax=(float)(zmaxsls)/(float)srcRect.width;
		
		float 	offx=2f*(float)srcRect.x/srcRect.width, vwidth=2f*(float)imageWidth/srcRect.width,
				offy=2f*(1f-((float)(srcRect.y+srcRect.height)/imageHeight))*(float)imageHeight/srcRect.height,
				vheight=2f*(float)imageHeight/srcRect.height, 
				tw=(2*imageWidth-tex4div(imageWidth))/(float)imageWidth,th=(2*imageHeight-tex4div(imageHeight))/(float)imageHeight;
		//Quad, 3 space verts, 3 texture verts per each of 4 points of a quad
		float[] initVerts=new float[] {
				-1f-offx, 			(-1f-offy)*yrat, 			-zmax,		0, th, go3d?1f:0,
				-1f-offx+vwidth, 	(-1f-offy)*yrat, 			zmax,		tw, th, 0,
				-1f-offx+vwidth, 	(-1f-offy+vheight)*yrat, 	zmax,		tw, 0, 0,
				-1f-offx, 			(-1f-offy+vheight)*yrat, 	-zmax,		0, 0, go3d?1f:0
		};

		//drawing
		gl.glDisable(GL_SCISSOR_TEST);
		//gl.glDrawBuffers(1, new int[] {GL_BACK_LEFT},0);
		//gl.glDrawBuffer(GL_BACK_LEFT);
		gl.glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
		gl.glClearBufferfv(GL_COLOR, 0, new float[] {0f,0f,0f,0f},0);
        gl.glClearBufferfv(GL_DEPTH, 0, new float[] {0f},0);
		
		int views=1;
		if(go3d && stereoType.ordinal()>0)views=2;
		for(int stereoi=0;stereoi<views;stereoi++) {
			glos.programs.useProgram("image");
			if(go3d) {
				if(stereoType==StereoType.QUADBUFFER) {
					if(stereoi==1)
						gl.glDrawBuffer(GL_RIGHT);
						//gl.glDrawBuffers(1, new int[] {GL_BACK_RIGHT},0);
				}else if(stereoType==StereoType.CARDBOARD) {
					float[] ortho = FloatUtil.makeOrtho(new float[16], 0, false, -CB_MAXSIZE, CB_MAXSIZE, -CB_MAXSIZE*yrat, CB_MAXSIZE*yrat, -CB_MAXSIZE, CB_MAXSIZE);
					float[] translate=FloatUtil.makeTranslation(new float[16], 0, false, (stereoi==0?(-CB_MAXSIZE*CB_TRANSLATE):(CB_MAXSIZE*CB_TRANSLATE)), 0f, 0f);
					ortho=FloatUtil.multMatrix(ortho, translate);
					gl.glEnable(GL_SCISSOR_TEST);
					int height=drawable.getSurfaceHeight();
					int width=drawable.getSurfaceWidth();
					int y=(int)((1f-(1f/CB_MAXSIZE))*yrat/2f*(float)height);
					gl.glScissor((width/2)-(int)(width/CB_MAXSIZE/2f) + (int)(CB_TRANSLATE*width/2f*(stereoi==0?-1:1)), y, (int)(width/CB_MAXSIZE), (int)(height/CB_MAXSIZE));
					glos.buffers.loadMatrix("global", ortho);
				}else if(stereoType==StereoType.ANAGLYPH) {
					gl.glBindFramebuffer(GL_FRAMEBUFFER, stereoFramebuffers[0]);
					gl.glBindRenderbuffer(GL_RENDERBUFFER, stereoFramebuffers[1]);
					if(stereoi==0) {
						JCGLObjects.PixelTypeInfo info=JCGLObjects.getPixelTypeInfo(pixelType3d,4);
						gl.glBindTexture(GL_TEXTURE_3D, glos.textures.get("anaglyph"));
						gl.glTexImage3D(GL_TEXTURE_3D, 0, info.glInternalFormat, drawable.getSurfaceWidth(),drawable.getSurfaceHeight(), 1, 0, GL_RGBA, info.glPixelSize, null);
						gl.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
						gl.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
						gl.glFramebufferTexture3D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_3D, glos.textures.get("anaglyph"), 0, 0);
						gl.glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT, drawable.getSurfaceWidth(), drawable.getSurfaceHeight());
						gl.glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, stereoFramebuffers[1]);
						gl.glBindTexture(GL_TEXTURE_3D, 0);
					}
					gl.glViewport(0, 0, drawable.getSurfaceWidth(), drawable.getSurfaceHeight());
					gl.glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
					if(gl.glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)IJ.error("not ready");
				}
				
				//Rotate
				float dxst=(float)dx;
				if(stereoi>0) {dxst-=(float)JCP.stereoSep; if(dxst<0)dxst+=360f;}
				float[] matrix=FloatUtil.makeRotationEuler(new float[16], 0, dy*FloatUtil.PI/180f, (float)dxst*FloatUtil.PI/180f, (float)dz*FloatUtil.PI/180f);
				glos.buffers.loadMatrix("model", matrix);
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
				
				if(ltr==null || !(ltr[0]==left && ltr[1]==top && ltr[2]==reverse) || !srcRect.equals(prevSrcRect)) {
					ByteBuffer vertb=(ByteBuffer)glos.buffers.getDirectBuffer(GL_ARRAY_BUFFER, "image");
					vertb.rewind();
					if(left) { //left or right
						lim=imageWidth*4*6;
						for(float p=0;p<imageWidth;p+=1.0f) {
							float xt,xv;
							if(reverse) {
								xt=(p+0.5f)/imageWidth*tw;
								xv=(p*2f-srcRect.width-2f*srcRect.x)/srcRect.width;
							} else {
								xt=(imageWidth-(p+0.5f))/imageWidth*tw;
								xv=((imageWidth-p)*2f-srcRect.width-2f*srcRect.x)/srcRect.width;
							}
							for(int i=0;i<4;i++) {
								vertb.putFloat(xv); vertb.putFloat(initVerts[i*6+1]); vertb.putFloat(initVerts[i*6+2]);
								vertb.putFloat(xt); vertb.putFloat(initVerts[i*6+4]); vertb.putFloat(initVerts[i*6+5]);
							}
						}
					} else if(top) { //top or bottom
						lim=imageHeight*4*6;
						for(float p=0;p<imageHeight;p+=1.0f) {
							float yt,yv;
							if(reverse) {
								yt=(p+0.5f)/imageHeight*th;
								yv=(float)(imageHeight-p)/srcRect.height*2f*yrat+initVerts[1];
							}else {
								yt=(float)(imageHeight-(p+0.5f))/imageHeight*th;
								yv=(float)p/srcRect.height*2f*yrat+initVerts[1];
							}
							for(int i=0;i<4;i++) {
								float zv=initVerts[i*6+2];
								float zt=initVerts[i*6+5];
								if(i==1) {zv=-zmax; zt=1f;}
								else if(i==3) {zv=zmax; zt=0f;}
								vertb.putFloat(initVerts[i*6]); vertb.putFloat(yv); vertb.putFloat(zv);
								vertb.putFloat(initVerts[i*6+3]); vertb.putFloat(yt); vertb.putFloat(zt);
							}
						}
					}else { //front or back
						lim=zmaxsls*4*6;
						for(float csl=0;csl<zmaxsls;csl+=1.0f) {
							float z=csl;
							if(!reverse) z=((float)zmaxsls-csl-1f);
							for(int i=0;i<4;i++) {
								vertb.putFloat(initVerts[i*6]); vertb.putFloat(initVerts[i*6+1]); vertb.putFloat(((float)zmaxsls-2f*z)/srcRect.width); 
								vertb.putFloat(initVerts[i*6+3]); vertb.putFloat(initVerts[i*6+4]); vertb.putFloat((z+0.5f)/zmaxsls); 
							}
						}
					}
				}
				ltr=new boolean[] {left,top,reverse};
				
				//Blend
				gl.glEnable(GL_BLEND);
				if(renderFunction.equals("MAX")) {
					gl.glBlendEquation(GL_MAX);
					gl.glBlendFunc(GL_SRC_COLOR, GL_DST_COLOR);
				}else if(renderFunction.equals("ALPHA")) {
					gl.glBlendEquation(GL_FUNC_ADD);
					gl.glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
				}
				
			}else {
				gl.glDisable(GL_BLEND);
				lim=initVerts.length;
				boolean push=false;
				if(!srcRect.equals(prevSrcRect))push=true;
				if(push) {
					ByteBuffer vertb=(ByteBuffer)glos.buffers.getDirectBuffer(GL_ARRAY_BUFFER, "image");
					vertb.rewind(); vertb.asFloatBuffer().rewind();
					vertb.asFloatBuffer().put(initVerts);
					for(int i=0;i<initVerts.length/6;i++)vertb.asFloatBuffer().put(i*6+5,0.5f);
				}
			}
			
			//setluts
			ByteBuffer lutMatrixPointer=(ByteBuffer)glos.buffers.ubuffers.get("lut");
			lutMatrixPointer.rewind();
			LUT[] luts=imp.getLuts();
			boolean[] active=new boolean[COMPS];
			for(int i=0;i<COMPS;i++)active[i]=true;
			if(imp.isComposite())active=((CompositeImage)imp).getActiveChannels();
			int cmode=imp.getCompositeMode();
			int bitd=imp.getBitDepth();
			double topmax=Math.pow(2, bitd==24?8:bitd)-1.0;
			for(int i=0;i<6;i++) {
				float min=0,max=0,color=0;
				if(luts==null || luts.length==0 ||bitd==24) {
					max=1f;
					color=i==0?8:0;//(i==0?1:i==1?2:i==2?4:0);
				}else {
					if(i<luts.length) {
						int rgb=luts[i].getRGB(255);
						if(active[i] && !(cmode!=IJ.COMPOSITE && imp.getC()!=(i+1))) {
							if(cmode==IJ.GRAYSCALE)color=7;
							else color=(((rgb & 0x00ff0000)==0x00ff0000)?1:0) + (((rgb & 0x0000ff00)==0x0000ff00)?2:0) + (((rgb & 0x000000ff)==0x000000ff)?4:0);
						}
						if(bitd<32) {
							min=(float)(Math.round(luts[i].min)/topmax);
							max=(float)(Math.round(luts[i].max)/topmax);
						}else {
							min=(float)luts[i].min;
							max=(float)luts[i].max;
						}
						if(min==max) {if(min==0){max+=(1/topmax);}else{min-=(1/topmax);}}
					}
				}
				lutMatrixPointer.putFloat(min);
				lutMatrixPointer.putFloat(max);
				lutMatrixPointer.putFloat(color);
				lutMatrixPointer.putFloat(0f); //padding for vec3 std140
			}
			lutMatrixPointer.rewind();
			
			glos.bindUniformBuffer("global", 1);
			glos.bindUniformBuffer("model", 2);
			glos.bindUniformBuffer("lut", 3);
			glos.drawTexVao("image",GL_UNSIGNED_SHORT, lim/4, chs);
			glos.unBindBuffer(GL_UNIFORM_BUFFER, 1);
			glos.unBindBuffer(GL_UNIFORM_BUFFER, 2);
			glos.unBindBuffer(GL_UNIFORM_BUFFER, 3);
			glos.programs.stopProgram();

			
			if(roi!=null || overlay!=null) { 
				float z=0f;
				float zf=(float)(cal.pixelDepth/cal.pixelWidth)/srcRect.width;
				if(go3d) z=((float)sls-2f*sl)*zf;
				gl.glEnable(GL_BLEND);
				gl.glBlendEquation(GL_FUNC_ADD);
				gl.glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
				if(!JCP.openglroi) {
					if(doRoi)drawGraphics(gl, z, "roiGraphic", 0);
					if(doOv!=null) {
						for(int osl=0;osl<sls;osl++) {
							if(doOv[osl]) {
								drawGraphics(gl, ((float)sls-2f*(float)osl)*zf, "overlay", osl);
							}
						}
					}
				}else {
					Color anacolor=null;
					if(stereoType==StereoType.ANAGLYPH && go3d) {
						if(JCP.dubois)anacolor=(stereoi==0)?Color.RED:Color.CYAN;
						else anacolor=(stereoi==0)?JCP.leftAnaglyphColor:JCP.rightAnaglyphColor;
					}
					gl.glDisable(GL_BLEND);
					rgldu.setImp(imp);
					glos.bindUniformBuffer("global", 1);
					glos.bindUniformBuffer("model", 2);
					
					rgldu.drawRoiGL(drawable, roi, true, anacolor, go3d);
					if(overlay!=null) {
						for(int i=0;i<overlay.size();i++) {
							rgldu.drawRoiGL(drawable, overlay.get(i), false, anacolor, go3d);
						}
					}
					
					gl.glBindBufferBase(GL_UNIFORM_BUFFER, 1, 0);
					gl.glBindBufferBase(GL_UNIFORM_BUFFER, 2, 0);
				}
			}
			boolean nzi=(!myHZI && (srcRect.width<imageWidth || srcRect.height<imageHeight));
			
			if(nzi) {
				drawMyZoomIndicator(drawable);
			}
			//IJ.log("\\Update0:Display took: "+(System.nanoTime()-starttime)/1000000L+"ms");	 
			gl.glDisable(GL_SCISSOR_TEST);
			gl.glFinish();
			
			if(go3d && stereoType==StereoType.ANAGLYPH) {
				gl.glBindFramebuffer(GL_FRAMEBUFFER, 0);
				gl.glBindRenderbuffer(GL_RENDERBUFFER, 0);
				gl.glViewport(0, 0, drawable.getSurfaceWidth(), drawable.getSurfaceHeight());
				gl.glEnable(GL_BLEND);
				gl.glBlendEquation(GL_MAX);
				gl.glBlendFunc(GL_SRC_COLOR, GL_DST_COLOR);
				FloatBuffer vb=GLBuffers.newDirectFloatBuffer(new float[] {
						-1,	-yrat,	0, 	0,0,0.5f,
						1,	-yrat,	0, 	1,0,0.5f,
						1,	yrat,	0, 	1,1,0.5f,
						-1,	yrat,	0,	0,1,0.5f
				});


				glos.programs.useProgram("anaglyph");
				gl.glUniformMatrix3fv(glos.programs.getLocation("anaglyph", "ana"), 1, false, JCP.anaColors[stereoi], 0);
				gl.glUniform1f(glos.programs.getLocation("anaglyph", "dubois"), JCP.dubois?1f:0f);
				//gl.glEnable(GL3.GL_FRAMEBUFFER_SRGB);
				drawGraphics(gl, "anaglyph", 0, "idm", vb);
				//gl.glDisable(GL3.GL_FRAMEBUFFER_SRGB);
				glos.programs.stopProgram();
			}
		} //stereoi for
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
		undersample=us;
		resetBuffers();
	}
	
	public void resetBuffers() {
		sb.resetSlices();
		deletePBOs=true;
		myImageUpdated=true;
		repaint();
	}
	
	private int tex4div(int wh) {
		return wh+((wh%4)>0?(4-wh%4):0);
	}
	
	public void set3dPixelType(PixelType newtype) {
		int bits=imp.getBitDepth();
		if(bits==24)bits=8;
		if(newtype==PixelType.FLOAT && bits<32) {IJ.error("Not enough image bits for float display pixel");return;}
		if((newtype==PixelType.SHORT || newtype==PixelType.INT_RGB10A2) && (bits<16)) {IJ.error("Not enough image bits for high bit display pixel");return;}

		//while(updatingBuffers>0)IJ.wait(50);
		pixelType3d=newtype;
		resetBuffers();
	}
	
	private int getCurrent3dPixelType() {
		for(int i=0;i<PixelType.values().length;i++)if(pixelType3d==PixelType.values()[i])return i;
		return -1;
	}
	
	private void drawGraphics(GL3 gl, String name, int index, String modelmatrix, Buffer vb) {

		ShortBuffer eb=GLBuffers.newDirectShortBuffer(new short[] {0,1,2,2,3,0});
		gl.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
		gl.glBindBufferBase(GL_UNIFORM_BUFFER, 1, glos.buffers.get(GL_UNIFORM_BUFFER, "global"));
		gl.glBindBufferBase(GL_UNIFORM_BUFFER, 2, glos.buffers.get(GL_UNIFORM_BUFFER, modelmatrix));
		glos.drawTexVaoWithEBOVBO(name, index, eb, vb);
		gl.glBindBufferBase(GL_UNIFORM_BUFFER, 1, 0);
		gl.glBindBufferBase(GL_UNIFORM_BUFFER, 2, 0);
		if(Prefs.interpolateScaledImages)gl.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
	}
	
	private void drawGraphics(GL3 gl, float z, String name, int index) {
		float yrat=(float)srcRect.height/srcRect.width;
		FloatBuffer vb=GLBuffers.newDirectFloatBuffer(new float[] {
				-1,	-yrat,	z, 	0,1,0.5f,
				1,	-yrat,	z, 	1,1,0.5f,
				1,	yrat,	z, 	1,0,0.5f,
				-1,	yrat,	z,	0,0,0.5f
		});
		glos.programs.useProgram("roi");
		drawGraphics(gl, name, index, "model", vb);
		glos.programs.stopProgram();
	}


	private PixelType getPixelType() {
		switch(imp.getBitDepth()) {
		case 1 : return PixelType.BYTE;
		case 8 : return PixelType.BYTE;
		case 16 : return PixelType.SHORT;
		case 24 : return PixelType.INT_RGBA8;
		case 32 : return PixelType.FLOAT;
		}
		return PixelType.BYTE;
	}
	
	
	public void toggle3d() {
		set3d(!go3d);
	}

	public void set3d(boolean newboo) {
		if(go3d==newboo)return;
		threeDupdated=true;
		myImageUpdated=true;
		go3d=newboo;
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
		repaint();
	}
	
	public void setStereo(StereoType stereoTypeChoice) {
		stereoType=stereoTypeChoice;
		stereoUpdated=true;
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
		new StackWindow(imp,new ImageCanvas(imp) {
			private static final long serialVersionUID = 1L;
			@Override
			public void paint(Graphics g){
				updateMirror();
				icc.repaint();
				super.paint(g);
			}
		});
		imp.getWindow().addWindowListener(this);
	}
	
	public void revert() {
		showUpdateButton(false);
		if(isMirror){
			new StackWindow(imp,new ImageCanvas(imp));
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
		setGL(drawable);
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
		
		drawable.getGL().getGL3().glDisable(GL_BLEND);
		zoomIndVerts.rewind();
		float[] color=new float[] {(float)128/255, (float)128/255, 1f, 1f};
		zoomIndVerts.put(x1).put(y1).put(0f).put(color);
		zoomIndVerts.put(x1+w1).put(y1).put(0f).put(color);
		zoomIndVerts.put(x1+w1).put(y1-h1).put(0f).put(color);
		zoomIndVerts.put(x1).put(y1-h1).put(0f).put(color);
		zoomIndVerts.rewind();
		
		glos.bindUniformBuffer("global", 1);
		glos.bindUniformBuffer("idm", 2);
		rgldu.drawGLfb(gl, zoomIndVerts, GL_LINE_LOOP);
		zoomIndVerts.rewind();
		zoomIndVerts.put(x1+x2).put(y1-y2).put(0f).put(color);
		zoomIndVerts.put(x1+x2+w2).put(y1-y2).put(0f).put(color);
		zoomIndVerts.put(x1+x2+w2).put(y1-y2-h2).put(0f).put(color);
		zoomIndVerts.put(x1+x2).put(y1-y2-h2).put(0f).put(color);
		zoomIndVerts.rewind();
		
		rgldu.drawGLfb(gl, zoomIndVerts, GL_LINE_LOOP);

		gl.glBindBufferBase(GL_UNIFORM_BUFFER, 1, 0);
		gl.glBindBufferBase(GL_UNIFORM_BUFFER, 2, 0);
		
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
		if(icc!=null) {
			updateMirror();
			icc.repaint(x,y,width,height);
		}else super.repaint(x,y,width,height);
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
				//int a=0;
				//for(int i=0;i<imageFBs.length;i++)if(imageFBs!=null)a++;
				//if(a<imageFBs.length)mi3d.setLabel(lbl+" PBOs:"+a+"/"+imageFBs.length);
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
			int bits=imp.getBitDepth();
			int end=0; 
			if(bits==24) addCMI(menu,pixelTypeStrings[4],pixelType3d==PixelType.values()[4]);
			else {end=1; if(bits>8)end++; if(bits>16)end++;}
			for(int i=0;i<end;i++) addCMI(menu,pixelTypeStrings[i],pixelType3d==PixelType.values()[i]);
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
			
			//menu=new Menu("Normal Pixel Type");
			//for(int i=0;i<pixelTypeStrings.length;i++) addCMI(menu,pixelTypeStrings[i],pixelType==PixelType.values()[i]);
			//dcpopup.add(menu);
			
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
			sb.resetSlices();
			myImageUpdated=true; 
			repaint();
		}
		else if(cmd.equals("revert")){revert();}
		else if(cmd.equals("reset3d")){resetAngles();}
		else if(cmd.equals("prefs")){JCP.preferences();}
		else if(cmd.equals("bgload")) {
			IJ.error("TODO: bgload");
			//if(go3d) updateBuffers(imp.getT(),true); 
			//else updateBuffersBackground(null);
		}else if(cmd.equals("Recorder")){
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
				set3dPixelType(PixelType.values()[temp]);//,whichmenu.equals("3D Pixel Type")
				checkRenderPopup(whichmenu, pixelTypeStrings[getCurrent3dPixelType()]);//whichmenu.equals("3D Pixel Type")
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
		if(!icc.getSize().equals(s)) {
			setSize(s);
			icc.getParent().setSize(s.width+ins.left+ins.right,s.height+ins.top+ins.bottom);
		}
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
	}
	
	@Override
	public void mouseDragged(MouseEvent e) {
		if(shouldKeep(e)) {
			if(IJ.spaceBarDown()&&isMirror) {
				scroll(e.getX(),e.getY());
				imp.getCanvas().setSourceRect(srcRect);
			}else if(go3d){
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
