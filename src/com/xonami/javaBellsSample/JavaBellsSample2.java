/**
 * 
 */
package com.xonami.javaBellsSample;

import java.io.IOException;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.logging.Level;

import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JingleIQ;
import org.jitsi.service.libjitsi.LibJitsi;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.packet.Packet;

import org.jivesoftware.smackx.ServiceDiscoveryManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xonami.javaBells.JingleManager;
import org.jivesoftware.smackx.entitycaps.EntityCapsManager;

/**
 * 
 * This is an alternate sample that shows a class that can both send and receive calls.
 * This is similar in function to the main example, but structured differently -- there is more common code in the main class between
 * the caller and the receiver. This may be a more useful example in real applications which need to both call out and in.
 * 
 * @author bjorn
 *
 */
public class JavaBellsSample2 {
	enum Action {
		CALL,
		ANSWER,
	}
	protected final static Logger logger = LoggerFactory.getLogger(Logger.class);
	
	private static final String CALLER = "Caller";
	private static final String RECEIVER = "Receiver";
	
	private final String username, password, host;
	
	private final String receiverJid;

	private XMPPConnection connection;
	private SampleJinglePacketHandler sampleJinglePacketHandler;
	
	private boolean running = true;
	private Thread mainThread;
	
	/** prints usage and exits. */
	public static void usage(String name) {
		System.out.println( "Usage: " + name + " action username password host" );
		System.out.println( "\t action: may be CALL or ANSWER" );
		System.exit(1);
	}

	public static void main(String[] args) {
		if( args.length != 5 )
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
		Action action   = Action.valueOf(args[1]);
		System.out.println( "action: " + action );
		String username = args[2];
		String password = args[3];
		String host     = args[4];
		
		System.out.println( "u/p @ h: " + username + " / XXXXX @ " + host);
		
		// start threads to actually do the work of calling/answering:
		JavaBellsSample2 m = new JavaBellsSample2( username, password, host, action == Action.CALL ? CALLER : RECEIVER );

		if( !m.waitForLogin( 5000 ) ) {
			System.err.println( "Could not connect within 5 seconds." );
			System.exit(1);
		}
		
		if( action == Action.CALL ) {
			try {
				m.startCall(m.receiverJid);
			} catch (IOException e1) {
				e1.printStackTrace();
			}
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
	public JavaBellsSample2( String username, String password, String host, String resource ) {
		this.username = username;
		this.password = password;
		this.host     = host;

		receiverJid = username + "@" + host + "/" + RECEIVER;
		
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
	public void startCall(String targetJid) throws IOException {
		sampleJinglePacketHandler.initiateOutgoingCall(targetJid);
	}
	
	/** starts a "receiver" in another thread. The receiver connects to the XMPP server,
	 * and waits for a jingle session initiation request from the caller.
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
                    ServiceDiscoveryManager.setIdentityName("usbcamera");
                    ServiceDiscoveryManager.setIdentityType("camera");
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
								System.out.println( RECEIVER + " -----> : " + packet.toXML() );
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
							if( packet.getClass() == JingleIQ.class ) {
								JingleIQ jiq = (JingleIQ) packet ;
								System.out.println( resource + " <----- [jingle packet]: " + jiq.getSID() + " : " + jiq.getAction() );
							} else {
								System.out.println( resource + " <----- : " + packet.toXML() );
							}
						}},
						new PacketFilter() {
						@Override
						public boolean accept(Packet packet) {
							return true;
						}} );

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
