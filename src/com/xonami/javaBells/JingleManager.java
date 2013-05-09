package com.xonami.javaBells;

import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JingleIQ;
import net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JingleIQProvider;

import org.jivesoftware.smack.Connection;
import org.jivesoftware.smack.ConnectionCreationListener;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jivesoftware.smackx.ServiceDiscoveryManager;

/**
 * Manages global smack/jingle features such as converting jingle packets to jingleIQ objects.
 * 
 * @author bjorn
 *
 */
public class JingleManager {
	private static boolean enabled = false;
	
	public static synchronized final void enableJingle() {
		if( enabled )
			return;
		enabled = true;
        ProviderManager providerManager = ProviderManager.getInstance();
        providerManager.addIQProvider( JingleIQ.ELEMENT_NAME,
                JingleIQ.NAMESPACE,
                new JingleIQProvider());

        Connection.addConnectionCreationListener(new ConnectionCreationListener() {
            public synchronized void connectionCreated(Connection connection) {
            	if( ! ServiceDiscoveryManager.getInstanceFor(connection).includesFeature(JingleIQ.NAMESPACE) )
            		ServiceDiscoveryManager.getInstanceFor(connection).addFeature(JingleIQ.NAMESPACE);
            }
        });
	}
}
