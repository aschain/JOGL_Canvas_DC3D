package ajs.joglcanvas;

import java.awt.Button;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Label;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.AdjustmentEvent;

import ij.CompositeImage;
import ij.ImageListener;
import ij.ImagePlus;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.LUT;
import ij.process.ShortProcessor;

@SuppressWarnings("serial")
public class JCBrightness extends JCAdjuster implements ImageListener {
	
	private NumberScrollPanel[] gnsps,mins,maxs;
	private double[] dmaxs, dmins, cmins, cmaxs;
	private boolean isRunning=false;

	public JCBrightness(JOGLImageCanvas jica) {
		super("JCBrightness", jica);
		float[] inits=jic.getGamma();
		int chs=imp.getNChannels();
		dmaxs=new double[chs]; dmins=new double[chs];
		cmaxs=new double[chs]; cmins=new double[chs];
		LUT[] luts=imp.getLuts();
		if(luts==null || luts.length==0) {luts=new LUT[] {imp.getProcessor().getLut()};}
		for(int ch=0;ch<chs;ch++) {
			for(int sl=0;sl<(imp.getNSlices());sl++) {
				for(int fr=0;fr<(imp.getNFrames());fr++) {
					ImageProcessor ip=imp.getStack().getProcessor(imp.getStackIndex(ch+1, sl+1, fr+1));
					if(ip instanceof ShortProcessor)((ShortProcessor)ip).resetMinAndMax();
					else if(ip instanceof FloatProcessor)((FloatProcessor)ip).resetMinAndMax();
					else {dmaxs[ch]=255; dmins[ch]=0; sl=imp.getNSlices();break;}
					double min=ip.getMin(),max=ip.getMax();
					if((ch+fr+sl)==0) {dmaxs[ch]=max; dmins[ch]=min;}
					else {
						dmaxs[ch]=(dmaxs[ch]>max?dmaxs[ch]:max);
						dmins[ch]=(dmins[ch]<min?dmins[ch]:min);
					}
				}
			}
		}
		if(inits==null)inits=new float[] {1f,1f,1f,1f,1f,1f};
		setLayout(new GridBagLayout());
		GridBagConstraints c= new GridBagConstraints();
		c.gridx=1;
		c.gridy=0;
		c.fill=GridBagConstraints.HORIZONTAL;
		c.insets=new Insets(5,5,5,5);
		c.anchor=GridBagConstraints.WEST;
		add(new Label("Min and Max:"),c);
		c.anchor=GridBagConstraints.CENTER;
		mins=new NumberScrollPanel[imp.getNChannels()];
		maxs=new NumberScrollPanel[imp.getNChannels()];
		int exp=(imp.getBitDepth()>24?4:0);
		for(int i=0;i<chs;i++) {
			c.gridx=0;
			c.gridy++;
			c.anchor=GridBagConstraints.WEST;
			c.gridheight=2;
			add(new Label("C"+(i+1)+":"),c);
			c.anchor=GridBagConstraints.CENTER;
			c.gridheight=1;
			c.gridx=1;
			LUT lut=(i>=luts.length?luts[0]:luts[i]);
			cmins[i]=lut.min; cmaxs[i]=lut.max;
			mins[i]=new NumberScrollPanel((float)lut.min,(float)dmins[i],(float)dmaxs[i],'m',exp);
			add(mins[i], c);
			mins[i].setFocusable(false);
			c.gridy++;
			maxs[i]=new NumberScrollPanel((float)lut.max,(float)dmins[i]+1f/(float)Math.pow(10f,(float)exp),(float)dmaxs[i]+1f/(float)Math.pow(10f,(float)exp),'M',exp);
			add(maxs[i], c);
			maxs[i].setFocusable(false);
			mins[i].addAdjustmentListener(this);
			maxs[i].addAdjustmentListener(this);
		}
		c.gridy++;
		Button b=new Button("Reset");
		b.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				for(int i=0;i<chs;i++) {
					mins[i].setFloatValue((float)dmins[i]);
					maxs[i].setFloatValue((float)dmaxs[i]);
				}
				updateMinMaxs();
			}
		});
		add(b,c);
		c.anchor=GridBagConstraints.WEST;
		add(new Label("Gamma for Channels:"),c);
		c.anchor=GridBagConstraints.CENTER;
		gnsps=new NumberScrollPanel[imp.getNChannels()];
		for(int i=0;i<chs;i++) {
			c.gridx=0;
			c.anchor=GridBagConstraints.WEST;
			c.gridy++;
			add(new Label("C"+(i+1)+":"),c);
			c.anchor=GridBagConstraints.CENTER;
			c.gridx=1;
			gnsps[i]=new NumberScrollPanel(inits[i],0f,5.0f,'G',2);
			add(gnsps[i], c);
			gnsps[i].addAdjustmentListener(this);
			gnsps[i].setFocusable(false);
		}
		c.gridy++;
		b=new Button("Reset");
		b.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				for(int i=0;i<gnsps.length;i++)gnsps[i].setValue(100);
				jic.setGamma(null);
			}
		});
		add(b,c);
		pack();
		setToDefaultLocation();
		ImagePlus.addImageListener(this);
		show();
	}
	
	public void updateMinMaxScrollbars() {
		for(int i=0;i<mins.length;i++) {if(mins[i].getValueIsAdjusting())return; if(maxs[i].getValueIsAdjusting())return;}
		LUT[] luts=imp.getLuts();
		if(luts==null || luts.length==0) {luts=new LUT[] {imp.getProcessor().getLut()};}
		for(int i=0;i<mins.length;i++) {
			mins[i].setFloatValue((float)luts[i].min);
			maxs[i].setFloatValue((float)luts[i].max);
		}
	}
	
	public void updateMinMaxs() {
		int sl=imp.getZ(),fr=imp.getT();
		if(imp instanceof CompositeImage) {
			CompositeImage cimp=(CompositeImage)imp;
			LUT[] luts=cimp.getLuts();
			for(int i=0;i<imp.getNChannels();i++) {luts[i].min=mins[i].getFloatValue(); luts[i].max=maxs[i].getFloatValue();}
			cimp.setLuts(luts);
		}else {
			for(int i=0;i<imp.getNChannels();i++) {
				ImageProcessor ip=imp.getStack().getProcessor(imp.getStackIndex(i+1, sl, fr));
				ip.setMinAndMax(mins[i].getFloatValue(),maxs[i].getFloatValue());
				//LUT lut=ip.getLut(); lut.min=mins[i].getFloatValue(); lut.max=maxs[i].getFloatValue();
				//ip.setLut(lut);
			}
		}
		imp.updateAndDraw();
	}
	
	public void updateGamma() {
		float[] gamma=new float[gnsps.length];
		for(int i=0;i<gnsps.length;i++) {
			gamma[i]=gnsps[i].getFloatValue();
		}
		jic.setGamma(gamma);
	}

	@Override
	public void adjustmentValueChanged(AdjustmentEvent e) {
		if(jic.icc==null || !jic.icc.isVisible())dispose();
		Object source=e.getSource();
		if(source instanceof NumberScrollPanel) {
			for(int i=0;i<gnsps.length;i++) if(source==gnsps[i]) {updateGamma(); return;}
			for(int i=0;i<mins.length;i++) {
				if(source==mins[i]) {
					float val=mins[i].getFloatValue();
					if(val>=maxs[i].getFloatValue())maxs[i].setFloatValue(val+maxs[i].getFloatIncrement());
				}else if(source==maxs[i]) {
					float val=maxs[i].getFloatValue();
					if(val<=mins[i].getFloatValue())mins[i].setFloatValue(val-mins[i].getFloatIncrement());
				}
			}
			updateMinMaxs();
		}

	}

	@Override
	public void imageOpened(ImagePlus imp) {
	}
	@Override
	public void imageClosed(ImagePlus imp) {
	}
	@Override
	public void imageUpdated(ImagePlus imp) {
		//IJ.log("\\Update0:JCB "+java.time.Clock.systemUTC().instant());
		if(imp==this.imp && !isRunning) {
			(new Thread() {
				@Override
				public void run() {
					isRunning=true;
					updateMinMaxScrollbars();
					try {
						Thread.sleep(500);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					isRunning=false;
				}
			}).start();
		}
	}
}
