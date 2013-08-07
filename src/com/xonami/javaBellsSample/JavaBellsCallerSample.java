/**
 * 
 */
package com.xonami.javaBellsSample;

import java.io.IOException;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.Collection;
import java.util.logging.Level;

import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JingleIQ;
import org.jitsi.service.libjitsi.LibJitsi;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.RosterEntry;
import org.jivesoftware.smack.RosterListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.Presence;

import org.jivesoftware.smackx.ServiceDiscoveryManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xonami.javaBells.JingleManager;
import org.jivesoftware.smackx.entitycaps.EntityCapsManager;

/**
 * 
 * Example code for calling out only.
 * 
 * @author bjorn
 *
 */
public class JavaBellsCallerSample {
	protected final static Logger logger = LoggerFactory.getLogger(Logger.class);
	
	private static final String CALLER = "Caller";
	private static final String RECEIVER = "Receiver";
	
	private final String username, password, host;

	private final String fulljid, barejid;

	private XMPPConnection connection;
	private SampleJinglePacketHandler sampleJinglePacketHandler;
	
	private boolean running = true;
	private Thread mainThread;
	
	/** prints usage and exits. */
	public static void usage(String name) {
		System.out.println( "Usage: " + name + " username password host" );
		System.exit(1);
	}

	public static void main(String[] args) {
		if( args.length != 4 )
			usage(args[0]);
		
		Thread.setDefaultUncaughtExceptionHandler( new UncaughtExceptionHandler() {
			@Override
			public void uncaughtException(Thread t, Throwable e) {
				logger.error("In thread: ", t);
				logger.error("Uncaught Exception: ", e);
			}
		}) ;
		
		// reduce the insane, unreadable amount of chattiness from libjitsi and ice4j:
		java.util.logging.Logger l = java.util.logging.Logger.getLogger("");
		l.setLevel(Level.WARNING);
		
		// -- libjitsi needs to be started
		LibJitsi.start();
		
		// -- we need to initialize jingle
		JingleManager.enableJingle();

		// parse commandline args:
		String cmd      = args[0];
		System.out.println( "cmd: " + cmd );
		String username = args[1];
		String password = args[2];
		String host     = args[3];
		
		System.out.println( "u/p @ h: " + username + " / " + password + " @ " + host);
		
		// start threads to actually do the work of calling/answering:
		JavaBellsCallerSample m = new JavaBellsCallerSample( username, password, host, CALLER );

		if( !m.waitForLogin( 5000 ) ) {
			System.err.println( "Could not connect within 5 seconds." );
			System.exit(1);
		}
		
		// stop threads when the user hits enter, cleanup and exit.
		System.out.println( "Hit enter to stop: " );
		while( true )
			try {
				System.in.read();
				break;
			} catch (IOException e) {}
		m.joinMainThread();
		LibJitsi.stop();
		System.exit(0);
	}
	
	/** creates a new object with the given username and password on the given host. */
	public JavaBellsCallerSample( String username, String password, String host, String resource ) {
		this.username = username;
		this.password = password;
		this.host     = host;
		
		fulljid = username + "@" + host + "/" + resource;
		barejid = username + "@" + host + "/" ;
		
		startMainThread(resource);
	}
	
	/** waits for both calling and answering thread to return. */
	public void joinMainThread() {
		running = false;
		if( mainThread != null )
			while( true )
				try {
					synchronized(mainThread ) {
						mainThread.notify();
					}
					mainThread.join();
					break;
				} catch (InterruptedException e) {}
	}
	
	public boolean waitForLogin( long wait ) {
		//FIXME: this function doesn't check for error conditions
		long start = System.currentTimeMillis();
		while( connection == null || !connection.isAuthenticated() )
			try {
				if( start - System.currentTimeMillis() > wait )
					return false;
				Thread.sleep(100);
			} catch (InterruptedException e) {}
		return true;
	}
	
	/** starts a connection in another thread. After connecting to the XMPP server,
	 * it will wait for another user to connect with the same bare jid but different resource
	 * and attempt to call them.
	 */
	public void startMainThread(final String resource) {
		mainThread = new Thread() {
			@Override
			public void run() {
				try {
					log( resource, "connecting to " + host );
					
					// connect to host (don't log in yet)
					ConnectionConfiguration config = new ConnectionConfiguration(host);
					connection = new XMPPConnection( config );
					connection.connect();
					// setup service discovery and entity capabilities.
					// this ensures that other software, such as Jitsi, knows that we support
					// ice and so on
					//ServiceDiscoveryManager.setIdentityName("Java Bells");
					ServiceDiscoveryManager disco = ServiceDiscoveryManager.getInstanceFor(connection);
					EntityCapsManager ecm = EntityCapsManager.getInstanceFor(connection);
					
					ecm.enableEntityCaps();

					disco.addFeature("http://jabber.org/protocol/disco#info");
					disco.addFeature("urn:xmpp:jingle:1");
					disco.addFeature("urn:xmpp:jingle:transports:ice-udp:1");
					disco.addFeature("urn:xmpp:jingle:apps:rtp:1");
					disco.addFeature("urn:xmpp:jingle:apps:rtp:audio");
					disco.addFeature("urn:xmpp:jingle:apps:rtp:video");
					
					// Handle all incoming Jingle packets with a Jingle Packet Handler.
					sampleJinglePacketHandler = new SampleJinglePacketHandler(connection) ;
					
					// display out all packets that get sent:
					connection.addPacketSendingListener(
						new PacketListener() {
							@Override
							public void processPacket(Packet packet) {
								System.out.println( resource + " ----> : " + packet.toXML() );
							}
						},
						new PacketFilter() {
							@Override
							public boolean accept(Packet packet) {
								return true;
							}
						} ) ;
					
					//display all incoming packets
					connection.addPacketListener( new PacketListener() {
						@Override
						public void processPacket(Packet packet) {
							if(  packet.getClass() == JingleIQ.class ) {
								JingleIQ j = (JingleIQ) packet;
								System.out.println( resource + " <---- : [jingle packet] " + j.getSID() + " : " + j.getAction() + " : " + packet.toXML() );
							} else {
								System.out.println( resource + " <---- : " + packet.toXML() );
							}
						}},
						new PacketFilter() {
						@Override
						public boolean accept(Packet packet) {
							return true;
						}} );
					
					//respond to presence packets. This is where we look for a the user and connect
					connection.addPacketListener( new PacketListener() {
						boolean once = false;
						@Override
						public void processPacket(Packet packet) {
							if( once )
								return;
							once = true;
//							System.out.println( packet.getClass() + " : " + packet );
							Presence p = (Presence) packet ;
							if( p.isAvailable() ) {
								String from = p.getFrom();
								
								if( from.startsWith(barejid) && !from.equals(fulljid) ) {
									//same user different resource. call them:
									try {
										sampleJinglePacketHandler.initiateOutgoingCall(from);
									} catch( IOException ioe ) {
										throw new RuntimeException( ioe );
									}
								}
							}
						}},
						new PacketFilter() {
						@Override
						public boolean accept(Packet packet) {
							return packet.getClass() == Presence.class;
						}
					} );

					// -- log in
					log( RECEIVER, "logging on as " + username + "/" + resource );
					connection.login(username, password, resource);
					
					// -- just hang out until we are asked to exit.
					// the work will be done by the SampleJingleSession created by the SampleJinglePacketHandler
					// we created and applied earlier.
					log( RECEIVER, "Waiting..." );
					while( running ) {
						synchronized ( this ) {
							try {
								wait(1000);
							} catch (InterruptedException e) {}
						}
					}
					connection.disconnect();
					log( resource, "Done. Exiting thread." );
				} catch ( Exception e ) {
					System.out.println( resource + ": " + e );
					e.printStackTrace();
					System.exit(1);
				}
			}
		};
		mainThread.start();
	}
	
	private void log( String tag, String message ) {
		logger.info( "[{}]: {}", tag, message );
	}
}
