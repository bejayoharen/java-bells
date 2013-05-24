package com.xonami.javaBellsSample;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.util.List;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.ContentPacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JingleIQ;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JinglePacketFactory;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.ContentPacketExtension.CreatorEnum;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.Reason;

import org.ice4j.ice.Agent;
import org.ice4j.ice.IceProcessingState;
import org.jivesoftware.smack.XMPPConnection;

import com.xonami.javaBells.DefaultJingleSession;
import com.xonami.javaBells.IceAgent;
import com.xonami.javaBells.JinglePacketHandler;
import com.xonami.javaBells.JingleStream;
import com.xonami.javaBells.JingleStreamManager;
import com.xonami.javaBells.StunTurnAddress;

/**
 * Handles jingle packets for the receiver.
 * In this example, we accept all calls, but by changing the code in
 * accept call from, we could easily change it to only accept
 * sessionInitiation requests if they come from the expected caller.
 * 
 * @author bjorn
 *
 */
public class ReceiverJingleSession extends DefaultJingleSession implements PropertyChangeListener {
	private final String callerJid;
	private IceAgent iceAgent;
	private JingleStreamManager jingleStreamManager;
	private JingleStream jingleStream;

	public ReceiverJingleSession(JinglePacketHandler jinglePacketHandler, String callerJid, String sessionId, XMPPConnection connection) {
		super(jinglePacketHandler, sessionId, connection);
		this.callerJid = callerJid;
	}
	
	@Override
	protected void closeSession(Reason reason) {
		super.closeSession(reason);
		if( jingleStream != null )
			jingleStream.shutdown();
		iceAgent.freeAgent();
	}

	/** accepts the call only if it's from the caller want. */
	@Override
	public void handleSessionInitiate(JingleIQ jiq) {
		// acknowledge:
		ack(jiq);
		// set the peerJid
		peerJid = jiq.getFrom();
		// compare it to the expected caller:
		try {
			if ( acceptCallFrom(peerJid) ) {
				System.out.println("Accepting call!");

				// okay, it matched, so accept the call and start negotiating
				StunTurnAddress sta = StunTurnAddress.getAddress( connection );
				
				jingleStreamManager = new JingleStreamManager(CreatorEnum.initiator);
				List<ContentPacketExtension> acceptedContent = jingleStreamManager.parseIncomingAndBuildMedia( jiq, ContentPacketExtension.SendersEnum.both );

				if( acceptedContent == null ) {
					System.out.println("Rejecting call!");
					// it didn't match. Reject the call.
					closeSession(Reason.INCOMPATIBLE_PARAMETERS);
					return;
				}

				iceAgent = new IceAgent(false, sta.getStunAddresses(), sta.getTurnAddresses());
				iceAgent.createStreams(jingleStreamManager.getMediaNames());

				iceAgent.addAgentStateChangeListener(this);
				iceAgent.addLocalCandidateToContents(acceptedContent);
	
				JingleIQ iq = JinglePacketFactory.createSessionAccept(myJid, peerJid, sessionId, acceptedContent);
				connection.sendPacket(iq);
				state = SessionState.NEGOTIATING_TRANSPORT;
				
				iceAgent.addRemoteCandidates( jiq );
				iceAgent.startConnectivityEstablishment();
			} else {
				System.out.println("Rejecting call!");
				// it didn't match. Reject the call.
				closeSession(Reason.DECLINE);
			}
		} catch( IOException ioe ) {
			System.out.println("An error occured. Rejecting call!");
			JingleIQ iq = JinglePacketFactory.createCancel(myJid, peerJid, sessionId);
			connection.sendPacket(iq);
			closeSession(Reason.FAILED_APPLICATION);
		} catch( IllegalArgumentException iae ) {
			System.out.println("An error occured. Rejecting call!");
			JingleIQ iq = JinglePacketFactory.createCancel(myJid, peerJid, sessionId);
			connection.sendPacket(iq);
			closeSession(Reason.FAILED_APPLICATION);
		}
	}

	private boolean acceptCallFrom(String peerJid) {
		//accept calls from the expected caller:
		//peerJid.equals(callerJid);
		
		//or
		
		//accept all calls:
		return true;
	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		Agent agent = (Agent) evt.getSource();
		System.out.println("\n\n++++++++++++++++++++++++++++\n\n");
		try {
			System.out.println(agent.getStreams().iterator().next().getCheckList());
		} catch (Exception e) {
		}
		System.out.println("New State: " + evt.getNewValue());
		for( String s : iceAgent.getStreamNames() ) {
			System.out.println("Stream          : " + s );
			System.out.println("Local Candidate : " + agent.getSelectedLocalCandidate(s));
			System.out.println("Remote Candidate: " + agent.getSelectedRemoteCandidate(s));
		}
		System.out.println("\n\n++++++++++++++++++++++++++++\n\n");
		if (agent.getState() == IceProcessingState.COMPLETED) {
			try {
				for( String s : iceAgent.getStreamNames() ) {
					System.out.println( "For Stream : " + s );
					jingleStream = jingleStreamManager.startStream(s, iceAgent);
					jingleStream.quickShow(jingleStreamManager.getDefaultAudioDevice());
				}
			} catch (IOException ioe) {
				ioe.printStackTrace(); // FIXME: deal with this.
			}
		} else if (agent.getState() == IceProcessingState.FAILED) {
			closeSession(Reason.CONNECTIVITY_ERROR);
		}
	}
}
