package ajs.joglcanvas;

import java.awt.image.BufferedImage;

public interface BIScreenGrabber {
	
	/**
	 * On every display() call, JOGLImageCanvas will check if the 
	 * screengrabber isReadyForUpdate() then send a BufferedImage
	 * to the screengrabber via screenUpdated(bufferedImageScreenShot).
	 */
	public void screenUpdated(BufferedImage bufferedImageScreenShot);
	
	/**This should return true when it can accept a new
	 * BufferedImage.  Otherwise JOGLImageCanvas will not bother
	 * to generate a new BufferedImage Screenshot at that time.
	 * 
	 * @return readyForUpdate
	 */
	public boolean isReadyForUpdate();

}
