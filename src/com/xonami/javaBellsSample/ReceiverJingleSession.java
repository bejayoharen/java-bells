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
import org.jitsi.service.neomedia.MediaType;
import org.jivesoftware.smack.XMPPConnection;

import com.xonami.javaBells.DefaultJingleSession;
import com.xonami.javaBells.IceAgent;
import com.xonami.javaBells.JinglePacketHandler;
import com.xonami.javaBells.JingleStream;
import com.xonami.javaBells.JingleStreamManager;
import com.xonami.javaBells.StunTurnAddress;

/**
 * Handles jingle packets for the receiver.
 * In this example, we only accept sessionInitiation requests if they
 * come from the expected caller.
 * 
 * @author bjorn
 *
 */
public class ReceiverJingleSession extends DefaultJingleSession implements PropertyChangeListener {
	private final String callerJid;
	private IceAgent iceAgent;
	private JingleStreamManager jingelStreamManager;
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
			if (peerJid.equals(callerJid)) {
				System.out.println("Accepting call!");

				// okay, it matched, so accept the call and start negotiating
				
				String name = JingleStreamManager.getContentPacketName(jiq);
				
				StunTurnAddress sta = StunTurnAddress.getAddress( connection );
				
				jingelStreamManager = new JingleStreamManager(CreatorEnum.initiator);
				jingelStreamManager.addDefaultMedia( MediaType.VIDEO, "video" );
				List<ContentPacketExtension> contentList = jingelStreamManager.createContentList(ContentPacketExtension.SendersEnum.both);
				try {
					iceAgent = new IceAgent(false, name, sta.getStunAddresses(), sta.getTurnAddresses());
				} catch( IOException ioe ) {
					throw new RuntimeException( ioe );
				}
				iceAgent.addAgentStateChangeListener(this);
				iceAgent.addLocalCandidateToContents(contentList);
	
				JingleIQ iq = JinglePacketFactory.createSessionAccept(myJid, peerJid, sessionId, contentList);
				connection.sendPacket(iq);
				state = SessionState.NEGOTIATING_TRANSPORT;
				
				iceAgent.addRemoteCandidates( jiq );
				iceAgent.startConnectivityEstablishment();
			} else {
				System.out.println("Rejecting call!");
				// it didn't match. Reject the call.
				JingleIQ iq = JinglePacketFactory.createCancel(myJid, peerJid, sessionId);
				connection.sendPacket(iq);
				closeSession(Reason.DECLINE);
			}
		} catch( IOException ioe ) {
			System.out.println("An error occured. Rejecting call!");
			JingleIQ iq = JinglePacketFactory.createCancel(myJid, peerJid, sessionId);
			connection.sendPacket(iq);
			closeSession(Reason.FAILED_APPLICATION);
		}
	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		Agent agent = (Agent) evt.getSource();
		System.out.println( "\n\n++++++++++++++++++++++++++++\n\n" );
		try {
		System.out.println( agent.getStreams().iterator().next().getCheckList() );
		} catch( Exception e ) {}
		System.out.println( "New State: " + evt.getNewValue() );
		System.out.println( "Local Candidate : " + agent.getSelectedLocalCandidate(iceAgent.getStreamName()) );
		System.out.println( "Remote Candidate: " + agent.getSelectedRemoteCandidate(iceAgent.getStreamName()) );
		System.out.println( "\n\n++++++++++++++++++++++++++++\n\n" );
		if(agent.getState() == IceProcessingState.COMPLETED) { //FIXME what to do on failure?
			try {
				jingleStream = jingelStreamManager.startStream( iceAgent.getStreamName(), iceAgent );
				jingleStream.quickShow();
            } catch( IOException ioe ) {
            	ioe.printStackTrace(); //FIXME: deal with this.
            }
        } else if( agent.getState() == IceProcessingState.FAILED ) {
        	closeSession(Reason.CONNECTIVITY_ERROR);
        }
	}
}
