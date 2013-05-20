package com.xonami.javaBells;

import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.List;

import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.CandidatePacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.CandidateType;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.ContentPacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.ContentPacketExtension.CreatorEnum;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.IceUdpTransportPacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JingleIQ;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.RemoteCandidatePacketExtension;

import org.ice4j.Transport;
import org.ice4j.TransportAddress;
import org.ice4j.ice.Agent;
import org.ice4j.ice.Candidate;
import org.ice4j.ice.Component;
import org.ice4j.ice.IceMediaStream;
import org.ice4j.ice.IceProcessingState;
import org.ice4j.ice.NominationStrategy;
import org.ice4j.ice.RemoteCandidate;
import org.ice4j.ice.harvest.StunCandidateHarvester;
import org.ice4j.ice.harvest.TurnCandidateHarvester;


/**
 * IceAgent is essentially a wrapper for org.ice4j.ice.Agent with some utilities
 * to translate jingle iq data to and from that class.
 * 
 * @author bjorn
 *
 */
public class IceAgent {
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
	private final String streamname; //FIXME: this needs to support multiple streams
	
	/** creates an ice agent with the given parameters. */
	public IceAgent( final boolean controling, final String streamname, TransportAddress stunAddresses[], TransportAddress turnAddresses[] ) throws IOException {
		this.agent = new Agent();
		this.controling = controling;
		this.streamname = streamname;
		agent.setControlling(controling);
		agent.setNominationStrategy(NominationStrategy.NOMINATE_HIGHEST_PRIO);
		
		//stun and turn
		if( stunAddresses != null )
			for( TransportAddress ta : stunAddresses )
				agent.addCandidateHarvester(new StunCandidateHarvester(ta) );
		
//		LongTermCredential ltr = new LongTermCredential(generateNonce(5), generateNonce(15));
		if( turnAddresses != null )
			for( TransportAddress ta : turnAddresses )
				agent.addCandidateHarvester(new TurnCandidateHarvester(ta) );
		
		// create streams:
		try {
			//FIXME: create one audio and one video stream.
			createStream( 9090, streamname ); //FIXME check for open port and suggest that, no? why 9090?
		} catch( BindException be ) {
			throw new IOException(be);
		}
	}
	/** returns the underlying agent object. */
	Agent getAgent() {
		return agent;
	}
	/** returns the current processing state. */
	public IceProcessingState getState() {
		return agent.getState();
	}
	/** listens for changes in processing state */
	public void addAgentStateChangeListener( PropertyChangeListener pcl ) {
		agent.addStateChangeListener(pcl);
	}
	//FIXME: multiple streams need to be supported.
	public String getStreamName() {
		return streamname;
	}
	/** takes the remote candidates passed by the JingleIQ and incorporates that information into this ice agent. */
	public void addRemoteCandidates(JingleIQ jiq) {
		try {
			for (ContentPacketExtension contentpe : jiq.getContentList()) {
				String name = contentpe.getName();
				IceMediaStream ims = agent.getStream(name);
				if (ims != null) {
					// System.out.println( ims + " : " + name );
					for (IceUdpTransportPacketExtension tpe : contentpe.getChildExtensionsOfType(IceUdpTransportPacketExtension.class)) {
						// System.out.println( "\t" + tpe );
						if (tpe.getPassword() != null)
							ims.setRemotePassword(tpe.getPassword());
						if (tpe.getUfrag() != null)
							ims.setRemoteUfrag(tpe.getUfrag());

						List<CandidatePacketExtension> candidates = tpe.getChildExtensionsOfType(CandidatePacketExtension.class);
						if (candidates == null || candidates.size() == 0)
							continue;
						// Sorts the remote candidates (host < reflexive <
						// relayed) in
						// order to create first host, then reflexive, the
						// relayed
						// candidates, to be able to set the relative-candidate
						// matching the rel-addr/rel-port attribute.
						Collections.sort(candidates);

						for (CandidatePacketExtension cpe : candidates) {
							if (cpe.getGeneration() != agent.getGeneration())
								continue;
							// System.out.println( "\t\t"+cpe );
							InetAddress ia;
							try {
								ia = InetAddress.getByName(cpe.getIP());
							} catch (UnknownHostException uhe) {
								continue;
							}

							TransportAddress relatedAddr = null;
							if (cpe.getRelAddr() != null && cpe.getRelPort() != -1) {
								relatedAddr = new TransportAddress(cpe.getRelAddr(), cpe.getRelPort(), Transport.parse(cpe.getProtocol().toLowerCase()));
							}

							Component component = ims.getComponent(cpe.getComponent());
							if (component != null) {
								// we should always be able to find this b/c of the sorting we did.
								RemoteCandidate relatedCandidate = component.findRemoteCandidate(relatedAddr);
								// System.out.println( "\t\t\t" +
								// component.getComponentID() );
								component.addRemoteCandidate(new RemoteCandidate(new TransportAddress(ia, cpe.getPort(), Transport.parse(cpe.getProtocol().toLowerCase())),
										component, convertType(cpe.getType()), Integer.toString(cpe.getFoundation()), cpe.getPriority(), relatedCandidate));
							}
						}
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(0);
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
	
	public ContentPacketExtension getSelectedRemoteCandidateContent() {
		ContentPacketExtension cpe = new ContentPacketExtension(CreatorEnum.initiator, getStreamName());
		IceUdpTransportPacketExtension transport = new IceUdpTransportPacketExtension();
		transport.setPassword( agent.getLocalPassword() );
		transport.setUfrag( agent.getLocalUfrag() );
		
        RemoteCandidate rc = agent.getSelectedRemoteCandidate(getStreamName());
        RemoteCandidatePacketExtension rcp = new RemoteCandidatePacketExtension();
        rcp.setComponent(rc.getParentComponent().getComponentID());
        rcp.setFoundation(Integer.parseInt(rc.getFoundation()));
        rcp.setGeneration(agent.getGeneration());
        rcp.setIP(rc.getTransportAddress().getHostAddress() );
        rcp.setPort(rc.getTransportAddress().getPort() );
        rcp.setProtocol(rc.getTransport().name().toLowerCase());
        rcp.setType(convertType(rc.getType()));
        
        transport.addCandidate(rcp);
        cpe.addChildExtension(transport);

		return cpe;
	}
	
//	/** returns the remote transport address parsed from the given jiq or null if there was a parsing problem. */
//	public NameAndTransportAddress getTransportAddressFromRemoteCandidate(JingleIQ jiq) {
//		ContentPacketExtension cpe = jiq.getContentForType(IceUdpTransportPacketExtension.class);
//		if( cpe == null )
//			return null;
//		String name = cpe.getName();
//		if( name == null )
//			return null;
//		IceUdpTransportPacketExtension transport = null;
//		for( PacketExtension pe : cpe.getChildExtensions() ) {
//			if( pe instanceof IceUdpTransportPacketExtension ) {
//				transport = (IceUdpTransportPacketExtension) pe;
//				break;
//			}
//		}
//		if( transport == null )
//			return null;
//		RemoteCandidatePacketExtension rcp = transport.getRemoteCandidate();
//		if( rcp == null )
//			return null;
//		if( rcp.getIP() == null || rcp.getProtocol() == null )
//			return null;
//		try {
//			TransportAddress ta = new TransportAddress( rcp.getIP(), rcp.getPort(), Transport.parse(rcp.getProtocol()) );
//			return new NameAndTransportAddress( name, ta );
//		} catch( IllegalArgumentException iae ) {
//			return null;
//		}
//	}
//	
//	public CandidatePair getCandidatePairFromRemoteCandidate(JingleIQ jiq) {
//		ContentPacketExtension cpe = jiq.getContentForType(IceUdpTransportPacketExtension.class);
//		if( cpe == null )
//			return null;
//		String name = cpe.getName();
//		if( name == null )
//			return null;
//		IceUdpTransportPacketExtension transport = null;
//		for( PacketExtension pe : cpe.getChildExtensions() ) {
//			if( pe instanceof IceUdpTransportPacketExtension ) {
//				transport = (IceUdpTransportPacketExtension) pe;
//				break;
//			}
//		}
//		if( transport == null )
//			return null;
//		RemoteCandidatePacketExtension rcp = transport.getRemoteCandidate();
//		if( rcp == null )
//			return null;
//		if( rcp.getIP() == null || rcp.getProtocol() == null )
//			return null;
//		
//		IceMediaStream ims = agent.getStream(name);
//		if( ims == null )
//			return null;
//		System.out.println( name + " : " + agent.getLocalUfrag() + " : " + transport.getUfrag());
//		CandidatePair cp = ims.findCandidatePair(agent.getLocalUfrag(), transport.getUfrag());
//		System.out.println( ims.getCheckList() );
//		return cp;
//	}
	
	public void addLocalCandidateToContents(List<ContentPacketExtension> contentList) {
		IceUdpTransportPacketExtension ext = getLocalCandidatePacketExtension();
		for( ContentPacketExtension cpe : contentList ) {
			cpe.addChildExtension(ext);
		}
	}
	public IceUdpTransportPacketExtension getLocalCandidatePacketExtension() {
		IceUdpTransportPacketExtension transport = new IceUdpTransportPacketExtension();
		transport.setPassword( agent.getLocalPassword() );
		transport.setUfrag( agent.getLocalUfrag() );

		try {
		for( IceMediaStream ims : agent.getStreams() ) {
			for( Component c : ims.getComponents() ) {
				for( Candidate<?> can : c.getLocalCandidates() ) {
					CandidatePacketExtension candidate = new CandidatePacketExtension();
					candidate.setComponent(c.getComponentID());
					candidate.setFoundation(Integer.parseInt(can.getFoundation()));
					candidate.setGeneration(agent.getGeneration());
					candidate.setID(String.valueOf(c.getComponentID()));
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
		} catch( Exception e ) {
			e.printStackTrace();
			System.exit(0);
		}
		return transport;
	}
	
	private CandidateType convertType(org.ice4j.ice.CandidateType type) {
		String ts = type.toString();
		return CandidateType.valueOf(ts);
	}
	private org.ice4j.ice.CandidateType convertType(CandidateType type) {
		String ts = type.toString();
		return org.ice4j.ice.CandidateType.parse(ts);
	}

	public void createStream( int rtpPort, String name ) throws BindException, IllegalArgumentException, IOException {
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
