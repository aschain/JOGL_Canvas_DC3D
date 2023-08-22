package ajs.joglcanvas;

import ij.CompositeImage;
import ij.IJ;
import ij.ImageListener;
import ij.ImagePlus;
import ij.Prefs;
import ij.gui.ImageCanvas;
import ij.gui.ImageWindow;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.gui.ScrollbarWithLabel;
import ij.gui.StackWindow;
import ij.gui.Toolbar;
import ij.measure.Calibration;
import ij.process.ByteStatistics;
import ij.process.ImageProcessor;
import ij.process.LUT;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.CheckboxMenuItem;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Menu;
import java.awt.MenuItem;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PopupMenu;
import java.awt.Rectangle;
import java.awt.Button;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.nio.Buffer;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;

import javax.swing.JPopupMenu;

import java.nio.ByteBuffer;

import com.jogamp.common.nio.Buffers;
import com.jogamp.newt.awt.NewtCanvasAWT;
import com.jogamp.newt.opengl.GLWindow;
import com.jogamp.newt.Display;
import com.jogamp.newt.MonitorDevice;
import com.jogamp.newt.NewtFactory;
import com.jogamp.newt.Screen;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2GL3;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLContext;
import com.jogamp.opengl.util.texture.awt.AWTTextureIO;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLException;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.math.FloatUtil;
import com.jogamp.opengl.util.GLBuffers;

import ajs.joglcanvas.StackBuffer.MinMax;


public class JOGLImageCanvas extends ImageCanvas implements GLEventListener, ImageListener, KeyListener, ActionListener, ItemListener{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	final public NewtCanvasAWT icc;
	final public GLWindow glw;
	final private StackBuffer sb;
	private JCGLObjects glos;
	protected boolean disablePopupMenu;
	protected double dpimag=1.0;
	protected float surfaceScale=1.0f;
	protected boolean myImageUpdated=true;
	private boolean deletePBOs=false;
	protected boolean isMirror=false;
	public Frame mirror=null;
	private boolean mirrorMagUnlock=false;
	private ImageState imageState;
	private boolean[] ltr=null;
	private boolean mouseDragged=false;

	protected boolean go3d=JCP.go3d;
	public String renderFunction=JCP.renderFunction;
	protected int sx,sy, osx, osy;
	protected float dx=0f,dy=0f,dz=0f, tx=0f, ty=0f, tz=0f, supermag=0f, zNear=JCP.zNear, zFar=JCP.zFar;
	private float[] gamma=null;
	
	private PopupMenu dcpopup=null;
	private MenuItem mi3d=null;
	protected boolean myHZI=false;

	final private FloatBuffer[] zoomIndVerts=new FloatBuffer[] {GLBuffers.newDirectFloatBuffer(4*3+4*4),GLBuffers.newDirectFloatBuffer(4*3+4*4)};
	private int lim;
	private int undersample=JCP.undersample;
	public enum StereoType{
		OFF("Stereo off"), 
		CARDBOARD("Google Cardboard-SBS"), 
		ANAGLYPH("Anaglyph"), 
		HSBS("Half Side-by-side"), 
		QUADBUFFER("OpenGL Quad Buffers");
		
		private final String description;
		StereoType(String description){
			this.description=description;
		}
		public String getDescription() {return description;}
		public static String[] getAllDescriptions() {
			StereoType[] sts=StereoType.values();
			String[] all=new String[sts.length];
			for(int i=0;i<sts.length;i++) {
				all[i]=sts[i].getDescription();
			}
			return all;
		}
		public static StereoType getStereoType(String description) {
			StereoType[] sts=StereoType.values();
			for(int i=0;i<sts.length;i++) {
				if(sts[i].getDescription().contentEquals(description)) return sts[i];
			}
			return StereoType.OFF;
		}
	};
	private static final float CB_MAXSIZE=4f;
	private static final float CB_TRANSLATE=0.5f;
	private StereoType stereoType=StereoType.OFF;
	private boolean stereoUpdated=true,threeDupdated=true;
	private int[] stereoFramebuffers=new int[1],stereoRenderbuffers=new int[1],tempFramebuffers=new int[1],tempRenderbuffers=new int[1];
	//private boolean mylock=false;

	enum PixelType{BYTE, SHORT, FLOAT, INT_RGB10A2, INT_RGBA8};
	private static final String[] pixelTypeStrings=new String[] {"4 bytes (8bpc, 32bit)","4 shorts (16bpc 64bit)","4 floats (32bpc 128bit)","1 int RGB10A2 (10bpc, 32bit)","1 int RGBA8 (8bpc, 32bit)"};
	protected PixelType pixelType3d=PixelType.BYTE;
	private static final int COMPS=1;
	
	private BIScreenGrabber screengrabber=null;
	private RoiGLDrawUtility rgldu=null;
	private boolean scbrAdjusting=false;
	private CutPlanesCube cutPlanes;
	private JCAdjuster jccpDialog,jcgDialog,jcrDialog;
	private boolean verbose=false;
	private long dragtime;
	final private JOGLEventAdapter joglEventAdapter;
	private Keypresses kps;
	public float fov=45f;
	public float frustumZshift;
	public float zmax=1f;
	public GLContext context;
	public Point2D.Double oicp=null;
	private Button menuButton;
	//private long starttime=0;
	private boolean onScreenMirrorCursor=false;
	private boolean warpPointerWorks=false;
	final private boolean stereoEnabled;
	private boolean one3Dslice=false;

	public JOGLImageCanvas(ImagePlus imp, boolean mirror) {
		super(imp);
		if(imp.getNSlices()==1)go3d=false;
		pixelType3d=getPixelType(imp);
		isMirror=mirror;
		//if(!isMirror) {setOverlay(imp.getCanvas().getOverlay());}
		imageState=new ImageState(imp, this);
		imageState.prevSrcRect=new Rectangle(0,0,0,0);
		Calibration cal=imp.getCalibration();
		zmax=(float)(cal.pixelDepth/cal.pixelWidth*(double)imp.getNSlices()/(double)imp.getWidth());
		cutPlanes=new CutPlanesCube(0,0,0,imp.getWidth(), imp.getHeight(), imp.getNSlices(), true);
		kps=new Keypresses();
		GraphicsConfiguration gc=imp.getWindow().getGraphicsConfiguration();
		AffineTransform t=gc.getDefaultTransform();
		dpimag=t.getScaleX();
		if(dpimag!=1.0)log("GC DPImag: "+dpimag);
		
		GLCapabilities glc=JCP.getGLCapabilities();
		if(glc==null) {
			log("Error in GL Capabilities, using default");
			glc=new GLCapabilities(GLProfile.getDefault());
		}
		int bits=imp.getBitDepth();
		if((bits<16 || bits==24)&& (glc.getRedBits()>8 || glc.getGreenBits()>8 || glc.getBlueBits()>8) ) {
			log("JOGLCanvas Deep Color Warning:\nOriginal image is 8 bits or less and therefore \nwon't display any differently with HDR 10 bits or higher display.");
		}
		
		sb=new StackBuffer(imp);
		
		Display display = NewtFactory.createDisplay(null);
		display.addReference();
		Screen screen=NewtFactory.createScreen(display, 0);
		screen.addReference();
		glw=GLWindow.create(glc);
		stereoEnabled=glc.getStereo();
		icc=new NewtCanvasAWT(glw){
			private static final long serialVersionUID = 1256279205085144008L;
			@Override
			public void reshape(int x, int y, int width, int height) {
				super.reshape(x,y,width, height);
				if(! IJ.isMacOSX()) {
					if(isMirror) {
						if(dpimag!=1.0) {java.awt.EventQueue.invokeLater(new Runnable() {public void run() {glw.setSize((int)(width*dpimag+0.5),(int)(height*dpimag+0.5));}});}
					}else {
						if(dpimag!=1.0) {glw.setSize((int)(width*dpimag+0.5),(int)(height*dpimag+0.5));}
					}
				}
				Dimension s=new Dimension(width,height);
				setMinimumSize(s);
				setPreferredSize(s);
			}
		};
		joglEventAdapter=new JOGLEventAdapter(this, glw);
		icc.setPreferredSize(new Dimension(imageWidth,imageHeight));
		glw.addGLEventListener(this);
		icc.setMinimumSize(new Dimension(10,10));
		ImagePlus.addImageListener(this);
		createPopupMenu();
		if(mirror)createMirror();
		addAdjustmentListening();
		java.awt.EventQueue.invokeLater(new Runnable() {
			@Override
			public void run() {
				showMenuButton(true);
			}
		});
		disablePopupMenu(go3d);
	}
	
	private void setDPImag(GLAutoDrawable drawable) {
		double newdpi=1.0;
		double gcdpi=icc.getGraphicsConfiguration().getDefaultTransform().getScaleX();
		if(gcdpi!=1.0)newdpi=gcdpi;
		if(newdpi!=1.0 && drawable!=null) {
			if(newdpi==(double)drawable.getSurfaceWidth()/(double)((int)glw.getWidth())) newdpi=1.0;
			//if(JCP.debug)log("reset dpimag to 1.0 because drawable matched glwindow width");
		}
		float[] ssc=glw.getCurrentSurfaceScale(new float[2]);
		if(ssc[0]!=1.0f) {
			if(JCP.debug)log("SurfaceScale:"+ssc[0]+" "+ssc[1]);
			surfaceScale=ssc[0];
			if(newdpi==1.0)newdpi=surfaceScale;
		}
		if(dpimag!=newdpi)
			setDPImag(newdpi);
	}
	
	public void setOne3Dslice(boolean boo) {
		one3Dslice=boo;
		this.resetGlobalMatrices(null);
	}

	//GLEventListener methods
	@Override
	public void init(GLAutoDrawable drawable) {
		glos=new JCGLObjects(drawable);
		context=drawable.getContext();
		int maxsize=glos.getMaxTextureSize(false);
		if(maxsize<Math.max(imp.getWidth(), imp.getHeight())) {
			log("*** JOGLCanvas images must be below "+maxsize+" on your computer ****\n*** Cancelling...");
			revert();
			return;
		}
		
		setDPImag(drawable);
		
		glos.clearColor();
		glos.glDisable(GL2GL3.GL_DEPTH_TEST);
		glos.glDisable(GL2GL3.GL_MULTISAMPLE);
		
		ByteBuffer elementBuffer2d=GLBuffers.newDirectByteBuffer(new byte[] {0,1,2,2,3,0});
		ByteBuffer vertb=GLBuffers.newDirectByteBuffer(4*6*Buffers.SIZEOF_FLOAT);
		float 	tw=(2*imageWidth-tex4div(imageWidth))/(float)imageWidth,
				th=(2*imageHeight-tex4div(imageHeight))/(float)imageHeight;
		//For display of the square, there are 3 space verts and 3 texture verts
		//for each of the 4 points of the square.
		vertb.asFloatBuffer().put(new float[] {
				-1f, 	-1f, 	0,		0, th, 0.5f,
				 1f, 	-1f, 	0,		tw, th, 0.5f,
				 1f, 	1f, 	0,		tw, 0, 0.5f,
				-1f, 	1f, 	0,		0, 0, 0.5f
		});
		
		glos.newTexture("image2d", imp.getNChannels(), false);
		glos.newArrayBuffer("image2d", vertb);
		glos.newElementBuffer("image2d", elementBuffer2d);
		glos.newVao("image2d");
		
		glos.newProgram("image2d", "shaders", "texture", "texture2d");
		glos.newProgram("image", "shaders", "texture", "texture");
		glos.newProgram("roi", "shaders", "texture", "roiTexture");

		glos.newTexture("roiGraphic", false);
		glos.newArrayBuffer("roiGraphic");
		glos.newElementBuffer("roiGraphic");
		glos.newVao("roiGraphic");

		FloatBuffer id=GLBuffers.newDirectFloatBuffer(FloatUtil.makeIdentity(new float[16]));
		FloatBuffer aid=GLBuffers.newDirectFloatBuffer(new float[] {
				1f, 0, 0, 0,
				0, 1f, 0, 0,
				0, 0, -1f, 0,
				0, 0, 0, 1f,
				1f, 0, 0, 0,
				0, 1f, 0, 0,
				0, 0, 1f, 0,
				0, 0, 0, 1f
			});
		
		glos.newUniformBuffer("global", 16*2 * Buffers.SIZEOF_FLOAT, null);
		glos.newUniformBuffer("globalidm", 16*2 * Buffers.SIZEOF_FLOAT, aid);
		glos.getUniformBuffer("globalidm").setBindName("global");
		glos.newUniformBuffer("model", 16 * Buffers.SIZEOF_FLOAT, null);
		glos.newUniformBuffer("modelr", 16 * Buffers.SIZEOF_FLOAT, null);
		glos.getUniformBuffer("modelr").setBindName("model");
		glos.newUniformBuffer("luts", 12*4 * Buffers.SIZEOF_FLOAT, null);
		glos.newUniformBuffer("idm", 16 * Buffers.SIZEOF_FLOAT, id);

		glos.getUniformBuffer("model").loadIdentity();
		glos.getUniformBuffer("modelr").loadIdentity();
		//global written during reshape call
		
		rgldu=new RoiGLDrawUtility(imp, drawable,glos.getProgram("roi"), dpimag);
		
		//int[] pf=new int[1];
		//for(int i=1;i<5;i++) {
		//	JCGLObjects.PixelTypeInfo pti=JCGLObjects.getPixelTypeInfo(getPixelType(),i);
		//	gl.glGetInternalformativ(GL_TEXTURE_3D, pti.glInternalFormat, GL_TEXTURE_IMAGE_FORMAT, 1, pf, 0);
		//	log("Best in format for comps:"+i+" Int format:"+pti.glInternalFormat+" my form:"+pti.glFormat+" best:"+pf[0]);
		//}
		initAnaglyph();
		Point pre=MouseInfo.getPointerInfo().getLocation();
		glw.warpPointer(1,1);
		Point post=MouseInfo.getPointerInfo().getLocation();
		glw.warpPointer(pre.x,pre.y);
		if((pre.x-post.x+pre.y-post.y)==0) {
			//warp not working
			if(JCP.debug)log("warp not working");
		}else {
			warpPointerWorks=true;
			if(JCP.debug)log("warp working");
		}
		log("  Acc: "+glw.getChosenGLCapabilities());
	}
	
	private void init3dTex() {
		Calibration cal=imp.getCalibration();
		long zmaxsls=(long)((double)imp.getNSlices()*cal.pixelDepth/cal.pixelWidth);
		long maxsize=Math.max((long)imp.getWidth(), Math.max((long)imp.getHeight(), zmaxsls));

		ByteBuffer vertb=glos.getDirectBuffer(GL2GL3.GL_ARRAY_BUFFER, "image3d");
		int floatsPerVertex=6;
		long vertbSize=maxsize*floatsPerVertex*4*Buffers.SIZEOF_FLOAT;
		if(vertb==null || ((Buffer)vertb).capacity()!=(int)vertbSize) {
			int elementsPerSlice=6;
			short[] e=new short[(int)maxsize*elementsPerSlice];
			for(int i=0; i<(maxsize);i++) {
				e[i*6+0]=(short)(i*4+0); e[i*6+1]=(short)(i*4+1); e[i*6+2]=(short)(i*4+2);
				e[i*6+3]=(short)(i*4+2); e[i*6+4]=(short)(i*4+3); e[i*6+5]=(short)(i*4+0);
			}
			ShortBuffer elementBuffer=GLBuffers.newDirectShortBuffer(e);
			((Buffer)elementBuffer).rewind();
	
			glos.newTexture("image3d", imp.getNChannels(), true);
			glos.newArrayBuffer("image3d", maxsize*floatsPerVertex*4*Buffers.SIZEOF_FLOAT, null);
			glos.newElementBuffer("image3d", ((Buffer)elementBuffer).capacity()*Buffers.SIZEOF_SHORT, elementBuffer);
			glos.newVao("image3d");
		}
	}
	
	private void initAnaglyph() {
		//Unlike the image2d vertex buffer, this one is not vertically
		//flipped with respect to the texture coordinates.
		FloatBuffer avb=GLBuffers.newDirectFloatBuffer(new float[] {
				-1,	-1,	0, 	0,0,0.5f,
				1,	-1,	0, 	1,0,0.5f,
				1,	1,	0, 	1,1,0.5f,
				-1,	1,	0,	0,1,0.5f
		});
		byte[] eb=new byte[] {0,1,2,2,3,0};
		
		glos.newTexture("temp",2, false);
		glos.newArrayBuffer("temp", avb);
		glos.newElementBuffer("temp", GLBuffers.newDirectByteBuffer(eb));
		glos.newVao("temp");
		glos.newFramebuffers(1, tempFramebuffers, 0);
		glos.newRenderbuffers(1, tempRenderbuffers, 0);
		
		glos.newTexture("anaglyph",2, false);
		glos.newArrayBuffer("anaglyph", avb);
		glos.newElementBuffer("anaglyph", GLBuffers.newDirectByteBuffer(eb));
		glos.newVao("anaglyph");
		glos.newFramebuffers(1, stereoFramebuffers, 0);
		glos.newRenderbuffers(1, stereoRenderbuffers, 0);
		
		glos.newProgram("anaglyph", "shaders", "texture", "anaglyph");

		glos.newUniformBuffer("3d-view", 16*2 * Buffers.SIZEOF_FLOAT, null);
		glos.newUniformBuffer("stereo-left-view", 16*2 * Buffers.SIZEOF_FLOAT, null);
		glos.newUniformBuffer("stereo-right-view", 16*2 * Buffers.SIZEOF_FLOAT, null);

		glos.getUniformBuffer("3d-view").setBindName("global");
		glos.getUniformBuffer("stereo-left-view").setBindName("global");
		glos.getUniformBuffer("stereo-right-view").setBindName("global");
		
		glos.newUniformBuffer("CB-left", 16*2 * Buffers.SIZEOF_FLOAT, null);
		glos.newUniformBuffer("CB-right", 16*2 * Buffers.SIZEOF_FLOAT, null);

		glos.getUniformBuffer("CB-left").setBindName("global");
		glos.getUniformBuffer("CB-right").setBindName("global");
		
		float[] orthocbl = FloatUtil.makeOrtho(new float[16], 0, false, -CB_MAXSIZE, CB_MAXSIZE, -CB_MAXSIZE, CB_MAXSIZE, -CB_MAXSIZE, CB_MAXSIZE);
		float[] orthocbr = FloatUtil.makeOrtho(new float[16], 0, false, -CB_MAXSIZE, CB_MAXSIZE, -CB_MAXSIZE, CB_MAXSIZE, -CB_MAXSIZE, CB_MAXSIZE);
		float[] translatecbl=FloatUtil.makeTranslation(new float[16], 0, false, -CB_MAXSIZE*CB_TRANSLATE, 0f, 0f);
		float[] translatecbr=FloatUtil.makeTranslation(new float[16], 0, false, CB_MAXSIZE*CB_TRANSLATE, 0f, 0f);
		FloatUtil.multMatrix(orthocbl, translatecbl);
		FloatUtil.multMatrix(orthocbr, translatecbr);
		glos.getUniformBuffer("CB-left").loadIdentity(0);
		glos.getUniformBuffer("CB-left").loadMatrix(orthocbl, 16 * Buffers.SIZEOF_FLOAT);
		glos.getUniformBuffer("CB-right").loadIdentity(0);
		glos.getUniformBuffer("CB-right").loadMatrix(orthocbr, 16 * Buffers.SIZEOF_FLOAT);
		
		glos.newUniformBuffer("HSBS-left", 16*2 * Buffers.SIZEOF_FLOAT, null);
		glos.newUniformBuffer("HSBS-right", 16*2 * Buffers.SIZEOF_FLOAT, null);

		glos.getUniformBuffer("HSBS-left").setBindName("global");
		glos.getUniformBuffer("HSBS-right").setBindName("global");
		orthocbl = FloatUtil.makeOrtho(new float[16], 0, false, -2f, 2f, -1f, 1f, -1f, 1f);
		orthocbr = FloatUtil.makeOrtho(new float[16], 0, false, -2f, 2f, -1f, 1f, -1f, 1f);
		translatecbl=FloatUtil.makeTranslation(new float[16], 0, false, -1f, 0f, 0f);
		translatecbr=FloatUtil.makeTranslation(new float[16], 0, false, 1f, 0f, 0f);
		FloatUtil.multMatrix(orthocbl, translatecbl);
		FloatUtil.multMatrix(orthocbr, translatecbr);
		glos.getUniformBuffer("HSBS-left").loadIdentity(0);
		glos.getUniformBuffer("HSBS-left").loadMatrix(orthocbl, 16 * Buffers.SIZEOF_FLOAT);
		glos.getUniformBuffer("HSBS-right").loadIdentity(0);
		glos.getUniformBuffer("HSBS-right").loadMatrix(orthocbr, 16 * Buffers.SIZEOF_FLOAT);
		
	}
	
	@Override
	public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
		if(JCP.debug) log("Reshaping:x"+x+" y"+y+" w"+width+" h"+height);
		setDPImag(drawable);
		double ratio=1.0;
		if(go3d && stereoType==StereoType.CARDBOARD) {
			Rectangle r=getCBViewportAspectRectangle(x,y,width,height);
			glos.glViewport(r.x, r.y, r.width, r.height);
		}else {
			ratio=((float)drawable.getSurfaceWidth()/drawable.getSurfaceHeight())/((float)srcRect.width/srcRect.height);
		}
		resetGlobalMatrices(drawable);
		if(isMirror) {
			if(drawable.getSurfaceWidth()!=getWidth() || drawable.getSurfaceHeight()!=getHeight()) {
				double mag=1.0, xoffset=0, yoffset=0;
				if(ratio>1.0) {
					mag=(double)drawable.getSurfaceHeight()/getHeight();
					xoffset=((double)drawable.getSurfaceWidth()-(double)getWidth()*mag)/2.0;
				}
				if(ratio<=1.0) {
					mag=(double)drawable.getSurfaceWidth()/getWidth();
					yoffset=((double)drawable.getSurfaceHeight()-(double)getHeight()*mag)/2.0;
				}
				Rectangle2D.Double adjRect=null;
				if(!IJ.isMacOSX())adjRect=new Rectangle2D.Double(-xoffset,-yoffset,1/mag,1/mag);
				else adjRect=new Rectangle2D.Double(-xoffset,-yoffset,1.0,1.0);
				if(JCP.debug) log("adjRect "+adjRect);
				joglEventAdapter.setAdjRect(adjRect);
			}else joglEventAdapter.setAdjRect(null);
		}
		
		if(JCP.debug) {
			log("OIC Size:  w"+super.getSize().width+" h"+super.getSize().height+" srcRect w"+srcRect.width+" h"+srcRect.height);
			Rectangle b=icc.getBounds();
			log("NCA Size:  x"+b.x+" y"+b.y+" w"+b.getWidth()+" h"+b.getHeight());
			if(isMirror) {Insets ins=mirror.getInsets(); log("Mirror Insets: tb"+(ins.top+ins.bottom)+" lr"+(ins.left+ins.right));}
			log("Drbl size w"+drawable.getSurfaceWidth()+" h"+drawable.getSurfaceHeight());
			com.jogamp.nativewindow.util.Rectangle gb=glw.getBounds();
			log("glw size  x"+gb.getX()+" y"+gb.getY()+"w"+gb.getWidth()+" h"+gb.getHeight()+" sw"+glw.getSurfaceWidth()+" sh"+glw.getSurfaceHeight());
			int[] vps=glos.getViewport();
			//if(dpimag>1.0)
			log("VPS: "+vps[0]+" "+vps[1]+" "+vps[2]+" "+vps[3]);
		}
	}
	
	private Rectangle getCBViewportAspectRectangle(int x, int y, int width, int height) {
			int w=width,h=height;
			double aspect=(double)srcRect.width*CB_MAXSIZE/(double)srcRect.height;
			if(width<(int)((double)height*aspect+0.5))h=(int)(width/aspect+0.5);
			else if(height<(int)(width/aspect+0.5))w=(int)(height*aspect+0.5);
			return new Rectangle((width-w)/2, (int)(((height/CB_MAXSIZE)-h)/2*CB_MAXSIZE), w, (int)(h*CB_MAXSIZE+0.5));
	}
	
	/**
	 * @param rat ratio of ratios: drawable w/h : srcRect w/h
	 */
	private void resetGlobalMatrices(GLAutoDrawable drawable) {
		double ratio=1.0;
		if(drawable!=null)ratio=((double)drawable.getSurfaceWidth()/drawable.getSurfaceHeight())/((double)srcRect.width/srcRect.height);
		else ratio=((double)glw.getSurfaceWidth()/glw.getSurfaceHeight())/((double)srcRect.width/srcRect.height);
		float sx=1f, sy=1f;
		if(ratio>1.0f) sx=(float)(1.0/ratio);  else sy=(float)ratio;
		//float znear=zNear, zfar=zFar;
		//if(one3Dslice) {znear=-2/(float)imageWidth; zfar=-znear;}
		float[] viewmatrix=FloatUtil.makeOrtho(new float[16], 0, false, -1f/sx, 1f/sx, -1f/sy, 1f/sy, zNear/sx, zFar/sx);
		
		//clear "projection" matrix
		glos.getUniformBuffer("global").loadIdentity();
		//set the second, "view" matrix to the ratio scale
		//float[] viewmatrix=new float[] {
		//		sx, 0, 0, 0,
		//		0, sy, 0, 0,
		//		0, 0, -sx, 0,
		//		0, 0, 0, 1f
		//	};
		glos.getUniformBuffer("global").loadMatrix(viewmatrix, 16*Buffers.SIZEOF_FLOAT);
		if(go3d) {
			float xrat=1f, yrat=1f;
			if(ratio>1.0)xrat=(float)ratio;else yrat=(float)ratio;
			float nearZ = zNear-JCP.zNear+1f, depthZ=zFar-JCP.zNear+1f, IOD=JCP.stereoSep, g_initial_fov=(float)Math.toRadians(fov);
			double frustumshift = (IOD/2)*nearZ/depthZ;
			float ftop = (float)Math.tan(g_initial_fov/2)*nearZ;
			float fbottom = -ftop;
			for(int i=0;i<3;i++) {
				String bname="stereo-left-view";
				float left_right_direction = -1.0f;
				if(i==1) {
					bname="stereo-right-view";
					left_right_direction = 1.0f;
				}else if(i==2){
					bname="3d-view";
					left_right_direction = 0f;
				}
				float fright =(float)(xrat*ftop+frustumshift*left_right_direction);
				float fleft =-fright;
				float[] g_projection_matrix=null;
				if(JCP.doFrustum && !one3Dslice) {
					g_projection_matrix = FloatUtil.makeFrustum(new float[16], 0, false, fleft, fright, fbottom/yrat, ftop/yrat, nearZ, depthZ);
					frustumZshift=-1.35f*(zmax+1f);//-1f-(3.33f*zmax);
				} else {
					//g_projection_matrix=FloatUtil.makeIdentity(new float[16]);
					g_projection_matrix=viewmatrix;
					frustumZshift=1f;
					//if(one3Dslice)frustumZshift=1f;
				}
			  // update the view matrix
				float[] eye=new float[] {left_right_direction*IOD/2, 0, 1};
				float[] center=new float[] {0, 0, -1f};
				float[] up=new float[] {0,1,0};
				float[] g_view_matrix = FloatUtil.makeLookAt(new float[16], 0, eye, 0, center, 0, up, 0, new float[16]);
				glos.getUniformBuffer(bname).loadMatrix(g_projection_matrix, 0);
				glos.getUniformBuffer(bname).loadMatrix(g_view_matrix, 16*Buffers.SIZEOF_FLOAT);
			}
		}
	}

	@Override
	public void dispose(GLAutoDrawable drawable) {
		log("Disposing GL Canvas");
		System.out.println("Disposing ajs-----------------------");
		glw.setPointerVisible(true);
		java.awt.EventQueue.invokeLater(new Runnable() {
			public void run() {
					if(jccpDialog!=null)jccpDialog.dispose();
					if(jcgDialog!=null)jcgDialog.dispose();
					if(jcrDialog!=null)jcrDialog.dispose();
			}
		});
		glos.setGL(drawable);
		glos.dispose();
		imp.unlock();
	}
	
	public static void log(String string) {
		java.awt.EventQueue.invokeLater(new Runnable() {
			@Override
			public void run() {
				if(JCP.quiet && !JCP.debug)IJ.showStatus(string);
				else IJ.log(string);
			}
		});
	}

	@Override
	public void display(GLAutoDrawable drawable) {
		if(imp==null) {revert(); return;}
		if(imp.isLocked()) {if(JCP.debug)log("imp.lock "+System.currentTimeMillis());return;}
		//imp.lockSilently(); //causing z scrollbar to lose focus?
		if(getPaintPending()) {if(JCP.debug)log("mylock "+System.currentTimeMillis());return;};
		setPaintPending(true);
		long displaytime = 0;
		if(JCP.debug && verbose) {
			displaytime=System.nanoTime();
			//log("\\Update2:Display took: "+(System.nanoTime()-starttime)/1000000L+"ms");
			log("\\Update0:Display start took: "+String.format("%5.1f", (float)(System.nanoTime()-dragtime)/1000000f)+"ms");
		}
		int sl=imp.getZ()-1, fr=imp.getT()-1,chs=imp.getNChannels(),sls=imp.getNSlices(),frms=imp.getNFrames();
		Calibration cal=imp.getCalibration();
		int srcRectWidthMag = (int)(srcRect.width*magnification+0.5);
		int srcRectHeightMag = (int)(srcRect.height*magnification+0.5);
		if(go3d&&sls==1)go3d=false;
		imageState.check(dx,dy,dz);
		boolean needDraw=false;
		if(imageState.isChanged.srcRect) resetGlobalMatrices(drawable);
		
		glos.setGL(drawable);
		int maxsize=glos.getMaxTextureSize(true);
		int bigsize=Math.max(imp.getWidth(), imp.getHeight());
		if(go3d && maxsize<bigsize) {
			while(maxsize<bigsize) {
				bigsize/=2;
				undersample*=2;
			}
		}
		sb.setPixelType(go3d?pixelType3d:getPixelType(), go3d?undersample:1);
		
		if(go3d && stereoUpdated) {
			if(JCP.debug)log("stereoUpdate reshape");
			//if(stereoType==StereoType.ANAGLYPH ) {
			//	if(!glos.textures.containsKey("anaglyph"))initAnaglyph();
			//}
			resetGlobalMatrices(drawable);
			stereoUpdated=false;
			needDraw=true;
		}
		if(threeDupdated || deletePBOs) {
			if(go3d) {
				if(JCP.debug)log("threeDupdated");
				init3dTex();
				glos.getTexture("image3d").initiate(pixelType3d, sb.bufferWidth, sb.bufferHeight, sls, 1);
				ltr=null;
			}else {
				glos.getUniformBuffer("model").loadIdentity();
				//resetGlobalMatrices();
				glos.getTexture("image2d").initiate(getPixelType(), sb.bufferWidth, sb.bufferHeight, 1, 1);
			}
			imageState.isChanged.srcRect=true;
			threeDupdated=false;
			needDraw=true;
		}
		
		//Roi and Overlay if not drawn with gl (create roi textures from imageplus)
		Roi roi=imp.getRoi();
		ij.gui.Overlay overlay=getOverlay();
		boolean doRoi=false;
		boolean[] doOv=null;
		if(!JCP.openglroi) {
			//&& (roi!=null || (!go3d && overlay!=null)) && (!isPoint || (isPoint && !go3d))) {
			if(!go3d) {
				BufferedImage roiImage;
				Graphics g;
				if(roi!=null || overlay!=null) {
					roiImage=new BufferedImage(srcRectWidthMag, srcRectHeightMag, BufferedImage.TYPE_INT_ARGB);
					g=roiImage.getGraphics();
					if(roi!=null) {roi.draw(g); doRoi=true;}
					if(overlay!=null) {
						for(int i=0;i<overlay.size();i++) {
							Roi oroi=overlay.get(i);
							oroi.setImage(imp);
							int rc=oroi.getCPosition(), rz=oroi.getZPosition(),rt=oroi.getTPosition();
							if((rc==0||rc==imp.getC()) && (rz==0||rz==imp.getZ()) && (rt==0||rt==imp.getT())) {oroi.drawOverlay(g); doRoi=true;}
						}
					}
					if(doRoi)glos.getTexture("roiGraphic").createRgbaTexture(AWTTextureIO.newTextureData(glos.getGLProfile(), roiImage, false).getBuffer(), roiImage.getWidth(), roiImage.getHeight(), 1, 4, false);
				}
			}else{   // if(!JCP.openglroi && (overlay!=null || isPoint) && go3d) 
				doOv=new boolean[sls];
				if(!glos.textures.containsKey("overlay") || glos.getTexture("overlay").getTextureLength()!=sls) {
					glos.newTexture("overlay",sls, false);
					glos.newArrayBuffer("overlay");
					glos.newElementBuffer("overlay");
					glos.newVao("overlay");
				}
				boolean isPoint=(roi instanceof PointRoi);
				boolean didpt=false;
				for(int osl=0;osl<sls;osl++) {
					BufferedImage roiImage=null;
					Graphics g=null;
					if(roi!=null && !isPoint && sl==osl) {
						if(g==null) {
							roiImage=new BufferedImage(srcRectWidthMag, srcRectHeightMag, BufferedImage.TYPE_INT_ARGB);
							g=roiImage.getGraphics();
						}
						roi.draw(g); doOv[osl]=true;
					}
					if(overlay!=null) {
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
					}
					if(isPoint) {
						if(g==null) {
							roiImage=new BufferedImage(srcRectWidthMag, srcRectHeightMag, BufferedImage.TYPE_INT_ARGB);
							g=roiImage.getGraphics();
						}
						for(int i=0;i<roi.getPolygon().npoints;i++) {
							int pos=((PointRoi)roi).getPointPosition(i);
							if(pos==imp.getStackIndex(imp.getC(), osl+1, fr+1)) {
								imp.setSliceWithoutUpdate(imp.getStackIndex(imp.getC(), osl+1, fr+1));
								roi.draw(g);
								doOv[osl]=true; didpt=true;
								break;
							}
						}
					}
					if(doOv[osl]) {
						glos.getTexture("overlay").createRgbaTexture(osl, AWTTextureIO.newTextureData(glos.getGLProfile(), roiImage, false).getBuffer(), roiImage.getWidth(), roiImage.getHeight(), 1, 4, false);
					}
				}
				if(isPoint && didpt)imp.setSliceWithoutUpdate(imp.getStackIndex(imp.getC(), sl+1, fr+1));
			}
		}
		
		//make sure image PBO is set up
		if(glos.getPboLength("image")!=chs*(sb.isFrameStack?1:frms) || deletePBOs) {
			glos.newPbo("image", chs*(sb.isFrameStack?1:frms));
			sb.resetSlices();
			deletePBOs=false;
		}
		
		
		//Update Image PBO and texture if init or image changed----
		
		//log("miu:"+myImageUpdated+" sb.r:"+(myImageUpdated&& !imageState.isChanged.czt && !imageState.isChanged.minmax && !scbrAdjusting)+" "+imageState);
		if(myImageUpdated) {
			if(!imageState.isChanged.czt && !imageState.isChanged.minmax && !scbrAdjusting) {
				sb.resetSlices();
			}
			if(go3d) {
				for(int i=0;i<chs;i++){ 
					for(int ifr=0;ifr<frms;ifr++) {
						for(int isl=0;isl<sls;isl++) {
							if(!sb.isSliceUpdated(isl, ifr)) {
								glos.getPbo("image").updateSubRgbaPBO(ifr*chs+i, sb.getSliceBuffer(i+1, isl+1, ifr+1),0, isl*sb.sliceSize, sb.sliceSize, sb.bufferSize);
								if(i==(chs-1)) {sb.updateSlice(isl,ifr);}
							}
						}
					}
				}
				
			}else {
				int cfr=sb.isFrameStack?0:fr;
				if(JCP.usePBOforSlices) {
					if(!sb.isSliceUpdated(sl,fr)) {
						try {
							for(int i=0;i<chs;i++) {
								int ccfr=cfr*chs+i;
								glos.getPbo("image").updateSubRgbaPBO(ccfr, sb.getSliceBuffer(i+1, sl+1, fr+1),0, (sb.isFrameStack?fr:sl)*sb.sliceSize, sb.sliceSize, sb.bufferSize);
								sb.updateSlice(sl, fr);
							}
						}catch(Exception e) {
							if(e instanceof GLException) {
								GLException gle=(GLException)e;
								log(gle.getMessage());
								log("Out of memory, switching usePBOforSlices off");
								JCP.usePBOforSlices=false;
								sb.resetSlices();
								glos.disposePbo("image");
								glos.newPbo("image", chs*(sb.isFrameStack?1:frms));
							}
						}
					}
					for(int i=0;i<chs;i++) {
						int ccfr=cfr*chs+i;
						glos.loadTexFromPbo("image", ccfr, "image2d", i, sb.bufferWidth, sb.bufferHeight, 1, sb.isFrameStack?fr:sl, getPixelType(), COMPS, false, Prefs.interpolateScaledImages);
					}
				}else {
					for(int i=0;i<chs;i++) {
						glos.getTexture("image2d").createRgbaTexture(i, sb.getSliceBuffer(i+1, sl+1, fr+1), sb.bufferWidth, sb.bufferHeight, 1, COMPS, Prefs.interpolateScaledImages);
					}
				}
			}
		}
		
		if(go3d) {
			if(imageState.isChanged.t || myImageUpdated) {
				if(JCP.debug)log("t changed or myImageUpdated");
				needDraw=true;
				for(int i=0;i<chs;i++) {
					int ccfr=fr*chs+i;
					glos.loadTexFromPbo("image", ccfr, "image3d", i, sb.bufferWidth, sb.bufferHeight, sls, 0, pixelType3d, COMPS, false, Prefs.interpolateScaledImages);
				}
			}
			if((supermag+magnification)<=0)supermag=0f-(float)magnification;
			if((supermag+magnification)>24)supermag=24f-(float)magnification;
		}
		myImageUpdated=false;
		if(imageState.isChanged.minmax) needDraw=true;
		
		//setluts and threshold
		ByteBuffer lutMatrixPointer=glos.getDirectBuffer(GL2GL3.GL_UNIFORM_BUFFER, "luts");
		((Buffer)lutMatrixPointer).rewind();
		LUT[] luts=imp.getLuts();
		boolean[] active=new boolean[chs];
		for(int i=0;i<chs;i++)active[i]=true;
		if(imp.isComposite())active=((CompositeImage)imp).getActiveChannels();
		int cmode=imp.getCompositeMode();
		int bitd=imp.getBitDepth();
		double topmax=Math.pow(2, bitd==24?8:bitd)-1.0;
		for(int i=0;i<6;i++) {
			float min=0,max=0,color=0,tmin=0,tmax=0,lutType=0;
			if(luts==null || luts.length==0 ||bitd==24) {
				max=1f;
				color=i==0?8:0;//(i==0?1:i==1?2:i==2?4:0);
			}else {
				if(i<luts.length) {
					int rgb=luts[i].getRGB(255);
					boolean inv=false;
					if((rgb & 0x00ffffff)==0) {
						if((luts[i].getRGB(0)&0x00ffffff)>0)rgb=luts[i].getRGB(0);
						inv=true;
					}
					if(active[i] && (cmode==IJ.COMPOSITE || imp.getC()==(i+1))) {
						if(cmode==IJ.GRAYSCALE)color=7;
						else color=(((rgb & 0x00ff0000)==0x00ff0000)?1:0) + (((rgb & 0x0000ff00)==0x0000ff00)?2:0) + (((rgb & 0x000000ff)==0x000000ff)?4:0);
						if(imp.isThreshold()&& (chs==1 || cmode==IJ.GRAYSCALE || cmode==IJ.COLOR) && imp.getProcessor().getLutUpdateMode()!=ImageProcessor.NO_LUT_UPDATE){
							color=9f;
							ImageProcessor ip=imp.getProcessor();
							tmin=(float)(ip.getMinThreshold()/topmax);
							tmax=(float)(ip.getMaxThreshold()/topmax);
							lutType=(float)(10+imp.getProcessor().getLutUpdateMode());
						}
					}
					if(bitd<32) {
						min=(float)(Math.round(luts[i].min)/topmax);
						max=(float)(Math.round(luts[i].max)/topmax);
					}else {
						if(sb.minmaxs==null) {
							min=(float)luts[i].min;
							max=(float)luts[i].max;
						}else{
							double or=sb.minmaxs[i].max-sb.minmaxs[i].min;
							max=(float)((luts[i].max-sb.minmaxs[i].min)/or);
							min=(float)((luts[i].min-sb.minmaxs[i].min)/or);
						}
					}
					if(min==max) {if(min==0){max+=(1/topmax);}else{min-=(1/topmax);}}
					if(inv) {float temp=max; max=min; min=temp;}
				}
			}
			lutMatrixPointer.putFloat(i*4*Float.BYTES,min);
			lutMatrixPointer.putFloat((i*4+1)*Float.BYTES,max);
			lutMatrixPointer.putFloat((i*4+2)*Float.BYTES,color);
			lutMatrixPointer.putFloat((i*4+3)*Float.BYTES,(gamma==null || gamma.length<=i)?0f:gamma[i]);
			lutMatrixPointer.putFloat((i+6)*4*Float.BYTES,tmin);
			lutMatrixPointer.putFloat(((i+6)*4+1)*Float.BYTES,tmax);
			lutMatrixPointer.putFloat(((i+6)*4+2)*Float.BYTES,lutType);
			lutMatrixPointer.putFloat(((i+6)*4+3)*Float.BYTES,0f); //padding
		}
		((Buffer)lutMatrixPointer).rewind();
		
		if( (imageState.isChanged.srcRect) || go3d) {
			if(JCP.debug && imageState.isChanged.srcRect)log("Dsp- imageState.isChanged.srcRect");
			//Set up model matrix
			float 	trX=-((float)(srcRect.x*2+srcRect.width)/imageWidth-1f),
					trY=((float)(srcRect.y*2+srcRect.height)/imageHeight-1f),
					scX=(float)imageWidth/srcRect.width+(go3d?supermag:0f),
					scY=(float)imageHeight/srcRect.height+(go3d?supermag:0f);
			float[] translate=null,scale=null,rotate=null;
			if(tx>2.0f)tx=2.0f; if(tx<-2.0f)tx=-2.0f;
			if(ty>2.0f)ty=2.0f; if(ty<-2.0f)ty=-2.0f;
			if(tz>2.0f)tz=2.0f; if(tz<-2.0f)tz=-2.0f;
			translate=FloatUtil.makeTranslation(new float[16], false, trX+(go3d?tx:0f), trY+(go3d?ty:0f), go3d?tz:0f);
			scale=FloatUtil.makeScale(new float[16], false, scX, scY, scX);
			if(!go3d)
				glos.getUniformBuffer("model").loadMatrix(FloatUtil.multMatrix(scale, translate));//note this modifies scale
		//}
		
			if(imageState.isChanged.srcRect || imageState.isChanged.rotation) needDraw=true;
			
			//Rotation-Translation-Scale Model Matrix
			//and order of drawing slices depending on rotation
			if(go3d) {
				//Set up matricies
				rotate=FloatUtil.makeRotationEuler(new float[16], 0, dy*FloatUtil.PI/180f, dx*FloatUtil.PI/180f, dz*FloatUtil.PI/180f);
				//log("\\Update0:X x"+Math.round(100.0*matrix[0])/100.0+" y"+Math.round(100.0*matrix[1])/100.0+" z"+Math.round(100.0*matrix[2])/100.0);
				//log("\\Update1:Y x"+Math.round(100.0*matrix[4])/100.0+" y"+Math.round(100.0*matrix[5])/100.0+" z"+Math.round(100.0*matrix[6])/100.0);
				//log("\\Update2:Z x"+Math.round(100.0*matrix[8])/100.0+" y"+Math.round(100.0*matrix[9])/100.0+" z"+Math.round(100.0*matrix[10])/100.0);
				//log(FloatUtil.matrixToString(null, "rot: ", "%10.4f", rotate, 0, 4, 4, false).toString());
				
				boolean left,top,reverse;
				float Xza=Math.abs(rotate[2]), Yza=Math.abs(rotate[6]), Zza=Math.abs(rotate[10]);
				float maxZvec=Math.max(Xza, Math.max(Yza, Zza));
				left=(maxZvec==Xza);
				top=(maxZvec==Yza);
				reverse=(Zza==rotate[10]);
				if(left)reverse=Xza==rotate[2];
				if(top)reverse=Yza==rotate[6];
				float[] modelTransform=new float[16];
				if(imageWidth!=imageHeight) {
					float ratio=(float)imageWidth/(float)imageHeight;
					modelTransform=FloatUtil.makeScale(modelTransform, false, ((imageWidth>imageHeight)?1.0f:ratio), ((imageWidth>imageHeight)?1.0f/ratio:1.0f), 1.0f); //scale aspect ratio
					float[] temptrans=FloatUtil.multMatrix(modelTransform, translate, new float[16]);
					float[] temprot=FloatUtil.multMatrix(rotate, temptrans, new float[16]);
					modelTransform=FloatUtil.multMatrix(FloatUtil.makeScale(new float[16], false, ((imageWidth>imageHeight)?1.0f:1.f/ratio), ((imageWidth>imageHeight)?ratio:1.0f), 1.0f), temprot, new float[16]);
					modelTransform=FloatUtil.multMatrix(scale, modelTransform, new float[16]);
				}else
					FloatUtil.multMatrix(scale, FloatUtil.multMatrix(rotate, translate, new float[16]), modelTransform);
				modelTransform=FloatUtil.multMatrix(FloatUtil.makeTranslation(new float[16], false, 0, 0, frustumZshift), modelTransform);
				glos.getUniformBuffer("model").loadMatrix(modelTransform);
				
				//roi model matrix
				scX=(float)imageWidth/srcRect.width;
				scY=(float)imageHeight/srcRect.height;
				if(tx!=0 || ty!=0 || tz!=0)FloatUtil.multMatrix(rotate,FloatUtil.makeTranslation(new float[16], false, tx*scX, ty*scY, tz*scX));
				if(supermag!=0f) {
					float tsm=(scX+supermag)/scX;
					rotate=FloatUtil.multMatrix(FloatUtil.makeScale(new float[16], false, tsm, tsm, tsm), rotate);
				}
				rotate=FloatUtil.multMatrix(FloatUtil.makeTranslation(new float[16], false, 0, 0, frustumZshift), rotate);
				glos.getUniformBuffer("modelr").loadMatrix(rotate);
				
				if(ltr==null || !(ltr[0]==left && ltr[1]==top && ltr[2]==reverse) || cutPlanes.changed /*|| imageState.isChanged.srcRect*/) {
					needDraw=true;
					cutPlanes.changed=false;
					double zrat=cal.pixelDepth/cal.pixelWidth;
					int zmaxsls=(int)(zrat*(double)sls);
					zmax=(float)(zmaxsls)/(float)imageWidth;
					final float[] initVerts=cutPlanes.getInitCoords();
					//For display of the square, there are 3 space verts and 3 texture verts
					//for each of the 4 points of the square.
					ByteBuffer vertb=glos.getDirectBuffer(GL2GL3.GL_ARRAY_BUFFER, "image3d");
					((Buffer)vertb).clear();
					lim=0;
					if(left) { //left or right
						for(float p=cutPlanes.x();p<cutPlanes.w();p+=1.0f) {
							float xt,xv, pn;
							if(reverse) pn=p;
							else pn=cutPlanes.w()-(p-cutPlanes.x()+1.0f);
							xt=(pn+0.5f)/imageWidth*cutPlanes.twm;
							xv=xt*2f-1f;
							for(int i=0;i<4;i++) {
								vertb.putFloat(xv); vertb.putFloat(initVerts[i*6+1]); vertb.putFloat(initVerts[i*6+2]);
								vertb.putFloat(xt); vertb.putFloat(initVerts[i*6+4]); vertb.putFloat(initVerts[i*6+5]);
								lim+=6;
							}
						}
					} else if(top) { //top or bottom
						for(float p=cutPlanes.y();p<cutPlanes.h();p+=1.0f) {
							float yt,yv,pn;
							if(!reverse) pn=p;
							else pn=cutPlanes.h()-(p-cutPlanes.y()+1.0f);
							yt=(pn+0.5f)/imageHeight*cutPlanes.thm;
							yv=1f-yt*2f;
							for(int i=0;i<4;i++) {
								float zv=initVerts[i*6+2];
								float zt=initVerts[i*6+5];
								if(i==1) {zv=initVerts[2]; zt=initVerts[5];}
								else if(i==3) {zv=initVerts[8]; zt=initVerts[11];}
								vertb.putFloat(initVerts[i*6]); vertb.putFloat(yv); vertb.putFloat(zv);
								vertb.putFloat(initVerts[i*6+3]); vertb.putFloat(yt); vertb.putFloat(zt);
								lim+=6;
							}
						}
					}else { //front or back
						int zr=(int)(cutPlanes.z()*zrat), dr=(int)(cutPlanes.d()*zrat);
						for(int csl=zr;csl<dr;csl++) {
							int z=csl;
							if(reverse) z=dr-(csl-zr+1);
							//float cz=((float)z-0.5f*(float)zrat)/zmaxsls;
							float cz=((float)z)/(float)zmaxsls;
							for(int i=0;i<4;i++) {
								vertb.putFloat(initVerts[i*6]); vertb.putFloat(initVerts[i*6+1]); vertb.putFloat((float)(zmaxsls-2*z)/imageWidth); 
								vertb.putFloat(initVerts[i*6+3]); vertb.putFloat(initVerts[i*6+4]); vertb.putFloat(cz); 
								lim+=6;
							}
						}
					}
					((Buffer)vertb).limit(lim*Buffers.SIZEOF_FLOAT);
				}
				ltr=new boolean[] {left,top,reverse};	
			}
		}else 
			lim=24;

		// GL draw init----
		
		//Potential stereo diffs
		int views=1;
		if(go3d && stereoType.ordinal()>0)views=2;
		glos.glDrawBuffer(GL2GL3.GL_BACK);
		glos.clearColorDepth();

		int width=drawable.getSurfaceWidth(), height=drawable.getSurfaceHeight();
		for(int stereoi=0;stereoi<views;stereoi++) {
			
			//Projection matrix
			String bname="global";
			
			if(go3d) {
				
				//Projection matrix 3d
				bname="3d-view";
				if(stereoType.ordinal()>0) {
					if(stereoi==0)bname="stereo-left-view";
					else bname="stereo-right-view";
				}
				
				if(needDraw) {
					glos.drawToTexture("anaglyph", stereoi, width, height, stereoFramebuffers[0], stereoRenderbuffers[0], pixelType3d);
					glos.clearColorDepth();
					
					//Blend
					glos.setGLrenderFunction(renderFunction);
					
					//drawing the 3d volume
					glos.drawTexVaoWithProgramBuffers("image3d", lim/4, chs, false, "image", new String[] {bname,"model","luts"});

				}
			}else {
				glos.glDrawBuffer(GL2GL3.GL_BACK);
				//int[] vps=glos.getViewport();
				//glos.drawToTexture("anaglyph", 0, imp.getWidth(), imp.getHeight(), stereoFramebuffers[0], stereoRenderbuffers[0], getPixelType(imp));
				//glos.glViewport(0, 0, imp.getWidth(), imp.getHeight());
				glos.clearColorDepth();
				
				glos.glDisable(GL2GL3.GL_BLEND);
				//drawing the whole image with lut processesing to anaglyph texture, using no projection or model transformations
				//to correct for min/max
				glos.drawTexVaoWithProgramBuffers("image2d", lim/4, chs, false, "image2d", new String[] {"global", "model","luts"});
				
				//draw
				//glos.stopDrawingToTexture();
				//glos.glViewport(vps[0], vps[1], vps[2], vps[3]);
				//glos.glDisable(GL2GL3.GL_BLEND);
				//glos.drawTexVaoWithProgramBuffers("anaglyph", 6, 1, Prefs.interpolateScaledImages, "roi", new String[] {bname, "model"});
			}

			//Draw roi and overlay and cursor crosshairs
			//
			if(go3d) {
				glos.drawToTexture("temp", stereoi, width, height, tempFramebuffers[0], tempRenderbuffers[0], pixelType3d);
				glos.clearColorDepth();
			}
			boolean drawCrosshairs=(JCP.drawCrosshairs>0 && oicp!=null  && (isMirror || go3d));
			if(roi!=null || overlay!=null || drawCrosshairs) { 
				//if(go3d)log(FloatUtil.matrixToString(null, "rot2: ", "%10.4f", rotate, 0, 4, 4, false).toString());
				float z=0f;
				float zf=(float)(cal.pixelDepth/cal.pixelWidth)/(float)srcRect.width;
				if(go3d) z=((float)sls-2f*sl)*zf;
				glos.setGLrenderFunction("ALPHA");
				if(!JCP.openglroi) {
					if(doRoi || doOv!=null) {
						glos.useProgram("roi");
						glos.bindUniformBuffer(bname, 1);
						glos.bindUniformBuffer(go3d?"modelr":"idm", 2);
						if(doOv!=null) {
							for(int osl=0;osl<sls;osl++) {
								if(doOv[osl] && (!cutPlanes.applyToRoi || (osl>=cutPlanes.z() && osl<cutPlanes.d())) ) {
									float zc=((float)sls-2f*(float)osl)*zf;
									drawGraphics(zc, "overlay", osl);
								}
							}
						}
						if(doRoi)
							drawGraphics(z, "roiGraphic", 0);
						glos.stopProgram();
						glos.unBindUniformBuffer(1);
						glos.unBindUniformBuffer(2);
					}
				}
				if(JCP.openglroi || drawCrosshairs){
					Color anacolor=null;
					if(stereoType==StereoType.ANAGLYPH && go3d) {
						if(JCP.dubois)anacolor=(stereoi==0)?Color.RED:Color.CYAN;
						else anacolor=(stereoi==0)?JCP.leftAnaglyphColor:JCP.rightAnaglyphColor;
					}
					glos.glDisable(GL2GL3.GL_BLEND);
					//if(glos.glver==2)rgldu.startDrawing();
					//glos.bindUniformBuffer(bname, 1);
					//glos.bindUniformBuffer(go3d?"modelr":"idm", 2);
					rgldu.setBuffers(glos.getUniformBuffer(bname),glos.getUniformBuffer(go3d?"modelr":"idm"));
					if(JCP.openglroi) {
						if(overlay!=null) {
							for(int i=0;i<overlay.size();i++) {
								rgldu.drawRoiGL(drawable, overlay.get(i), true, anacolor, go3d);
							}
						}
						if(roi!=null)
							rgldu.drawRoiGL(drawable, roi, false, anacolor, go3d);
					}
					if(drawCrosshairs) {
						float left=-1f, right=1f, top=-1f, bottom=1f, front=sls*zf, back=(-sls+2)*zf, oix=(float)screenXD(oicp.getX()), oiy=(float)screenYD(oicp.getY());
						oix=(oix+0.5f)/dstWidth*2f-1f; oiy=(dstHeight-oiy-0.5f)/dstHeight*2f-1f;
						if(JCP.drawCrosshairs==1 || (isMirror & !go3d)) {
							float wpx=13f*2f/dstWidth, hpx=13f*2f/dstHeight;
							left=oix-wpx; right=oix+wpx; top=oiy+hpx; bottom=oiy-hpx; front=Math.min(z+wpx,front); back=Math.max(z-wpx,back);
							//constrain
							//left=Math.max(oix-wpx,-1f); right=Math.min(oix+wpx,1f); top=Math.min(1f,oiy+hpx); bottom=Math.max(-1f,oiy-hpx); front=Math.min(z+wpx,front); back=Math.max(z-wpx,back);
						}
						Color c=anacolor!=null?anacolor:Color.white;
						rgldu.drawGL(new float[] {left,oiy, z, right,oiy,z}, c, GL.GL_LINE_STRIP);
						rgldu.drawGL(new float[] {oix,bottom, z, oix, top, z}, c, GL.GL_LINE_STRIP);
						if(go3d)rgldu.drawGL(new float[] {oix, oiy, front, oix, oiy, back}, c, GL.GL_LINE_STRIP);
					}

					glos.unBindUniformBuffer(1);
					glos.unBindUniformBuffer(2);
				}
			}
			
			//draw zoom indicator
			boolean nzi=(!myHZI && (srcRect.width<imageWidth || srcRect.height<imageHeight));
			if(nzi) {
				drawMyZoomIndicator(drawable, bname);
			}
			//log("\\Update0:Display took: "+(System.nanoTime()-starttime)/1000000L+"ms");
			glos.finish();
		} //stereoi
		
		if(go3d) {
			glos.stopDrawingToTexture();
			
			glos.setGLrenderFunction("MAX");
			String program="roi";
			if(stereoType==StereoType.ANAGLYPH) program="anaglyph";
			
			for(int stereoi=0;stereoi<views;stereoi++) {
				String globalmat="globalidm";
				if(stereoType==StereoType.ANAGLYPH) {
					glos.setAnaglyphColor(JCP.anaColors[stereoi], JCP.dubois);
				}else {
					if(stereoType==StereoType.QUADBUFFER) {
						if(stereoi==0)glos.getGL2GL3().glDrawBuffer(GL2GL3.GL_BACK_LEFT);
						else glos.getGL2GL3().glDrawBuffer(GL2GL3.GL_BACK_RIGHT);
						glos.clearColorDepth();
					}
					if(stereoType==StereoType.CARDBOARD) {
						if(stereoi==0)globalmat="CB-left";
						else globalmat="CB-right";
					}else if(stereoType==StereoType.HSBS) {
						if(stereoi==0)globalmat="HSBS-left";
						else globalmat="HSBS-right";
					}
				}
				glos.useProgram(program);
				glos.bindUniformBuffer(globalmat, 1);
				glos.bindUniformBuffer("idm", 2);
				glos.drawTexVao("anaglyph",stereoi, GL2GL3.GL_UNSIGNED_BYTE, 6, 1, false); 
				glos.drawTexVao("temp",stereoi, GL2GL3.GL_UNSIGNED_BYTE, 6, 1, false); 
				glos.unBindUniformBuffer(1);
				glos.unBindUniformBuffer(2);
				glos.finish();
			}
			glos.stopProgram();
		}
		
		if(JCP.debug && verbose) {
			log("\\Update1:Display took: "+String.format("%5.1f", (float)(System.nanoTime()-displaytime)/1000000f)+"ms");
		}
		
		if(imageUpdated) {imageUpdated=false;} //ImageCanvas imageupdated only for single ImagePlus
		imageState.update(dx,dy,dz);
		
		if(screengrabber!=null) {
			if(screengrabber.isReadyForUpdate()) {
				BufferedImage bi=grabScreen(drawable);
				screengrabber.screenUpdated(bi);
			}
		}
		setPaintPending(false);
	}
	
	public void setGamma(float[] gamma) {
		this.gamma=gamma;
		repaint();
	}
	public float[] getGamma() {return gamma;}

	public void updateCutPlanesCube(int[] c) {
		if(c==null || c.length<6)return;
		int i=0;
		if(cutPlanes.x==c[i++] &&
		cutPlanes.y==c[i++] &&
		cutPlanes.z==c[i++] &&
		cutPlanes.w==c[i++] &&
		cutPlanes.h==c[i++] &&
		cutPlanes.d==c[i++])return;
		cutPlanes.updateCube(c);
		repaint();
	}
	
	public void setCutPlanesApplyToRoi(boolean update) {cutPlanes.applyToRoi=update; repaint();}
	
	public CutPlanesCube getCutPlanesCube() {
		CutPlanesCube c=cutPlanes;
		return new CutPlanesCube(c.x(),c.y(),c.z(),c.w(),c.h(),c.d(), c.applyToRoi);
	}
	
	class CutPlanesCube{
		private int x,y,z,w,h,d;
		public boolean applyToRoi=true;
		public boolean changed=true;
		private float[] initCoords=null, screenCoords=null;
		private float vx,vy,vz,vw,vh,vd,tx,ty,tz,tw,th,td;
		public float twm, thm;
		
		public CutPlanesCube(int x, int y, int z, int width, int height, int depth, boolean applyToRoi) {
			this.x=x; this.y=y; this.z=z;
			this.w=width; this.h=height; this.d=depth;
			this.applyToRoi=applyToRoi;
			twm=(2*imageWidth-tex4div(imageWidth))/(float)imageWidth;
			thm=(2*imageHeight-tex4div(imageHeight))/(float)imageHeight;
			updateCoords();
		}

		public int x() {return x;}
		public int y() {return y;}
		public int z() {return z;}
		public int w() {return w;}
		public int h() {return h;}
		public int d() {return d;}
		
		public void updateCube(int[] xyzwhd) {
			if(xyzwhd==null || xyzwhd.length<6)return;
			x=xyzwhd[0]; y=xyzwhd[1]; z=xyzwhd[2];
			w=xyzwhd[3]; h=xyzwhd[4]; d=xyzwhd[5];
			changed=true;
			initCoords=null;
			screenCoords=null;
			updateCoords();
		}
		
		private void updateCoords() {
			vx=(float)x/imp.getWidth()*2f-1f; vy=(float)y/imp.getHeight()*2f-1f; vz=-((float)z/imp.getNSlices()*2f-1f);
			vw=(float)w/imp.getWidth()*2f-1f; vh=(float)h/imp.getHeight()*2f-1f; vd=-((float)d/imp.getNSlices()*2f-1f);
			tx=(float)x/imp.getWidth()*twm; ty=(float)y/imp.getHeight()*thm; tz=(float)z/imp.getNSlices();
			tw=(float)w/imp.getWidth()*twm; th=(float)h/imp.getHeight()*thm; td=(float)d/imp.getNSlices();
		}
		
		public float[] getVertCoords() {
			return new float[] {vx,vy,vz,vw,vh,vd};
		}
		
		public float[] getInitCoords() {
			if(initCoords!=null) {return initCoords;}
			initCoords=new float[] {
					vx, -vh, vd*zmax,   tx, th, td,
					vw, -vh, vz*zmax,   tw, th, tz,
					vw, -vy, vz*zmax,   tw, ty, tz,
					vx, -vy, vd*zmax,   tx, ty, td
			};
			return initCoords;
		}
		
		public float[] getScreenCoords() {
			if(screenCoords!=null)return screenCoords;
			float mag=(float)magnification;
			float srw=(float)srcRect.width, srh=(float)srcRect.height;
			float offx=(float)srcRect.x, offy=(float)srcRect.y;
			float dw=(int)(mag*srw+0.5), dh=(int)(mag*srh+0.5);
			float glx=((x-offx)/srw+0.5f/dw)*2f-1f, gly=(((srh-(y-offy))/srh-0.5f/dh)*2f-1f);
			if(glx<-1f)glx=-1f; if(glx>1f)glx=1f; if(gly<-1f)gly=-1f; if(gly>1f)gly=1f;
			float glw=((w-offx)/srw+0.5f/dw)*2f-1f, glh=(((srh-(h-offy))/srh-0.5f/dh)*2f-1f);
			if(glw<-1f)glw=-1f; if(glw>1f)glw=1f; if(glh<-1f)glh=-1f; if(glh>1f)glh=1f;
			float tx=(glx+1f)/2f*twm, ty=(gly+1f)/2f*thm, tw=(glw+1f)/2f*twm, th=(glh+1f)/2f*thm;
			screenCoords=new float[] {
					glx, gly, 0f, tx, ty, 0.5f,
					glw, gly, 0f, tw, ty, 0.5f,
					glw, glh, 0f, tw, th, 0.5f,
					glx, glh, 0f, tx, th, 0.5f,
			};
			return screenCoords;
		}
	}
	
	static class ImageState{
		private final ImagePlus imp;
		private final JOGLImageCanvas jic;
		public Rectangle prevSrcRect;
		public double prevMag;
		public MinMax[] minmaxs;
		public Calibration prevCal;
		public int c,z,t;
		public float odx, ody, odz;
		public IsChanged isChanged=new IsChanged();
		public boolean setNextSrcRect=false;
		
		public ImageState(ImagePlus imp, JOGLImageCanvas jic) {
			this.imp=imp;
			this.jic=jic;
			update(0f,0f,0f);
		}
		
		public void update(float dx, float dy, float dz) {
			prevSrcRect=(Rectangle)jic.getSrcRect().clone();
			prevMag=jic.getMagnification();
			minmaxs=MinMax.getMinMaxs(imp.getLuts());
			prevCal=(Calibration)imp.getCalibration().clone();
			c=imp.getC(); z=imp.getZ(); t=imp.getT();
			odx=dx; ody=dy; odz=dz;
			reset();
		}
		
		public void check(float dx, float dy, float dz) {
			isChanged.srcRect= (!prevSrcRect.equals(jic.getSrcRect())) || setNextSrcRect || prevMag!=jic.getMagnification();
			MinMax[] newMinmaxs=MinMax.getMinMaxs(imp.getLuts());
			isChanged.minmax=false;
			for(int i=0;i<minmaxs.length;i++) {
				if(newMinmaxs[i].min!=minmaxs[i].min || newMinmaxs[i].max!=minmaxs[i].max)isChanged.minmax=true;
			}
			isChanged.cal=!prevCal.equals(imp.getCalibration());
			isChanged.c=(c!=imp.getC());
			isChanged.z=(z!=imp.getZ());
			isChanged.t=(t!=imp.getT());
			isChanged.czt=(isChanged.c || isChanged.z || isChanged.t);
			isChanged.slice=!((c==imp.getC()||imp.getCompositeMode()==IJ.COMPOSITE) && z==imp.getSlice() && t==imp.getFrame());
			isChanged.rotation=!(dx==odx && dy==ody && dz==odz);
		}
		
		public void reset() {
			isChanged.reset();
			setNextSrcRect=false;
		}
		
		static class IsChanged{
			public boolean c,z,t,czt,cal,minmax,srcRect, slice, rotation;
			public void reset() {
				slice=srcRect=minmax=cal=czt=c=z=t=rotation=false;
			}
		}
		
		public String toString() {
			return ("isChanged: c:"+isChanged.c+" z:"+isChanged.z+" t:"+isChanged.t+" cal:"+isChanged.cal+" minmax:"+isChanged.minmax+" srcRect:"+isChanged.srcRect+" slice:"+isChanged.slice+" rotation:"+isChanged.rotation);
		}
	}
	
	public BufferedImage grabScreen(GLAutoDrawable drawable) {
		int[] vps=glos.getViewport();
		int width=vps[2], height=vps[3];
		int x=0,y=0;
		if(stereoType==StereoType.CARDBOARD) {
			y=(int)((1f-(1f/CB_MAXSIZE))*(float)srcRect.height/(float)srcRect.width/2f*(float)height);
			height/=CB_MAXSIZE;
		}
		return glos.grabScreen(x, y, width, height);
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
	
	public static int tex4div(int wh) {
		return JCP.tex4?(wh+((wh%4)>0?(4-wh%4):0)):wh;
	}
	
	public void set3dPixelType(PixelType newtype) {
		if(pixelType3d==newtype)return;
		int bits=imp.getBitDepth();
		if(bits==24)bits=8;
		if(newtype==PixelType.FLOAT && bits<32) {IJ.error("Not enough image bits for float display pixel");return;}
		if((newtype==PixelType.SHORT || newtype==PixelType.INT_RGB10A2) && (bits<16)) {IJ.error("Not enough image bits for high bit display pixel");return;}
		pixelType3d=newtype;
		resetBuffers();
	}
	
	private int getCurrent3dPixelType() {
		for(int i=0;i<PixelType.values().length;i++)if(pixelType3d==PixelType.values()[i])return i;
		return -1;
	}
	
	private void drawGraphics(float z, String name, int index) {
		FloatBuffer vb;
		if(go3d && cutPlanes.applyToRoi) {
			final float[] iv=cutPlanes.getScreenCoords();
			vb=GLBuffers.newDirectFloatBuffer(new float[] {
					iv[0],	iv[1],	z, 	iv[3],  1f-iv[4],  0.5f,
					iv[6],	iv[7],	z, 	iv[9],  1f-iv[10], 0.5f,
					iv[12],	iv[13],	z, 	iv[15], 1f-iv[16], 0.5f,
					iv[18],	iv[19],	z,	iv[21], 1f-iv[22], 0.5f
			});
		}else {
			vb=GLBuffers.newDirectFloatBuffer(new float[] {
					-1,	-1,	z, 	0, 1, 0.5f,
					 1,	-1,	z, 	1, 1, 0.5f,
					 1,	 1,	z, 	1, 0, 0.5f,
					-1,	 1,	z,	0, 0, 0.5f
			});
		}
		//glos.useProgram("roi");
		glos.drawGraphics(name, index, vb);
		//glos.stopProgram();
	}


	private PixelType getPixelType() {
		return getPixelType(imp);
	}
	
	public static PixelType getPixelType(ImagePlus imp) {
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
		set3d(newboo, true);
	}

	public void set3d(boolean newboo, boolean repaint) {
		if(imp.getNSlices()==1)newboo=false;
		if(go3d==newboo)return;
		threeDupdated=true;
		myImageUpdated=true;
		go3d=newboo;
		disablePopupMenu=go3d;
		if(repaint)repaint();
	}
	
	public void setStereo(StereoType stereoTypeChoice) {
		if(stereoTypeChoice==StereoType.QUADBUFFER && !stereoEnabled) {
			if(IJ.showMessageWithCancel("QuadBuffer","Stereo must be enabled in preferences before \n creating JOGLcanvas to use quadbuffer. \n Activate and restart now?")) {
				JCP.preferStereo=true;
				revert();
				java.awt.EventQueue.invokeLater(new Runnable() {
					@Override
					public void run() {
						JOGLImageCanvas jic=JCP.convertToJOGLCanvas(imp,isMirror);
						jic.set3d(true,false);
						jic.setStereo(StereoType.QUADBUFFER);
					}
				});
			}
			return;
		}
		stereoType=stereoTypeChoice;
		if(!(stereoType==StereoType.OFF))set3d(true, false);
		stereoUpdated=true;
		myImageUpdated=true;
		if(JCP.qbfullscreen && stereoType==StereoType.QUADBUFFER)setFullscreen(true);
		repaint();
	}
	
	class JCLayout implements LayoutManager{

		@Override
		public void addLayoutComponent(String name, Component comp) {
		}
		@Override
		public void removeLayoutComponent(Component comp) {
		}
		@Override
		public Dimension preferredLayoutSize(Container parent) {
			Dimension dim=new Dimension(0,0);
			Insets ins=parent.getInsets();
			Dimension cdim=parent.getComponent(0).getSize();
			dim.width+=ins.left+ins.right+cdim.width;
			dim.height+=ins.top+ins.bottom+cdim.height;
			return dim;
		}
		@Override
		public Dimension minimumLayoutSize(Container parent) {
			return preferredLayoutSize(parent);
		}
		@Override
		public void layoutContainer(Container parent) {
			parent.getComponent(0).setLocation(parent.getInsets().left, parent.getInsets().top);
			Dimension dim=parent.getSize();
			Insets ins=parent.getInsets();
			parent.getComponent(0).setSize(dim.width-ins.left-ins.right, dim.height-ins.top-ins.bottom);
			if(JCP.debug)IJ.log("JCLayout move "+parent.getComponent(0));
		}
		
	}
	
	private void createMirror() {
		isMirror=true;
		mirror=new Frame("JOGL-DC3D Mirror of "+imp.getTitle());
		mirror.setLayout(new JCLayout());
		mirror.add(icc);
		WindowAdapter wl=new WindowAdapter() {
			public void windowClosing(WindowEvent e) { if(JCP.debug)log("revertting"); revert(); }
		};
		mirror.addWindowListener(wl);
		joglEventAdapter.addMouseWheelListener(new MouseAdapter() {
			@Override
			public void mouseWheelMoved(MouseWheelEvent e) {
				ImageWindow win=imp.getWindow();
				if(win==null)return;
				setCursor(e.getX(), e.getY(), offScreenX(e.getX()), offScreenY(e.getY()));
				win.mouseWheelMoved(e);
				repaintLater();
			}
		});
		addKeyListener(new KeyAdapter() {
			@Override
			public void keyReleased(KeyEvent e) {
				repaintLater();
			}
		});
		java.awt.EventQueue.invokeLater(new Runnable() {
			@Override
			public void run() {
				mirror.pack();
				mirror.setVisible(true);
				IJ.wait(200);
		        mirror.toFront();
			}
		});
	}
	
	//private void updateMirror() {
		//if(!isMirror)return;
		//if(imp==null) {dispose();return;}
		//if(mirror==null || !mirror.isVisible() || glw.isFullscreen())return;
		//if(!mirrorMagUnlock) {
		//	int glww=glw.getSurfaceWidth(), glwh=glw.getSurfaceHeight();
		//	int w=getWidth(), h=getHeight();
		//	int wm=(int)(w*dpimag+0.5), hm=(int)(h*dpimag+0.5);
		//	if(glww!=wm || glwh!=hm)setMirrorSize(w,h);
		//}
	//}
	
	public void setMirrorMagUnlock(boolean set) {
		mirrorMagUnlock=set;
		if(!mirrorMagUnlock)setMirrorSize(getWidth(),getHeight());
	}
	
	public boolean toggleFullscreen() {
		return setFullscreen(!glw.isFullscreen());
	}
	
	public boolean setFullscreen(boolean dofs) {
		glw.setUndecorated(dofs);
		if(dofs) {
			ArrayList<MonitorDevice> ml=new ArrayList<MonitorDevice>();
			if(JCP.debug) for(MonitorDevice md : glw.getScreen().getMonitorDevices())log("MD:"+md);
			java.util.List<MonitorDevice> mds=glw.getScreen().getMonitorDevices();
			if(mds.size()==1) {
				ml.add(mds.get(0));
			}else{
				ij.gui.GenericDialog gd=new ij.gui.GenericDialog("Monitors");
				for(int i=0;i<mds.size();i++) gd.addCheckbox("Monitor "+(i+1), i==0?true:false);
				gd.showDialog();
				if(gd.wasCanceled())return !dofs;
				for(int i=0;i<mds.size();i++)if(gd.getNextBoolean())ml.add(mds.get(i));
			}
			//ml.add(glw.getMainMonitor());
			java.awt.EventQueue.invokeLater(new Runnable() {
				public void run() {glw.setFullscreen(ml);}
			});
		}else glw.setFullscreen(false);
		return dofs;
	}
	
	public void dispose() {
		glw.destroy();
		if(mirror!=null)mirror.dispose();
		mirror=null;
	}
	
	public void revert() {
		//showUpdateButton(false);
		if(imp==null || imp.getWindow()==null || imp.getWindow().isClosed()) {
			dispose();
			return;
		}
		if(!isMirror) {
			glw.destroy();
		}else{
			joglEventAdapter.removeMouseMotionListener(this);
			joglEventAdapter.removeMouseListener(this);
		}
		if(imp!=null)JCP.switchWindow(imp, new ImageCanvas(imp));
		if(isMirror) {
			java.awt.EventQueue.invokeLater(new Runnable() {
				public void run() {
					if(JCP.debug)log("post new stackwindow, before GLW destroy");
					if(glw!=null)glw.destroy();
					if(JCP.debug)log("after GLW destroy");
					if(mirror!=null)mirror.dispose();
					if(JCP.debug)log("after mirror dispose");
					mirror=null;
				}
			});
		}
	}
	
	public void setRenderFunction(String function) {
		if(function.equals("MAX") || function.equals("ALPHA"))renderFunction=function;
		repaint();
	}


	//Overridden ImageCanvas Methods
		
	//adapted from drawZoomIndicator() in ImageCanvas
	void drawMyZoomIndicator(GLAutoDrawable drawable, String projMatrix) {
		if(myHZI) return;
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
		float w=(float)(int)(srcRect.width*magnification+0.5);
		float h=(float)(int)(srcRect.height*magnification+0.5);
		x1=x1/w*2f-1f; y1=((h-y1)/h*2f-1f);
		w1=w1/w*2f; h1=h1/h*2f;
		x2=x2/w*2f; y2=y2/h*2f;
		w2=w2/w*2f; h2=h2/h*2f;
		
		((Buffer)zoomIndVerts[0]).rewind();
		float[] color=new float[] {(float)128/255, (float)128/255, 1f, 1f};
		zoomIndVerts[0].put(x1).put(y1).put(0f).put(color);
		zoomIndVerts[0].put(x1+w1).put(y1).put(0f).put(color);
		zoomIndVerts[0].put(x1+w1).put(y1-h1).put(0f).put(color);
		zoomIndVerts[0].put(x1).put(y1-h1).put(0f).put(color);
		((Buffer)zoomIndVerts[0]).rewind();
		
		((Buffer)zoomIndVerts[1]).rewind();
		zoomIndVerts[1].put(x1+x2).put(y1-y2).put(0f).put(color);
		zoomIndVerts[1].put(x1+x2+w2).put(y1-y2).put(0f).put(color);
		zoomIndVerts[1].put(x1+x2+w2).put(y1-y2-h2).put(0f).put(color);
		zoomIndVerts[1].put(x1+x2).put(y1-y2-h2).put(0f).put(color);
		((Buffer)zoomIndVerts[1]).rewind();

		glos.glDisable(GL2GL3.GL_BLEND);
		drawable.getGL().glLineWidth((float)dpimag);
		if(glos.glver==2)rgldu.startDrawing();
		glos.bindUniformBuffer(projMatrix, 1);
		glos.bindUniformBuffer("idm", 2);
		rgldu.drawGLfb(drawable, zoomIndVerts[0], GL2GL3.GL_LINE_LOOP);
		rgldu.drawGLfb(drawable, zoomIndVerts[1], GL2GL3.GL_LINE_LOOP);
		glos.unBindBuffer(GL2GL3.GL_UNIFORM_BUFFER, 1);
		glos.unBindBuffer(GL2GL3.GL_UNIFORM_BUFFER, 2);
		drawable.getGL().glLineWidth(1f);
		
	}

	/*Called in super()*/
	@Override
	public void setSize(int width, int height) {
		if(width==getWidth() && height==getHeight() && icc.getWidth()!=0 && icc.getHeight()!=0)return;
		if(icc!=null) {
			if(JCP.debug)log("SetSize w:"+width+" h:"+height);
			if(isMirror) {
				super.setSize(width, height);
				if(!mirrorMagUnlock)setMirrorSize(width, height);
			}else icc.setSize(width, height);
			dstWidth = width;
			dstHeight = height;
		}else super.setSize(width, height);
	}
	
	public void setMirrorSize(int width, int height) {
		if(mirror.getExtendedState()!=Frame.MAXIMIZED_BOTH) {
			if(icc.getWidth()==width && icc.getHeight()==height)return;
			if(JCP.debug)log("sms icc w"+icc.getWidth()+" h"+icc.getHeight()+" -> w"+width+" h"+height);
			java.awt.Insets ins=mirror.getInsets();
			java.awt.EventQueue.invokeLater(new Runnable() {public void run() {
				mirror.setSize(width+ins.left+ins.right,height+ins.top+ins.bottom);
				if(JCP.debug)IJ.log("setMirrorSize done");
			}});
		}
	}
	
	@Override
	public void setLocation(int x, int y) {
		//if(JCP.debug)IJ.log("setLocation x"+x+" y"+y+" dpi"+dpimag);
		if(icc!=null && !isMirror) {
			int xd=(int)(x*dpimag+0.5), yd=(int)(y*dpimag+0.5);
			icc.setLocation(xd, yd);
			//float ssc[]=new float[2];glw.getCurrentSurfaceScale(ssc);
			//xd=(int)(x*ssc[0]+0.5); yd=(int)(y*ssc[0]+0.5);
			//if(IJ.isMacintosh())glw.setPosition(xd-x, yd-y);
		}
		else super.setLocation(x, y);
	}

	@Override
	public void setSize(Dimension newsize) {
		setSize(newsize.width,newsize.height);
	}

	@Override
	public void repaint() {
		if(icc!=null) {
			//updateMirror();
			icc.repaint();
		}
		else if(glos.isGLset()) setPaintPending(false);
		if(isMirror) {
			if(!imp.isHyperStack())imp.updateImage();
			super.repaint();
		}
	}
	
	@Override
	public void paint(Graphics g) {
		if(isMirror) {
			super.paint(g);
			if(onScreenMirrorCursor && oicp!=null) {
				g.setColor(Color.red);
				g.drawRect(screenXD(oicp.x)-1,screenYD(oicp.y)-1,3,3);
			}
		}
	}
	
	public void repaintLater() {
		java.awt.EventQueue.invokeLater(new Runnable() {
			@Override
			public void run() {
				repaint();
			}
		});
	}

	@Override
	public void repaint(int x,int y,int width,int height) {
		if(icc!=null) {
			//updateMirror();
			repaintLater();
		}
		if(isMirror) {
			super.repaint(x,y,width,height);
		}
	}

	@Override
	public Point getLocation() {
		if(icc!=null && !isMirror)return icc.getLocation();
		else return super.getLocation();
	}

	@Override
	public Rectangle getBounds() {
		if(icc!=null && !isMirror)return icc.getBounds();
		else return super.getBounds();
	}

	@Override
	public void setCursor(Cursor cursor) {
		if(icc!=null && !isMirror)icc.setCursor(cursor);
		if(isMirror || icc==null) super.setCursor(cursor);
	}

	/* called in super() disable so I don't have to remove them*/
	@Override
	public void addMouseListener(MouseListener object) {
		if(joglEventAdapter!=null && !isMirror)joglEventAdapter.addMouseListener(object);
		super.addMouseListener(object);
	}
	@Override
	public void addMouseMotionListener(MouseMotionListener object) {
		if(joglEventAdapter!=null && !isMirror)joglEventAdapter.addMouseMotionListener(object);
		super.addMouseMotionListener(object);
	}
	@Override
	public void addMouseWheelListener(MouseWheelListener object) {
		if(JCP.debug)IJ.log("adding mouse wheel listener to "+object+" and jea is null?"+(joglEventAdapter==null)+" mir"+isMirror);
		if(joglEventAdapter!=null && !isMirror)joglEventAdapter.addMouseWheelListener(object);
		super.addMouseWheelListener(object);
	}
	@Override
	public void addKeyListener(KeyListener object) {
		if(joglEventAdapter!=null && !isMirror)joglEventAdapter.addKeyListener(object);
		super.addKeyListener(object);
	}
	
	/*just in case*/
	@Override
	public void removeMouseListener(MouseListener object) {
		if(joglEventAdapter!=null && !isMirror)joglEventAdapter.removeMouseListener(object);
		else super.removeMouseListener(object);
	}
	@Override
	public void removeMouseMotionListener(MouseMotionListener object) {
		if(joglEventAdapter!=null && !isMirror)joglEventAdapter.removeMouseMotionListener(object);
		else super.removeMouseMotionListener(object);
	}
	@Override
	public void removeKeyListener(KeyListener object) {
		if(joglEventAdapter!=null && !isMirror)joglEventAdapter.removeKeyListener(object);
		else super.removeKeyListener(object);
	}
	

	@Override
	public void requestFocus() {
		if(icc!=null && !isMirror)icc.requestFocus();
		if(isMirror) super.requestFocus();
	}
	@Override
	public Container getParent() {
		if(icc!=null && !isMirror)return (imp.getWindow());
		else return super.getParent();
	}
	//@Override
	//public Graphics getGraphics() {
	//	if(icc!=null)return icc.getGraphics();
	//	else return super.getGraphics();
	//}
	@Override
	public Dimension getSize() {
		if(icc!=null && !isMirror)return icc.getSize();
		else return super.getSize();
	}
	@Override
	public Dimension getPreferredSize() {
		if(icc!=null && !isMirror)return icc.getPreferredSize();
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
		if(icc!=null && !isMirror)icc.add(popup);
		else super.add(popup);
	}
	
	public void add(JPopupMenu popup) {
		if(icc!=null && !isMirror)getParent().add(popup);
	}
	
	/** Adapted from ImageCanvas, but shows JOGLCanvas popupmenu*/
	@Override
	protected void handlePopupMenu(MouseEvent e) {
		if(icc==null || e.getSource()==this)super.handlePopupMenu(e);
		else {
			//if (disablePopupMenu) return;
			glw.setPointerVisible(true);
			if (IJ.debugMode) log("show popup: " + (e.isPopupTrigger()?"true":"false"));
			Roi roi = imp.getRoi();
			if (roi!=null && roi.getState()==Roi.CONSTRUCTING) {
				return;
			}

			if (dcpopup!=null) {
				icc.add(dcpopup);
				dcpopup.show(icc,joglEventAdapter.getDejustedX(e.getX()),joglEventAdapter.getDejustedY(e.getY()));
			}
			//boolean drawCrosshairs=(JCP.drawCrosshairs>0 && oicp!=null  && (isMirror || go3d));
			//glw.setPointerVisible(!drawCrosshairs);
		}
	}

	/** Disable/enable popup menu. */
	@Override
	public void disablePopupMenu(boolean status) {
		disablePopupMenu = status;
	}
	
	/*
	 * Popup Menu functions
	 */
	
	public void createPopupMenu() {
		if(dcpopup==null) {
			dcpopup=new PopupMenu("JOGLCanvas Options");
			
			Menu threeDmenu=new Menu("3d Options");
			if(imp.getNSlices()==1) threeDmenu.setEnabled(false);
			mi3d=addMI(threeDmenu,go3d?"Turn 3d off":"Turn 3d on","3d",imp.getNSlices()>1);
			
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

			addMI(threeDmenu,"Update 3d Image","update");
			addMI(threeDmenu,"Reset 3d view","reset3d");
			
			menu=new Menu("Adjustments");
			addMI(menu,"Adjust Cut Planes","adjust3d");
			addMI(menu,"Adjust Contrast/Gamma","gamma");
			addMI(menu,"Adjust Rot-Trans","rottrans");
			threeDmenu.add(menu);
			
			menu=new Menu("Stereoscopic 3d");
			StereoType[] sts=StereoType.values();
			for(int i=0;i<sts.length;i++) {
				addMI(menu,sts[i].getDescription(),sts[i].getDescription());
			}
			threeDmenu.add(menu);
			addMI(threeDmenu, "Save image or movie", "Recorder");
			
			dcpopup.add(threeDmenu);
			
			//menu=new Menu("Normal Pixel Type");
			//for(int i=0;i<pixelTypeStrings.length;i++) addCMI(menu,pixelTypeStrings[i],pixelType==PixelType.values()[i]);
			//dcpopup.add(menu);

			//addMI(dcpopup, "Switch use PBO for Slices", "usePBOforSlices");
			//addMI(dcpopup, "Override GUI dpi", "guidpi");
			menu=new Menu("Other Options");
			addCMI(menu, "Fullscreen", !(glw==null || !glw.isFullscreen()));
			if(isMirror)addCMI(menu, "Resizable Mirror", mirrorMagUnlock);
			if(isMirror)addMI(menu, "Toggle Left Click 3d Control", "rightclick");
			if(imp.getNSlices()==1)addMI(menu,"Adjust Contrast/Gamma","gamma");
			addMI(menu, "JOGL Canvas Preferences", "prefs");
			addMI(menu, "Revert to Normal Window", "revert");
			dcpopup.add(menu);
			Object ijpopup=JCP.getIJPopupMenu();
			if(ijpopup instanceof PopupMenu) {
				PopupMenu popup=(PopupMenu)ijpopup;
				popup.setLabel("ImageJ Popup Menu");
				dcpopup.add(popup);
			}
		}
	}
	
	private MenuItem addMI(Menu menu, String label, String cmd) {
		return addMI(menu, label, cmd, true);
	}
	
	private MenuItem addMI(Menu menu, String label, String cmd, boolean enabled) {
		MenuItem mi=new MenuItem(label);
		mi.setActionCommand(cmd);
		mi.addActionListener(this);
		mi.setEnabled(enabled);
		menu.add(mi);
		return mi;
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
		else if(cmd.equals("adjust3d")){
			if(jccpDialog==null || !jccpDialog.isVisible()) {jccpDialog=new JCCutPlanes(this); positionDialog(jccpDialog);}
			else jccpDialog.requestFocus();
		}else if(cmd.equals("gamma")){
			if(jcgDialog==null || !jcgDialog.isVisible()) {jcgDialog=new JCBrightness(this); positionDialog(jcgDialog);}
			else jcgDialog.requestFocus();
		}else if(cmd.equals("rottrans")){
			if(jcrDialog==null || !jcrDialog.isVisible()) {jcrDialog=new JCRotator(this); positionDialog(jcrDialog);}
			else jcrDialog.requestFocus();
		}else if(cmd.equals("rightclick")){
			disablePopupMenu(!disablePopupMenu);
		}else if(cmd.equals("prefs")){JCP.preferences();}
		else if(cmd.equals("Recorder")){
			IJ.run("JOGL Canvas Recorder",imp.getTitle());
		}else if(cmd.equals("usePBOforSlices")) {
			if(JCP.usePBOforSlices) {
				log("PBOslices off");
				JCP.usePBOforSlices=false;
			}else {
				log("PBOslices on");
				JCP.usePBOforSlices=true;
			}
		}else if("guidpi".equals(cmd)){
			double tempdpi=IJ.getNumber("Manual set GUI dpi", dpimag);
			if(tempdpi!=IJ.CANCELED)
				setDPImag(tempdpi);
		}else if(((Menu)((MenuItem)e.getSource()).getParent()).getLabel().equals("Stereoscopic 3d")) {
			StereoType stTc=StereoType.getStereoType(cmd);
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
		}else if(((MenuItem)e.getSource()).getLabel().equals("Resizable Mirror")) {
			setMirrorMagUnlock(!mirrorMagUnlock);
			((CheckboxMenuItem)e.getSource()).setState(mirrorMagUnlock);
		}else if(((MenuItem)e.getSource()).getLabel().equals("Fullscreen")) {
			((CheckboxMenuItem)e.getSource()).setState(toggleFullscreen());
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
	
	private void positionDialog(JCAdjuster jca) {
		JCAdjuster[] jcas=new JCAdjuster[] {jccpDialog,jcgDialog,jcrDialog};
		for(int i=0;i<jcas.length;i++) {
			if(jca!=jcas[i] && jcas[i]!=null && jcas[i].isVisible() && jca.getLocation().equals(jcas[i].getLocation())) {
				Point p=jcas[i].getLocation();
				Dimension dim=jcas[i].getSize();
				Point loc=new Point(p.x,p.y+dim.height+5);
				GraphicsEnvironment ge=GraphicsEnvironment.getLocalGraphicsEnvironment();
				GraphicsDevice lstGDs[] = ge.getScreenDevices();
				Rectangle bounds=new Rectangle(0,0,0,0);
		        for (GraphicsDevice gd : lstGDs) {
		            bounds.add(gd.getDefaultConfiguration().getBounds());
		        }
				jca.setLocation(loc);
				Rectangle jbs=jca.getBounds();
				if(!bounds.contains(jbs)){loc.x=Math.min(bounds.width-jbs.width,jbs.x);loc.y=Math.min(bounds.height-jbs.height, jbs.y);jca.setLocation(loc);}
				positionDialog(jca);
				return;
			}
		}
	}
	
	
	/*
	 * ImageListener functions
	 */
	public void imageOpened(ImagePlus imp) {}

	public void imageClosed(ImagePlus imp) {}

	public void imageUpdated(ImagePlus uimp) {
		if(imp.equals(uimp)) {
			myImageUpdated=true;
			if(!go3d) {
				repaintLater();
			}
		}
	}
	
	/**
	 * To use the BIScreenGrabber function. Set to null to stop
	 * the screengrabs.
	 * @param sg
	 */
	public void setBIScreenGrabber(BIScreenGrabber sg) {
		screengrabber=sg;
	}

	/*
	 * Key and mouse events
	 */
	
	/**
	 * Key Events. Taken especially for mirror.
	 */
	@Override
	public void keyPressed(KeyEvent e) {
		if(JCP.debug) System.out.println("AJS- JIC-keyPressed"+Character.toString(e.getKeyChar()));
		//if(glw.isFullscreen()) {
			for(char key : kps.okchars)if(key==Character.toLowerCase(e.getKeyChar())) {
				kps.set(e.getKeyChar()); 
				if(JCP.debug)log("Keypress "+e.getKeyChar());
				return;
			}
			if(kps.keyIsPressed)return;
		//}
		//if(!(e.getKeyChar()=='='||e.getKeyChar()=='-')) ij.keyPressed(e);
		ij.keyPressed(e);
	}
	@Override
	public void keyReleased(KeyEvent e) {
		if(JCP.debug) System.out.println("AJS- JIC-keyReleased"+Character.toString(e.getKeyChar())); 
		char key=e.getKeyChar();
		int code=e.getKeyCode();
		//if(glw.isFullscreen()) {
		if(kps.keyIsPressed && (code==KeyEvent.VK_UP || code==KeyEvent.VK_DOWN || code==KeyEvent.VK_LEFT || code==KeyEvent.VK_RIGHT)) {
			int a=1;
			int b=1;
			if(code==KeyEvent.VK_DOWN)a=-1;
			if(code==KeyEvent.VK_LEFT)b=-1;
			int x=cutPlanes.x(), y=cutPlanes.y(), z=cutPlanes.z(), w=cutPlanes.w(), h=cutPlanes.h(), d=cutPlanes.d();
			switch(kps.key) {
			case 'z':
				z+=a;
				if(z==d)d++;
				break;
			case 'x':
				x+=a*10;
				if(x==w)w++;
				break;
			case 'y':
				y+=a*10;
				if(y==h)h++;
				break;
			case 'w':
				w+=a*10;
				if(w==x)x--;
				break;
			case 'h':
				h+=a*10;
				if(h==y)y--;
				break;
			case 'd':
				d+=a;
				if(d==z)z--;
				break;
			case 'c':
				if(code==KeyEvent.VK_LEFT || code==KeyEvent.VK_RIGHT) {
					imp.setC(imp.getC()+b);
				}else {
					double max=imp.getDisplayRangeMax();
					int c=max>100?100:max>10?10:1;
					imp.setDisplayRange(imp.getDisplayRangeMin(), max+a*c);
				}
				break;
			case 'm':
				double min=imp.getDisplayRangeMin();
				int c=min>100?100:min>10?10:1;
				imp.setDisplayRange(min+a*c, imp.getDisplayRangeMax());
				break;
			}
			if(z<0)z=0; if(y<0)y=0; if(z<0)z=0;
			if(z==imp.getNSlices())z--; if(x==imp.getWidth())x--; if(y==imp.getHeight())y--;
			if(w<1)w=1; if(h<1)h=1; if(d<1)d=1;
			if(w>imp.getWidth())w--; if(h>imp.getHeight())h--; if(d>imp.getNSlices())d--;
			cutPlanes.updateCube(new int[] {x,y,z,w,h,d});
			kps.keyWasUsed();
		}
		if(key=='u') {
			if(go3d) {
				myImageUpdated=true;
			}
		}else if(code==KeyEvent.VK_ESCAPE) {
			if(glw.isFullscreen())toggleFullscreen();
		}else {
			/*if(key=='='||key=='-') {
				Point loc = getCursorLoc();
				if (!cursorOverImage()) {
					loc.x = srcRect.x + srcRect.width/2;
					loc.y = srcRect.y + srcRect.height/2;
				}
				int x = screenX(loc.x);
				int y = screenY(loc.y);
				ImageCanvas ic=isMirror?imp.getCanvas():this;
				if(key=='=')ic.zoomIn(x,y);
				else ic.zoomOut(x,y);
			}*/
			
		}
		for(char kpkey : kps.okchars) {if(kpkey==e.getKeyChar()) {
			if(kps.keyIsPressed) {
				if(!kps.keyWasUsed) ij.keyPressed(e);
				kps.unset(e.getKeyChar()); 
				if(JCP.debug) log("Keypress off: "+e.getKeyChar()); 
			}
		}}
		//}
		repaintLater();
		ij.keyReleased(e);
		ij.keyTyped(e);
	}
	@Override
	public void keyTyped(KeyEvent e) {
	}
	
	class Keypresses{
		public volatile char key;
		public volatile boolean keyIsPressed=false;
		public volatile boolean keyWasUsed=false;
		public final char[] okchars=new char[] {'z','d','x','w','y','h','c','m'};
		
		public Keypresses() {
		}
		
		public void set(char key) {
			if(Character.isAlphabetic(key)) {
				this.key=Character.toLowerCase(key);
				keyIsPressed=true;
				keyWasUsed=false;
			}
		}
		
		public void unset(char key) {
			if(this.key==key) {
				reset();
			}
		}
		
		public void reset() {
			keyIsPressed=false;
			keyWasUsed=false;
		}
		
		public void keyWasUsed() {
			keyWasUsed=true;
		}
	}
	
	private Point2D.Double offScreenD(Point p, double dpifix) {
		if(p==null)return null;
		int x=(int)(p.x/dpifix+0.5);
		int y=(int)(p.y/dpifix+0.5);
		return new Point2D.Double(offScreenXD(x),offScreenYD(y));
	}
	
	public void setImageCursorPosition(MouseEvent e) {
		if(e==null) {oicp=null; return;}
		setImageCursorPosition(offScreenD(e.getPoint(), 1.0));
	}
	
	public void setImageCursorPosition(Point2D.Double p) {
		if(p==null)oicp=null;
		//constrain
		//if(p.x<0)p.x=0; if(p.y<0)p.y=0;
		//if(p.x>imp.getWidth())p.x=imp.getWidth(); if(p.y>imp.getHeight())p.y=imp.getHeight();
		oicp=new Point2D.Double(p.x,p.y);
	}
	
	/**
	 * Test to keep right click or send to super
	 * Adapted from https://stackoverflow.com/questions/2972512/how-to-detect-right-click-event-for-mac-os
	 * @param e
	 * @return
	 */
	private static boolean isRightClick(MouseEvent e) {
	    return (e.getButton()==MouseEvent.BUTTON3 ||
	            (IJ.isMacintosh() &&
	                    (e.getModifiers() & MouseEvent.BUTTON1_MASK) != 0 &&
	                    (e.getModifiers() & MouseEvent.CTRL_MASK) != 0));
	}
	
	private boolean shouldKeep(MouseEvent e) {
		if(e.getSource()==this)return false;
		if(isRightClick(e) && !disablePopupMenu) return false;
		if(isMirror) return go3d&&(isRightClick(e) || !disablePopupMenu);
		return (go3d && ((IJ.getToolName()=="hand" && !IJ.spaceBarDown()) || isRightClick(e)));
	}
	
	@Override
	public void mousePressed(MouseEvent e) {
		//if(go3d && ((e.getModifiersEx() & (MouseEvent.BUTTON1_DOWN_MASK | MouseEvent.BUTTON3_DOWN_MASK))>0))resetAngles();
		if(shouldKeep(e)) {
			sx = e.getX();
			sy = e.getY();
			osx=sx; osy=sy;
		}else {
			if(isMirror && isRightClick(e) && Toolbar.getToolId()!=Toolbar.MAGNIFIER) {handlePopupMenu(e);return;}
			super.mousePressed(e);
			repaintLater();
		}
	}
	
	public void setSuperMag(float m) { if(supermag!=m)imageState.setNextSrcRect=true; supermag=m;}
	public float getSuperMag() {return supermag;}
	
	@Override
	public void mouseDragged(MouseEvent e) {
		if(shouldKeep(e)) {
			mouseDragged=true;
			boolean alt=(e.getModifiersEx() & MouseEvent.ALT_DOWN_MASK)!=0;
			boolean ctrl=(e.getModifiersEx() & (MouseEvent.CTRL_DOWN_MASK | MouseEvent.META_DOWN_MASK))!=0;
			boolean shift=(e.getModifiersEx() & MouseEvent.SHIFT_DOWN_MASK)!=0;
			//if(JCP.debug) {IJ.log("\\Update2:   Drag took: "+String.format("%5.1f", (float)(System.nanoTime()-dragtime)/1000000f)+"ms"); dragtime=System.nanoTime();}
			if(go3d){
				if((e.getX()-osx)==0 && (e.getY()-osy)==0)return;
				float xd=(float)(e.getX()-sx)/(float)srcRect.width;
				float yd=(float)(e.getY()-sy)/(float)srcRect.height;
				if(JCP.debug)log("Dragged "+e.getX()+"x "+e.getY()+"y  sx"+sx+" sy"+sy);
				if(warpPointerWorks) {
					int wx=(int)(joglEventAdapter.getDejustedX(osx)*dpimag/surfaceScale+0.5), wy=(int)(joglEventAdapter.getDejustedY(osy)*dpimag/surfaceScale+0.5);
					glw.warpPointer(wx,wy);
				}else{sx=e.getX(); sy=e.getY();}
				if(alt||e.getButton()==MouseEvent.BUTTON2) {
					if(shift) {
						tz-=yd;
						imageState.setNextSrcRect=true;
					}else dz+=yd*90f;
				}else if(shift) {
					if(ctrl) {
						setSuperMag(supermag-yd*(float)magnification);
					}else {
						tx+=xd;
						ty-=yd;
						imageState.setNextSrcRect=true;
					}
				}else {
					dx+=xd*90f;
					dy+=yd*90f;
				}
				if(dz<0)dz+=360; if(dz>360)dz-=360;
				if(dx<0)dx+=360; if(dx>360)dx-=360;
				if(dy<0)dy+=360; if(dy>360)dy-=360;
			}
		}else {
			if(isMirror && e.getSource()==icc) {
				onScreenMirrorCursor=true;
			}else onScreenMirrorCursor=false;
			boolean drawCrosshairs=(JCP.drawCrosshairs>0 && (isMirror || go3d));
			if(drawCrosshairs) {setImageCursorPosition(e);}
			super.mouseDragged(e);
		}
		repaintLater();
	}
	
	@Override
	public void mouseReleased(MouseEvent e) {
		if(shouldKeep(e)) {
			if(!mouseDragged) {handlePopupMenu(e);return;}
			//if(JCP.drawCrosshairs>0) {
			glw.warpPointer((int)(joglEventAdapter.getDejustedX(osx)*dpimag/surfaceScale+0.5), (int)(joglEventAdapter.getDejustedY(osy)*dpimag/surfaceScale+0.5));
			if(JCP.debug)log("Pointer warped "+joglEventAdapter.getDejustedX(osx)+" "+joglEventAdapter.getDejustedY(osy));
			//}
		}else {
			super.mouseReleased(e);
		}
		mouseDragged=false;
	}
	
	public void resetAngles() {
		dx=0f; dy=0f; dz=0f; tx=0f; ty=0f; tz=0f; supermag=0f;
		repaint();
	}
	
	@Override
	public void mouseMoved(MouseEvent e) {
		//if(JCP.debug)log("\\Update:MouseMoved "+e.getX()+" "+e.getY());
		//if(shouldKeep(e)){
		//	if(JCP.drawCrosshairs>0) {setImageCursorPosition(e, dpimag); repaintLater();}
		//}else {
			//if(dpimag>1.0)e=fixMouseEvent(e);
			if(isMirror && e.getSource()==icc) {
				onScreenMirrorCursor=true;
			}else onScreenMirrorCursor=false;
			boolean drawCrosshairs=(JCP.drawCrosshairs>0  && (isMirror || go3d));
			if(drawCrosshairs) {setImageCursorPosition(e); repaintLater();}
			super.mouseMoved(e);
		//}
	}
	
	@Override
	public void mouseEntered(MouseEvent e) {
		if(e.getSource()==icc && JCP.drawCrosshairs>0 && (isMirror || go3d)) {glw.setPointerVisible(false);}else glw.setPointerVisible(true);
		if(e.getSource()==this)glw.setPointerVisible(true);
		super.mouseEntered(e);
	}
	
	@Override
	public void mouseExited(MouseEvent e) {
		oicp=null;
		glw.setPointerVisible(true);
		super.mouseExited(e);
	}
	
	@Override
	public void mouseClicked(MouseEvent e) {
		if(!shouldKeep(e)){
			//if(dpimag>1.0)e=fixMouseEvent(e);
			super.mouseClicked(e);
		}
	}
	
	/**
	 * For testing or in case one needs the current angles
	 * in degrees caused by dragging the 3d image.
	 * Adding translation in here, too (in -1f to 1f float).
	 * @return float[]{dx, dy, dz, tx, ty, tz}
	 */
	public float[] getEulerAngles() {
		return new float[] {dx,dy,dz,tx,ty,tz};
	}
	
	public void setEulerAngles(float[] eas) {
		if(eas==null || eas.length!=6) {resetAngles(); return;}
		dx=eas[0]; dy=eas[1]; dz=eas[2];
		tx=eas[3]; ty=eas[4]; tz=eas[5];
		imageState.setNextSrcRect=true;
	}
	
	/**
	 * 
	 * @param nearfar
	 */
	public void setNearFar(float[] nearfar) {
		zNear=nearfar[0];
		zFar=nearfar[1];
		resetGlobalMatrices(null);
		imageState.setNextSrcRect=true;
	}
	
	public void setDPImag(double dpi) {
		dpimag=dpi;
		if(joglEventAdapter!=null)joglEventAdapter.setDPI((float)dpi);
		if(rgldu!=null)rgldu.setDPI((float)dpi);
		if(JCP.debug)log("Dpimag set "+dpimag);
	}
	
	public void setStereoUpdated() {
		stereoUpdated=true;
	}
	
	private void addAdjustmentListening() {
		if(imp==null || imp.getWindow()==null) {log("JOGLCanvas was created but no ImagePlus Window was found"); return;}
		if(imp.getWindow().getClass().getSimpleName().equals("ImageWindow"))return; //Don't need for ImageWindow
		StackWindow stwin=(StackWindow) imp.getWindow();
		Component[] comps=stwin.getComponents();
		for(int i=0;i<comps.length;i++) {
			if(comps[i] instanceof ScrollbarWithLabel) {
				ScrollbarWithLabel scr=(ScrollbarWithLabel)comps[i];
				scr.addAdjustmentListener(new AdjustmentListener() {
					@Override
					public void adjustmentValueChanged(AdjustmentEvent e) {
						scbrAdjusting=e.getValueIsAdjusting();
					}
				});
			}
		}
	}
	
	/**
	 * Adds an update button the the window.
	 * The 3D image takes some time to load into memory,
	 * So it is not updated on every draw.  In case the user
	 * changes the image (cut, paste, draw, fill, process),
	 * The user can then press the update button (or type
	 * u) to update the 3d image.
	 * @param show
	 */
	public void showMenuButton(boolean show) {
		boolean nowin=(imp==null || imp.getWindow()==null || !(imp.getWindow() instanceof StackWindow));
		if(menuButton!=null && (!show || nowin)) {
			Container parent=menuButton.getParent();
			if(parent!=null) {parent.remove(menuButton);}
			menuButton=null;
		}
		if(nowin)return;
		StackWindow stwin=(StackWindow) imp.getWindow();
		if(show && menuButton!=null) {
			if(menuButton.getParent()!=null && menuButton.getParent().getParent()==stwin && menuButton.isEnabled())return;
		}
		ScrollbarWithLabel scr=null;
		Component[] comps=stwin.getComponents();
		for(int i=0;i<comps.length;i++) {
			if(comps[i] instanceof ij.gui.ScrollbarWithLabel) {
				scr=(ScrollbarWithLabel)comps[i];
			}
		}
		if(scr!=null) {
			//Remove any orphaned updateButtons, like with a crashed JOGLImageCanvas
			comps=scr.getComponents();
			for(int i=0;i<comps.length;i++) {
				if(comps[i] instanceof Button) {
					String label=((Button)comps[i]).getLabel();
					if(label.equals("GL")) {
						scr.remove(comps[i]);
					}
				}
			}
			
			if(show) {
				menuButton= new Button("GL");
				menuButton.addActionListener(new ActionListener() {
					public void actionPerformed(ActionEvent e) {
						if (dcpopup!=null) {
							menuButton.add(dcpopup);
							dcpopup.show(menuButton,10,10);
						}
					}
				});
				menuButton.setFocusable(false);
				scr.add(menuButton,java.awt.BorderLayout.EAST);
			}
			stwin.pack();
		}
	}
	
	/**
	 * Perhaps this could be integrated with plugins like CLIJ
	 * so that images processed on the GPU could stay on the GPU
	 * for display.
	 * 
	 * Returns an int array of GL PBO handles.  I use one PBO for
	 * every channel and frame, indexed as:
	 * currentFrame*channels+currentChannel
	 * 
	 * Pixels within the pbo are byte, short, float, or int (rgb24), depending on 
	 * the original image, and include all slices.
	 * @return
	 */
	public int[] getPBOnames() {
		return glos.getPbo("image").pbos;
	}
	
	public int getPBOSize() {
		return sb.bufferBytes;
	}
	
	public GLContext getContext() {
		return context;
	}

}