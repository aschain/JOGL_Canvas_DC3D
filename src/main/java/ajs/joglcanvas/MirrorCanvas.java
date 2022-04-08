package ajs.joglcanvas;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

import ij.ImagePlus;
import ij.gui.ImageCanvas;

public class MirrorCanvas extends ImageCanvas {

	private static final long serialVersionUID = 1L;
	private boolean onScreenMirrorCursor=false;
	JOGLImageCanvas jic;
	private double dpimag=1.0;
	
	public MirrorCanvas(JOGLImageCanvas jic, ImagePlus imp) {
		super(imp);
		this.jic=jic;
		addKeyListener(new KeyAdapter() {
			@Override
			public void keyReleased(KeyEvent e) {
				jic.repaintLater();
			}
		});
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
			g.drawRect(screenXD(jic.oicp.x)-1,screenYD(jic.oicp.y)-1,3,3);
		}
	}
	@Override
	public void mouseEntered(MouseEvent e) {
		if(JCP.drawCrosshairs>0)jic.glw.setPointerVisible(true);
		super.mouseEntered(e);
	}
	@Override
	public void mouseMoved(MouseEvent e) {
		if(JCP.drawCrosshairs>0) {
			jic.setImageCursorPosition(e, dpimag);
			jic.repaintLater();
		}
		super.mouseMoved(e);
	}
	@Override
	public void mouseExited(MouseEvent e) {
		jic.oicp=null;
		super.mouseExited(e);
	}
	@Override
	public void mouseDragged(MouseEvent e) {
		if(JCP.drawCrosshairs>0) {
			jic.setImageCursorPosition(e, dpimag);
		}
		jic.repaintLater();
		super.mouseDragged(e);
	}
	@Override
	public void mousePressed(MouseEvent e) {
		jic.repaintLater();
		super.mousePressed(e);
	}
	@Override
	public void mouseReleased(MouseEvent e) {
		jic.repaintLater();
		super.mouseReleased(e);
	}
}
