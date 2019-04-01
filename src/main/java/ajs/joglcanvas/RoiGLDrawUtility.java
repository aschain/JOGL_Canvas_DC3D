package ajs.joglcanvas;

import static com.jogamp.opengl.GL3.*;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Rectangle;
import java.nio.FloatBuffer;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL3;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.util.GLBuffers;
import com.jogamp.opengl.util.awt.TextRenderer;

import ij.ImagePlus;
import ij.gui.Arrow;
import ij.gui.OvalRoi;
import ij.gui.PointRoi;
import ij.gui.Roi;
import ij.gui.TextRoi;
import ij.process.FloatPolygon;

public class RoiGLDrawUtility {
	private TextRenderer textRenderer;
	private ImagePlus imp;
	private GL3 gl;
	private JCGLObjects rglos=null;
	float px=2f/1024f;
	float yrat=1f;
	float w=-1f,h,offx,offy;
	boolean isAnaglyph=false;
	Color anacolor=null;

	public RoiGLDrawUtility(ImagePlus imp) {
		this.imp=imp;
		Rectangle srcRect=imp.getCanvas().getSrcRect();
		w=(float)srcRect.width; h=(float)srcRect.height;
		offx=(float)srcRect.x; offy=(float)srcRect.y;
		px=2f/((float)imp.getCanvas().getMagnification()*w);
		yrat=(float)srcRect.height/srcRect.width;
	}
	
	public void updateSrcRect(GLAutoDrawable drawable) {
		Rectangle srcRect=imp.getCanvas().getSrcRect();
		w=(float)srcRect.width; h=(float)srcRect.height;
		offx=(float)srcRect.x; offy=(float)srcRect.y;
		px=2f/(float)drawable.getSurfaceWidth();
		yrat=(float)srcRect.height/srcRect.width;
	}
	
	public void drawRoiGL(GLAutoDrawable drawable, Roi roi, float z, boolean drawHandles) {
		drawRoiGL(drawable,roi,z,drawHandles,null);
	}
	
	public void setGL(GLAutoDrawable drawable) {
		gl=drawable.getGL().getGL3();
	}
	
	public void setGL(GL gl1) {
		gl=gl1.getGL3();
	}

	public void drawRoiGL(GLAutoDrawable drawable, Roi roi, float z, boolean drawHandles, Color acolor) {
		setGL(drawable);
		if(roi==null)return;
		isAnaglyph=(acolor!=null);
		this.anacolor=acolor;
		setGL(drawable);
		updateSrcRect(drawable);
		
		gl.glDisable(GL_MULTISAMPLE);

		int tp=roi.getType();

		if(roi instanceof TextRoi){
			drawTextRoi((TextRoi)roi, z);
			if(!drawHandles)return;
		}

		FloatPolygon fp=roi.getFloatPolygon();
		if(roi instanceof OvalRoi) {
			fp=getOvalFloatPolygon((OvalRoi)roi);
		}
		//float[] xpoints=fp.xpoints;
		//float[] ypoints=fp.ypoints;
		//int n=fp.npoints;
		//IJ.log("Roi: "+roi.getClass().getName());
		if(tp==Roi.LINE && roi.getStrokeWidth()>1 && fp.npoints==4) {
			fp=new FloatPolygon(
					new float[] {(fp.xpoints[0]+fp.xpoints[1])/2,(fp.xpoints[2]+fp.xpoints[3])/2},
					new float[] {(fp.ypoints[0]+fp.ypoints[1])/2,(fp.ypoints[2]+fp.ypoints[3])/2},
					2);
		}
		
		int todraw=GL_LINE_STRIP;
		if(tp==Roi.RECTANGLE || tp==Roi.TRACED_ROI || tp==Roi.OVAL || (!(roi.getState()==Roi.CONSTRUCTING) && (tp==Roi.POLYGON || tp==Roi.FREEROI)) )todraw=GL_LINE_LOOP;
		if(tp==Roi.FREEROI && (roi instanceof ij.gui.EllipseRoi || roi.getClass().getName()=="ij.gui.RotatedRectRoi"))todraw=GL_LINE_LOOP;
		//if(tp==Roi.POINT)todraw=GL_POINTS;
		gl.glLineWidth(1f);
		Color color=Roi.getColor();
		if(isAnaglyph) color=anacolor;
		
		float[] coords=getGLCoords(fp, z, false);
		int n=fp.npoints;
		float[] xpoints=new float[n];
		float[] ypoints=new float[n];
		for(int i=0;i<n;i++) {
			xpoints[i]=coords[i*3];
			ypoints[i]=coords[i*3+1];
		}
		
		if(tp==Roi.POINT) {
			drawPoints((PointRoi)roi, xpoints, ypoints, z);
			drawHandles=false;
		}else {
			gl.glDisable(GL_BLEND);
			drawGL(coords, color,todraw);
			
			//if it is a line with width
			float strokeWidth=roi.getStrokeWidth();
			if(strokeWidth>1 && (tp==Roi.LINE || tp==Roi.FREELINE || tp==Roi.POLYLINE) && !(roi instanceof Arrow) && fp.npoints>=2) {
				Color c=new Color(color.getRed(), color.getGreen(), color.getBlue(), 77);
				drawPolyWideLine(fp, c, strokeWidth, z);
			}
			if(roi instanceof Arrow) { //arrow
				gl.glEnable(GL_MULTISAMPLE);
				drawArrow((Arrow)roi, color, z);
			}
		}
		
		if(drawHandles) {
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
				if(n==44) { //roundrect
					xpoints[0]=xpoints[8];
					xpoints[1]=xpoints[9]; ypoints[1]=ypoints[19];
					xpoints[2]=xpoints[32]; ypoints[2]=ypoints[20];
					xpoints[3]=xpoints[33]; ypoints[3]=ypoints[43];
				};
				xhandles=new float[8];
				yhandles=new float[8];
				for(int i=0;i<4;i++) {
					xhandles[i*2]=xpoints[i]; yhandles[i*2]=ypoints[i];
					int j=(i==(n-1))?(0):(i+1);
					xhandles[i*2+1]=(xpoints[i]+xpoints[j])/2; yhandles[i*2+1]=(ypoints[i]+ypoints[j])/2;
				}
			}else if(tp==Roi.POLYGON || tp==Roi.POLYLINE || tp==Roi.ANGLE) {
				xhandles=xpoints; yhandles=ypoints;
			}else if(tp==Roi.LINE){
				if(n==2) {
					xhandles=new float[] {xpoints[0],(xpoints[0]+xpoints[1])/2,xpoints[1]};
					yhandles=new float[] {ypoints[0],(ypoints[0]+ypoints[1])/2,ypoints[1]};
				}
			}else if(tp==Roi.FREEROI) {
				if(n==4 && !(roi instanceof ij.gui.FreehandRoi)) {
					xhandles=new float[4];
					yhandles=new float[4];
					for(int i=0;i<4;i++) {
						int j=(i==(n-1))?(0):(i+1);
						xhandles[i]=(xpoints[i]+xpoints[j])/2; yhandles[i]=(ypoints[i]+ypoints[j])/2;
					}
				}
			}
			for(int i=0;i<xhandles.length;i++) {
				drawHandle(xhandles[i],yhandles[i],z,Roi.HANDLE_SIZE);
			}
		}
	}
	
	public float[] getSubGLCoords(FloatPolygon fp, int start, int end, float z, boolean convert) {

		int length=end-start;
		float[] coords=new float[length*3];
		for(int i=start;i<end;i++) {
			float x=(fp.xpoints[i]-offx)/w*2f-1f;
			float y=((1-(fp.ypoints[i]-offy)/h)*2f-1f)*yrat;
			coords[(i-start)*3]=x; coords[(i-start)*3+1]=y; coords[(i-start)*3+2]=z;
			if(convert) {
				fp.xpoints[i]=x;
				fp.ypoints[i]=y;
			}
		}
		return coords;
	}
	
	public float[] getGLCoords(FloatPolygon fp, float z, boolean convert) {

		return getSubGLCoords(fp,0,fp.npoints,z,convert);
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
		drawGLfb(gl, fb, toDraw);
	}
	/**
	 * drawGLfb requires a FloatBuffer fb which has 3 position floats followed by 4 color floats per vertex.
	 * @param gl
	 * @param fb
	 * @param toDraw
	 */
	public void drawGLfb(GL3 gl, FloatBuffer fb, int toDraw) {
		setGL(gl);
		if(rglos==null) {
			rglos= new JCGLObjects(gl);
			rglos.newBuffer(GL_ARRAY_BUFFER, "roiGL");
			rglos.newBuffer(GL_ELEMENT_ARRAY_BUFFER, "roiGL");
			rglos.newVao("roiGL", 3, GL_FLOAT, 4, GL_FLOAT);
			rglos.programs.newProgram("color", "shaders", "color", "color");
		}else rglos.setGL(gl);
		rglos.drawVao(toDraw, "roiGL", fb, "color");
	}
	
	/** draws an Roi handle. x,y,z are all opengl float positions
	 *  Handle is in  IMAGEJ int!!
	 */
	public void drawHandle(float x, float y, float z, int hsi, Color color, boolean border) {
		hsi/=2;
		float hs=(float)hsi/(((1f/px)<128f)?2f:1f);
		
		gl.glLineWidth(1f);
		Color acolor=color;
		if(isAnaglyph)acolor=anacolor;
		float[] coords={
				x-hs*px, y-hs*px, z,
				x+hs*px, y-hs*px, z,
				x-hs*px, y+hs*px, z,
				x+hs*px, y+hs*px, z
		};
		drawGL(coords,acolor,GL_TRIANGLE_STRIP);

		if(border) {
			coords[6]=coords[9]; coords[9]=x-hs*px;
			drawGL(coords,new float[] {0f, 0f, 0f,1f},GL_LINE_LOOP);
		}
	}
	
	/** draws a default white Roi handle. x,y,z are all opengl float positions*/
	public void drawHandle(float x, float y, float z, int handlesize) {
		drawHandle(x,y,z,handlesize,Color.WHITE, true);
	}
	
	/** draws points. x,y,z are all opengl float positions*/
	public void drawPoints(PointRoi roi, float[] x, float[] y, float z) {
		for(int n=0;n<x.length;n++)drawPoint(roi, x[n],y[n],z, n);
	}
	
	/** draws a point. x,y,z are all opengl float positions*/
	public void drawPoint(PointRoi roi, float x, float y, float z, int n) {
		gl.glDisable(GL_BLEND);
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
		boolean colorSet = false;
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
		if (type==HYBRID || type==CROSSHAIR) {
			if (type==0)
				if(isAnaglyph) color=anacolor;
				else color=Color.white;
			else {
				if(isAnaglyph) color=anacolor;
				colorSet = true;
			}
			gl.glLineWidth(1f);
			if (sizei>LARGE)
				gl.glLineWidth(3f);
			drawLine(x-(sizei+3)*px, y, x+(sizei+2)*px, y, z, color);
			drawLine(x, y-(sizei+3)*px, x, y+(sizei+2)*px, z, color);
		}
		if (type==HYBRID || type==DOT) { 
			if (!colorSet) {
				if(isAnaglyph)color=anacolor;
				colorSet = true;
			}
			if (sizei>LARGE)
				gl.glLineWidth(1f);
			if (sizei>LARGE && type==DOT)
				fillOval(x-sizei/2*px, y-sizei/2*px, sizei*px, sizei*px, z, color);
			else if (sizei>LARGE && type==HYBRID)
				drawHandle(x,y,z,sizei-4,color, false);
			else if (sizei>SMALL && type==HYBRID)
				drawHandle(x,y,z,sizei-2,color, false);
			else
				drawHandle(x,y,z,sizei,color, false);
		}
		int nPoints=roi.getNCoordinates();
		float fontSize=9f*px;
		if (roi.getShowLabels() && nPoints>1) {
			float offset = 2f;
			if (nCounters==1) {
				//if (!colorSet)
					//setColor(gl,color);
				drawString(""+n, TextRoi.LEFT, color, x+offset*px, y-(offset*px+fontSize), z);
			} else if (counters!=null) {
				//setColor(gl, getPointColor(counters[n-1]));
				drawString(""+counters[n-1], TextRoi.LEFT, getPointColor(counters[n-1]), x+offset*px, y-(offset*px+fontSize), z);
			}
		}
		if ((sizei>TINY||type==DOT) && (type==HYBRID||type==DOT)) {
			if (sizei>LARGE && type==HYBRID)
				drawOval(x-(sizei/2-1)*px, y-(sizei/2)*px, (sizei-2)*px, (sizei-2)*px, z, Color.black);
			else if (sizei>SMALL && type==HYBRID)
				drawOval(x-sizei/2*px, y-(sizei/2+1)*px, (sizei)*px, (sizei)*px, z, Color.black);
			else
				drawOval(x-(sizei/2+1)*px, y-(sizei/2+2)*px, (sizei+2)*px, (sizei+2)*px, z, Color.black);
		}
		if (type==CIRCLE) {
			float scaledSize = (sizei+1);
			if(isAnaglyph)color=anacolor;
			if (sizei>LARGE)
				gl.glLineWidth(2f);
			drawOval(x-scaledSize*px/2f, y-scaledSize*px/2f, scaledSize*px, scaledSize*px, z, color);
		}
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
		gl.glEnable(GL_BLEND);
		gl.glEnable(GL_STENCIL_TEST);
		gl.glStencilFunc(GL_EQUAL, 0, 0xFF);
		gl.glStencilOp(GL_KEEP, GL_KEEP, GL_INCR);
		gl.glStencilMask(0xFF); // Write to stencil buffer
		gl.glClear(GL_STENCIL_BUFFER_BIT); // Clear stencil buffer (0 by default)
		drawGL(wlcoords, color, GL_TRIANGLE_STRIP);
		gl.glStencilMask(0x00);
		gl.glDisable(GL_STENCIL_TEST);
	}
	
	private void drawArrow(Arrow aroi, Color color, float z) {
		drawArrow(aroi,color, z,false);
		if(aroi.getDoubleHeaded())drawArrow(aroi,color, z,true);
	}
	
	private void drawArrow(Arrow aroi, Color color, float z, boolean flip) {
		//ripped from Arrow calculatePoints()
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
			}else {
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
		drawPolyWideLine(shaftfp, aroi.getStrokeColor(), (float)shaftWidth, z);
		if(style==Arrow.FILLED||style==Arrow.NOTCHED) {
			float[] acoords=getGLCoords(new FloatPolygon(new float[] {points[2*1],points[2*2],points[2*3],points[2*1],points[2*4],points[2*3]},new float[] {points[2*1+1],points[2*2+1],points[2*3+1],points[2*1+1],points[2*4+1],points[2*3+1]}, 6),z,false);
			drawGL(acoords, color, GL_TRIANGLES);
		}
	}
	
	public float glX(float x) {
		return (x-offx)/w*2f-1f;
	}
	
	public float glY(float y) {
		return ((h-(y-offy))/h*2f-1f)*yrat;
	}
	
	public int impX(float x) {
		return (int)((x+1f)*w/2f+offx);
	}
	
	public int impY(float y) {
		return (int)(offy+h-((y/yrat+1f)*h/2f));
	}
	
	/** x,y, z are in opengl float positions*/
	public void drawLine(float x1,float y1, float x2,float y2, float z, Color color) {
		drawGL(new float[] {x1,y1,z,x2,y2,z},color,GL_LINE_STRIP);
	}
	
	/** x,y, z are in opengl float positions*/
	public void fillOval(float x, float y, float width, float height, float z, Color color) {
		drawOval(x, y, width, height, z, true, color);
	}
	
	/** x,y, z are in opengl float positions*/
	public void drawOval(float x, float y, float width, float height, float z, Color color) {
		drawOval(x, y, width, height, z, false, color);
	}
	
	/** x,y, z are in opengl float positions*/
	public void drawOval(float x, float y, float width, float height, float z, boolean fill, Color color) {
		if(imp==null)return;
		int todraw=GL_LINE_LOOP;
		if(fill)todraw=GL_TRIANGLE_STRIP;
		drawGLFP(todraw, getOvalFloatPolygon(new Rectangle(impX(x),impY(y),(int)(width*w/2f),(int)(-height*h/2f)),72),z, color);
	}
	
	/** FloatPolygon has coordinates in IMAGEJ (NOT opengl -1 to 1) except z*/
	public void drawGLFP(int GLtypetodraw, FloatPolygon fp, float z, Color color) {
		drawGL(getGLCoords(fp,z,false), color,GLtypetodraw);
	}
	
	public static FloatPolygon getOvalFloatPolygon(OvalRoi roi) {
		return getOvalFloatPolygon(roi.getBounds(), 72);
	}
	
	/** minimizes points but has unequal distribution over the oval*/
	public static FloatPolygon getMinOvalFloatPolygon(final Rectangle b) {
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
	
	/** 
	 * Rectangle in IMAGEJ coordinates, and returns FloatPolygon with IMAGEJ
	 * coordinates of oval with the bounding rectangle
	 * @param b The bounding rectangle of the oval
	 * @param num Number of points to make up the oval, a divisor of 
	 *			  360 and between 20 and 360
	 * @return FloatPolygon with ImageJ coordinates of an oval
	 */
	public static FloatPolygon getOvalFloatPolygon(final Rectangle b, int num) {
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
	
	
	public static FloatPolygon getWideLine(float width, float x1, float y1, float x2, float y2) {
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
	
	public static FloatPolygon getWideLineTriStrip(float width, FloatPolygon line) {
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
	
	public static float[] getFloatColor(Color color) {
		return new float[] {(float)color.getRed()/255f,(float)color.getGreen()/255f,(float)color.getBlue()/255f,(float)color.getAlpha()/255f};
	}
	
	protected void drawTextRoi(TextRoi troi, float z) {
		if(textRenderer==null || !textRenderer.getFont().equals(troi.getCurrentFont())) textRenderer = new TextRenderer(troi.getCurrentFont(),troi.getAntialiased(),false);
		int just=troi.getJustification();
		String[] text=troi.getText().split("\n");
		Rectangle b=troi.getBounds();
		FontMetrics fm=imp.getCanvas().getGraphics().getFontMetrics(troi.getCurrentFont());
		int fontHeight=fm.getHeight()-1;
		textRenderer.begin3DRendering();
		textRenderer.setColor(troi.getStrokeColor());
		for(int i=0;i<text.length;i++) {
			float x=glX(b.x);
			float y=glY((float)b.y+(float)fm.getAscent()+(float)(fontHeight*i));
			if(just==TextRoi.LEFT) {
			}else if(just==TextRoi.CENTER) {
				x=glX((float)(b.width-fm.stringWidth(text[i]))/2f);
			}else if(just==TextRoi.RIGHT) {
				x=glX((float)(b.x-offx+b.width-fm.stringWidth(text[i])));
			}
			textRenderer.draw3D(text[i], x, y, z, px*(float)imp.getCanvas().getMagnification()); 
		}
		textRenderer.end3DRendering();
	}
	
	/** x,y,z are in opengl float positions
	 * 
	 * @param gl
	 * @param text Text to display
	 * @param just Justification (0 is left, 1 is center, 2 is right)
	 * @param color Text color
	 */
	protected void drawString(String text, int just, Color color, float x, float y, float z) {
		Font font=new Font("SansSerif", Font.PLAIN, 9);
		if(textRenderer==null || !textRenderer.getFont().equals(font))textRenderer =new TextRenderer(font,false,false);
		textRenderer.begin3DRendering();
		textRenderer.setColor(color);
		textRenderer.draw3D(text, x, y, z, px); 
		textRenderer.end3DRendering();
	}

}
