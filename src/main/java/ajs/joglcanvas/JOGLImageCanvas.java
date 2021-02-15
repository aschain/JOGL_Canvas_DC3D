package ajs.joglcanvas;

import ij.CompositeImage;
import ij.IJ;
import ij.ImageListener;
import ij.ImagePlus;
import ij.Prefs;
import ij.gui.ImageCanvas;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.gui.ScrollbarWithLabel;
import ij.gui.StackWindow;
import ij.measure.Calibration;
import ij.process.LUT;
import java.awt.geom.AffineTransform;

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
import java.awt.Image;
import java.awt.Insets;
import java.awt.Menu;
import java.awt.MenuItem;
import java.awt.Point;
import java.awt.PopupMenu;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
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
import com.jogamp.opengl.GL2GL3;
import static com.jogamp.opengl.GL2.*;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.util.texture.awt.AWTTextureIO;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLException;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.util.awt.AWTGLReadBufferUtil;
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
	protected boolean myImageUpdated=true;
	//protected boolean needImageUpdate=false;
	private boolean deletePBOs=false;
	protected boolean isMirror=false;
	public Frame mirror=null;
	private boolean mirrorMagUnlock=false;
	private ImageState imageState;
	private boolean[] ltr=null;

	protected boolean go3d=JCP.go3d;
	public String renderFunction=JCP.renderFunction;
	protected int sx,sy;
	protected float dx=0f,dy=0f,dz=0f, tx=0f, ty=0f, tz=0f, supermag=0f;
	private float[] gamma=null;
	
	private PopupMenu dcpopup=null;
	private MenuItem mi3d=null;
	protected boolean myHZI=false;

	private GL2GL3 gl=null;
	final private FloatBuffer zoomIndVerts=GLBuffers.newDirectFloatBuffer(4*3+4*4);
	private int lim;
	private int undersample=JCP.undersample;
	enum StereoType{OFF, CARDBOARD, ANAGLYPH, QUADBUFFER};
	private final static String[] stereoTypeStrings=new String[] {"Stereo off", "Google Cardboard-SBS","Anaglyph (red-cyan)","OpenGL Quad Buffers"};
	private static final float CB_MAXSIZE=4f;
	private static final float CB_TRANSLATE=0.5f;
	private StereoType stereoType=StereoType.OFF;
	private boolean stereoUpdated=true,threeDupdated=true;
	private int[] stereoFramebuffers=new int[2];
	private boolean mylock=false;

	enum PixelType{BYTE, SHORT, FLOAT, INT_RGB10A2, INT_RGBA8};
	private static final String[] pixelTypeStrings=new String[] {"4 bytes (8bpc, 32bit)","4 shorts (16bpc 64bit)","4 floats (32bpc 128bit)","1 int RGB10A2 (10bpc, 32bit)","1 int RGBA8 (8bpc, 32bit)"};
	protected PixelType pixelType3d=PixelType.BYTE;
	private static final int COMPS=1;
	
	private BIScreenGrabber myscreengrabber=null;
	private AWTGLReadBufferUtil ss=null;
	private RoiGLDrawUtility rgldu=null;
	private boolean scbrAdjusting=false;
	private CutPlanesCube cutPlanes;
	private JCAdjuster jccpDialog,jcgDialog,jcrDialog;
	private boolean verbose=false;
	private long dragtime;
	//private Button updateButton;
	//private long starttime=0;

	public JOGLImageCanvas(ImagePlus imp, boolean mirror) {
		super(imp);
		pixelType3d=getPixelType(imp);
		isMirror=mirror;
		if(!mirror) {setOverlay(imp.getCanvas().getOverlay());}
		imageState=new ImageState(imp);
		imageState.prevSrcRect=new Rectangle(0,0,0,0);
		cutPlanes=new CutPlanesCube(0,0,0,imp.getWidth(), imp.getHeight(), imp.getNSlices(), true);
		GraphicsConfiguration gc=imp.getWindow().getGraphicsConfiguration();
		AffineTransform t=gc.getDefaultTransform();
		dpimag=t.getScaleX();
		if(dpimag!=1.0)IJ.log("GC DPImag: "+dpimag);
		GLCapabilities glc=JCP.getGLCapabilities();
		int bits=imp.getBitDepth();
		if((bits<16 || bits==24)&& (glc.getRedBits()>8 || glc.getGreenBits()>8 || glc.getBlueBits()>8) ) {
			IJ.log("JOGLCanvas Deep Color Warning:\nOriginal image is 8 bits or less and therefore \nwon't display any differently with HDR 10 bits or higher display.");
		}
		if(glc==null) {
			IJ.showMessage("error in GL Capabilities, using default");
			glc=new GLCapabilities(GLProfile.getDefault());
		}
		Display display = NewtFactory.createDisplay(null);
		display.addReference();
		Screen screen=NewtFactory.createScreen(display, 0);
		screen.addReference();
		glw=GLWindow.create(glc);
		icc=new NewtCanvasAWT(glw){
			private static final long serialVersionUID = 1256279205085144008L;
			@Override
			public void reshape(int x, int y, int width, int height) {
				//if(isMirror) super.setSize((int)(width*dpimag+0.5),(int)(height*dpimag+0.5));
				//else { 
					super.reshape(x,y,width, height);
					if(isMirror)
						java.awt.EventQueue.invokeLater(new Runnable() {public void run() {glw.setSize((int)(width*dpimag+0.5),(int)(height*dpimag+0.5));}});
					else
						glw.setSize((int)(width*dpimag+0.5),(int)(height*dpimag+0.5));
					Dimension s=new Dimension(width,height);
					setMinimumSize(s);
					setPreferredSize(s);
				//}
			}
			//@Override
			//public Dimension getSize() {
			//	Dimension s=super.getSize();
			//	if(isMirror) {
			//		s.width=(int)(s.width/dpimag+0.5);
			//		s.height=(int)(s.height/dpimag+0.5);
			//	}
			//	return s;
			//}
		};
		createPopupMenu();
		sb=new StackBuffer(imp);
		final JOGLImageCanvas jic=this;
		new JOGLEventAdapter(jic, glw);
		icc.setPreferredSize(new Dimension(imageWidth,imageHeight));
		glw.addGLEventListener(this);
		icc.setMinimumSize(new Dimension(10,10));
		ImagePlus.addImageListener(this);
		
		if(mirror)createMirror();
		addAdjustmentListening();
	}
	
	private void setGL(GLAutoDrawable drawable) {
		glos.setGL(drawable);
		gl=glos.getGL2GL3();
	}

	//GLEventListener methods
	@Override
	public void init(GLAutoDrawable drawable) {
		JCP.version=drawable.getGL().glGetString(GL_VERSION);
		glos=new JCGLObjects(drawable);
		setGL(drawable);
		
		if(dpimag!=1.0) {
			double pd=dpimag;
			if(dpimag==(double)drawable.getSurfaceWidth()/(double)((int)glw.getWidth())) dpimag=1.0;
			if(dpimag!=pd)IJ.log("new DPImag: "+dpimag);
		}
		
		float[] ssc=new float[2];
		glw.getCurrentSurfaceScale(ssc);
		if(ssc[0]!=1.0f) {
			java.awt.EventQueue.invokeLater(new Runnable() {
				public void run() {IJ.log("SurfaceScale:"+ssc[0]+" "+ssc[1]);}
			});
		}
		if(ssc[0]!=1.0f || (dpimag!=1.0 && !isMirror)) {
			if(ssc[0]==1.0f)ssc[0]=(float)dpimag;
			com.jogamp.newt.event.MouseListener[] mls=glw.getMouseListeners();
			for(com.jogamp.newt.event.MouseListener ml : mls)if(ml instanceof JOGLEventAdapter)((JOGLEventAdapter)ml).setDPI(ssc[0]);
			//if(glw.setSurfaceScale(new float[] {1f,1f}))IJ.log("Changed to 1.0 1.0");
			//else IJ.log("Unable to change SurfaceScale");
		}
		gl.glClearColor(0f, 0f, 0f, 0f);
		gl.glDisable(GL_DEPTH_TEST);
		gl.glDisable(GL_MULTISAMPLE);
		
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
		
		glos.newTexture("image2d", imp.getNChannels());
		glos.newBuffer(GL_ARRAY_BUFFER, "image2d", vertb);
		glos.newBuffer(GL_ELEMENT_ARRAY_BUFFER, "image2d", elementBuffer2d);
		glos.newVao("image2d", 3, GL_FLOAT, 3, GL_FLOAT);
		glos.newProgram("image", "shaders", "texture", "texture");

		glos.newTexture("roiGraphic");
		glos.newBuffer(GL_ARRAY_BUFFER, "roiGraphic");
		glos.newBuffer(GL_ELEMENT_ARRAY_BUFFER, "roiGraphic");
		glos.newVao("roiGraphic", 3, GL_FLOAT, 3, GL_FLOAT);
		glos.newProgram("roi", "shaders", "roiTexture", "roiTexture");

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
		
		glos.newBuffer(GL_UNIFORM_BUFFER, "global", 16*2 * Buffers.SIZEOF_FLOAT, null);
		glos.newBuffer(GL_UNIFORM_BUFFER, "globalidm", 16*2 * Buffers.SIZEOF_FLOAT, aid);
		glos.newBuffer(GL_UNIFORM_BUFFER, "model", 16 * Buffers.SIZEOF_FLOAT, null);
		glos.newBuffer(GL_UNIFORM_BUFFER, "modelr", 16 * Buffers.SIZEOF_FLOAT, null);
		glos.newBuffer(GL_UNIFORM_BUFFER, "lut", 6*4 * Buffers.SIZEOF_FLOAT, null);
		glos.newBuffer(GL_UNIFORM_BUFFER, "idm", 16 * Buffers.SIZEOF_FLOAT, id);

		glos.getUniformBuffer("model").loadIdentity();
		glos.getUniformBuffer("modelr").loadIdentity();
		//global written during reshape call
		
		//int[] pf=new int[1];
		//for(int i=1;i<5;i++) {
		//	JCGLObjects.PixelTypeInfo pti=JCGLObjects.getPixelTypeInfo(getPixelType(),i);
		//	gl.glGetInternalformativ(GL_TEXTURE_3D, pti.glInternalFormat, GL_TEXTURE_IMAGE_FORMAT, 1, pf, 0);
		//	IJ.log("Best in format for comps:"+i+" Int format:"+pti.glInternalFormat+" my form:"+pti.glFormat+" best:"+pf[0]);
		//}
	}
	
	private void init3dTex() {
		Calibration cal=imp.getCalibration();
		long zmaxsls=(long)((double)imp.getNSlices()*cal.pixelDepth/cal.pixelWidth);
		long maxsize=Math.max((long)imp.getWidth(), Math.max((long)imp.getHeight(), zmaxsls));

		ByteBuffer vertb=glos.getDirectBuffer(GL_ARRAY_BUFFER, "image3d");
		int floatsPerVertex=6;
		long vertbSize=maxsize*floatsPerVertex*4*Buffers.SIZEOF_FLOAT;
		if(vertb==null || vertb.capacity()!=(int)vertbSize) {
			int elementsPerSlice=6;
			short[] e=new short[(int)maxsize*elementsPerSlice];
			for(int i=0; i<(maxsize);i++) {
				e[i*6+0]=(short)(i*4+0); e[i*6+1]=(short)(i*4+1); e[i*6+2]=(short)(i*4+2);
				e[i*6+3]=(short)(i*4+2); e[i*6+4]=(short)(i*4+3); e[i*6+5]=(short)(i*4+0);
			}
			ShortBuffer elementBuffer=GLBuffers.newDirectShortBuffer(e);
			elementBuffer.rewind();
	
			glos.newTexture("image3d", imp.getNChannels());
			glos.newBuffer(GL_ARRAY_BUFFER, "image3d", maxsize*floatsPerVertex*4*Buffers.SIZEOF_FLOAT, null);
			glos.newBuffer(GL_ELEMENT_ARRAY_BUFFER, "image3d", elementBuffer.capacity()*Buffers.SIZEOF_SHORT, elementBuffer);
			glos.newVao("image3d", 3, GL_FLOAT, 3, GL_FLOAT);
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
		glos.newTexture("anaglyph");
		glos.newBuffer(GL_ARRAY_BUFFER, "anaglyph", avb);
		glos.newBuffer(GL_ELEMENT_ARRAY_BUFFER, "anaglyph", GLBuffers.newDirectByteBuffer(new byte[] {0,1,2,2,3,0}));
		glos.newVao("anaglyph", 3, GL_FLOAT, 3, GL_FLOAT);
		gl.glGenFramebuffers(1, stereoFramebuffers, 0);
		gl.glGenRenderbuffers(1, stereoFramebuffers, 1);
		
		glos.newProgram("anaglyph", "shaders", "roiTexture", "anaglyph");
		glos.addLocation("anaglyph", "ana");
		glos.addLocation("anaglyph", "dubois");
		
	}
	
	@Override
	public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
		if(JCP.debug) IJ.log("Reshaping:x"+x+" y"+y+" w"+width+" h"+height);
		//width=(int)((double)width*dpimag+0.5);
		//height=(int)((double)height*dpimag+0.5);float rat=1.0f;
		float rat=1.0f;
		if(go3d && stereoType==StereoType.CARDBOARD) {
			Rectangle r=getCBViewportAspectRectangle(x,y,width,height);
			gl.glViewport(r.x, r.y, r.width, r.height);
		}else {
			rat=((float)drawable.getSurfaceWidth()/drawable.getSurfaceHeight())/((float)srcRect.width/srcRect.height);
		}
		resetGlobalMatrices(rat);
		
		if(JCP.debug) {
			IJ.log("OIC Size:  w"+imp.getCanvas().getSize().width+" h"+imp.getCanvas().getSize().height);
			Rectangle b=icc.getBounds();
			IJ.log("NCA Size:  x"+b.x+" y"+b.y+" w"+b.getWidth()+" h"+b.getHeight());
			if(isMirror) {Insets ins=mirror.getInsets(); IJ.log("Insets: tb"+(ins.top+ins.bottom)+" lr"+(ins.left+ins.right));}
			IJ.log("Drbl size w"+drawable.getSurfaceWidth()+" h"+drawable.getSurfaceHeight());
			com.jogamp.nativewindow.util.Rectangle gb=glw.getBounds();
			IJ.log("glw size  x"+gb.getX()+" y"+gb.getY()+"w"+gb.getWidth()+" h"+gb.getHeight());
			int[] vps=new int[4];
			gl.glGetIntegerv(GL_VIEWPORT, vps, 0);
			//if(dpimag>1.0)
			IJ.log("VPS: "+vps[0]+" "+vps[1]+" "+vps[2]+" "+vps[3]);
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
	private void resetGlobalMatrices(float rat) {
		float sx=1f, sy=1f;
		//float ratio = ((float)srcRect.width/srcRect.height)/((float)imageWidth/imageHeight);
		//if(ratio>1.0f)sx/=ratio; else sy*=ratio;
		if(rat>1.0f) sx/=rat;  else sy*=rat;
		//FloatUtil.makeOrtho(new float[16], 0, false, -1f/sx, 1f/sx, -1f/sy, 1f/sy, -1f/sx, 1f/sx);
		glos.getUniformBuffer("global").loadMatrix(new float[] {
				sx, 0, 0, 0,
				0, sy, 0, 0,
				0, 0, -sx, 0,
				0, 0, 0, 1f
			}, 0);
		glos.getUniformBuffer("global").loadIdentity(16*Buffers.SIZEOF_FLOAT);
	}
	
	private void resetGlobalMatrices(GLAutoDrawable drawable) {
		resetGlobalMatrices(((float)drawable.getSurfaceWidth()/drawable.getSurfaceHeight())/((float)srcRect.width/srcRect.height));
	}

	@Override
	public void dispose(GLAutoDrawable drawable) {
		IJ.log("Disposing GL Canvas");
		System.out.println("Disposing ajs-----------------------");
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

	@Override
	public void display(GLAutoDrawable drawable) {
		long displaytime=System.nanoTime();
		if(JCP.debug && verbose) {
			//IJ.log("\\Update2:Display took: "+(System.nanoTime()-starttime)/1000000L+"ms");
			IJ.log("\\Update0:Display start took: "+String.format("%5.1f", (float)(System.nanoTime()-dragtime)/1000000f)+"ms");
		}
		if(imp.isLocked()) {if(JCP.debug)IJ.log("imp.lock "+System.currentTimeMillis());return;}
		//imp.lockSilently(); //causing z scrollbar to lose focus?
		if(mylock) {if(JCP.debug)IJ.log("mylock "+System.currentTimeMillis());return;};
		mylock=true;
		imageState.check();
		if(imageState.isChanged.srcRect)resetGlobalMatrices(drawable);
		if(JCP.openglroi && rgldu==null) rgldu=new RoiGLDrawUtility(imp, drawable,glos.programs.get("roi"));
		int sl=imp.getZ()-1, fr=imp.getT()-1,chs=imp.getNChannels(),sls=imp.getNSlices(),frms=imp.getNFrames();
		Calibration cal=imp.getCalibration();
		if(go3d&&sls==1)go3d=false;
		sb.setPixelType(go3d?pixelType3d:getPixelType(), go3d?undersample:1);
		
		int srcRectWidthMag = (int)(srcRect.width*magnification+0.5);
		int srcRectHeightMag = (int)(srcRect.height*magnification+0.5);
		setGL(drawable);
		
		if(go3d && stereoUpdated) {
			if(JCP.debug)IJ.log("stereoUpdate reshape");
			reshape(drawable,0,0,drawable.getSurfaceWidth(),drawable.getSurfaceHeight());
			if(stereoType==StereoType.ANAGLYPH) {
				if(!glos.textures.containsKey("anaglyph"))initAnaglyph();
			}
			if(stereoType!=StereoType.CARDBOARD)gl.glViewport(0, 0, drawable.getSurfaceWidth(), drawable.getSurfaceHeight());
			stereoUpdated=false;
		}
		if(threeDupdated || deletePBOs) {
			if(go3d) {
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
		}
		
		//Roi and Overlay
		Roi roi=imp.getRoi();
		ij.gui.Overlay overlay=imp.getCanvas().getOverlay();
		boolean doRoi=false;
		boolean[] doOv=null;
		if(!JCP.openglroi) {
			//&& (roi!=null || (!go3d && overlay!=null)) && (!isPoint || (isPoint && !go3d))) {
			if(!go3d) {
				BufferedImage roiImage=new BufferedImage(srcRectWidthMag, srcRectHeightMag, BufferedImage.TYPE_INT_ARGB);
				Graphics g=roiImage.getGraphics();
				if(roi!=null) {roi.draw(g); doRoi=true;}
				if(overlay!=null) {
					for(int i=0;i<overlay.size();i++) {
						Roi oroi=overlay.get(i);
						oroi.setImage(imp);
						int rc=oroi.getCPosition(), rz=oroi.getZPosition(),rt=oroi.getTPosition();
						if((rc==0||rc==imp.getC()) && (rz==0||rz==imp.getZ()) && (rt==0||rt==imp.getT())) {oroi.drawOverlay(g); doRoi=true;}
					}
				}
				if(doRoi)glos.getTexture("roiGraphic").createRgbaTexture(AWTTextureIO.newTextureData(gl.getGLProfile(), roiImage, false).getBuffer(), roiImage.getWidth(), roiImage.getHeight(), 1, 4, false);
			}else{   // if(!JCP.openglroi && (overlay!=null || isPoint) && go3d) 
				doOv=new boolean[sls];
				if(!glos.textures.containsKey("overlay") || glos.getTexture("overlay").getTextureLength()!=sls) {
					glos.newTexture("overlay",sls);
					glos.newBuffer(GL_ARRAY_BUFFER, "overlay");
					glos.newBuffer(GL_ELEMENT_ARRAY_BUFFER, "overlay");
					glos.newVao("overlay", 3, GL_FLOAT, 3, GL_FLOAT);
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
						glos.getTexture("overlay").createRgbaTexture(osl, AWTTextureIO.newTextureData(gl.getGLProfile(), roiImage, false).getBuffer(), roiImage.getWidth(), roiImage.getHeight(), 1, 4, false);
					}
				}
				if(isPoint && didpt)imp.setSliceWithoutUpdate(imp.getStackIndex(imp.getC(), sl+1, fr+1));
			}
		}
		
		if(glos.getPboLength("image")!=(chs*frms) || deletePBOs) {
			glos.newPbo("image", chs*(sb.isFrameStack?1:frms));
			sb.resetSlices();
			deletePBOs=false;
		}
		
		//if(needImageUpdate) {
		//	if(imageState.isChanged.slice || imageState.isChanged.minmax)needImageUpdate=false;
		//	else showUpdateButton(true);
		//}
		
		//IJ.log("miu:"+myImageUpdated+" sb.r:"+(myImageUpdated&& !imageState.isChanged.czt && !imageState.isChanged.minmax && !scbrAdjusting)+" "+imageState);
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
								if(i==(chs-1))sb.updateSlice(isl,ifr);
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
								IJ.log(gle.getMessage());
								IJ.log("Out of memory, switching usePBOforSlices off");
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
			myImageUpdated=false;
			//showUpdateButton(false);
			//if(needImageUpdate) {needImageUpdate=false;}
		}
		if(go3d) {
			for(int i=0;i<chs;i++) {
				int ccfr=fr*chs+i;
				glos.loadTexFromPbo("image", ccfr, "image3d", i, sb.bufferWidth, sb.bufferHeight, sls, 0, pixelType3d, COMPS, false, Prefs.interpolateScaledImages);
			}
		}
		
		if(go3d) {
			if((supermag+magnification)<=0)supermag=0f-(float)magnification;
			if((supermag+magnification)>24)supermag=24f-(float)magnification;
		}
		float 	trX=-((float)(srcRect.x*2+srcRect.width)/imageWidth-1f),
				trY=((float)(srcRect.y*2+srcRect.height)/imageHeight-1f),
				scX=(float)imageWidth/srcRect.width+(go3d?supermag:0f),
				scY=(float)imageHeight/srcRect.height+(go3d?supermag:0f);
		float[] translate=null,scale=null,rotate=null;
		if( (imageState.isChanged.srcRect || imageState.resized) || go3d) {
			if(tx>2.0f)tx=2.0f; if(tx<-2.0f)tx=-2.0f;
			if(ty>2.0f)ty=2.0f; if(ty<-2.0f)ty=-2.0f;
			if(tz>2.0f)tz=2.0f; if(tz<-2.0f)tz=-2.0f;
			translate=FloatUtil.makeTranslation(new float[16], false, trX+(go3d?tx:0f), trY+(go3d?ty:0f), go3d?tz:0f);
			scale=FloatUtil.makeScale(new float[16], false, scX, scY, scX);
			if(!go3d)
				glos.getUniformBuffer("model").loadMatrix(FloatUtil.multMatrix(scale, translate));//note this modifies scale
		}
		
		//drawing
		gl.glDisable(GL_SCISSOR_TEST);
		//gl.glDrawBuffers(1, new int[] {GL_BACK_LEFT},0);
		//gl.glDrawBuffer(GL_BACK_LEFT);
		glos.clearColorDepth();
		
		int views=1;
		if(go3d && stereoType.ordinal()>0)views=2;
		for(int stereoi=0;stereoi<views;stereoi++) {
			glos.useProgram("image");
			if(go3d) {
				if(stereoType==StereoType.QUADBUFFER) {
					if(stereoi==0)
						gl.glDrawBuffer(GL_LEFT);
					else
						gl.glDrawBuffer(GL_RIGHT);
						//gl.glDrawBuffers(1, new int[] {GL_BACK_RIGHT},0);
				}else if(stereoType==StereoType.CARDBOARD) {
					float[] orthocb = FloatUtil.makeOrtho(new float[16], 0, false, -CB_MAXSIZE, CB_MAXSIZE, -CB_MAXSIZE, CB_MAXSIZE, -CB_MAXSIZE, CB_MAXSIZE);
					float[] translatecb=FloatUtil.makeTranslation(new float[16], 0, false, (stereoi==0?(-CB_MAXSIZE*CB_TRANSLATE):(CB_MAXSIZE*CB_TRANSLATE)), 0f, 0f);
					FloatUtil.multMatrix(orthocb, translatecb);
					gl.glEnable(GL_SCISSOR_TEST);
					int width=drawable.getSurfaceWidth();
					int height=drawable.getSurfaceHeight();
					//int width=(int)((double)drawable.getSurfaceWidth()*dpimag+0.5);
					//int height=(int)((double)drawable.getSurfaceHeight()*dpimag+0.5);
					Rectangle r=getCBViewportAspectRectangle(0,0,width,height);
					int x=(int)(width/2)-(int)(r.width/CB_MAXSIZE/2f) + (int)(CB_TRANSLATE*r.width/2f*(stereoi==0?-1:1));
					//int y=(int)((1f-(1f/CB_MAXSIZE))*yrat/2f*(float)r.height);
					int y=(int)(height/2)-(int)(r.height/CB_MAXSIZE/2f);
					gl.glScissor(x, y, (int)(r.width/CB_MAXSIZE), (int)(r.height/CB_MAXSIZE));
					glos.getUniformBuffer("global").loadMatrix(orthocb);
				}else if(stereoType==StereoType.ANAGLYPH) {
					resetGlobalMatrices(drawable);
					int[] vps=new int[4]; gl.glGetIntegerv(GL_VIEWPORT, vps, 0);
					int width=vps[2], height=vps[3];
					gl.glBindFramebuffer(GL_FRAMEBUFFER, stereoFramebuffers[0]);
					gl.glBindRenderbuffer(GL_RENDERBUFFER, stereoFramebuffers[1]);
					if(stereoi==0) {
						JCGLObjects.PixelTypeInfo info=new JCGLObjects.PixelTypeInfo(pixelType3d,4);
						gl.glBindTexture(GL_TEXTURE_3D, glos.getTexture("anaglyph",0));
						gl.glTexImage3D(GL_TEXTURE_3D, 0, info.glInternalFormat, width, height, 1, 0, GL_RGBA, info.glPixelSize, null);
						gl.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
						gl.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
						gl.glFramebufferTexture3D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_3D, glos.getTexture("anaglyph",0), 0, 0);
						gl.glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT, width, height);
						gl.glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, stereoFramebuffers[1]);
						gl.glBindTexture(GL_TEXTURE_3D, 0);
					}
					// gl.glViewport(r.x, r.y, r.width, r.height);
					//gl.glViewport(0, 0, width, height);
					gl.glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
					if(gl.glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)IJ.error("not ready");
				}
				
				//Rotate
				float dxst=(float)dx;
				if(stereoi>0) {dxst-=(float)JCP.stereoSep; if(dxst<0)dxst+=360f;}
				rotate=FloatUtil.makeRotationEuler(new float[16], 0, dy*FloatUtil.PI/180f, (float)dxst*FloatUtil.PI/180f, (float)dz*FloatUtil.PI/180f);
				//IJ.log("\\Update0:X x"+Math.round(100.0*matrix[0])/100.0+" y"+Math.round(100.0*matrix[1])/100.0+" z"+Math.round(100.0*matrix[2])/100.0);
				//IJ.log("\\Update1:Y x"+Math.round(100.0*matrix[4])/100.0+" y"+Math.round(100.0*matrix[5])/100.0+" z"+Math.round(100.0*matrix[6])/100.0);
				//IJ.log("\\Update2:Z x"+Math.round(100.0*matrix[8])/100.0+" y"+Math.round(100.0*matrix[9])/100.0+" z"+Math.round(100.0*matrix[10])/100.0);
				//IJ.log(FloatUtil.matrixToString(null, "rot: ", "%10.4f", rotate, 0, 4, 4, false).toString());
				
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
				glos.getUniformBuffer("model").loadMatrix(modelTransform);
				
				if(ltr==null || !(ltr[0]==left && ltr[1]==top && ltr[2]==reverse) || cutPlanes.changed/*|| imageState.isChanged.srcRect*/) {
					cutPlanes.changed=false;
					double zrat=cal.pixelDepth/cal.pixelWidth;
					int zmaxsls=(int)(zrat*(double)sls);
					float zmax=(float)(zmaxsls)/(float)imageWidth;
					float 	tw=(2*imageWidth-tex4div(imageWidth))/(float)imageWidth,
							th=(2*imageHeight-tex4div(imageHeight))/(float)imageHeight;
					final float[] initVerts=cutPlanes.getInitCoords(zmax, tw, th);
					//For display of the square, there are 3 space verts and 3 texture verts
					//for each of the 4 points of the square.
					ByteBuffer vertb=glos.getDirectBuffer(GL_ARRAY_BUFFER, "image3d");
					vertb.clear();
					lim=0;
					if(left) { //left or right
						for(float p=cutPlanes.x();p<cutPlanes.w();p+=1.0f) {
							float xt,xv, pn;
							if(reverse) pn=p;
							else pn=cutPlanes.w()-(p-cutPlanes.x()+1.0f);
							xt=(pn+0.5f)/imageWidth*tw;
							xv=xt*2f-1f;
							for(int i=0;i<4;i++) {
								vertb.putFloat(xv); vertb.putFloat(initVerts[i*6+1]); vertb.putFloat(initVerts[i*6+2]);
								vertb.putFloat(xt); vertb.putFloat(initVerts[i*6+4]); vertb.putFloat(initVerts[i*6+5]);
							}
							lim++;
						}
						lim*=24;
					} else if(top) { //top or bottom
						for(float p=cutPlanes.y();p<cutPlanes.h();p+=1.0f) {
							float yt,yv,pn;
							if(!reverse) pn=p;
							else pn=cutPlanes.h()-(p-cutPlanes.y()+1.0f);
							yt=(pn+0.5f)/imageHeight*th;
							yv=1f-yt*2f;
							for(int i=0;i<4;i++) {
								float zv=initVerts[i*6+2];
								float zt=initVerts[i*6+5];
								if(i==1) {zv=initVerts[2]; zt=initVerts[5];}
								else if(i==3) {zv=initVerts[8]; zt=initVerts[11];}
								vertb.putFloat(initVerts[i*6]); vertb.putFloat(yv); vertb.putFloat(zv);
								vertb.putFloat(initVerts[i*6+3]); vertb.putFloat(yt); vertb.putFloat(zt);
							}
							lim++;
						}
						lim*=24;
					}else { //front or back
						for(float csl=(int)(cutPlanes.z()*zrat);csl<(int)(cutPlanes.d()*zrat);csl+=1.0f) {
							float z=csl;
							if(reverse) z=(cutPlanes.d()*(float)zrat)-(csl-(int)(cutPlanes.z()*(float)zrat));//z=((float)zmaxsls-csl-1f);
							for(int i=0;i<4;i++) {
								vertb.putFloat(initVerts[i*6]); vertb.putFloat(initVerts[i*6+1]); vertb.putFloat(((float)zmaxsls-2f*z)/imageWidth); 
								vertb.putFloat(initVerts[i*6+3]); vertb.putFloat(initVerts[i*6+4]); vertb.putFloat((z+0.5f)/zmaxsls); 
							}
							lim++;
						}
						lim*=24;
					}
					vertb.limit(lim*4);
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
				lim=24;
			}
			
			//setluts
			ByteBuffer lutMatrixPointer=glos.getDirectBuffer(GL_UNIFORM_BUFFER, "lut");
			lutMatrixPointer.rewind();
			LUT[] luts=imp.getLuts();
			boolean[] active=new boolean[chs];
			for(int i=0;i<chs;i++)active[i]=true;
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
					}
				}
				lutMatrixPointer.putFloat(min);
				lutMatrixPointer.putFloat(max);
				lutMatrixPointer.putFloat(color);
				lutMatrixPointer.putFloat((gamma==null || gamma.length<=i)?0f:gamma[i]); //padding for vec3 std140
			}
			lutMatrixPointer.rewind();
			
			
			glos.bindUniformBuffer("global", 1);
			glos.bindUniformBuffer("model", 2);
			glos.bindUniformBuffer("lut", 3);
			if(go3d)
				glos.drawTexVao("image3d",GL_UNSIGNED_SHORT, lim/4, chs);
			else
				glos.drawTexVao("image2d",GL_UNSIGNED_BYTE, lim/4, chs);
			glos.unBindBuffer(GL_UNIFORM_BUFFER, 1);
			glos.unBindBuffer(GL_UNIFORM_BUFFER, 2);
			glos.unBindBuffer(GL_UNIFORM_BUFFER, 3);
			glos.stopProgram();

			
			if(roi!=null || overlay!=null) { 
				if(go3d) {
					scX=(float)imageWidth/srcRect.width;
					scY=(float)imageHeight/srcRect.height;
					FloatUtil.multMatrix(rotate,FloatUtil.makeTranslation(new float[16], false, tx*scX, ty*scY, tz*scX));
					if(supermag!=0f) {
						float tsm=(scX+supermag)/scX;
						rotate=FloatUtil.multMatrix(FloatUtil.makeScale(new float[16], false, tsm, tsm, tsm), rotate);
					}
					glos.getUniformBuffer("modelr").loadMatrix(rotate);
				}
				//if(go3d)IJ.log(FloatUtil.matrixToString(null, "rot2: ", "%10.4f", rotate, 0, 4, 4, false).toString());
				float z=0f;
				float zf=(float)(cal.pixelDepth/cal.pixelWidth)/srcRect.width;
				if(go3d) z=((float)sls-2f*sl)*zf;
				gl.glEnable(GL_BLEND);
				gl.glBlendEquation(GL_FUNC_ADD);
				gl.glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
				if(!JCP.openglroi) {
					if(doRoi)drawGraphics(gl, z, "roiGraphic", 0, (go3d?"modelr":"idm"));
					if(doOv!=null) {
						for(int osl=0;osl<sls;osl++) {
							if(doOv[osl] && (!cutPlanes.applyToRoi || (osl>=cutPlanes.z() && osl<cutPlanes.d())) ) {
								drawGraphics(gl, ((float)sls-2f*(float)osl)*zf, "overlay", osl, (go3d?"modelr":"idm"));
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
					if(rgldu==null) rgldu=new RoiGLDrawUtility(imp, drawable);
					if(glos.glver==2)rgldu.startDrawing();
					glos.bindUniformBuffer("global", 1);
					glos.bindUniformBuffer(go3d?"modelr":"idm", 2);
					
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
				gl.glEnable(GL_BLEND);
				gl.glBlendEquation(GL_MAX);
				gl.glBlendFunc(GL_SRC_COLOR, GL_DST_COLOR);

				glos.useProgram("anaglyph");
				gl.glUniformMatrix3fv(glos.getLocation("anaglyph", "ana"), 1, false, JCP.anaColors[stereoi], 0);
				gl.glUniform1f(glos.getLocation("anaglyph", "dubois"), JCP.dubois?1f:0f);

				glos.bindUniformBuffer("globalidm", 1);
				glos.bindUniformBuffer("idm", 2);
				glos.drawTexVao("anaglyph",GL_UNSIGNED_BYTE, 6, 1);
				glos.unBindBuffer(GL_UNIFORM_BUFFER,1);
				glos.unBindBuffer(GL_UNIFORM_BUFFER,2);
				glos.stopProgram();
			}
		} //stereoi
		if(JCP.debug && verbose) {
			IJ.log("\\Update1:Display took: "+String.format("%5.1f", (float)(System.nanoTime()-displaytime)/1000000f)+"ms");
		}
		
		if(imageUpdated) {imageUpdated=false;} //ImageCanvas imageupdated only for single ImagePlus
		imageState.update();
		
		if(myscreengrabber!=null) {
			if(myscreengrabber.isReadyForUpdate()) {
				BufferedImage bi=grabScreen(drawable);
				myscreengrabber.screenUpdated(bi);
			}
		}
		mylock=false;
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
		
		public CutPlanesCube(int x, int y, int z, int width, int height, int depth, boolean applyToRoi) {
			this.x=x; this.y=y; this.z=z;
			this.w=width; this.h=height; this.d=depth;
			this.applyToRoi=applyToRoi;
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
		}
		
		public float[] getInitCoords(float zmax, float dtw, float dth) {
			if(initCoords!=null) {return initCoords;}
			float vx=(float)x/imp.getWidth()*2f-1f, vy=(float)y/imp.getHeight()*2f-1f, vz=-((float)z/imp.getNSlices()*2f-1f),
				  vw=(float)w/imp.getWidth()*2f-1f, vh=(float)h/imp.getHeight()*2f-1f, vd=-((float)d/imp.getNSlices()*2f-1f);
			float tx=(float)x/imp.getWidth(), ty=(float)y/imp.getHeight(), ttz=(float)z/imp.getNSlices(),
				  tw=(float)w/imp.getWidth(), th=(float)h/imp.getHeight(), td=(float)d/imp.getNSlices();
			initCoords=new float[] {
					vx, -vh, vd*zmax,   tx,     th*dth, td,
					vw, -vh, vz*zmax,   tw*dtw, th*dth, ttz,
					vw, -vy, vz*zmax,   tw*dtw, ty,     ttz,
					vx, -vy, vd*zmax,   tx,     ty,     td
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
			float tx=(glx+1f)/2f, ty=(gly+1f)/2f, tw=(glw+1f)/2f, th=(glh+1f)/2f;
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
		private ImagePlus imp;
		public Rectangle prevSrcRect;
		public MinMax[] minmaxs;
		public Calibration prevCal;
		public int c,z,t;
		public IsChanged isChanged=new IsChanged();
		public boolean resized=false;
		
		public ImageState(ImagePlus imp) {
			this.imp=imp;
			update();
		}
		
		public void update() {
			prevSrcRect=(Rectangle)imp.getCanvas().getSrcRect().clone();
			minmaxs=MinMax.getMinMaxs(imp.getLuts());
			prevCal=(Calibration)imp.getCalibration().clone();
			c=imp.getC(); z=imp.getZ(); t=imp.getT();
			reset();
		}
		
		public void check() {
			isChanged.srcRect=!prevSrcRect.equals(imp.getCanvas().getSrcRect());
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
		}
		
		public void reset() {
			isChanged.reset();
			resized=false;
		}
		
		static class IsChanged{
			public boolean c,z,t,czt,cal,minmax,srcRect, slice;
			public void reset() {
				slice=srcRect=minmax=cal=czt=c=z=t=false;
			}
		}
		
		public String toString() {
			return ("isChanged: c:"+isChanged.c+" z:"+isChanged.z+" t:"+isChanged.t+" cal:"+isChanged.cal+" minmax:"+isChanged.minmax+" srcRect:"+isChanged.srcRect+" slice:"+isChanged.slice);
		}
	}
	
	public BufferedImage grabScreen(GLAutoDrawable drawable) {
		int[] vps=new int[4]; gl.glGetIntegerv(GL_VIEWPORT, vps, 0);
		int width=vps[2], height=vps[3];
		int x=0,y=0;
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
	
	private void drawGraphics(GL2GL3 gl, float z, String name, int index, String modelmatrix) {
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
		glos.useProgram("roi");
		drawGraphics(gl, name, index, modelmatrix, vb);
		glos.stopProgram();
	}
	
	private void drawGraphics(GL2GL3 gl, String name, int index, String modelmatrix, Buffer vb) {

		ShortBuffer eb=GLBuffers.newDirectShortBuffer(new short[] {0,1,2,2,3,0});
		gl.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
		glos.bindUniformBuffer("global", 1);
		glos.bindUniformBuffer(modelmatrix, 2);
		glos.drawTexVaoWithEBOVBO(name, index, eb, vb);
		glos.unBindBuffer(GL_UNIFORM_BUFFER,1);
		glos.unBindBuffer(GL_UNIFORM_BUFFER,2);
		if(Prefs.interpolateScaledImages)gl.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
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
		if(go3d==newboo)return;
		threeDupdated=true;
		myImageUpdated=true;
		go3d=newboo;
		repaint();
	}
	
	public void setStereo(StereoType stereoTypeChoice) {
		stereoType=stereoTypeChoice;
		stereoUpdated=true;
		myImageUpdated=true;
		repaint();
	}
	
	private void createMirror() {
		isMirror=true;
		mirror=new Frame("JOGL-DC3D Mirror of "+imp.getTitle());
		mirror.add(icc);
		mirror.addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) { revert(); }
		});
		for(com.jogamp.newt.event.MouseListener ml : glw.getMouseListeners()) {
			if(ml instanceof JOGLEventAdapter) {
				((JOGLEventAdapter)ml).setMouseWheelListener(new MouseAdapter() {
					@Override
					public void mouseWheelMoved(MouseWheelEvent e) {
						imp.getCanvas().setCursor(e.getX(), e.getY(), offScreenX(e.getX()), offScreenY(e.getY()));
						imp.getWindow().mouseWheelMoved(e);
						updateMirror(); repaint();
					}
				});
			}
		}
		addMirrorListeners();
		java.awt.EventQueue.invokeLater(new Runnable() {
			@Override
			public void run() {
				mirror.pack();
				mirror.setVisible(true);
				IJ.wait(200);
		        mirror.toFront();
			}
		});
		imp.setProperty("JOGLImageCanvas", this);
	}
	
	private void addMirrorListeners() {
		final JOGLImageCanvas jic=this;
		java.awt.EventQueue.invokeLater(new Runnable() {
			@Override
			public void run() {
				new StackWindow(imp,new ImageCanvas(imp) {
					private static final long serialVersionUID = 1L;
					@Override
					public void paint(Graphics g){
						jic.repaint();
						super.paint(g);
					}
				});
				imp.getWindow().addWindowListener(new WindowAdapter() {
					public void windowClosing(WindowEvent e) {
						glw.destroy();mirror.dispose();mirror=null;
					}
				});
			}
			
		});
	}
	
	private void updateMirror() {
		if(!isMirror)return;
		if(imp==null) {revert();return;}
		ImageCanvas ic=imp.getCanvas();
		if(ic==null) {revert();return;}
		srcRect=ic.getSrcRect();
		magnification=ic.getMagnification();
		if(mirror==null || !mirror.isVisible() || glw.isFullscreen())return;
		int glww=glw.getSurfaceWidth(), glwh=glw.getSurfaceHeight();
		int w=ic.getWidth(), h=ic.getHeight();
		if(mirrorMagUnlock || mirror.getExtendedState()==Frame.MAXIMIZED_BOTH) {
			Insets ins=mirror.getInsets();
			Dimension d=mirror.getSize();
			w=d.width-ins.left-ins.right;
			h=d.height-ins.top-ins.bottom;
		}
		int wm=(int)(w*dpimag+0.5), hm=(int)(h*dpimag+0.5);
		if(glww!=wm || glwh!=hm) {
			setSize(w,h);
		}
	}
	
	public void setMirrorMagUnlock(boolean set) {
		mirrorMagUnlock=set;
		updateMirror();
	}
	
	public boolean toggleFullscreen() {
		boolean dofs=!glw.isFullscreen();
		glw.setUndecorated(dofs);
		if(dofs) {
			ArrayList<MonitorDevice> ml=new ArrayList<MonitorDevice>();
			for(MonitorDevice md : glw.getScreen().getMonitorDevices())IJ.log("MD:"+md);
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
	
	public void revert() {
		//showUpdateButton(false);
		if(isMirror){
			if(imp!=null) {
				java.awt.EventQueue.invokeLater(new Runnable() {
					public void run() {
						new StackWindow(imp,new ImageCanvas(imp));
						imp.setProperty("JOGLImageCanvas", null);
						if(JCP.debug)IJ.log("post new stackwindow, before GLW destroy");
						glw.destroy();
						if(JCP.debug)IJ.log("after GLW destroy");
						mirror.dispose();
						if(JCP.debug)IJ.log("after mirror dispose");
						mirror=null;
					}
				});
			}
		}else {
			glw.destroy();
			final int mode=imp.getDisplayMode();
			java.awt.EventQueue.invokeLater(new Runnable(){
				public void run() {
					new StackWindow(imp);
					imp.getCanvas().setMagnification(magnification);
					imp.getCanvas().setSourceRect(srcRect);
					imp.setDisplayMode(mode);
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
		float w=(float)(srcRect.width*magnification);
		float h=(float)(srcRect.height*magnification);
		float yrat=1;//(float)srcRect.height/srcRect.width;
		x1=x1/w*2f-1f; y1=((h-y1)/h*2f-1f)*yrat;
		w1=w1/w*2f; h1=h1/h*2f*yrat;
		x2=x2/w*2f; y2=y2/h*2f*yrat;
		w2=w2/w*2f; h2=h2/h*2f*yrat;
		
		gl.glDisable(GL_BLEND);
		gl.glLineWidth((float)dpimag);
		zoomIndVerts.rewind();
		float[] color=new float[] {(float)128/255, (float)128/255, 1f, 1f};
		zoomIndVerts.put(x1).put(y1).put(0f).put(color);
		zoomIndVerts.put(x1+w1).put(y1).put(0f).put(color);
		zoomIndVerts.put(x1+w1).put(y1-h1).put(0f).put(color);
		zoomIndVerts.put(x1).put(y1-h1).put(0f).put(color);
		zoomIndVerts.rewind();
		
		if(rgldu==null) rgldu=new RoiGLDrawUtility(imp, drawable);
		if(glos.glver==2)rgldu.startDrawing();
		glos.bindUniformBuffer("global", 1);
		glos.bindUniformBuffer("idm", 2);
		rgldu.drawGLfb(drawable, zoomIndVerts, GL_LINE_LOOP);
		//zoomIndVerts.rewind();
		zoomIndVerts.put(x1+x2).put(y1-y2).put(0f).put(color);
		zoomIndVerts.put(x1+x2+w2).put(y1-y2).put(0f).put(color);
		zoomIndVerts.put(x1+x2+w2).put(y1-y2-h2).put(0f).put(color);
		zoomIndVerts.put(x1+x2).put(y1-y2-h2).put(0f).put(color);
		zoomIndVerts.rewind();
		
		rgldu.drawGLfb(drawable, zoomIndVerts, GL_LINE_LOOP);

		glos.unBindBuffer(GL_UNIFORM_BUFFER, 1);
		glos.unBindBuffer(GL_UNIFORM_BUFFER, 2);
		gl.glLineWidth(1f);
		
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
			if(JCP.debug)IJ.log("SetSize w:"+width+" h:"+height);
			//Dimension s=new Dimension((int)(width*dpimag+0.5), (int)(height*dpimag+0.5));
			Dimension s=new Dimension(width,height);
			if(isMirror && mirror.getExtendedState()!=Frame.MAXIMIZED_BOTH) {
				java.awt.Insets ins=mirror.getInsets();
				if(JCP.debug)IJ.log("mirror setsize Insets: tb"+(ins.top+ins.bottom)+" lr"+(ins.left+ins.right));
				mirror.setSize(width+ins.left+ins.right,height+ins.top+ins.bottom);
			}
			icc.setSize(s);
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
			updateMirror();
			icc.repaint();
		}
		else if(gl!=null) setPaintPending(false);
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
		if(icc!=null)return (isMirror?mirror:imp.getWindow());
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
	
	public void add(JPopupMenu popup) {
		if(icc!=null)getParent().add(popup);
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
				//x=(int)(x/dpimag);
				//y=(int)(y/dpimag);
				add(dcpopup);
				dcpopup.show(icc,x,y);
			}
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
			addMI(threeDmenu,"Adjust Cut Planes","adjust3d");
			addMI(threeDmenu,"Adjust Contrast/Gamma","gamma");
			addMI(threeDmenu,"Adjust Rot-Trans","rottrans");
			
			menu=new Menu("Stereoscopic 3d");
			for(int i=0;i<stereoTypeStrings.length;i++) {
				addMI(menu,stereoTypeStrings[i],stereoTypeStrings[i]);
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
			if(isMirror)addCMI(menu, "Resizable Mirror", mirrorMagUnlock);
			addCMI(menu, "Fullscreen", !(glw==null || !glw.isFullscreen()));
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
		}else if(cmd.equals("prefs")){JCP.preferences();}
		else if(cmd.equals("Recorder")){
			IJ.run("JOGL Canvas Recorder",imp.getTitle());
		}else if(cmd.equals("usePBOforSlices")) {
			if(JCP.usePBOforSlices) {
				IJ.log("PBOslices off");
				JCP.usePBOforSlices=false;
			}else {
				IJ.log("PBOslices on");
				JCP.usePBOforSlices=true;
			}
		}else if("guidpi".equals(cmd)){
			dpimag=IJ.getNumber("Manual set GUI dpi", 2.0);
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
			if(!go3d)myImageUpdated=true;
			else {
				//needImageUpdate=true;
			}
		}
	}
	
	/**
	 * To use the BIScreenGrabber function. Set to null to stop
	 * the screengrabs.
	 * @param sg
	 */
	public void setBIScreenGrabber(BIScreenGrabber sg) {
		myscreengrabber=sg;
	}

	/*
	 * Key and mouse events
	 */
	
	/**
	 * Key Events. Taken especially for mirror.
	 */
	@Override
	public void keyPressed(KeyEvent e) {
		//System.out.println("AJS- JIC-keyPressed"+Character.toString(e.getKeyChar()));
		if(!(e.getKeyChar()=='='||e.getKeyChar()=='-')) ij.keyPressed(e);}
	@Override
	public void keyReleased(KeyEvent e) {
		//System.out.println("AJS- JIC-keyReleased"+Character.toString(e.getKeyChar())); 
		ij.keyReleased(e);
	}
	@Override
	public void keyTyped(KeyEvent e) {
		//System.out.println("AJS- JIC-keyTyped"+Character.toString(e.getKeyChar()));
		char key=e.getKeyChar();
		int code=e.getKeyCode();
		if(key=='u') {
			if(go3d) {
				myImageUpdated=true;
				repaint();
			}
		}else if(code==KeyEvent.VK_ESCAPE) {
			if(glw.isFullscreen())toggleFullscreen();
		}else {
			if(key=='='||key=='-') {
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
			}
			if(isMirror) {updateMirror(); repaint();}
		}
		ij.keyTyped(e);
	}
	
	/**
	 * Test to keep right click or send to super
	 * Adapted from https://stackoverflow.com/questions/2972512/how-to-detect-right-click-event-for-mac-os
	 * @param e
	 * @return
	 */
	private static boolean isRightClick(MouseEvent e) {
	    return (e.getButton()==MouseEvent.BUTTON3 ||
	            (System.getProperty("os.name").contains("Mac OS X") &&
	                    (e.getModifiers() & MouseEvent.BUTTON1_MASK) != 0 &&
	                    (e.getModifiers() & MouseEvent.CTRL_MASK) != 0));
	}
	
	private boolean shouldKeep(MouseEvent e) {
		return ((!isRightClick(e) && isMirror) || (!isRightClick(e) && (go3d && ((IJ.getToolName()=="hand" && !IJ.spaceBarDown()) || IJ.controlKeyDown()))));
	}
	
	@Override
	public void mousePressed(MouseEvent e) {
		//if(go3d && ((e.getModifiersEx() & (MouseEvent.BUTTON1_DOWN_MASK | MouseEvent.BUTTON3_DOWN_MASK))>0))resetAngles();
		if(shouldKeep(e)) {
			sx = e.getX();
			sy = e.getY();
			if(IJ.spaceBarDown()) {
				setupScroll(offScreenX(sx),offScreenY(sy));
			} 
		}else super.mousePressed(e);
	}
	
	public void setSuperMag(float m) { supermag=m;}
	public float getSuperMag() {return supermag;}
	
	@Override
	public void mouseDragged(MouseEvent e) {
		if(shouldKeep(e)) {
			boolean alt=(e.getModifiersEx() & MouseEvent.ALT_DOWN_MASK)!=0;
			boolean ctrl=(e.getModifiersEx() & (MouseEvent.CTRL_DOWN_MASK | MouseEvent.META_DOWN_MASK))!=0;
			boolean shift=(e.getModifiersEx() & MouseEvent.SHIFT_DOWN_MASK)!=0;
			//if(JCP.debug) {IJ.log("\\Update2:   Drag took: "+String.format("%5.1f", (float)(System.nanoTime()-dragtime)/1000000f)+"ms"); dragtime=System.nanoTime();}
			if(IJ.spaceBarDown()&&isMirror) {
				scroll(e.getX(),e.getY());
				imp.getCanvas().setSourceRect(srcRect);
			}else if(go3d){
				float xd=(float)(e.getX()-sx)/(float)srcRect.width;
				float yd=(float)(e.getY()-sy)/(float)srcRect.height;
				sx=e.getX(); sy=e.getY();
				if(alt||e.getButton()==MouseEvent.BUTTON2) {
					if(shift) {
						tz-=yd;
					}else dz+=yd*90f;
				}else if(shift) {
					if(ctrl) {
						supermag-=yd*magnification;
					}else {
						tx+=xd;
						ty-=yd;
					}
				}else {
					dx+=xd*90f;
					dy+=yd*90f;
				}
				if(dz<0)dz+=360; if(dz>360)dz-=360;
				if(dx<0)dx+=360; if(dx>360)dx-=360;
				if(dy<0)dy+=360; if(dy>360)dy-=360;
			}
			if(isMirror)updateMirror();
			repaint();
		}else super.mouseDragged(e);
	}
	
	@Override
	public void mouseReleased(MouseEvent e) {
		if(shouldKeep(e)) {
			//if((IJ.shiftKeyDown())) {
			//	resetAngles();
			//}
		}else super.mouseReleased(e);
	}
	
	public void resetAngles() {
		dx=0f; dy=0f; dz=0f; tx=0f; ty=0f; tz=0f;
		repaint();
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
		repaint();
	}
	
	public void setGuiDPI(double dpi) {
		dpimag=dpi;
	}
	
	private void addAdjustmentListening() {
		if(imp==null || imp.getWindow()==null || !(imp.getWindow() instanceof StackWindow)) {IJ.log("JOGLCanvas was created but no ImagePlus Window was found"); return;}
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
	/*public void showUpdateButton(boolean show) {
		boolean nowin=(imp==null || imp.getWindow()==null || !(imp.getWindow() instanceof StackWindow));
		if(updateButton!=null && (!show || nowin)) {
			Container parent=updateButton.getParent();
			if(parent!=null) {parent.remove(updateButton);}
			updateButton=null;
		}
		if(nowin)return;
		StackWindow stwin=(StackWindow) imp.getWindow();
		if(show && updateButton!=null) {
			if(updateButton.getParent()!=null && updateButton.getParent().getParent()==stwin && updateButton.isEnabled())return;
		}
		ScrollbarWithLabel scr=null;
		Component[] comps=stwin.getComponents();
		for(int i=0;i<comps.length;i++) {
			if(comps[i] instanceof ij.gui.ScrollbarWithLabel) {
				scr=(ScrollbarWithLabel)comps[i];
				scr.addAdjustmentListener(new AdjustmentListener() {
					@Override
					public void adjustmentValueChanged(AdjustmentEvent e) {
						sbAdjusting=e.getValueIsAdjusting();
					}
				});
			}
		}
		if(scr!=null) {
			//Remove any orphaned updateButtons, like with a crashed JOGLImageCanvas
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
				updateButton= new Button("Update");
				updateButton.addActionListener(new ActionListener() {
					public void actionPerformed(ActionEvent e) {
						updateButton.setLabel("Updating...");
						updateButton.setEnabled(false);
						updateButton.repaint();
						if(isMirror && mirror==null) {showUpdateButton(false);return;}
						myImageUpdated=true; repaint();
					}
				});
				updateButton.setFocusable(false);
				scr.add(updateButton,BorderLayout.EAST);
			}
			stwin.pack();
		}
	}
	*/
	
	/**
	 * Perhaps this could be integrated with plugins like CLIJ
	 * so that images processed on the GPU could stay on the GPU
	 * for display.
	 * 
	 * Returns an int array of GL PBO handles.  I use one PBO for
	 * every channel and frame, indexed as:
	 * currentFrame*channels+currentChannel
	 * @return
	 */
	public int[] getPBOnames() {
		return glos.getPbo("image").pbos;
	}

}
