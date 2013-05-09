package com.xonami.javaBells;

import java.util.HashMap;

import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JingleIQ;

import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.packet.Packet;

/**
 * Processes incoming jingle packets on a given connection.
 * Creates new jingleSessions as needed and passes the JingleIQ to them
 * based on their action.
 * 
 * @author bjorn
 *
 */
public class JinglePacketHandler implements PacketListener, PacketFilter {
	private final HashMap<String,JingleSession> jingleSessions = new HashMap<String,JingleSession>();
	protected final XMPPConnection connection;
	
	public JinglePacketHandler( XMPPConnection connection ) {
		this.connection = connection;
		connection.addPacketListener( this, this );
	}
	
	@Override
	public void processPacket(Packet packet) {
		JingleIQ jiq = (JingleIQ) packet;
		
		String sid = jiq.getSID();
		JingleSession js = jingleSessions.get(sid);
		if( js == null ) {
			js = createJingleSession( sid, jiq );
			jingleSessions.put( sid, js );
		}
		switch( jiq.getAction() ) {
		case CONTENT_ACCEPT:
			js.handleContentAcept( jiq );
			break;
		case CONTENT_ADD:
			js.handleContentAdd( jiq );
			break;
		case CONTENT_MODIFY:
			js.handleContentModify( jiq );
			break;
		case CONTENT_REJECT:
			js.handleContentReject( jiq );
			break;
		case CONTENT_REMOVE:
			js.handleContentRemove( jiq );
			break;
		case DESCRIPTION_INFO:
			js.handleDescriptionInfo( jiq );
			break;
		case SECURITY_INFO:
			js.handleSecurityInfo( jiq );
			break;
		case SESSION_ACCEPT:
			js.handleSessionAccept( jiq );
			break;
		case SESSION_INFO:
			js.handleSessionInfo( jiq );
			break;
		case SESSION_INITIATE:
			js.handleSessionInitiate( jiq );
			break;
		case SESSION_TERMINATE:
			js.handleSessionTerminate( jiq );
			break;
		case TRANSPORT_ACCEPT:
			js.handleTransportAccept( jiq );
			break;
		case TRANSPORT_INFO:
			js.handleTransportInfo( jiq );
			break;
		case TRANSPORT_REJECT:
			js.handleTransportReject( jiq );
			break;
		case TRANSPORT_REPLACE:
			js.handleSessionReplace( jiq );
			break;
		}
	}
	
	public JingleSession removeJingleSession( JingleSession session ) {
		return jingleSessions.remove( session.getSessionId() );
	}
	
	/**
	 * Override this to create JingleSessions the way you want. If you do not
	 * Override this, a DefaultJingleSession Object will be returned.
	 * 
	 * @param sid
	 * @param jiq
	 */
	public JingleSession createJingleSession( String sid, JingleIQ jiq ) {
		return new DefaultJingleSession(this, sid, connection);
	}

	/**
	 * Only handle Jingle packets.
	 */
	@Override
	public boolean accept(Packet packet) {
		return packet.getClass() == JingleIQ.class;
	}
}
