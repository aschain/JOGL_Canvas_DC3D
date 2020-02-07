package ajs.joglcanvas;

import java.awt.Button;
import java.awt.Container;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Label;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.AdjustmentEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;

@SuppressWarnings("serial")
public class JCRotator extends JCAdjuster implements MouseMotionListener {
	
	private final static char[] cps=new char[] {'X','Y','Z'};
	NumberScrollPanel[] rnsps= new NumberScrollPanel[3];
	NumberScrollPanel[] tnsps= new NumberScrollPanel[3];

	public JCRotator(JOGLImageCanvas jica) {
		super("Rotation and Translation", jica);
		float[] inits=jic.getEulerAngles();
		if(inits==null)inits=new float[] {0f,0f,0f,0f,0f,0f};
		setLayout(new GridBagLayout());
		GridBagConstraints c= new GridBagConstraints();
		c.gridx=0;
		c.gridy=0;
		c.fill=GridBagConstraints.HORIZONTAL;
		c.anchor=GridBagConstraints.CENTER;
		c.insets=new Insets(5,5,5,5);
		add(new Label("Rotation"),c);
		for(int i=0;i<3;i++) {
			c.gridy++;
			rnsps[i]=new NumberScrollPanel(inits[i],0,360,cps[i],0);
			add(rnsps[i], c);
			rnsps[i].addAdjustmentListener(this);
			rnsps[i].setFocusable(false);
		}
		c.gridy++;
		Button b=new Button("Reset");
		b.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if(jic.icc==null || !jic.icc.isVisible())dispose();
				for(int i=0;i<rnsps.length;i++)rnsps[i].setFloatValue(0);
				update();
			}
		});
		add(b,c);
		c.gridy++;
		add(new Label("Translation"),c);
		for(int i=0;i<3;i++) {
			c.gridy++;
			tnsps[i]=new NumberScrollPanel(inits[i+3],-2.0f,2.0f,cps[i],2);
			add(tnsps[i], c);
			tnsps[i].addAdjustmentListener(this);
			tnsps[i].setFocusable(false);
		}
		c.gridy++;
		b=new Button("Reset");
		b.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if(jic.icc==null || !jic.icc.isVisible())dispose();
				for(int i=0;i<tnsps.length;i++)tnsps[i].setFloatValue(0);
				update();
			}
		});
		add(b,c);
		pack();
		Container win=jic.icc.getParent();
		Point loc=win.getLocation();
		setLocation(new Point(loc.x+win.getSize().width+10,loc.y+5));
		show();
		jic.icc.addMouseMotionListener(this);
	}
	
	public void update() {
		float[] eas=new float[6];
		for(int i=0;i<rnsps.length;i++) {
			eas[i]=rnsps[i].getFloatValue();
			eas[i+3]=tnsps[i].getFloatValue();
		}
		jic.setEulerAngles(eas);
	}

	@Override
	public void adjustmentValueChanged(AdjustmentEvent e) {
		if(jic.icc==null || !jic.icc.isVisible())dispose();
		Object source=e.getSource();
		if(source instanceof NumberScrollPanel) {
			update();
		}

	}

	@Override
	public void mouseDragged(MouseEvent e) {
		float[] inits=jic.getEulerAngles();
		for(int i=0;i<rnsps.length;i++) {
			rnsps[i].setFloatValue(inits[i]);
			tnsps[i].setFloatValue(inits[i+3]);
			repaint();
		}
	}
	@Override
	public void mouseMoved(MouseEvent e) {}
}
