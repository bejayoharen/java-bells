package com.xonami.javaBells;

import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Collection;
import java.util.Collections;
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
import org.ice4j.ice.IceProcessingState;
import org.ice4j.ice.NominationStrategy;
import org.ice4j.ice.RemoteCandidate;
import org.ice4j.ice.harvest.StunCandidateHarvester;
import org.ice4j.ice.harvest.TurnCandidateHarvester;
import org.ice4j.security.LongTermCredential;
//import org.ice4j.security.LongTermCredential;


/**
 * IceAgent is essentially a wrapper for org.ice4j.ice.Agent with some utilities
 * to translate jingle iq data to and from that class.
 * 
 * Note that you must call freeAgent when you are done to ensure proper garbage collection.
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
	
	private int candidateId = 0;
	
	static final int MIN_STREAM_PORT = 5000;
	static final int MAX_STREAM_PORT = 9000;
	static volatile int streamPort = (int) ( random.nextFloat() * ( MAX_STREAM_PORT - MIN_STREAM_PORT ) + MIN_STREAM_PORT );

	private final Agent agent;
	
	/** creates an ice agent with the given parameters. */
	public IceAgent( final boolean controling, StunTurnAddress sta ) {
		this.agent = new Agent();
		agent.setControlling(controling);
		if( controling )
			agent.setNominationStrategy(NominationStrategy.NOMINATE_HIGHEST_PRIO);
		
		//stun and turn
		if( sta.stunAddresses != null )
			for( TransportAddress ta : sta.stunAddresses )
				agent.addCandidateHarvester(new StunCandidateHarvester(ta) );
		
		//LongTermCredential ltr = new LongTermCredential("1234", "abcd" ); //generateNonce(5), generateNonce(15));
		if( sta.turnAddresses != null )
			for( TransportAddress ta : sta.turnAddresses )
				agent.addCandidateHarvester(new TurnCandidateHarvester(ta) );
	}

	public IceAgent(final boolean controlling,
					final TransportAddress[] stunAddresses, final TransportAddress[] turnAddresses,
					final LongTermCredential ltCreds) {
		this.agent = new Agent();
        agent.setControlling(controlling);
        if( controlling )
            agent.setNominationStrategy(NominationStrategy.NOMINATE_HIGHEST_PRIO);

        //stun and turn
        if (stunAddresses != null)
            for( TransportAddress ta : stunAddresses )
                agent.addCandidateHarvester(new StunCandidateHarvester(ta) );

        if (turnAddresses != null)
            for( TransportAddress ta : turnAddresses )
                agent.addCandidateHarvester(new TurnCandidateHarvester(ta, ltCreds) );

    }
	public void createStreams( Collection<String> streamnames ) throws IOException {
		for( String s : streamnames ) {
			createStream( s );
		}
	}
	/** returns the underlying agent object. */
	public Agent getAgent() {
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
	public void removeAgentStateChangeListener( PropertyChangeListener pcl ) {
		agent.removeStateChangeListener(pcl);
	}
	public List<String> getStreamNames() {
		return agent.getStreamNames();
	}
    public List<IceMediaStream> getStreams() {
        return agent.getStreams();
    }
    public void removeStream(IceMediaStream stream) {agent.removeStream(stream);}
	public void freeAgent() {
		agent.free();
	}
	/** takes the remote candidates passed by the JingleIQ and incorporates that information into this ice agent. */
	public void addRemoteCandidates(JingleIQ jiq) {
		try {
			for (ContentPacketExtension contentpe : jiq.getContentList()) {
				String name = contentpe.getName();
				IceMediaStream ims = agent.getStream(name);
				if (ims != null) {
					for (IceUdpTransportPacketExtension tpe : contentpe.getChildExtensionsOfType(IceUdpTransportPacketExtension.class)) {
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
								// we should always be able to find this if there is one b/c of the sorting we did.
								RemoteCandidate relatedCandidate = relatedAddr != null ? component.findRemoteCandidate(relatedAddr) : null;
								TransportAddress ta = new TransportAddress(ia, cpe.getPort(), Transport.parse(cpe.getProtocol().toLowerCase()));
								RemoteCandidate rc = new RemoteCandidate( ta,
										component,
										convertType(cpe.getType()),
										cpe.getFoundation(),
										cpe.getPriority(),
										relatedCandidate);
								component.addRemoteCandidate(rc);
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
	}
	
	/**
	 * iterates through the content list and adds corresponding ICE transport packet extensions.
	 * 
	 * 
	 * @param contentList the content list already containing the content descriptions generated by Jingle.
	 * @throws IllegalArgumentException if the content list contains streams that are not found here.
	 */
	public void addLocalCandidateToContents(List<ContentPacketExtension> contentList) throws IllegalArgumentException {
		for( ContentPacketExtension cpe : contentList ) {
			String streamName = cpe.getName();
			IceUdpTransportPacketExtension ext = getLocalCandidatePacketExtensionForStream( streamName );
			if( ext == null )
				throw new IllegalArgumentException("No Stream found for " + streamName );
			cpe.addChildExtension(ext);
		}
	}
	private CandidateType convertType(org.ice4j.ice.CandidateType type) {
		String ts = type.toString();
		return CandidateType.valueOf(ts);
	}

    public IceUdpTransportPacketExtension getLocalCandidatePacketExtensionForStream( String streamName ) {
        IceUdpTransportPacketExtension transport = new IceUdpTransportPacketExtension();
        transport.setPassword( agent.getLocalPassword() );
        transport.setUfrag( agent.getLocalUfrag() );

        try {
            IceMediaStream ims = agent.getStream(streamName);
            if( ims == null )
                return null;

            for( Component c : ims.getComponents() ) {
                for( Candidate<?> can : c.getLocalCandidates() ) {
                    CandidatePacketExtension candidate = new CandidatePacketExtension();
                    candidate.setComponent(c.getComponentID());
                    candidate.setFoundation(can.getFoundation());
                    candidate.setGeneration(agent.getGeneration());
                    candidate.setID(nextCandidateId());
                    candidate.setNetwork(0); //FIXME: we need to identify the network card properly.
                    TransportAddress ta = can.getTransportAddress();
                    candidate.setIP( ta.getHostAddress() );
                    candidate.setPort( ta.getPort() );
                    candidate.setPriority(can.getPriority());
                    candidate.setProtocol(can.getTransport().toString());
                    if( can.getRelatedAddress() != null ) {
                        candidate.setRelAddr(can.getRelatedAddress().getHostAddress());
                        candidate.setRelPort(can.getRelatedAddress().getPort());
                    }
                    candidate.setType(convertType(can.getType()));

                    transport.addCandidate(candidate);
                }
            }
        } catch( Exception e ) {
            e.printStackTrace();
            System.exit(0);
        }
        return transport;
    }
    private org.ice4j.ice.CandidateType convertType(CandidateType type) {
		String ts = type.toString();
		return org.ice4j.ice.CandidateType.parse(ts);
	}

	public void createStream( String name ) throws BindException, IllegalArgumentException, IOException {
		IceMediaStream stream = agent.createMediaStream(name);
		int rtpPort = getStreamPort();
		agent.createComponent(stream, Transport.UDP, rtpPort, rtpPort, rtpPort+100);
		agent.createComponent(stream, Transport.UDP, rtpPort+1, rtpPort+1, rtpPort+101);
	}

	private static int getStreamPort() {
		if( ( streamPort & 0x01 ) == 0x01 )
			++streamPort;
		if( streamPort >= MAX_STREAM_PORT )
			streamPort = MIN_STREAM_PORT;
		int r = streamPort;
		streamPort += 2;
		if( streamPort >= MAX_STREAM_PORT )
			streamPort = MIN_STREAM_PORT;
		return r;
	}
	
	private synchronized String nextCandidateId() {
		return String.valueOf(++candidateId);
	}
	public boolean hasCandidatesForAllStreams() {
		for( String nm : agent.getStreamNames() ) {
				if( agent.getStream(nm).getRemoteUfrag() == null )
					return false;
		}
		return true;
	}
	
//	public static String generateNonce(int length) {
//		StringBuilder s = new StringBuilder( length );
//		for( int i=0; i<length; ++i ) {
//			int r = random.nextInt( 26 + 26 + 10 );
//			char c;
//			if( r >= 26 + 26 ) {
//				c = (char) ( '0' + (r-26-26) );
//			} else if( r >= 26 ) {
//				c = (char) ( 'A' + (r-26) );
//			} else {
//				c = (char) ( 'a' + r );
//			}
//			s.append( c );
//		}
//		return s.toString();
//	}
}
