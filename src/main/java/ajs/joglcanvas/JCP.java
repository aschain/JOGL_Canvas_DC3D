package ajs.joglcanvas;

import java.util.List;
import java.util.ArrayList;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import static com.jogamp.opengl.GL.GL_VERSION;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GL2ES2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLCapabilitiesImmutable;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.math.FloatUtil;
import com.jogamp.opengl.util.gl2.GLUT;
import com.jogamp.opengl.GLDrawableFactory;
import com.jogamp.opengl.GLEventListener;

import ij.ImagePlus;
import ij.Menus;
import ij.IJ;
import ij.Prefs;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.ImageCanvas;
import ij.gui.StackWindow;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;



public class JCP implements PlugIn {

	public static JOGLCanvasService listenerInstance=null;
	public static String defaultBitString="default";
	public static String glProfileName=Prefs.get("ajs.joglcanvas.glProfileName", "");
	public static int undersample=(int)Prefs.get("ajs.joglcanvas.undersample", 1.0);
	public static String renderFunction=Prefs.get("ajs.joglcanvas.renderFunction", "MAX");
	public static boolean openglroi=Prefs.get("ajs.joglcanvas.openglroi", false);
	public static boolean usePBOforSlices=Prefs.get("ajs.joglcanvas.usePBOforSlices", false);
	public static Color leftAnaglyphColor=new Color((int) Prefs.get("ajs.joglcanvas.leftAnaglyphColor",Color.RED.getRGB()));
	public static Color rightAnaglyphColor=new Color((int) Prefs.get("ajs.joglcanvas.rightAnaglyphColor",Color.CYAN.getRGB()));
	public static boolean dubois=Prefs.get("ajs.joglcanvas.dubois", false);
	public static float stereoSep=(float)Prefs.get("ajs.joglcanvas.stereoSep", 0.05);
	public static String version="";
	public static String defaultVersion="";
	public static String glslVersion="",glslDefVersion="";
	public static float[][] anaColors;
	public static boolean go3d=Prefs.get("ajs.joglcanvas.go3d", false);
	public static boolean preferStereo=Prefs.get("ajs.joglcanvas.preferStereo", false);
	public static boolean wrappedBuffers=Prefs.get("ajs.joglcanvas.wrappedBuffers", true);
	public static boolean doFrustum=Prefs.get("ajs.joglcanvas.doFrustum", false);
	public static boolean qbfullscreen=Prefs.get("ajs.joglcanvas.qbfullscreen", false);
	public static int drawCrosshairs=(int)Prefs.get("ajs.joglcanvas.drawCrosshairs", 0);
	public static boolean debug=false;
	private static final float[][] duboisColors = new float[][] {
		 {0.456f, -0.04f, -0.015f,
		 0.5f, -0.038f, -0.021f,
		 0.176f, -0.016f, -0.005f},
		 
		{-0.043f,  0.378f, -0.072f,
		-0.088f,  0.734f, -0.113f,
		-0.002f,  0.018f,  1.226f}};
		
	/**
	 * This method gets called by ImageJ / Fiji.
	 *
	 * @param arg can be specified in plugins.config
	 * @see ij.plugin.PlugIn#run(java.lang.String)
	 */
	@Override
	public void run(String arg) {
		
		fillAnaColors();
		
		if(arg.equals("setprefs")) {
			preferences();
			return;
		}

		ImagePlus imp=WindowManager.getCurrentImage();
		if(imp!=null) {
			if(imp.getCanvas() instanceof JOGLImageCanvas) {
				if(arg.equals("")||arg.equals("MirrorWindow")) {
					((JOGLImageCanvas)imp.getCanvas()).revert();
					if(arg.equals(""))return;
				}
			}
		}

		if(arg.equals("MirrorWindow")) {
			addJOGLCanvasMirror(imp);
			return;
		}
		if(imp!=null) {
			convertToJOGLCanvas(imp);
		}

	}
	
	public static void startListener() {
		if(listenerInstance!=null) {
			IJ.log("Restarting JOGL Canvas DC3D service...");
			ImagePlus.removeImageListener(listenerInstance);
			listenerInstance=null;
		}
		listenerInstance=new JOGLCanvasService();
		ImagePlus.addImageListener(listenerInstance);
		IJ.log("JOGL Canvas DC3D service started...");
	}
	
	public static void stopListener() {
		if(listenerInstance!=null) {
			ImagePlus.removeImageListener(listenerInstance);
			listenerInstance=null;
			IJ.log("JOGL Canvas DC3D service stopped...");
		}
	}

	private static void convertToJOGLCanvas(ImagePlus imp, boolean doMirror) {
		if(imp==null)return;
		JOGLImageCanvas jic=getJOGLImageCanvas(imp);
		if(jic!=null) {
			boolean isMirror=jic.isMirror;
			jic.revert();
			if(isMirror==doMirror) return;
		}
		if(IJ.isLinux())System.setProperty("jogl.disable.openglcore", "true"); //avoids this bug https://github.com/processing/processing/issues/5476
		String classname= imp.getWindow().getClass().getSimpleName();
		if(classname.equals("ImageWindow") || classname.equals("StackWindow")) {
			if(imp.getNChannels()>6) {
				IJ.error("JOGL Canvas currently limited to 6 channels");
				return;
			}
			java.awt.EventQueue.invokeLater(new Runnable() {
			    @Override
			    public void run() {
			    	boolean changes=imp.changes;
					boolean prompt=false;
					ij.gui.Roi roi=imp.getRoi();
					if(roi!=null && roi instanceof ij.gui.PointRoi) {
						prompt=((ij.gui.PointRoi)roi).promptBeforeDeleting();
						((ij.gui.PointRoi)roi).promptBeforeDeleting(false);
					}
					imp.changes=false;
					JOGLImageCanvas jic=new JOGLImageCanvas(imp,doMirror);
					if(doMirror)
						new StackWindow(imp,jic);
					else
						new JCStackWindow(imp, jic);
					imp.changes=changes;
					if(prompt)((ij.gui.PointRoi)roi).promptBeforeDeleting(true);
			    }
			});
		}
	}
	
	public static void convertToJOGLCanvas(ImagePlus imp) {
		if(IJ.shiftKeyDown()) {convertToJOGLCanvas(imp,true);}
		else convertToJOGLCanvas(imp,false);
	}
	
	public static void addJOGLCanvasMirror(ImagePlus imp) {
		convertToJOGLCanvas(imp,true);
	}
	
	public static JOGLImageCanvas getJOGLImageCanvas(ImagePlus imp) {
		if(imp==null)return null;
		ImageCanvas ic=imp.getCanvas();
		if(ic instanceof JOGLImageCanvas)return (JOGLImageCanvas)ic;
		Object jic=imp.getProperty("JOGLImageCanvas");
		if(jic instanceof JOGLImageCanvas)return (JOGLImageCanvas)jic;
		return null;
	}
	
	public static void addJCPopups() {
		addJCPopup("Convert to JOGL Canvas");
		addJCPopup("Show JOGL Canvas Mirror");
	}
	
	public static void addJCPopup(String action) {
		action=actionConverter(action);
		if(action.contentEquals("")) {IJ.log("usage: addJCPopup(\"convert\" or \"mirror\")");return;}
		if(hasInstalledPopup(action))return;
		Object popup=getIJPopupMenu();
		ActionListener al=null;
		if(action.toLowerCase().contains("convert")) {
			action="Convert to JOGL Canvas";
			al=new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					JCP.convertToJOGLCanvas(WindowManager.getCurrentImage());
				}
			};
			if(popup instanceof PopupMenu) {
				MenuItem dcmi=new MenuItem(action);
				dcmi.addActionListener(al);
				((PopupMenu)popup).add(dcmi);
			}else if(popup instanceof JPopupMenu){
				JMenuItem dcmi=new JMenuItem(action);
				dcmi.addActionListener(al);
				((JPopupMenu)popup).add(dcmi);
			}
		}
		if(action.toLowerCase().contains("mirror")){
			action="Show JOGL Canvas Mirror";
			al=new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					JCP.addJOGLCanvasMirror(WindowManager.getCurrentImage());
				}
			};
			if(popup instanceof PopupMenu) {
				MenuItem dcmi=new MenuItem(action);
				dcmi.addActionListener(al);
				((PopupMenu)popup).add(dcmi);
			}else if(popup instanceof JPopupMenu){
				JMenuItem dcmi=new JMenuItem(action);
				dcmi.addActionListener(al);
				((JPopupMenu)popup).add(dcmi);
			}
		}
	}
	
	public static void removeJCPopup(String action) {
		getJCMenuItem(action, true);
	}
	
	private static String actionConverter(String action) {
		if(action.toLowerCase().contains("convert"))return "Convert to JOGL Canvas";
		if(action.toLowerCase().contains("mirror"))return "Show JOGL Canvas Mirror";
		return "";
	}
	
	public static Object getIJPopupMenu() {
		Method gpm=null;
		Object popup=null;
		try {
			gpm=Menus.class.getMethod("getPopupMenu");
			popup = gpm.invoke(null, new Object[0]);
		} catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			e.printStackTrace();
		}
		return popup;
	}
	
	private static Object getJCMenuItem(String action, boolean remove) {
		action=actionConverter(action);
		if(action.contentEquals(""))return null;
		Object popup=getIJPopupMenu();
		if(popup instanceof PopupMenu) {
			PopupMenu apopup=(PopupMenu)popup;
			for(int i=0;i<apopup.getItemCount();i++) {
				MenuItem a=apopup.getItem(i);
				if(a.getLabel().equals(action)) {
					if(remove)apopup.remove(i);
					return a;
				}
			}
		}else if(popup instanceof JPopupMenu) {
			JPopupMenu apopup=(JPopupMenu)popup;
			for(int i=0;i<apopup.getComponentCount();i++) {
				Component a=apopup.getComponent(i);
				if(a instanceof JMenuItem && ((JMenuItem)a).getText().equals(action)) {
					if(remove)apopup.remove(i);
					return a;
				}
			}
		}
		return null;
	}
	
	static boolean hasInstalledPopup(String action) {
		action=actionConverter(action);
		if(action.contentEquals(""))return false;
		if(getJCMenuItem(action, false)!=null)return true;
		return false;
	}
	
	public static void openTestImage() {
		int w=1024,h=512;
		int min=1538, addmax=1024, ipmax=4096;
		int n=4, fac=2;
		ImagePlus testimp=IJ.createImage("JCP test image", "16-bit", w, h, 1);
		ImageProcessor ip=testimp.getProcessor();
		int[] divs=new int[n];
		for(int i=0;i<n;i++){divs[i]=(int)Math.pow(2, fac*i);}
		for(int y=0;y<h;y++) {ip.set(0,y,0);ip.set(w-1,y,ipmax-1);}
		int end=w-1; //w-1
		for(int x=1;x<end;x++){
			int value=min+x*addmax/w;
			for(int y=0;y<h;y++) {
				// first nth 12bit, further nth part is 12-(n*4) bit
				int div=divs[y*n/h];
				value=(int)(((double)value+0.5*(double)div)/div)*div;
				ip.set(x,y,value);
			}
		}
		ip.setMinAndMax(0, ipmax-1);
		testimp.setColor(new Color(255,255,255));
		for(int ni=0;ni<n;ni++)ip.drawString(""+(12-(ni*fac))+"-bit", 0, ip.getFontMetrics().getHeight()+ni*h/n);
		testimp.show();
	}
	
	public static GLCapabilities getGLCapabilities() {
		if(IJ.isLinux())System.setProperty("jogl.disable.openglcore", "true"); //avoids this bug https://github.com/processing/processing/issues/5476
		fillAnaColors();

		GLProfile glProfile=null;
		if(!glProfileName.contentEquals("")) glProfile=GLProfile.get(glProfileName);
		//else glProfile = GLProfile.getDefault();
		else glProfile = GLProfile.getMaxProgrammable(true);
		if(!glProfile.isGL2ES2()) {
			IJ.showMessage("Deep Color requires at least OpenGL 2ES2");
			return null;
		}
		GLCapabilities glCapabilities = new GLCapabilities( glProfile );
		
		//If setprefs is run before glCapabilites was defined, defaultBitString might be the intended bits
		//Otherwise it will still be "default" and then get it from preferences
		if(defaultBitString.equals("default")) defaultBitString=Prefs.get("ajs.joglcanvas.colordepths",defaultBitString);
		//If it is still not defined then ask
		if(defaultBitString.contentEquals("default")) {
			preferences();
			if(!glProfileName.contentEquals("")) glProfile=GLProfile.get(glProfileName);
			else glProfile = GLProfile.getMaxProgrammable(true);
			glCapabilities = new GLCapabilities( glProfile );
		}
		if(defaultBitString==null || defaultBitString.equals("default")) return null;
		setGLCapabilities(glCapabilities, defaultBitString);
		IJ.log("Starting JOGL with Settings:\n   "+glCapabilities);
		return glCapabilities;
	}
	
	public static void setGLCapabilities(GLCapabilities glCapabilities, String bitdepthstr) {

		String[] bitdepths=bitdepthstr.split(",");
		if(bitdepths.length!=4)return;

		if(glCapabilities==null)return;
		int b=Integer.parseInt(bitdepths[2]), g=Integer.parseInt(bitdepths[1]), r=Integer.parseInt(bitdepths[0]), a=Integer.parseInt(bitdepths[3]);
		glCapabilities.setBlueBits(b);
		glCapabilities.setGreenBits(g);
		glCapabilities.setRedBits(r);
		glCapabilities.setAlphaBits(a);
		glCapabilities.setHardwareAccelerated(true);
		glCapabilities.setDoubleBuffered(true);
		glCapabilities.setStencilBits(1);
		glCapabilities.setSampleBuffers(true);
		glCapabilities.setNumSamples(4);
		glCapabilities.setStereo(preferStereo);
		if(preferStereo && ((g+r+b)>26))IJ.log("JOGLImageCanvas Warning: Active stereo disables HDR 10-bit, disable it in prefs if you prefer HDR");
	}

	
	public static void getGLVersion(boolean max) {
		IJ.log("Getting OpenGL version...");
		boolean glCisnull=false;
		if(IJ.isLinux())System.setProperty("jogl.disable.openglcore", "true"); //avoids this bug https://github.com/processing/processing/issues/5476
		glCisnull=true;
		GLProfile.initSingleton();
		GLProfile glProfile=null;
		if(max) glProfile= GLProfile.getMaxProgrammable(true);
		else glProfile= GLProfile.getDefault();
		if(!glProfile.isGL2ES2()) IJ.showMessage("Deep Color requires at least OpenGL 2 ES2");
		GLCapabilities glCapabilities = new GLCapabilities( glProfile );
		Frame win=new Frame();
		win.setSize(100,100);
		GLCanvas glc=new GLCanvas(glCapabilities);
		glc.addGLEventListener(new GLEventListener() {
			@Override
			public void init(GLAutoDrawable drawable) {
				String ver=drawable.getGL().glGetString(GL_VERSION), glslver=drawable.getGL().glGetString(GL2.GL_SHADING_LANGUAGE_VERSION);
				if(max) {
					JCP.version=ver;
					JCP.glslVersion=glslver;
				}else {
					JCP.defaultVersion=ver;
					JCP.glslDefVersion=glslver;
				}
				IJ.log("\\Update:"+ver);
				IJ.log(glslver);
			}
			@Override
			public void dispose(GLAutoDrawable drawable) {}
			@Override
			public void display(GLAutoDrawable drawable) {
				GL gl=drawable.getGL();
				if(gl.isGL2()) {
					GL2 gl2=drawable.getGL().getGL2();
					gl2.glBegin(GL2.GL_LINE);
					gl2.glVertex2f(-1, -1);
					gl2.glVertex2f(1,1);
					gl2.glEnd();
				}else if(gl.isGL3()) {
					gl.getContext();
				}
			}
			@Override
			public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {}
			
		});
		win.add(glc);
		win.setVisible(true);
		while(!glc.areAllGLEventListenerInitialized()) {
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}}
		win.dispose();
		if(glCisnull)glCapabilities=null;
	}
	
	public static void preferences() {
		//if(version.equals("")) {getGLVersion(false); getGLVersion(true);}
		if(IJ.isLinux())System.setProperty("jogl.disable.openglcore", "true"); //avoids this bug https://github.com/processing/processing/issues/5476
		String defaultstr=defaultBitString;
		if(defaultstr.equals("default"))defaultstr=Prefs.get("ajs.joglcanvas.colordepths","8,8,8,8");
		if(defaultstr.equals("default"))defaultstr="8,8,8,8";
		GLProfile def=GLProfile.getDefault();
		GLProfile max=GLProfile.getMaxProgrammable(true);
		List<GLCapabilitiesImmutable> glcList=GLDrawableFactory.getFactory(def).getAvailableCapabilities(null);
		if(glcList.size()==0)glcList=GLDrawableFactory.getFactory(max).getAvailableCapabilities(null);
		ArrayList<String> bitdepths=new ArrayList<String>();
		bitdepths.add(defaultstr);
		for(int i=0;i<glcList.size();i++) {
			boolean add=true;
			GLCapabilitiesImmutable glc=glcList.get(i);
			String tempstr=""+glc.getRedBits()+","+glc.getGreenBits()+","+glc.getBlueBits()+","+glc.getAlphaBits();
			for(int j=0;j<bitdepths.size();j++) {if(bitdepths.get(j).equals(tempstr)) {add=false; break;}}
			if(add)bitdepths.add(tempstr);
		}
		ArrayList<String> profiles=new ArrayList<String>();
		String defprof="";
		for(String prof : GLProfile.GL_PROFILE_LIST_ALL) {
			if(GLProfile.isAvailable(prof)) {
				if(prof.contentEquals(def.getImplName())) {prof=prof+" (Default)"; defprof=prof;}
				else if(prof.contentEquals(max.getImplName())) {prof=prof+" (Max Default)"; defprof=prof;}
				profiles.add(prof);
			}
		}
		GenericDialog gd=new GenericDialog("JOGL Canvas Deep Color 3D Display Options");
		//gd.addMessage("GL Ver: "+GLProfile.getDefault().getImplName());
		//gd.addMessage("GL Default Ver: "+GLProfile.getMaxProgrammable(true).getImplName());
		//if(!version.equals(""))gd.addMessage("GL Ver: "+version);
		//if(!defaultVersion.equals(""))gd.addMessage("GL Default Ver: "+defaultVersion);
		gd.addMessage("You can choose a specific GLProfile instead of the default if you like:");
		boolean hasPref=!Prefs.get("ajs.joglcanvas.glProfileName", "").contentEquals("");
		if(profiles.size()>1) {
			gd.addChoice("GLProfile:", profiles.toArray(new String[profiles.size()]), glProfileName.contentEquals("")?defprof:glProfileName);
			gd.addCheckbox("Always use the selected profile?", hasPref);
			if(hasPref)gd.addCheckbox("Clear saved profile", false);
		}
		gd.addMessage("For High-bit Monitors:\nChoose the color bit depths from those available\nChoices are bits for R,G,B,A respectively");
		gd.addChoice("Bitdepths:", bitdepths.toArray(new String[bitdepths.size()]), bitdepths.get(0));
		gd.addStringField("Or enter R,G,B,A if you are sure (e.g. 10,10,10,2)", "");
		gd.addMessage("Service:");
		gd.addCheckbox("Run on all opened images?", listenerInstance!=null);
		gd.addMessage("Add to ImageJ Popup Menu:");
		gd.addCheckbox("Convert to JOGL Canvas", hasInstalledPopup("convert"));
		gd.addCheckbox("Add JOGL Canvas Mirror", hasInstalledPopup("mirror"));
		gd.addMessage("Other Settings:");
		gd.addCheckbox("3D on by default?", go3d);
		gd.addChoice("Default 3d Render Type", new String[] {"MAX","ALPHA"}, renderFunction);
		gd.addChoice("Default Undersampling for 3D", new String[] {"None","2","4","6"},undersample==1?"None":(""+undersample));
		gd.addCheckbox("Stereoscopic settings", false);
		gd.addCheckbox("Prefer active stereo over high dynamic range", preferStereo);
		gd.addCheckbox("Open 10-bit test image", false);
		gd.addMessage("Advanced Settings:");
		gd.addCheckbox("Draw ROI with OpenGL (in progress)", openglroi);
		String[] cursorChoices=new String[] {"Off", "Short", "Long"};
		gd.addChoice("Draw cursor crosshairs (requires GL ROI)", cursorChoices, cursorChoices[drawCrosshairs]);
		gd.addCheckbox("Store whole stack in PBO, even for 2D (more video memory but faster)", usePBOforSlices);
		gd.addCheckbox("Use image arrays wrapped in a buffer for video memory", wrappedBuffers);
		gd.addCheckbox("Show some extra debug info", debug);
		gd.showDialog();
		
		if(gd.wasCanceled())return;
		if(profiles.size()>1) {
			glProfileName=gd.getNextChoice().replace(" (Default)","").replace(" (Max Default)", "");
			if(gd.getNextBoolean())Prefs.set("ajs.joglcanvas.glProfileName", glProfileName);
			if(hasPref && gd.getNextBoolean())Prefs.set("ajs.joglcanvas.glProfileName", "");
		}
		String bd=gd.getNextChoice();
		String userbd=gd.getNextString();
		if(!userbd.equals("")) {
			if(userbd.split(",").length!=4) 
				IJ.showMessage("Bit depths string was not valid (need 4 bit depths separated by commas).\n"
							+ "Using "+bd);
			else bd=userbd;
		}
		defaultBitString=bd;
		Prefs.set("ajs.joglcanvas.colordepths",bd);
		if(gd.getNextBoolean()) {
			if(listenerInstance==null)
				startListener();
			else
				IJ.log("JOGL Canvas service running...");
		}else{
			stopListener();
		}
		//PopupMenus
		if(gd.getNextBoolean()) addJCPopup("convert"); else removeJCPopup("convert");
		if(gd.getNextBoolean()) addJCPopup("mirror"); else removeJCPopup("mirror");
		go3d=gd.getNextBoolean();
		Prefs.set("ajs.joglcanvas.go3d", go3d);
		renderFunction=gd.getNextChoice();
		Prefs.set("ajs.joglcanvas.renderFunction", renderFunction);
		String newus=gd.getNextChoice();
		undersample=newus.equals("None")?1:Integer.parseInt(newus);
		Prefs.set("ajs.joglcanvas.undersample", (double)undersample);
		boolean doana=gd.getNextBoolean();
		preferStereo=gd.getNextBoolean();
		Prefs.set("ajs.joglcanvas.preferStereo", preferStereo);
		boolean dotest=gd.getNextBoolean();
		openglroi=gd.getNextBoolean();
		Prefs.set("ajs.joglcanvas.openglroi", openglroi);
		drawCrosshairs=gd.getNextChoiceIndex();
		Prefs.set("ajs.joglcanvas.drawCrosshairs", drawCrosshairs);
		usePBOforSlices=gd.getNextBoolean();
		Prefs.set("ajs.joglcanvas.usePBOforSlices", usePBOforSlices);
		wrappedBuffers=gd.getNextBoolean();
		Prefs.set("ajs.joglcanvas.wrappedBuffers", wrappedBuffers);
		debug=gd.getNextBoolean();
		
		if(doana) anaglyphSettings();
		if(dotest) openTestImage();


		Prefs.savePreferences();
	}
	
	public static void anaglyphSettings(){
		JOGLImageCanvas jic=getJOGLImageCanvas(WindowManager.getCurrentImage());
		JFrame asettings=new JFrame("JOGLCanvas Stereo Options");
		asettings.setSize(500,750);
		
		class MyCanvas extends GLCanvas implements GLEventListener, MouseListener, MouseMotionListener{
			private static final long serialVersionUID = 1L;
			public Color left=leftAnaglyphColor, right=rightAnaglyphColor;
			public int sep=(int)(stereoSep*100f);
			protected int sx,sy;
			protected float dx=0f,dy=0f,dz=0f;
			
			public MyCanvas() {
				addGLEventListener(this);
				addMouseListener(this);
				addMouseMotionListener(this);
			}
			
			@Override
			public void init(GLAutoDrawable drawable) {
				GL2ES2 gl2 = drawable.getGL().getGL2ES2();
				gl2.glClearColor(0f, 0f, 0f, 0f);
			}
			@Override
			public void dispose(GLAutoDrawable drawable) {}
			@Override
			public void display(GLAutoDrawable drawable) {
				GL2 gl2=drawable.getGL().getGL2();
				gl2.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
				gl2.glDisable(GL2ES2.GL_DEPTH_TEST);
				gl2.glEnable(GL2ES2.GL_BLEND);
				gl2.glBlendEquation(GL2.GL_MAX);
				gl2.glBlendFunc(GL2.GL_SRC_COLOR, GL2.GL_DST_COLOR);

				gl2.glMatrixMode(GL2.GL_PROJECTION);
				gl2.glLoadIdentity();
				gl2.glOrtho(-1, 1, -1, 1, -1, 1);
				gl2.glMatrixMode(GL2.GL_MODELVIEW);
				//float rat=((float)drawable.getSurfaceWidth()/drawable.getSurfaceHeight());
				//float frustumZshift=-1.35f*(1.5f);
				
				for(int i=0;i<2;i++) {
					gl2.glLoadIdentity();
					
					//Rotate
					float dxc=dx;
					if(doFrustum) {
						gl2.glMatrixMode(GL2.GL_PROJECTION);
						float nearZ=1f, IOD=(float)sep/100f, g_initial_fov=(float)Math.toRadians(45), depthZ=5f;
						float ftop=(float)Math.tan(g_initial_fov/2)*nearZ;
						float fbottom = -ftop;
						float left_right_direction = (i==0)?-1.0f:1.0f;
						double frustumshift = (IOD/2)*nearZ/depthZ;
						float fright =(float)(ftop+frustumshift*left_right_direction);
						float fleft =-fright;
						float[] g_projection_matrix = FloatUtil.makeFrustum(new float[16], 0, false, fleft, fright, fbottom, ftop, nearZ, depthZ);
						// update the view matrix
						float[] eye=new float[] {left_right_direction*IOD/2, 0, 1};
						float[] center=new float[] {0, 0, -1f};
						float[] up=new float[] {0,1,0};
						float[] g_view_matrix = FloatUtil.makeLookAt(new float[16], 0, eye, 0, center, 0, up, 0, new float[16]);
						gl2.glLoadMatrixf(FloatUtil.multMatrix(g_projection_matrix, g_view_matrix), 0);
						gl2.glMatrixMode(GL2.GL_MODELVIEW);
						gl2.glTranslatef(0f, 0f, -1.5f);
						
					}else {
						dxc+=sep/2f;
						dxc=dx-(float)(i*sep);
					}
					gl2.glRotatef(dxc, 0f, 1.0f, 0f);
					gl2.glRotatef(dy, 1.0f, 0f, 0f);
					gl2.glRotatef(dz, 0f, 0f, 1.0f);
					
					
					Color color=((i==0)?left:right);
					//if(dubois) {
					//	float[] cs=multiplyMatrix(new float[] {1f, 1f,1f},duboisColors[i]);
					//	color=new Color(cs[0]*255f,cs[1]*255f,cs[2]*255f);
					//}
					gl2.glColor3f((float)color.getRed()/255f, (float)color.getGreen()/255f, (float)color.getBlue()/255f);
					
					
					GLUT glut=new GLUT();
					glut.glutWireTeapot(0.5);
				}
			}
			@Override
			public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
				//setSize(width/2,width/4);
			}
			@Override
			public void mousePressed(MouseEvent e) {
				sx = e.getX();
				sy = e.getY();
			}
			@Override
			public void mouseDragged(MouseEvent e) {
				dx+=(float)(e.getX()-sx)/200f*90f;
				sx=e.getX();
				dy+=(float)(e.getY()-sy)/200f*90f;
				sy=e.getY();
				if(dz<0)dz+=360; if(dz>360)dz-=360;
				if(dx<0)dx+=360; if(dx>360)dx-=360;
				if(dy<0)dy+=360; if(dy>360)dy-=360;
				repaint();
			}
			@Override
			public void mouseMoved(MouseEvent e) {}
			@Override
			public void mouseClicked(MouseEvent e) {}
			@Override
			public void mouseReleased(MouseEvent e) {}
			@Override
			public void mouseEntered(MouseEvent e) {}
			@Override
			public void mouseExited(MouseEvent e) {}
		}
		
		MyCanvas canvas=new MyCanvas();
		canvas.setPreferredSize(new Dimension(500,250));
		JSlider[] sds=new JSlider[6];
		sds[0]=new JSlider(JSlider.HORIZONTAL, 0, 255, leftAnaglyphColor.getRed());
		sds[1]=new JSlider(JSlider.HORIZONTAL, 0, 255, leftAnaglyphColor.getGreen());
		sds[2]=new JSlider(JSlider.HORIZONTAL, 0, 255, leftAnaglyphColor.getBlue());
		sds[3]=new JSlider(JSlider.HORIZONTAL, 0, 255, rightAnaglyphColor.getRed());
		sds[4]=new JSlider(JSlider.HORIZONTAL, 0, 255, rightAnaglyphColor.getGreen());
		sds[5]=new JSlider(JSlider.HORIZONTAL, 0, 255, rightAnaglyphColor.getBlue());
		
		ChangeListener cl=new ChangeListener() {
			@Override
			public void stateChanged(ChangeEvent e) {
				int clr=canvas.left.getRed(),clg=canvas.left.getGreen(),clb=canvas.left.getBlue();
				int crr=canvas.right.getRed(),crg=canvas.right.getGreen(),crb=canvas.right.getBlue();
				int update=((JSlider)e.getSource()).getValue();
				JSlider usl=((JSlider)e.getSource());
				canvas.left=new Color(usl==sds[0]?update:clr,usl==sds[1]?update:clg,usl==sds[2]?update:clb);
				canvas.right=new Color(usl==sds[3]?update:crr,usl==sds[4]?update:crg,usl==sds[5]?update:crb);
				canvas.repaint();
			}
		};
		for(int i=0;i<sds.length;i++) {
			sds[i].addChangeListener(cl);
			sds[i].setMajorTickSpacing(50);
			sds[i].setPaintTicks(true);
			sds[i].setPaintLabels(true);
		}

		JSlider sepsl=new JSlider(JSlider.HORIZONTAL, 0, 30, canvas.sep>30?5:canvas.sep);
		sepsl.setMajorTickSpacing(5);
		sepsl.setPaintTicks(true);
		sepsl.setPaintLabels(true);

		sepsl.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent e) {
				canvas.sep=((JSlider)e.getSource()).getValue();
				canvas.repaint();
			}
		});
		
		JPanel panel=new JPanel();
		panel.setLayout(new GridBagLayout());
		GridBagConstraints c=new GridBagConstraints();
		c.gridy=0;
		//c.weighty=1; c.weightx=1;
		//c.gridwidth=9;
		c.gridx=0; c.gridwidth=1; panel.add(new JLabel("   "),c);
		c.gridx=1;  panel.add(new JLabel("Anaglyph Left Eye Color"),c);
		c.gridx=2; panel.add(new JLabel("   "),c);
		c.gridx=3; panel.add(new JLabel("Anaglyph Right Eye Color"),c);
		c.anchor=GridBagConstraints.CENTER;
		for(int i=0;i<3;i++) {
			String clr=(i==0)?"Red":(i==1)?"Green":"Blue";
			c.gridy++;
			c.gridx=0; panel.add(new JLabel(clr),c);
			c.gridx=1; panel.add(sds[i],c);
			c.gridx=2; panel.add(new JLabel(clr),c);
			c.gridx=3; panel.add(sds[i+3],c);
		}
		c.gridy++; 
		c.gridx=0; c.gridwidth=4; panel.add(new JLabel(" "),c);
		c.gridy++; 
		c.gridx=0; c.gridwidth=2; c.anchor=GridBagConstraints.WEST;
		JCheckBox cb=new JCheckBox("Dubois-red-cyan",dubois);
		cb.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent e)  {
				dubois=e.getStateChange()==ItemEvent.SELECTED;
				if(jic!=null) {jic.setStereoUpdated(); jic.repaint();}
			}
		});
		panel.add(cb,c);
		c.gridx=1; c.gridwidth=2; c.anchor=GridBagConstraints.EAST; panel.add(new JLabel("Eye Separation"),c);
		c.gridx=3; c.gridwidth=1; c.anchor=GridBagConstraints.CENTER; panel.add(sepsl,c);

		c.gridy++; c.gridx=0; c.gridwidth=4; panel.add(new JLabel(" "),c);
		cb=new JCheckBox("Perspective instead of orthogonal projection",doFrustum);
		cb.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent e)  {
				doFrustum=e.getStateChange()==ItemEvent.SELECTED;
				Prefs.set("ajs.joglcanvas.doFrustum",doFrustum);
				canvas.repaint();
				if(jic!=null) {jic.setStereoUpdated(); jic.repaint();}
			}
		});
		
		c.gridy++; c.gridx=0; c.gridwidth=4; panel.add(cb,c);
		c.gridy++; c.gridx=0; c.gridwidth=4; panel.add(new JLabel(" "),c);
		cb=new JCheckBox("Auto fullscreen on OpenGL Quad buffer stereo",qbfullscreen);
		cb.addItemListener(new ItemListener() {
			@Override
			public void itemStateChanged(ItemEvent e)  {
				qbfullscreen=e.getStateChange()==ItemEvent.SELECTED;
				Prefs.set("ajs.joglcanvas.qbfullscreen",doFrustum);
			}
		});
		c.gridy++; c.gridx=0; c.gridwidth=4; panel.add(cb,c);

		c.gridy++; c.gridx=0; c.gridwidth=4; c.anchor=GridBagConstraints.CENTER;
		panel.add(canvas, c);
		
		JPanel bpanel=new JPanel();
		bpanel.setLayout(new GridLayout(1,2,10,2));
		JButton button=new JButton("OK");
		button.setSize(200,50);
		button.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				leftAnaglyphColor=canvas.left;
				rightAnaglyphColor=canvas.right;
				stereoSep=(float)canvas.sep/100f;
				Prefs.set("ajs.joglcanvas.leftAnaglyphColor",leftAnaglyphColor.getRGB());
				Prefs.set("ajs.joglcanvas.rightAnaglyphColor",rightAnaglyphColor.getRGB());
				Prefs.set("ajs.joglcanvas.dubois", dubois);
				Prefs.set("ajs.joglcanvas.stereoSep", stereoSep);
				fillAnaColors();
				asettings.dispose();
				if(jic!=null) {jic.setStereoUpdated(); jic.repaint();}
			}
		});
		bpanel.add(button);
		button=new JButton("Cancel");
		button.setSize(200,100);
		button.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				asettings.dispose();
			}
		});
		bpanel.add(button);
		
		asettings.setLayout(new BorderLayout());
		asettings.add(panel, BorderLayout.NORTH);
		asettings.add(bpanel,BorderLayout.SOUTH);
		asettings.pack();
		asettings.setVisible(true);
		canvas.repaint();
	}
	
	public static void fillAnaColors(){
		if(dubois) {
			//Source of below: bino, a 3d video player:  https://github.com/eile/bino/blob/master/src/video_output_render.fs.glsl
			// Source of this matrix: http://www.site.uottawa.ca/~edubois/anaglyph/LeastSquaresHowToPhotoshop.pdf
			/*anaColors = new float[][] {
				 {0.437f, -0.062f, -0.048f,
				 0.449f, -0.062f, -0.050f,
				 0.164f, -0.024f, -0.017f},
				 
				{-0.011f,  0.377f, -0.026f,
				-0.032f,  0.761f, -0.093f,
				-0.007f,  0.009f,  1.234f}};*/
			anaColors = duboisColors;
		}else {
			float lr=(float)JCP.leftAnaglyphColor.getRed()/255f, lg=(float)JCP.leftAnaglyphColor.getGreen()/255f, lb=(float)JCP.leftAnaglyphColor.getBlue()/255f,
				rr=(float)JCP.rightAnaglyphColor.getRed()/255f, gr=(float)JCP.rightAnaglyphColor.getGreen()/255f, br=(float)JCP.rightAnaglyphColor.getBlue()/255f;
			anaColors=new float[][] {{ lr,lr,lr,lg,lg,lg,lb,lb,lb},
								{rr,rr,rr,gr,gr,gr,br,br,br}};
		}
	}
	
	public static void setDebug(boolean b) {debug=b;}
	public static void setStereoSep(float sep) {stereoSep=sep; Prefs.set("ajs.joglcanvas.stereoSep",stereoSep);}

}