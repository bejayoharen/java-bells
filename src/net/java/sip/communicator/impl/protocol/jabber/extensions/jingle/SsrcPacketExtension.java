package net.java.sip.communicator.impl.protocol.jabber.extensions.jingle;

import net.java.sip.communicator.impl.protocol.jabber.extensions.AbstractPacketExtension;

/**
 * Copyright (c) Tuenti Technologies. All rights reserved.
 *
 * @author Manuel Peinado Gallego <mpeinado@tuenti.com>
 */
public class SsrcPacketExtension extends AbstractPacketExtension {
	public static final String ELEMENT_NAME = "ssrc";

	public SsrcPacketExtension() {
		super(null, ELEMENT_NAME);
	}
}