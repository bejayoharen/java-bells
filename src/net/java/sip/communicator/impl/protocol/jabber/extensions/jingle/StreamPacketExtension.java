package net.java.sip.communicator.impl.protocol.jabber.extensions.jingle;

import ch.imvs.sdes4j.srtp.SrtpCryptoAttribute;
import net.java.sip.communicator.impl.protocol.jabber.extensions.AbstractPacketExtension;

/**
 * Copyright (c) Tuenti Technologies. All rights reserved.
 *
 * @author Manuel Peinado Gallego <mpeinado@tuenti.com>
 */
public class StreamPacketExtension extends AbstractPacketExtension {
	public static final String ELEMENT_NAME = "stream";

	public StreamPacketExtension() {
		super(null, ELEMENT_NAME);
	}

	public void setSsrc(SsrcPacketExtension ssrc) {
		getChildExtensions().clear();
		addChildExtension(ssrc);
	}

	public SsrcPacketExtension getSsrc() {
		return getChildExtensionsOfType(SsrcPacketExtension.class).get(0);
	}
}