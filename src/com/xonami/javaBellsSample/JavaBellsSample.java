/**
 * 
 */
package com.xonami.javaBellsSample;

import java.io.IOException;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.List;
import java.util.logging.Level;

import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.ContentPacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JingleIQ;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JinglePacketFactory;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.ContentPacketExtension.CreatorEnum;

import org.jitsi.service.libjitsi.LibJitsi;
import org.jitsi.service.neomedia.MediaType;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.packet.Packet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.xonami.javaBells.IceAgent;
import com.xonami.javaBells.JingleManager;
import com.xonami.javaBells.JinglePacketHandler;
import com.xonami.javaBells.JingleSession;
import com.xonami.javaBells.JingleUtil;
import com.xonami.javaBells.StunTurnAddress;

/**
 * 
 * This is the main class for demonstrating jingle bells.
 * Compile and run without arguments for usage.
 * 
 * @author bjorn
 *
 */
public class JavaBellsSample {
	enum Action {
		CALL,
		ANSWER,
		CALL_AND_ANSWER
	}
	protected final static Logger logger = LoggerFactory.getLogger(Logger.class);
	
	private static final String CALLER = "Caller";
	private static final String RECEIVER = "Receiver";
	
	private final String username, password, host;
	
	private final String callerJid, receiverJid;
	
	boolean running = true;
	Thread answerThread, callThread;
	
	/** prints usage and exits. */
	public static void usage(String name) {
		System.out.println( "Usage: " + name + " action username password host" );
		System.out.println( "\t action: may be CALL, ANSWER or CALL_AND_ANSWER" );
		System.exit(1);
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		if( args.length != 5 )
			usage(args[0]);
		
		Thread.setDefaultUncaughtExceptionHandler( new UncaughtExceptionHandler() {
			@Override
			public void uncaughtException(Thread t, Throwable e) {
				logger.error("In thread: ", t);
				logger.error("Uncaught Exception: ", e);
				System.exit(1);
			}
		}) ;
		
		// reduce the insane, unreadable amount of chattiness from libjitsi and ice4j:
		java.util.logging.Logger l = java.util.logging.Logger.getLogger("");
		l.setLevel(Level.WARNING);
		
		LibJitsi.start();
		
		JingleManager.enableJingle();

		String cmd      = args[0];
		System.out.println( "cmd: " + cmd );
		Action action   = Action.valueOf(args[1]);
		System.out.println( "action: " + action );
		String username = args[2];
		String password = args[3];
		String host     = args[4];
		
		System.out.println( "u/p @ h: " + username + " / " + password + " @ " + host);
		
		JavaBellsSample m = new JavaBellsSample( username, password, host );
		if( action == Action.ANSWER || action == Action.CALL_AND_ANSWER ) {
			m.startAnswer();
		}
		if( action == Action.CALL || action == Action.CALL_AND_ANSWER ) {
			m.startCall();
		}
		
		System.out.println( "Hit enter to stop: " );
		while( true )
			try {
				System.in.read();
				break;
			} catch (IOException e) {}
		m.joinAll();
		LibJitsi.stop();
		System.exit(0);
	}
	
	/** creates a new object with the given username and password on the given host. */
	public JavaBellsSample( String username, String password, String host ) {
		this.username = username;
		this.password = password;
		this.host     = host;
		
		callerJid = username + "@" + host + "/" + CALLER ;
		receiverJid = username + "@" + host + "/" + RECEIVER ;
	}
	
	/** waits for both calling and answering thread to return. */
	public void joinAll() {
		running = false;
		if( answerThread != null )
			while( true )
				try {
					synchronized( answerThread ) {
						answerThread.notify();
					}
					answerThread.join();
					break;
				} catch (InterruptedException e) {}
		if( callThread != null )
			while( true )
				try {
					synchronized( callThread ) {
						callThread.notify();
					}
					callThread.join();
					break;
				} catch (InterruptedException e) {}
	}
	
	/** starts a "receiver" in another thread. The receiver connects to the XMPP server,
	 * and waits for a jingle session initiation request from the caller.
	 */
	public void startAnswer() {
		answerThread = new Thread() {
			@Override
			public void run() {
				try {
					log( RECEIVER, "connecting to " + host );
					
					ConnectionConfiguration config = new ConnectionConfiguration(host);
					XMPPConnection connection = new XMPPConnection( config );
					connection.connect();
					
					new JinglePacketHandler(connection) {
						@Override
						public JingleSession createJingleSession( String sid, JingleIQ jiq ) {
							return new ReceiverJingleSession(this, callerJid, sid, this.connection );
						}
					} ;
					
					//display jingle packets
					connection.addPacketListener( new PacketListener() {
						@Override
						public void processPacket(Packet packet) {
							JingleIQ j = (JingleIQ) packet;
							System.out.println( RECEIVER + "[jingle packet]: " + j.getSID() + " : " + j.getAction() );
						}},
						new PacketFilter() {
						@Override
						public boolean accept(Packet packet) {
							return packet.getClass() == JingleIQ.class;
						}} );
					//display all incoming packets
					connection.addPacketListener( new PacketListener() {
						@Override
						public void processPacket(Packet packet) {
							System.out.println( RECEIVER + ": " + packet.toXML() );
						}},
						new PacketFilter() {
						@Override
						public boolean accept(Packet packet) {
							return true;
						}} );

					log( RECEIVER, "logging on as " + username + "/" + RECEIVER );
					connection.login(username, password, RECEIVER);
					//This doesn't work in my testing, so we use DNS instead.
//					log( RECEIVER, "running exodisco" );
//					Packet exodisco = new Packet() {
//						@Override
//						public String toXML() {
//							return "<iq from='" + receiverJid + "'"
//								    + " id='" + Packet.nextID() + "'"
//								    + " to='" + host + "'"
//								    + " type='get'>"
//								    + "<services xmlns='urn:xmpp:extdisco:1' type='stun'/>"
//								    + "</iq>" ;
//						}
//					} ;
//					connection.sendPacket(exodisco);
					
					log( RECEIVER, "Waiting..." );
					while( running ) {
						synchronized ( this ) {
							try {
								wait(1000);
							} catch (InterruptedException e) {}
						}
					}
					connection.disconnect();
					log( RECEIVER, "Done. Exiting thread." );
				} catch ( Exception e ) {
					System.out.println( RECEIVER + ": " + e );
					e.printStackTrace();
					System.exit(1);
				}
			}
		};
		answerThread.start();
	}
	
	/** starts a "caller" in another thread. The caller connects to the XMPP server,
	 * waits for the receiver to connect, and sends them a jingle request.
	 */
	public void startCall() {
		callThread = new Thread() {
			@Override
			public void run() {
				try {
					log( CALLER, "connecting to " + host );

					XMPPConnection connection = new XMPPConnection( host );
					connection.connect();
					StunTurnAddress sta = StunTurnAddress.getAddress( connection );
					
					final IceAgent iceAgent = new IceAgent(true, callerJid, "video", sta.getStunAddresses(), sta.getTurnAddresses());
					
					new JinglePacketHandler(connection) {
						@Override
						public JingleSession createJingleSession( String sid, JingleIQ jiq ) {
							return new CallerJingleSession(iceAgent, this, receiverJid, sid, this.connection);
						}
					} ;
					
					connection.addPacketListener( new PacketListener() {
							@Override
							public void processPacket(Packet packet) {
								System.out.println( CALLER + ": " + packet.toXML() );
							}
						},
						new PacketFilter() {
							@Override
							public boolean accept(Packet packet) {
								return packet.getClass() == JingleIQ.class;
							}
						} );
					
//					PacketCollector collector = connection.createPacketCollector( new PacketFilter() {
//						@Override
//						public boolean accept(Packet packet) {
//							return true;
//						}} );
					
					log( CALLER, "logging on as " + username + "/" + CALLER );
					connection.login(username, password, CALLER);
					
					//this only works if they are in our roster
//					log( CALLER, "Waiting for Receiver to become available." );
//					while( running && !connection.getRoster().contains(receiverJid) ) {
//						collector.nextResult(100);
//					}
					
					log( CALLER, "Ringing" );
//					CallPeerJabberImpl callPeer = new CallPeerJabberImpl(username + "/" + RECEIVER, null);
					
					List<ContentPacketExtension> contentList = JingleUtil.createContentList(MediaType.VIDEO, CreatorEnum.initiator, "video", ContentPacketExtension.SendersEnum.both);
					iceAgent.addLocalCandidateToContents(contentList);
					
					//offer.add( new ContentPacketExtension( ContentPacketExtension.CreatorEnum.initiator, "session", "camera", ContentPacketExtension.SendersEnum.both ) );
					
		            JingleIQ sessionInitIQ = JinglePacketFactory.createSessionInitiate(
		            		connection.getUser(), //my JID
		            		receiverJid, //target jid
		            		JingleIQ.generateSID(),
		            		contentList );
		            
		            System.out.println( "CALLER: sending jingle request: " + sessionInitIQ.toXML() );
		            
		            connection.sendPacket(sessionInitIQ);
					
					log( CALLER, "Waiting..." );
					while( running ) {
						synchronized ( this ) {
							try {
								wait(1000);
							} catch (InterruptedException e) {}
						}
					}
					connection.disconnect();
					log( CALLER, "Done. Exiting thread." );
				} catch ( Exception e ) {
					System.out.println( CALLER + ": " + e );
					e.printStackTrace();
					System.exit(1);
				}
			}
		};
		callThread.start();
	}
	
	
	private void log( String tag, String message ) {
		logger.info( "[{}]: {}", tag, message );
	}
}
