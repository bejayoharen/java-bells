package com.xonami.javaBells;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.ContainerEvent;
import java.awt.event.ContainerListener;

import javax.swing.JFrame;
import javax.swing.JPanel;

import org.jitsi.service.neomedia.AudioMediaStream;
import org.jitsi.service.neomedia.MediaStream;
import org.jitsi.service.neomedia.VideoMediaStream;
import org.jitsi.service.neomedia.device.MediaDevice;
import org.jitsi.util.event.VideoEvent;
import org.jitsi.util.event.VideoListener;

public class JingleStream {
	private final String name;
	private final MediaStream mediaStream;
//	private final JingleStreamManager jingleStreamManager;
	private JPanel visualComponent;
	
	public JingleStream(String name, MediaStream mediaStream, JingleStreamManager jingleStreamManager) {
		this.name = name;
		this.mediaStream = mediaStream;
//		this.jingleStreamManager = jingleStreamManager;
	}
	
	public String getName() {
		return name;
	}
	
	/** quick and easy way to show the feed */
	public void quickShow(MediaDevice audioDevice) {
		JPanel p = getVisualComponent();
		if( p != null ) {
			final JFrame f = new JFrame( name );
			f.getContentPane().add(p);
			f.pack();
			f.setResizable(false);
			f.setVisible(true);
			f.toFront();
			p.addContainerListener( new ContainerListener() {
				@Override
				public void componentAdded(ContainerEvent e) {
					f.pack();
				}
				@Override
				public void componentRemoved(ContainerEvent e) {
					f.pack();
				}
			} );
		}
		startAudio(audioDevice);
	}
	
	/** starts the audio if this is an audio stream. */
	public void startAudio(MediaDevice mediaDevice) {
		if( mediaStream instanceof AudioMediaStream ) {
			AudioMediaStream ams = ((AudioMediaStream) mediaStream);
			ams.setDevice(mediaDevice);
			ams.start();
		}
	}
	
	/** returns a visual component for this stream or null if this is not a video stream. */
	public JPanel getVisualComponent() {
		if( visualComponent != null )
			return visualComponent;
		if( mediaStream instanceof VideoMediaStream ) {
			visualComponent = new JPanel( new BorderLayout() );
			VideoMediaStream vms = ((VideoMediaStream) mediaStream);
			vms.addVideoListener( new VideoListener() {
				@Override
				public void videoAdded(VideoEvent event) {
					videoUpdate(event);
				}
				@Override
				public void videoRemoved(VideoEvent event) {
					videoUpdate(event);
				}
				@Override
				public void videoUpdate(VideoEvent event) {
					updateVisualComponent();
				}
			} );
			updateVisualComponent();
			return visualComponent;
		} else {
			return null;
		}
	}
	
	private void updateVisualComponent() {
		visualComponent.removeAll();
		VideoMediaStream vms = ((VideoMediaStream) mediaStream);
		for( Component c : vms.getVisualComponents() ) {
			visualComponent.add(c); //only the first one
			break;
		}
		visualComponent.revalidate();
	}

	public void shutdown() {
		mediaStream.close();
	}
}
