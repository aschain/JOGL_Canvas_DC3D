package ajs.joglcanvas;

import java.awt.Component;

import com.jogamp.newt.event.KeyEvent;
import com.jogamp.newt.event.KeyListener;
import com.jogamp.newt.event.MouseEvent;
import com.jogamp.newt.event.MouseListener;

import jogamp.newt.awt.event.AWTNewtEventFactory;

public class JOGLEventAdapter implements MouseListener, KeyListener {

	private final Component source;
	private final java.awt.event.MouseListener ml;
	private final java.awt.event.MouseMotionListener mml;
	private final java.awt.event.KeyListener kl;
	
	/**
	 * 
	 * @param source  where the Mouse event should appear to come from (Newt events may come from non-Components)
	 * @param mouseListener
	 * @param mouseMotionListener
	 * @param keyListener
	 */
	public JOGLEventAdapter(Component source, com.jogamp.newt.Window win, java.awt.event.MouseListener mouseListener, java.awt.event.MouseMotionListener mouseMotionListener, java.awt.event.KeyListener keyListener) {
		this.source=source;
		this.ml=mouseListener;
		this.mml=mouseMotionListener;
		this.kl=keyListener;
		if(win!=null) {
			if(ml!=null||mml!=null)win.addMouseListener(this);
			if(kl!=null)win.addKeyListener(this);
		}
		
	}
	
	public JOGLEventAdapter(JOGLImageCanvas jic, com.jogamp.newt.Window win) {
		this(jic.icc, win, jic, jic, jic);
	}
	
	public final java.awt.event.MouseEvent convertME(MouseEvent e){
		return new java.awt.event.MouseEvent(source, eventTypeNEWT2AWT(e.getEventType()), e.getWhen(), e.getModifiers(), e.getX(), e.getY(), e.getX(), e.getY(), (int)e.getClickCount(), false, (int)e.getButton());
	}

	/**
	 * newt MouseListener->java.awt.MouseListener
	 */
	@Override
	public void mouseClicked(MouseEvent e) {if(ml!=null)ml.mouseClicked(convertME(e));}
	public void mouseEntered(MouseEvent e)  {if(ml!=null)ml.mouseEntered(convertME(e));}
	public void mouseExited(MouseEvent e) {if(ml!=null)ml.mouseExited(convertME(e));}
	public void mousePressed(MouseEvent e)  {if(ml!=null)ml.mousePressed(convertME(e));}
	public void mouseReleased(MouseEvent e) {if(ml!=null)ml.mouseReleased(convertME(e));}
	/**
	 * newt MouseListener->java.awt.MouseMotionListener
	 */
	@Override
	public void mouseMoved(MouseEvent e)  {if(mml!=null)mml.mouseMoved(convertME(e));}
	public void mouseDragged(MouseEvent e)  {if(mml!=null)mml.mouseDragged(convertME(e));}
	/**
	 * newt MouseListener->java.awt.MouseWheelListener
	 */
	@Override
	public void mouseWheelMoved(MouseEvent e) {}
	
	public static final int eventTypeNEWT2AWT(final short newtType) {
        switch( newtType ) {
            case com.jogamp.newt.event.WindowEvent.EVENT_WINDOW_DESTROY_NOTIFY: return java.awt.event.WindowEvent.WINDOW_CLOSING;
            case com.jogamp.newt.event.WindowEvent.EVENT_WINDOW_DESTROYED: return java.awt.event.WindowEvent.WINDOW_CLOSED;
            case com.jogamp.newt.event.WindowEvent.EVENT_WINDOW_GAINED_FOCUS: return java.awt.event.WindowEvent.WINDOW_ACTIVATED;
            case com.jogamp.newt.event.WindowEvent.EVENT_WINDOW_LOST_FOCUS: return java.awt.event.WindowEvent.WINDOW_DEACTIVATED;

            case com.jogamp.newt.event.WindowEvent.EVENT_WINDOW_MOVED: return java.awt.event.ComponentEvent.COMPONENT_MOVED;
            case com.jogamp.newt.event.WindowEvent.EVENT_WINDOW_RESIZED: return java.awt.event.ComponentEvent.COMPONENT_RESIZED;
            
            case com.jogamp.newt.event.MouseEvent.EVENT_MOUSE_CLICKED: return java.awt.event.MouseEvent.MOUSE_CLICKED;
            case com.jogamp.newt.event.MouseEvent.EVENT_MOUSE_PRESSED: return java.awt.event.MouseEvent.MOUSE_PRESSED;
            case com.jogamp.newt.event.MouseEvent.EVENT_MOUSE_RELEASED: return java.awt.event.MouseEvent.MOUSE_RELEASED;
            case com.jogamp.newt.event.MouseEvent.EVENT_MOUSE_MOVED: return java.awt.event.MouseEvent.MOUSE_MOVED;
            case com.jogamp.newt.event.MouseEvent.EVENT_MOUSE_ENTERED: return java.awt.event.MouseEvent.MOUSE_ENTERED;
            case com.jogamp.newt.event.MouseEvent.EVENT_MOUSE_EXITED: return java.awt.event.MouseEvent.MOUSE_EXITED;
            case com.jogamp.newt.event.MouseEvent.EVENT_MOUSE_DRAGGED: return java.awt.event.MouseEvent.MOUSE_DRAGGED;
            case com.jogamp.newt.event.MouseEvent.EVENT_MOUSE_WHEEL_MOVED: return java.awt.event.MouseEvent.MOUSE_WHEEL;

            case com.jogamp.newt.event.KeyEvent.EVENT_KEY_PRESSED: return java.awt.event.KeyEvent.KEY_PRESSED;
            case com.jogamp.newt.event.KeyEvent.EVENT_KEY_RELEASED: return java.awt.event.KeyEvent.KEY_RELEASED;

        }
        return (short)0;
    }
	
	public static final int eventTypeNEWT2AWTFE(final short newtType, boolean doWindowEvent) {
		 switch( newtType ) {
         	case com.jogamp.newt.event.WindowEvent.EVENT_WINDOW_LOST_FOCUS: if(doWindowEvent) return java.awt.event.WindowEvent.WINDOW_LOST_FOCUS; else return java.awt.event.FocusEvent.FOCUS_LOST;
            case com.jogamp.newt.event.WindowEvent.EVENT_WINDOW_GAINED_FOCUS: if(doWindowEvent)return java.awt.event.WindowEvent.WINDOW_GAINED_FOCUS; else return java.awt.event.FocusEvent.FOCUS_GAINED;
            
		 }
		 return (short)0;
	}
	
	public final java.awt.event.KeyEvent convertKE(KeyEvent e){
		return new java.awt.event.KeyEvent(source, eventTypeNEWT2AWT(e.getEventType()), e.getWhen(), e.getModifiers(), AWTNewtEventFactory.newtKeyCode2AWTKeyCode(e.getKeyCode()), e.getKeyChar());
	}

	/**
	 * newt KeyListener-> java.awt.KeyListener
	 */
	@Override
	public void keyPressed(KeyEvent e) {if(kl!=null)kl.keyPressed(convertKE(e));}
	@Override
	public void keyReleased(KeyEvent e) {if(kl!=null) {kl.keyReleased(convertKE(e)); kl.keyTyped(convertKE(e));}}
	
}
