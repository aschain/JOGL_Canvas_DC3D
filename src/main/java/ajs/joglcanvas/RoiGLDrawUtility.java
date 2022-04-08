package ajs.joglcanvas;

import static com.jogamp.opengl.GL3.*;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

import com.jogamp.opengl.GL2GL3;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.util.GLBuffers;
import com.jogamp.opengl.util.texture.awt.AWTTextureIO;
//import com.jogamp.opengl.GL2ES2;
//import com.jogamp.opengl.fixedfunc.GLMatrixFunc;
//import com.jogamp.opengl.util.PMVMatrix;
//import com.jogamp.graph.curve.Region;
//import com.jogamp.graph.curve.opengl.RegionRenderer;
//import com.jogamp.graph.curve.opengl.RenderState;
//import com.jogamp.graph.curve.opengl.TextRegionUtil;
//import com.jogamp.graph.font.FontFactory;
//import com.jogamp.graph.geom.SVertex;

import ajs.joglcanvas.JCGLObjects.JCProgram;
import ajs.joglcanvas.JOGLImageCanvas.CutPlanesCube;
import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.gui.Arrow;
import ij.gui.Line;
import ij.gui.OvalRoi;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.gui.ShapeRoi;
import ij.gui.TextRoi;
import ij.measure.Calibration;
import ij.process.FloatPolygon;

public class RoiGLDrawUtility {
	//private RegionRenderer regionRenderer=null;
	private ImagePlus imp;
	private GL2GL3 gl;
	private GLAutoDrawable drawable;
	private JCGLObjects rglos=null;
	float px=2f/1024f;
	//float yrat=1f;
	float w=-1f,h,offx,offy,dw, dh;
	double mag;
	boolean go3d;
	Color anacolor=null;
	float dpimag=1f;

	public RoiGLDrawUtility(ImagePlus imp, GLAutoDrawable drawable, JCProgram program, double dpimag) {
		this.imp=imp;
		rglos= new JCGLObjects(drawable);
		rglos.newBuffer(GL_ARRAY_BUFFER, "roiGL");
		rglos.newBuffer(GL_ELEMENT_ARRAY_BUFFER, "roiGL");
		rglos.newVao("roiGL", 3, GL_FLOAT, 4, GL_FLOAT);
		rglos.newProgram("color", "shaders", "color", "color");
		
		rglos.newTexture("text", false);
		rglos.newBuffer(GL_ARRAY_BUFFER, "text");
		rglos.newBuffer(GL_ELEMENT_ARRAY_BUFFER, "text");
		rglos.newVao("text", 3, GL_FLOAT, 3, GL_FLOAT);
		rglos.programs.put("text",program);
		updateSrcRect();
		setGL(drawable);
		this.dpimag=(float)dpimag;
	}
	
	private void updateSrcRect() {
		Rectangle srcRect=imp.getCanvas().getSrcRect();
		mag=imp.getCanvas().getMagnification();
		w=(float)srcRect.width; h=(float)srcRect.height;
		offx=(float)srcRect.x; offy=(float)srcRect.y;
		dw=(int)(mag*w+0.5);
		dh=(int)(mag*h+0.5);
		px=2f/(int)(mag*h+0.5)*dpimag;
		//yrat=1f;//(float)srcRect.height/srcRect.width;
	}
	
	private void setGL(GLAutoDrawable drawable) {
		this.drawable=drawable;
		rglos.setGL(drawable.getGL());
		this.gl=rglos.getGL2GL3();
	}
	
	public void setImp(ImagePlus imp) {
		this.imp=imp;
	}
	
	public void startDrawing() {
		rglos.useProgram("color");
	}

	/**
	 * 
	 * @param drawable
	 * @param roi
	 * @param isRoi
	 * @param anacolor should be null if not anaglyph
	 * @param go3d
	 */
	public void drawRoiGL(GLAutoDrawable drawable, Roi roi, boolean isRoi, Color anacolor, boolean go3d) {
		if(roi==null)return;
		if(roi instanceof ShapeRoi) {
			Roi[] rois=((ShapeRoi)roi).getRois();
			for(Roi r : rois) {
				if(!(r instanceof ShapeRoi))drawRoiGL(drawable, r, false, anacolor, go3d);
				else drawRoiGL(drawable, ((ShapeRoi)r).shapeToRoi(), false, anacolor, go3d);
			}
			return;
		}

		gl.glEnable(GL_MULTISAMPLE);
		this.go3d=go3d;
		this.anacolor=anacolor;
		int sls=imp.getNSlices(), ch=imp.getC(), sl=imp.getZ(), fr=imp.getT();
		int rch=roi.getCPosition(), rsl=roi.getZPosition(), rfr=roi.getTPosition();
		if(!(rfr==0 || rfr==fr))return;
		if(!go3d && !((rch==0 || ch==rch) && (rsl==0 || rsl==sl)))return;
		int tp=roi.getType();
		if(go3d) {
			CutPlanesCube fc=JCP.getJOGLImageCanvas(imp).getCutPlanesCube();
			if(rsl!=0 && fc.applyToRoi && (rsl<fc.z() ||rsl>=fc.d()))return;
			Rectangle b=roi.getBounds();
			if(tp!=Roi.POINT && fc.applyToRoi) {
				if((b.x+b.width)<fc.x() || (b.x>fc.w()))return;
				if((b.y+b.height)<fc.y() || (b.y>fc.h()))return;
			}
		}
		setGL(drawable);
		updateSrcRect();
		gl.glLineWidth((float)dpimag);
		boolean drawHandles=isRoi;
		//if(isRoi && roi.getState()==Roi.CONSTRUCTING)drawHandles=false;

		float z=0f;
		Calibration cal=imp.getCalibration();
		float zf=(float)(cal.pixelDepth/cal.pixelWidth)/w;
		if(go3d) {
			z=((float)sls-2f*(rsl==0?(sl-1):(rsl-1)))*zf;
		}
		
		if(roi instanceof TextRoi){
			drawTextRoiString((TextRoi)roi, z, !isRoi);
			if(!drawHandles)return;
		}

		FloatPolygon fp=roi.getFloatPolygon();
		
		if(tp==Roi.POINT) {
			PointRoi proi=(PointRoi)roi;
			int n=fp.npoints;
			for(int i=0;i<n;i++) {
				int pos=proi.getPointPosition(i);
				if(pos==0)pos=imp.getCurrentSlice();
				int[] hpos=imp.convertIndexToPosition(pos);
				int rc=hpos[0], rz=hpos[1], rf=hpos[2];
				if(!go3d) {
					if(imp.getCurrentSlice()==pos || pos==0 || Prefs.showAllPoints)
						drawPoint(proi, fp.xpoints[i], fp.ypoints[i], 0f,i);
				}else {
					if(rf==imp.getT() && (rc==imp.getC()||Prefs.showAllPoints)) {
						CutPlanesCube fc=JCP.getJOGLImageCanvas(imp).getCutPlanesCube();
						if( !fc.applyToRoi || ( rz>fc.z() && rz<=fc.d() 
								&& fp.xpoints[i]>fc.x() && fp.xpoints[i]<=fc.w()
								&& fp.ypoints[i]>fc.y() && fp.ypoints[i]<=fc.h())
								) {
							float pz=((float)sls-2f*(float)hpos[1])*zf;
							drawPoint(proi, fp.xpoints[i], fp.ypoints[i], pz, i);
						}
					}
				}
			}
			return;
		}
		
		if(roi instanceof OvalRoi) {
			fp=getOvalFloatPolygon((OvalRoi)roi);
		}


		float strokeWidth=roi.getStrokeWidth();
		if(tp==Roi.LINE && strokeWidth>1 && fp.npoints==4) {
			fp=new FloatPolygon(
					new float[] {(fp.xpoints[0]+fp.xpoints[1])/2,(fp.xpoints[2]+fp.xpoints[3])/2},
					new float[] {(fp.ypoints[0]+fp.ypoints[1])/2,(fp.ypoints[2]+fp.ypoints[3])/2},
					2);
		}
		FloatPolygon loopfp=fp;
		
		int todraw=GL_LINE_STRIP;
		if(tp==Roi.RECTANGLE || tp==Roi.TRACED_ROI || tp==Roi.OVAL || (!(roi.getState()==Roi.CONSTRUCTING) && (tp==Roi.POLYGON || tp==Roi.FREEROI))
			|| (tp==Roi.FREEROI && (roi instanceof ij.gui.EllipseRoi || roi.getClass().getName()=="ij.gui.RotatedRectRoi"))) {
			//todraw=GL_LINE_LOOP;
			int n=fp.npoints+1;
			float[] xpoints=new float[n];
			float[] ypoints=new float[n];
			for(int i=0; i<(n-1); i++) {
				xpoints[i]=fp.xpoints[i];
				ypoints[i]=fp.ypoints[i];
			}
			xpoints[n-1]=fp.xpoints[0];
			ypoints[n-1]=fp.ypoints[0];
			loopfp=new FloatPolygon(xpoints, ypoints, n);
		}

		Color roicolor=anacolor==null?roi.getStrokeColor():anacolor;
		if(roicolor==null)roicolor=Roi.getColor();
		if(roi.getFillColor()!=null) {
			todraw=GL_TRIANGLE_STRIP;
			roicolor=roi.getFillColor();
			roi.getStroke();
		}
		
		Color altcolor=new Color(roicolor.getRed(),roicolor.getGreen(), roicolor.getBlue(), roicolor.getAlpha());
		if(roicolor.getAlpha()==77)altcolor=new Color(altcolor.getRed(),altcolor.getGreen(), altcolor.getBlue(),255);
		if(roi instanceof TextRoi)altcolor=Roi.getColor();
		
		drawGLFP(todraw, loopfp, z, altcolor);
		

		//if it is a line with width
		if(strokeWidth>1 && !(roi instanceof Arrow) && fp.npoints>=2) {
			//Color c=new Color(roicolor.getRed(), roicolor.getGreen(), roicolor.getBlue(), 77);
			drawPolyWideLine(loopfp, roicolor, strokeWidth, z);
			
		}
		
		if(roi instanceof Arrow) { //arrow
			drawArrow((Arrow)roi, z);
		}
		
		if(drawHandles) {
			int n=fp.npoints;
			float[] xpoints=fp.xpoints;
			float[] ypoints=fp.ypoints;
			//IJ.log("\\Update:tp:"+tp+" n:"+n+" cn: "+roi.getClass().getSimpleName());
			float[] xhandles=new float[0],yhandles=new float[0];
			if(roi instanceof ij.gui.EllipseRoi || tp==Roi.OVAL) {
				int hn=4; //n==72 ellipse
				if(tp==Roi.OVAL)hn=8;
				xhandles=new float[hn]; yhandles=new float[hn];
				for(int i=0;i<hn;i++) {
					xhandles[i]=xpoints[i*n/hn];
					yhandles[i]=ypoints[i*n/hn];
				}
			}else if(tp==Roi.RECTANGLE) {
				xhandles=new float[8];
				yhandles=new float[8];
				float[] xps=xpoints, yps=ypoints;
				if(roi.getCornerDiameter()>0){ //roundrect indices
					Roi rect=(Roi)roi.clone();
					rect.setCornerDiameter(0);
					FloatPolygon rfp=rect.getFloatPolygon();
					xps=rfp.xpoints; yps=rfp.ypoints;
				}
				for(int i=0;i<4;i++) {
					xhandles[i*2]=xps[i]; yhandles[i*2]=yps[i];
					int j=((i==3)?0:(i+1));
					xhandles[i*2+1]=(xps[i]+xps[j])/2; yhandles[i*2+1]=(yps[i]+yps[j])/2;
				}
			}else if(tp==Roi.POLYGON || tp==Roi.POLYLINE || tp==Roi.ANGLE) {
				xhandles=xpoints; yhandles=ypoints;
			}else if(tp==Roi.LINE){
				if(n==2) {
					xhandles=new float[] {xpoints[0],(xpoints[0]+xpoints[1])/2,xpoints[1]};
					yhandles=new float[] {ypoints[0],(ypoints[0]+ypoints[1])/2,ypoints[1]};
				}
			}else if(tp==Roi.FREEROI) {
				if(n>=4 && !(roi instanceof ij.gui.FreehandRoi) && !(roi.getClass().getSimpleName().equals("PolygonRoi"))) {
					xhandles=new float[4];
					yhandles=new float[4];
					for(int i=0;i<4;i++) {
						int j=(i==(n-1))?(0):(i+1);
						xhandles[i]=(xpoints[i]+xpoints[j])/2; yhandles[i]=(ypoints[i]+ypoints[j])/2;
					}
				}
			}
			for(int i=0;i<xhandles.length;i++) {
				int hs=getHandleWidth(roi);
				if(i==0 && ((tp==Roi.LINE && !roi.getClass().getSimpleName().equals("Arrow")) || tp==Roi.POLYLINE || tp==Roi.POLYGON)) {
					if(roi.getState()==Roi.CONSTRUCTING && (tp==Roi.POLYLINE || tp==Roi.POLYGON))drawHandle((int)xhandles[i], (int)yhandles[i], z, hs+4, roicolor, true);
					drawHandle((int)xhandles[i], (int)yhandles[i], z, hs, roicolor, true);
				}else if(i==3 && (roi.getClass().getSimpleName().equals("RotatedRectRoi"))) {
					drawHandle((int)xhandles[i], (int)yhandles[i], z, hs, roicolor, true);
				}else drawHandle((int)xhandles[i], (int)yhandles[i], z, hs, anacolor==null?Color.WHITE:anacolor, true);
			}
		}
	}
	
	private int getHandleWidth(Roi roi) {
		int threshold1 = 7500;
		int threshold2 = 1500;
		Rectangle b=roi.getBounds();
		double size = (b.width*b.height)*mag*mag;
		if (roi instanceof Line) {
			size = ((Line)roi).getLength()*mag;
			threshold1 = 150;
			threshold2 = 50;
		} else {
			if (roi.getState()==Roi.CONSTRUCTING && !(roi.getType()==Roi.RECTANGLE||roi.getType()==Roi.OVAL))
				size = threshold1 + 1;	
		}
		int width = 7;
		if (size>threshold1) {
		} else if (size>threshold2) {
			width = 5;
		} else {
			width = 3;
		}
		int inc = roi.getHandleSize() - 7;
		width += inc;
		if(width<3)width=1;
		return width;
	}
	
	public float[] ijToGLCoords(float[] ijcoords, boolean screen) {
		float[] glcoords=new float[ijcoords.length];
		Calibration cal=imp.getCalibration();
		float zf=(float)(cal.pixelDepth/cal.pixelWidth)/w;
		int sls=imp.getNSlices();
		for(int i=0; i<ijcoords.length; i++) {
			if(i%3==0)glcoords[i]=screen?(sglx((int)ijcoords[i])):(glX(ijcoords[i]));
			else if(i%3==1)glcoords[i]=screen?(sgly((int)ijcoords[i])):(glY(ijcoords[i]));
			else if(i%3==2)glcoords[i]=((float)sls-2f*(ijcoords[i]-1))*zf;
		}
		return glcoords;
	}
	
	private float[] getSubGLCoords(FloatPolygon fp, int start, int end, float z, boolean screen) {

		int length=end-start;
		float[] coords=new float[length*3];
		for(int i=start;i<end;i++) {
			float x,y;
			if(screen) {
				x=sglx((int)fp.xpoints[i]);
				y=sgly((int)fp.ypoints[i]);
			}else {
				x=glX(fp.xpoints[i]);
				y=glY(fp.ypoints[i]);
			}
			coords[(i-start)*3]=x; coords[(i-start)*3+1]=y; coords[(i-start)*3+2]=z;
		}
		return coords;
	}
	
	private float[] getGLCoords(FloatPolygon fp, float z, boolean screen) {

		return getSubGLCoords(fp,0,fp.npoints,z,screen);
	}
	
	public void drawGLij(float[] coords, Color color, int toDraw) {
		drawGL(ijToGLCoords(coords, false), color, toDraw);
	}
	
	public void drawGL(float[] coords, Color color, int toDraw) {
		drawGL(coords, new float[] {(float)color.getRed()/255f,(float)color.getGreen()/255f,(float)color.getBlue()/255f,(float)color.getAlpha()/255f},toDraw);
	}
	
	public void drawGL(float[] coords, float[] color, int toDraw) {
		if(coords.length<6)return;
		FloatBuffer fb=GLBuffers.newDirectFloatBuffer(coords.length/3*7);
		for(int i=0;i<coords.length;i+=3) {
			fb.put(coords[i]).put(coords[i+1]).put(coords[i+2]).put(color);
		}
		drawGLfb(drawable, fb, toDraw);
	}
	
	/**
	 * drawGLfb requires a FloatBuffer fb which has 3 position floats followed by 4 color floats per vertex.
	 * @param gl
	 * @param fb
	 * @param toDraw
	 */
	public void drawGLfb(GLAutoDrawable drawable, FloatBuffer fb, int toDraw) {
		setGL(drawable);
		rglos.drawVao(toDraw, "roiGL", fb, "color");
	}
	
	/** draws an Roi handle. x,y, are IMAGEJ subpixel positions
	 *                       z is opengl float position
	 *  Handle is in  IMAGEJ int!!
	 */
	private void drawHandle(float x, float y, float z, int hsi, Color color, boolean border) {
		int hs=(int)((float)hsi/(2*(((1f/px)<128f)?2f:1f)));
		
		gl.glLineWidth(1f);
		float[] coords={
				sglx(sx(x)-hs), sgly(sy(y)-hs), z,
				sglx(sx(x)+hs), sgly(sy(y)-hs), z,
				sglx(sx(x)-hs), sgly(sy(y)+hs), z,
				sglx(sx(x)+hs), sgly(sy(y)+hs), z
		};
		drawGL(coords,color,GL_TRIANGLE_STRIP);

		if(border) {
			float t=coords[6]; coords[6]=coords[9]; coords[9]=t;
			drawGL(coords,new float[] {0f, 0f, 0f,1f},GL_LINE_LOOP);
		}
	}
	
	/** draws points. x,y,z are all opengl float positions*/
	//private void drawPoints(PointRoi roi, float[] z, Color anacolor) {
	//	for(int n=0;n<z.length;n++) {
	//		if(z[n]>-2f)drawPoint(roi, z[n], n, anacolor);
	//	}
	//}
	
	/** draws a point. x,y,z are all opengl float positions*/
	private void drawPoint(PointRoi roi, float x, float y, float z, int n) {
		boolean ms=gl.glIsEnabled(GL_MULTISAMPLE);
		gl.glEnable(GL_MULTISAMPLE);
		n++;
		final int TINY=1, SMALL=3, MEDIUM=5, LARGE=7, EXTRA_LARGE=11;
		final int HYBRID=0, CROSSHAIR=1, DOT=2, CIRCLE=3;
		int sizei=3;
		switch(roi.getSize()) {
			case 0: sizei=TINY; break;
			case 1: sizei=SMALL; break;
			case 2: sizei=MEDIUM; break;
			case 3: sizei=LARGE; break;
			case 4: sizei=EXTRA_LARGE; break;
		}
		Color strokeColor=roi.getStrokeColor();
		Color color = strokeColor!=null?strokeColor:Roi.getColor();
		if (roi.isActiveOverlayRoi()) {
			if (color==Color.cyan)
				color = Color.magenta;
			else
				color = Color.cyan;
		}
		//int nCounters=roi.getNCounters();
		int[] counters=roi.getCounters();
		int nCounters=0;
		for (int counter=0; counter<nCounters; counter++) {
			if (roi.getCount(counter)>0) nCounters++;
		}
		if(nCounters==0)nCounters++;
		int type=roi.getPointType();
		if (nCounters>1 && counters!=null && n<=counters.length)
			color = getPointColor(counters[n-1]);
		if(anacolor!=null)color=anacolor;
		if (type==HYBRID || type==CROSSHAIR) {
			gl.glLineWidth(1f);
			if (sizei>LARGE)
				gl.glLineWidth(3f);
			drawLine(sglx(sx(x)-(sizei+2)), glY(y), sglx(sx(x)+(sizei+2)), glY(y), z, type==HYBRID?Color.WHITE:color);
			drawLine(glX(x), sgly(sy(y)-(sizei+2)), glX(x), sgly(sy(y)+(sizei+2)), z, type==HYBRID?Color.WHITE:color);
		}
		gl.glLineWidth(1f);
		if (type==HYBRID || type==DOT) { 
			if (sizei>LARGE)
				gl.glLineWidth(1f);
			if (sizei>LARGE && type==DOT)
				fillOval(sx(x)-sizei/2, sy(y)-sizei/2, sizei, sizei, z, color);
			else if (sizei>LARGE && type==HYBRID)
				drawHandle(x,y,z,sizei-4,color, false);
			else if (sizei>SMALL && type==HYBRID)
				drawHandle(x,y,z,sizei-1,color, false);
			else
				drawHandle(x,y,z,sizei,color, false);
		}
		int nPoints=roi.getNCoordinates();
		//int fontSize=9;
		if (roi.getShowLabels() && nPoints>1) {
			int offset = 2;
			if (nCounters==1) {
				drawString(""+n, color, sglx(sx(x)+offset), sgly(sy(y)+(offset)), z); //y offset +fontSize;
			} else if (counters!=null) {
				drawString(""+counters[n-1], getPointColor(counters[n-1]), sglx(sx(x)+offset), sgly(sy(y)+(offset)), z);//y offset +fontSize
			}
		}
		if ((sizei>TINY||type==DOT) && (type==HYBRID||type==DOT)) {
			if (sizei>LARGE && type==HYBRID)
				drawOval(sx(x)-(sizei/2-1), sy(y)-(sizei/2-1), (sizei-2), (sizei-2), z, Color.black);
			else if (sizei>SMALL && type==HYBRID)
				drawOval(sx(x)-sizei/2, sy(y)-(sizei/2+1), (sizei), (sizei), z, Color.black);
			else
				drawOval(sx(x)-(sizei/2+1), sy(y)-(sizei/2+1), (sizei+2), (sizei+2), z, Color.black);
		}
		if (type==CIRCLE) {
			int scaledSize = (sizei+1);
			if (sizei>LARGE)
				gl.glLineWidth(2f);
			drawOval(sx(x)-scaledSize, sy(y)-scaledSize, scaledSize, scaledSize, z, color);
		}
		if(!ms)gl.glDisable(GL_MULTISAMPLE);
	}
	
	private Color getPointColor(int index) {
		Color[] colors = new Color[10];
		colors[0]=Color.yellow; colors[1]=Color.magenta; colors[2]=Color.cyan;
		colors[3]=Color.orange; colors[4]=Color.green; colors[5]=Color.blue;
		colors[6]=Color.white; colors[7]=Color.darkGray; colors[8]=Color.pink;
		colors[9]=Color.lightGray;
		
		return colors[index%10];
	}
	
	private void drawPolyWideLine(FloatPolygon fp, Color color, float width, float z) {
		float[] wlcoords=getGLCoords(getWideLineTriStrip(width,fp), z, false);
		boolean bl=gl.glIsEnabled(GL_BLEND);
		gl.glEnable(GL_BLEND);
		gl.glEnable(GL_STENCIL_TEST);
		gl.glStencilFunc(GL_EQUAL, 0, 0xFF);
		gl.glStencilOp(GL_KEEP, GL_KEEP, GL_INCR);
		gl.glStencilMask(0xFF); // Write to stencil buffer
		gl.glClear(GL_STENCIL_BUFFER_BIT); // Clear stencil buffer (0 by default)
		drawGL(wlcoords, color, GL_TRIANGLE_STRIP);
		gl.glStencilMask(0x00);
		gl.glDisable(GL_STENCIL_TEST);
		if(!bl)gl.glDisable(GL_BLEND);
	}
	
	private void drawArrow(Arrow aroi, float z) {
		boolean ms=gl.glIsEnabled(GL_MULTISAMPLE);
		gl.glEnable(GL_MULTISAMPLE);
		drawArrow(aroi, z,false);
		if(aroi.getDoubleHeaded())drawArrow(aroi, z,true);
		if(!ms)gl.glDisable(GL_MULTISAMPLE);
	}
	
	/**
	 * drawArrow adapted from Arrow calculatePoints()
	 * @param aroi
	 * @param z
	 * @param flip
	 */
	private void drawArrow(Arrow aroi, float z, boolean flip) {
		Color color=anacolor==null?aroi.getStrokeColor():anacolor;
		double tip = 0.0;
		double base;
		double shaftWidth = aroi.getStrokeWidth();
		double length = 8+10*shaftWidth*0.5;
		length = length*(aroi.getHeadSize()/10.0);
		length -= shaftWidth*1.42;
		int style=aroi.getStyle();
		if (style==Arrow.NOTCHED) length*=0.74;
		if (style==Arrow.OPEN) length*=1.32;
		if (length<0.0 || style==Arrow.HEADLESS) length=0.0;
		double x1d=aroi.x1d, y1d=aroi.y1d,x2d=aroi.x2d,y2d=aroi.y2d;
		if(flip) {x1d=x2d;y1d=y2d;x2d=aroi.x1d;y2d=aroi.y1d;}
		double dx=x2d-x1d, dy=y2d-y1d;
		double arrowLength = Math.sqrt(dx*dx+dy*dy);
		dx=dx/arrowLength; dy=dy/arrowLength;
		float[] points = new float[2*5];
		if (aroi.getDoubleHeaded() && style!=Arrow.HEADLESS) {
			points[0] = (float)(x1d+dx*shaftWidth*2.0);
			points[1] = (float)(y1d+dy*shaftWidth*2.0);
		} else {
			points[0] = (float)x1d;
			points[1] = (float)y1d;
		}
        if (length>0) {
			//double factor = style==Arrow.OPEN?1.3:1.42;
			if (style==Arrow.BAR) {
				points[3*2] = (float)(x2d-dx*shaftWidth*0.5);
				points[3*2+1] = (float)(y2d-dy*shaftWidth*0.5);
			}else{
				length+=shaftWidth*2;
				points[3*2] = (float)x2d;//(float)(x2d-dx*shaftWidth*factor);
				points[3*2+1] = (float)y2d;//(float)(y2d-dy*shaftWidth*factor);
			}
		} else {
			points[3*2] = (float)x2d;
			points[3*2+1] = (float)y2d;
		}
		final double alpha = Math.atan2(points[3*2+1]-points[1], points[3*2]-points[0]);
		double SL = 0.0;
		switch (style) {
			case Arrow.FILLED: case Arrow.HEADLESS:
				tip = Math.toRadians(20.0);
				base = Math.toRadians(90.0);
				points[1*2]   = (float) (points[3*2]	- length*Math.cos(alpha));
				points[1*2+1] = (float) (points[3*2+1] - length*Math.sin(alpha));
				SL = length*Math.sin(base)/Math.sin(base+tip);;
				break;
			case Arrow.NOTCHED:
				tip = Math.toRadians(20);
				base = Math.toRadians(120);
				points[1*2]   = (float) (points[3*2] - length*Math.cos(alpha));
				points[1*2+1] = (float) (points[3*2+1] - length*Math.sin(alpha));
				SL = length*Math.sin(base)/Math.sin(base+tip);;
				break;
			case Arrow.OPEN:
				tip = Math.toRadians(25); //30
				points[1*2] = points[3*2];
				points[1*2+1] = points[3*2+1];
				SL = length;
				break;
			case Arrow.BAR:
				tip = Math.toRadians(90); //30
				points[1*2] = points[3*2];
				points[1*2+1] = points[3*2+1];
				SL = length;
				break;       
		}
		// P2 = P3 - SL*alpha+tip
		points[2*2] = (float) (points[3*2]	- SL*Math.cos(alpha+tip));
		points[2*2+1] = (float) (points[3*2+1] - SL*Math.sin(alpha+tip));
		// P4 = P3 - SL*alpha-tip
		points[4*2]   = (float) (points[3*2]	- SL*Math.cos(alpha-tip));
		points[4*2+1] = (float) (points[3*2+1] - SL*Math.sin(alpha-tip));
		
		FloatPolygon shaftfp=new FloatPolygon(
				new float[] {points[0],points[1*2]},
				new float[] {points[0+1],points[1*2+1]},
				2);
		if(style==Arrow.OPEN||style==Arrow.BAR) {
			 shaftfp=new FloatPolygon(
					new float[] {points[0],points[3*2],points[2*2],points[3*2],points[4*2]},
					new float[] {points[0+1],points[3*2+1],points[2*2+1],points[3*2+1],points[4*2+1]},
					5);
		}
		drawPolyWideLine(shaftfp, color, (float)shaftWidth, z);
		if(style==Arrow.FILLED||style==Arrow.NOTCHED) {
			float[] acoords=getGLCoords(new FloatPolygon(new float[] {points[2*1],points[2*2],points[2*3],points[2*1],points[2*4],points[2*3]},new float[] {points[2*1+1],points[2*2+1],points[2*3+1],points[2*1+1],points[2*4+1],points[2*3+1]}, 6),z,false);
			drawGL(acoords, color, GL_TRIANGLES);
		}
		//for(int i=0;i<points.length/2;i++)
		//	IJ.log("x"+points[i*2]+" y"+points[i*2+1]);
	}
	
	private float glX(float x) {
		double xf=x, offx=this.offx, w=this.w, dw=this.dw;
		return (float)((xf-offx)/w*2.0+1.0/dw-1.0);
	}
	
	private float glY(float y) {
		double h=this.h, yf=y, offy=this.offy, dh=this.dh;
		return (float)(((h-(yf-offy))/h)*2.0-1.0/dh-1.0);
	}
	
	private int sx(float x) {
		return (int)((x-offx)*mag+0.5);
	}
	
	private int sy(float y) {
		return (int)((y-offy)*mag+0.5);
	}
	
	/**
	 * screen coordinate (not image) to gl
	 */
	private float sglx(int x) {
		return ((float)x+0.5f)/dw*2f-1f;
	}
	
	/**
	 * screen coordinate (not image) to gl
	 */
	private float sgly(int y) {
		return ((dh-(float)y-0.5f)/dh*2f-1f);
	}
	
	//private int impX(float x) {
	//	return (int)((x+1f)*w/2f+offx);
	//}
	
	//private int impY(float y) {
	//	return (int)(offy+h-((y/yrat+1f)*h/2f));
	//}
	
	/** x,y,z are in opengl float*/
	private void drawLine(float x1, float y1, float x2, float y2, float z, Color color) {
		drawGL(new float[] {x1,y1,z,x2,y2,z},color,GL_LINE_STRIP);
	}
	
	/** x,y are in image SCREEN positions, z is in opengl float*/
	private void fillOval(int x, int y, int width, int height, float z, Color color) {
		drawOval(x, y, width, height, z, true, color);
	}
	
	/** x,y are in image SCREEN positions, z is in opengl float*/
	private void drawOval(int x, int y, int width, int height, float z, Color color) {
		drawOval(x, y, width, height, z, false, color);
	}
	
	/** x,y are in SCREEN pixel positions, z is in opengl float*/
	private void drawOval(int x, int y, int width, int height, float z, boolean fill, Color color) {
		if(imp==null)return;
		int todraw=GL_LINE_LOOP;
		if(fill)todraw=GL_TRIANGLE_STRIP;
		drawGL(getGLCoords(getOvalFloatPolygon(new Rectangle(x,y,width,height),72),z,true), color, todraw);
	}
	
	/** FloatPolygon has coordinates in IMAGEJ (NOT opengl -1 to 1) except z*/
	private void drawGLFP(int GLtypetodraw, FloatPolygon fp, float z, Color color) {
		drawGL(getGLCoords(fp,z,false), color,GLtypetodraw);
	}
	
	private static FloatPolygon getOvalFloatPolygon(OvalRoi roi) {
		return getOvalFloatPolygon(roi.getBounds(), 72);
	}
	
	/** minimizes points but has unequal distribution over the oval*/
	/*
	private static FloatPolygon getMinOvalFloatPolygon(final Rectangle b) {
		final double x=(double)b.x+(double)b.width/2.0;
		final double y=(double)b.y+(double)b.height/2.0;
		final double xrad=(double)b.width/2f;
		final double yrad=(double)b.height/2f;
		int[] xmax=new int[360];
		int[] ymax=new int[360];
		int n=0;
		boolean linex=false,liney=false;
		int xprev=0, yprev=0;
		for(int i=0;i<360;i++) {
			double rad=(double)i*Math.PI/180;
			int xnew=(int)(x+xrad*Math.cos(rad));
			int ynew=(int)(y+yrad*Math.sin(rad));
			boolean add=true;
			boolean addline=false;
			if(n>0){
				if(xnew==xmax[n-1] && ynew==ymax[n-1]) {
					if(linex || liney) {linex=false;liney=false;addline=true;}
					else add=false;
				}else{
					if(xnew==xmax[n-1] && xnew==xprev) {
						if(liney) {liney=false; addline=true;}
						else{linex=true;xprev=xnew;yprev=ynew; add=false;}
						
					}else if(ynew==ymax[n-1] && ynew==yprev) {
						if(linex) {linex=false; addline=true;}
						else{liney=true;xprev=xnew;yprev=ynew; add=false;}
					}
				}
			}
			xprev=xnew; yprev=ynew;
			if(addline){xmax[n]=xprev;ymax[n]=yprev;n++;}
			if(add){xmax[n]=xnew;ymax[n]=ynew;n++;}
		}
		float[] xpoints=new float[n];
		float[] ypoints=new float[n];
		for(int i=0;i<n;i++) {xpoints[i]=xmax[i];ypoints[i]=ymax[i];}
		return new FloatPolygon(xpoints,ypoints,n);
	}
	*/
	
	/** 
	 * Rectangle in IMAGEJ coordinates, and returns FloatPolygon with IMAGEJ
	 * coordinates of oval with the bounding rectangle
	 * @param b The bounding rectangle of the oval
	 * @param num Number of points to make up the oval, a divisor of 
	 *			  360 and between 20 and 360
	 * @return FloatPolygon with ImageJ coordinates of an oval
	 */
	private static FloatPolygon getOvalFloatPolygon(final Rectangle b, int num) {
		final int[] divs=new int[] {4,8,12,20,24,30,36,40,45,60,72,90,120,180,360};
		int dmin=360; int n=num;
		for(int i=0;i<divs.length;i++) {
			int diff=Math.abs(num-divs[i]);
			if(diff<dmin) {dmin=diff; n=divs[i];}
			if(divs[i]>(double)b.width*Math.PI)break;
		}
		final double xrad=(double)b.width/2.0;
		final double yrad=(double)b.height/2.0;
		final double x=(double)b.x+xrad;
		final double y=(double)b.y+yrad;
		float[] xpoints=new float[n];
		float[] ypoints=new float[n];
		for(double i=0;i<360;i+=360/n) {
			double rad=i*Math.PI/180;
			xpoints[(int) (i/(360/n))]=(float) (x+(xrad*Math.cos(rad)));
			ypoints[(int) (i/(360/n))]=(float) (y+(yrad*Math.sin(rad)));
		}
		return new FloatPolygon(xpoints,ypoints,n);
	}
	
	
	private static FloatPolygon getWideLine(float width, float x1, float y1, float x2, float y2) {
		// Ripped from getFloatPolygon for ij.gui.Line
		double x1d=(double)x1, y1d=(double)y1,x2d=(double)x2,y2d=(double)y2;
		double angle = Math.atan2(y1d-y2d, x2d-x1d);
		double width2 = width/2.0;
		double p1x = x1d + Math.cos(angle+Math.PI/2d)*width2;
		double p1y = y1d - Math.sin(angle+Math.PI/2d)*width2;
		double p2x = x1d + Math.cos(angle-Math.PI/2d)*width2;
		double p2y = y1d - Math.sin(angle-Math.PI/2d)*width2;
		
		double p3x = x2d + Math.cos(angle+Math.PI/2d)*width2;
		double p3y = y2d - Math.sin(angle+Math.PI/2d)*width2;
		double p4x = x2d + Math.cos(angle-Math.PI/2d)*width2;
		double p4y = y2d - Math.sin(angle-Math.PI/2d)*width2;
		return new FloatPolygon(new float[] {(float) p1x,(float) p2x,(float) p3x,(float) p4x},new float[] {(float) p1y,(float) p2y,(float) p3y,(float) p4y},4);
	}
	
	private static FloatPolygon getWideLineTriStrip(float width, FloatPolygon line) {
		//int npoints=1+(line.npoints-1)*4+(line.npoints-2)*4;
		int npoints=(line.npoints-1)*4;
		float[] xpoints=new float[npoints],ypoints=new float[npoints];
		int n=0;
		//FloatPolygon prevfp=null;
		for(int i=0;i<line.npoints-1;i++) {
			FloatPolygon fp=getWideLine(width, line.xpoints[i], line.ypoints[i], line.xpoints[i+1], line.ypoints[i+1]);
			//if(i>0) {
			//	xpoints[n]=prevfp.xpoints[2]; ypoints[n++]=prevfp.ypoints[2];
			//	xpoints[n]=fp.xpoints[1]; ypoints[n++]=fp.ypoints[1];
			//	xpoints[n]=prevfp.xpoints[3]; ypoints[n++]=prevfp.ypoints[3];
			//}
			xpoints[n]=fp.xpoints[0]; ypoints[n++]=fp.ypoints[0];
			xpoints[n]=fp.xpoints[1]; ypoints[n++]=fp.ypoints[1];
			xpoints[n]=fp.xpoints[2]; ypoints[n++]=fp.ypoints[2];
			xpoints[n]=fp.xpoints[3]; ypoints[n++]=fp.ypoints[3];
			//prevfp=fp;
		}
		return new FloatPolygon(xpoints,ypoints,npoints);
	}
	
	//private static float[] getFloatColor(Color color) {
	//	return new float[] {(float)color.getRed()/255f,(float)color.getGreen()/255f,(float)color.getBlue()/255f,(float)color.getAlpha()/255f};
	//}
	
	public static float[] getVecSquare(float vx, float vy, float vz, float vw, float vh, float tx, float ty, float tz, float tw, float th) {
		return new float[] {
				vx, vy-vh, vz, tx, ty, tz,
				vx+vw, vy-vh, vz, tx+tw, ty, tz,
				vx+vw, vy, vz, tx+tw, ty+th, tz,
				vx, vy, vz, tx, ty+th, tz
		};
	}
	
	protected void drawTextRoiString(TextRoi troi, float x, float y, float z, boolean noscale) {
		float aa=noscale?1:(mag>1?(float)mag:1);
		float magor1=noscale?(float)mag:1f;
		Rectangle bounds=troi.getBounds();
		if((bounds.width * bounds.height) == 0)return;
		String text=troi.getText();
		if(text==null || "".contentEquals(text))return;
		//BufferedImage roiImage=new BufferedImage((int)(bounds.width*aa*mag*dpimag+0.5f), (int)(bounds.height*aa*mag*dpimag+0.5f), BufferedImage.TYPE_INT_ARGB);
		BufferedImage roiImage=new BufferedImage((int)(bounds.width*aa*dpimag+0.5f), (int)(bounds.height*aa*dpimag+0.5f), BufferedImage.TYPE_INT_ARGB);
		Graphics g=roiImage.getGraphics();
		ImagePlus rimp=troi.getImage();
		troi.setImage(null);
		troi.setLocation(0.0, 0.0);
		Font font=troi.getCurrentFont();
		troi.setFont(font.deriveFont((float)(font.getSize()*aa)));
		troi.drawOverlay(g);
		troi.setImage(rimp);
		troi.setFont(font);
		troi.setLocation(bounds.x, bounds.y);
		rglos.getTexture("text").createRgbaTexture(AWTTextureIO.newTextureData(gl.getGLProfile(), roiImage, false).getBuffer(), roiImage.getWidth(), roiImage.getHeight(), 1, 4, false);
		g.dispose();
		FloatBuffer vb=GLBuffers.newDirectFloatBuffer(getVecSquare(x, y, z, (float)bounds.width/magor1/w*2f*dpimag, (float)bounds.height/magor1/h*2f*dpimag, 0f, 1f, 0.5f, 1f, -1f));
		ShortBuffer eb=GLBuffers.newDirectShortBuffer(new short[] {0,1,2,2,3,0});
		//gl.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
		rglos.useProgram("text");
		gl.glEnable(GL_BLEND);
		rglos.drawTexVaoWithEBOVBO("text", 0, eb, vb);
		rglos.stopProgram();
		gl.glDisable(GL_BLEND);
	}
	
	private void drawTextRoiString(TextRoi troi, float z, boolean isOverlay) {
		Rectangle bounds=troi.getBounds();
		drawTextRoiString(troi, glX(bounds.x*dpimag), glY(bounds.y*dpimag), z, false);
	}
	
	private void drawString(String text, Color color, float x, float y, float z) {
		drawString(text, color, x, y, z, new Font("SansSerif", Font.PLAIN, 12));
	}
	
	private void drawString(String text, Color color, float x, float y, float z, Font font) {
		if(font==null)font=new Font(TextRoi.getDefaultFontName(), TextRoi.getDefaultFontStyle(), TextRoi.getDefaultFontSize());
		//font=new Font(font.getName(),font.getStyle(),(int)(font.getSize()*icmag+0.5));
		TextRoi troi=new TextRoi(text, 0.0, 0.0, font);
		troi.setStrokeColor(color);
		drawTextRoiString(troi, x, y, z, true);
	}
	
	
	/** z is in opengl float position, x and y in imagej coords
	 * 
	 * @param gl
	 * @param text Text to display
	 * @param just Justification (0 is left, 1 is center, 2 is right)
	 * @param color Text color 
	 */
	//protected void drawString(String text, Font font, Color color, float x, float y, float z) {
		//if(font==null) font=new Font("SansSerif", Font.PLAIN, 12);
		//TextRoi troi=new TextRoi(0, 0, text, font);
		//troi.setStrokeColor(color);
	//	drawTextRoiString(text, color, x,y,z, true);
	//}

	/*
	protected void drawTextRoiOld(TextRoi troi, float z) {
		//if(textRenderer==null || !textRenderer.getFont().equals(troi.getCurrentFont())) textRenderer = new TextRenderer(troi.getCurrentFont(),troi.getAntialiased(),false);
		int just=troi.getJustification();
		String[] text=troi.getText().split("\n");
		Rectangle b=troi.getBounds();
		FontMetrics fm=imp.getCanvas().getGraphics().getFontMetrics(troi.getCurrentFont());
		int fontHeight=fm.getHeight()-1;
		for(int i=0;i<text.length;i++) {
			float x=glX(b.x);
			float y=glY((float)b.y+(float)fm.getAscent()+(float)(fontHeight*i));
			if(just==TextRoi.LEFT) {
			}else if(just==TextRoi.CENTER) {
				x=glX((float)b.x+(float)(b.width-fm.stringWidth(text[i]))/2f);
			}else if(just==TextRoi.RIGHT) {
				x=glX((float)(b.x+b.width-fm.stringWidth(text[i])));
			}
			drawString(text[i],troi.getStrokeColor(),x,y,z,px*(float)imp.getCanvas().getMagnification(),troi.getAntialiased(),troi.getCurrentFont());
		}
	}
	*/
	/*
	 protected void drawString(String text, Font font, Color color, float x, float y, float z) {
		if(font==null) font=new Font("SansSerif", Font.PLAIN, 9);
		//if(textRenderer==null || !textRenderer.getFont().equals(font))textRenderer =new TextRenderer(font,false,false);
		FontMetrics fm=imp.getCanvas().getGraphics().getFontMetrics(font);
		String[] texta=text.split("\n");
		for(int i=0;i<texta.length;i++)
			drawString(texta[i], color, x,y-((fm.getHeight()-1)*i)/dh*2f,z, px, false, font);
	 }
	
	private void drawStringOld(String text, Color color, float x, float y, float z, float mag, boolean aa, Font font) {
		//if(textRenderer==null)textRenderer =new TextRenderer(new Font("SansSerif", Font.PLAIN, 9),false,false);
		rglos.stopProgram();
		if(regionRenderer==null) {
			RenderState rs=RenderState.createRenderState(SVertex.factory());
			rs.setColorStatic(1f,1f,1f,1f);
			rs.setHintMask(RenderState.BITHINT_GLOBAL_DEPTH_TEST_ENABLED);
			regionRenderer=RegionRenderer.create(rs, RegionRenderer.defaultBlendEnable, RegionRenderer.defaultBlendDisable);
			
		}
		GL2ES2 gl2=gl.getGL2ES2();
		//gl2.glMatrixMode(GL2.GL_PROJECTION);
		//gl2.glLoadIdentity();
		//gl2.glOrtho(-1, 1, -h/w, h/w, -1, 1);
		//gl2.glMatrixMode(GL2.GL_MODELVIEW);
		//gl2.glLoadIdentity();
		//gl2.glLoadMatrixf(glos.buffers.ubuffers.get("model").asFloatBuffer());
		
		int aabit=(aa?Region.VBAA_RENDERING_BIT:0)|Region.VARWEIGHT_RENDERING_BIT;
		regionRenderer.getRenderState().setColorStatic((float)color.getRed()/255f, (float)color.getGreen()/255f, (float)color.getBlue()/255f, (float)color.getAlpha()/255f);
		regionRenderer.init(gl2, aabit);
		TextRegionUtil util = new TextRegionUtil(aabit);
		PMVMatrix pmv=regionRenderer.getMatrix();
		pmv.glMatrixMode(GLMatrixFunc.GL_MODELVIEW);
		pmv.glLoadIdentity();
		//pmv.glLoadMatrixf(glos.buffers.ubuffers.get("model").asFloatBuffer());
		pmv.glMatrixMode(GLMatrixFunc.GL_PROJECTION);
		pmv.glLoadIdentity();
		pmv.glOrthof(-1f, 1f, -h/w, h/w, -1f, 1f);
		//regionRenderer.reshapeOrtho((int)dw, (int)dh, 0.001f, 1f);
		regionRenderer.getRenderState().setWeight(1f);
		com.jogamp.graph.font.Font jfont=null;
		try {
			jfont = FontFactory.get(FontFactory.JAVA).get(0, 0);
		} catch (IOException e) {
			e.printStackTrace();
		}
		//com.jogamp.graph.font.Font jfont=com.jogamp.graph.font.Font.Metrics
		//float ps=font.getSize();
		regionRenderer.enable(gl2, true);
		util.drawString3D(gl2, regionRenderer, jfont, 20f, text, null, new int[] {4});
		//null could be rgba color float array
		regionRenderer.enable(gl2, false);
		//textRenderer.setColor(color);
		//textRenderer.draw3D(text, x, y, z, 1f); 
		//textRenderer.end3DRendering();
		rglos.useProgram("color");
	}
	*/

}
