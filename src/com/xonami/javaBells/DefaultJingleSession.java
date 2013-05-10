package com.xonami.javaBells;

import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.packet.IQ;

import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JingleIQ;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JinglePacketFactory;

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
		closeSession();
		return false;
	}

	protected void closeSession() {
		state = SessionState.CLOSED;
		jinglePacketHandler.removeJingleSession(this);
	}

	public void ack( IQ iq ) {
		IQ resp = IQ.createResultIQ(iq);
		connection.sendPacket(resp);
	}

	public void handleContentAcept(JingleIQ jiq) {
		checkAndAck(jiq);
	}

	public void handleContentAdd(JingleIQ jiq) {
		checkAndAck(jiq);
	}

	public void handleContentModify(JingleIQ jiq) {
		checkAndAck(jiq);
	}

	public void handleContentReject(JingleIQ jiq) {
		checkAndAck(jiq);
	}

	public void handleContentRemove(JingleIQ jiq) {
		checkAndAck(jiq);
	}

	public void handleDescriptionInfo(JingleIQ jiq) {
		checkAndAck(jiq);
	}

	public void handleSecurityInfo(JingleIQ jiq) {
		checkAndAck(jiq);
	}

	public void handleSessionAccept(JingleIQ jiq) {
		checkAndAck(jiq);
	}

	public void handleSessionInfo(JingleIQ jiq) {
		checkAndAck(jiq);
	}

	/** sets the peerJid and cancels the session. */
	public void handleSessionInitiate(JingleIQ jiq) {
		ack(jiq);
		peerJid = jiq.getFrom();
		JingleIQ iq = JinglePacketFactory.createCancel(myJid, peerJid, sessionId);
		connection.sendPacket(iq);
		closeSession();
	}

	/** sets the state to closed. */
	public void handleSessionTerminate(JingleIQ jiq) {
		if( !checkAndAck(jiq) )
			return;
		closeSession();
	}

	
	public void handleTransportAccept(JingleIQ jiq) {
		checkAndAck(jiq);
	}

	public void handleTransportInfo(JingleIQ jiq) {
		checkAndAck(jiq);
	}

	public void handleTransportReject(JingleIQ jiq) {
		if( !checkAndAck(jiq) )
			return;
		closeSession();
	}

	public void handleSessionReplace(JingleIQ jiq) {
		checkAndAck(jiq);
	}

	@Override
	public String getSessionId() {
		return sessionId;
	}
}
