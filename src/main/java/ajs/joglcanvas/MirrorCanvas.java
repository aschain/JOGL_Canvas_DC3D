package ajs.joglcanvas;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.event.MouseEvent;

import ij.ImagePlus;
import ij.gui.ImageCanvas;

public class MirrorCanvas extends ImageCanvas {

	private static final long serialVersionUID = 1L;
	private boolean onScreenMirrorCursor=false;
	JOGLImageCanvas jic;
	
	public MirrorCanvas(JOGLImageCanvas jic, ImagePlus imp) {
		super(imp);
		this.jic=jic;
	}
	
	public void drawCursorPoint(boolean boo) {
		onScreenMirrorCursor=boo;
		repaint();
	}
	@Override
	public void paint(Graphics g){
		//jic.repaint();
		super.paint(g);
		if(onScreenMirrorCursor && jic.oicp!=null) {
			g.setColor(Color.red);
			g.drawRect(screenX(jic.oicp.x)-1,screenY(jic.oicp.y)-1,3,3);
		}
	}
	@Override
	public void mouseEntered(MouseEvent e) {
		if(JCP.drawCrosshairs)jic.glw.setPointerVisible(true);
		super.mouseEntered(e);
	}
	@Override
	public void mouseMoved(MouseEvent e) {
		jic.oicp=new Point(offScreenX(e.getX()),offScreenY(e.getY()));
		if(JCP.drawCrosshairs)jic.repaintLater();
		super.mouseMoved(e);
	}
	@Override
	public void mouseExited(MouseEvent e) {
		jic.oicp=null;
		super.mouseExited(e);
	}
	@Override
	public void mouseDragged(MouseEvent e) {
		jic.oicp=new Point(offScreenX(e.getX()),offScreenY(e.getY()));
		super.mouseDragged(e);
	}
}
