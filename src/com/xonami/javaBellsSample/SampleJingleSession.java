/**
 * 
 */
package com.xonami.javaBellsSample;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.util.List;

import org.ice4j.ice.Agent;
import org.ice4j.ice.IceProcessingState;
import org.jivesoftware.smack.XMPPConnection;

import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.ContentPacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JingleIQ;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JinglePacketFactory;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.Reason;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.ContentPacketExtension.CreatorEnum;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.ContentPacketExtension.SendersEnum;

import com.xonami.javaBells.DefaultJingleSession;
import com.xonami.javaBells.IceAgent;
import com.xonami.javaBells.JinglePacketHandler;
import com.xonami.javaBells.JingleStream;
import com.xonami.javaBells.JingleStreamManager;
import com.xonami.javaBells.StunTurnAddress;

/**
 * @author bjorn
 *
 */
public class SampleJingleSession extends DefaultJingleSession implements PropertyChangeListener {
	public enum CallMode {
		CALL,
		ANSWER,
		DONOTANSWER,
	}
	private IceAgent iceAgent;
	private JingleStreamManager jingleStreamManager;
	private JingleStream jingleStream;
	private final CallMode callMode;
	private boolean active = false;

	/** constructor for incoming calls. */
	public SampleJingleSession(JinglePacketHandler jinglePacketHandler, JingleStreamManager jingleStreamManager, IceAgent iceAgent, String sessionId, JingleIQ jiq, XMPPConnection connection, CallMode callMode) {
		super(jinglePacketHandler,sessionId,connection);
		this.callMode = callMode;
		this.jingleStreamManager = jingleStreamManager;
		this.iceAgent = iceAgent;
		System.out.println( "CREATING JINGLE SESSION " + callMode ) ;
		switch( callMode ) {
		case ANSWER:
		case DONOTANSWER:
			if( jingleStreamManager != null || iceAgent != null )
				throw new RuntimeException();
			break;
		case CALL:
			if( jingleStreamManager == null || iceAgent == null )
				throw new RuntimeException();
			iceAgent.addAgentStateChangeListener(this);
			propertyChange( new PropertyChangeEvent(iceAgent.getAgent(), null, null, null) );
			break;
		}
	}

	@Override
	protected void closeSession(Reason reason) {
		System.out.println( "Jingle Session closing: :::::::" + reason + "::::::::" );
		active = false;
		super.closeSession(reason);
		if( jingleStream != null )
			jingleStream.shutdown();
		if( iceAgent != null )
			iceAgent.freeAgent();
	}

	@Override
	public void handleSessionInitiate(JingleIQ jiq) {
		if( state == SessionState.CLOSED )
			return;
		// acknowledge:
		ack(jiq);
		try {
			switch( callMode ) {
			case CALL:
				System.out.println("Rejecting call (wrong mode).");
				closeSession(Reason.CONNECTIVITY_ERROR);
				break;
			case ANSWER:
				System.out.println("Accepting call!");
				
				// set the peerJid
				peerJid = jiq.getFrom();

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

				iceAgent = new IceAgent(false, sta);
				iceAgent.createStreams(jingleStreamManager.getMediaNames());

				iceAgent.addAgentStateChangeListener(this);
				iceAgent.addLocalCandidateToContents(acceptedContent);

				JingleIQ iq = JinglePacketFactory.createSessionAccept(myJid, peerJid, sessionId, acceptedContent);
				connection.sendPacket(iq);
				state = SessionState.NEGOTIATING_TRANSPORT;
				
				iceAgent.addRemoteCandidates( jiq );
				iceAgent.startConnectivityEstablishment();
				active = true;
				break;
			case DONOTANSWER:
				System.out.println("Rejecting call (busy).");
				closeSession(Reason.BUSY);
				break;
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
		} catch( Exception e ) {
			System.out.println("An error occured. Rejecting call!");
			JingleIQ iq = JinglePacketFactory.createCancel(myJid, peerJid, sessionId);
			connection.sendPacket(iq);
			closeSession(Reason.FAILED_APPLICATION);
		}
	}
	
	@Override
	public void handleSessionInfo(JingleIQ jiq) {
		if( !check(jiq) )
			return;
		if( jiq.getSessionInfo() != null && jiq.getSessionInfo().getElementName().equals("ringing")) {
			ack( jiq );
			return;
		}
		unsupportedInfo( jiq );
	}
	
	@Override
	public void handleSessionAccept(JingleIQ jiq) {
		switch( callMode ) {
		case CALL:
//			System.out.println( "====:: Got session accept from " + jiq + " :: " + peerJid );
			
			if( peerJid == null )
				peerJid = jiq.getFrom();
			
			//acknowledge
			if( !checkAndAck(jiq) )
				return;
			
//			System.out.println( "====:: Processing accept" );

			state = SessionState.NEGOTIATING_TRANSPORT;
			
			try {
				if( null == jingleStreamManager.parseIncomingAndBuildMedia( jiq, SendersEnum.both ) )
					throw new IOException( "No incoming streams detected." );
//				System.out.println( "====:: Processing accept a" );
				iceAgent.addRemoteCandidates( jiq );
				if( iceAgent.hasCandidatesForAllStreams() ) {
					iceAgent.startConnectivityEstablishment();
//					System.out.println( "====:: Processing accept b" );
				}
//				System.out.println( "====:: Processing accept c" );
				active = true;
			} catch( IOException ioe ) {
				ioe.printStackTrace();
				closeSession(Reason.FAILED_APPLICATION);
			} catch( Exception e ) {
				e.printStackTrace();
				closeSession(Reason.FAILED_APPLICATION);
			}
			break;
		case ANSWER:
			System.out.println("Rejecting call (wrong mode).");
			closeSession(Reason.CONNECTIVITY_ERROR);
			break;
		case DONOTANSWER:
			System.out.println("Rejecting call (busy).");
			closeSession(Reason.BUSY);
			break;
		}
	}
	
	@Override
	public void handleTransportInfo(JingleIQ jiq) {
		switch( callMode ) {
		case CALL:
//			System.out.println( "====:: Got transport-info from " + jiq + " :: " + peerJid );
			
			if( peerJid == null )
				peerJid = jiq.getFrom();
			
			//acknowledge
			if( !checkAndAck(jiq) )
				return;
			
//			System.out.println( "====:: Processing transport-info..." );

			state = SessionState.NEGOTIATING_TRANSPORT;
			
			try {
//				if( null == jingleStreamManager.parseIncomingAndBuildMedia( jiq, SendersEnum.both ) )
//					throw new IOException( "No incoming streams detected." );
//				jingleStreamManager.parseIncomingAndBuildMedia( jiq, SendersEnum.both );
//				System.out.println( "====:: Processing transport-info a" );
				iceAgent.addRemoteCandidates( jiq );
				if( iceAgent.hasCandidatesForAllStreams() ) {
					iceAgent.startConnectivityEstablishment();
//					System.out.println( "====:: Processing transport-info b" );
				}
//				System.out.println( "====:: Processing transport-info c" );
				active = true;
//			} catch( IOException ioe ) {
//				ioe.printStackTrace();
//				closeSession(Reason.FAILED_APPLICATION);
			} catch( Exception e ) {
				e.printStackTrace();
				closeSession(Reason.FAILED_APPLICATION);
			}
			break;
		case ANSWER:
			System.out.println("Rejecting call (wrong mode).");
			closeSession(Reason.CONNECTIVITY_ERROR);
			break;
		case DONOTANSWER:
			System.out.println("Rejecting call (busy).");
			closeSession(Reason.BUSY);
			break;
		}
	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		Agent agent = (Agent) evt.getSource();
		System.out.println("\n\n++++++++++++++++++++++++++++\n\n");
		try {
			System.out.println(agent.getStreams().iterator().next().getCheckList());
		} catch (Exception e) {
		}
		System.out.println("New State: " + evt.getNewValue() + " : " + iceAgent.getState() );
		for( String s : iceAgent.getStreamNames() ) {
			System.out.println("Stream          : " + s );
			System.out.println("Local Candidate : " + agent.getSelectedLocalCandidate(s));
			System.out.println("Remote Candidate: " + agent.getSelectedRemoteCandidate(s));
		}
		System.out.println("\n\n++++++++++++++++++++++++++++\n\n");
		if (agent.getState() == IceProcessingState.COMPLETED) {
			try {
				// when connecting to jitsi, a failed is sometimes sent after completed
				iceAgent.removeAgentStateChangeListener(this);
				for( String s : iceAgent.getStreamNames() ) {
					System.out.println( "For Stream : " + s );
					jingleStream = jingleStreamManager.startStream(s, iceAgent);
					jingleStream.quickShow(jingleStreamManager.getDefaultAudioDevice());
				}
			} catch (IOException ioe) {
				System.out.println( "IOException." );
				ioe.printStackTrace(); // FIXME: deal with this.
				closeSession(Reason.CONNECTIVITY_ERROR);
			} catch( Exception e ) {
				System.out.println( "Exception." );
				e.printStackTrace(); // FIXME: deal with this.
				closeSession(Reason.CONNECTIVITY_ERROR);
			}
		} else if (agent.getState() == IceProcessingState.FAILED) {
			closeSession(Reason.CONNECTIVITY_ERROR);
		}
	}

	public boolean isActive() {
		return active;
	}
}
