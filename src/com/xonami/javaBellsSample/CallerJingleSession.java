package com.xonami.javaBellsSample;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.CandidatePacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.ContentPacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JingleAction;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JingleIQ;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JinglePacketFactory;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.ContentPacketExtension.CreatorEnum;

import org.ice4j.ice.Agent;
import org.ice4j.ice.Component;
import org.ice4j.ice.IceMediaStream;
import org.ice4j.ice.IceProcessingState;
import org.ice4j.ice.RemoteCandidate;
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
public class CallerJingleSession extends DefaultJingleSession implements PropertyChangeListener {
	private final IceUtil iceUtil;
	
	public CallerJingleSession(IceUtil iceUtil, JinglePacketHandler jinglePacketHandler, String peerJid, String sessionId, XMPPConnection connection) {
		super(jinglePacketHandler, sessionId, connection);
		this.iceUtil = iceUtil;
		this.peerJid = peerJid;
		
		iceUtil.getAgent().addStateChangeListener( this );
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
	
	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		Agent agent = iceUtil.getAgent();
		
		System.out.println( "-------------- Caller - Agent Property Change - -----------------" );
		System.out.println( "New State: " + evt.getNewValue() );
		System.out.println( "Local Candidate : " + agent.getSelectedLocalCandidate(iceUtil.getStreamName()) );
		System.out.println( "Remote Candidate: " + agent.getSelectedRemoteCandidate(iceUtil.getStreamName()) );
		System.out.println( "-------------- Caller - Agent Property Change - -----------------" );
		
        if(agent.getState() == IceProcessingState.COMPLETED) //FIXME what to do on failure?
        {
            List<IceMediaStream> streams = agent.getStreams();

            //////////
            for(IceMediaStream stream : streams)
            {
                String streamName = stream.getName();
                System.out.println( "Pairs selected for stream: " + streamName);
                List<Component> components = stream.getComponents();

                for(Component cmp : components)
                {
                    String cmpName = cmp.getName();
                    System.out.println(cmpName + ": " + cmp.getSelectedPair());
                }
            }

            System.out.println("Printing the completed check lists:");
            for(IceMediaStream stream : streams)
            {
                String streamName = stream.getName();
                System.out.println("Check list for  stream: " + streamName);
                //uncomment for a more verbose output
                System.out.println(stream.getCheckList());
            }
            ////////////
            
            ContentPacketExtension cp = iceUtil.getSelectedRemoteCandidateContent();
            
            ArrayList<ContentPacketExtension> contentList = new ArrayList<ContentPacketExtension>(1);
            contentList.add( cp );
            
            JingleIQ transInfo = JinglePacketFactory.createSessionAccept(myJid, peerJid, sessionId, contentList);
            transInfo.setAction(JingleAction.TRANSPORT_INFO);
            
            connection.sendPacket(transInfo);
        }
	}
}

