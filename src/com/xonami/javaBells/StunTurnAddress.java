package com.xonami.javaBells;

import java.util.HashMap;

import org.ice4j.Transport;
import org.ice4j.TransportAddress;
import org.jivesoftware.smack.XMPPConnection;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Record;
import org.xbill.DNS.SRVRecord;
import org.xbill.DNS.TextParseException;
import org.xbill.DNS.Type;

/**
 * finds, stores and caches STUN and TURN server addresses based on DNS entries.
 * @author bjorn
 */
public class StunTurnAddress {
	protected TransportAddress stunAddresses[];
	protected TransportAddress turnAddresses[];
	private Thread addressFetchThread;
	private final String hosts[];
	private static HashMap<String,StunTurnAddress> hostToAddressMap = new HashMap<String,StunTurnAddress>();

	/** call this to get the STUN and TURN server addresses associated with the given connection.
	 * The objects are cached locally so they don't require re-hitting the DNS servers. The actual
	 * fetch happens in the background, starting when a StunTurnAddress object is created.
	 * 
	 * @param connection
	 * @return
	 */
	public static StunTurnAddress getAddress( XMPPConnection connection ) {
		//FIXME: my stun server is not setup correctly (it needs another IP address).
		// so we fall back on jitsi for now.
		String hosts[] = new String[] {
//				connection.getServiceName(),
//				connection.getHost(),
				"jitsi.org",
		};
		String hostsAsOneString = "";
		for( int i=0; i<hosts.length; ++i )
			hostsAsOneString = hostsAsOneString + ":" + hosts[i];
		StunTurnAddress ret = hostToAddressMap.get(hostsAsOneString);
		if( ret != null )
			return ret;
		
		ret = new StunTurnAddress( hosts );
		hostToAddressMap.put(hostsAsOneString, ret);
		
		return ret;
	}
	
	protected StunTurnAddress( String[] hosts ) {
		this.hosts = hosts;
		
		startAddressFetch();
	}
	
	private synchronized void completeAddressFetch() {
		while( addressFetchThread != null ) {
			try {
				addressFetchThread.join();
				addressFetchThread = null;
			} catch (InterruptedException e) {}
		}
	}

	private synchronized void startAddressFetch() {
		if( addressFetchThread != null )
			return;
		addressFetchThread = new Thread() {
			@Override
			public void run() {
				Record[] stunRecords = null;
				Record[] turnRecords = null;
				for( int i=0; i<hosts.length; ++i ) {
					String stunQuery = "_stun._udp." + hosts[i] ;
					String turnQuery = "_turn._udp." + hosts[i] ;
					
					try {
						if( stunRecords == null )
							stunRecords = lookupSrv( stunQuery );
						if( turnRecords == null )
							turnRecords = lookupSrv( turnQuery );
						if( stunRecords != null && turnRecords != null )
							break;
					} catch( TextParseException tpe ) {
						throw new RuntimeException( tpe );
					}
				}
				if( stunRecords == null ) {
					stunAddresses = null ;
				} else {
					stunAddresses = new TransportAddress[stunRecords.length];
					for( int i=0; i<stunRecords.length; ++i ) {
						SRVRecord srv = (SRVRecord) stunRecords[i] ;
						stunAddresses[i] = new TransportAddress(srv.getTarget().toString().replaceFirst("\\.$", ""), srv.getPort(), Transport.UDP);
					}
				}
				if( turnRecords == null ) {
					turnAddresses = null ;
				} else {
					turnAddresses = new TransportAddress[stunRecords.length];
					for( int i=0; i<turnAddresses.length; ++i ) {
						SRVRecord srv = (SRVRecord) turnRecords[i] ;
						turnAddresses[i] = new TransportAddress(srv.getTarget().toString().replaceFirst("\\.$", ""), srv.getPort(), Transport.UDP);
					}
				}
			}

			private Record[] lookupSrv(String query) throws TextParseException {
				while( true ) {
					try {
						// Bug 6427854 causes this to sometimes throw an NPE
						return new Lookup( query, Type.SRV ).run();
					} catch( NullPointerException npe ) {
						Thread.yield();
					}
				}
			}
		};
		addressFetchThread.start();
	}

	/**
	 * Ensures that the DNS fetch (which runs in the background) is complete and returns the addresses of the stun server
	 * or null of none were found.
	 */
	public TransportAddress[] getStunAddresses() {
		completeAddressFetch();
		return stunAddresses.clone();
	}

	/**
	 * Ensures that the DNS fetch (which runs in the background) is complete and returns the addresses of the turn server
	 * or null of none were found.
	 */
	public TransportAddress[] getTurnAddresses() {
		completeAddressFetch();
		return turnAddresses.clone();
	}
}
