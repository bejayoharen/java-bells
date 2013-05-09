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
import com.xonami.javaBells.JingleUtil;
import com.xonami.javaBells.StunTurnAddress;

/**
 * handles jingle packets for the caller.
 * 
 * @author bjorn
 *
 */
public class CallerJingleSession extends DefaultJingleSession {
	public CallerJingleSession(String peerJid, String sessionId, XMPPConnection connection) {
		super(sessionId, connection);
		this.peerJid = peerJid;
	}

	@Override
	public void handleSessionAccept(JingleIQ jiq) {
		//acknowledge
		if( !checkAndAck(jiq) )
			return;
		
		IceUtil iceUtil;
		try {
			String name = JingleUtil.getContentPacketName(jiq);
			
			StunTurnAddress sta = StunTurnAddress.getAddress( connection );
			
			List<ContentPacketExtension> contentList = JingleUtil.createContentList(MediaType.VIDEO, CreatorEnum.initiator, "video", ContentPacketExtension.SendersEnum.both);
			try {
				iceUtil = new IceUtil(true, connection.getUser(), name, sta.getStunAddresses(), sta.getTurnAddresses());
			} catch( IOException ioe ) {
				throw new RuntimeException( ioe );
			}
			iceUtil.addTransportToContents(contentList,0);
	
			JingleIQ iq = JinglePacketFactory.createSessionAccept(myJid, peerJid, sessionId, contentList);
			connection.sendPacket(iq);
			state = SessionState.NEGOTIATING_TRANSPORT;
			
			iceUtil.addRemoteCandidates( jiq );
			iceUtil.startConnectivityEstablishment();
			System.out.println( iq.toXML() );
			
//			System.out.println( "sleeping..." );
//			try {
//				Thread.sleep(60000);
//			} catch (InterruptedException e) {}
//			
//			System.out.println( "Caller Exit" );
//			System.exit(0);
		} catch (IOException ioe) {
			System.out.println("An error occured. Rejecting call!");
			JingleIQ iq = JinglePacketFactory.createCancel(myJid, peerJid, sessionId);
			connection.sendPacket(iq);
			state = SessionState.CLOSED;
		}
	}
	
}
