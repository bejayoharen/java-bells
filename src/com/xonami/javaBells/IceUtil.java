package com.xonami.javaBells;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.List;

import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.CandidatePacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.CandidateType;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.ContentPacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.IceUdpTransportPacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JingleIQ;

import org.ice4j.Transport;
import org.ice4j.TransportAddress;
import org.ice4j.ice.Agent;
import org.ice4j.ice.Candidate;
import org.ice4j.ice.Component;
import org.ice4j.ice.IceMediaStream;
import org.ice4j.ice.NominationStrategy;
import org.ice4j.ice.RemoteCandidate;
import org.ice4j.ice.harvest.StunCandidateHarvester;
import org.ice4j.ice.harvest.TurnCandidateHarvester;
import org.ice4j.security.LongTermCredential;


/**
 * Utility class for gathering and processing ice candidates. This class also converts
 * XMPP/jingle formatted data to and from ice format.
 * 
 * FIXME: this class is incomplete
 * 
 * @author bjorn
 *
 */
public class IceUtil {
	static SecureRandom random ;
	static {
		try {
			// Create a secure random number generator
			random = SecureRandom.getInstance("SHA1PRNG");
		} catch(NoSuchAlgorithmException e) {
			throw new RuntimeException();
		}
	}
	
	private final Agent agent; //FIXME need to free this when done
	private final boolean controling;
	
	public IceUtil( final boolean controling, String username, final String streamname, TransportAddress stunAddresses[], TransportAddress turnAddresses[] ) throws IOException {
		this.agent = new Agent();
		this.controling = controling;
		agent.setControlling(controling);
		{
			agent.setNominationStrategy(NominationStrategy.NOMINATE_HIGHEST_PRIO);
			agent.addStateChangeListener(new PropertyChangeListener() {

				@Override
				public void propertyChange(PropertyChangeEvent pce) {
					// FIXME: this is where we find out about agent processing status, I believe.
					System.out.println( "-------------- "  + controling + " - Agent Property Change - -----------------" );
					System.out.println( "New State: " + pce.getNewValue() );
					System.out.println( "Local Candidate : " + agent.getSelectedLocalCandidate(streamname) );
					System.out.println( "Remote Candidate: " + agent.getSelectedRemoteCandidate(streamname) );
					System.out.println( "-------------- "  + controling + " - Agent Property Change - -----------------" );
				}
				
			});
		}
		
		//stun and turn
		if( stunAddresses != null )
			for( TransportAddress ta : stunAddresses )
				agent.addCandidateHarvester(new StunCandidateHarvester(ta,username) );
		
		LongTermCredential ltr = new LongTermCredential(username, generateNonce(15));
		if( turnAddresses != null )
			for( TransportAddress ta : turnAddresses )
				agent.addCandidateHarvester(new TurnCandidateHarvester(ta,ltr) );
		// create streams:
		try {
			createStream( 9090, streamname ); //FIXME check for open port and suggest that, no?
		} catch( BindException be ) {
			throw new IOException(be);
		}
	}
	
	public void addRemoteCandidates(JingleIQ jiq) {
		
		for( ContentPacketExtension contentpe : jiq.getContentList() ) {
			String name = contentpe.getName();
			IceMediaStream ims = agent.getStream( name );
			if( ims != null ) {
				for( IceUdpTransportPacketExtension tpe : contentpe.getChildExtensionsOfType(IceUdpTransportPacketExtension.class) ) {
					ims.setRemotePassword(tpe.getPassword());
					ims.setRemoteUfrag(tpe.getUfrag());
					for( CandidatePacketExtension cpe : tpe.getCandidateList() ) {
						InetAddress ia;
						try {
							ia = InetAddress.getByName(cpe.getIP());
						} catch (UnknownHostException uhe) {
							continue;
						}
						for( Component component : ims.getComponents() ) {
							component.addRemoteCandidate( new RemoteCandidate(
									new TransportAddress(ia, cpe.getPort(), Transport.parse(cpe.getProtocol().toLowerCase())),
									component,
									convertType(cpe.getType()),
									Integer.toString(cpe.getFoundation()),
									cpe.getPriority(),
									null) //FIXME: related candidate
							);
						}
					}
				}
			}
		}
	}
	
	public void startConnectivityEstablishment() {
		agent.startConnectivityEstablishment();
		for( IceMediaStream ims : agent.getStreams() ) {
			System.out.println( "+++++++" + controling + "+ Checklist ++++++++++++++" );//FIXME
			System.out.println( ims.getCheckList() );
			System.out.println( "+++++++" + controling + "+ Checklist ++++++++++++++" );//FIXME
		}
	}
	
	public IceUdpTransportPacketExtension getPacketExtension(int generation) {
		IceUdpTransportPacketExtension transport = new IceUdpTransportPacketExtension();
		transport.setPassword( agent.getLocalPassword() );
		transport.setUfrag( agent.getLocalUfrag() );

		for( IceMediaStream ims : agent.getStreams() ) {
			for( Component c : ims.getComponents() ) {
				for( Candidate<?> can : c.getLocalCandidates() ) {
					CandidatePacketExtension candidate = new CandidatePacketExtension();
					candidate.setComponent(c.getComponentID());
					candidate.setFoundation(Integer.parseInt(can.getFoundation()));
					candidate.setGeneration(generation);
					candidate.setID(generateNonce(10));//FIXME: how do we establish the ID?
					candidate.setNetwork(0); //FIXME: we need to identify the network card properly.
					TransportAddress ta = can.getTransportAddress();
					candidate.setIP( ta.getHostAddress() );
					candidate.setPort( ta.getPort() );
					candidate.setPriority(can.getPriority());
					candidate.setProtocol(can.getTransport().name().toLowerCase());
					if( can.getRelatedAddress() != null ) {
						candidate.setRelAddr(can.getRelatedAddress().getHostAddress());
						candidate.setRelPort(can.getRelatedAddress().getPort());
					}
					candidate.setType(convertType(can.getType()));
					
					transport.addCandidate(candidate);
				}
			}
		}
		return transport;
	}
	public void addTransportToContents(List<ContentPacketExtension> contentList, int generation) {
		IceUdpTransportPacketExtension ext = getPacketExtension(generation);
		for( ContentPacketExtension cpe : contentList ) {
			cpe.addChildExtension(ext);
		}
	}
	
	private CandidateType convertType(org.ice4j.ice.CandidateType type) {
		String ts = type.toString();
		return CandidateType.valueOf(ts);
	}
	private org.ice4j.ice.CandidateType convertType(CandidateType type) {
		String ts = type.toString();
		return org.ice4j.ice.CandidateType.parse(ts);
	}

	private void createStream( int rtpPort, String name ) throws BindException, IllegalArgumentException, IOException {
		IceMediaStream stream = agent.createMediaStream(name);
		agent.createComponent(stream, Transport.UDP, rtpPort, rtpPort, rtpPort+100);
		agent.createComponent(stream, Transport.UDP, rtpPort+1, rtpPort+1, rtpPort+101);
	}
	
	
	
	public static String generateNonce(int length) {
		StringBuilder s = new StringBuilder( length );
		for( int i=0; i<length; ++i ) {
			int r = random.nextInt( 26 + 26 + 10 );
			char c;
			if( r >= 26 + 26 ) {
				c = (char) ( '0' + (r-26-26) );
			} else if( r >= 26 ) {
				c = (char) ( 'A' + (r-26) );
			} else {
				c = (char) ( 'a' + r );
			}
			s.append( c );
		}
		return s.toString();
	}
}
