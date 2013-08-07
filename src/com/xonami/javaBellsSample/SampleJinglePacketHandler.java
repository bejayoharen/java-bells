/**
 * 
 */
package com.xonami.javaBellsSample;

import java.io.IOException;
import java.util.List;

import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.ContentPacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JingleIQ;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JinglePacketFactory;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.ContentPacketExtension.CreatorEnum;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.ContentPacketExtension.SendersEnum;

import org.jitsi.service.neomedia.MediaType;
import org.jivesoftware.smack.XMPPConnection;

import com.xonami.javaBells.IceAgent;
import com.xonami.javaBells.JinglePacketHandler;
import com.xonami.javaBells.JingleSession;
import com.xonami.javaBells.JingleStreamManager;
import com.xonami.javaBells.StunTurnAddress;

/**
 * @author bjorn
 *
 */
public class SampleJinglePacketHandler extends JinglePacketHandler {
	private SampleJingleSession currentJingleSession = null;
	private JingleStreamManager jingleStreamManager = null;
	private IceAgent iceAgent = null;
	
	public SampleJinglePacketHandler(XMPPConnection connection) {
		super(connection);
	}
	
	public void initiateOutgoingCall(final String targetJid) throws IOException {
		// derive stun and turn server addresses from the connection:
		StunTurnAddress sta = StunTurnAddress.getAddress( connection );
		// create an ice agent using the stun/turn address. We will need this to figure out
		// how to connect our clients:
		iceAgent = new IceAgent(true, sta);
		// setup our jingle stream manager using the default audio and video devices:
		jingleStreamManager = new JingleStreamManager(CreatorEnum.initiator);
		jingleStreamManager.addDefaultMedia(MediaType.VIDEO, "video");
		jingleStreamManager.addDefaultMedia(MediaType.AUDIO, "audio");
		// create ice streams that correspond to the jingle streams that we want
		iceAgent.createStreams(jingleStreamManager.getMediaNames());

		List<ContentPacketExtension> contentList = jingleStreamManager.createContentList(SendersEnum.both);
		iceAgent.addLocalCandidateToContents(contentList);
		
        JingleIQ sessionInitIQ = JinglePacketFactory.createSessionInitiate(
        		connection.getUser(),
        		targetJid,
        		JingleIQ.generateSID(),
        		contentList );
        
        connection.sendPacket(sessionInitIQ);
	}
	
	@Override
	public JingleSession removeJingleSession( JingleSession js ) {
		if( js == currentJingleSession ) {
			currentJingleSession = null;
			jingleStreamManager = null;
			iceAgent = null;
		}
		JingleSession ret = super.removeJingleSession(js);
		return ret;
	}
	
	@Override
	public JingleSession createJingleSession( String sid, JingleIQ jiq ) {
		if( currentJingleSession != null && currentJingleSession.isActive() )
			return new SampleJingleSession( this, null, null, sid, jiq, connection, SampleJingleSession.CallMode.DONOTANSWER );
		else if( iceAgent != null && jingleStreamManager != null )
			return currentJingleSession = new SampleJingleSession( this, jingleStreamManager, iceAgent, sid, jiq, connection, SampleJingleSession.CallMode.CALL );
		else
			return currentJingleSession = new SampleJingleSession( this, null, null, sid, jiq, connection, SampleJingleSession.CallMode.ANSWER );
	}
}
