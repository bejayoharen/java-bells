package net.java.sip.communicator.impl.protocol.jabber.extensions.jingle;

import net.java.sip.communicator.impl.protocol.jabber.extensions.AbstractPacketExtension;

import java.util.List;

/**
 * Copyright (c) Tuenti Technologies. All rights reserved.
 *
 * @author Manuel Peinado Gallego <mpeinado@tuenti.com>
 */
public class RtcpMuxExtension extends AbstractPacketExtension {
	/**
	 * The name of the "payload-type" element.
	 */
	public static final String ELEMENT_NAME = "rtcp-mux";

	/**
	 * Creates a new {@link RtcpMuxExtension} instance.
	 */
	public RtcpMuxExtension()
	{
		super(null, ELEMENT_NAME);
	}
}