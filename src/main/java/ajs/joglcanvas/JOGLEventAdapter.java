package ajs.joglcanvas;

import java.awt.Component;
import java.awt.Point;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import com.jogamp.newt.event.KeyEvent;
import com.jogamp.newt.event.KeyListener;
import com.jogamp.newt.event.MouseEvent;
import com.jogamp.newt.event.MouseListener;

import jogamp.newt.awt.event.AWTNewtEventFactory;

public class JOGLEventAdapter implements MouseListener, KeyListener {

	private final Component source;
	private final ArrayList<java.awt.event.MouseListener> mouseListeners=new ArrayList<java.awt.event.MouseListener>();
	private final ArrayList<java.awt.event.MouseMotionListener> mouseMotionListeners=new ArrayList<java.awt.event.MouseMotionListener>();
	private ArrayList<java.awt.event.MouseWheelListener> mouseWheelListeners=new ArrayList<java.awt.event.MouseWheelListener>();
	private final ArrayList<java.awt.event.KeyListener> keyListeners=new ArrayList<java.awt.event.KeyListener>();
	private float dpimag=1.0f;
	private Point sourceLoc=null;
	private AtomicBoolean running=new AtomicBoolean();
	static private final int MAX_MOUSE_BUTTONS=java.awt.MouseInfo.getNumberOfButtons();
	private Rectangle2D.Double adjRect=null;
	
	/**
	 * 
	 * @param source  where the Mouse event should appear to come from (Newt events may come from non-Components)
	 * @param mouseListener
	 * @param mouseMotionListener
	 * @param keyListener
	 */
	public JOGLEventAdapter(Component source, com.jogamp.newt.Window win, java.awt.event.MouseListener mouseListener, java.awt.event.MouseMotionListener mouseMotionListener, java.awt.event.MouseWheelListener mouseWheelListener, java.awt.event.KeyListener keyListener) {
		this.source=source;
		if(mouseListener!=null) mouseListeners.add(mouseListener);
		if(mouseMotionListener!=null) mouseMotionListeners.add(mouseMotionListener);
		if(mouseWheelListener!=null) mouseWheelListeners.add(mouseWheelListener);
		if(keyListener!=null) keyListeners.add(keyListener);
		if(win!=null) {
			win.addMouseListener(this);
			win.addKeyListener(this);
		}
	}
	
	public JOGLEventAdapter(JOGLImageCanvas jic, com.jogamp.newt.Window win) {
		this(jic.icc, win, jic, jic, null, jic);
	}
	/**
	 * Set the GUI DPI scale in case it does not match with AWT
	 * @param dpi
	 */
	public void setDPI(float dpi) {dpimag=dpi;}
	
	/**
	 * Allows for adjusting the mouse location
	 * adjRect is x,y -offset, w,h - scale
	 * @param r
	 */
	public void setAdjRect(Rectangle2D.Double r) {
		adjRect=r;
	}
	
	public int getDejustedX(int x) {
		if(adjRect==null) return x;
		return (int)((double)x/adjRect.width-adjRect.x);
	}
	
	public int getDejustedY(int y) {
		if(adjRect==null) return y;
		return (int)((double)y/adjRect.height-adjRect.y);
	}
	
	public java.awt.event.MouseEvent getDejustedMouseEvent(java.awt.event.MouseEvent ae){
		if(adjRect==null)return ae;
		return new java.awt.event.MouseEvent((Component)ae.getSource(), ae.getID(), ae.getWhen(), ae.getModifiers(), getDejustedX(ae.getX()),
				getDejustedY(ae.getY()), ae.getXOnScreen(), ae.getYOnScreen(), ae.getClickCount(), ae.isPopupTrigger(), ae.getButton());
	}
	
	public void addMouseListener(java.awt.event.MouseListener mouseListener) {
		if(mouseListener!=null) mouseListeners.add(mouseListener);
	}
	
	public void addMouseMotionListener(java.awt.event.MouseMotionListener mouseMotionListener) {
		if(mouseMotionListener!=null) mouseMotionListeners.add(mouseMotionListener);
	}
	
	public void addMouseWheelListener(java.awt.event.MouseWheelListener mouseWheelListener) {
		if(mouseWheelListener!=null) mouseWheelListeners.add(mouseWheelListener);
	}
	
	public void addKeyListener(java.awt.event.KeyListener keyListener) {
		if(keyListener!=null) keyListeners.add(keyListener);
	}
	
	public void removeMouseListener(java.awt.event.MouseListener mouseListener) {
		if(mouseListener!=null) mouseListeners.remove(mouseListener);
	}
	
	public void removeMouseMotionListener(java.awt.event.MouseMotionListener mouseMotionListener) {
		if(mouseMotionListener!=null) mouseMotionListeners.remove(mouseMotionListener);
	}
	
	public void removeMouseWheelListener(java.awt.event.MouseWheelListener mouseWheelListener) {
		if(mouseWheelListener!=null) mouseWheelListeners.remove(mouseWheelListener);
	}
	
	public void removeKeyListener(java.awt.event.KeyListener keyListener) {
		if(keyListener!=null) keyListeners.remove(keyListener);
	}
	
	public ArrayList<java.awt.event.MouseListener> getMouseListeners(){
		return mouseListeners;
	}
	
	public ArrayList<java.awt.event.MouseMotionListener> getMouseMotionListeners(){
		return mouseMotionListeners;
	}
	
	public ArrayList<java.awt.event.MouseWheelListener> getMouseWheelListeners(){
		return mouseWheelListeners;
	}
	
	public ArrayList<java.awt.event.KeyListener> getKeyListeners(){
		return keyListeners;
	}
	
	/**
	 * newt MouseListener->java.awt.MouseListener
	 */
	@Override
	public void mouseClicked(MouseEvent e) {
		if(mouseListeners.size()==0)return;
		final java.awt.event.MouseEvent ame=convertME(e);
		java.awt.EventQueue.invokeLater(new Runnable() { public void run() {
			for(java.awt.event.MouseListener ml : mouseListeners) ml.mouseClicked(ame);}});
	}
	public void mouseEntered(MouseEvent e)  {
		if(mouseListeners.size()==0) return; 
		final java.awt.event.MouseEvent ame=convertME(e);
		java.awt.EventQueue.invokeLater(new Runnable() { public void run() {
			for(java.awt.event.MouseListener ml : mouseListeners) ml.mouseEntered(ame);}});
	}
	public void mouseExited(MouseEvent e) {
		if(mouseListeners.size()==0) return; 
		final java.awt.event.MouseEvent ame=convertME(e);
		java.awt.EventQueue.invokeLater(new Runnable() { public void run() {
			for(java.awt.event.MouseListener ml : mouseListeners) ml.mouseExited(ame);}});
	}
	public void mousePressed(MouseEvent e) {
		if(mouseListeners.size()==0) return; 
		final java.awt.event.MouseEvent ame=convertME(e);
		java.awt.EventQueue.invokeLater(new Runnable() { public void run() {
			for(java.awt.event.MouseListener ml : mouseListeners) ml.mousePressed(ame);}});
	}
	public void mouseReleased(MouseEvent e) {
		if(mouseListeners.size()==0) return; 
		final java.awt.event.MouseEvent ame=convertME(e);
		java.awt.EventQueue.invokeLater(new Runnable() { public void run() {
			for(java.awt.event.MouseListener ml : mouseListeners) ml.mouseReleased(ame);}});
	}
	/**
	 * newt MouseListener->java.awt.MouseMotionListener
	 */
	@Override
	public void mouseMoved(MouseEvent e) {
		if(mouseMotionListeners.size()==0) return; 
		final java.awt.event.MouseEvent ame=convertME(e);
		java.awt.EventQueue.invokeLater(new Runnable() { public void run() {
			for(java.awt.event.MouseMotionListener mml : mouseMotionListeners) mml.mouseMoved(ame);}});
	}
	public void mouseDragged(MouseEvent e) {
		if(mouseMotionListeners.size()==0) return; 
		final java.awt.event.MouseEvent ame=convertME(e);
		java.awt.EventQueue.invokeLater(new Runnable() { 
			public void run() {
				for(java.awt.event.MouseMotionListener mml : mouseMotionListeners) {
					if(e.getButton()==0)mml.mouseMoved(ame);
					else mml.mouseDragged(ame);
				}
			}
		});
	}
	/**
	 * newt MouseListener->java.awt.MouseWheelListener
	 */
	@Override
	public void mouseWheelMoved(MouseEvent e) {
		if(mouseWheelListeners.size()==0) return; 
		final java.awt.event.MouseWheelEvent amwe=(java.awt.event.MouseWheelEvent)convertME(e);
		java.awt.EventQueue.invokeLater(new Runnable() { public void run() {
			for(java.awt.event.MouseWheelListener mwl : mouseWheelListeners) mwl.mouseWheelMoved(amwe);}});
	}

	/**
	 * newt KeyListener-> java.awt.KeyListener
	 */
	@Override
	public void keyPressed(KeyEvent e) {
		if(keyListeners.size()==0 || e.isAutoRepeat()) return; 
		final java.awt.event.KeyEvent ake=convertKE(e);
		java.awt.EventQueue.invokeLater(new Runnable() { public void run() {
			for(java.awt.event.KeyListener kl : keyListeners) kl.keyPressed(ake);}});
	}
	@Override
	public void keyReleased(KeyEvent e) {
		if(keyListeners.size()==0 || e.isAutoRepeat()) return; 
		final java.awt.event.KeyEvent ake=convertKE(e);
		java.awt.EventQueue.invokeLater(new Runnable() { public void run() {
			for(java.awt.event.KeyListener kl : keyListeners) {kl.keyReleased(ake); kl.keyTyped(ake);}}});
	}
	
	
	/**
	 * private converting functions, using set source and dpimag
	 * @param e
	 * @return
	 */
	private java.awt.event.MouseEvent convertME(MouseEvent e){
		if(!running.get()) {
			running.set(true);
			sourceLoc=source.getLocation(sourceLoc);
			running.set(false);
		}
		return convertME(e, source, dpimag, sourceLoc, adjRect);
	}
	private java.awt.event.KeyEvent convertKE(KeyEvent e){
		return convertKE(e, source);
	}
	
	
	
	/**
	 * Converts NEWT MouseEvent to AWT MouseEvent
	 * 
	 * @param e			The NEWT MouseEvent
	 * @param source	The apparent AWT source of the event
	 * @param dpimag	To correct for dpi magnification if necessary (use 1.0f if not)
	 * @return			The AWT MouseEvent
	 */
	public static java.awt.event.MouseEvent convertME(MouseEvent e, Component source, float dpimag){
		Point p=null;
		if(source.isVisible()) {
			try {
				p=source.getLocationOnScreen();
			}catch(Exception ex) {}
		}
		return convertME(e,source,dpimag,p, null);
	}
	
	/**
	 * Converts NEWT MouseEvent to AWT MouseEvent and does not require a call to the awt source
	 * 
	 * @param e			The NEWT MouseEvent
	 * @param source	The apparent AWT source of the event
	 * @param dpimag	To correct for dpi magnification if necessary (use 1.0f if not)
	 * @return			The AWT MouseEvent
	 */
	public static java.awt.event.MouseEvent convertME(MouseEvent e, Component source, float dpimag, Point sourceLoc, Rectangle2D.Double adjRect){
		java.awt.event.MouseEvent res=null;
		int x=(int)(e.getX()/dpimag),
			y=(int)(e.getY()/dpimag),
			sx=x, sy=y;
		if(sourceLoc!=null) {
			sx=x+sourceLoc.x;
			sy=y+sourceLoc.y;
		}
		if(adjRect!=null) {
			x+=(int)adjRect.x; y+=(int)adjRect.y;
			x=(int)((double)x*adjRect.width); y=(int)((double)y*adjRect.height);
		}
		if(e.getEventType() ==MouseEvent.EVENT_MOUSE_WHEEL_MOVED) {
			float rot=e.getRotation()[1];
			if((e.getModifiers() & java.awt.event.InputEvent.SHIFT_DOWN_MASK) !=0)rot=e.getRotation()[1];
			res=new java.awt.event.MouseWheelEvent(source, eventTypeNEWT2AWT(e.getEventType()), e.getWhen(), newtModifiers2awt(e.getModifiers(),true),
					x, y, sx, sy, (int)e.getClickCount(), (int)e.getButton()==3,
					java.awt.event.MouseWheelEvent.WHEEL_BLOCK_SCROLL, (int) e.getRotationScale(), (int)-rot, (double) -rot);
		}
		else res=new java.awt.event.MouseEvent(source, eventTypeNEWT2AWT(e.getEventType()), e.getWhen(), newtModifiers2awt(e.getModifiers(),true), 
					x, y, sx, sy, (int)e.getClickCount(), (int)e.getButton()==3, (int)e.getButton());
		/*
		if(JCP.debug) {
			System.out.println("--");
			System.out.println("newt:"+e);
			System.out.println("awt:"+res);
		}
		*/
		return res;
	}
	
	/**
	 * Converts NEWT KeyEvent to AWT KeyEvent
	 * @param NEWT KeyEvent e
	 * @return AWT KeyEvent
	 */
	public static java.awt.event.KeyEvent convertKE(KeyEvent e, Component source){
		return new java.awt.event.KeyEvent(source, eventTypeNEWT2AWT(e.getEventType()), e.getWhen(), newtModifiers2awt(e.getModifiers(),true), AWTNewtEventFactory.newtKeyCode2AWTKeyCode(e.getKeyCode()), e.getKeyChar());
	}
	
	/**
	 * Adapted from eventTypeAWT2NEWT from jogamp.newt.event.AWTNewtEventFactory
	 * 
	 * @param newtType
	 * @return
	 */
	public static int eventTypeNEWT2AWT(final short newtType) {
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

	/**
	 * Adapted from eventTypeAWT2NEWT from jogamp.newt.event.AWTNewtEventFactory (The FocusEvents)
	 * 
	 * @param newtType
	 * @param doWindowEvent
	 * @return
	 */
	public static int eventTypeNEWT2AWTFE(final short newtType, boolean doWindowEvent) {
		 switch( newtType ) {
         	case com.jogamp.newt.event.WindowEvent.EVENT_WINDOW_LOST_FOCUS: if(doWindowEvent) return java.awt.event.WindowEvent.WINDOW_LOST_FOCUS; else return java.awt.event.FocusEvent.FOCUS_LOST;
            case com.jogamp.newt.event.WindowEvent.EVENT_WINDOW_GAINED_FOCUS: if(doWindowEvent)return java.awt.event.WindowEvent.WINDOW_GAINED_FOCUS; else return java.awt.event.FocusEvent.FOCUS_GAINED;
            
		 }
		 return (short)0;
	}
	
	/**
	 * Modified from awtModifiers2Newt from jogamp.newt.event.AWTNewtEventFactory
	 * 
     * Converts the specified set of NEWT event modifiers
     * modifiers to the equivalent AWT event modifiers and extended event.
     *
     * <p>
     * See <a href="#AWTEventModifierMapping"> AWT event modifier mapping details</a>.
     * </p>
     *
     * @param newtMods
     * The NEWT event modifiers.
     *
     * @param ex
     * Whether to output AWT extended event modifiers, or regular modifiers.
     * 
     */
    public static int newtModifiers2awt(final int newtMods, boolean ex) {
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
        }

        for(int i=0;i<MAX_MOUSE_BUTTONS;i++) {
        	if((newtMods & com.jogamp.newt.event.InputEvent.getButtonMask(i+1)) != 0) awtMods |= java.awt.event.InputEvent.getMaskForButton(i+1);
        }

        return awtMods;
    }
	
}
