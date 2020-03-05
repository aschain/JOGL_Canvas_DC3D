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
	private java.awt.event.MouseWheelListener mwl;
	private final java.awt.event.KeyListener kl;
	private float dpimag=1.0f;
	public boolean verbose=false;
	
	/**
	 * 
	 * @param source  where the Mouse event should appear to come from (Newt events may come from non-Components)
	 * @param mouseListener
	 * @param mouseMotionListener
	 * @param keyListener
	 */
	public JOGLEventAdapter(Component source, com.jogamp.newt.Window win, java.awt.event.MouseListener mouseListener, java.awt.event.MouseMotionListener mouseMotionListener, java.awt.event.MouseWheelListener mouseWheelListener, java.awt.event.KeyListener keyListener) {
		this.source=source;
		this.ml=mouseListener;
		this.mml=mouseMotionListener;
		this.mwl=mouseWheelListener;
		this.kl=keyListener;
		if(win!=null) {
			if(ml!=null||mml!=null||mwl!=null)win.addMouseListener(this);
			if(kl!=null)win.addKeyListener(this);
		}
	}
	
	public JOGLEventAdapter(Component source, com.jogamp.newt.Window win, java.awt.event.MouseWheelListener mouseWheelListener) {
		this(source, win, null, null, mouseWheelListener, null);
	}
	
	public JOGLEventAdapter(JOGLImageCanvas jic, com.jogamp.newt.Window win) {
		this(jic.icc, win, jic, jic, null, jic);
	}
	
	public void setDPI(float dpi) {dpimag=dpi;}
	
	public void setMouseWheelListener(java.awt.event.MouseWheelListener mouseWheelListener) {this.mwl=mouseWheelListener;}
	
	public final java.awt.event.MouseEvent convertME(MouseEvent e){
		java.awt.event.MouseEvent res=null;
		if(e.getEventType() ==MouseEvent.EVENT_MOUSE_WHEEL_MOVED) {
			float rot=e.getRotation()[1];
			if((e.getModifiers() & java.awt.event.InputEvent.SHIFT_DOWN_MASK) !=0)rot=e.getRotation()[1];
			res=new java.awt.event.MouseWheelEvent(source, eventTypeNEWT2AWT(e.getEventType()), e.getWhen(), newtModifiers2awt(e.getModifiers(),true),
					(int)(e.getX()/dpimag), (int)(e.getY()/dpimag),
					(int)(e.getX()/dpimag)+source.getLocationOnScreen().x, (int)(e.getY()/dpimag)+source.getLocationOnScreen().y, 
					(int)e.getClickCount(), (int)e.getButton()==3,
					java.awt.event.MouseWheelEvent.WHEEL_BLOCK_SCROLL, (int) e.getRotationScale(), (int)-rot, (double) -rot);
		}
		else res=new java.awt.event.MouseEvent(source, eventTypeNEWT2AWT(e.getEventType()), e.getWhen(), e.getModifiers(), 
					(int)(e.getX()/dpimag), (int)(e.getY()/dpimag), 
					(int)(e.getX()/dpimag)+source.getLocationOnScreen().x, (int)(e.getY()/dpimag)+source.getLocationOnScreen().y,
					(int)e.getClickCount(), (int)e.getButton()==3, (int)e.getButton());
		if(JCP.debug && verbose) {
			System.out.println("--");
			System.out.println("newt:"+e);
			System.out.println("awt:"+res);
		}
		
		return res;
	}

	public boolean check(Object o) {
		if(o==null)return false;
		return true;
	}
	
	/**
	 * newt MouseListener->java.awt.MouseListener
	 */
	@Override
	public void mouseClicked(MouseEvent e) {
		if(check(ml)) java.awt.EventQueue.invokeLater(new Runnable() { public void run() {ml.mouseClicked(convertME(e));}});
	}
	public void mouseEntered(MouseEvent e)  {
		if(check(ml)) java.awt.EventQueue.invokeLater(new Runnable() { public void run() {ml.mouseEntered(convertME(e));}});
	}
	public void mouseExited(MouseEvent e) {
		if(check(ml)) java.awt.EventQueue.invokeLater(new Runnable() { public void run() {ml.mouseExited(convertME(e));}});
	}
	public void mousePressed(MouseEvent e) {
		if(check(ml)) java.awt.EventQueue.invokeLater(new Runnable() { public void run() {ml.mousePressed(convertME(e));}});
	}
	public void mouseReleased(MouseEvent e) {
		if(check(ml)) java.awt.EventQueue.invokeLater(new Runnable() { public void run() {ml.mouseReleased(convertME(e));}});
	}
	/**
	 * newt MouseListener->java.awt.MouseMotionListener
	 */
	@Override
	public void mouseMoved(MouseEvent e) {
		if(check(mml)) java.awt.EventQueue.invokeLater(new Runnable() { public void run() {mml.mouseMoved(convertME(e));}});
	}
	public void mouseDragged(MouseEvent e) {
		if(check(mml)) java.awt.EventQueue.invokeLater(new Runnable() { public void run() {mml.mouseDragged(convertME(e));}});
	}
	/**
	 * newt MouseListener->java.awt.MouseWheelListener
	 */
	@Override
	public void mouseWheelMoved(MouseEvent e) {
		if(check(mwl))java.awt.EventQueue.invokeLater(new Runnable() { public void run() {mwl.mouseWheelMoved((java.awt.event.MouseWheelEvent)convertME(e));}});
	}
	
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
	
	/**
     * Converts the specified set of AWT event modifiers and extended event
     * modifiers to the equivalent NEWT event modifiers.
     *
     * <p>
     * See <a href="#AWTEventModifierMapping"> AWT event modifier mapping details</a>.
     * </p>
     *
     * @param awtMods
     * The AWT event modifiers.
     *
     * @param awtModsEx
     * The AWT extended event modifiers.
     * AWT passes mouse button specific bits here and are the preferred way check the mouse button state.
     */
    public static final int newtModifiers2awt(final int newtMods, boolean ex) {
        int awtMods = 0;

        if(!ex) {
	        if ((newtMods & com.jogamp.newt.event.InputEvent.SHIFT_MASK) != 0)     awtMods |= java.awt.event.InputEvent.SHIFT_MASK;
	        if ((newtMods & com.jogamp.newt.event.InputEvent.CTRL_MASK) != 0)      awtMods |= java.awt.event.InputEvent.CTRL_MASK;
	        if ((newtMods & com.jogamp.newt.event.InputEvent.META_MASK) != 0)      awtMods |= java.awt.event.InputEvent.META_MASK;
	        if ((newtMods & com.jogamp.newt.event.InputEvent.ALT_MASK) != 0)       awtMods |= java.awt.event.InputEvent.ALT_MASK;
	        if ((newtMods & com.jogamp.newt.event.InputEvent.ALT_GRAPH_MASK) != 0) awtMods |= java.awt.event.InputEvent.ALT_GRAPH_MASK;
        }else {
        	if ((newtMods & com.jogamp.newt.event.InputEvent.SHIFT_MASK) != 0)     awtMods |= java.awt.event.InputEvent.SHIFT_DOWN_MASK;
        	if ((newtMods & com.jogamp.newt.event.InputEvent.CTRL_MASK) != 0)      awtMods |= java.awt.event.InputEvent.CTRL_DOWN_MASK;
        	if ((newtMods & com.jogamp.newt.event.InputEvent.META_MASK) != 0)      awtMods |= java.awt.event.InputEvent.META_DOWN_MASK;
        	if ((newtMods & com.jogamp.newt.event.InputEvent.ALT_MASK) != 0)       awtMods |= java.awt.event.InputEvent.ALT_DOWN_MASK;
        	if ((newtMods & com.jogamp.newt.event.InputEvent.ALT_GRAPH_MASK) != 0) awtMods |= java.awt.event.InputEvent.ALT_GRAPH_DOWN_MASK;

        	if ((newtMods & com.jogamp.newt.event.InputEvent.getButtonMask(1)) != 0) awtMods |= java.awt.event.InputEvent.BUTTON1_DOWN_MASK;
        	if ((newtMods & com.jogamp.newt.event.InputEvent.getButtonMask(1)) != 0) awtMods |= java.awt.event.InputEvent.BUTTON2_DOWN_MASK;
        	if ((newtMods & com.jogamp.newt.event.InputEvent.getButtonMask(1)) != 0) awtMods |= java.awt.event.InputEvent.BUTTON3_DOWN_MASK;
        }

        return newtMods;
    }
	
	public final java.awt.event.KeyEvent convertKE(KeyEvent e){
		return new java.awt.event.KeyEvent(source, eventTypeNEWT2AWT(e.getEventType()), e.getWhen(), newtModifiers2awt(e.getModifiers(),true), AWTNewtEventFactory.newtKeyCode2AWTKeyCode(e.getKeyCode()), e.getKeyChar());
	}

	/**
	 * newt KeyListener-> java.awt.KeyListener
	 */
	@Override
	public void keyPressed(KeyEvent e) {
		if(check(kl))java.awt.EventQueue.invokeLater(new Runnable() { public void run() {kl.keyPressed(convertKE(e));}});
		}
	@Override
	public void keyReleased(KeyEvent e) {
		if(check(kl)) java.awt.EventQueue.invokeLater(new Runnable() { public void run() {kl.keyReleased(convertKE(e)); kl.keyTyped(convertKE(e));}});
		}
	
}
