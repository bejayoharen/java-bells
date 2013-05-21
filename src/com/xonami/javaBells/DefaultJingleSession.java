package com.xonami.javaBells;

import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.packet.IQ;

import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JingleIQ;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JinglePacketFactory;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.Reason;

/**
 * This is a basic implementation of a JingleSession.
 * Without subclassing this, connections are rejected
 * and most other behavior is a reasonable default.
 * 
 * @author bjorn
 *
 */
public class DefaultJingleSession implements JingleSession {
	public enum SessionState {
		NEW,
		NEGOTIATING_TRANSPORT,
		OPEN,
		CLOSED,
	}
	
	protected final JinglePacketHandler jinglePacketHandler;
	protected final String myJid;
	protected final String sessionId;
	protected final XMPPConnection connection;
	protected SessionState state;
	protected String peerJid;
	
	/** creates a new DefaultJingleSession with the given info. */
	public DefaultJingleSession( JinglePacketHandler jinglePacketHandler, String sessionId, XMPPConnection connection ) {
		this.jinglePacketHandler = jinglePacketHandler;
		this.myJid = connection.getUser();
		this.sessionId = sessionId;
		this.connection = connection;
		this.state = SessionState.NEW;
	}
	
	/** checks to make sure the packet came from the expected peer and that the state is not closed.
	 * If so, it acknowledges and returns true.
	 * If not, it returns false, sends a cancel, and sets the state to closed.
	 * Do NOT call this function before you set the peerJid.
	 */
	protected boolean checkAndAck( JingleIQ jiq ) {
		if( peerJid == null )
			throw new RuntimeException("Don't call this before setting peerJid!");
		if( state == SessionState.CLOSED )
			return false;
		if( peerJid.equals(jiq.getFrom()) ) {
			ack(jiq);
			return true;
		}
		closeSession(Reason.CONNECTIVITY_ERROR);
		return false;
	}

	/** You may want to override this method to close any Jingle Streams you have open.
	 * To send a close message to the peer, include a reason. If reason is null, no message will be sent.*/
	protected void closeSession(Reason reason) {
		if( reason != null )
			connection.sendPacket(JinglePacketFactory.createSessionTerminate(myJid, peerJid, sessionId, reason, null));
		state = SessionState.CLOSED;
		jinglePacketHandler.removeJingleSession(this);
	}

	/** Simply sends an ack to the given iq. */
	public void ack( IQ iq ) {
		IQ resp = IQ.createResultIQ(iq);
		connection.sendPacket(resp);
	}

	/** Calls checkAndAck. */
	public void handleContentAcept(JingleIQ jiq) {
		checkAndAck(jiq);
	}
	/** Calls checkAndAck. */
	public void handleContentAdd(JingleIQ jiq) {
		checkAndAck(jiq);
	}
	/** Calls checkAndAck. */
	public void handleContentModify(JingleIQ jiq) {
		checkAndAck(jiq);
	}
	/** Calls checkAndAck. */
	public void handleContentReject(JingleIQ jiq) {
		checkAndAck(jiq);
	}
	/** Calls checkAndAck. */
	public void handleContentRemove(JingleIQ jiq) {
		checkAndAck(jiq);
	}
	/** Calls checkAndAck. */
	public void handleDescriptionInfo(JingleIQ jiq) {
		checkAndAck(jiq);
	}
	/** Calls checkAndAck. */
	public void handleSecurityInfo(JingleIQ jiq) {
		checkAndAck(jiq);
	}
	/** Calls checkAndAck. */
	public void handleSessionAccept(JingleIQ jiq) {
		checkAndAck(jiq);
	}
	/** Calls checkAndAck. */
	public void handleSessionInfo(JingleIQ jiq) {
		checkAndAck(jiq);
	}

	/** sets the peerJid and closes the session. Subclasses will want to
	 * override this if they plan to handle incoming sessions. */
	public void handleSessionInitiate(JingleIQ jiq) {
		ack(jiq);
		peerJid = jiq.getFrom();
		JingleIQ iq = JinglePacketFactory.createCancel(myJid, peerJid, sessionId);
		connection.sendPacket(iq);
		closeSession(Reason.DECLINE);
	}

	/** Closes the session. */
	public void handleSessionTerminate(JingleIQ jiq) {
		if( !checkAndAck(jiq) )
			return;
		closeSession(null);
	}
	/** Calls checkAndAck. */
	public void handleTransportAccept(JingleIQ jiq) {
		checkAndAck(jiq);
	}
	/** Calls checkAndAck. */
	public void handleTransportInfo(JingleIQ jiq) {
		checkAndAck(jiq);
	}
	/** Closes the session. */
	public void handleTransportReject(JingleIQ jiq) {
		if( !checkAndAck(jiq) )
			return;
		closeSession(Reason.GENERAL_ERROR);
	}
	/** Calls checkAndAck. */
	public void handleSessionReplace(JingleIQ jiq) {
		checkAndAck(jiq);
	}

	/** returns the sessionId for the current session. */
	@Override
	public String getSessionId() {
		return sessionId;
	}
}
