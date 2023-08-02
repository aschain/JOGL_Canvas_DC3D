package ajs.joglcanvas;

import java.awt.Component;
import java.awt.Container;
import javax.swing.JFrame;
import ij.ImagePlus;
import ij.gui.StackWindow;

public class JCStackWindow extends StackWindow {

	private final boolean isMirror;
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	public JCStackWindow(ImagePlus imp, JOGLImageCanvas jic) {
		super(imp,jic);
		this.isMirror=jic.isMirror;
		if(!isMirror) {
			Container c=this;
			if(c instanceof JFrame)c=((JFrame)c).getContentPane();
	        Component[] wincs=c.getComponents();
	        
	        for(int i=0;i<wincs.length;i++) {
	        		if(wincs[i] instanceof JOGLImageCanvas) {c.remove(i); c.add(jic.icc, i);}
	        }
			repaint();
			if(JCP.mouseWheelFix)jic.addMouseWheelListener(this);
		}
		setTitle(imp.getTitle());
	}
	
	@Override
	public void setTitle(String title) {
		if(!isMirror)super.setTitle(title+" (JOGL Canvas)");
		else super.setTitle(title);
	}
	
	@Override
	public boolean close() {
		if(isMirror && imp.getWindow()==this) ((JOGLImageCanvas) imp.getCanvas()).dispose();
		return super.close();
	}
		
}
