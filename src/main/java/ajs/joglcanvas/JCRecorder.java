package ajs.joglcanvas;

import java.awt.image.BufferedImage;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.plugin.PlugIn;

public class JCRecorder implements PlugIn, BIScreenGrabber {

	private static final double MIN_FRAME_RATE = 200;
	
	public boolean saving=false;
	private volatile boolean updated=false;
	private BufferedImage currImage;
	private volatile boolean stop=false;
	private JCRecorderBox recbox;
	private JOGLImageCanvas dcic;
	
	@Override
	public void run(String arg) {
		ImagePlus imp=WindowManager.getCurrentImage();
		if(imp==null) {IJ.noImage();return;}
		dcic=JCP.getJOGLImageCanvas(imp);
		if(dcic==null){IJ.showMessage("Image is not DeepColor");return;}
		recbox=new JCRecorderBox(this);
	}
	
	public void screenUpdated(BufferedImage image) {
		currImage=image;
		updated=true;
	}

	public boolean isReadyForUpdate() {
		return !updated;
	}
    
    public void stop() {
    	stop=true;
    }
	
	public void startSaving() {
		if(saving)return;
		saving=true;
		recbox.setStopEnabled(true);
		recbox.setStartEnabled(false);
		dcic.setBIScreenGrabber(this);
		stop=false;
		(new Thread() {
			public void run() {
				boolean start=true;
		        long startTime = System.nanoTime();
		        long elapsedTime=startTime;
		        long waitTime=0;
		        long fn=0;
		        String mystatus="";
				ImageStack newimgst=null;

		        while ((waitTime<(60L*1000000000L)) && !stop) {
		        	if(updated) {
			        	currImage = convertToType(currImage, BufferedImage.TYPE_3BYTE_BGR);
			        	elapsedTime=System.nanoTime();
			        	mystatus="Frame: "+(fn++)+" Time: "+(((float)elapsedTime-(float)startTime)/1000000000f)+"s";
			        	recbox.setStatus(mystatus);
			        	ImagePlus adderimg=new ImagePlus("add image",currImage);
			        	if(start) {
			        		newimgst=new ImageStack(adderimg.getWidth(),adderimg.getHeight());
			        		start=false;
			        	}
		        		newimgst.addSlice(mystatus, adderimg.getProcessor());
		    			adderimg.close();
			        	updated=false;
		        	}else {
		        		waitTime=System.nanoTime()-elapsedTime;
		        		recbox.setStatus(mystatus+" Idle: "+(waitTime/1000000000L)+"s");
		        	}
		        	// sleep for min frame rate milliseconds
		        	try {
		        		Thread.sleep((long) (MIN_FRAME_RATE));
		        	} catch (InterruptedException e) {
		        		// ignore
		        	}
		        }

        		(new ImagePlus("Rec-"+dcic.getImage().getTitle(),newimgst)).show();
		        dcic.setBIScreenGrabber(null);
		        saving=false;
		        stop=false;
		        recbox.setStatus(mystatus+"\nCompleted movie");
				recbox.setStopEnabled(false);
				recbox.setStartEnabled(true);
			}
		}).start();
	}
	
	public static BufferedImage convertToType(BufferedImage sourceImage,
            int targetType) {

        BufferedImage image;

        // if the source image is already the target type, return the source
        // image
        if (sourceImage.getType() == targetType) {
            image = sourceImage;
        }
        // otherwise create a new image of the target type and draw the new
        // image
        else {
            image = new BufferedImage(sourceImage.getWidth(),
                    sourceImage.getHeight(), targetType);
            image.getGraphics().drawImage(sourceImage, 0, 0, null);
        }

        return image;

    }
}
