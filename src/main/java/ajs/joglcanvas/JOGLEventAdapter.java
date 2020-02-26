package ajs.joglcanvas;

import com.jogamp.newt.event.MouseEvent;
import com.jogamp.newt.event.MouseListener;

public class JOGLEventAdapter implements MouseListener {

	private JOGLImageCanvas jic;
	private java.awt.event.KeyListener kl=null;
	
	public JOGLEventAdapter(JOGLImageCanvas jic) {
		this.jic=jic;
	}
	
	public void setKeyListener(java.awt.event.KeyListener kl) {
		this.kl=kl;
	}
	
	public java.awt.event.MouseEvent convertMouseEvent(MouseEvent e){
		
	}

	@Override
	public void mouseClicked(MouseEvent e) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void mouseEntered(MouseEvent e) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void mouseExited(MouseEvent e) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void mousePressed(MouseEvent e) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void mouseReleased(MouseEvent e) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void mouseMoved(MouseEvent e) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void mouseDragged(MouseEvent e) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void mouseWheelMoved(MouseEvent e) {
		// TODO Auto-generated method stub
		
	}

}
