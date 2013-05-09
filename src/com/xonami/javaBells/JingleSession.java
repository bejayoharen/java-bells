package com.xonami.javaBells;

import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JingleIQ;


/**
 * Interface that represents a Jingle Session. Subclasses, such as DefaultJingleSession,
 * Receive messages when an incoming IQ is received.
 * 
 * @author bjorn
 *
 */
public interface JingleSession {
	/** Called when the session receives a content-accept jingle iq */
	public void handleContentAcept(JingleIQ jiq) ;
	/** Called when the session receives a content-add jingle iq */
	public void handleContentAdd(JingleIQ jiq) ;
	/** Called when the session receives a content-modify jingle iq */
	public void handleContentModify(JingleIQ jiq) ;
	/** Called when the session receives a content-reject jingle iq */
	public void handleContentReject(JingleIQ jiq) ;
	/** Called when the session receives a content-remove jingle iq */
	public void handleContentRemove(JingleIQ jiq) ;
	/** Called when the session receives a description-info jingle iq */
	public void handleDescriptionInfo(JingleIQ jiq) ;
	/** Called when the session receives a security-info jingle iq */
	public void handleSecurityInfo(JingleIQ jiq) ;
	/** Called when the session receives a session-accept jingle iq */
	public void handleSessionAccept(JingleIQ jiq) ;
	/** Called when the session receives a session-info jingle iq */
	public void handleSessionInfo(JingleIQ jiq) ;
	/** Called when the session receives a session-initiate jingle iq */
	public void handleSessionInitiate(JingleIQ jiq) ;
	/** Called when the session receives a session-terminate jingle iq */
	public void handleSessionTerminate(JingleIQ jiq) ;
	/** Called when the session receives a transport-accept jingle iq */
	public void handleTransportAccept(JingleIQ jiq) ;
	/** Called when the session receives a transport-info jingle iq */
	public void handleTransportInfo(JingleIQ jiq) ;
	/** Called when the session receives a transport-reject jingle iq */
	public void handleTransportReject(JingleIQ jiq) ;
	/** Called when the session receives a session-replace jingle iq */
	public void handleSessionReplace(JingleIQ jiq) ;
	/** returns the ID of this session. */
	public String getSessionId();
}
