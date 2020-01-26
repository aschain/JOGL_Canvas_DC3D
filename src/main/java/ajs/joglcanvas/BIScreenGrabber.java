package ajs.joglcanvas;

import java.awt.image.BufferedImage;

/**
 * On every display() call, JOGLImageCanvas will check if the 
 * screengrabber isReadyForUpdate() then send a BufferedImage
 * to the screengrabber via screenUpdated(bufferedImageScreenShot).
 */
public interface BIScreenGrabber {
	
	/**
	 * This should return true when it can accept a new
	 * BufferedImage.  Otherwise JOGLImageCanvas will not bother
	 * to generate a new BufferedImage Screenshot at that time.
	 * 
	 * @return readyForUpdate
	 */
	public boolean isReadyForUpdate();
	
	/**
	 * Puts the pixels from the display into a BufferedImage and sends to 
	 * the BIScreenGrabber via this method.
	 * @param bufferedImageScreenShot
	 */
	public void screenUpdated(BufferedImage bufferedImageScreenShot);

}
