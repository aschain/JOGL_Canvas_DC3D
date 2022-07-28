package ajs.joglcanvas;

import java.awt.Button;
import java.awt.Checkbox;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.TextArea;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicBoolean;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Macro;
import ij.WindowManager;
import ij.plugin.PlugIn;

public class JCRecorder implements PlugIn, BIScreenGrabber {

	private static final double MIN_FRAME_RATE = 200;
	
	public AtomicBoolean saving=new AtomicBoolean(false);
	private volatile boolean updated=false;
	private BufferedImage currImage;
	private volatile boolean stop=false;
	private JCRecorderBox recbox;
	private JOGLImageCanvas dcic;
	private boolean crosshairs=false;
	private int prevCrosshairs;
	
	@Override
	public void run(String arg) {
		ImagePlus imp=null;
		String opts=Macro.getOptions();
		if(opts!=null && !opts.equals("")) {
			imp=WindowManager.getImage(opts.substring(0, opts.length()-1));
		}
		if(imp==null) imp=WindowManager.getCurrentImage();
		if(imp==null) {IJ.noImage();return;}
		dcic=JCP.getJOGLImageCanvas(imp);
		if(dcic==null){IJ.showMessage("Image is not DeepColor");return;}
		dcic.icc.getParent().requestFocus();
		prevCrosshairs=JCP.drawCrosshairs;
		JCP.drawCrosshairs=0;
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
        dcic.setBIScreenGrabber(null);
    }
	
	public void startSaving() {
		if(saving.get())return;
		saving.set(true);
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
				String title;
				int tn=0;
				do{tn++;title=dcic.getImage().getTitle()+"-rec"+IJ.pad(tn, 2);}while(WindowManager.getImage(title)!=null);
				dcic.repaint();

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

        		(new ImagePlus(title,newimgst)).show();
		        saving.set(false);
		        stop=false;
		        recbox.setStatus(mystatus+"\nCompleted movie");
				recbox.setStopEnabled(false);
				recbox.setStartEnabled(true);
			}
		}).start();
	}
	
	public void genStack() {
		if(saving.get())return;
		saving.set(true);
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
				String title;
				int tn=0;
				ImagePlus imp=dcic.getImage();
				do{tn++;title=imp.getTitle()+"-genstack"+IJ.pad(tn, 2);}while(WindowManager.getImage(title)!=null);
				dcic.setOne3Dslice(true);
				float slice=2/(float)imp.getWidth();
				float[] preveas=dcic.getEulerAngles();

				for(float z=-1f;z<1f;z+=slice) {
					if(stop)break;
					float[] eas=dcic.getEulerAngles();
					eas[5]=z;
					dcic.setEulerAngles(eas);
					dcic.repaint();
			        do{
			        	// sleep for min frame rate milliseconds
			        	try {
			        		Thread.sleep(1L);
			        	} catch (InterruptedException e) {
			        		// ignore
			        	}
		        		waitTime=System.nanoTime()-elapsedTime;
			        	elapsedTime=System.nanoTime();
		        		recbox.setStatus(mystatus+" Idle: "+(waitTime/1000000000L)+"s");
				        if(waitTime>(60L*1000000000L))stop=true;
			        }while (!stop && !updated && dcic.getPaintPending());
		        	if(updated) {
			        	currImage = convertToType(currImage, BufferedImage.TYPE_3BYTE_BGR);
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
		        		IJ.log(mystatus+" missed frame "+z);
		        		recbox.setStatus(mystatus+" missed frame "+z);
		        	}
		        }

				dcic.setEulerAngles(preveas);
				dcic.setOne3Dslice(false);
				dcic.repaint();
        		(new ImagePlus(title,newimgst)).show();
		        saving.set(false);
		        stop=false;
		        recbox.setStatus(mystatus+"\nCompleted genstack");
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
	
	class JCRecorderBox extends Frame {
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;
		private TextArea status;
		private JCRecorder recorder;
		private Button startButton, stopButton;
		
		public JCRecorderBox(JCRecorder r) {
			super("JOGL Canvas Recorder");
			recorder=r;
			setSize(400,400);
			setLayout(new GridBagLayout());
			addWindowListener(new WindowAdapter(){
				public void windowClosing(WindowEvent we){
					recorder.stop();
					dispose();
				}
			});
			GridBagConstraints c = new GridBagConstraints();
			c.fill=GridBagConstraints.HORIZONTAL;
			c.weightx=1; c.weighty=1;
			c.gridx=0;c.gridy=0;
			c.gridwidth=2;
			status=new TextArea("Idle...",5,55,TextArea.SCROLLBARS_NONE);
			status.setEditable(false);
			add(status,c);
			c.gridy=1;
			c.gridwidth=1;
			startButton=new Button("Start Recording");
			stopButton=new Button("Stop");
			stopButton.setEnabled(false);
			startButton.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					recorder.startSaving();
				}
			});
			add(startButton,c);
			c.gridx=1;
			stopButton.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					recorder.stop();
					
				}
			});
			add(stopButton,c);
			Checkbox cb=new Checkbox("Record crosshairs?", crosshairs);
			cb.addItemListener(new ItemListener() {
				public void itemStateChanged(ItemEvent e) {
					crosshairs=e.getStateChange()==ItemEvent.SELECTED;
					if(!crosshairs)JCP.drawCrosshairs=0;
					else JCP.drawCrosshairs=prevCrosshairs;
				}
			});
			c.gridy++;
			c.gridx=0;
			add(cb,c);
			Button b=new Button("Generate z-stack");
			b.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					recorder.genStack();
				}
			});
			c.gridx++;
			add(b,c);
			setVisible(true);
		}
		
		public void setStatus(String update) {
			status.setText(update);
		}
		
		public void setStartEnabled(boolean boo){
			startButton.setEnabled(boo);
		}
		public void setStopEnabled(boolean boo){
			stopButton.setEnabled(boo);
		}
	}

}
