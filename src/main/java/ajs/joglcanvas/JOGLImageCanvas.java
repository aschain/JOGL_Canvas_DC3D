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
import com.jogamp.opengl.GL4;

import static com.jogamp.opengl.GL.GL_FLOAT;
import static com.jogamp.opengl.GL2ES3.GL_UNIFORM_BUFFER;
import static com.jogamp.opengl.GL4.*;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.util.texture.awt.AWTTextureIO;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLException;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.util.awt.AWTGLReadBufferUtil;
import com.jogamp.opengl.math.FloatUtil;
import com.jogamp.opengl.util.glsl.ShaderCode;
import com.jogamp.opengl.util.glsl.ShaderProgram;
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
	private boolean isFrameStack=false;
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

	private GL4 gl;
	private JCGLObjects glos;
	private Program[] programs;
	private float[] anaColors;
	private ByteBuffer vertb=null;
	private FloatBuffer zoomIndVerts=null;
	private int lim;
	private Buffer[] imageFBs;
	private boolean[] updatedBuffers;
	private boolean[] updatedBuffersSlices;
	private int updatingBuffers=0;
	private int undersample=JCP.undersample;
	enum StereoType{OFF, CARDBOARD, ANAGLYPH, QUADBUFFER};
	private static String[] stereoTypeStrings=new String[] {"Stereo off", "Google Cardboard-SBS","Anaglyph (red-cyan)","OpenGL Quad Buffers"};
	private static final float CB_MAXSIZE=4f;
	private static final float CB_TRANSLATE=0.44f;
	private StereoType stereoType=StereoType.OFF;
	private int anaSiLoc, anaLoc;
	private boolean stereoUpdated=true,threeDupdated=true;

	enum PixelType{BYTE, SHORT, FLOAT, INT_RGB10A2};
	private static final String[] pixelTypeStrings=new String[] {"4 bytes (8bpc, 32bit)","4 shorts (16bpc 64bit)","4 floats (32bpc 128bit)","1 int RGB10A2 (10bpc, 32bit)"};
	private PixelType pixelType=PixelType.BYTE;
	private PixelType pixelType3d=PixelType.BYTE;
	private int COMPS=0;
	
	private BIScreenGrabber myscreengrabber=null;
	private AWTGLReadBufferUtil ss=null;
	private RoiGLDrawUtility rgldu=null;
	//private long starttime=0;

	public JOGLImageCanvas(ImagePlus imp, boolean mirror) {
		super(imp);
		int bitDepth=imp.getBitDepth();
		COMPS=bitDepth==24?3:imp.getNChannels();
		if(!mirror) {setOverlay(imp.getCanvas().getOverlay());}
		updateLastPosition();
		prevSrcRect=new Rectangle(0, 0, 0, 0);
		if(JCP.glCapabilities==null && !JCP.setGLCapabilities()) IJ.showMessage("error in GL Capabilities");
		int[] bits=new int[] {JCP.glCapabilities.getAlphaBits(),JCP.glCapabilities.getRedBits(),JCP.glCapabilities.getGreenBits(),JCP.glCapabilities.getBlueBits()};
		if(imp.getBitDepth()>8 && bitDepth!=24) {
			if(bits[0]>8||bits[1]>8||bits[2]>8||bits[3]>8 && JCP.glCapabilities.getGLProfile().isGL4()) {
				pixelType=PixelType.SHORT;
			}
			if(bits[0]<=2 && bits[1]==10 && bits[2]==10 && bits[3]==10) {
				pixelType=PixelType.INT_RGB10A2;
			}
		}
		createPopupMenu();
		initBuffers(imp.getNFrames(),imp.getNSlices());
		icc=new GLCanvas(JCP.glCapabilities);
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
		if(JCP.backgroundLoadBuffers) {
			updateBuffersBackground(null);
		}
		if(mirror)setMirror();
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
	
	private GL4 getGL(GLAutoDrawable drawable) {
		return drawable.getGL().getGL4();
	}

	//GLEventListener methods
	@Override
	public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
		gl = getGL(drawable);
		float[] ortho = FloatUtil.makeOrtho(new float[16], 0, false, -1f, 1f, -(float)srcRect.height/srcRect.width, (float)srcRect.height/srcRect.width, -1f, 1f);
		glos.buffers.loadMatrix("global", ortho);
		glos.buffers.loadIdentity("global",16*Buffers.SIZEOF_FLOAT);
		gl.glViewport(x, y, width, height);
		int srcRectWidthMag = (int)(srcRect.width*magnification+0.5);
		int srcRectHeightMag = (int)(srcRect.height*magnification+0.5);
		//int[] vps=new int[4];
		//gl.glGetIntegerv(GL_VIEWPORT, vps, 0);
		//if(dpimag>1.0)IJ.log("bef VPS: "+vps[0]+" "+vps[1]+" "+vps[2]+" "+vps[3]);
		if(dpimag>1.0 && !IJ.isMacOSX())gl.glViewport(0, 0, (int)(srcRectWidthMag*dpimag+0.5), (int)(srcRectHeightMag*dpimag+0.5));
		//gl.glGetIntegerv(GL_VIEWPORT, vps, 0);
		//if(dpimag>1.0)IJ.log("aft VPS: "+vps[0]+" "+vps[1]+" "+vps[2]+" "+vps[3]);
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
		gl = getGL(drawable);
		gl.glClearColor(0f, 0f, 0f, 0f);
		gl.glDisable(GL4.GL_DEPTH_TEST);
		
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
		

		glos=new JCGLObjects(gl);
		glos.newTexture("image");
		vertb=glos.newBuffer(GL_ARRAY_BUFFER, "image", maxsize*4*Buffers.SIZEOF_FLOAT, null);
		glos.newBuffer(GL_ELEMENT_ARRAY_BUFFER, "image", maxsize*Buffers.SIZEOF_SHORT, elementBuffer);
		glos.newVao("image", 3, GL_FLOAT, 3, GL_FLOAT);

		glos.newTexture("roiGraphic");
		glos.newBuffer(GL_ARRAY_BUFFER, "roiGraphic");
		glos.newBuffer(GL_ELEMENT_ARRAY_BUFFER, "roiGraphic");
		glos.newVao("roi", 3, GL_FLOAT, 3, GL_FLOAT);
		
		glos.newBuffer(GL_UNIFORM_BUFFER, "global", 16*2 * Buffers.SIZEOF_FLOAT, null);
		glos.newBuffer(GL_UNIFORM_BUFFER, "model", 16 * Buffers.SIZEOF_FLOAT, null);
		glos.newBuffer(GL_UNIFORM_BUFFER, "lut", 16 * Buffers.SIZEOF_FLOAT, null);
		glos.newBuffer(GL_UNIFORM_BUFFER, "idm", 16 * Buffers.SIZEOF_FLOAT, GLBuffers.newDirectFloatBuffer(FloatUtil.makeIdentity(new float[16])));
		
		glos.buffers.loadIdentity("model");
		//global written during reshape call

        int numProgs=4;
		programs=new Program[numProgs];
		programs[0]=new Program(gl, "shaders", "texture", "texture");
		programs[1]=new Program(gl, "shaders", "color", "color");
		programs[2]=new Program(gl, "shaders", "texture", "anaglyph");
		programs[3]=new Program(gl, "shaders", "roiTexture", "roiTexture");
		anaSiLoc=gl.glGetUniformLocation(programs[2].name, "stereoi");
		anaLoc=gl.glGetUniformLocation(programs[2].name, "ana");

		if(JCP.dubois) {
		//Source of below: bino, a 3d video player:  https://github.com/eile/bino/blob/master/src/video_output_render.fs.glsl
		// Source of this matrix: http://www.site.uottawa.ca/~edubois/anaglyph/LeastSquaresHowToPhotoshop.pdf
		anaColors = new float[] {
				 0.437f, -0.062f, -0.048f,
				 0.449f, -0.062f, -0.050f,
				 0.164f, -0.024f, -0.017f
				 
				-0.011f,  0.377f, -0.026f,
				-0.032f,  0.761f, -0.093f,
				-0.007f,  0.009f,  1.234f};
		}else {
			float r=(float)JCP.leftAnaglyphColor.getRed()/255f, g=(float)JCP.leftAnaglyphColor.getGreen()/255f, b=(float)JCP.leftAnaglyphColor.getBlue()/255f,
				rr=(float)JCP.rightAnaglyphColor.getRed()/255f, gr=(float)JCP.rightAnaglyphColor.getGreen()/255f, br=(float)JCP.rightAnaglyphColor.getBlue()/255f;
			anaColors=new float[] { r,r,r,g,g,g,b,b,b,
								rr,rr,rr,gr,gr,gr,br,br,br};
		}
		
		zoomIndVerts=GLBuffers.newDirectFloatBuffer(4*3+4*4);
	}

	@Override
	public void dispose(GLAutoDrawable drawable) {
		gl = getGL(drawable);
		glos.setGL(gl);
		glos.dispose();

        for(int i=0;i<programs.length;i++) if(programs[i]!=null)gl.glDeleteProgram(programs[i].name);
        
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
		
		int srcRectWidthMag = (int)(srcRect.width*magnification+0.5);
		int srcRectHeightMag = (int)(srcRect.height*magnification+0.5);
		gl = getGL(drawable);
		glos.setGL(gl);
		
		if(stereoUpdated) {
			if(stereoType!=StereoType.CARDBOARD) {
				glos.buffers.loadIdentity("global", 0);
			}
			if(stereoType==StereoType.ANAGLYPH) {
				
			}
			stereoUpdated=false;
		}
		if(threeDupdated) {
			if(!go3d) {
				glos.buffers.loadIdentity("model", 0);
				glos.buffers.loadIdentity("global", 0);
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
			if(doRoi)glos.textures.createRgbaTexture("roi", AWTTextureIO.newTextureData(gl.getGLProfile(), roiImage, false).getBuffer(), srcRectWidthMag, srcRectHeightMag, 1, 4);
		}
		boolean[] doOv=null;
		if(!JCP.openglroi && overlay!=null && go3d) {
			doOv=new boolean[sls];
			if(!glos.textures.containsKey("overlay") || glos.textures.getLength("overlay")!=sls)glos.newTexture("overlay",sls);
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
		
		if(!glos.textures.containsPboKey("image") || deletePBOs || glos.textures.getPboLength("image")!=frms) {
			glos.textures.newPbo("image", frms);
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
						sliceImage=getImageBufferSlice(imp.getZ(), imp.getT());
						checkBuffers();
						int psize=tex4div(imageWidth)*tex4div(imageHeight)*((pixelType==PixelType.INT_RGB10A2)?1:COMPS);
						int bsize=sls*psize;
						if(imageFBs[fr]==null) {
							if(pixelType==PixelType.FLOAT)imageFBs[fr]=GLBuffers.newDirectFloatBuffer(bsize);
							else if(pixelType==PixelType.SHORT)imageFBs[fr]=GLBuffers.newDirectShortBuffer(bsize);
							else if(pixelType==PixelType.BYTE)imageFBs[fr]=GLBuffers.newDirectByteBuffer(bsize);
							else if(pixelType==PixelType.INT_RGB10A2)imageFBs[fr]=GLBuffers.newDirectIntBuffer(bsize);
						}
						updateImageStackBuffer(imageFBs[fr],sliceImage,sl+1);
						int offset=psize*sl;
						try {
							glos.textures.updateSubRgbaPBO("image",fr, imageFBs[fr],offset, psize, bsize);
							updatedBuffersSlices[fr*sls+sl]=true;
						}catch(Exception e) {
							if(e instanceof GLException) {
								GLException gle=(GLException)e;
								IJ.log(gle.getMessage());
								IJ.log("Out of memory, switching usePBOforSlices off");
								usePBOforSlices=false;
								imageFBs[fr]=null; 
								glos.textures.disposePbo("image");
								glos.textures.newPbo("image", frms);
								updatedBuffersSlices=new boolean[sls*frms];
							}
						}
					}else loadtex=true;
				}else {
					sliceImage=getImageBufferSlice(imp.getZ(), imp.getT());
				}
				if(loadtex)glos.textures.loadTexFromPBO("image",fr, tex4div(imageWidth), tex4div(imageHeight), 1, sl, pixelType, COMPS);
				else {
					gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
					glos.textures.createRgbaTexture("image", sliceImage, tex4div(imageWidth), tex4div(imageHeight), 1, COMPS);
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
					glos.textures.updateRgbaPBO("image", ifr, imageFBs[ifr]);
					updatedBuffers[ifr]=false;
					IJ.showStatus("PBO load");
				}
			}
			glos.textures.loadTexFromPBO("image", fr, tex4div(imageWidth/undersample), tex4div(imageHeight/undersample), sls, 0, pixelType3d, COMPS);
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
		gl.glUseProgram(programs[0].name);
		gl.glDisable(GL_SCISSOR_TEST);
		gl.glDrawBuffers(1, new int[] {GL4.GL_BACK}, 0);
		gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
		//gl.glClearBufferfv(GL4.GL_COLOR, 0, new float[] {0f,0f,0f,0f},0);
        //gl.glClearBufferfv(GL4.GL_DEPTH, 0, new float[] {0f},0);
		
		int views=1;
		if(go3d && stereoType.ordinal()>0)views=2;
		for(int stereoi=0;stereoi<views;stereoi++) {
			if(go3d) {
				
				if(stereoType==StereoType.QUADBUFFER) {
					if(stereoi==0)gl.glDrawBuffers(1, new int[] {GL4.GL_BACK_LEFT}, 0);
					else gl.glDrawBuffers(1, new int[] {GL4.GL_BACK_RIGHT}, 0);
				}else if(stereoType==StereoType.CARDBOARD) {
					float[] ortho = FloatUtil.makeOrtho(new float[16], 0, false, -CB_MAXSIZE, CB_MAXSIZE, -CB_MAXSIZE*yrat, CB_MAXSIZE*yrat, -CB_MAXSIZE, CB_MAXSIZE);
					float[] translate=FloatUtil.makeTranslation(new float[16], 0, false, (stereoi==0?(-CB_MAXSIZE*CB_TRANSLATE):(CB_MAXSIZE*CB_TRANSLATE)), 0f, 0f);
					ortho=FloatUtil.multMatrix(ortho, translate);
					gl.glEnable(GL_SCISSOR_TEST);
					int height=drawable.getSurfaceHeight();
					int y=(int)((1f-(1f/CB_MAXSIZE))*yrat/2f*(float)height);
					height/=CB_MAXSIZE;
					gl.glScissor((drawable.getSurfaceWidth()/2)-(int)(drawable.getSurfaceWidth()/CB_MAXSIZE/2f) + (int)(CB_TRANSLATE*drawable.getSurfaceWidth()/2f*(stereoi==0?-1:1)), y, (int)(drawable.getSurfaceWidth()/CB_MAXSIZE), height);
					glos.buffers.loadMatrix("global", ortho);
				}else if(stereoType==StereoType.ANAGLYPH) {
					gl.glUseProgram(programs[2].name);
					gl.glUniform1i(anaSiLoc, stereoi);
					gl.glUniformMatrix3fv(anaLoc, 2, false, anaColors, 0);
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
					//for(int i=0;i<vertb.limit()/6;i++) {
					//	if(i==4)i=vertb.limit()/6-4;
					//	IJ.log((reverse?"rev ":"")+"Vert"+i+" vx"+vertb.get(i*6)+" vy"+vertb.get(i*6+1)+" vz"+vertb.get(i*6+2)+
					//	" tx"+vertb.get(i*6+3)+" ty"+vertb.get(i*6+4)+" tz"+vertb.get(i*6+5));
					//}
				}
				ltr=new boolean[] {left,top,reverse};
				
				//Blend
				gl.glEnable(GL4.GL_BLEND);
				if(renderFunction.equals("MAX")) {
					gl.glBlendEquation(GL4.GL_MAX);
					gl.glBlendFunc(GL4.GL_SRC_COLOR, GL4.GL_DST_COLOR);
				}else if(renderFunction.equals("ALPHA")) {
					gl.glBlendEquation(GL4.GL_FUNC_ADD);
					gl.glBlendFunc(GL4.GL_SRC_ALPHA, GL4.GL_ONE_MINUS_SRC_ALPHA);
				}
				
			}else {
				gl.glDisable(GL4.GL_BLEND);
				lim=initVerts.length;
				boolean push=false;
				if(!srcRect.equals(prevSrcRect))push=true;
				if(push) {
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
			for(int i=0;i<4;i++) {
				float min=0,max=0,color=0;
				if(luts==null || bitd==24) {
					lutMatrixPointer.putFloat(0f);
					lutMatrixPointer.putFloat(1f);
					lutMatrixPointer.putFloat(i==0?1:i==1?2:i==2?4:0);
					lutMatrixPointer.putFloat(0f);
				}else {
					if(i<luts.length) {
						int rgb=luts[i].getRGB(255);
						if(active[i] && !(cmode!=IJ.COMPOSITE && imp.getC()!=(i+1))) {
							if(cmode==IJ.GRAYSCALE)color=7;
							else color=(((rgb & 0x00ff0000)==0x00ff0000)?1:0) + (((rgb & 0x0000ff00)==0x0000ff00)?2:0) + (((rgb & 0x000000ff)==0x000000ff)?4:0);
						}
						min=(float)(luts[i].min/topmax);
						max=(float)(luts[i].max/topmax);
					}
					lutMatrixPointer.putFloat(min);
					lutMatrixPointer.putFloat(max);
					lutMatrixPointer.putFloat(color);
					lutMatrixPointer.putFloat(0f);
				}
			}
			
			glos.bindUniformBuffer("global", 1);
			glos.bindUniformBuffer("model", 2);
			glos.bindUniformBuffer("lut", 3);
			glos.drawTexVao("image", GL_UNSIGNED_SHORT, lim/4);
			glos.unBindBuffer(GL_UNIFORM_BUFFER, 1);
			glos.unBindBuffer(GL_UNIFORM_BUFFER, 2);
			glos.unBindBuffer(GL_UNIFORM_BUFFER, 3);
			
			if(roi!=null || overlay!=null) { 
				float z=0f;
				float zf=(float)(cal.pixelDepth/cal.pixelWidth)/srcRect.width;
				if(go3d) z=((float)sls-2f*sl)*zf;
				gl.glEnable(GL4.GL_MULTISAMPLE);
				gl.glBlendEquation(GL4.GL_FUNC_ADD);
				gl.glBlendFunc(GL4.GL_SRC_ALPHA, GL4.GL_ONE_MINUS_SRC_ALPHA);
				if(!JCP.openglroi) {
					if(doRoi)drawGraphics(gl, z, "roi", 0);
					if(doOv!=null) {
						for(int osl=0;osl<sls;osl++) {
							if(doOv[osl]) {
								drawGraphics(gl, ((float)sls-2f*(float)osl)*zf, "overlay", osl);
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
									drawRoiGL(drawable, oroi, ((float)sls-2f*(float)(rz-1))*zf, false, anacolor);
								}
							}else {
								if((rc==0||rc==imp.getC()) && (rz==0||(rz)==imp.getZ()) && (rt==0||(rt)==imp.getT()))drawRoiGL(drawable, oroi, z, false, anacolor);
							}
						}
					}
					drawRoiGL(drawable, roi, z, true, anacolor);
				}
			}
			boolean nzi=(!myHZI && (srcRect.width<imageWidth || srcRect.height<imageHeight));
			
			if(nzi) {
				gl.glDisable(GL4.GL_MULTISAMPLE);
				drawMyZoomIndicator(drawable);
			}
			//IJ.log("\\Update0:Display took: "+(System.nanoTime()-starttime)/1000000L+"ms");		
			gl.glFinish();
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
		while(updatingBuffers>0)IJ.wait(50);
		undersample=us;
		resetBuffers();
	}
	
	private int tex4div(int wh) {
		return wh+((wh%4)>0?(4-wh%4):0);
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
			myImageUpdated=true;
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
		int width=tex4div(imageWidth), height=tex4div(imageHeight);
		int offset=(slice-1)*width*height*((stackBuffer instanceof IntBuffer)?1:COMPS);
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
		int stsl=0,endsl=imp.getNSlices(),stfr=frame-1;
		if(frame==0) {stsl=0; endsl=1;stfr=0;frame=imp.getNFrames();}
		return getImageBuffer(is3d, stsl, endsl, stfr, frame, buffer, false);
	}
	
	//just return a new buffer for the one slice
	protected Buffer getImageBufferSlice(int slice, int frame) {
		return getImageBuffer(false, slice-1, slice, frame-1, frame, null, true);
	}
	
	//If there is a buffer, it should be for one whole frame, otherwise one slice or whole frame
	public Buffer getImageBuffer(boolean is3d, int stsl, int endsl, int stfr, int endfr, Buffer buffer, boolean notdirect) {
		PixelType type=this.pixelType;
		int bits=imp.getBitDepth();
		int width=imageWidth, height=imageHeight;
		if(is3d) {
			width/=undersample; height/=undersample;
			type=this.pixelType3d;
		}
		width=tex4div(width); height=tex4div(height);
		int chs=imp.getNChannels();
		COMPS=bits==24?3:chs;
		int size=width*height*COMPS*(endsl-stsl)*(endfr-stfr);
		Object outPixels;
		if(bits==8)outPixels=new byte[size];
		else if(bits==16)outPixels=new short[size];
		else if(bits==24) {size/=COMPS; outPixels=new int[size];}
		else outPixels=new float[size];
		ImageStack imst=imp.getStack();
		for(int fr=stfr;fr<endfr; fr++) {
			for(int csl=stsl;csl<endsl;csl++) {
				int offset=((csl-stsl))*width*height*chs+(fr-stfr)*(endsl-stsl)*width*height*chs;
				for(int i=0;i<chs;i++) {
					ImageProcessor ip=imst.getProcessor(imp.getStackIndex(i+1, csl+1, fr+1));
					Object pixels=ip.getPixels();
					
					if(is3d) {
						pixels=convertForUndersample(pixels,imageWidth,imageHeight);
					}
					addPixels(outPixels, width, pixels, imageWidth, imageHeight, offset, i, chs);
					
				}
			}
		}

		if(type==PixelType.BYTE && (buffer==null || buffer.limit()!=size)) {
			if(notdirect)buffer=ByteBuffer.allocate(size);
			else buffer=GLBuffers.newDirectByteBuffer(size);
		}else if(type==PixelType.SHORT && (buffer==null || buffer.limit()!=size)) {
			if(notdirect)buffer=ShortBuffer.allocate(size);
			else buffer=GLBuffers.newDirectShortBuffer(size);
		}else if(type==PixelType.INT_RGB10A2 && (buffer==null || buffer.limit()!=size)) {
			if(notdirect)buffer=IntBuffer.allocate(size/COMPS);
			else buffer=GLBuffers.newDirectIntBuffer(size/COMPS);
		}else if(type==PixelType.FLOAT && (buffer==null || buffer.limit()!=size)) {
			if(notdirect)buffer=FloatBuffer.allocate(size);
			else buffer=GLBuffers.newDirectFloatBuffer(size);
		}
		
		buffer.position(0);
		if(type==PixelType.BYTE) {
			if(bits==8)((ByteBuffer)buffer).put(((byte[])outPixels));
			else {
				for(int i=0;i<size;i++) {
					if(bits==16)((ByteBuffer)buffer).put((byte)(((int)((((short[])outPixels)[i]&0xffff)/65535.0*255.0))));
					else if(bits==32)((ByteBuffer)buffer).put((byte)(((int)(((float[])outPixels)[i]*255f))));
					else {
						int rgb=((int[])outPixels)[i];
						((ByteBuffer)buffer).put((byte)((rgb&0xff0000)>>16)).put((byte)((rgb&0xff00)>>8)).put((byte)(rgb&0xff));
						//if(COMPS==4)((ByteBuffer)buffer).put((byte)((rgb&0xff000000)>>24));
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
					int red=(((int)((shortPixels[i]&0xffff)/65535f*1023f))&0x3ff);
					int green=(COMPS<2)?0:(((int)((shortPixels[i+1]&0xffff)/65535f*1023f))&0x3ff);
					int blue=(COMPS<3)?0:(((int)((shortPixels[i+2]&0xffff)/65535f*1023f))&0x3ff);
					int alpha=1;
					//if(COMPS==4) alpha=(((int)((shortPixels[i+3]&0xffff)/65535f*0x3))&0x3);
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
	
	private void drawGraphics(GL4 gl, float z, String name, int index) {
		float yrat=(float)srcRect.height/srcRect.width;
		FloatBuffer vb=GLBuffers.newDirectFloatBuffer(new float[] {
				-1,	-yrat,	z, 	0,1,0.5f,
				1,	-yrat,	z, 	1,1,0.5f,
				1,	yrat,	z, 	1,0,0.5f,
				-1,	yrat,	z,	0,0,0.5f
		});
		gl.glUseProgram(programs[3].name);
		gl.glTexParameteri(GL4.GL_TEXTURE_3D, GL4.GL_TEXTURE_MAG_FILTER, GL4.GL_NEAREST);
		gl.glBindBufferBase(GL_UNIFORM_BUFFER, 1, glos.buffers.get(GL_UNIFORM_BUFFER, "global"));
		gl.glBindBufferBase(GL_UNIFORM_BUFFER, 2, glos.buffers.get(GL_UNIFORM_BUFFER, "model"));
		glos.drawTexVao(name, index, vb);
		gl.glBindBufferBase(GL_UNIFORM_BUFFER, 1, 0);
		gl.glBindBufferBase(GL_UNIFORM_BUFFER, 2, 0);
		if(Prefs.interpolateScaledImages)gl.glTexParameteri(GL4.GL_TEXTURE_3D, GL4.GL_TEXTURE_MAG_FILTER,GL4.GL_LINEAR);
		gl.glUseProgram(0);
		
	}
	

	
	//from https://github.com/jvm-graphics-labs/hello-triangle/blob/master/src/main/java/gl/HelloTriangleSimple.java
	private class Program {

        public int name = 0;

        public Program(GL4 gl, String root, String vertex, String fragment) {

            ShaderCode vertShader = ShaderCode.create(gl, GL4.GL_VERTEX_SHADER, this.getClass(), root, null, vertex,
                    "vert", null, true);
            ShaderCode fragShader = ShaderCode.create(gl, GL4.GL_FRAGMENT_SHADER, this.getClass(), root, null, fragment,
                    "frag", null, true);

            ShaderProgram shaderProgram = new ShaderProgram();

            shaderProgram.add(vertShader);
            shaderProgram.add(fragShader);

            shaderProgram.init(gl);

            name = shaderProgram.program();

            shaderProgram.link(gl, System.err);
        }
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
		GL4 gl=drawable.getGL().getGL4();
		if(rgldu==null)rgldu=new RoiGLDrawUtility(imp);
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

		zoomIndVerts.rewind();
		float[] color=new float[] {(float)128/255, (float)128/255, 1f, 77f/255f};
		zoomIndVerts.put(x1).put(y1).put(0f).put(color);
		zoomIndVerts.put(x1+w1).put(y1).put(0f).put(color);
		zoomIndVerts.put(x1+w1).put(y1-h1).put(0f).put(color);
		zoomIndVerts.put(x1).put(y1-h1).put(0f).put(color);
		zoomIndVerts.rewind();
		

		gl.glUseProgram(programs[1].name);
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

		gl.glUseProgram(0);
		gl.glBindBufferBase(GL_UNIFORM_BUFFER, 1, 0);
		gl.glBindBufferBase(GL_UNIFORM_BUFFER, 2, 0);
		
	}
	
	private void drawRoiGL(GLAutoDrawable drawable, Roi roi, float z, boolean drawHandles, Color anacolor) {
		if(roi==null)return;
		if(rgldu==null)rgldu=new RoiGLDrawUtility(imp);
		
		GL4 gl=drawable.getGL().getGL4();
		gl.glUseProgram(programs[1].name);
		glos.bindUniformBuffer("global", 1);
		glos.bindUniformBuffer("model", 2);
		
		rgldu.drawRoiGL(drawable, roi, z, drawHandles, anacolor);
		

		gl.glUseProgram(0);
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
