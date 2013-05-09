package com.xonami.javaBellsSample;

import java.io.IOException;
import java.util.List;

import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.ContentPacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JingleIQ;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JinglePacketFactory;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.ContentPacketExtension.CreatorEnum;

import org.jitsi.service.neomedia.MediaType;
import org.jivesoftware.smack.XMPPConnection;

import com.xonami.javaBells.DefaultJingleSession;
import com.xonami.javaBells.IceUtil;
import com.xonami.javaBells.JinglePacketHandler;
import com.xonami.javaBells.JingleUtil;
import com.xonami.javaBells.StunTurnAddress;

/**
 * handles jingle packets for the caller.
 * 
 * @author bjorn
 *
 */
public class CallerJingleSession extends DefaultJingleSession {
	private final IceUtil iceUtil;
	
	public CallerJingleSession(IceUtil iceUtil, JinglePacketHandler jinglePacketHandler, String peerJid, String sessionId, XMPPConnection connection) {
		super(jinglePacketHandler, sessionId, connection);
		this.iceUtil = iceUtil;
		this.peerJid = peerJid;
	}

	@Override
	public void handleSessionAccept(JingleIQ jiq) {
		//acknowledge
		if( !checkAndAck(jiq) )
			return;

		state = SessionState.NEGOTIATING_TRANSPORT;
		
		iceUtil.addRemoteCandidates( jiq );
		iceUtil.startConnectivityEstablishment();
	}
}