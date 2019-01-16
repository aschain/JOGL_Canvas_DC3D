package ajs.joglcanvas;

import java.awt.Button;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.TextArea;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;


public class JCRecorderBox extends Frame {
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
