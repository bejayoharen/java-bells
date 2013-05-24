package com.xonami.javaBellsSample;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.util.List;

import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.ContentPacketExtension.SendersEnum;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JingleIQ;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.Reason;

import org.ice4j.ice.Agent;
import org.ice4j.ice.Component;
import org.ice4j.ice.IceMediaStream;
import org.ice4j.ice.IceProcessingState;
import org.jivesoftware.smack.XMPPConnection;

import com.xonami.javaBells.DefaultJingleSession;
import com.xonami.javaBells.IceAgent;
import com.xonami.javaBells.JingleStream;
import com.xonami.javaBells.JingleStreamManager;
import com.xonami.javaBells.JinglePacketHandler;

/**
 * handles jingle packets for the caller.
 * 
 * @author bjorn
 *
 */
public class CallerJingleSession extends DefaultJingleSession implements PropertyChangeListener {
	private final IceAgent iceAgent;
	private final JingleStreamManager jingleStreamManager;
	private JingleStream jingleStream;
	
	public CallerJingleSession(IceAgent iceAgent, JingleStreamManager jingleStreamManager, JinglePacketHandler jinglePacketHandler, String peerJid, String sessionId, XMPPConnection connection) {
		super(jinglePacketHandler, sessionId, connection);
		this.iceAgent = iceAgent;
		this.jingleStreamManager = jingleStreamManager;
		this.peerJid = peerJid;
		
		iceAgent.addAgentStateChangeListener( this );
	}
	
	@Override
	protected void closeSession(Reason reason) {
		super.closeSession(reason);
		if( jingleStream != null )
			jingleStream.shutdown();
		iceAgent.freeAgent();
	}

	@Override
	public void handleSessionAccept(JingleIQ jiq) {
		//acknowledge
		if( !checkAndAck(jiq) )
			return;

		state = SessionState.NEGOTIATING_TRANSPORT;
		
		try {
			if( null == jingleStreamManager.parseIncomingAndBuildMedia( jiq, SendersEnum.both ) )
				throw new IOException( "No incoming streams detected." );
			iceAgent.addRemoteCandidates( jiq );
			iceAgent.startConnectivityEstablishment();
		} catch( IOException ioe ) {
			ioe.printStackTrace();
			closeSession(Reason.FAILED_APPLICATION);
		}
	}
	
	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		Agent agent = (Agent) evt.getSource();
		
		System.out.println( "-------------- Caller - Agent Property Change - -----------------" );
		System.out.println( "New State: " + evt.getNewValue() );
		for( String s : iceAgent.getStreamNames() ) {
			System.out.println("Stream          : " + s );
			System.out.println("Local Candidate : " + agent.getSelectedLocalCandidate(s));
			System.out.println("Remote Candidate: " + agent.getSelectedRemoteCandidate(s));
		}
		System.out.println( "-------------- Caller - Agent Property Change - -----------------" );
		
        if(agent.getState() == IceProcessingState.COMPLETED) {
            List<IceMediaStream> streams = agent.getStreams();

            //////////
            for(IceMediaStream stream : streams)
            {
                String streamName = stream.getName();
                System.out.println( "Pairs selected for stream: " + streamName);
                List<Component> components = stream.getComponents();

                for(Component cmp : components)
                {
                    String cmpName = cmp.getName();
                    System.out.println(cmpName + ": " + cmp.getSelectedPair());
                }
            }

            System.out.println("Printing the completed check lists:");
            for(IceMediaStream stream : streams)
            {
                String streamName = stream.getName();
                System.out.println("Check list for  stream: " + streamName);
                //uncomment for a more verbose output
                System.out.println(stream.getCheckList());
            }
            ////////////
            
            try {
            	for( String s : iceAgent.getStreamNames() ) {
					jingleStream = jingleStreamManager.startStream(s, iceAgent);
					jingleStream.quickShow(jingleStreamManager.getDefaultAudioDevice());
				}
            } catch( IOException ioe ) {
            	ioe.printStackTrace(); //FIXME: deal with this.
            }
        } else if( agent.getState() == IceProcessingState.FAILED ) {
        	closeSession(Reason.CONNECTIVITY_ERROR);
        }
	}
}

